package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.calls.VoiceReplies

/**
 * The conversation loop after Ampli has said something (unit-tested, plain Kotlin):
 *
 * wake → Ampli's line (recorded clip, system TTS or the bundled voice) → wait until that line has
 * really finished → listen chime → "Sun raha hoon…" → listen with the full command grammar plus
 * short answers ([ListenMode.FollowUp]) → act, answer, or say one short line and close.
 *
 * The pieces that decide each step live here so they can be tested without a car.
 */
object FollowUp {

    /**
     * True when Ampli's line asks the driver something, so the session keeps listening after it
     * instead of ending: the last sentence ends in a question mark ("…कित चालणा सै आज?",
     * "Where do you want to go today?"). A question in the middle ("%s? Searching right away.")
     * does not count.
     */
    fun expectsAnswer(line: String?): Boolean {
        val t = line?.trim()?.trimEnd('"', '\'', '”', '’', ')', ' ', ' ') ?: return false
        return t.endsWith("?") || t.endsWith("？")
    }

    /**
     * The listen mode for a turn: a place name after "where to?" is free speech; after any line
     * Ampli said (the greeting, "हाँ भाई, बोल।", a question) the follow-up grammar with its
     * gentler thresholds; after a bare beep (no line) the strict command grammar, as before.
     */
    fun mode(destination: Boolean, spokeLine: Boolean): ListenMode = when {
        destination -> ListenMode.Free
        spokeLine -> ListenMode.FollowUp
        else -> ListenMode.Commands
    }

    /**
     * Whether a follow-up listen may open. Only the session itself and a phone call can stop it:
     * the post-action cooldown ([SpeechGate.coolingDown]) rests the wake word only, never the
     * answer to Ampli's own question.
     */
    @Suppress("UNUSED_PARAMETER")
    fun mayListen(sessionActive: Boolean, inCall: Boolean, lastActionAt: Long, now: Long): Boolean =
        sessionActive && !inCall

    /** What a short answer to Ampli's small talk ("कैसा सै?", "कित चालणा सै?") means. */
    enum class Reply { Fine, No, Other }

    /**
     * Small-talk answers for the English model (Roman Hindi / Hinglish), so "badhiya", "sab theek"
     * or "main theek hoon" after "भाई कैसा सै?" is heard instead of rejected as "[unk]". Each one
     * must classify as [Reply.Fine] or [Reply.No] in [reply] (unit-tested).
     */
    val CHAT = listOf(
        "badhiya", "badiya", "badhiya hai", "sab badhiya", "sab theek", "sab theek hai", "theek", "theek hoon",
        "main theek hoon", "mast", "ekdam mast", "good", "fine", "very good", "i am fine", "all good",
        "kuch nahi", "nothing", "no thanks",
    )

    /** [CHAT] for the Hindi model, in Devanagari. */
    val HINDI_CHAT = listOf(
        "बढ़िया", "बढ़िया है", "सब बढ़िया", "सब ठीक", "सब ठीक है", "ठीक", "ठीक हूँ", "मैं ठीक हूँ", "मस्त", "एकदम मस्त",
        "कुछ नहीं", "कुछ ना",
    )

    private val FINE = listOf(
        "badhiya", "badiya", "badhia", "theek", "thik", "theek hoon", "mast", "good", "fine", "all good", "great",
        "raazi", "raji", "khush", "accha", "achha", "बढ़िया", "बढिया", "ठीक", "मस्त", "राजी", "खुश", "अच्छा",
    )
    private val NONE = listOf("kuch nahi", "kuch na", "nothing", "no thanks", "कुछ नहीं", "कुछ ना")

    /**
     * A plain answer to small talk: "haan / badhiya / sab theek" is [Reply.Fine] (Ampli then asks
     * where to), "nahi / rehne de / kuch nahi" is [Reply.No] (Ampli says OK and closes). Anything
     * else is [Reply.Other] and goes through the command parser as usual.
     */
    fun reply(text: String): Reply {
        val t = VoiceReplies.normalize(text)
        if (t.isEmpty()) return Reply.Other
        val padded = " $t "
        fun has(words: List<String>) = words.any { padded.contains(" " + VoiceReplies.normalize(it) + " ") }
        if (has(NONE)) return Reply.No
        return when (VoiceReplies.yesNo(t)) {
            VoiceReplies.YesNo.No -> Reply.No
            VoiceReplies.YesNo.Yes -> Reply.Fine
            VoiceReplies.YesNo.Unknown -> if (has(FINE)) Reply.Fine else Reply.Other
        }
    }

    /**
     * What to do when the follow-up heard nothing at all. After a line Ampli said, one short
     * closing line ("ठीक सै, जद मर्जी हो, हे एम्पली बोल दिए।") and the session ends; after a bare
     * beep (most likely a false wake) it ends quietly.
     */
    fun onSilence(spokeLine: Boolean): Silence = if (spokeLine) Silence.CloseWithLine else Silence.CloseQuietly

    enum class Silence { CloseWithLine, CloseQuietly }

    /**
     * An offline follow-up result that held only "[unk]" (a place name or a word outside the
     * grammar). It ends the listen at once, instead of waiting out the window, when the sound
     * lasted long enough to be a word ([UNCLEAR_MIN_S]) rather than a click or a breath.
     */
    fun unclearEndsListen(mode: ListenMode, words: List<HeardWord>, spanSeconds: Double): Boolean =
        mode == ListenMode.FollowUp && words.isNotEmpty() && words.all { it.word == SpeechGate.UNK } &&
            spanSeconds >= UNCLEAR_MIN_S

    const val UNCLEAR_MIN_S = 0.35
}

/**
 * When a spoken line has finished, for all three ways Ampli speaks (unit-tested). Each path
 * reports its own end (ExoPlayer STATE_ENDED for clips, UtteranceProgressListener.onDone for
 * the system TTS, the AudioTrack playback head for the bundled voice); these deadlines are the
 * safety net when that report never comes, sized from the clip duration or the text length so a
 * lost callback never keeps the microphone shut for long.
 */
object SpeechCompletion {
    /** Extra time after a known clip duration (decoder start, the unit's audio latency). */
    const val CLIP_GRACE_MS = 1_200L

    /** Per clip when the duration is not known yet; the old fixed wait. */
    const val CLIP_UNKNOWN_EACH_MS = 8_000L
    const val CLIP_UNKNOWN_BASE_MS = 5_000L

    /** Longest wait for a clip line whose duration is known. */
    const val CLIP_MAX_MS = 30_000L

    /**
     * The latest moment a clip line can still be playing: its known total length plus
     * [CLIP_GRACE_MS], or the old per-clip allowance while the length is unknown.
     */
    fun clipDeadlineMs(knownTotalMs: Long?, clipCount: Int): Long =
        if (knownTotalMs != null && knownTotalMs > 0) {
            (knownTotalMs + CLIP_GRACE_MS).coerceAtMost(CLIP_MAX_MS)
        } else {
            CLIP_UNKNOWN_BASE_MS + clipCount.coerceAtLeast(1) * CLIP_UNKNOWN_EACH_MS
        }

    /** Longest wait for a system-TTS line: about 8 characters a second plus start-up. */
    fun ttsDeadlineMs(chars: Int): Long = 4_000L + chars.coerceAtLeast(0) * 120L

    /** How long a line of [chars] characters takes at a slow speaking rate (~14 characters a second). */
    fun ttsEstimateMs(chars: Int): Long = 1_000L + chars.coerceAtLeast(0) * 70L

    /** The engine must report "not speaking" this long in a row before that is believed. */
    const val TTS_QUIET_MS = 400L

    /**
     * True when a system-TTS line is over although onDone never came: the engine was seen
     * speaking and has been quiet for [TTS_QUIET_MS]; or it was never seen speaking but the line
     * should be over by now ([ttsEstimateMs]); or the hard [ttsDeadlineMs] has passed.
     */
    fun ttsFinished(elapsedMs: Long, chars: Int, sawSpeaking: Boolean, quietMs: Long): Boolean = when {
        elapsedMs >= ttsDeadlineMs(chars) -> true
        sawSpeaking -> quietMs >= TTS_QUIET_MS
        else -> elapsedMs >= ttsEstimateMs(chars) && quietMs >= TTS_QUIET_MS
    }
}
