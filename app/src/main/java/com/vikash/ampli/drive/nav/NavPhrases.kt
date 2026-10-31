package com.vikash.ampli.drive.nav

import com.vikash.ampli.drive.voice.HaryanviLines
import com.vikash.ampli.drive.voice.LinePicker
import com.vikash.ampli.drive.voice.VoiceClips
import java.util.Locale

/**
 * Builds the Haryanvi sentence for a [NavPrompt]: "<distance prefix | now prefix> <manoeuvre line>".
 * [roman] = the Latin version, for units without a Hindi voice. Plain Kotlin, unit-tested.
 */
object NavPhrases {

    fun event(maneuver: Maneuver, exitNumber: Int?): HaryanviLines.Event = when (maneuver) {
        Maneuver.Left -> HaryanviLines.Event.NavLeft
        Maneuver.Right -> HaryanviLines.Event.NavRight
        Maneuver.SlightLeft -> HaryanviLines.Event.NavSlightLeft
        Maneuver.SlightRight -> HaryanviLines.Event.NavSlightRight
        Maneuver.SharpLeft -> HaryanviLines.Event.NavSharpLeft
        Maneuver.SharpRight -> HaryanviLines.Event.NavSharpRight
        Maneuver.UTurn -> HaryanviLines.Event.NavUTurn
        Maneuver.KeepLeft -> HaryanviLines.Event.NavKeepLeft
        Maneuver.KeepRight -> HaryanviLines.Event.NavKeepRight
        Maneuver.Flyover -> HaryanviLines.Event.NavFlyover
        Maneuver.Ramp -> HaryanviLines.Event.NavRamp
        Maneuver.Exit -> HaryanviLines.Event.NavExit
        Maneuver.Roundabout -> if (exitNumber != null) HaryanviLines.Event.NavRoundaboutExit else HaryanviLines.Event.NavRoundabout
        Maneuver.Arrive -> HaryanviLines.Event.NavArrive
        Maneuver.Continue -> HaryanviLines.Event.NavContinue
    }

    /** Arriving and "keep going straight" are whole sentences; they take no distance or "abhi". */
    fun takesPrefix(maneuver: Maneuver): Boolean = maneuver != Maneuver.Arrive && maneuver != Maneuver.Continue

    fun desi(prompt: NavPrompt, picker: LinePicker, roman: Boolean): String? = desiPhrase(prompt, picker, roman)?.text

    /**
     * The sentence and, for the recorded voice, its clips (empty when one part can't be recorded).
     * [hint]: the [TurnHint] line used instead of the plain turn line, if any.
     */
    data class Phrase(val text: String, val clips: List<String>, val hint: TurnHint? = null)

    /**
     * Like [desi], plus the recorded-voice clips: "<distance clip> <tail clip> <turn clip>" for a
     * turn ahead (distance rounded to the nearest recorded one), "<ib clip> <turn clip>" for now.
     *
     * A [ParsedTurn.hint] replaces the turn line in the prompt ahead (and in the arriving one).
     * [recordedOnly]: the recorded voice speaks, so a hint line it has no clip for (a line with a
     * number) is skipped and the plain turn line is said instead.
     */
    fun desiPhrase(prompt: NavPrompt, picker: LinePicker, roman: Boolean, recordedOnly: Boolean = false): Phrase? {
        hintPhrase(prompt, picker, roman, recordedOnly)?.let { return it }
        val turn = prompt.turn
        val main = picker.pickIndexed(event(turn.maneuver, turn.exitNumber)) ?: return null
        val body = fill(if (roman) main.line.roman else main.line.deva, turn.exitNumber ?: 0)
        val bodyClips = VoiceClips.clipsFor(main, arrayOf(turn.exitNumber ?: 0)) { picker.pickIndexed(it) }
        if (!takesPrefix(turn.maneuver)) return Phrase(body, bodyClips)
        return withPrefix(prompt, picker, roman, body, bodyClips, null)
    }

    /** The [TurnHint] version of [prompt], or null when it has none, it doesn't apply or can't be said. */
    private fun hintPhrase(prompt: NavPrompt, picker: LinePicker, roman: Boolean, recordedOnly: Boolean): Phrase? {
        val hint = prompt.turn.hint ?: return null
        val arg: Int
        if (hint == TurnHint.ArrivingRight) {
            // "बस आ गे, 100 मीटर पै सुल्टे हाथ…": only with a real distance left.
            arg = prompt.distanceM?.takeIf { it >= ARRIVING_MIN_M } ?: return null
        } else {
            if (prompt.now) return null
            arg = 0
        }
        val main = picker.pickIndexed(hint.event) ?: return null
        val body = fill(if (roman) main.line.roman else main.line.deva, arg)
        val bodyClips = VoiceClips.clipsFor(main, arrayOf(arg)) { picker.pickIndexed(it) }
        if (recordedOnly && bodyClips.isEmpty()) return null
        if (!hint.prefix) return Phrase(body, bodyClips, hint)
        return withPrefix(prompt, picker, roman, body, bodyClips, hint)
    }

    private fun withPrefix(prompt: NavPrompt, picker: LinePicker, roman: Boolean, body: String, bodyClips: List<String>, hint: TurnHint?): Phrase {
        var prefixClips: List<String> = emptyList()
        val prefix = when {
            prompt.now -> picker.pickIndexed(HaryanviLines.Event.NavNow)?.let {
                prefixClips = listOf(it.id)
                if (roman) it.line.roman else it.line.deva
            }
            prompt.distanceM != null -> picker.pickIndexed(HaryanviLines.Event.NavSoon)?.let {
                prefixClips = VoiceClips.distanceClips(prompt.distanceM, picker.pickIndexed(HaryanviLines.Event.NavSoonTail))
                fill(if (roman) it.line.roman else it.line.deva, distance(prompt.distanceM, roman))
            }
            else -> null
        }
        if (prefix == null) return Phrase(body, bodyClips, hint)
        val clips = if (prefixClips.isNotEmpty() && bodyClips.isNotEmpty()) prefixClips + bodyClips else emptyList()
        return Phrase("$prefix $body", clips, hint)
    }

    /** The arriving line with metres is only said this far out or more. */
    const val ARRIVING_MIN_M = 50

    /** "400 मीटर", "1.5 किलोमीटर" (roman: "400 metre", "1.5 kilometre"). */
    fun distance(m: Int, roman: Boolean): String = if (m < 1000) {
        if (roman) "$m metre" else "$m मीटर"
    } else {
        val km = if (m % 1000 == 0) (m / 1000).toString() else String.format(Locale.US, "%.1f", m / 1000.0)
        if (roman) "$km kilometre" else "$km किलोमीटर"
    }

    private fun fill(template: String, arg: Any): String = try {
        String.format(Locale.US, template, arg)
    } catch (_: Exception) {
        template
    }
}
