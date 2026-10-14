package com.vikash.ampli.drive.nav

import com.vikash.ampli.drive.voice.HaryanviLines
import com.vikash.ampli.drive.voice.LinePicker
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

    fun desi(prompt: NavPrompt, picker: LinePicker, roman: Boolean): String? {
        val turn = prompt.turn
        val main = picker.pick(event(turn.maneuver, turn.exitNumber)) ?: return null
        val body = fill(if (roman) main.roman else main.deva, turn.exitNumber ?: 0)
        if (!takesPrefix(turn.maneuver)) return body
        val prefix = when {
            prompt.now -> picker.pick(HaryanviLines.Event.NavNow)?.let { if (roman) it.roman else it.deva }
            prompt.distanceM != null -> picker.pick(HaryanviLines.Event.NavSoon)?.let {
                fill(if (roman) it.roman else it.deva, distance(prompt.distanceM, roman))
            }
            else -> null
        }
        return if (prefix == null) body else "$prefix $body"
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
