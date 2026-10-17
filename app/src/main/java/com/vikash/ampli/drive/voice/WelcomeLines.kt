package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.prefs.VoiceStyle
import java.util.Locale

/**
 * The welcome message spoken once per car start (boot, ignition on, or the launcher showing
 * after a reboot). The two Haryanvi lines are the owner's own wording; keep them as they are.
 *
 * - Haryanvi style, name set: alternates "Ram Ram {name} Ji, aaj kit ka plan hai?" and
 *   "Ram Ram Tai Aale, aaj kit ki taiyaari hai?" on successive starts.
 * - Haryanvi style, no name: always "Ram Ram Tai Aale, aaj kit ki taiyaari hai?".
 * - Standard style: a plain Hindi (Hindi screen language and a Hindi voice) or English welcome.
 *
 * [Welcome.text] is Devanagari for the hi-IN voice; without a Hindi voice the Latin line is
 * spoken with en-IN instead. Plain Kotlin, unit-tested.
 */
object WelcomeLines {
    const val NAMED_DEVA = "राम राम %1\$s जी, आज कित का प्लान सै?"
    const val NAMED_ROMAN = "Ram Ram %1\$s Ji, aaj kit ka plan hai?"
    const val TAI_DEVA = "राम राम ताई आले, आज कित की तैयारी सै?"
    const val TAI_ROMAN = "Ram Ram Tai Aale, aaj kit ki taiyaari hai?"

    const val STANDARD_HI_NAMED = "नमस्ते %1\$s जी, एम्पली कार हब में आपका स्वागत है। सुरक्षित चलाइए।"
    const val STANDARD_HI = "नमस्ते जी, एम्पली कार हब में आपका स्वागत है। सुरक्षित चलाइए।"
    const val STANDARD_EN_NAMED = "Welcome, %1\$s. Have a safe drive."
    const val STANDARD_EN = "Welcome to Ampli Car Hub. Have a safe drive."

    /** What to say and show. [language] is the app-language tag Speaker should use ("hi" or "en-IN"). */
    data class Welcome(val text: String, val language: String, val card: String, val named: Boolean)

    /**
     * Picks the welcome. [count] is how many welcomes played before (stored, so the alternation
     * carries across reboots); [hindiVoice] whether the TTS engine has hi-IN; [hindiScreen]
     * whether the screen language is Hindi (or a language Hindi stands in for).
     */
    fun select(style: VoiceStyle, name: String?, count: Int, hindiVoice: Boolean, hindiScreen: Boolean): Welcome {
        val who = OwnerName.clean(name)
        return when (style) {
            VoiceStyle.Haryanvi -> {
                val named = who.isNotEmpty() && count % 2 == 0
                val lang = if (hindiVoice) "hi" else "en-IN"
                if (named) {
                    Welcome(fill(if (hindiVoice) NAMED_DEVA else NAMED_ROMAN, who), lang, "Ram Ram $who Ji 🙏", true)
                } else {
                    Welcome(if (hindiVoice) TAI_DEVA else TAI_ROMAN, lang, "Ram Ram Tai Aale 🙏", false)
                }
            }
            VoiceStyle.Standard -> {
                val hindi = hindiVoice && hindiScreen
                val named = who.isNotEmpty()
                val text = when {
                    hindi && named -> fill(STANDARD_HI_NAMED, who)
                    hindi -> STANDARD_HI
                    named -> fill(STANDARD_EN_NAMED, who)
                    else -> STANDARD_EN
                }
                val card = when {
                    hindi && named -> "नमस्ते $who जी 🙏"
                    hindi -> "नमस्ते जी 🙏"
                    named -> "Welcome, $who 🙏"
                    else -> "Welcome 🙏"
                }
                Welcome(text, if (hindi) "hi" else "en-IN", card, named)
            }
        }
    }

    /** Screen languages whose speech uses the Hindi voice (see VoiceLocales). */
    fun isHindiScreen(languageTag: String): Boolean =
        Locale.forLanguageTag(languageTag).language in setOf("hi", "bgc", "bho", "mai")

    private fun fill(template: String, name: String): String = String.format(Locale.US, template, name)
}

/** The car owner's name for the welcome (Settings › Voice › Your name). Plain Kotlin, unit-tested. */
object OwnerName {
    const val MAX_LENGTH = 30

    /**
     * Trims, collapses spaces, drops a trailing "ji" / "जी" (the welcome adds its own) and caps
     * the length. Returns "" for no name.
     */
    fun clean(raw: String?): String {
        var s = raw.orEmpty().replace(Regex("\\s+"), " ").trim().trim('.', ',', '!', '?', '।').trim()
        s = s.replace(Regex("(?i)\\s+(ji|jee|जी)$"), "").trim()
        if (s.equals("ji", ignoreCase = true) || s == "जी") s = ""
        return s.take(MAX_LENGTH).trim()
    }

    /**
     * The name out of a spoken answer: "मेरा नाम विकास है" → "विकास", "my name is Vikash" →
     * "Vikash", "Vikash" → "Vikash".
     */
    fun fromSpeech(heard: String): String {
        var s = heard.replace(Regex("\\s+"), " ").trim().trim('.', ',', '!', '?', '।').trim()
        for (p in PREFIXES) {
            if (s.lowercase().startsWith("$p ")) {
                s = s.substring(p.length).trim()
                break
            }
        }
        for (suffix in SUFFIXES) {
            if (s.lowercase().endsWith(" $suffix")) {
                s = s.substring(0, s.length - suffix.length - 1).trim()
                break
            }
        }
        return clean(s)
    }

    private val PREFIXES = listOf(
        "my name is", "i am", "i'm", "this is", "name is",
        "mera naam", "mhara naam", "mhaara naam", "maara naam", "mera nam", "naam",
        "मेरा नाम", "म्हारा नाम", "म्हारा नां", "मेरा नां", "मैं", "नाम",
    )
    private val SUFFIXES = listOf("hai", "hain", "sai", "se", "है", "हैं", "सै", "से", "hoon", "hun", "हूं", "हूँ")
}
