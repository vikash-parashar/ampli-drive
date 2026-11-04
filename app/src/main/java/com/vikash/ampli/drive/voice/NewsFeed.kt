package com.vikash.ampli.drive.voice

/**
 * "Aaj ki taaza khabar": headline titles from one public RSS feed (BuildConfig.NEWS_FEED_URL),
 * plain Kotlin and unit-tested. Only the item titles are read, never article bodies, and nothing is
 * ever made up: no feed, no headlines.
 */
object NewsFeed {
    /** Headlines are fetched at most once per this long. */
    const val CACHE_MS = 30L * 60 * 1000

    /** The whole fetch must finish in this time. */
    const val TIMEOUT_MS = 6_000

    /** Headlines read per answer; "aur khabar" reads the next ones. */
    const val BATCH = 3

    /** At most this many are kept from one fetch. */
    const val MAX_ITEMS = 15

    /** Longest feed read, bytes. */
    const val MAX_BYTES = 512 * 1024

    /** Who the headlines come from, said first ("PIB ki taaza khabar:"). */
    data class Source(val deva: String, val roman: String, val en: String)

    fun source(url: String): Source {
        val host = Regex("^[a-zA-Z]+://([^/:?#]+)").find(url.trim())?.groupValues?.get(1)?.lowercase().orEmpty()
        return when {
            host.endsWith("pib.gov.in") -> Source("पीआईबी", "PIB", "PIB")
            host.endsWith("newsonair.gov.in") || host.endsWith("newsonair.com") -> Source("आकाशवाणी", "Akashvani", "All India Radio")
            host.endsWith("ddnews.gov.in") -> Source("डीडी न्यूज", "DD News", "DD News")
            else -> Source("खबर", "Khabar", "the news feed")
        }
    }

    /** "PIB ki taaza khabar:" */
    fun intro(source: Source, script: ChatLines.Script): String = when (script) {
        ChatLines.Script.Deva -> "${source.deva} की ताज़ा खबर:"
        ChatLines.Script.Roman -> "${source.roman} ki taaza khabar:"
        ChatLines.Script.English -> "Headlines from ${source.en}:"
    }

    /**
     * The `<item><title>` texts of an RSS 2.0 (or Atom `<entry>`) feed, in order, cleaned up for
     * speech: CDATA unwrapped, entities decoded (also double-escaped ones like `&amp;#8216;`), tags
     * and extra spaces removed, duplicates and empty titles dropped. The channel's own title is
     * never a headline.
     */
    fun parseTitles(xml: String, max: Int = MAX_ITEMS): List<String> {
        val out = LinkedHashSet<String>()
        for (item in ITEM.findAll(xml)) {
            val raw = TITLE.find(item.value)?.groupValues?.get(1) ?: continue
            val title = clean(raw)
            if (title.isNotEmpty()) out += title
            if (out.size >= max) break
        }
        return out.toList()
    }

    private val ITEM = Regex("<(item|entry)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val TITLE = Regex("<title\\b[^>]*>(.*?)</title\\s*>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val CDATA = Regex("<!\\[CDATA\\[(.*?)]]>", RegexOption.DOT_MATCHES_ALL)

    internal fun clean(raw: String): String {
        var s = CDATA.replace(raw) { it.groupValues[1] }
        // Twice: some feeds escape the entity itself ("&amp;#8216;").
        repeat(2) { s = decodeEntities(s) }
        s = s.replace(Regex("<[^>]*>"), " ")
        s = s.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()
        return s
    }

    private val ENTITY = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);")
    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "ndash" to "–", "mdash" to "—", "hellip" to "…",
    )

    private fun decodeEntities(s: String): String = ENTITY.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { codePoint(it) } ?: m.value
            e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { codePoint(it) } ?: m.value
            else -> NAMED[e] ?: m.value
        }
    }

    private fun codePoint(cp: Int): String? = if (Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else null

    /** The headlines from [from] on, [size] of them (empty once all were read). */
    fun batch(titles: List<String>, from: Int, size: Int = BATCH): List<String> =
        if (from >= titles.size || from < 0) emptyList() else titles.subList(from, minOf(titles.size, from + size))

    /** True while a fetch at [fetchedAt] is still fresh at [now]. */
    fun fresh(fetchedAt: Long, now: Long): Boolean = fetchedAt > 0 && now >= fetchedAt && now - fetchedAt < CACHE_MS

    /**
     * What Ampli says for one batch: the source first ("PIB ki taaza khabar:"), then each title
     * as a sentence, then "say 'aur khabar' for more" while more are left.
     */
    fun speech(source: Source, titles: List<String>, script: ChatLines.Script, first: Boolean, moreLeft: Boolean): String {
        val stop = if (script == ChatLines.Script.Deva) "।" else "."
        val body = titles.joinToString(" ") { t -> if (t.last() in ".!?।") t else t + stop }
        val head = if (first) intro(source, script) + " " else ""
        val tail = if (moreLeft) " " + ChatLines.lines(ChatLines.Topic.NewsMore).first().text(script) else ""
        return head + body + tail
    }
}
