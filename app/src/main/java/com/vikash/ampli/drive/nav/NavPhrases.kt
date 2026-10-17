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

    /** The sentence and, for the recorded voice, its clips (empty when one part can't be recorded). */
    data class Phrase(val text: String, val clips: List<String>)

    /**
     * Like [desi], plus the recorded-voice clips: "<distance clip> <tail clip> <turn clip>" for a
     * turn ahead (distance rounded to the nearest recorded one), "<ib clip> <turn clip>" for now.
     */
    fun desiPhrase(prompt: NavPrompt, picker: LinePicker, roman: Boolean): Phrase? {
        val turn = prompt.turn
        val main = picker.pickIndexed(event(turn.maneuver, turn.exitNumber)) ?: return null
        val body = fill(if (roman) main.line.roman else main.line.deva, turn.exitNumber ?: 0)
        val bodyClips = VoiceClips.clipsFor(main, arrayOf(turn.exitNumber ?: 0)) { picker.pickIndexed(it) }
        if (!takesPrefix(turn.maneuver)) return Phrase(body, bodyClips)
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
        if (prefix == null) return Phrase(body, bodyClips)
        val clips = if (prefixClips.isNotEmpty() && bodyClips.isNotEmpty()) prefixClips + bodyClips else emptyList()
        return Phrase("$prefix $body", clips)
    }

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
