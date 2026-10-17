package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.fastag.TollEvent
import com.vikash.ampli.drive.prefs.VoiceStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Speaks [HaryanviLines] when Settings › Voice style is "Haryanvi desi". Hindi voice installed:
 * the Devanagari line with hi-IN. No Hindi voice: the Latin line with en-IN, which reads it
 * well enough. Safety events never get here (forEvent returns null for them).
 *
 * Also the personality's own moments that have no standard-style speech (the Standard style
 * stays as it was): the FASTag toll line, and Settings › Voice › Hear samples.
 */
class DesiVoice(private val app: DriveApp) {
    private val picker = LinePicker()
    private val scope = MainScope()
    private var queue: Job? = null
    private var started = false

    val enabled: Boolean get() = app.prefs.voiceStyle.value == VoiceStyle.Haryanvi

    /** The sample playing in Settings › Voice › Hear samples, for its animation. */
    private val _sampling = MutableStateFlow<Pair<HaryanviLines.Event, String>?>(null)
    val sampling: StateFlow<Pair<HaryanviLines.Event, String>?> = _sampling.asStateFlow()

    /**
     * The desi line for [event] with [args] filled in, or null when the style is off ([force]
     * ignores the style). [Spoken.clips] holds the recorded-voice clips (a stand-in line when this
     * one has a name or number in it, see [VoiceClips.clipsFor]).
     */
    suspend fun line(event: HaryanviLines.Event, vararg args: Any, force: Boolean = false): Spoken? {
        if (!enabled && !force) return null
        val picked = picker.pickIndexed(event) ?: return null
        val hindi = app.speaker.hasVoice(HINDI)
        val line = picked.line
        val clips = VoiceClips.clipsFor(picked, args) { picker.pickIndexed(it) }
        return Spoken(format(if (hindi) line.deva else line.roman, args), if (hindi) "hi" else "en-IN", clips)
    }

    /** Speaks the desi line; returns false when the style is off so the caller says the standard line. */
    suspend fun say(event: HaryanviLines.Event, vararg args: Any): Boolean {
        val spoken = line(event, *args) ?: return false
        app.voicePlayer.say(spoken)
        return true
    }

    /**
     * Speaks the desi line after anything this queue is already saying, unless the style is off,
     * a Hey Ampli session is running or a call is on. For the personality's own moments.
     */
    fun sayLater(event: HaryanviLines.Event, vararg args: Any) {
        if (!enabled) return
        val old = queue
        queue = scope.launch {
            old?.join()
            if (app.voice.busy.value || app.speaker.inCall()) return@launch
            say(event, *args)
        }
    }

    /** Idempotent; called with the other long-lived listeners (MainActivity and the background monitor). */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            app.fastag.arrivals.collect { onToll(it) }
        }
    }

    /** FASTag toll SMS: "टोल कट गया, 145 रुपये, बैलेंस 855 रुपये बच्या सै।" */
    private fun onToll(e: TollEvent) {
        val amount = e.amount ?: return
        val balance = e.balance
        if (balance != null) {
            sayLater(HaryanviLines.Event.TollPaid, rupees(amount), rupees(balance))
        } else {
            sayLater(HaryanviLines.Event.TollPaidNoBalance, rupees(amount))
        }
    }

    /** Settings › Voice › Hear samples: one line of [event], whatever the voice style. */
    fun sample(event: HaryanviLines.Event) {
        val old = queue
        queue = scope.launch {
            old?.cancel()
            app.voicePlayer.stop()
            val spoken = line(event, *HaryanviLines.sampleArgs(event), force = true) ?: return@launch
            _sampling.value = event to spoken.text
            try {
                app.voicePlayer.say(spoken)
            } finally {
                if (_sampling.value?.first == event) _sampling.value = null
            }
        }
    }

    private companion object {
        val HINDI: Locale = Locale.forLanguageTag("hi-IN")

        fun format(template: String, args: Array<out Any>): String = try {
            String.format(Locale.US, template, *args)
        } catch (_: Exception) {
            template
        }

        /** 145.0 → "145", 145.5 → "145.50". */
        fun rupees(v: Double): String =
            if (v % 1.0 == 0.0) v.roundToLong().toString() else String.format(Locale.US, "%.2f", v)
    }
}
