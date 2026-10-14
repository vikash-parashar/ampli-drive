package com.vikash.ampli.drive.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FatigueTimerTest {
    private val min = 60_000L

    /** Feeds one sample a minute from [from] to [to] (minutes, exclusive) at [kmh]; returns minutes that fired. */
    private fun FatigueTimer.drive(from: Int, to: Int, kmh: Float): List<Int> =
        (from until to).filter { onSample(it * min, kmh) }

    @Test
    fun firesAtTwoHoursOfDriving() {
        val t = FatigueTimer()
        assertEquals(listOf(120), t.drive(0, 121, 60f))
        assertEquals(120 * min, t.drivingMs(120 * min))
    }

    @Test
    fun shortStopDoesNotReset() {
        val t = FatigueTimer()
        assertTrue(t.drive(0, 60, 60f).isEmpty())
        assertTrue(t.drive(60, 68, 0f).isEmpty()) // 8-minute tea stop
        assertEquals(listOf(120), t.drive(68, 121, 60f))
    }

    @Test
    fun longStopResets() {
        val t = FatigueTimer()
        t.drive(0, 100, 60f)
        t.drive(100, 112, 0f) // 12 minutes parked
        assertFalse(t.driving)
        assertTrue(t.drive(112, 200, 60f).isEmpty())
        assertEquals(listOf(232), t.drive(200, 233, 60f))
    }

    @Test
    fun gapWithoutSamplesCountsAsBreak() {
        val t = FatigueTimer()
        t.drive(0, 100, 60f)
        // Unit off for 15 minutes, then driving again.
        assertTrue(t.drive(115, 200, 60f).isEmpty())
    }

    @Test
    fun snoozeRepeatsAfterTwentyMinutes() {
        val t = FatigueTimer(thresholdMs = 60 * min)
        assertEquals(listOf(60), t.drive(0, 61, 50f))
        t.snooze(61 * min)
        assertEquals(listOf(81, 101), t.drive(61, 102, 50f))
    }

    @Test
    fun slowCrawlIsNotDriving() {
        val t = FatigueTimer(thresholdMs = 30 * min)
        assertTrue(t.drive(0, 100, 8f).isEmpty())
        assertFalse(t.driving)
    }
}
