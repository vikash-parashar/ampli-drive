package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.prefs.VoiceStyle
import java.util.Locale

/**
 * The welcome message spoken once per car start (boot, ignition on, or the launcher showing
 * after a reboot). The Haryanvi lines live in [HaryanviLines] (event Welcome and the four
 * time-of-day events).
 *
 * The car never says the owner's name (the owner's choice): every welcome is a no-name one, and
 * the name, when set, shows only on the welcome card ("Ram Ram Vikash Ji 🙏"). That also lets the
 * recorded voice pack say every welcome.
 *
 * - Haryanvi style: an opener ("राम राम भाई, आज कित का प्लान सै?"), then a time-of-day line
 *   ("सवेरे सवेरे कित चाल पड़े ताऊ?"). Openers and time lines rotate with the stored welcome
 *   count, so the same line never plays on two starts in a row, across reboots too.
 * - Standard style: a plain Hindi (Hindi screen language and a Hindi voice) or English welcome.
 *
 * [Welcome.text] is Devanagari for the hi-IN voice; without a Hindi voice the Latin line is
 * spoken with en-IN instead. Plain Kotlin, unit-tested.
 */
object WelcomeLines {
    const val STANDARD_HI = "नमस्ते जी, एम्पली कार हब में आपका स्वागत है। सुरक्षित चलाइए।"
    const val STANDARD_EN = "Welcome to Ampli Car Hub. Have a safe drive."

    /**
     * What to say and show. [language] is the app-language tag Speaker should use ("hi" or
     * "en-IN"); [clips] the recorded-voice clips (opener + time of day) in the Haryanvi style.
     */
    data class Welcome(
        val text: String,
        val language: String,
        val card: String,
        val clips: List<String> = emptyList(),
    ) {
        val spoken: Spoken get() = Spoken(text, language, clips, caption = true)
    }

    /**
     * Picks the welcome. [count] is how many welcomes played before (stored, so the rotation
     * carries across reboots); [hindiVoice] whether the TTS engine has hi-IN; [hindiScreen]
     * whether the screen language is Hindi (or a language Hindi stands in for); [hour] the local
     * hour (0..23) for the time-of-day line. [name] only goes on the card.
     */
    fun select(style: VoiceStyle, name: String?, count: Int, hindiVoice: Boolean, hindiScreen: Boolean, hour: Int = 9): Welcome {
        val who = OwnerName.clean(name)
        val n = count.coerceAtLeast(0)
        return when (style) {
            VoiceStyle.Haryanvi -> {
                val opener = rotate(HaryanviLines.Event.Welcome, n)
                val time = rotate(HaryanviLines.timeOfDay(hour), n)
                fun text(p: Picked) = if (hindiVoice) p.line.deva else p.line.roman
                val card = if (who.isNotEmpty()) "Ram Ram $who Ji 🙏" else "Ram Ram 🙏"
                Welcome("${text(opener)} ${text(time)}", if (hindiVoice) "hi" else "en-IN", card, listOf(opener.id, time.id))
            }
            VoiceStyle.Standard -> {
                val hindi = hindiVoice && hindiScreen
                val card = when {
                    hindi && who.isNotEmpty() -> "नमस्ते $who जी 🙏"
                    hindi -> "नमस्ते जी 🙏"
                    who.isNotEmpty() -> "Welcome, $who 🙏"
                    else -> "Welcome 🙏"
                }
                Welcome(if (hindi) STANDARD_HI else STANDARD_EN, if (hindi) "hi" else "en-IN", card)
            }
        }
    }

    /** The [i]th line of [event], wrapping: consecutive [i] never give the same line. */
    private fun rotate(event: HaryanviLines.Event, i: Int): Picked {
        val lines = HaryanviLines.forEvent(event)!!
        val index = i % lines.size
        return Picked(event, index, lines[index])
    }

    /** Screen languages whose speech uses the Hindi voice (see VoiceLocales). */
    fun isHindiScreen(languageTag: String): Boolean =
        Locale.forLanguageTag(languageTag).language in setOf("hi", "bgc", "bho", "mai")
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
