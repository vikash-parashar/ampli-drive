package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.DriveApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale

/**
 * The welcome audio message when the car starts: "राम राम भाई, आज कित का प्लान सै?" (see
 * [WelcomeLines]; the name shows on the card, never spoken). CarStart calls [play] once per ignition cycle (boot, ACC on, or the launcher
 * showing after a reboot, with its 3-minute dedupe); Settings › Voice › Play welcome now calls it
 * any time.
 *
 * At boot the TTS service is often still starting, so the engine is warmed up first (onInit, one
 * retry after 5 s). Speech holds may-duck audio focus, so music dips and comes back. During a
 * phone call nothing is spoken; the card still shows.
 */
class WelcomeGreeter(private val app: DriveApp) {

    /** The card on screen ("Ram Ram Vikash Ji 🙏"), shown for [CARD_MS]; [id] changes per welcome. */
    data class Card(val title: String, val line: String, val id: Long)

    private val _card = MutableStateFlow<Card?>(null)
    val card: StateFlow<Card?> = _card.asStateFlow()

    /** Main-thread scope that outlives the screen that asked (the name step closes while it speaks). */
    private val scope = MainScope()

    @Volatile private var busy = false

    /** Settings › Play welcome now, and right after the first-run name step. Ignored while one plays. */
    fun playSoon() {
        if (busy) return
        scope.launch { play() }
    }

    fun dismissCard() {
        _card.value = null
    }

    /** Picks, shows and speaks the welcome. Main thread (Speaker's rule). */
    suspend fun play() {
        busy = true
        try {
            playNow()
        } finally {
            busy = false
        }
    }

    private suspend fun playNow() {
        val prefs = app.prefs
        val speaker = app.speaker
        val voice = app.voicePlayer.voice
        val ready = speaker.warmUp()
        val hindiVoice = ready && speaker.hasVoice(HINDI)
        val screenTag = prefs.language.value.ifEmpty { Locale.getDefault().toLanguageTag() }
        val count = prefs.welcomeCount
        val welcome = WelcomeLines.select(
            style = prefs.voiceStyle.value,
            name = prefs.ownerName.value,
            count = count,
            hindiVoice = hindiVoice,
            hindiScreen = WelcomeLines.isHindiScreen(screenTag),
            hour = LocalTime.now().hour,
        )
        prefs.welcomeCount = count + 1
        _card.value = Card(welcome.card, welcome.text, System.nanoTime())
        if (speaker.inCall()) return
        val recorded = app.voicePlayer.library.plan(voice, welcome.spoken) != null
        if (!ready && !recorded) return
        app.voicePlayer.say(welcome.spoken, needFocus = true)
    }

    companion object {
        const val CARD_MS = 5_000L
        private val HINDI: Locale = Locale.forLanguageTag("hi-IN")
    }
}
