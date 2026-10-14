package com.vikash.ampli.drive.voice

import java.util.Locale

/**
 * Maps the app language (a tag from AppLanguages, or "" for the system language) to the
 * locales used for speech recognition and text-to-speech.
 *
 * Google speech recognition covers en-IN, hi-IN, bn-IN, ta-IN, te-IN, mr-IN, gu-IN, kn-IN,
 * ml-IN, pa-IN and ur-IN. Languages it doesn't cover borrow the closest one: Bhojpuri,
 * Haryanvi and Maithili use Hindi, Konkani uses Marathi then Hindi, Assamese falls back to
 * Bengali. Every list ends in en-IN, the last resort for both engines.
 */
object VoiceLocales {

    /** Recognition locales to try, best first. */
    fun recognition(appLanguage: String): List<String> = candidates(appLanguage)

    /** TTS locales to try, best first. */
    fun speech(appLanguage: String): List<Locale> = candidates(appLanguage).map { Locale.forLanguageTag(it) }

    /** The base language the intent parser should use ("hi", "en", "zh"…). */
    fun parserLanguage(appLanguage: String): String {
        val tag = appLanguage.ifEmpty { Locale.getDefault().toLanguageTag() }
        return when (val lang = Locale.forLanguageTag(tag).language) {
            "mai", "bho", "bgc" -> "hi"
            "" -> "en"
            else -> lang
        }
    }

    private fun candidates(appLanguage: String): List<String> {
        val tag = appLanguage.ifEmpty { Locale.getDefault().toLanguageTag() }
        val locale = Locale.forLanguageTag(tag)
        val list = when (locale.language) {
            "hi", "mai", "bho", "bgc" -> listOf("hi-IN")
            "bn" -> listOf("bn-IN")
            "ta" -> listOf("ta-IN")
            "te" -> listOf("te-IN")
            "mr" -> listOf("mr-IN")
            "gu" -> listOf("gu-IN")
            "kn" -> listOf("kn-IN")
            "ml" -> listOf("ml-IN")
            "pa" -> listOf("pa-IN")
            "ur" -> listOf("ur-IN")
            "kok" -> listOf("mr-IN", "hi-IN")
            "or" -> listOf("or-IN", "hi-IN")
            "as" -> listOf("as-IN", "bn-IN")
            "ne" -> listOf("ne-NP", "hi-IN")
            "de" -> listOf("de-DE")
            "fr" -> listOf("fr-FR")
            "zh" -> listOf("zh-CN")
            // English: keep the system's region when it has one (en-GB, en-US), else Indian English.
            "en" -> if (locale.country.isNotEmpty()) listOf(locale.toLanguageTag()) else emptyList()
            else -> listOf(locale.toLanguageTag())
        }
        return (list + "en-IN").distinct()
    }
}
