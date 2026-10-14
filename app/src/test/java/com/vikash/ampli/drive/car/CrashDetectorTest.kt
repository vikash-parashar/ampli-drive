package com.vikash.ampli.drive.car

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashDetectorTest {
    private val g = CrashDetector.STANDARD_GRAVITY.toFloat()

    @Test
    fun gravityAloneIsNotAnImpact() {
        val c = CrashDetector()
        for (i in 0 until 200) assertFalse(c.onAccel(i * 20L, 0f, 0f, g, includesGravity = true))
    }

    @Test
    fun potholeBelowThresholdIsIgnored() {
        val c = CrashDetector()
        for (i in 0 until 50) c.onAccel(i * 20L, 0f, 0f, g, true)
        assertFalse(c.onAccel(1000L, 0f, 0f, g + 2.5f * g, true))
    }

    @Test
    fun hardImpactTriggersOnce() {
        val c = CrashDetector()
        for (i in 0 until 50) c.onAccel(i * 20L, 0f, 0f, g, true)
        assertTrue(c.onAccel(1000L, 6f * g, 0f, g, true))
        // Cool-down: the rebound doesn't start a second countdown.
        assertFalse(c.onAccel(1020L, -6f * g, 0f, g, true))
        assertTrue(c.onAccel(70_000L, 0f, 5f * g, 0f, false))
    }

    @Test
    fun linearAccelerationThreshold() {
        val c = CrashDetector()
        assertFalse(c.onAccel(0L, 3.9f * g, 0f, 0f, false))
        assertTrue(c.onAccel(10L, 4.1f * g, 0f, 0f, false))
    }

    @Test
    fun suddenStopFromSpeedTriggers() {
        val c = CrashDetector()
        assertFalse(c.onSpeed(0L, 62f))
        assertFalse(c.onSpeed(1000L, 60f))
        assertTrue(c.onSpeed(2000L, 0f))
    }

    @Test
    fun normalBrakingDoesNotTrigger() {
        val c = CrashDetector()
        var t = 0L
        for (v in listOf(60f, 50f, 40f, 30f, 20f, 10f, 0f)) {
            assertFalse(c.onSpeed(t, v))
            t += 1000L
        }
    }

    @Test
    fun slowTrafficStopDoesNotTrigger() {
        val c = CrashDetector()
        assertFalse(c.onSpeed(0L, 25f))
        assertFalse(c.onSpeed(1000L, 0f))
    }
}
