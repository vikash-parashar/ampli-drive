package com.vikash.ampli.drive.bhajan

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.vikash.ampli.drive.DriveApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Settings › When the car starts › Bhajan (off by default): one short bhajan, low, after the
 * welcome and the reminders, on the days the driver picked ([BhajanPlan]). Only Ampli's own
 * recordings from assets/bhajan/ play; a bhajan whose file isn't in this build is skipped
 * quietly. It stops when music starts (audio focus), when "Hey Ampli" listens, on "छोड़ दे" and
 * after [BhajanPlan.MAX_MS] whatever the file. One free bhajan; the rest need Pro.
 */
class BhajanPlayer(private val app: DriveApp) {
    private val sp = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val scope = MainScope()
    private val audio = app.getSystemService(AudioManager::class.java)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<BhajanSettings> = _settings.asStateFlow()

    /** The bundled bhajans, from assets/bhajan/manifest.json (empty when it is missing). */
    val catalog: List<Bhajan> by lazy {
        try {
            app.assets.open(BhajanPlan.MANIFEST).bufferedReader().use { BhajanPlan.parse(it.readText()) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** What the bhajan card shows while one plays. */
    data class Now(val bhajan: Bhajan, val day: DayOfWeek, val startedAtMs: Long)

    private val _now = MutableStateFlow<Now?>(null)
    val now: StateFlow<Now?> = _now.asStateFlow()

    private var player: MediaPlayer? = null
    private var done: CompletableDeferred<Unit>? = null
    private var watch: Job? = null

    private val focus: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(ATTRS)
        .setOnAudioFocusChangeListener { change ->
            // Music started, a call rang, a navigation prompt took over: the bhajan gives way.
            if (change < 0) scope.launch { stop() }
        }
        .build()

    fun update(change: (BhajanSettings) -> BhajanSettings) {
        val s = change(_settings.value)
        _settings.value = s
        sp.edit()
            .putBoolean(KEY_ON, s.enabled)
            .putString(KEY_DAYS, BhajanPlan.encodeDays(s.days))
            .putBoolean(KEY_MORNING, s.everyMorning)
            .putString(KEY_CHOICE, BhajanPlan.encodeChoice(s.choice))
            .putFloat(KEY_VOLUME, s.volume)
            .apply()
    }

    private fun load() = BhajanSettings(
        enabled = sp.getBoolean(KEY_ON, false),
        days = BhajanPlan.decodeDays(sp.getString(KEY_DAYS, null)) ?: BhajanSettings.DEFAULT_DAYS,
        everyMorning = sp.getBoolean(KEY_MORNING, false),
        choice = BhajanPlan.decodeChoice(sp.getString(KEY_CHOICE, null)),
        volume = sp.getFloat(KEY_VOLUME, BhajanSettings.DEFAULT_VOLUME),
    )

    /** True when the bhajan's audio is bundled in this build. */
    fun exists(b: Bhajan): Boolean = try {
        app.assets.openFd(b.file).use { it.length > 0 }
    } catch (_: Exception) {
        false
    }

    /** CarStart, after the welcome and the reminders. Returns when the bhajan ends or is stopped. */
    suspend fun playAtStart(showCard: Boolean) {
        val now = LocalDate.now()
        val decision = BhajanPlan.decide(
            settings = _settings.value,
            catalog = catalog,
            today = now.dayOfWeek,
            hour = LocalTime.now().hour,
            epochDay = now.toEpochDay(),
            lastPlayedDay = sp.getLong(KEY_LAST_DAY, -1L),
            isPro = app.prefs.pro.value.isPro,
            exists = ::exists,
        )
        val noBundled = decision is BhajanDecision.Skip &&
            (decision.reason == BhajanDecision.Reason.FileMissing || decision.reason == BhajanDecision.Reason.NoBhajans)
        if (decision !is BhajanDecision.Play && !noBundled) {
            Log.d(TAG, "no bhajan: $decision")
            return
        }
        // Never over a call, a Hey Ampli session, or music the driver already has playing.
        if (app.speaker.inCall() || app.voice.busy.value) return
        if (audio?.isMusicActive == true) {
            Log.d(TAG, "music already playing: no bhajan")
            return
        }
        sp.edit().putLong(KEY_LAST_DAY, now.toEpochDay()).apply()
        if (decision is BhajanDecision.Play) {
            play(decision.bhajan, decision.volume, now.dayOfWeek, showCard)
            return
        }
        // No Ampli recording in this build (none is bundled yet): the day's deity from the
        // driver's own songs, then Ampli MP3's search, else say how to add one.
        val deity = Deity.of(now.dayOfWeek)
        val volume = _settings.value.volume.coerceIn(0.1f, 1f)
        val local = findLocal(deity, now.toEpochDay())
        if (local != null) {
            playLocal(deity, local, volume, now.dayOfWeek, showCard)
            return
        }
        if (app.mediaHub.playFromSearch(com.vikash.ampli.drive.media.MediaHub.AMPLI_PACKAGE, deity.nameEn + " bhajan")) {
            Log.d(TAG, "asked Ampli MP3 for ${deity.nameEn}")
            return
        }
        try {
            app.speaker.say(app.getString(com.vikash.ampli.drive.R.string.bhajan_none_found, app.getString(deityName(deity))), app.prefs.language.value)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private fun deityName(d: Deity): Int = when (d) {
        Deity.Shiv -> com.vikash.ampli.drive.R.string.deity_shiv
        Deity.Hanuman -> com.vikash.ampli.drive.R.string.deity_hanuman
        Deity.Ganesh -> com.vikash.ampli.drive.R.string.deity_ganesh
        Deity.Vishnu -> com.vikash.ampli.drive.R.string.deity_vishnu
        Deity.Durga -> com.vikash.ampli.drive.R.string.deity_durga
        Deity.Shani -> com.vikash.ampli.drive.R.string.deity_shani
        Deity.Surya -> com.vikash.ampli.drive.R.string.deity_surya
    }

    /**
     * The driver's own songs (MediaStore; needs the storage / audio permission Car Hub asks for
     * the music features). Nothing is downloaded or streamed. Off the main thread.
     */
    private suspend fun findLocal(deity: Deity, seed: Long): LocalTrack? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val perm = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
        if (app.checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED) return@withContext null
        val tracks = ArrayList<LocalTrack>()
        try {
            val cols = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.ALBUM,
                android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
                android.provider.MediaStore.Audio.Media.DURATION,
            )
            app.contentResolver.query(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, null, null, null)?.use { c ->
                while (c.moveToNext() && tracks.size < MAX_SCAN) {
                    tracks += LocalTrack(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5))
                }
            }
        } catch (_: Exception) {
        }
        BhajanPlan.pickLocal(deity, tracks, seed)
    }

    private suspend fun playLocal(deity: Deity, t: LocalTrack, volume: Float, day: DayOfWeek, showCard: Boolean) {
        val uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, t.id)
        val shown = Bhajan(
            id = "local_${t.id}",
            title = t.title ?: deity.nameEn,
            titleHi = t.title ?: deity.nameHi,
            greeting = deity.greeting,
            file = "",
            seconds = (minOf(if (t.durationMs > 0) t.durationMs else LOCAL_MAX_MS, LOCAL_MAX_MS) / 1000).toInt(),
            free = true,
            days = setOf(day),
            original = false,
        )
        playSource(shown, volume, day, showCard, LOCAL_MAX_MS) { mp -> mp.setDataSource(app, uri) }
    }

    /** The settings sheet's "Play": the chosen bhajan once (Pro ones need Pro). */
    fun preview(b: Bhajan) {
        if (BhajanPlan.locked(b, app.prefs.pro.value.isPro) || !exists(b)) return
        scope.launch { play(b, _settings.value.volume, LocalDate.now().dayOfWeek, showCard = false) }
    }

    private suspend fun play(b: Bhajan, volume: Float, day: DayOfWeek, showCard: Boolean) =
        playSource(b, volume, day, showCard, BhajanPlan.MAX_MS) { mp ->
            app.assets.openFd(b.file).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        }

    private suspend fun playSource(b: Bhajan, volume: Float, day: DayOfWeek, showCard: Boolean, maxMs: Long, source: (MediaPlayer) -> Unit) {
        stop()
        val signal = CompletableDeferred<Unit>()
        done = signal
        val mp = try {
            MediaPlayer().apply {
                source(this)
                setAudioAttributes(ATTRS)
                setVolume(volume, volume)
                setOnCompletionListener { signal.complete(Unit) }
                setOnErrorListener { _, _, _ -> signal.complete(Unit); true }
                prepare()
            }
        } catch (e: Exception) {
            Log.w(TAG, "can't play ${b.id}: ${e.message}")
            return
        }
        if (audio?.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            mp.release()
            return
        }
        player = mp
        if (showCard) _now.value = Now(b, day, System.currentTimeMillis())
        // "Hey Ampli" (wake word or button) stops it at once.
        watch = scope.launch {
            app.voice.busy.first { it }
            stop()
        }
        try {
            mp.start()
            withTimeoutOrNull(maxMs) { signal.await() }
        } finally {
            stop()
        }
    }

    /** "छोड़ दे", music, Hey Ampli, or the end. Safe to call any time. */
    fun stop() {
        watch?.cancel()
        watch = null
        player?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        if (player != null) audio?.abandonAudioFocusRequest(focus)
        player = null
        _now.value = null
        done?.complete(Unit)
        done = null
    }

    private companion object {
        const val TAG = "BhajanPlayer"
        const val FILE = "ampli_bhajan"
        const val KEY_ON = "on"
        const val KEY_DAYS = "days"
        const val KEY_MORNING = "every_morning"
        const val KEY_CHOICE = "choice"
        const val KEY_VOLUME = "volume"
        const val KEY_LAST_DAY = "last_day"

        /** A song of the driver's own plays at most this long at a car start. */
        const val LOCAL_MAX_MS = 10 * 60_000L

        /** Songs looked at in MediaStore (a big library on a slow unit). */
        const val MAX_SCAN = 5_000

        val ATTRS: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
