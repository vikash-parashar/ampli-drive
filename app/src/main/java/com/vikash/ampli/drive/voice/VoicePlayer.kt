package com.vikash.ampli.drive.voice

import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vikash.ampli.drive.DriveApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Speaks a [Spoken] line: the recorded voice pack (Settings › Voice: Male "Ampli Bhai" / Female
 * "Ampli Behen") when every clip of the line is bundled and up to date, otherwise the phone's TTS
 * ([Speaker]). Clips are mono Opus in Ogg, played back to back by ExoPlayer (its own Ogg reader
 * plus the platform Opus decoder, so it works on Android 8). Like the TTS path it holds
 * AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK while talking, so music dips instead of stopping.
 * All calls from the main thread.
 */
class VoicePlayer(private val app: DriveApp) {
    private val audio = app.getSystemService(AudioManager::class.java)
    private var player: ExoPlayer? = null
    private var playing: CompletableDeferred<Boolean>? = null

    private val _lastLine = MutableStateFlow<String?>(null)

    /**
     * The last line Ampli said from the reviewed line bank (never message text or a search
     * answer): Home shows it as the caption, so it is always family-safe.
     */
    val lastLine: StateFlow<String?> = _lastLine.asStateFlow()

    /** Shows [line] as Home's caption without saying it (screen previews; only bank lines go here). */
    internal fun showCaption(line: String) {
        _lastLine.value = line
    }

    /** What is bundled in this build. Loaded once; an APK never changes its assets. */
    val library: ClipLibrary by lazy { load() }

    private val focusAttributes = android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val duckFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(focusAttributes)
        .build()

    /** The voice in use (Settings › Voice), with the default applied. */
    val voice: VoicePack get() = VoicePack.resolve(app.prefs.voicePack.value, library.count(VoicePack.Male))

    /**
     * Says [spoken] and returns when done. With [needFocus], nothing is said when Android refuses
     * audio focus (a call is on).
     */
    suspend fun say(spoken: Spoken, needFocus: Boolean = false) {
        sayChecked(spoken, needFocus)
    }

    /**
     * [say] that reports whether anything was heard: false when audio focus was refused, the clips
     * failed and there is no phone voice, or the phone voice failed. [preferRecorded]: use the
     * recorded Ampli voice even when Settings › Voice says phone voice (the car-start welcome).
     */
    suspend fun sayChecked(spoken: Spoken, needFocus: Boolean = false, preferRecorded: Boolean = false): Boolean {
        if (spoken.caption) _lastLine.value = spoken.text
        var v = voice
        // No phone voice on this unit (TS7 and other units without a TTS engine): the recorded
        // Ampli voice says it instead, rather than nothing at all.
        if (!v.human && (preferRecorded || !ttsAvailable())) v = fallbackVoice()
        val clips = library.plan(v, spoken)
        if (clips != null && v.folder != null) {
            val uris = clips.map { Uri.parse("asset:///voice/${v.folder}/$it.ogg") }
            when (play(uris, needFocus)) {
                Result.Played -> return true
                Result.NoFocus -> return false
                Result.Failed -> Unit // Fall through to TTS below.
            }
        }
        return app.speaker.sayChecked(spoken.text, spoken.language, needFocus)
    }

    /** True when every clip of [spoken] is bundled in the recorded voice [sayChecked] would use. */
    fun hasRecorded(spoken: Spoken): Boolean {
        val v = voice.takeIf { it.human } ?: fallbackVoice()
        return v.folder != null && library.plan(v, spoken) != null
    }

    private val _ttsMissing = MutableStateFlow(false)

    /** True once we found that the unit has no working TTS engine (Settings › Voice says so). */
    val ttsMissing: StateFlow<Boolean> = _ttsMissing.asStateFlow()

    suspend fun ttsAvailable(): Boolean {
        val ok = app.speaker.isAvailable()
        _ttsMissing.value = !ok
        return ok
    }

    /** The driver's recorded voice if they picked one, else Male. */
    private fun fallbackVoice(): VoicePack = app.prefs.voicePack.value?.takeIf { it.human } ?: VoicePack.Male

    /**
     * A plain (standard style) line. With a TTS engine it is spoken as written; without one the
     * recorded line for [standIn] plays (its stand-in clip when the line has a name or number),
     * so the driver still hears something close.
     */
    suspend fun sayPlain(text: String, language: String, standIn: HaryanviLines.Event?, vararg args: Any, needFocus: Boolean = false) {
        if (standIn == null || ttsAvailable()) {
            app.speaker.say(text, language, needFocus)
            return
        }
        val line = app.desi.line(standIn, *args, force = true)
        if (line == null) app.speaker.say(text, language, needFocus) else say(line, needFocus)
    }

    /**
     * Recorded stand-in clips for a standard-style line, for units without a TTS engine: the clips
     * of each [events] line in turn (a camera alert gets its distance clip plus the tail, see
     * [VoiceClips.clipsFor]). Empty when the phone voice works, or when any event has no recorded
     * line; [say] then uses the phone voice as before.
     */
    suspend fun standInClips(events: List<Pair<HaryanviLines.Event, Array<out Any>>>): List<String> {
        if (events.isEmpty() || ttsAvailable()) return emptyList()
        val out = ArrayList<String>()
        for ((event, args) in events) {
            val clips = app.desi.line(event, *args, force = true)?.clips
            if (clips.isNullOrEmpty()) return emptyList()
            out += clips
        }
        return out
    }

    /** [standInClips] for one event. */
    suspend fun standInClips(event: HaryanviLines.Event, vararg args: Any): List<String> =
        standInClips(listOf(event to args))

    private enum class Result { Played, NoFocus, Failed }

    private suspend fun play(uris: List<Uri>, needFocus: Boolean): Result {
        stopPlayer()
        val focus = audio?.requestAudioFocus(duckFocus)
        if (needFocus && focus == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return Result.NoFocus
        val done = CompletableDeferred<Boolean>()
        playing = done
        val exo = try {
            ExoPlayer.Builder(app).build()
        } catch (e: Exception) {
            Log.w(TAG, "no player", e)
            audio?.abandonAudioFocusRequest(duckFocus)
            return Result.Failed
        }
        player = exo
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(C.USAGE_ASSISTANT)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build()
            // Focus is ours (may-duck, like TTS), so ExoPlayer must not ask for its own.
            exo.setAudioAttributes(attrs, false)
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) done.complete(true)
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "clip failed", error)
                    done.complete(false)
                }
            })
            exo.setMediaItems(uris.map { MediaItem.fromUri(it) })
            exo.prepare()
            exo.play()
            // Clips are a few seconds each; never wait forever.
            val ok = withTimeoutOrNull(5_000L + uris.size * 8_000L) { done.await() } ?: false
            return if (ok) Result.Played else Result.Failed
        } finally {
            if (player === exo) player = null
            if (playing === done) playing = null
            exo.release()
            audio?.abandonAudioFocusRequest(duckFocus)
        }
    }

    private fun stopPlayer() {
        // A newer line cuts the one playing, like TTS's QUEUE_FLUSH. Counts as played, no TTS after.
        playing?.complete(true)
        player?.stop()
    }

    /** Stops the clip and the TTS. */
    fun stop() {
        stopPlayer()
        app.speaker.stop()
    }

    private fun load(): ClipLibrary {
        val recorded = HashMap<VoicePack, Map<String, String>>()
        for (v in VoicePack.entries) {
            val folder = v.folder ?: continue
            val index = try {
                app.assets.open("voice/$folder/clips.json").bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                continue // Not in this build yet.
            }
            try {
                val clips = JSONObject(index).getJSONObject("clips")
                val map = HashMap<String, String>()
                for (id in clips.keys()) map[id] = clips.getString(id)
                recorded[v] = map
            } catch (e: Exception) {
                Log.w(TAG, "bad clips.json for $folder", e)
            }
        }
        return ClipLibrary(recorded)
    }

    private companion object {
        const val TAG = "VoicePlayer"
    }
}
