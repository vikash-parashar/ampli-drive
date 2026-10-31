package com.vikash.ampli.drive.calls

import java.text.Normalizer
import java.util.Locale

/**
 * The short spoken answers to the call and message cards, as plain Kotlin (unit-tested). The
 * recogniser hears Hindi in Devanagari or Latin letters ("haan" / "हाँ"), Haryanvi words
 * ("ठा ले", "रहण दे") and English ("yes", "answer"). Anything else is [Unknown]: the card stays
 * up for a tap and nothing happens on its own.
 */
object VoiceReplies {

    /** What the driver said to "भाई, मम्मी का फोन आ रया सै, ठा ल्यूं?". */
    enum class Call { Answer, Reject, Unknown }

    /** What the driver said to "रमेश का WhatsApp आया सै, सुणाऊं?" (and to "जवाब देणा सै?"). */
    enum class Message { Read, Skip, Reply, Unknown }

    /** A plain yes / no ("भेज दूं?"). */
    enum class YesNo { Yes, No, Unknown }

    private val ANSWER = listOf(
        "uthaa", "utha", "uthao", "utha le", "uthale", "utha lo", "tha le", "thale", "tha lyo", "thaa le",
        "haan", "han", "ha", "haa", "haanji", "haan ji", "ji haan", "yes", "yeah", "answer", "pick", "pick up", "accept",
        "baat kara", "baat karao", "baat kar", "jod", "jod de", "ok", "okay", "theek hai", "theek sai", "thik hai",
        "उठा", "उठा ले", "उठाओ", "उठा लो", "ठा ले", "ठा लो", "ठा ल्यो", "ठा ल्यूं", "हाँ", "हां", "हा", "हाँ जी", "जी हाँ",
        "बात करा", "बात करवा", "जोड़ दे", "ठीक सै", "ठीक है", "ओके",
    )
    private val REJECT = listOf(
        "kaat", "kaat de", "kat", "kat de", "kaato", "naa", "na", "nahi", "nahin", "no", "nope", "reject", "decline",
        "baad mein", "baad me", "baad main", "baad mai", "abhi nahi", "abhi na", "rehne de", "rehan de", "rahan de", "band kar", "mat utha",
        "काट", "काट दे", "काटो", "ना", "नहीं", "नही", "बाद में", "बाद मैं", "अभी नहीं", "रहण दे", "रहने दे", "बंद कर", "मत उठा",
    )
    private val READ = listOf(
        "suna", "suna de", "sunade", "sunao", "suna do", "padh", "padh de", "padho", "bata", "bata de", "read", "read it",
        "haan", "han", "haa", "yes", "yeah", "ok", "okay", "haan suna",
        "सुणा", "सुणा दे", "सुना", "सुना दे", "सुनाओ", "पढ़", "पढ़ दे", "पढ़ो", "बता", "बता दे", "हाँ", "हां", "हा", "ओके",
    )
    private val SKIP = listOf(
        "rehan de", "rahan de", "rehne de", "rahne de", "chhod", "chhod de", "chod de", "baad mein", "baad me", "baad main",
        "naa", "na", "nahi", "nahin", "no", "skip", "later", "mat suna",
        "रहण दे", "रहन दे", "रहने दे", "छोड़", "छोड़ दे", "बाद में", "बाद मैं", "ना", "नहीं", "नही", "मत सुणा", "मत सुना",
    )
    private val REPLY = listOf(
        "jawab", "jawab de", "jawaab", "jawab bhej", "reply", "answer back", "likh", "likh de", "bhej",
        "जवाब", "जवाब दे", "जवाब भेज", "लिख", "लिख दे", "रिप्लाई",
    )
    private val YES = listOf(
        "haan", "han", "haa", "ha", "haanji", "haan ji", "yes", "yeah", "ok", "okay", "bhej", "bhej de", "bhej do", "send", "send it",
        "theek hai", "theek sai", "sahi hai", "हाँ", "हां", "हा", "भेज", "भेज दे", "भेज दो", "ठीक है", "ठीक सै", "सही है", "ओके",
    )
    private val NO = listOf(
        "naa", "na", "nahi", "nahin", "no", "mat bhej", "cancel", "rehne de", "rehan de", "chhod de", "galat",
        "ना", "नहीं", "नही", "मत भेज", "रहण दे", "रहने दे", "छोड़ दे", "गलत", "कैंसल",
    )

    fun call(text: String): Call {
        val t = normalize(text)
        if (t.isEmpty()) return Call.Unknown
        // "naa, kaat de" / "mat utha": a refusal wins over a "yes" word inside it.
        val reject = matches(t, REJECT)
        val answer = matches(t, ANSWER)
        return when {
            reject && !answer -> Call.Reject
            answer && !reject -> Call.Answer
            reject && answer -> if (t.contains("mat ") || t.contains("मत ") || t.startsWith("naa") || t.startsWith("na ") || t.startsWith("ना")) Call.Reject else Call.Unknown
            else -> Call.Unknown
        }
    }

    fun message(text: String): Message {
        val t = normalize(text)
        if (t.isEmpty()) return Message.Unknown
        if (matches(t, REPLY)) return Message.Reply
        val skip = matches(t, SKIP)
        val read = matches(t, READ)
        return when {
            skip && !read -> Message.Skip
            read && !skip -> Message.Read
            skip && read -> if (t.contains("mat ") || t.contains("मत ")) Message.Skip else Message.Unknown
            else -> Message.Unknown
        }
    }

    fun yesNo(text: String): YesNo {
        val t = normalize(text)
        if (t.isEmpty()) return YesNo.Unknown
        val no = matches(t, NO)
        val yes = matches(t, YES)
        return when {
            no -> YesNo.No // "mat bhej" contains "bhej": a no always wins here (nothing is sent).
            yes -> YesNo.Yes
            else -> YesNo.Unknown
        }
    }

    /** Lower case, accents and punctuation gone, nukta folded ("पढ़" = "पढ"), single spaces. */
    fun normalize(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        val noNukta = Normalizer.normalize(lower, Normalizer.Form.NFD).replace("़", "")
        val folded = Normalizer.normalize(noNukta, Normalizer.Form.NFC)
            .replace('ं', 'ँ') // "हां" and "हाँ" are the same answer.
        val sb = StringBuilder()
        for (ch in folded) {
            sb.append(if (ch.isLetterOrDigit() || ch.isWhitespace() || Character.getType(ch) == Character.NON_SPACING_MARK.toInt() || Character.getType(ch) == Character.COMBINING_SPACING_MARK.toInt()) ch else ' ')
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private val folded: (String) -> String = { normalize(it) }

    /** True when one of [words] is in [t] as whole words. */
    private fun matches(t: String, words: List<String>): Boolean {
        val padded = " $t "
        return words.any { w -> padded.contains(" " + folded(w) + " ") }
    }
}

/** What the car says for a caller: the contact name, else the number in small groups. */
object CallerLabel {
    /** "+91 98123 45210" → "9 8 1 2 3, 4 5 2 1 0" so TTS reads digits, not "ninety-eight crore". */
    fun spokenNumber(number: String): String {
        val digits = number.filter { it.isDigit() }.let { if (it.length > 10 && it.startsWith("91")) it.drop(2) else it }
        if (digits.isEmpty()) return ""
        return digits.chunked(5).joinToString(", ") { it.toCharArray().joinToString(" ") }
    }

    /** For the card: "+91 98xxx xx210" style, only the last three digits shown when unknown. */
    fun maskedNumber(number: String): String {
        val digits = number.filter { it.isDigit() }
        if (digits.length < 6) return number.trim()
        val local = if (digits.length > 10 && digits.startsWith("91")) digits.drop(2) else digits
        val head = local.take(2)
        val tail = local.takeLast(3)
        return "+91 ${head}xxx xx$tail"
    }

    /** The name to say: the contact, else the spoken number, else null (a private number). */
    fun spoken(contact: String?, number: String?): String? =
        contact?.trim()?.takeIf { it.isNotEmpty() } ?: number?.let { spokenNumber(it) }?.takeIf { it.isNotEmpty() }
}

/**
 * The buttons on a ringing call's notification (the phone's dialer, or a head unit's Bluetooth
 * phone app). Used when Android won't let an app pick up or decline directly: the matching
 * button is pressed instead, exactly as a tap would.
 */
object CallActions {
    enum class Kind { Answer, Decline, Other }

    private val ANSWER = listOf("answer", "accept", "pick up", "pickup", "उत्तर", "स्वीकार", "उठाएं", "उठाएँ", "जवाब दें", "रिसीव", "receive")
    private val DECLINE = listOf("decline", "reject", "dismiss", "hang up", "end call", "अस्वीकार", "काटें", "कॉल काटें", "मना", "समाप्त")

    fun classify(title: CharSequence?): Kind {
        val t = title?.toString()?.lowercase()?.trim() ?: return Kind.Other
        if (t.isEmpty()) return Kind.Other
        // "अस्वीकार" contains "स्वीकार": decline is checked first.
        if (DECLINE.any { t.contains(it) }) return Kind.Decline
        if (ANSWER.any { t.contains(it) }) return Kind.Answer
        return Kind.Other
    }
}
