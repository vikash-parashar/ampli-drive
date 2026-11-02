package com.vikash.ampli.drive.voice

/**
 * Maps the Settings › Voice sensitivity slider (1 = strict, 5 = eager) onto what Vosk can tune.
 *
 * Vosk has no keyword-spotting threshold of its own. With a fixed grammar every sound is forced
 * onto one of the phrases or "[unk]", so the two levers are:
 * - the phrase list: strict levels accept only the real wake phrases, eager levels also accept
 *   the dictionary words "Ampli" is often heard as ("amply", "ample") and "hi ampli";
 * - the word confidence Vosk reports for a final result (`setWords(true)`), and whether a
 *   partial result (faster, but less certain) may wake the assistant at all.
 *
 * Pure Kotlin, so the mapping is unit-tested.
 */
object WakeSensitivity {

    data class Tuning(
        /** Phrases in the Vosk grammar, "[unk]" excluded. */
        val phrases: List<String>,
        /** Lowest average word confidence (0..1) that counts as the wake word on a final result. */
        val minConfidence: Double,
        /**
         * Partial results may wake the assistant (quicker response, more false wakes). Only the
         * two-word phrases ("hey ampli") ever wake from a partial; the name alone never does.
         */
        val acceptPartial: Boolean,
        /**
         * Lowest confidence for just the name ("ampli" without "hey"). Higher than [minConfidence]:
         * one short word is what road noise and film songs most often get forced onto.
         */
        val soloConfidence: Double = 1.01,
    ) {
        /** JSON grammar for org.vosk.Recognizer. */
        val grammarJson: String
            get() = (phrases + "[unk]").joinToString(", ", "[", "]") { "\"" + it + "\"" }
    }

    private val CORE = listOf("hey ampli", "ok ampli", "okay ampli", "hello ampli")
    private val SOUND_ALIKE = listOf("hey amply", "ok amply", "okay amply", "hello amply")
    private val LOOSE = listOf("hey ample", "ok ample", "hello ample", "hi ampli", "hi amply")
    /** Just the name ("Ampli, gaana chala"), from level 3 (the default) up: owner's ask 2026-10-31. */
    private val SOLO = listOf("ampli", "amply")

    /**
     * Level 3 (the default) no longer wakes on partial results, and the name alone needs a clear
     * 0.8: ambient talk and music were waking it and then running a command (owner, 2026-11).
     */
    fun tuning(level: Int): Tuning = when (level.coerceIn(1, 5)) {
        1 -> Tuning(CORE, minConfidence = 0.85, acceptPartial = false)
        2 -> Tuning(CORE + SOUND_ALIKE, minConfidence = 0.75, acceptPartial = false)
        3 -> Tuning(CORE + SOUND_ALIKE + LOOSE + SOLO, minConfidence = 0.6, acceptPartial = false, soloConfidence = 0.8)
        4 -> Tuning(CORE + SOUND_ALIKE + LOOSE + SOLO, minConfidence = 0.5, acceptPartial = true, soloConfidence = 0.7)
        else -> Tuning(CORE + SOUND_ALIKE + LOOSE + SOLO, minConfidence = 0.35, acceptPartial = true, soloConfidence = 0.55)
    }

    private fun tokens(text: String): List<String> = text.lowercase().split(' ').filter { it.isNotBlank() }

    /** A partial result ("partial" text, no confidences): only a two-word wake phrase counts. */
    fun acceptsPartial(text: String, tuning: Tuning): Boolean {
        if (!tuning.acceptPartial) return false
        val words = tokens(text).filter { it != UNK }
        return words.zipWithNext().any { (a, b) -> "$a $b" in tuning.phrases }
    }

    /**
     * A final result: the "result" array of (word, conf) pairs when Vosk gave one, else just
     * the text (then only the phrase is checked, which counts as full confidence; the name
     * alone is not accepted that way).
     *
     * The name alone must also open the utterance (at most one "[unk]" before it): "Ampli, gaana
     * chala" wakes, while talk that brushes past something like "ampli" in the middle does not.
     */
    fun acceptsFinal(text: String, words: List<Pair<String, Double>>, tuning: Tuning): Boolean {
        if (words.isEmpty()) {
            val clean = tokens(text).filter { it != UNK }
            return clean.zipWithNext().any { (a, b) -> "$a $b" in tuning.phrases }
        }
        val all = words.map { it.first.lowercase() to it.second }
        // Two-word phrase anywhere, at the phrase confidence.
        val clean = all.filter { it.first != UNK }
        for (i in 0 until clean.size - 1) {
            if ("${clean[i].first} ${clean[i + 1].first}" in tuning.phrases) {
                return (clean[i].second + clean[i + 1].second) / 2.0 >= tuning.minConfidence
            }
        }
        // The name alone: near the start, and clearer.
        val solo = all.indexOfFirst { it.first != UNK && it.first in tuning.phrases }
        if (solo < 0 || solo > 1) return false
        return all[solo].second >= tuning.soloConfidence
    }

    private const val UNK = "[unk]"
}
