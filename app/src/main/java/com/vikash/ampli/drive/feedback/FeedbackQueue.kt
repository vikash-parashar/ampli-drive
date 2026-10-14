package com.vikash.ampli.drive.feedback

/**
 * A report waiting to be sent: the request JSON, plus local copies of its attachments
 * (screenshots, logs) in the app's files folder.
 */
data class QueuedReport(
    val localId: String,
    val createdAt: Long,
    val json: String,
    val attachments: List<String> = emptyList(),
    val attempts: Int = 0,
)

/**
 * Offline queue for reports, stored as one prefs string. Pure, so it is unit-tested.
 *
 * Each entry is one line: `localId \t createdAt \t attempts \t attachments \t json`. The JSON
 * comes from [FeedbackJson], which never writes a raw line break or tab; attachment paths are
 * joined with '|' (app-private paths never contain it).
 */
object FeedbackQueue {
    const val MAX_ENTRIES = 20
    const val MAX_ATTEMPTS = 8

    /** What to do with a queued report after one try. */
    enum class Next { Done, Retry, Drop, RateLimited }

    fun encode(list: List<QueuedReport>): String = list.joinToString("\n") {
        listOf(it.localId, it.createdAt.toString(), it.attempts.toString(), it.attachments.joinToString("|"), it.json).joinToString("\t")
    }

    fun decode(s: String?): List<QueuedReport> {
        if (s.isNullOrBlank()) return emptyList()
        return s.split('\n').mapNotNull { line ->
            val p = line.split('\t', limit = 5)
            if (p.size < 5) return@mapNotNull null
            val created = p[1].toLongOrNull() ?: return@mapNotNull null
            QueuedReport(
                localId = p[0],
                createdAt = created,
                attempts = p[2].toIntOrNull() ?: 0,
                attachments = if (p[3].isEmpty()) emptyList() else p[3].split('|'),
                json = p[4],
            )
        }
    }

    /** Adds [r] at the end; the oldest entries go when the queue is full. */
    fun add(list: List<QueuedReport>, r: QueuedReport): List<QueuedReport> = (list.filterNot { it.localId == r.localId } + r).takeLast(MAX_ENTRIES)

    /**
     * After a try: [httpCode] is the response status, or null when the server was not reached
     * (offline, timeout). 2xx is done; 429 waits for later; other 4xx can never succeed.
     */
    fun next(httpCode: Int?, attempts: Int): Next = when {
        httpCode != null && httpCode in 200..299 -> Next.Done
        httpCode == 429 -> Next.RateLimited
        httpCode != null && httpCode in 400..499 -> Next.Drop
        attempts + 1 >= MAX_ATTEMPTS -> Next.Drop
        else -> Next.Retry
    }

    /** The queue after [r] was tried with result [n]. */
    fun apply(list: List<QueuedReport>, r: QueuedReport, n: Next): List<QueuedReport> = when (n) {
        Next.Done, Next.Drop -> list.filterNot { it.localId == r.localId }
        Next.Retry, Next.RateLimited -> list.map { if (it.localId == r.localId) it.copy(attempts = it.attempts + 1) else it }
    }
}
