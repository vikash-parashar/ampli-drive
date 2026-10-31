package com.vikash.ampli.drive.messages

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A crash must silence every queued WhatsApp message, not only the newest one. */
class MessageQueueTest {

    @Test
    fun cancelAllDropsEveryQueuedMessageAndLaterOnesStillPlay() = runBlocking {
        val queue = MessageQueue(coroutineContext.minusKey(Job))
        val spoken = mutableListOf<Int>()
        for (i in 1..3) queue.enqueue { delay(150); spoken += i }
        delay(20)
        assertEquals(3, queue.pending)

        assertTrue(queue.cancelAll())
        delay(700)
        assertEquals("old messages played after cancelAll", emptyList<Int>(), spoken)
        assertEquals(0, queue.pending)
        assertFalse(queue.cancelAll())

        // A message after the cancel plays, on its own.
        queue.enqueue { delay(50); spoken += 4 }
        delay(400)
        assertEquals(listOf(4), spoken)
    }

    @Test
    fun messagesPlayInOrderOneAfterAnother() = runBlocking {
        val queue = MessageQueue(coroutineContext.minusKey(Job))
        val spoken = mutableListOf<Int>()
        queue.enqueue { delay(120); spoken += 1 }
        queue.enqueue { spoken += 2 }
        queue.enqueue { delay(10); spoken += 3 }
        delay(500)
        assertEquals(listOf(1, 2, 3), spoken)
    }
}
