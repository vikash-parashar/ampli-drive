package com.vikash.ampli.drive.voice.tts

/**
 * Plain decisions of the bundled voice engine, without Android types (unit-tested,
 * BundledTtsTest): which engine speaks a line, which bundled voice reads a text, and how a long
 * text is cut so the first words sound while the rest is still being made.
 */
object BundledTts {
    /** Who speaks a line. */
    enum class Engine { System, Bundled, None }

    /**
     * The phone's own TTS when it works and has the wanted language; else Ampli's bundled voice
     * when it has that language (the TS7 has no TTS at all; many units have one without Hindi);
     * else whichever of the two works at all (a system voice in another language, as before, or a
     * bundled voice reading the text in its own accent); else nothing.
     */
    fun pick(systemReady: Boolean, systemHasLanguage: Boolean, bundledHasLanguage: Boolean, bundledReady: Boolean): Engine = when {
        systemReady && systemHasLanguage -> Engine.System
        bundledHasLanguage -> Engine.Bundled
        systemReady -> Engine.System
        bundledReady -> Engine.Bundled
        else -> Engine.None
    }

    /** The bundled voice language that covers [language] (a base language such as "hi"), or null. */
    fun covers(language: String, installed: Set<String>): Boolean = when (language) {
        // Haryanvi, Bhojpuri and Maithili lines are written in Devanagari like Hindi.
        "hi", "mai", "bho", "bgc" -> HINDI in installed
        "en" -> ENGLISH in installed
        else -> false
    }

    /**
     * Which installed bundled voice ([HINDI], [ENGLISH]) reads [text] for a line in
     * [parserLanguage], or null when none can. Indic script needs the Hindi voice (the English
     * voice cannot read it); Latin text goes to the voice of the line's language, else the other
     * one (the Hindi voice reads English words with an Indian accent).
     */
    fun voiceFor(text: String, parserLanguage: String, installed: Set<String>): String? {
        val indicScript = text.any { it in 'ऀ'..'෿' }
        val order = when {
            indicScript -> listOf(HINDI)
            covers(parserLanguage, setOf(HINDI)) -> listOf(HINDI, ENGLISH)
            else -> listOf(ENGLISH, HINDI)
        }
        return order.firstOrNull { it in installed }
    }

    /** Longest piece synthesised at once. */
    const val MAX_CHUNK = 160

    /**
     * Cuts [text] at sentence ends (. ! ? । ॥ and line breaks; a full stop only before a space,
     * so "1.5" and "Rs.135" stay whole), then cuts pieces longer than [MAX_CHUNK] at commas and
     * spaces. Pieces shorter than a few characters join the one before.
     */
    fun chunks(text: String): List<String> {
        val sentences = mutableListOf<String>()
        val cur = StringBuilder()
        for (i in text.indices) {
            val c = text[i]
            cur.append(c)
            val next = text.getOrNull(i + 1)
            val end = when (c) {
                '।', '॥', '!', '?', '\n' -> true
                '.' -> next == null || next.isWhitespace()
                else -> false
            }
            if (end) {
                sentences += cur.toString()
                cur.clear()
            }
        }
        if (cur.isNotEmpty()) sentences += cur.toString()

        val out = mutableListOf<String>()
        for (s in sentences.flatMap { split(it.trim()) }) {
            if (s.isBlank()) continue
            if (out.isNotEmpty() && s.count { it.isLetterOrDigit() } < 3 && out.last().length + s.length <= MAX_CHUNK) {
                out[out.size - 1] = out.last() + " " + s
            } else {
                out += s
            }
        }
        return out
    }

    private fun split(s: String): List<String> {
        if (s.length <= MAX_CHUNK) return listOf(s)
        val window = s.substring(0, MAX_CHUNK)
        val cut = listOf(window.lastIndexOf(','), window.lastIndexOf(' ')).firstOrNull { it > MAX_CHUNK / 3 } ?: MAX_CHUNK
        return listOf(s.substring(0, cut + if (cut < MAX_CHUNK && s[cut] == ',') 1 else 0).trim()) + split(s.substring(cut).trimStart(',', ' '))
    }

    const val HINDI = "hi"
    const val ENGLISH = "en"
}
