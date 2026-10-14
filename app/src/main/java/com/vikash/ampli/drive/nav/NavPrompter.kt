package com.vikash.ampli.drive.nav

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.os.SystemClock
import android.util.Log
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.i18n.AppLanguages
import com.vikash.ampli.drive.prefs.VoiceStyle
import com.vikash.ampli.drive.voice.HaryanviLines
import com.vikash.ampli.drive.voice.LinePicker
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Settings › Haryanvi navigation prompts. Car Hub hands navigation to Google Maps; this reads
 * Maps' next-turn notification (via [NavTurnRepository], filled by the notification listener),
 * and speaks each manoeuvre once ahead and once "abhi", in the Haryanvi desi voice style or, in
 * the Standard style, in plain Hindi/English. The [Speaker][com.vikash.ampli.drive.voice.Speaker]
 * ducks music while it talks. The line also shows under the floating speed bubble ([caption]).
 *
 * While navigating it also says a slow-down line when the speed stays above the known limit.
 */
class NavPrompter(private val app: DriveApp) {
    private val scope = MainScope()
    private val planner = NavPromptPlanner()
    private val nag = OverLimitNag()
    private val picker = LinePicker()
    private var started = false
    private var speech: Job? = null
    private var captionJob: Job? = null

    /** For Settings › About › Diagnostics. */
    data class Debug(
        val title: String,
        val text: String?,
        val parsed: ParsedTurn?,
        val lastSpoken: String?,
        val atMs: Long,
    )

    private val _debug = MutableStateFlow<Debug?>(null)
    val debug: StateFlow<Debug?> = _debug.asStateFlow()

    /** The line just spoken, for a few seconds: the speed bubble shows it over Maps. */
    private val _caption = MutableStateFlow<String?>(null)
    val caption: StateFlow<String?> = _caption.asStateFlow()

    /** Idempotent; called by MainActivity and the background monitor. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            NavTurnRepository.turn.collect { onTurn(it) }
        }
        scope.launch {
            combine(app.speedTracker.state, app.cameraAlerts.speedLimitKmh) { gps, limit -> gps.speedKmh to limit }
                .collect { (speed, limit) -> onSpeed(speed, limit) }
        }
    }

    private fun onTurn(turn: NextTurn?) {
        if (turn == null) {
            planner.reset()
            return
        }
        val parsed = MapsTurnParser.parse(turn.title, turn.text)
        _debug.value = Debug(turn.title, turn.text, parsed, _debug.value?.lastSpoken, System.currentTimeMillis())
        if (parsed == null || !app.prefs.navPrompts.value) return
        val prompt = planner.onTurn(parsed) ?: return
        say { haryanvi -> if (haryanvi) desiText(prompt) else standardText(prompt) }
    }

    private fun onSpeed(speedKmh: Float?, limitKmh: Int?) {
        val prefs = app.prefs
        if (!prefs.navPrompts.value || NavTurnRepository.turn.value == null) return
        val level = nag.onSpeed(SystemClock.elapsedRealtime(), speedKmh, limitKmh)
        if (level == OverLimitNag.Level.None) return
        // Pro camera voice alerts already say "slow down" near the camera.
        if (prefs.pro.value.isPro && prefs.cameraVoice.value && prefs.cameraAlerts.value) return
        val limit = limitKmh ?: return
        say { haryanvi ->
            if (haryanvi) {
                val event = if (level == OverLimitNag.Level.EaseOff) HaryanviLines.Event.SpeedEaseOff else HaryanviLines.Event.SpeedLimit
                desiLine(event, limit)
            } else {
                val id = if (level == OverLimitNag.Level.EaseOff) R.string.nav_speed_ease_off else R.string.nav_speed_limit
                Spoken(localized().getString(id, limit), language())
            }
        }
    }

    private data class Spoken(val text: String, val language: String)

    /** Speaks one line after the previous one, and shows it under the bubble. */
    private fun say(build: suspend (haryanvi: Boolean) -> Spoken?) {
        val old = speech
        speech = scope.launch {
            old?.join()
            val haryanvi = app.prefs.voiceStyle.value == VoiceStyle.Haryanvi
            val spoken = try {
                build(haryanvi)
            } catch (e: Exception) {
                Log.w(TAG, "prompt failed", e)
                null
            } ?: return@launch
            _debug.value = _debug.value?.copy(lastSpoken = spoken.text)
            showCaption(spoken.text)
            app.speaker.say(spoken.text, spoken.language)
        }
    }

    private fun showCaption(text: String) {
        _caption.value = text
        captionJob?.cancel()
        captionJob = scope.launch {
            delay(CAPTION_MS)
            _caption.value = null
        }
    }

    private suspend fun hindiVoice(): Boolean = app.speaker.hasVoice(HINDI)

    private suspend fun desiText(prompt: NavPrompt): Spoken? {
        val hindi = hindiVoice()
        val text = NavPhrases.desi(prompt, picker, roman = !hindi) ?: return null
        return Spoken(text, if (hindi) "hi" else "en-IN")
    }

    private suspend fun desiLine(event: HaryanviLines.Event, arg: Any): Spoken? {
        val hindi = hindiVoice()
        val line = picker.pick(event) ?: return null
        val template = if (hindi) line.deva else line.roman
        val text = try {
            String.format(Locale.US, template, arg)
        } catch (_: Exception) {
            template
        }
        return Spoken(text, if (hindi) "hi" else "en-IN")
    }

    /** Plain prompt in the app language (English and Hindi strings; others fall back to English). */
    private fun standardText(prompt: NavPrompt): Spoken {
        val res = localized()
        val turn = prompt.turn
        val action = when (turn.maneuver) {
            Maneuver.Left -> res.getString(R.string.nav_turn_left)
            Maneuver.Right -> res.getString(R.string.nav_turn_right)
            Maneuver.SlightLeft -> res.getString(R.string.nav_slight_left)
            Maneuver.SlightRight -> res.getString(R.string.nav_slight_right)
            Maneuver.SharpLeft -> res.getString(R.string.nav_sharp_left)
            Maneuver.SharpRight -> res.getString(R.string.nav_sharp_right)
            Maneuver.UTurn -> res.getString(R.string.nav_u_turn)
            Maneuver.KeepLeft -> res.getString(R.string.nav_keep_left)
            Maneuver.KeepRight -> res.getString(R.string.nav_keep_right)
            Maneuver.Flyover -> res.getString(R.string.nav_flyover)
            Maneuver.Ramp -> res.getString(R.string.nav_ramp)
            Maneuver.Exit -> res.getString(R.string.nav_exit)
            Maneuver.Roundabout -> turn.exitNumber?.let { res.getString(R.string.nav_roundabout_exit, it) }
                ?: res.getString(R.string.nav_roundabout)
            Maneuver.Arrive -> res.getString(R.string.nav_arrive)
            Maneuver.Continue -> res.getString(R.string.nav_continue)
        }
        val text = when {
            !NavPhrases.takesPrefix(turn.maneuver) -> action
            prompt.now -> res.getString(R.string.nav_now, action)
            prompt.distanceM != null -> res.getString(R.string.nav_in_distance, distanceText(res, prompt.distanceM), action)
            else -> action
        }
        return Spoken(text.replaceFirstChar { it.titlecase(res.resources.configuration.locales[0]) }, language())
    }

    private fun distanceText(res: Context, m: Int): String = if (m < 1000) {
        res.getString(R.string.nav_distance_m, m)
    } else {
        val km = if (m % 1000 == 0) (m / 1000).toString() else String.format(res.resources.configuration.locales[0], "%.1f", m / 1000.0)
        res.getString(R.string.nav_distance_km, km)
    }

    private fun language(): String = AppLanguages.current(app, app.prefs.language.value)

    private fun localized(): Context {
        val tag = language()
        if (tag.isEmpty()) return app
        val config = Configuration(app.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return app.createConfigurationContext(config)
    }

    private companion object {
        const val TAG = "NavPrompter"
        const val CAPTION_MS = 7_000L
        val HINDI: Locale = Locale.forLanguageTag("hi-IN")
    }
}
