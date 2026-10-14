package com.vikash.ampli.drive.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.MainActivity
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.cameras.CameraRepository
import com.vikash.ampli.drive.i18n.AppLanguages
import com.vikash.ampli.drive.system.Launchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale

/** What the voice overlay shows. */
data class VoiceUiState(
    val active: Boolean = false,
    val phase: Phase = Phase.Idle,
    /** Live transcript of what the driver is saying. */
    val heard: String = "",
    /** What Ampli said last. */
    val reply: String = "",
    /** Destinations waiting for "the first / second / third one" (or a tap). */
    val options: List<Place> = emptyList(),
) {
    enum class Phase { Idle, Listening, Working, Speaking }
}

/**
 * The "Hey Ampli" conversation: greeting, one or a few listen → act turns, spoken replies.
 * Started by the wake word (VoiceService), the Home "Hey Ampli" button or the steering-wheel
 * voice key. Everything is spoken so nothing needs reading or touching while driving; the
 * overlay is a touch fallback. Runs on the main thread.
 */
class VoiceAssistant(private val app: DriveApp) {
    private val scope = MainScope()
    /** Shared with the speed-camera alerts, so there is one TTS engine. */
    private val speaker get() = app.speaker
    /** System recogniser, else the offline Vosk command model (units without Google speech). */
    private val listener = Listener(app, VoskCommandListener(app))
    private val cameras: CameraRepository get() = app.cameras
    private var job: Job? = null

    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    /** True for the whole session. VoiceService keeps the wake-word mic closed meanwhile. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Set by VoiceService: closes the wake-word microphone synchronously before we listen. */
    var releaseWakeMic: (() -> Unit)? = null

    private enum class Expect { Nothing, Destination, Choice }

    /** Starts a session unless one is running. */
    fun start() {
        if (job?.isActive == true) return
        releaseWakeMic?.invoke()
        _busy.value = true
        _state.value = VoiceUiState(active = true, phase = VoiceUiState.Phase.Working)
        job = scope.launch {
            try {
                session()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A failing engine must never leave the mic or the overlay stuck.
            } finally {
                listener.cancel()
                speaker.stop()
                _state.value = VoiceUiState()
                _busy.value = false
            }
        }
    }

    /**
     * Runs [block] with the microphone to itself (Settings › Voice › Check voice "Listen test"):
     * the wake word lets go of the mic meanwhile, as during a session.
     */
    suspend fun <T> withMic(block: suspend () -> T): T {
        releaseWakeMic?.invoke()
        _busy.value = true
        try {
            return block()
        } finally {
            _busy.value = false
        }
    }

    /** The overlay's Cancel button. */
    fun cancel() {
        job?.cancel()
    }

    /** A tap on one of the listed places. */
    fun choose(index: Int) {
        val place = _state.value.options.getOrNull(index) ?: return
        val old = job
        job = scope.launch {
            old?.cancelAndJoin()
            _busy.value = true
            _state.value = VoiceUiState(active = true, phase = VoiceUiState.Phase.Working)
            try {
                val lang = language()
                navigateTo(place, localized(lang), lang)
            } finally {
                _state.value = VoiceUiState()
                _busy.value = false
            }
        }
    }

    private fun language(): String = AppLanguages.current(app, app.prefs.language.value)

    private fun localized(tag: String): Context {
        if (tag.isEmpty()) return app
        val config = Configuration(app.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return app.createConfigurationContext(config)
    }

    private suspend fun session() {
        val lang = language()
        val res = localized(lang)
        val parserLang = VoiceLocales.parserLanguage(lang)

        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            say(res, lang, R.string.voice_mic_denied)
            return
        }
        if (!listener.isAvailable()) {
            // Neither a system recogniser nor the offline model: start the model download so
            // the next try works, and point at Settings › Voice › Check voice.
            app.wakeModel.ensure()
            say(res, lang, R.string.voice_unavailable)
            return
        }

        var expect = Expect.Nothing
        val now = System.currentTimeMillis()
        if (now - app.prefs.lastVoiceGreeting > GREETING_EVERY_MS) {
            app.prefs.lastVoiceGreeting = now
            styled(res, lang, HaryanviLines.Event.Greeting, greeting(res))
            expect = Expect.Destination
        }

        var misses = 0
        repeat(MAX_TURNS) {
            speaker.earcon()
            _state.update { it.copy(phase = VoiceUiState.Phase.Listening, heard = "") }
            val result = listener.listen(
                lang,
                onPartial = { partial -> _state.update { it.copy(heard = partial) } },
                // Offline fallback: command phrases only, except when a place name is expected.
                commandsOnly = expect != Expect.Destination,
            )
            val text = when (result) {
                Listener.Result.Unavailable -> {
                    say(res, lang, R.string.voice_unavailable)
                    return
                }
                Listener.Result.Silence -> {
                    misses++
                    if (misses >= 2 || expect == Expect.Nothing) {
                        // Nothing said after the wake word: usually a false wake, so stay quiet.
                        if (expect != Expect.Nothing) styled(res, lang, HaryanviLines.Event.Cancelled, res.getString(R.string.voice_cancelled))
                        return
                    }
                    styled(res, lang, HaryanviLines.Event.DidntCatch, res.getString(R.string.voice_didnt_catch))
                    return@repeat
                }
                is Listener.Result.Heard -> result.text
            }
            _state.update { it.copy(heard = text, phase = VoiceUiState.Phase.Working) }
            val intent = VoiceIntents.parse(text, parserLang, expectingChoice = expect == Expect.Choice)
            expect = handle(intent, text, expect, res, lang, parserLang) ?: return
        }
    }

    /** Acts on one utterance. Returns what to listen for next, or null when the session is done. */
    private suspend fun handle(
        intent: VoiceIntent,
        text: String,
        expect: Expect,
        res: Context,
        lang: String,
        parserLang: String,
    ): Expect? {
        when (intent) {
            is VoiceIntent.Navigate -> {
                if (intent.destination.isEmpty()) {
                    // Haryanvi desi: "Kit chalna sai?"
                    styled(res, lang, HaryanviLines.Event.AskDestination, res.getString(R.string.voice_where_to))
                    return Expect.Destination
                }
                return search(intent.destination, res, lang)
            }
            is VoiceIntent.ChooseOption -> {
                val place = _state.value.options.getOrNull(intent.index)
                if (place == null) {
                    say(res, lang, R.string.voice_say_number)
                    return Expect.Choice
                }
                navigateTo(place, res, lang)
                return null
            }
            is VoiceIntent.Unknown -> return when (expect) {
                Expect.Destination -> {
                    val dest = VoiceIntents.destinationFrom(text, parserLang)
                    if (dest.isEmpty()) null else search(dest, res, lang)
                }
                Expect.Choice -> {
                    // "Phoenix" instead of "the first one".
                    val spoken = VoiceIntents.normalize(text)
                    val hit = _state.value.options.indexOfFirst {
                        val name = VoiceIntents.normalize(it.name)
                        name.isNotEmpty() && (spoken.contains(name) || name.contains(spoken))
                    }
                    if (hit >= 0) {
                        navigateTo(_state.value.options[hit], res, lang)
                        null
                    } else {
                        say(res, lang, R.string.voice_say_number)
                        Expect.Choice
                    }
                }
                Expect.Nothing -> {
                    styled(res, lang, HaryanviLines.Event.NotUnderstood, res.getString(R.string.voice_not_understood))
                    null
                }
            }
            VoiceIntent.NextCamera -> {
                nextCamera(res, lang)
                return null
            }
            is VoiceIntent.PlayLocal -> {
                playLocal(intent.query, res, lang)
                return null
            }
            is VoiceIntent.PlayBluetooth -> {
                when (VoiceActions.playBluetooth(app, intent.query)) {
                    VoiceActions.BtResult.Playing -> say(res, lang, R.string.voice_bt_playing)
                    VoiceActions.BtResult.NoSession -> say(res, lang, R.string.voice_bt_start_phone)
                }
                return null
            }
            VoiceIntent.Resume -> VoiceActions.transport(app, VoiceActions.Transport.Play)
            VoiceIntent.Pause -> VoiceActions.transport(app, VoiceActions.Transport.Pause)
            VoiceIntent.Next -> VoiceActions.transport(app, VoiceActions.Transport.Next)
            VoiceIntent.Previous -> VoiceActions.transport(app, VoiceActions.Transport.Previous)
            VoiceIntent.OpenAndroidAuto -> {
                val label = VoiceActions.openPhoneLink(app, app.prefs.autoPackage.value)
                if (label != null) {
                    say(res, lang, res.getString(R.string.voice_auto_opening, label))
                } else {
                    say(res, lang, R.string.voice_auto_missing)
                }
                return null
            }
            is VoiceIntent.CallContact -> {
                Launchers.openDialer(app)
                if (intent.name.isNotEmpty()) {
                    say(res, lang, res.getString(R.string.voice_call, intent.name))
                } else {
                    say(res, lang, R.string.voice_call_any)
                }
                return null
            }
            VoiceIntent.Sos -> {
                // Opens the Help panel only. The SOS message is never sent without the driver.
                bringLauncherForward(MainActivity.VOICE_OPEN_HELP)
                say(res, lang, R.string.voice_sos)
                return null
            }
            VoiceIntent.ReportProblem -> {
                bringLauncherForward(MainActivity.VOICE_FEEDBACK)
                say(res, lang, R.string.voice_feedback)
                return null
            }
            VoiceIntent.Greeting -> {
                styled(res, lang, HaryanviLines.Event.Greeting, greeting(res))
                return Expect.Destination
            }
            VoiceIntent.ServiceDue -> {
                say(res, lang, CarAnswers.service(app, res))
                return null
            }
            VoiceIntent.TyrePressure -> {
                say(res, lang, CarAnswers.tyres(app, res))
                return null
            }
            VoiceIntent.EngineStatus -> {
                say(res, lang, CarAnswers.engine(app, res, VoiceLocales.parserLanguage(lang) == "hi"))
                return null
            }
            VoiceIntent.WhereParked -> {
                say(res, lang, CarAnswers.parked(app, res))
                return null
            }
            VoiceIntent.Cancel -> {
                styled(res, lang, HaryanviLines.Event.Cancelled, res.getString(R.string.voice_cancelled))
                return null
            }
        }
        return null
    }

    private suspend fun search(destination: String, res: Context, lang: String): Expect? {
        _state.update { it.copy(phase = VoiceUiState.Phase.Working, options = emptyList()) }
        val here = app.speedTracker.state.value.location
        // Search while Ampli says "Searching for…".
        val pending = scope.async { runCatching { PlaceSearch.search(destination, here) } }
        styled(res, lang, HaryanviLines.Event.Searching, res.getString(R.string.voice_searching, destination), destination)
        val places = pending.await().getOrElse {
            // No place search (offline?): Google Maps searches the name itself.
            if (VoiceActions.startNavigationByName(app, destination)) {
                styled(res, lang, HaryanviLines.Event.StartingNav, res.getString(R.string.voice_starting_nav, destination), destination)
            } else {
                say(res, lang, R.string.voice_search_failed)
            }
            return null
        }
        if (places.isEmpty()) {
            say(res, lang, res.getString(R.string.voice_no_places, destination))
            return Expect.Destination
        }
        if (places.size == 1) {
            say(res, lang, R.string.voice_found_one)
            navigateTo(places[0], res, lang)
            return null
        }
        _state.update { it.copy(options = places) }
        // Show the list on screen too (touch fallback), if the launcher is behind another app.
        bringLauncherForward(null)
        val locale = res.resources.configuration.locales[0]
        val speech = buildString {
            append(res.getString(R.string.voice_found_places, places.size))
            places.forEachIndexed { i, p ->
                append(' ')
                val name = listOfNotNull(p.name, p.area?.substringBefore(',')).joinToString(", ")
                if (p.distanceKm != null) {
                    append(res.getString(R.string.voice_option_km, i + 1, name, String.format(locale, "%.1f", p.distanceKm)))
                } else {
                    append(res.getString(R.string.voice_option, i + 1, name))
                }
            }
            append(' ')
            append(res.getString(R.string.voice_which_one))
        }
        say(res, lang, speech)
        return Expect.Choice
    }

    private suspend fun navigateTo(place: Place, res: Context, lang: String) {
        _state.update { it.copy(options = emptyList()) }
        styled(res, lang, HaryanviLines.Event.StartingNav, res.getString(R.string.voice_starting_nav, place.name), place.name)
        if (!VoiceActions.startNavigation(app, place)) say(res, lang, R.string.voice_nav_failed)
    }

    private suspend fun nextCamera(res: Context, lang: String) {
        val locale = res.resources.configuration.locales[0]
        fun answer(meters: Double, limit: Int?): String {
            val km = String.format(locale, "%.1f", meters / 1000.0)
            return if (limit != null) {
                res.getString(R.string.voice_camera_next_ahead, km, limit)
            } else {
                res.getString(R.string.voice_camera_next_ahead_nolimit, km)
            }
        }
        // A live alert on screen is the best answer; otherwise search 20 km ahead in the database.
        app.cameraAlerts.current.value?.let { alert ->
            say(res, lang, answer(alert.distanceMeters.toDouble(), alert.limitKmh))
            return
        }
        val gps = app.speedTracker.state.value
        val here = gps.location
        if (here == null) {
            say(res, lang, R.string.voice_no_location)
            return
        }
        cameras.ensureLoaded()
        val moving = here.hasBearing() && (gps.speedKmh ?: 0f) >= 10f
        val ahead = cameras.nextCamera(here, if (moving) here.bearing else null)
        if (ahead == null) {
            say(res, lang, R.string.voice_camera_none_ahead)
            return
        }
        say(res, lang, answer(ahead.distanceMeters, ahead.camera.maxspeed))
    }

    private suspend fun playLocal(query: String, res: Context, lang: String) {
        if (VoiceActions.hasAudioPermission(app)) {
            val song = VoiceActions.findSong(app, query)
            if (song != null) {
                say(res, lang, res.getString(R.string.voice_playing, song.title))
                VoiceActions.playSong(app, song)
                return
            }
        }
        if (query.isNotEmpty() &&
            (VoiceActions.playFromSearchSession(app, query) || VoiceActions.playFromSearchIntent(app, query))
        ) {
            say(res, lang, res.getString(R.string.voice_playing, query))
            return
        }
        if (!VoiceActions.hasAudioPermission(app)) {
            say(res, lang, R.string.voice_music_permission)
        } else {
            say(res, lang, res.getString(R.string.voice_song_not_found, query))
        }
    }

    private fun greeting(res: Context): String {
        val hour = LocalTime.now().hour
        val hello = res.getString(
            when {
                hour in 4..11 -> R.string.voice_greet_morning
                hour in 12..16 -> R.string.voice_greet_afternoon
                else -> R.string.voice_greet_evening
            },
        )
        return hello + " " + res.getString(R.string.voice_greet_question)
    }

    private suspend fun say(res: Context, lang: String, @StringRes id: Int) = say(res, lang, res.getString(id))

    /**
     * The Haryanvi desi line for [event] when that voice style is on, else [standard]. Safety
     * replies (SOS) never come through here, so they always stay plain and clear.
     */
    private suspend fun styled(res: Context, lang: String, event: HaryanviLines.Event, standard: String, vararg args: Any) {
        val desi = app.desi.line(event, *args)
        if (desi == null) {
            say(res, lang, standard)
            return
        }
        _state.update { it.copy(phase = VoiceUiState.Phase.Speaking, reply = desi.text) }
        speaker.say(desi.text, desi.language)
    }

    private suspend fun say(@Suppress("UNUSED_PARAMETER") res: Context, lang: String, text: String) {
        _state.update { it.copy(phase = VoiceUiState.Phase.Speaking, reply = text) }
        speaker.say(text, lang)
    }

    /**
     * Brings the launcher to the front, for the place list or the Help panel. Android lets the
     * default home app start activities from the background; without that role it may be ignored.
     */
    private fun bringLauncherForward(action: String?) {
        try {
            val intent = Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            if (action != null) intent.putExtra(MainActivity.EXTRA_VOICE_ACTION, action)
            app.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val MAX_TURNS = 4
        /** The full greeting at most every three hours; otherwise just the listening beep. */
        const val GREETING_EVERY_MS = 3L * 60 * 60 * 1000
    }
}
