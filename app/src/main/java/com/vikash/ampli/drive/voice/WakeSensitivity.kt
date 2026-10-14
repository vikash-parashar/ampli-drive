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
        /** Partial results may wake the assistant (quicker response, more false wakes). */
        val acceptPartial: Boolean,
    ) {
        /** JSON grammar for org.vosk.Recognizer. */
        val grammarJson: String
            get() = (phrases + "[unk]").joinToString(", ", "[", "]") { "\"" + it + "\"" }
    }

    private val CORE = listOf("hey ampli", "ok ampli", "okay ampli", "hello ampli")
    private val SOUND_ALIKE = listOf("hey amply", "ok amply", "okay amply", "hello amply")
    private val LOOSE = listOf("hey ample", "ok ample", "hello ample", "hi ampli", "hi amply")

    fun tuning(level: Int): Tuning = when (level.coerceIn(1, 5)) {
        1 -> Tuning(CORE, minConfidence = 0.85, acceptPartial = false)
        2 -> Tuning(CORE + SOUND_ALIKE, minConfidence = 0.75, acceptPartial = false)
        3 -> Tuning(CORE + SOUND_ALIKE + LOOSE, minConfidence = 0.6, acceptPartial = true)
        4 -> Tuning(CORE + SOUND_ALIKE + LOOSE, minConfidence = 0.45, acceptPartial = true)
        else -> Tuning(CORE + SOUND_ALIKE + LOOSE, minConfidence = 0.0, acceptPartial = true)
    }

    /** A partial result ("partial" text, no confidences). */
    fun acceptsPartial(text: String, tuning: Tuning): Boolean =
        tuning.acceptPartial && findPhrase(text.lowercase().split(' ').filter { it.isNotBlank() && it != "[unk]" }, tuning) >= 0

    /**
     * A final result: the "result" array of (word, conf) pairs when Vosk gave one, else just
     * the text (then only the phrase is checked, which counts as full confidence).
     */
    fun acceptsFinal(text: String, words: List<Pair<String, Double>>, tuning: Tuning): Boolean {
        if (words.isEmpty()) {
            return findPhrase(text.lowercase().split(' ').filter { it.isNotBlank() && it != "[unk]" }, tuning) >= 0
        }
        val clean = words.filter { it.first != "[unk]" }.map { it.first.lowercase() to it.second }
        val i = findPhrase(clean.map { it.first }, tuning)
        if (i < 0) return false
        return (clean[i].second + clean[i + 1].second) / 2.0 >= tuning.minConfidence
    }

    private fun findPhrase(words: List<String>, tuning: Tuning): Int =
        words.zipWithNext().indexOfFirst { (a, b) -> "$a $b" in tuning.phrases }
}
