package com.vikash.ampli.drive.nav

import java.util.Locale
import kotlin.math.roundToInt

/** The next manoeuvre, as far as Google Maps' notification text tells it. */
enum class Maneuver {
    Left, Right, SlightLeft, SlightRight, SharpLeft, SharpRight, UTurn,
    KeepLeft, KeepRight, Flyover, Ramp, Exit, Roundabout, Arrive, Continue,
}

/**
 * @param distanceM metres to the manoeuvre, null when the notification shows none.
 * @param exitNumber the roundabout exit ("take the 2nd exit"), when given.
 * @param key the instruction without its distance: a new key means a new manoeuvre.
 */
data class ParsedTurn(val maneuver: Maneuver, val distanceM: Int?, val exitNumber: Int?, val key: String)

/**
 * Reads Google Maps' navigation notification (title "450 m", text "Turn right onto NH 48", or
 * the instruction in the title). English and Hindi Maps strings. Plain Kotlin, unit-tested.
 *
 * Maps' wording changes between versions and languages, so this matches phrases, not exact
 * sentences; anything it can't place returns null and Ampli simply stays quiet.
 */
object MapsTurnParser {

    fun parse(title: String?, text: String?): ParsedTurn? {
        val parts = listOfNotNull(title, text).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        var distance: Int? = null
        val instructions = ArrayList<String>(2)
        for (p in parts) {
            val d = distanceIn(p)
            if (distance == null && d != null) distance = d
            val rest = DISTANCE.replace(p, " ").replace(SEPARATORS, " ").trim()
            if (rest.isNotEmpty()) instructions += rest
        }
        if (instructions.isEmpty()) return null
        for (raw in instructions) {
            val instruction = normalize(raw)
            val m = maneuverOf(dropRoadName(instruction)) ?: maneuverOf(instruction) ?: continue
            val exit = if (m == Maneuver.Roundabout) exitNumber(instruction) else null
            return ParsedTurn(m, distance, exit, "$m|${instruction.take(KEY_CHARS)}")
        }
        return null
    }

    /** Metres from "450 m", "1.2 km", "1,2 km", "400 मी", "1.5 किमी", "500 ft", "0.3 mi". */
    fun distanceIn(s: String): Int? {
        val match = DISTANCE.find(s.lowercase(Locale.ROOT)) ?: return null
        val n = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val unit = match.groupValues[2]
        val metres = when (unit) {
            "km", "किमी", "किमी.", "किलोमीटर", "कि.मी." -> n * 1000
            "mi", "miles", "mile" -> n * 1609.34
            "ft", "feet" -> n * 0.3048
            else -> n
        }
        return metres.roundToInt()
    }

    private fun maneuverOf(t: String): Maneuver? = RULES.firstOrNull { (_, phrases) -> phrases.any { has(t, it) } }?.first

    /**
     * "turn left onto right bank road" → "turn left": the road name can't change the turn.
     * Hindi puts the road first ("MG रोड पर बाएं मुड़ें"), so only "फिर" (then) cuts there.
     */
    private fun dropRoadName(t: String): String {
        var s = t
        for (marker in ROAD_MARKERS) {
            val i = s.indexOf(" $marker ")
            if (i > 0) s = s.substring(0, i)
        }
        return s
    }

    private fun exitNumber(t: String): Int? {
        Regex("(\\d+)\\s*(st|nd|rd|th)?\\s+exit").find(t)?.let { return it.groupValues[1].toIntOrNull() }
        Regex("exit\\s+(\\d+)").find(t)?.let { return it.groupValues[1].toIntOrNull() }
        ORDINALS.forEachIndexed { i, words -> if (words.any { has(t, it) }) return i + 1 }
        return null
    }

    private fun normalize(s: String): String = s.lowercase(Locale.ROOT)
        .replace('’', '\'')
        .replace(Regex("[\\p{P}&&[^'-]]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun has(t: String, phrase: String): Boolean =
        Regex("(?<![\\p{L}\\p{M}\\p{N}])" + Regex.escape(phrase) + "(?![\\p{L}\\p{M}\\p{N}])").containsMatchIn(t)

    private const val KEY_CHARS = 60

    private val DISTANCE = Regex(
        "(\\d+(?:[.,]\\d+)?)\\s*(km|किमी\\.?|किलोमीटर|कि\\.मी\\.|mi|miles|mile|ft|feet|m|मी\\.?|मीटर)(?![\\p{L}\\p{M}])",
        RegexOption.IGNORE_CASE,
    )
    private val SEPARATORS = Regex("[·•|]")

    private val ROAD_MARKERS = listOf(
        "onto", "on to", "toward", "towards", "to stay on", "on", "into", "at", "then", "for", "फिर",
    )

    private val ORDINALS = listOf(
        listOf("first", "पहले", "पहला", "पहली"),
        listOf("second", "दूसरे", "दूसरा", "दूसरी"),
        listOf("third", "तीसरे", "तीसरा", "तीसरी"),
        listOf("fourth", "चौथे", "चौथा", "चौथी"),
        listOf("fifth", "पांचवें", "पाँचवें", "पांचवा", "पाँचवा"),
    )

    private val LEFT = listOf("बाएं", "बाएँ", "बाईं", "बायें", "बाँए", "बायीं", "बाई")
    private val RIGHT = listOf("दाएं", "दाएँ", "दाईं", "दायें", "दाँए", "दायीं", "दाई")

    private fun hi(prefixes: List<String>, sides: List<String>) = prefixes.flatMap { p -> sides.flatMap { listOf("$p $it", "$it $p") } }

    /** Checked in order: the first rule with a matching phrase wins. */
    private val RULES: List<Pair<Maneuver, List<String>>> = listOf(
        Maneuver.Arrive to listOf(
            "arrive", "arrived", "arriving", "destination", "your destination", "पहुंच", "पहुँच", "पहुंचेंगे",
            "पहुँचेंगे", "पहुंच गए", "पहुँच गए", "गंतव्य", "मंज़िल", "मंजिल",
        ),
        Maneuver.UTurn to listOf("u-turn", "u turn", "uturn", "make a u", "यू-टर्न", "यू टर्न", "यूटर्न", "वापस मुड़ें"),
        Maneuver.Roundabout to listOf(
            "roundabout", "rotary", "traffic circle", "गोल चक्कर", "गोलचक्कर", "राउंडअबाउट", "चौराहे के चक्कर",
        ),
        Maneuver.Flyover to listOf("flyover", "overpass", "फ़्लाईओवर", "फ्लाईओवर", "फ्लाइओवर", "फ़्लाइओवर"),
        Maneuver.Ramp to listOf("ramp", "slip road", "रैंप", "रेम्प"),
        Maneuver.Exit to listOf("exit", "take exit", "निकास", "एग्ज़िट", "एग्जिट", "एक्जिट"),
        Maneuver.KeepLeft to listOf("keep left", "stay left", "bear left", "keep to the left") +
            hi(listOf("रहें", "रहे", "बने रहें"), LEFT) + LEFT.map { "$it ओर रहें" } + LEFT.map { "$it तरफ़ रहें" } + LEFT.map { "$it तरफ रहें" },
        Maneuver.KeepRight to listOf("keep right", "stay right", "bear right", "keep to the right") +
            hi(listOf("रहें", "रहे", "बने रहें"), RIGHT) + RIGHT.map { "$it ओर रहें" } + RIGHT.map { "$it तरफ़ रहें" } + RIGHT.map { "$it तरफ रहें" },
        Maneuver.SharpLeft to listOf("sharp left", "hard left") + hi(listOf("तीखा", "तीखे", "तेज़ी से", "तेजी से", "तेज़", "तेज"), LEFT),
        Maneuver.SharpRight to listOf("sharp right", "hard right") + hi(listOf("तीखा", "तीखे", "तेज़ी से", "तेजी से", "तेज़", "तेज"), RIGHT),
        Maneuver.SlightLeft to listOf("slight left", "slightly left", "slight turn left") + hi(listOf("हल्का", "हल्के", "थोड़ा", "थोड़े", "हल्के से"), LEFT),
        Maneuver.SlightRight to listOf("slight right", "slightly right", "slight turn right") + hi(listOf("हल्का", "हल्के", "थोड़ा", "थोड़े", "हल्के से"), RIGHT),
        Maneuver.Left to listOf("left") + LEFT,
        Maneuver.Right to listOf("right") + RIGHT,
        Maneuver.Continue to listOf(
            "continue", "head", "straight", "go straight", "merge", "सीधे", "सीधा", "जारी रखें", "आगे बढ़ें", "चलते रहें",
        ),
    )
}
