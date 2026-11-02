package com.vikash.ampli.drive.voice

/**
 * Amounts as drivers say them, for the petrol diary: digits ("1000", "१०००"), English words
 * ("one thousand five hundred"), Roman Hindi ("do hazaar", "pachaas", "dedh sau") and Devanagari
 * ("एक हज़ार", "पचास", "ढाई सौ"). Plain Kotlin, unit-tested. Input is already normalised
 * ([VoiceIntents.normalize]: lower case, no punctuation).
 */
object SpokenNumbers {

    /** What follows a number: rupees by default, litres, or kilograms (CNG). */
    enum class Unit { Rupees, Litres, Kilos }

    data class Amount(val value: Double, val unit: Unit)

    /**
     * The first spoken amount in [text] and its unit, or null when there is none. "Do" (two) only
     * counts before "sau" / "hazaar", because it is also the "kar do" of every request.
     */
    fun amount(text: String): Amount? {
        val words = text.split(' ').filter { it.isNotEmpty() }.map { nfc(it) }
        var i = 0
        while (i < words.size) {
            val parsed = numberAt(words, i)
            if (parsed != null) {
                val (value, end) = parsed
                val unit = unitAfter(words, end)
                if (value > 0) return Amount(value, unit)
                i = end
            } else {
                i++
            }
        }
        return null
    }

    /** Just the number, for tests and simple callers. */
    fun parse(text: String): Double? = amount(text)?.value

    /** Reads a number starting at [start]; returns it and the index after it, or null. */
    private fun numberAt(words: List<String>, start: Int): Pair<Double, Int>? {
        var total = 0.0
        var current = 0.0
        var any = false
        var half = 0.0 // "saadhe": the next number gets +0.5; "sawa" +0.25; "paune" -0.25
        var i = start
        while (i < words.size) {
            val w = words[i]
            val next = words.getOrNull(i + 1)
            val digits = digitValue(w)
            when {
                digits != null -> {
                    current += digits + half
                    half = 0.0
                    any = true
                }
                w in FRACTION_PREFIX -> {
                    // Only a prefix when a number follows.
                    if (next == null || (unitValue(next) == null && multiplier(next) == null && digitValue(next) == null)) break
                    half = FRACTION_PREFIX.getValue(w)
                }
                w in FRACTIONS -> {
                    current += FRACTIONS.getValue(w)
                    any = true
                }
                (w == "do" || w == "दो") -> {
                    if (next == null || multiplier(next) == null) break
                    current += 2.0 + half
                    half = 0.0
                    any = true
                }
                unitValue(w) != null -> {
                    current += unitValue(w)!! + half
                    half = 0.0
                    any = true
                }
                multiplier(w) != null -> {
                    val m = multiplier(w)!!
                    val base = if (current == 0.0) 1.0 else current
                    if (m >= 1000.0) {
                        total += base * m
                        current = 0.0
                    } else {
                        current = base * m
                    }
                    any = true
                }
                else -> break
            }
            i++
        }
        if (!any) return null
        return (total + current) to i
    }

    private fun unitAfter(words: List<String>, end: Int): Unit {
        for (k in end until minOf(words.size, end + 2)) {
            val w = words[k]
            if (w in LITRE_WORDS) return Unit.Litres
            if (w in KILO_WORDS) return Unit.Kilos
        }
        return Unit.Rupees
    }

    private fun digitValue(w: String): Double? {
        if (w.isEmpty() || w.any { !(it in '0'..'9' || it in '०'..'९' || it == '.') }) return null
        val ascii = w.map { c -> if (c in '०'..'९') '0' + (c - '०') else c }.joinToString("")
        return ascii.toDoubleOrNull()
    }

    private fun unitValue(w: String): Double? = UNITS[w]

    private fun multiplier(w: String): Double? = MULTIPLIERS[w]

    private val MULTIPLIERS: Map<String, Double> = mapOf(
        "hundred" to 100.0, "sau" to 100.0, "सौ" to 100.0,
        "thousand" to 1000.0, "hazaar" to 1000.0, "hazar" to 1000.0, "hajar" to 1000.0, "hajaar" to 1000.0,
        "हज़ार" to 1000.0, "हजार" to 1000.0, "हज़ार" to 1000.0,
        "lakh" to 100_000.0, "lac" to 100_000.0, "लाख" to 100_000.0,
    ).mapKeys { nfc(it.key) }

    /** "Dedh sau" = 150, "dhai hazaar" = 2,500. */
    private val FRACTIONS: Map<String, Double> = mapOf(
        "dedh" to 1.5, "derh" to 1.5, "डेढ़" to 1.5, "डेढ" to 1.5, "डेढ़" to 1.5,
        "dhai" to 2.5, "dhaai" to 2.5, "ढाई" to 2.5,
    ).mapKeys { nfc(it.key) }

    private val FRACTION_PREFIX: Map<String, Double> = mapOf(
        "saadhe" to 0.5, "sadhe" to 0.5, "साढ़े" to 0.5, "साढे" to 0.5, "साढ़े" to 0.5,
        "sawa" to 0.25, "सवा" to 0.25, "paune" to -0.25, "पौने" to -0.25,
    ).mapKeys { nfc(it.key) }

    private val LITRE_WORDS = setOf("litre", "litres", "liter", "liters", "ltr", "l", "लीटर", "लिटर").map { nfc(it) }.toSet()
    private val KILO_WORDS = setOf("kilo", "kilos", "kg", "kgs", "kilogram", "किलो").map { nfc(it) }.toSet()

    private val UNITS: Map<String, Double> = buildMap {
        val english = listOf(
            "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
            "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
        )
        english.forEachIndexed { i, w -> put(w, (i + 1).toDouble()) }
        listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
            .forEachIndexed { i, w -> put(w, ((i + 2) * 10).toDouble()) }
        // Roman Hindi as drivers (and the English model) spell it.
        val roman = mapOf(
            "ek" to 1, "teen" to 3, "char" to 4, "chaar" to 4, "paanch" to 5, "panch" to 5, "chhe" to 6,
            "chhah" to 6, "che" to 6, "saat" to 7, "aath" to 8, "nau" to 9, "das" to 10, "gyarah" to 11,
            "barah" to 12, "baarah" to 12, "terah" to 13, "chaudah" to 14, "pandrah" to 15, "solah" to 16,
            "satrah" to 17, "atharah" to 18, "unnees" to 19, "bees" to 20, "pachchees" to 25, "pachees" to 25,
            "pachis" to 25, "tees" to 30, "paintees" to 35, "chalees" to 40, "chalis" to 40, "chaalis" to 40,
            "paintalees" to 45, "pachaas" to 50, "pachas" to 50, "pachpan" to 55, "saath" to 60, "sath" to 60,
            "painsath" to 65, "sattar" to 70, "pachattar" to 75, "assi" to 80, "pachasi" to 85, "nabbe" to 90,
        )
        roman.forEach { (w, n) -> put(w, n.toDouble()) }
        val devanagari = mapOf(
            "एक" to 1, "तीन" to 3, "चार" to 4, "पांच" to 5, "पाँच" to 5, "छह" to 6, "छः" to 6, "छे" to 6,
            "सात" to 7, "आठ" to 8, "नौ" to 9, "दस" to 10, "ग्यारह" to 11, "बारह" to 12, "तेरह" to 13,
            "चौदह" to 14, "पंद्रह" to 15, "पन्द्रह" to 15, "सोलह" to 16, "सत्रह" to 17, "अठारह" to 18,
            "उन्नीस" to 19, "बीस" to 20, "पच्चीस" to 25, "तीस" to 30, "पैंतीस" to 35, "चालीस" to 40,
            "पैंतालीस" to 45, "पचास" to 50, "पचपन" to 55, "साठ" to 60, "पैंसठ" to 65, "सत्तर" to 70,
            "पचहत्तर" to 75, "अस्सी" to 80, "पचासी" to 85, "नब्बे" to 90,
        )
        devanagari.forEach { (w, n) -> put(nfc(w), n.toDouble()) }
    }

    /** Recognisers write ड़ / ढ़ as one character or as letter + nukta: compare one form. */
    private fun nfc(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)
}
