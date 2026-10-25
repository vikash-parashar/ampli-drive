package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.prefs.VoiceStyle
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
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

    /** How a welcome went, for Settings › Diagnostics › Start log (plain English on purpose). */
    enum class Outcome(val reason: String) {
        Spoken(""),
        InCall("a phone call was on"),
        NoVoice("no recorded clip for this line and no text-to-speech engine"),
        NoAudio("audio output was not ready (tried twice, 3 s apart)"),
    }

    /** Picks, shows and speaks the welcome. Main thread (Speaker's rule). [showCard] false: voice only. */
    suspend fun play(showCard: Boolean = true): Outcome {
        busy = true
        try {
            return playNow(showCard)
        } finally {
            busy = false
        }
    }

    private suspend fun playNow(showCard: Boolean): Outcome {
        val prefs = app.prefs
        val speaker = app.speaker
        val player = app.voicePlayer
        val screenTag = prefs.language.value.ifEmpty { Locale.getDefault().toLanguageTag() }
        val hindiScreen = WelcomeLines.isHindiScreen(screenTag)
        val count = prefs.welcomeCount
        val hour = LocalTime.now().hour
        fun pick(style: VoiceStyle, hindiVoice: Boolean) = WelcomeLines.select(
            style = style,
            name = prefs.ownerName.value,
            count = count,
            hindiVoice = hindiVoice,
            hindiScreen = hindiScreen,
            hour = hour,
        )
        // Recorded clips first: they need no text-to-speech engine and no warm-up (the TS7 and
        // many head units have no engine at all, and right after boot a real one is still starting).
        var welcome = pick(prefs.voiceStyle.value, hindiVoice = false)
        var recorded = player.hasRecorded(welcome.spoken)
        if (!recorded) {
            val ready = speaker.warmUp()
            if (ready) {
                welcome = pick(prefs.voiceStyle.value, hindiVoice = speaker.hasVoice(HINDI))
            } else {
                // Standard style has no recorded welcome: the recorded Haryanvi one beats silence.
                val standIn = pick(VoiceStyle.Haryanvi, hindiVoice = false)
                if (player.hasRecorded(standIn.spoken)) {
                    welcome = welcome.copy(text = standIn.text, language = standIn.language, clips = standIn.clips)
                    recorded = true
                }
            }
            if (!ready && !recorded) {
                prefs.welcomeCount = count + 1
                if (showCard) _card.value = Card(welcome.card, welcome.text, System.nanoTime())
                return Outcome.NoVoice
            }
        }
        prefs.welcomeCount = count + 1
        if (showCard) _card.value = Card(welcome.card, welcome.text, System.nanoTime())
        if (speaker.inCall()) return Outcome.InCall
        if (player.sayChecked(welcome.spoken, needFocus = true, preferRecorded = true)) return Outcome.Spoken
        // Right after boot the audio service or the unit's amplifier may not be ready yet, or the
        // vendor radio still holds audio focus: wait a moment and try once more.
        delay(RETRY_MS)
        if (speaker.inCall()) return Outcome.InCall
        return if (player.sayChecked(welcome.spoken, needFocus = true, preferRecorded = true)) Outcome.Spoken else Outcome.NoAudio
    }

    companion object {
        const val CARD_MS = 5_000L
        const val RETRY_MS = 3_000L
        private val HINDI: Locale = Locale.forLanguageTag("hi-IN")
    }
}
