package com.vikash.ampli.drive.system

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Six brightness steps like the unit's own quick app, never near-black. */
class BrightnessStepsTest {

    @Test
    fun table() {
        assertEquals(6, BrightnessSteps.COUNT)
        assertEquals(BrightnessSteps.COUNT, BrightnessSteps.LEVELS.size)
        assertArrayEquals(intArrayOf(45, 75, 110, 150, 200, 255), BrightnessSteps.LEVELS)
        assertEquals(45, BrightnessSteps.FLOOR)
        assertEquals(255, BrightnessSteps.value(6))
    }

    @Test
    fun levelsRiseAndStayAboveTheFloor() {
        for (i in 1 until BrightnessSteps.LEVELS.size) {
            assertTrue(BrightnessSteps.LEVELS[i] > BrightnessSteps.LEVELS[i - 1])
        }
        // The panel is near-black below about a quarter of 255: step 1 must stay clear of that.
        assertTrue(BrightnessSteps.value(1) >= 40)
        for (step in -3..10) {
            val v = BrightnessSteps.value(step)
            assertTrue("step $step", v in BrightnessSteps.FLOOR..BrightnessSteps.MAX)
        }
    }

    @Test
    fun valueClampsTheStep() {
        assertEquals(45, BrightnessSteps.value(0))
        assertEquals(45, BrightnessSteps.value(-1))
        assertEquals(255, BrightnessSteps.value(7))
        assertEquals(110, BrightnessSteps.value(3))
    }

    @Test
    fun stepForRoundTripsAndSnapsToTheNearest() {
        for (step in 1..6) assertEquals(step, BrightnessSteps.stepFor(BrightnessSteps.value(step)))
        assertEquals(1, BrightnessSteps.stepFor(0))
        assertEquals(1, BrightnessSteps.stepFor(10))
        assertEquals(1, BrightnessSteps.stepFor(59))
        assertEquals(2, BrightnessSteps.stepFor(61))
        assertEquals(3, BrightnessSteps.stepFor(102)) // the old "40%"
        assertEquals(5, BrightnessSteps.stepFor(210))
        assertEquals(6, BrightnessSteps.stepFor(255))
        assertEquals(6, BrightnessSteps.stepFor(400))
    }

    @Test
    fun nextStopsAtTheEnds() {
        assertEquals(4, BrightnessSteps.next(3, up = true))
        assertEquals(2, BrightnessSteps.next(3, up = false))
        assertEquals(6, BrightnessSteps.next(6, up = true))
        assertEquals(1, BrightnessSteps.next(1, up = false))
        assertEquals(2, BrightnessSteps.next(0, up = true))
    }

    @Test
    fun tapOnTheBarPicksTheSegment() {
        assertEquals(1, BrightnessSteps.fromFraction(0f))
        assertEquals(1, BrightnessSteps.fromFraction(0.1f))
        assertEquals(2, BrightnessSteps.fromFraction(0.2f))
        assertEquals(3, BrightnessSteps.fromFraction(0.4f))
        assertEquals(4, BrightnessSteps.fromFraction(0.55f))
        assertEquals(6, BrightnessSteps.fromFraction(0.99f))
        assertEquals(6, BrightnessSteps.fromFraction(1f))
        assertEquals(1, BrightnessSteps.fromFraction(-2f))
    }

    @Test
    fun nightDimIsStepTwoNotNearBlack() {
        assertEquals(2, BrightnessSteps.NIGHT_STEP)
        assertEquals(75, BrightnessSteps.value(BrightnessSteps.NIGHT_STEP))
        assertTrue(BrightnessSteps.windowValue(BrightnessSteps.NIGHT_STEP) > 0.25f)
        assertEquals(1f, BrightnessSteps.windowValue(6), 0.0001f)
    }
}
