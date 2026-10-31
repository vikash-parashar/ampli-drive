package com.vikash.ampli.drive.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The car picture is decoded at the pane's size, never bigger than it needs to be. */
class CarHeroTest {

    // car_hero.webp
    private val w = 1280
    private val h = 714

    @Test
    fun widePaneOn1280x800KeepsFullHeight() {
        // Map pane on the 1280x800 unit: about 520 x 560 px. Cover needs 714 px tall → no sampling.
        val p = CarHeroImage.plan(w, h, 520, 560)
        assertEquals(1, p.sample)
        assertEquals(560, p.height)
        assertTrue(p.width >= 520)
    }

    @Test
    fun smallPaneSamplesDown() {
        // 1024x600 unit: about 380 x 330 px. Half size (640x357) still covers it.
        val p = CarHeroImage.plan(w, h, 380, 330)
        assertEquals(2, p.sample)
        assertTrue(p.width >= 380 && p.height >= 330)
        assertTrue(w / p.sample >= p.width && h / p.sample >= p.height)
    }

    @Test
    fun tinyPaneSamplesMore() {
        val p = CarHeroImage.plan(w, h, 150, 80)
        assertEquals(8, p.sample)
        assertTrue(p.width >= 150 && p.height >= 80)
    }

    @Test
    fun neverScalesUp() {
        val p = CarHeroImage.plan(w, h, 2560, 1600)
        assertEquals(1, p.sample)
        assertEquals(w, p.width)
        assertEquals(h, p.height)
    }

    @Test
    fun badSizesAreSafe() {
        assertEquals(1, CarHeroImage.plan(w, h, 0, 0).sample)
        assertEquals(1, CarHeroImage.plan(0, 0, 300, 200).sample)
    }

    @Test
    fun greetingAddsTheName() {
        assertEquals("Good evening, Vikash", heroGreeting("Good evening!", "Vikash"))
        assertEquals("शुभ संध्या, Vikash", heroGreeting("शुभ संध्या!", " Vikash "))
        assertEquals("Good morning!", heroGreeting("Good morning!", ""))
        assertEquals("Good morning!", heroGreeting("Good morning!", null))
    }
}
