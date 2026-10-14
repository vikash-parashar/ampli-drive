package com.vikash.ampli.drive.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackQueueTest {
    private fun r(id: String, json: String = """{"title":"t"}""", att: List<String> = emptyList()) =
        QueuedReport(id, 1_700_000_000_000L, json, att)

    @Test
    fun encodeDecodeRoundTrip() {
        val list = listOf(
            r("a", """{"body":"line\nnext","x":"tab\tin"}""", listOf("/data/f/1.png#image/png", "/data/f/log.txt#text/plain")),
            r("b").copy(attempts = 3),
        )
        val back = FeedbackQueue.decode(FeedbackQueue.encode(list))
        assertEquals(list, back)
    }

    @Test
    fun decodeSkipsGarbageAndEmpty() {
        assertTrue(FeedbackQueue.decode(null).isEmpty())
        assertTrue(FeedbackQueue.decode("").isEmpty())
        assertEquals(1, FeedbackQueue.decode("broken line\nb\t5\t0\t\t{}").size)
    }

    @Test
    fun addKeepsNewestAndCapsSize() {
        var q = emptyList<QueuedReport>()
        repeat(FeedbackQueue.MAX_ENTRIES + 5) { q = FeedbackQueue.add(q, r("id$it")) }
        assertEquals(FeedbackQueue.MAX_ENTRIES, q.size)
        assertEquals("id5", q.first().localId)
        assertEquals("id${FeedbackQueue.MAX_ENTRIES + 4}", q.last().localId)
        // Same id again replaces, not duplicates.
        assertEquals(q.size, FeedbackQueue.add(q, r("id10")).size)
    }

    @Test
    fun outcomesByStatus() {
        assertEquals(FeedbackQueue.Next.Done, FeedbackQueue.next(201, 0))
        assertEquals(FeedbackQueue.Next.RateLimited, FeedbackQueue.next(429, 0))
        assertEquals(FeedbackQueue.Next.RateLimited, FeedbackQueue.next(429, 50))
        assertEquals(FeedbackQueue.Next.Drop, FeedbackQueue.next(400, 0))
        assertEquals(FeedbackQueue.Next.Retry, FeedbackQueue.next(null, 0))
        assertEquals(FeedbackQueue.Next.Retry, FeedbackQueue.next(503, 1))
        assertEquals(FeedbackQueue.Next.Drop, FeedbackQueue.next(null, FeedbackQueue.MAX_ATTEMPTS - 1))
    }

    @Test
    fun applyRemovesOrCountsAttempts() {
        val q = listOf(r("a"), r("b"))
        assertEquals(listOf("b"), FeedbackQueue.apply(q, q[0], FeedbackQueue.Next.Done).map { it.localId })
        assertEquals(listOf("b"), FeedbackQueue.apply(q, q[0], FeedbackQueue.Next.Drop).map { it.localId })
        val retried = FeedbackQueue.apply(q, q[1], FeedbackQueue.Next.Retry)
        assertEquals(1, retried.first { it.localId == "b" }.attempts)
        assertEquals(0, retried.first { it.localId == "a" }.attempts)
    }
}
