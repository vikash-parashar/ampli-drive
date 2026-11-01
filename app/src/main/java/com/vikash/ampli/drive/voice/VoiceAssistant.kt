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
import com.vikash.ampli.drive.nav.SavedKind
import com.vikash.ampli.drive.prefs.NavApp
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.system.PowerAction
import com.vikash.ampli.drive.system.PowerControl
import com.vikash.ampli.drive.system.PowerPlan
import com.vikash.ampli.drive.system.PowerUi
import com.vikash.ampli.drive.calls.VoiceReplies
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
    /** A big answer card: the time, the weather, the volume… */
    val answer: VoiceAnswer? = null,
) {
    enum class Phase { Idle, Listening, Working, Speaking }
}

/** What the answer card shows. [value] is the big line ("5:20 pm", "31°"). */
data class VoiceAnswer(
    val kind: Kind,
    val title: String,
    val value: String,
    val detail: String = "",
    /** Small print: the weather source credit, "say more" hints. */
    val note: String = "",
    val sky: com.vikash.ampli.drive.weather.Sky? = null,
) {
    enum class Kind { Time, Date, Weather, Volume, Speed, Brightness, Info, Problem, Fuel, Charging, Report }
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

    /**
     * What the next turn listens for. [Open]: right after a greeting, a command or a place name
     * (offline, commands are tried first and "where to?" is asked only when none is heard);
     * [Destination]: a place name after "where to?" (commands still win when one is said).
     */
    private enum class Expect { Nothing, Open, Destination, Choice, MoreVolume }

    /** The last volume change, for a follow-up "aur" / "more". */
    private var lastVolume = VoiceIntent.VolumeAction.Show

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
                app.voicePlayer.stop()
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

    /**
     * One short spoken answer outside a Hey Ampli session (the call and message cards: "हाँ / ना",
     * or a dictated reply). The wake word lets go of the mic meanwhile, a beep
     * says "speak now", and the recogniser listens once. Null when nothing was heard, a session
     * is running, the microphone isn't allowed or there is no recogniser.
     */
    suspend fun listenOnce(): String? {
        if (job?.isActive == true) return null
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return null
        if (!listener.isAvailable()) return null
        return withMic {
            speaker.earcon()
            when (val r = listener.listen(language(), onPartial = {}, commandsOnly = false)) {
                is Listener.Result.Heard -> r.text
                else -> null
            }
        }
    }

    /** True when [listenOnce] can work: a recogniser (or the offline model) and the mic. */
    fun canListen(): Boolean =
        app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && listener.isAvailable()

    /** Stops a [listenOnce] (the card was tapped or the call ended). */
    fun cancelListen() {
        if (job?.isActive != true) listener.cancel()
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
            app.wakeModel.ensure(automatic = true)
            VoskCommandListener.ensureHindiModel(app)
            say(res, lang, R.string.voice_unavailable)
            return
        }
        // Hindi-family language without a working system recogniser: fetch the Hindi model.
        VoskCommandListener.ensureHindiModel(app)

        var expect = Expect.Nothing
        val now = System.currentTimeMillis()
        if (now - app.prefs.lastVoiceGreeting > GREETING_EVERY_MS) {
            app.prefs.lastVoiceGreeting = now
            greet(res, lang)
            expect = Expect.Open
        } else {
            // Haryanvi desi between greetings: a short "हाँ भाई, बोल।" (Standard: just the beep).
            app.desi.line(HaryanviLines.Event.WakeReply)?.let { speakDesi(it) }
        }

        var misses = 0
        repeat(MAX_TURNS) {
            speaker.earcon()
            _state.update { it.copy(phase = VoiceUiState.Phase.Listening, heard = "") }
            val onPartial = { partial: String -> _state.update { it.copy(heard = partial) } }
            var result = listener.listen(
                lang,
                onPartial = onPartial,
                // Offline fallback: command phrases only, except right after "where to?". After
                // the greeting the offline model hears commands first ("gaana chalao" must not
                // become a place search); a place name is asked for next.
                commandsOnly = expect != Expect.Destination,
            )
            if (listener.lastEngine.offline) VoskCommandListener.ensureHindiModel(app)
            if (expect == Expect.Open && listener.lastEngine.offline &&
                RecognizerFallback.offlineNeedsWhereTo((result as? Listener.Result.Heard)?.text, parserLang)
            ) {
                // No command heard offline: ask for the place and transcribe it freely.
                styled(res, lang, HaryanviLines.Event.AskDestination, res.getString(R.string.voice_where_to))
                expect = Expect.Destination
                speaker.earcon()
                _state.update { it.copy(phase = VoiceUiState.Phase.Listening, heard = "") }
                result = listener.listen(lang, onPartial = onPartial, commandsOnly = false)
            }
            val text = when (result) {
                Listener.Result.Unavailable -> {
                    say(res, lang, R.string.voice_unavailable)
                    return
                }
                Listener.Result.Silence -> {
                    misses++
                    // After a volume change, silence just means "that's enough".
                    if (expect == Expect.MoreVolume) return
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
            // A new question clears the last answer card; "aur" after a volume change keeps it.
            if (intent !is VoiceIntent.Volume && expect != Expect.MoreVolume) _state.update { it.copy(answer = null) }
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
            is VoiceIntent.GoSaved -> {
                goSaved(intent.kind, res, lang)
                return null
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
                Expect.Open, Expect.Destination -> {
                    val dest = VoiceIntents.destinationFrom(text, parserLang)
                    val saved = VoiceIntents.savedKind(dest)
                    when {
                        dest.isEmpty() -> null
                        saved != null -> {
                            goSaved(saved, res, lang)
                            null
                        }
                        else -> search(dest, res, lang)
                    }
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
                Expect.MoreVolume -> VoiceIntents.volumeFollowUp(text, lastVolume)?.let { volume(it, null, res, lang) }
            }
            VoiceIntent.NextCamera -> {
                nextCamera(res, lang)
                return null
            }
            is VoiceIntent.FindFuel -> return findFuel(intent.filter, res, lang)
            is VoiceIntent.ReportRoad -> {
                reportRoad(intent.kind, res, lang)
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
            // Haryanvi desi says a short "ले, गाणा बजा दिया।"; the Standard style stays silent as before.
            VoiceIntent.Resume -> transport(VoiceActions.Transport.Play, HaryanviLines.Event.MusicPlay)
            VoiceIntent.Pause -> transport(VoiceActions.Transport.Pause, HaryanviLines.Event.MusicPause)
            VoiceIntent.Next -> transport(VoiceActions.Transport.Next, HaryanviLines.Event.MusicNext)
            VoiceIntent.Previous -> transport(VoiceActions.Transport.Previous, HaryanviLines.Event.MusicPrevious)
            VoiceIntent.OpenAndroidAuto -> {
                // Car Hub's own Phone Link sheet, unless the driver picked a screen-mirroring app.
                val pinned = app.prefs.autoPackage.value
                val label = if (pinned != null) VoiceActions.openPhoneLink(app, pinned) else null
                if (pinned == null) {
                    app.phoneBt.requestSheet()
                    try {
                        app.startActivity(
                            android.content.Intent(app, com.vikash.ampli.drive.MainActivity::class.java)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                        )
                    } catch (_: Exception) {
                    }
                    styled(res, lang, HaryanviLines.Event.CommandDone, res.getString(R.string.voice_auto_opening, res.getString(R.string.plink_title)))
                } else if (label != null) {
                    // Haryanvi desi: "ठीक सै, कर दिया।"
                    styled(res, lang, HaryanviLines.Event.CommandDone, res.getString(R.string.voice_auto_opening, label))
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
                greet(res, lang)
                return Expect.Open
            }
            is VoiceIntent.SmallTalk -> return smallTalk(intent.kind, res, lang)
            VoiceIntent.DashcamSave -> {
                // Pro dashcam: lock the last clip. Says why when it isn't recording.
                val saved = app.dashcam.saveNow(byVoice = true)
                if (saved) {
                    styled(res, lang, HaryanviLines.Event.DashcamSaved, res.getString(R.string.dashcam_voice_saved))
                } else {
                    say(res, lang, R.string.dashcam_voice_not_recording)
                }
                return null
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
                // Haryanvi desi: "रुक, देखूं गाड्डी कित खड़ी सै।" before the plain answer.
                app.desi.line(HaryanviLines.Event.WhereParked)?.let { speakDesi(it) }
                say(res, lang, CarAnswers.parked(app, res))
                return null
            }
            VoiceIntent.Cancel -> {
                // "Radd karo" also stops a power countdown that is still running.
                if (PowerControl.busy) PowerControl.close()
                styled(res, lang, HaryanviLines.Event.Cancelled, res.getString(R.string.voice_cancelled))
                return null
            }
            is VoiceIntent.Power -> {
                power(intent.action, res, lang, parserLang)
                return null
            }
            VoiceIntent.Stop -> {
                // "Stop" / "ruko" / "band karo": the music if any is playing, else the conversation.
                if (VoiceActions.isPlaying(app)) {
                    transport(VoiceActions.Transport.Pause, HaryanviLines.Event.MusicPause)
                } else {
                    styled(res, lang, HaryanviLines.Event.Cancelled, res.getString(R.string.voice_cancelled))
                }
                return null
            }
            is VoiceIntent.Volume -> return volume(intent.action, intent.level, res, lang)
            VoiceIntent.TimeNow -> {
                timeAnswer(res, lang)
                return null
            }
            VoiceIntent.DateToday -> {
                dateAnswer(res, lang)
                return null
            }
            is VoiceIntent.Weather -> {
                weatherAnswer(intent.rain, res, lang)
                return null
            }
            VoiceIntent.Speed -> {
                speedAnswer(res, lang)
                return null
            }
            VoiceIntent.StopNavigation -> {
                stopNavigation(res, lang)
                return null
            }
            VoiceIntent.OpenMaps -> {
                if (app.prefs.navApp.value == NavApp.Ampli) {
                    bringLauncherForward(MainActivity.VOICE_MAPS)
                } else {
                    Launchers.openNavigation(app, app.speedTracker.state.value.location)
                }
                styled(res, lang, HaryanviLines.Event.CommandDone, res.getString(R.string.voice_maps_opening))
                return null
            }
            is VoiceIntent.Brightness -> {
                brightness(intent.up, res, lang)
                return null
            }
            VoiceIntent.BluetoothSettings -> {
                app.phoneBt.openBluetoothSettings(app)
                styled(res, lang, HaryanviLines.Event.CommandDone, res.getString(R.string.voice_bt_settings_opening))
                return null
            }
            VoiceIntent.WhatCanISay -> {
                bringLauncherForward(MainActivity.VOICE_COMMANDS)
                styled(res, lang, HaryanviLines.Event.CommandDone, res.getString(R.string.voice_commands_opening))
                return null
            }
        }
        return null
    }

    // ------------------------------------------------------------------- Round 7 answers

    /**
     * Shows [a] on the answer card. When Car Hub is not in front (Google Maps on screen) the card
     * can't be seen, so the answer is also toasted, without covering the map.
     */
    private fun showAnswer(a: VoiceAnswer) {
        _state.update { it.copy(answer = a, options = emptyList()) }
        if (!MainActivity.inFront) {
            val line = listOf(a.title, a.value, a.detail).filter { it.isNotBlank() }.joinToString(" · ")
            try {
                android.widget.Toast.makeText(app, line, android.widget.Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
            }
        }
    }

    /** Keeps the card up long enough to read once the voice is done. */
    private suspend fun holdAnswer(shownAt: Long) {
        val left = ANSWER_HOLD_MS - (System.currentTimeMillis() - shownAt)
        if (left > 0) kotlinx.coroutines.delay(left)
    }

    /**
     * Says an answer. The recorded pieces ([clips], see [AnswerClips]) in the Haryanvi style or
     * when the unit has no TTS; else the phone voice reads [text]. No TTS and the pieces not
     * recorded yet: the short recorded acknowledgement of [standIn] plays (the card has the answer).
     */
    private suspend fun speakAnswer(text: String, lang: String, clips: List<String>, standIn: HaryanviLines.Event? = HaryanviLines.Event.CommandDone) {
        _state.update { it.copy(phase = VoiceUiState.Phase.Speaking, reply = text) }
        val tts = app.voicePlayer.ttsAvailable()
        val spoken = Spoken(text, lang, clips)
        when {
            clips.isNotEmpty() && (app.desi.enabled || !tts) && app.voicePlayer.hasRecorded(spoken) -> app.voicePlayer.say(spoken)
            tts -> speaker.say(text, lang)
            standIn != null -> app.desi.line(standIn, force = true)?.let { app.voicePlayer.say(it) }
            else -> kotlinx.coroutines.delay(1_500L)
        }
    }

    /** Locale for dates on the card; Haryanvi, Bhojpuri and Maithili read Hindi dates. */
    private fun dateLocale(res: Context): Locale {
        val l = res.resources.configuration.locales[0]
        return if (l.language in setOf("bgc", "bho", "mai")) Locale.forLanguageTag("hi-IN") else l
    }

    private suspend fun timeAnswer(res: Context, lang: String) {
        val shownAt = System.currentTimeMillis()
        val now = java.time.LocalDateTime.now()
        val time = android.text.format.DateFormat.getTimeFormat(res).format(java.util.Date())
        val date = now.toLocalDate().format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(dateLocale(res)))
        showAnswer(VoiceAnswer(VoiceAnswer.Kind.Time, res.getString(R.string.voice_answer_time), time, date))
        speakAnswer(res.getString(R.string.voice_time_is, time), lang, AnswerClips.time(now.hour, now.minute, app.desi.enabled))
        holdAnswer(shownAt)
    }

    private suspend fun dateAnswer(res: Context, lang: String) {
        val shownAt = System.currentTimeMillis()
        val today = java.time.LocalDate.now()
        val locale = dateLocale(res)
        val big = today.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", locale))
        val full = today.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(locale))
        showAnswer(VoiceAnswer(VoiceAnswer.Kind.Date, res.getString(R.string.voice_answer_date), big, full))
        speakAnswer(
            res.getString(R.string.voice_date_is, full),
            lang,
            AnswerClips.date(today.dayOfWeek.value, today.dayOfMonth, today.monthValue, app.desi.enabled),
        )
        holdAnswer(shownAt)
    }

    private fun skyName(res: Context, sky: com.vikash.ampli.drive.weather.Sky): String = res.getString(
        when (sky) {
            com.vikash.ampli.drive.weather.Sky.Clear -> R.string.wx_clear
            com.vikash.ampli.drive.weather.Sky.PartlyCloudy -> R.string.wx_partly_cloudy
            com.vikash.ampli.drive.weather.Sky.Cloudy -> R.string.wx_cloudy
            com.vikash.ampli.drive.weather.Sky.Rain -> R.string.wx_rain
            com.vikash.ampli.drive.weather.Sky.HeavyRain -> R.string.wx_heavy_rain
            com.vikash.ampli.drive.weather.Sky.Thunder -> R.string.wx_thunder
            com.vikash.ampli.drive.weather.Sky.Fog -> R.string.wx_fog
            com.vikash.ampli.drive.weather.Sky.Snow -> R.string.wx_snow
        },
    )

    private suspend fun weatherAnswer(rainQuestion: Boolean, res: Context, lang: String) {
        val shownAt = System.currentTimeMillis()
        val desiOn = app.desi.enabled
        val title = res.getString(R.string.voice_answer_weather)
        val here = app.speedTracker.state.value.location
        if (here == null) {
            showAnswer(VoiceAnswer(VoiceAnswer.Kind.Problem, title, "—", res.getString(R.string.voice_no_location)))
            speakAnswer(res.getString(R.string.voice_no_location), lang, AnswerClips.line("no_location", desiOn), standIn = null)
            holdAnswer(shownAt)
            return
        }
        _state.update { it.copy(phase = VoiceUiState.Phase.Working, reply = res.getString(R.string.voice_weather_checking)) }
        when (val r = app.nowWeather.get(here.latitude, here.longitude)) {
            is com.vikash.ampli.drive.weather.NowWeatherRepository.Result.Ok -> {
                val w = r.weather
                val sky = skyName(res, w.sky)
                val temp = w.temperatureC?.let { kotlin.math.round(it).toInt() }
                val rainLine = res.getString(if (w.rainLikely) R.string.voice_weather_rain_yes else R.string.voice_weather_rain_no)
                val detail = buildList {
                    add(sky)
                    w.rainChancePct?.let { add(res.getString(R.string.weather_rain_chance, it)) }
                    add(rainLine)
                }.joinToString(" · ")
                val source = res.getString(
                    if (w.source == com.vikash.ampli.drive.weather.WeatherSource.MetNorway) R.string.weather_source_met else R.string.weather_source_openmeteo,
                )
                val note = if (r.stale) res.getString(R.string.weather_stale) + " · " + source else source
                showAnswer(VoiceAnswer(VoiceAnswer.Kind.Weather, title, temp?.let { "$it°C" } ?: sky, detail, note, w.sky))
                val now = if (temp != null) res.getString(R.string.voice_weather_now, sky, temp) else "$sky."
                val text = if (rainQuestion) "$rainLine $now" else "$now $rainLine"
                val standIn = when {
                    w.sky == com.vikash.ampli.drive.weather.Sky.Thunder -> HaryanviLines.Event.WeatherThunder
                    w.sky == com.vikash.ampli.drive.weather.Sky.Fog -> HaryanviLines.Event.WeatherFog
                    w.rainLikely -> HaryanviLines.Event.WeatherRain
                    (w.temperatureC ?: 0.0) >= com.vikash.ampli.drive.weather.RouteWeather.HEAT_C -> HaryanviLines.Event.WeatherHeat
                    else -> HaryanviLines.Event.CommandDone
                }
                speakAnswer(text, lang, AnswerClips.weather(w.sky, w.temperatureC, w.rainLikely, rainQuestion, desiOn), standIn)
            }
            com.vikash.ampli.drive.weather.NowWeatherRepository.Result.Offline -> {
                showAnswer(VoiceAnswer(VoiceAnswer.Kind.Problem, title, "—", res.getString(R.string.voice_weather_offline)))
                speakAnswer(res.getString(R.string.voice_weather_offline), lang, AnswerClips.line("wx_offline", desiOn), standIn = null)
            }
            com.vikash.ampli.drive.weather.NowWeatherRepository.Result.Failed -> {
                showAnswer(VoiceAnswer(VoiceAnswer.Kind.Problem, title, "—", res.getString(R.string.voice_weather_failed)))
                speakAnswer(res.getString(R.string.voice_weather_failed), lang, AnswerClips.line("wx_failed", desiOn), standIn = null)
            }
        }
        holdAnswer(shownAt)
    }

    private suspend fun speedAnswer(res: Context, lang: String) {
        val shownAt = System.currentTimeMillis()
        val title = res.getString(R.string.voice_answer_speed)
        val kmh = app.speedTracker.state.value.speedKmh?.let { kotlin.math.round(it).toInt() }
        if (kmh == null) {
            showAnswer(VoiceAnswer(VoiceAnswer.Kind.Problem, title, "—", res.getString(R.string.voice_no_location)))
            speakAnswer(res.getString(R.string.voice_no_location), lang, AnswerClips.line("no_location", app.desi.enabled), standIn = null)
        } else {
            showAnswer(VoiceAnswer(VoiceAnswer.Kind.Speed, title, res.getString(R.string.voice_speed_value, kmh)))
            speakAnswer(res.getString(R.string.voice_speed_is, kmh), lang, AnswerClips.speed(kmh, app.desi.enabled))
        }
        holdAnswer(shownAt)
    }

    /** Volume by voice. Up, down and a bare "volume" wait for "aur" / "more" once more. */
    private suspend fun volume(action: VoiceIntent.VolumeAction, level: Int?, res: Context, lang: String): Expect? {
        val shownAt = System.currentTimeMillis()
        val title = res.getString(R.string.voice_answer_volume)
        val r = com.vikash.ampli.drive.media.VoiceVolume.apply(app.getSystemService(android.media.AudioManager::class.java), action, level)
        if (r == null) {
            showAnswer(VoiceAnswer(VoiceAnswer.Kind.Problem, title, "—", res.getString(R.string.voice_volume_failed)))
            speakAnswer(res.getString(R.string.voice_volume_failed), lang, emptyList(), standIn = null)
            holdAnswer(shownAt)
            return null
        }
        lastVolume = action
        val tenths = com.vikash.ampli.drive.media.VolumeMath.tenths(r.level, r.max)
        val more = action == VoiceIntent.VolumeAction.Up || action == VoiceIntent.VolumeAction.Down || action == VoiceIntent.VolumeAction.Show
        val value = if (r.muted) res.getString(R.string.voice_volume_muted) else res.getString(R.string.voice_volume_value, tenths)
        showAnswer(
            VoiceAnswer(
                VoiceAnswer.Kind.Volume,
                title,
                value,
                res.getString(R.string.voice_volume_steps, r.level, r.max),
                if (more) res.getString(R.string.voice_volume_more_hint) else "",
            ),
        )
        val clip = when (action) {
            VoiceIntent.VolumeAction.Up -> "vol_up"
            VoiceIntent.VolumeAction.Down -> "vol_down"
            VoiceIntent.VolumeAction.Max -> "vol_max"
            VoiceIntent.VolumeAction.Unmute -> "vol_unmute"
            else -> null
        }
        // Muted: nothing would be heard, the card and Android's volume panel say it.
        if (action != VoiceIntent.VolumeAction.Mute && action != VoiceIntent.VolumeAction.Show) {
            speakAnswer(value, lang, clip?.let { AnswerClips.line(it, app.desi.enabled) }.orEmpty())
        }
        if (more) return Expect.MoreVolume
        holdAnswer(shownAt)
        return null
    }

    private suspend fun stopNavigation(res: Context, lang: String) {
        if (app.navigator.state.value.visible) {
            app.navigator.stop()
            speakAnswer(res.getString(R.string.voice_nav_stopped), lang, AnswerClips.line("nav_stopped", app.desi.enabled))
            return
        }
        // Google Maps' guidance can't be ended from outside; say how.
        val text = res.getString(R.string.voice_nav_stop_maps)
        showAnswer(VoiceAnswer(VoiceAnswer.Kind.Info, res.getString(R.string.voice_answer_navigation), "", text))
        speakAnswer(text, lang, emptyList(), standIn = null)
        holdAnswer(System.currentTimeMillis() - ANSWER_HOLD_MS / 2)
    }

    /** "Brightness up / down": the system setting, once Car Hub may change it. */
    private suspend fun brightness(up: Boolean?, res: Context, lang: String) {
        val shownAt = System.currentTimeMillis()
        val title = res.getString(R.string.voice_answer_brightness)
        val r = com.vikash.ampli.drive.system.ScreenBrightness.change(app, up)
        if (r == null) {
            com.vikash.ampli.drive.system.ScreenBrightness.openPermission(app)
            showAnswer(VoiceAnswer(VoiceAnswer.Kind.Problem, title, "—", res.getString(R.string.voice_brightness_permission)))
            speakAnswer(res.getString(R.string.voice_brightness_permission), lang, emptyList(), standIn = null)
        } else {
            val value = res.getString(R.string.voice_brightness_value, r)
            showAnswer(VoiceAnswer(VoiceAnswer.Kind.Brightness, title, value))
            if (up != null) speakAnswer(value, lang, emptyList())
        }
        holdAnswer(shownAt)
    }

    /**
     * "Ghar chalo" / "office le chalo": straight to the saved place, no search. Not saved yet: the
     * answer is shown and toasted too, since many units have no speech engine to say it.
     */
    private suspend fun goSaved(kind: SavedKind, res: Context, lang: String) {
        val label = res.getString(if (kind == SavedKind.Office) R.string.place_office else R.string.place_home)
        val saved = app.places.get(kind)
        if (saved == null) {
            val text = res.getString(R.string.place_not_set, label)
            android.widget.Toast.makeText(app, text, android.widget.Toast.LENGTH_LONG).show()
            say(res, lang, text)
            return
        }
        navigateTo(saved.toPlace(label), res, lang)
    }

    private suspend fun search(destination: String, res: Context, lang: String): Expect? {
        _state.update { it.copy(phase = VoiceUiState.Phase.Working, options = emptyList()) }
        val here = app.speedTracker.state.value.location
        // Search while Ampli says "Searching for…".
        val pending = scope.async { runCatching { PlaceSearch.search(destination, here) } }
        styled(res, lang, HaryanviLines.Event.Searching, res.getString(R.string.voice_searching, destination), destination)
        val places = pending.await().getOrElse {
            if (app.prefs.navApp.value == NavApp.Ampli) {
                // Ampli map: show why on the navigation screen, with Try again and Google Maps.
                app.navigator.failSearch(destination, network = true)
                bringLauncherForward(MainActivity.VOICE_NAVIGATE)
                say(res, lang, R.string.voice_search_failed)
                return null
            }
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
        if (app.prefs.navApp.value == NavApp.Ampli) {
            // Car Hub's own map (Settings › Navigate with): the route loads while Ampli talks.
            app.navigator.start(place)
            bringLauncherForward(MainActivity.VOICE_NAVIGATE)
            styled(res, lang, HaryanviLines.Event.StartingNav, res.getString(R.string.voice_starting_nav, place.name), place.name)
            return
        }
        styled(res, lang, HaryanviLines.Event.StartingNav, res.getString(R.string.voice_starting_nav, place.name), place.name)
        if (!VoiceActions.startNavigation(app, place)) say(res, lang, R.string.voice_nav_failed)
    }

    // ------------------------------------------------------------------- Fuel finder and reports

    /**
     * "Nearest CNG pump": the answer card with the nearest station, the three nearest as a list
     * to pick by voice or tap ("pehla wala" starts the route), and the fuel screen behind it.
     */
    private suspend fun findFuel(filter: com.vikash.ampli.drive.fuel.FuelFilter, res: Context, lang: String): Expect? {
        val shownAt = System.currentTimeMillis()
        val desiOn = app.desi.enabled
        val ev = filter == com.vikash.ampli.drive.fuel.FuelFilter.Ev
        val kind = if (ev) VoiceAnswer.Kind.Charging else VoiceAnswer.Kind.Fuel
        val title = res.getString(R.string.voice_fuel_title) + if (filter != com.vikash.ampli.drive.fuel.FuelFilter.All) " · " + res.getString(fuelLabel(filter)) else ""
        val here = app.speedTracker.state.value.location
        if (here == null) {
            showAnswer(VoiceAnswer(VoiceAnswer.Kind.Problem, title, "—", res.getString(R.string.voice_no_location)))
            speakAnswer(res.getString(R.string.voice_no_location), lang, AnswerClips.line("no_location", desiOn), standIn = null)
            holdAnswer(shownAt)
            return null
        }
        _state.update { it.copy(phase = VoiceUiState.Phase.Working) }
        val hits = app.fuel.nearby(here.latitude, here.longitude, filter, limit = 3)
        // The fuel screen opens behind the card on the asked fuel.
        app.fuel.requestedFilter.value = filter
        bringLauncherForward(MainActivity.VOICE_FUEL)
        val picker = com.vikash.ampli.drive.voice.LinePicker()
        val first = hits.firstOrNull()
        if (first == null) {
            showAnswer(VoiceAnswer(VoiceAnswer.Kind.Problem, title, "—", res.getString(R.string.voice_fuel_none)))
            speakAnswer(
                res.getString(R.string.voice_fuel_none), lang,
                com.vikash.ampli.drive.fuel.FuelVoice.clips(filter, null) { picker.pickIndexed(it) },
                standIn = HaryanviLines.Event.FuelNone,
            )
            holdAnswer(shownAt)
            return null
        }
        val locale = res.resources.configuration.locales[0]
        val km = String.format(locale, "%.1f", first.distanceM / 1000.0)
        val name = com.vikash.ampli.drive.ui.stationName(res, first.station)
        val minutes = com.vikash.ampli.drive.fuel.FuelFinder.etaMinutes(first.distanceM)
        showAnswer(VoiceAnswer(kind, title, res.getString(R.string.fuel_km, km), res.getString(R.string.voice_fuel_detail, name, minutes)))
        // Tap or say "the first one" to go.
        _state.update { s ->
            s.copy(options = hits.map { h -> Place(com.vikash.ampli.drive.ui.stationName(res, h.station), null, h.station.lat, h.station.lon, h.distanceM / 1000.0) })
        }
        speakAnswer(
            res.getString(R.string.voice_fuel_answer, name, km), lang,
            com.vikash.ampli.drive.fuel.FuelVoice.clips(filter, first.distanceM) { picker.pickIndexed(it) },
            standIn = com.vikash.ampli.drive.fuel.FuelVoice.foundEvent(filter),
        )
        return Expect.Choice
    }

    private fun fuelLabel(f: com.vikash.ampli.drive.fuel.FuelFilter): Int = when (f) {
        com.vikash.ampli.drive.fuel.FuelFilter.All -> R.string.fuel_filter_all
        com.vikash.ampli.drive.fuel.FuelFilter.Cng -> R.string.fuel_filter_cng
        com.vikash.ampli.drive.fuel.FuelFilter.Petrol -> R.string.fuel_filter_petrol
        com.vikash.ampli.drive.fuel.FuelFilter.Diesel -> R.string.fuel_filter_diesel
        com.vikash.ampli.drive.fuel.FuelFilter.Ev -> R.string.fuel_filter_ev
    }

    /** "Police aage hai": saves a road report here (Pro), for two hours, on this unit only. */
    private suspend fun reportRoad(kind: com.vikash.ampli.drive.cameras.ReportKind, res: Context, lang: String) {
        val shownAt = System.currentTimeMillis()
        val title = res.getString(R.string.voice_report_title)
        val label = res.getString(
            when (kind) {
                com.vikash.ampli.drive.cameras.ReportKind.MobileCamera -> R.string.radar_item_mobile
                com.vikash.ampli.drive.cameras.ReportKind.Police -> R.string.radar_item_police
                com.vikash.ampli.drive.cameras.ReportKind.Accident -> R.string.radar_item_accident
                com.vikash.ampli.drive.cameras.ReportKind.Jam -> R.string.radar_item_jam
            },
        )
        if (!com.vikash.ampli.drive.cameras.RadarTier.allowed(com.vikash.ampli.drive.cameras.RadarFeature.Reports, app.prefs.pro.value.isPro)) {
            showAnswer(VoiceAnswer(VoiceAnswer.Kind.Report, title, label, res.getString(R.string.voice_report_pro)))
            speakAnswer(res.getString(R.string.voice_report_pro), lang, emptyList(), standIn = null)
            holdAnswer(shownAt)
            return
        }
        val gps = app.speedTracker.state.value
        val here = gps.location
        if (here == null) {
            showAnswer(VoiceAnswer(VoiceAnswer.Kind.Problem, title, label, res.getString(R.string.voice_no_location)))
            speakAnswer(res.getString(R.string.voice_no_location), lang, AnswerClips.line("no_location", app.desi.enabled), standIn = null)
            holdAnswer(shownAt)
            return
        }
        app.roadReports.add(kind, here, gps.speedKmh)
        val text = res.getString(R.string.voice_report_saved, label)
        showAnswer(VoiceAnswer(VoiceAnswer.Kind.Report, title, label, text))
        styled(res, lang, HaryanviLines.Event.ReportSaved, text)
        holdAnswer(shownAt)
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

    /**
     * "Play <song>" / "<song> bajao". Ampli MP3 Player installed: the exact song when this unit
     * has it (MediaStore), else Ampli's own search when it declares one, else its Car mode with
     * the spoken name in the "query" extra (a build that doesn't know it just autoplays). The
     * phone over Bluetooth only searches when its session says it can (most can't over AVRCP).
     */
    private suspend fun playLocal(query: String, res: Context, lang: String) {
        if (query.isEmpty()) {
            transport(VoiceActions.Transport.Play, HaryanviLines.Event.MusicPlay)
            return
        }
        if (VoiceActions.phoneSearch(app, query)) {
            styled(res, lang, HaryanviLines.Event.MusicPlayNamed, res.getString(R.string.voice_playing, query), query)
            return
        }
        val song = if (VoiceActions.hasAudioPermission(app)) VoiceActions.findSong(app, query) else null
        if (com.vikash.ampli.drive.system.AmpliMusic.installed(app)) {
            val title = song?.title ?: query
            val ok = (song != null && VoiceActions.playSong(app, song)) ||
                VoiceActions.ampliPlayFromSearch(app, query) ||
                com.vikash.ampli.drive.system.AmpliMusic.open(app, autoplay = true, query = query)
            if (ok) {
                styled(res, lang, HaryanviLines.Event.MusicPlayNamed, res.getString(R.string.voice_playing, title), title)
                return
            }
        }
        if (song != null) {
            styled(res, lang, HaryanviLines.Event.MusicPlayNamed, res.getString(R.string.voice_playing, song.title), song.title)
            VoiceActions.playSong(app, song)
            return
        }
        if (VoiceActions.playFromSearchSession(app, query) || VoiceActions.playFromSearchIntent(app, query)) {
            styled(res, lang, HaryanviLines.Event.MusicPlayNamed, res.getString(R.string.voice_playing, query), query)
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

    /**
     * The session greeting. Haryanvi desi: a greeting line and a time-of-day line ("जय बाबा की भाई!
     * कित चालणा सै आज? सवेरे सवेरे कित चाल पड़े ताऊ?"). Standard: good morning / evening.
     */
    private suspend fun greet(res: Context, lang: String) {
        val desi = app.desi.line(HaryanviLines.Event.Greeting)
        if (desi == null) {
            say(res, lang, greeting(res))
            return
        }
        val time = app.desi.line(HaryanviLines.timeOfDay(LocalTime.now().hour))
        speakDesi(if (time == null) desi else desi + time)
    }

    /**
     * "के हाल सै?", "कौन सै तू?", "चुटकला सुणा". Haryanvi desi lines; the Standard style answers
     * plainly (how are you gets the usual greeting, as before).
     */
    private suspend fun smallTalk(kind: VoiceIntent.Talk, res: Context, lang: String): Expect? = when (kind) {
        VoiceIntent.Talk.HowAreYou -> {
            val desi = app.desi.line(HaryanviLines.Event.TalkHowAreYou)
            if (desi != null) speakDesi(desi) else say(res, lang, greeting(res))
            Expect.Open
        }
        VoiceIntent.Talk.WhoAreYou -> {
            styled(res, lang, HaryanviLines.Event.TalkWhoAreYou, res.getString(R.string.voice_who_am_i))
            null
        }
        VoiceIntent.Talk.Joke -> {
            styled(res, lang, HaryanviLines.Event.TalkJoke, res.getString(R.string.voice_joke))
            null
        }
    }

    /** Music keys by voice, with the desi acknowledgement when that style is on. */
    private suspend fun transport(action: VoiceActions.Transport, ack: HaryanviLines.Event) {
        val ok = VoiceActions.transport(app, action)
        if (!ok) return
        val desi = app.desi.line(ack) ?: return
        // Let the player start (or stop) first, so the line plays over ducked music.
        kotlinx.coroutines.delay(ACK_DELAY_MS)
        speakDesi(desi)
    }

    private suspend fun speakDesi(desi: Spoken) {
        _state.update { it.copy(phase = VoiceUiState.Phase.Speaking, reply = desi.text) }
        app.voicePlayer.say(desi)
    }

    private suspend fun say(res: Context, lang: String, @StringRes id: Int) = say(res, lang, res.getString(id))

    /**
     * "Screen band karo": the screen-off countdown shows and Ampli listens once for "radd karo".
     * "Restart karo" / "power off karo": Ampli asks first (the card has Yes / No keys too) and
     * acts only on a clear yes; Restart and Power off then still count down with Cancel.
     */
    private suspend fun power(action: PowerAction, res: Context, lang: String, parserLang: String) {
        if (!MainActivity.inFront) bringLauncherForward(null)
        if (action == PowerAction.ScreenOff) {
            say(res, lang, R.string.power_say_screen_off)
            PowerControl.request(app, PowerAction.ScreenOff, voiceSeconds = PowerPlan.VOICE_SCREEN_OFF_S)
            val heard = hearOnce(lang) ?: return
            if (powerCancel(heard, parserLang) && PowerControl.busy) {
                PowerControl.close()
                styled(res, lang, HaryanviLines.Event.Cancelled, res.getString(R.string.voice_cancelled))
            }
            return
        }
        PowerControl.confirm(action)
        say(res, lang, if (action == PowerAction.Restart) R.string.power_ask_restart else R.string.power_ask_off)
        repeat(2) { turn ->
            // A tap on Yes / No answered already.
            if (PowerControl.state.value !is PowerUi.Confirm) return
            val heard = hearOnce(lang)
            if (PowerControl.state.value !is PowerUi.Confirm) return
            when {
                heard == null -> Unit
                powerCancel(heard, parserLang) -> {
                    PowerControl.close()
                    styled(res, lang, HaryanviLines.Event.Cancelled, res.getString(R.string.voice_cancelled))
                    return
                }
                VoiceReplies.yesNo(heard) == VoiceReplies.YesNo.Yes -> {
                    PowerControl.request(app, action)
                    return
                }
            }
            if (turn == 0) say(res, lang, R.string.power_ask_hint)
        }
        // Still unanswered: the card stays with its Yes / No keys and nothing happens by itself.
    }

    /** "Nahin", "radd karo", "ruko", "cancel". */
    private fun powerCancel(heard: String, parserLang: String): Boolean {
        if (VoiceReplies.yesNo(heard) == VoiceReplies.YesNo.No) return true
        val intent = VoiceIntents.parse(heard, parserLang)
        return intent == VoiceIntent.Cancel || intent == VoiceIntent.Stop
    }

    /** One free-speech listen inside the session (beep first); null on silence. */
    private suspend fun hearOnce(lang: String): String? {
        speaker.earcon()
        _state.update { it.copy(phase = VoiceUiState.Phase.Listening, heard = "") }
        val r = listener.listen(lang, onPartial = { p: String -> _state.update { it.copy(heard = p) } }, commandsOnly = false)
        return (r as? Listener.Result.Heard)?.text?.also { t -> _state.update { it.copy(heard = t, phase = VoiceUiState.Phase.Working) } }
    }

    /**
     * The Haryanvi desi line for [event] when that voice style is on, else [standard]. Safety
     * replies (SOS) never come through here, so they always stay plain and clear.
     */
    private suspend fun styled(res: Context, lang: String, event: HaryanviLines.Event, standard: String, vararg args: Any) {
        val desi = app.desi.line(event, *args)
        if (desi == null) {
            _state.update { it.copy(phase = VoiceUiState.Phase.Speaking, reply = standard) }
            // Without a TTS engine the recorded line for the same moment plays instead.
            app.voicePlayer.sayPlain(standard, lang, event, *args)
            return
        }
        speakDesi(desi)
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
        const val ACK_DELAY_MS = 600L

        /** The answer card stays at least this long. */
        const val ANSWER_HOLD_MS = 6_000L
        /** The full greeting at most every three hours; otherwise just the listening beep. */
        const val GREETING_EVERY_MS = 3L * 60 * 60 * 1000
    }
}
