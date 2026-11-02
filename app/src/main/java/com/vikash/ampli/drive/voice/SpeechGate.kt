package com.vikash.ampli.drive.voice

/** One word of a Vosk final result (`setWords(true)`) and how sure the model was of it (0..1). */
data class HeardWord(val word: String, val conf: Double)

/**
 * How sure the offline model was of one utterance: words it knew, "[unk]" tokens (sound it could
 * not fit to a phrase) and the average confidence of the known words. The system recogniser gives
 * no such numbers, so its results carry none (null) and pass every check.
 */
data class SpeechQuality(val known: Int, val unknown: Int, val meanConf: Double)

/** What one listen is for, which picks the offline grammar and how sure a result must be. */
enum class ListenMode {
    /** Right after "Hey Ampli": only the command phrases ([VoskCommands]). */
    Commands,

    /** A short answer to Ampli's question: "haan / na", "suna de", "uthao", "radd karo". */
    Replies,

    /** Free speech: a place name after "kahan chalna hai?", or a dictated message. */
    Free,

    /**
     * Right after Ampli said something in a session (the greeting, "हाँ भाई, बोल।", a question):
     * every command phrase plus the short answers and small-talk replies, with gentler
     * thresholds, because the driver is answering Ampli rather than talking over the radio.
     */
    FollowUp,
}

/**
 * Pure decisions that keep ambient talk and music from running commands (unit-tested).
 *
 * With a fixed grammar Vosk forces every sound onto one of its phrases or "[unk]", so road noise
 * or a song can come out as "navigate" or "bluetooth kholo". A result is acted on only when it is
 * a final result (never a partial), mostly known words, and confident enough; anything that opens
 * an app or starts a route needs a clearer result still; and right after an action the wake word
 * rests for a moment so the tail of a song or Ampli's own reply cannot start another one.
 */
object SpeechGate {
    const val UNK = "[unk]"

    /** Lowest average word confidence for a command phrase after the wake word. */
    const val MIN_CONF_COMMAND = 0.6

    /** Lowest average confidence for a short answer ("haan" can come out of a ringtone too). */
    const val MIN_CONF_REPLY = 0.7

    /** Lowest average confidence for free speech (a place name, a dictated message). */
    const val MIN_CONF_FREE = 0.55

    /**
     * Lowest average confidence for an answer to Ampli's own question ([ListenMode.FollowUp]).
     * Lower than [MIN_CONF_COMMAND]: the small model is less sure of a Haryanvi-accented "haan"
     * or "badhiya", and right after a question the driver is talking to Ampli. The wake word's
     * own strictness ([WakeSensitivity]) is unchanged.
     */
    const val MIN_CONF_FOLLOW_UP = 0.45

    /** [MIN_CONF_LAUNCH] / [MIN_CONF_LAUNCH_ONE_WORD] for an answer to Ampli's question. */
    const val MIN_CONF_LAUNCH_FOLLOW_UP = 0.6
    const val MIN_CONF_LAUNCH_ONE_WORD_FOLLOW_UP = 0.7

    /** An intent that opens an app or starts a route: at least two clear words at this confidence. */
    const val MIN_CONF_LAUNCH = 0.75

    /** ...or a single word this clear ("map" when "kholo" is not in the model's vocabulary). */
    const val MIN_CONF_LAUNCH_ONE_WORD = 0.85

    /** The wake word rests this long after a command ran. */
    const val ACTION_COOLDOWN_MS = 5_000L

    /** Sounds the free model writes for a cough, a breath or a word cut off; never a command. */
    private val FILLERS = setOf(
        "the", "a", "an", "uh", "um", "umm", "hmm", "hm", "mm", "huh", "ah", "oh", "eh", "er",
        "and", "i", "it", "is", "of", "to", "in", "on", "that", "this", "so", "but",
    )

    fun quality(words: List<HeardWord>): SpeechQuality {
        val known = words.filter { it.word.isNotBlank() && it.word != UNK }
        val unknown = words.count { it.word == UNK }
        val mean = if (known.isEmpty()) 0.0 else known.sumOf { it.conf } / known.size
        return SpeechQuality(known.size, unknown, mean)
    }

    /**
     * True when an offline final result is worth acting on; false means noise, and listening goes
     * on for the rest of the turn. [words] is Vosk's "result" array; without one (an older model)
     * only the cleaned text counts, as before.
     */
    fun accept(text: String, words: List<HeardWord>, mode: ListenMode): Boolean {
        if (VoskCommands.clean(text).isEmpty()) return false
        if (words.isEmpty()) return true
        val q = quality(words)
        if (q.known == 0) return false
        return when (mode) {
            // Mostly "[unk]": talk or music that happened to brush past a phrase.
            ListenMode.Commands -> q.unknown <= q.known && q.meanConf >= MIN_CONF_COMMAND
            // A ringtone or music under the answer adds "[unk]" around it, so only the answer's
            // own confidence counts here.
            ListenMode.Replies -> q.meanConf >= MIN_CONF_REPLY
            ListenMode.Free -> q.meanConf >= MIN_CONF_FREE &&
                words.any { it.word != UNK && it.word.lowercase() !in FILLERS }
            // An answer may have a breath or "arre" around it, but not be mostly unknown sound.
            ListenMode.FollowUp -> q.unknown <= q.known + 1 && q.meanConf >= MIN_CONF_FOLLOW_UP
        }
    }

    /** Intents that open another app, start a route, dial, or switch the screen off. */
    fun launches(intent: VoiceIntent): Boolean = when (intent) {
        is VoiceIntent.Navigate -> intent.destination.isNotEmpty()
        is VoiceIntent.GoSaved,
        VoiceIntent.OpenMaps,
        VoiceIntent.BluetoothSettings,
        VoiceIntent.OpenAndroidAuto,
        is VoiceIntent.CallContact,
        VoiceIntent.WhatCanISay,
        is VoiceIntent.Power,
        -> true
        else -> false
    }

    /**
     * May an intent that [launches] run on a result of [quality]? No "[unk]" at all, and two or
     * more words at [MIN_CONF_LAUNCH] (or one at [MIN_CONF_LAUNCH_ONE_WORD]). Null quality (the
     * system recogniser) always may.
     */
    fun mayLaunch(quality: SpeechQuality?, followUp: Boolean = false): Boolean {
        if (quality == null) return true
        if (quality.unknown > 0 || quality.known == 0) return false
        return if (quality.known >= 2) {
            quality.meanConf >= if (followUp) MIN_CONF_LAUNCH_FOLLOW_UP else MIN_CONF_LAUNCH
        } else {
            quality.meanConf >= if (followUp) MIN_CONF_LAUNCH_ONE_WORD_FOLLOW_UP else MIN_CONF_LAUNCH_ONE_WORD
        }
    }

    /**
     * A place name heard freely after "kahan chalna hai?" that may start a search (and so a route
     * in Google Maps): a few letters at least, and said clearly when the offline model heard it.
     */
    fun destinationOk(destination: String, quality: SpeechQuality?, followUp: Boolean = false): Boolean {
        if (destination.count { it.isLetterOrDigit() } < 3) return false
        if (quality == null) return true
        return quality.known >= 1 && quality.meanConf >= if (followUp) MIN_CONF_LAUNCH_FOLLOW_UP else MIN_CONF_LAUNCH
    }

    /** True while the wake word should still rest after a command ran at [lastActionAt]. */
    fun coolingDown(lastActionAt: Long, now: Long): Boolean =
        lastActionAt > 0L && now >= lastActionAt && now - lastActionAt < ACTION_COOLDOWN_MS

    /** Parses Vosk's "result" array entries (word, conf); a missing conf counts as sure. */
    fun words(pairs: List<Pair<String, Double>>): List<HeardWord> = pairs.map { HeardWord(it.first.lowercase(), it.second) }
}
