package com.vikash.ampli.drive.messages

/**
 * The rules for Settings › Read WhatsApp messages aloud, as plain Kotlin (unit-tested; the
 * Android side is [WhatsAppReader]). Voice only: the message text is never drawn on screen, never
 * stored, and nothing is ever sent or replied to.
 */

/** One incoming chat message taken from a WhatsApp notification. */
data class IncomingMessage(
    /** com.whatsapp or com.whatsapp.w4b. */
    val packageName: String,
    /** Who wrote it: the contact name WhatsApp shows (or a number). */
    val sender: String,
    val text: String,
    /** A group chat (the notification's conversation is a group). */
    val group: Boolean,
    /** When WhatsApp says the message arrived (ms since epoch). */
    val timeMs: Long,
)

/** What the gate decided for one message. */
sealed interface GateResult {
    data class Speak(val sender: String, val text: String) : GateResult
    data class Skip(val reason: Reason) : GateResult

    enum class Reason { Off, NotPro, NoDisclosure, Group, Old, Duplicate, RateLimited, Empty }
}

/** Settings the gate needs (see [MessagePrefs]). */
data class GateSettings(
    val enabled: Boolean,
    val disclosureAccepted: Boolean,
    val isPro: Boolean,
    val allowGroups: Boolean,
)

/**
 * Decides, per message, whether the car reads it out. Keeps a little memory for the rate limit
 * and duplicates (WhatsApp re-posts the same notification when a second message arrives).
 */
class MessageGate(
    private val minGapMs: Long = MIN_GAP_MS,
    private val perSenderGapMs: Long = PER_SENDER_GAP_MS,
    private val windowMs: Long = WINDOW_MS,
    private val maxPerWindow: Int = MAX_PER_WINDOW,
) {
    private val spokenAt = ArrayDeque<Long>()
    private val lastBySender = HashMap<String, Long>()
    private val recent = ArrayDeque<String>()

    fun decide(msg: IncomingMessage, settings: GateSettings, nowMs: Long): GateResult {
        if (!settings.enabled) return GateResult.Skip(GateResult.Reason.Off)
        if (!settings.disclosureAccepted) return GateResult.Skip(GateResult.Reason.NoDisclosure)
        if (!settings.isPro) return GateResult.Skip(GateResult.Reason.NotPro)
        if (msg.group && !settings.allowGroups) return GateResult.Skip(GateResult.Reason.Group)
        if (nowMs - msg.timeMs > MAX_AGE_MS) return GateResult.Skip(GateResult.Reason.Old)
        val sender = cleanSender(msg.sender)
        val text = cleanText(msg.text)
        if (sender.isEmpty()) return GateResult.Skip(GateResult.Reason.Empty)

        val key = msg.packageName + "|" + sender + "|" + msg.text.trim()
        if (key in recent) return GateResult.Skip(GateResult.Reason.Duplicate)
        remember(key)

        while (spokenAt.isNotEmpty() && nowMs - spokenAt.first() > windowMs) spokenAt.removeFirst()
        val last = spokenAt.lastOrNull()
        if (last != null && nowMs - last < minGapMs) return GateResult.Skip(GateResult.Reason.RateLimited)
        if (spokenAt.size >= maxPerWindow) return GateResult.Skip(GateResult.Reason.RateLimited)
        val senderLast = lastBySender[sender]
        if (senderLast != null && nowMs - senderLast < perSenderGapMs) return GateResult.Skip(GateResult.Reason.RateLimited)

        spokenAt.addLast(nowMs)
        lastBySender[sender] = nowMs
        return GateResult.Speak(sender, text)
    }

    private fun remember(key: String) {
        recent.addLast(key)
        while (recent.size > RECENT_KEYS) recent.removeFirst()
    }

    companion object {
        /** At most one message every 20 s. */
        const val MIN_GAP_MS = 20_000L

        /** The same person at most once a minute (a burst of "hi", "where are you", "call me"). */
        const val PER_SENDER_GAP_MS = 60_000L

        /** At most 6 messages in 10 minutes; the rest wait on the phone. */
        const val WINDOW_MS = 10 * 60_000L
        const val MAX_PER_WINDOW = 6

        /** Older messages (the listener reconnecting, a backlog) are never read out. */
        const val MAX_AGE_MS = 2 * 60_000L

        /** Longest text read out; longer messages are cut at a word. */
        const val MAX_CHARS = 160

        private const val RECENT_KEYS = 50

        /** "Rahul (2 messages)" → "Rahul"; "~ Rahul" → "Rahul". */
        fun cleanSender(raw: String): String = raw
            .replace(Regex("""\s*\(\d+\s+\S+\)\s*$"""), "")
            .trim()
            .trimStart('~')
            .let(::stripSymbols)
            .trim()
            .take(40)

        /**
         * Removes emoji and other symbols (TTS reads them as "smiling face with…"), links (read
         * as letter soup) and extra spaces, then cuts long text at a word.
         */
        fun cleanText(raw: String): String {
            val noLinks = raw.replace(Regex("""https?://\S+|www\.\S+"""), " ")
            val clean = stripSymbols(noLinks).replace(Regex("""\s+"""), " ").trim()
            if (clean.length <= MAX_CHARS) return clean
            val cut = clean.take(MAX_CHARS)
            val space = cut.lastIndexOf(' ')
            return (if (space > MAX_CHARS / 2) cut.take(space) else cut).trimEnd(',', ' ', '.') + "…"
        }

        private fun stripSymbols(s: String): String {
            val sb = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val cp = s.codePointAt(i)
                val type = Character.getType(cp)
                val drop = type == Character.OTHER_SYMBOL.toInt() ||
                    type == Character.SURROGATE.toInt() ||
                    type == Character.PRIVATE_USE.toInt() ||
                    type == Character.UNASSIGNED.toInt() ||
                    cp == 0xFE0F || cp == 0x200D || cp in 0x1F3FB..0x1F3FF
                if (!drop) sb.appendCodePoint(cp)
                i += Character.charCount(cp)
            }
            return sb.toString()
        }
    }
}

/** The words the car says. Plain Hindi with a Hindi voice, else English (the en-IN voice). */
object MessagePhrases {
    /** "Rahul का मैसेज: कहाँ पहुँचे?" */
    fun full(sender: String, text: String, hindi: Boolean): String = when {
        text.isEmpty() && hindi -> "$sender का मैसेज आया है।"
        text.isEmpty() -> "Message from $sender."
        hindi -> "$sender का मैसेज: $text"
        else -> "Message from $sender: $text"
    }

    /** After the recorded "व्हाट्सऐप पै मैसेज आया सै" clip: just "Rahul: कहाँ पहुँचे?". */
    fun afterClip(sender: String, text: String): String = if (text.isEmpty()) "$sender।" else "$sender: $text"
}
