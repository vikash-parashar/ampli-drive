package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.fastag.FastagKind
import com.vikash.ampli.drive.fastag.FastagLowReminder
import com.vikash.ampli.drive.fastag.TollEvent
import com.vikash.ampli.drive.prefs.VoiceStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
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
    private val fastagLow = FastagLowReminder()
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
        // A line worded per voice: the female wording when the female recorded voice speaks.
        val voice = if (line.devaF != null) app.voicePlayer.voice else null
        return Spoken(format(if (hindi) line.devaFor(voice) else line.romanFor(voice), args), if (hindi) "hi" else "en-IN", clips, caption = true)
    }

    /** Speaks the desi line; returns false when the style is off so the caller says the standard line. */
    suspend fun say(event: HaryanviLines.Event, vararg args: Any): Boolean {
        val spoken = line(event, *args) ?: return false
        app.voicePlayer.say(spoken)
        return true
    }

    /**
     * Speaks the desi line after anything this queue is already saying, unless the style is off.
     * While a Hey Ampli session runs or a call is on, the line waits for it to end (up to
     * [MAX_WAIT_MS]) instead of being dropped.
     */
    fun sayLater(event: HaryanviLines.Event, vararg args: Any) {
        if (!enabled) return
        later { say(event, *args) }
    }

    /** Runs [speak] after anything already queued, once Hey Ampli and any call are done. */
    private fun later(speak: suspend () -> Unit) {
        val old = queue
        queue = scope.launch {
            old?.join()
            if (!waitUntilFree()) return@launch
            speak()
        }
    }

    /** Waits while Hey Ampli is busy or a call is on. False when it never came free in time. */
    private suspend fun waitUntilFree(): Boolean {
        var waited = 0L
        while (app.voice.busy.value || app.speaker.inCall()) {
            if (waited >= MAX_WAIT_MS) return false
            delay(WAIT_STEP_MS)
            waited += WAIT_STEP_MS
        }
        // A short gap after a call or a Hey Ampli answer, so the line doesn't run into it.
        if (waited > 0L) delay(WAIT_STEP_MS)
        return true
    }

    /** Idempotent; called with the other long-lived listeners (MainActivity and the background monitor). */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            app.fastag.spoken.collect { e ->
                when (e.kind) {
                    FastagKind.Recharge -> onRecharge(e)
                    else -> onToll(e)
                }
            }
        }
    }

    /**
     * FASTag toll SMS, in every voice style. Haryanvi: "टोल कट गया, 145 रुपये, बैलेंस 855 रुपये
     * बच्या सै।" Standard: the plain line in the app language ("Toll paid, 145 rupees. Balance 855
     * rupees."), or the recorded toll line on units without a phone voice.
     */
    private fun onToll(e: TollEvent) {
        val amount = e.amount ?: return
        val balance = e.balance
        if (enabled) {
            if (balance != null) {
                sayLater(HaryanviLines.Event.TollPaid, rupees(amount), rupees(balance))
            } else {
                sayLater(HaryanviLines.Event.TollPaidNoBalance, rupees(amount))
            }
        } else {
            later {
                val lang = app.car.language()
                val res = app.car.localized(lang)
                if (balance != null) {
                    app.voicePlayer.sayPlain(
                        res.getString(R.string.fastag_voice_toll, rupees(amount), rupees(balance)), lang,
                        HaryanviLines.Event.TollPaid, rupees(amount), rupees(balance),
                    )
                } else {
                    app.voicePlayer.sayPlain(
                        res.getString(R.string.fastag_voice_toll_no_balance, rupees(amount)), lang,
                        HaryanviLines.Event.TollPaidNoBalance, rupees(amount),
                    )
                }
            }
        }
        // Under the driver's alert level: a gentle recharge nudge right after, once a day.
        if (balance != null && fastagLow.afterToll(balance, app.fastag.lowBalance.value, java.time.LocalDate.now().toEpochDay())) {
            if (enabled) {
                sayLater(HaryanviLines.Event.FastagLowBalance, rupees(balance))
            } else {
                later {
                    val lang = app.car.language()
                    val res = app.car.localized(lang)
                    app.voicePlayer.sayPlain(
                        res.getString(R.string.fastag_voice_low, rupees(balance)), lang,
                        HaryanviLines.Event.FastagLowBalance, rupees(balance),
                    )
                }
            }
        }
    }

    /** FASTag recharge SMS, in every voice style: "₹500 recharge hua, balance ₹855". */
    private fun onRecharge(e: TollEvent) {
        val amount = e.amount ?: return
        val balance = e.balance
        later {
            val lang = app.car.language()
            val res = app.car.localized(lang)
            val text = if (balance != null) {
                res.getString(R.string.fastag_voice_recharge, rupees(amount), rupees(balance))
            } else {
                res.getString(R.string.fastag_voice_recharge_no_balance, rupees(amount))
            }
            // No recorded recharge line exists: the phone voice says it (nothing on units without one).
            app.voicePlayer.sayPlain(text, lang, null)
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

        /** How long a FASTag line waits for a call or Hey Ampli to finish before it is dropped. */
        const val MAX_WAIT_MS = 15L * 60 * 1000
        const val WAIT_STEP_MS = 1_000L

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
