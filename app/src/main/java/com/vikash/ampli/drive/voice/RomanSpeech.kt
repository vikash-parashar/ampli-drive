package com.vikash.ampli.drive.voice

/**
 * Haryanvi in Latin letters is only spoken when the unit has no Hindi voice at all: then an
 * English voice (often eSpeak NG) reads it, and English spelling rules turn "sai" (सै) into
 * "sigh", "ke" (के) into "kee", "te" (तै) into "tee" and "ji" into "jai". Just before such a
 * line is spoken, the few short words that come up in almost every line are respelt the way an
 * English voice says them right ("seh", "kay", "tay", "jee"). The text on screen stays as it
 * is. Devanagari lines (any Hindi voice, the bundled one included) are never touched.
 * Plain Kotlin, unit-tested (RomanSpeechTest).
 */
object RomanSpeech {
    /** The voice tag Haryanvi lines in Latin letters are spoken with ([DesiVoice], [ChatLines]). */
    const val LANG = "en-IN"

    private val RESPELL = mapOf(
        "sai" to "seh", "se" to "seh", "sain" to "sen",
        "ke" to "kay", "kai" to "kay",
        "ne" to "nay", "nai" to "nay",
        "te" to "tay",
        "pe" to "pay", "pai" to "pay",
        "le" to "lay", "de" to "day", "re" to "ray",
        "ji" to "jee", "li" to "lee", "ri" to "ree", "di" to "dee", "gi" to "gee",
    )

    private val WORD = Regex("[A-Za-z]+")

    /** [text] respelt for an English voice when it is spoken with [LANG]; otherwise unchanged. */
    fun forVoice(text: String, voiceLanguage: String): String =
        if (voiceLanguage == LANG) respell(text) else text

    fun respell(text: String): String {
        if (text.any { it in 'ऀ'..'ॿ' }) return text
        return WORD.replace(text) { m ->
            val w = m.value
            val r = RESPELL[w.lowercase()] ?: return@replace w
            if (w[0].isUpperCase()) r.replaceFirstChar { it.uppercase() } else r
        }
    }
}
