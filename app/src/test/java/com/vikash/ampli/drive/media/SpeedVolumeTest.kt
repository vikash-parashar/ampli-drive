package com.vikash.ampli.drive.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeCurveTest {

    @Test
    fun nothingBelowThirtyOrWhenOff() {
        for (l in SpeedVolumeLevel.entries) assertEquals(0, VolumeCurve.steps(l, 29f, 15))
        assertEquals(0, VolumeCurve.steps(SpeedVolumeLevel.Off, 140f, 15))
    }

    @Test
    fun stepsRiseWithSpeedAndStopAtTheLevelCap() {
        val medium = listOf(30f, 49f, 50f, 70f, 90f, 110f, 160f).map { VolumeCurve.steps(SpeedVolumeLevel.Medium, it, 15) }
        assertEquals(listOf(0, 0, 1, 2, 3, 4, 4), medium)
        assertEquals(2, VolumeCurve.steps(SpeedVolumeLevel.Low, 200f, 15))
        assertEquals(6, VolumeCurve.steps(SpeedVolumeLevel.High, 200f, 15))
        assertTrue(VolumeCurve.steps(SpeedVolumeLevel.High, 80f, 15) > VolumeCurve.steps(SpeedVolumeLevel.Low, 80f, 15))
    }

    @Test
    fun scalesToTheUnitsVolumeRange() {
        // A 30-step head unit gets twice the steps; a tiny 7-step one still gets at least one.
        assertEquals(8, VolumeCurve.steps(SpeedVolumeLevel.Medium, 120f, 30))
        assertEquals(1, VolumeCurve.steps(SpeedVolumeLevel.Low, 61f, 7))
    }

    @Test
    fun hysteresisKeepsAStepUntilFourKmhBelow() {
        val on = VolumeCurve.stepsWithHysteresis(SpeedVolumeLevel.Medium, 70f, 15, previous = 1)
        assertEquals(2, on)
        assertEquals(2, VolumeCurve.stepsWithHysteresis(SpeedVolumeLevel.Medium, 68f, 15, previous = 2))
        assertEquals(1, VolumeCurve.stepsWithHysteresis(SpeedVolumeLevel.Medium, 65f, 15, previous = 2))
    }
}

class SpeedVolumeLogicTest {

    @Test
    fun raisesAtSpeedAndReturnsToBase() {
        val l = SpeedVolumeLogic()
        assertNull(l.onSample(SpeedVolumeLevel.Medium, 20f, 8, 15))
        assertEquals(10, l.onSample(SpeedVolumeLevel.Medium, 72f, 8, 15))
        assertNull(l.onSample(SpeedVolumeLevel.Medium, 72f, 10, 15))
        assertEquals(8, l.onSample(SpeedVolumeLevel.Medium, 10f, 10, 15))
    }

    @Test
    fun neverAboveMax() {
        val l = SpeedVolumeLogic()
        l.onSample(SpeedVolumeLevel.High, 0f, 14, 15)
        assertEquals(15, l.onSample(SpeedVolumeLevel.High, 150f, 14, 15))
        assertNull(l.onSample(SpeedVolumeLevel.High, 160f, 15, 15))
    }

    @Test
    fun manualChangeBecomesTheNewBase() {
        val l = SpeedVolumeLogic()
        l.onSample(SpeedVolumeLevel.Medium, 0f, 8, 15)
        assertEquals(10, l.onSample(SpeedVolumeLevel.Medium, 75f, 8, 15))
        // The driver turns it down to 9 at speed: kept, no jump.
        assertNull(l.onSample(SpeedVolumeLevel.Medium, 75f, 9, 15))
        // Slowing down returns to the driver's 9, not the old 8.
        assertNull(l.onSample(SpeedVolumeLevel.Medium, 10f, 9, 15))
        // Speeding up again boosts from the new base.
        assertEquals(11, l.onSample(SpeedVolumeLevel.Medium, 75f, 9, 15))
    }

    @Test
    fun switchingOffRestoresTheBaseOnlyIfStillOurs() {
        val l = SpeedVolumeLogic()
        l.onSample(SpeedVolumeLevel.Medium, 0f, 5, 15)
        assertEquals(7, l.onSample(SpeedVolumeLevel.Medium, 75f, 5, 15))
        assertEquals(5, l.onSample(SpeedVolumeLevel.Off, 75f, 7, 15))

        val other = SpeedVolumeLogic()
        other.onSample(SpeedVolumeLevel.Medium, 0f, 5, 15)
        other.onSample(SpeedVolumeLevel.Medium, 75f, 5, 15)
        // Someone else changed it meanwhile: leave it alone.
        assertNull(other.onSample(SpeedVolumeLevel.Off, 75f, 12, 15))
    }
}
