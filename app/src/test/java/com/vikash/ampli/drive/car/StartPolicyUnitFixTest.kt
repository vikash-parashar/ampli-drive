package com.vikash.ampli.drive.car

import com.vikash.ampli.drive.car.StartPolicy.RunMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Greeting after a power cut: boot detection that survives a wall clock reset. */
class StartPolicyUnitFixTest {
    private val min = 60_000L

    @Test
    fun aNewBootAlwaysGreetsEvenWhenTheClockResetToTheSameTime() {
        // No clock battery and no network: every power cut starts the clock at the same date.
        val last = RunMark(wallMs = 1_514_764_830_000L, elapsedMs = 30_000L, bootCount = 7)
        val now = RunMark(wallMs = 1_514_764_840_000L, elapsedMs = 35_000L, bootCount = 8)
        assertTrue(StartPolicy.shouldRun(last, now, StartTrigger.Boot))
        // Old wall-clock rule said no: that was the missing greeting.
        assertFalse(StartPolicy.shouldRun(last.wallMs, now.wallMs))
    }

    @Test
    fun withoutBootCountAShorterUptimeMeansANewBoot() {
        val last = RunMark(1_000_000L, elapsedMs = 200_000L, bootCount = -1)
        val now = RunMark(1_010_000L, elapsedMs = 40_000L, bootCount = -1)
        assertEquals(false, StartPolicy.sameBoot(last, now))
        assertTrue(StartPolicy.shouldRun(last, now, StartTrigger.Boot))
    }

    @Test
    fun oneGreetingPerBoot() {
        val last = RunMark(1_000_000L, 40_000L, 8)
        assertFalse(StartPolicy.shouldRun(last, RunMark(1_060_000L, 100_000L, 8), StartTrigger.Boot))
        assertFalse(StartPolicy.shouldRun(last, RunMark(1_000_000L + 30 * min, 40_000L + 30 * min, 8), StartTrigger.Boot))
    }

    @Test
    fun ignitionInTheSameBootNeedsTheGap() {
        val last = RunMark(1_000_000L, 40_000L, 8)
        assertFalse(StartPolicy.shouldRun(last, RunMark(1_060_000L, 100_000L, 8), StartTrigger.Ignition))
        // ACC off (deep sleep counts in elapsedRealtime) and on again an hour later.
        assertTrue(StartPolicy.shouldRun(last, RunMark(1_000_000L + 60 * min, 40_000L + 60 * min, 8), StartTrigger.Ignition))
    }

    @Test
    fun unknownBootFallsBackToBothClocks() {
        val last = RunMark(5_000_000L, 100_000L, -1)
        assertFalse(StartPolicy.shouldRun(last, RunMark(5_060_000L, 160_000L, -1), StartTrigger.Ignition))
        assertTrue(StartPolicy.shouldRun(last, RunMark(5_060_000L, 100_000L + 10 * min, -1), StartTrigger.Ignition))
        assertTrue(StartPolicy.shouldRun(null, RunMark(1L, 1L, -1), StartTrigger.Boot))
    }

    @Test
    fun markRoundTrip() {
        val m = RunMark(1_700_000_000_000L, 123_456L, 42)
        assertEquals(m, RunMark.decode(m.encode()))
        assertNull(RunMark.decode("1,2"))
        assertNull(RunMark.decode(null))
    }

    @Test
    fun freshProcessSoonAfterBootIsABoot() {
        assertTrue(StartPolicy.isNewBoot(lastBootCount = 8, bootCount = 8, uptimeMs = 90_000L, firstInProcess = true))
        assertFalse(StartPolicy.isNewBoot(lastBootCount = 8, bootCount = 8, uptimeMs = 90_000L, firstInProcess = false))
        assertFalse(StartPolicy.isNewBoot(lastBootCount = 8, bootCount = 8, uptimeMs = 10 * min, firstInProcess = true))
    }

    @Test
    fun wakeFromDeepSleep() {
        assertTrue(StartPolicy.wokeFromSleep(lastSleptMs = 0L, sleptMs = 30 * min))
        assertFalse(StartPolicy.wokeFromSleep(lastSleptMs = 0L, sleptMs = 20_000L))
        assertFalse(StartPolicy.wokeFromSleep(lastSleptMs = -1L, sleptMs = 30 * min))
        assertTrue(StartPolicy.screenOffWasStart(offAtElapsed = 100_000L, onAtElapsed = 100_000L + 5 * min))
        assertFalse(StartPolicy.screenOffWasStart(offAtElapsed = 100_000L, onAtElapsed = 130_000L))
        assertFalse(StartPolicy.screenOffWasStart(offAtElapsed = 0L, onAtElapsed = 10 * min))
    }
}
