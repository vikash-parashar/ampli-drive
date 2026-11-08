package com.vikash.ampli.drive.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings › Speedometer: bands, colours and motion of the four styles (SpeedoMath, SpeedoMotion). */
class SpeedoMathTest {

    private val sos = 0xFFD7263D.toInt()

    @Test
    fun bandsFollowTheBoard() {
        assertEquals(SpeedoMath.Band.Calm, SpeedoMath.band(0f, 100))
        assertEquals(SpeedoMath.Band.Calm, SpeedoMath.band(39.9f, 100))
        assertEquals(SpeedoMath.Band.Cruise, SpeedoMath.band(40f, 100))
        assertEquals(SpeedoMath.Band.Cruise, SpeedoMath.band(79f, 100))
        assertEquals(SpeedoMath.Band.Fast, SpeedoMath.band(80f, 100))
        assertEquals(SpeedoMath.Band.Fast, SpeedoMath.band(100.5f, 100))
        assertEquals(SpeedoMath.Band.Over, SpeedoMath.band(100.6f, 100))
        // Over a 60 limit while still under 80: "Dheere karo" wins.
        assertEquals(SpeedoMath.Band.Over, SpeedoMath.band(65f, 60))
        // No known limit: never "over", however fast.
        assertEquals(SpeedoMath.Band.Fast, SpeedoMath.band(130f, null))
        assertFalse(SpeedoMath.over(130f, null))
    }

    @Test
    fun coloursBlendSmoothlyBetweenBands() {
        val p = SpeedoMath.NIGHT
        assertEquals(p.calm, SpeedoMath.bandColor(20f, 0f, p))
        assertEquals(p.cruise, SpeedoMath.bandColor(60f, 0f, p))
        assertEquals(p.fast, SpeedoMath.bandColor(95f, 0f, p))
        assertEquals(p.over, SpeedoMath.bandColor(95f, 1f, p))
        // Half way through the 36..44 blend: neither pure teal-green nor pure cyan.
        val mid = SpeedoMath.bandColor(40f, 0f, p)
        assertTrue(mid != p.calm && mid != p.cruise)
        assertEquals(SpeedoMath.mix(p.calm, p.cruise, 0.5f), mid)
        // Each step of 1 km/h changes each channel only a little (no jumps).
        var prev = SpeedoMath.bandColor(30f, 0f, p)
        var v = 30.5f
        while (v <= 90f) {
            val c = SpeedoMath.bandColor(v, 0f, p)
            for (shift in intArrayOf(16, 8, 0)) {
                assertTrue("jump at $v", kotlin.math.abs(((c ushr shift) and 0xFF) - ((prev ushr shift) and 0xFF)) <= 40)
            }
            prev = c
            v += 0.5f
        }
    }

    @Test
    fun overLimitRedIsNeverTheSosRed() {
        assertEquals(0xFFFF5C47.toInt(), SpeedoMath.NIGHT.over)
        assertEquals(0xFFE8492C.toInt(), SpeedoMath.DAY.over)
        for (pal in listOf(SpeedoMath.NIGHT, SpeedoMath.DAY)) {
            for (c in listOf(pal.calm, pal.cruise, pal.fast, pal.over)) assertTrue(c != sos)
            var v = 0f
            while (v <= 160f) {
                for (t in floatArrayOf(0f, 0.5f, 1f)) assertTrue(SpeedoMath.bandColor(v, t, pal) != sos)
                v += 1f
            }
        }
        assertEquals(SpeedoMath.DAY, SpeedoMath.palette(light = true))
        assertEquals(SpeedoMath.NIGHT, SpeedoMath.palette(light = false))
    }

    @Test
    fun reducedMotionHasNoPulseAndNoRipple() {
        for (t in floatArrayOf(0f, 0.1f, 0.37f, 2f)) {
            assertEquals(1f, SpeedoMath.pulse(t, reduced = true), 0f)
            assertEquals(0f, SpeedoMath.ripple(t, reduced = true), 0f)
        }
        // Otherwise it pulses between 0 and 1.
        val values = (0 until 100).map { SpeedoMath.pulse(it * 0.01f, reduced = false) }
        assertTrue(values.minOrNull()!! < 0.1f && values.maxOrNull()!! > 0.9f)
    }

    @Test
    fun neonSegmentsLightUpToTheSpeed() {
        assertNull(SpeedoMath.neonHeat(0, 0f))
        // At 70 km/h half of the 56 segments are lit; the one at the head is the hottest.
        val lit = (0 until SpeedoMath.NEON_SEGMENTS).count { SpeedoMath.neonHeat(it, 70f) != null }
        assertEquals(28, lit)
        assertEquals(1f, SpeedoMath.neonHeat(27, 70f)!!, 0.06f)
        assertEquals(0f, SpeedoMath.neonHeat(0, 70f)!!, 0f)
    }

    @Test
    fun roadPerspectiveGoesFromBottomToTop() {
        assertEquals(690f, SpeedoMath.roadY(0f, 410f, 690f), 0.001f)
        assertEquals(410f, SpeedoMath.roadY(1f, 410f, 690f), 0.001f)
        assertEquals(240f, SpeedoMath.roadHalf(690f, 410f, 690f, 480f, 110f), 0.001f)
        assertEquals(55f, SpeedoMath.roadHalf(410f, 410f, 690f, 480f, 110f), 0.001f)
    }

    @Test
    fun motionEasesTurnsRedAndSettles() {
        val m = SpeedoMotion()
        m.snap(0f, 80)
        repeat(30) { m.step(1f / 30f, 100f, 80, reduced = false) }
        assertTrue(m.speed > 90f)
        assertTrue(m.overT > 0.5f)
        repeat(300) { m.step(1f / 30f, 100f, 80, reduced = false) }
        assertEquals(100f, m.speed, 0.05f)
        assertEquals(1f, m.overT, 0.01f)
        assertTrue(m.settled(100f, 80))
        // The needle overshoots a little on the way (a spring), then settles.
        val n = SpeedoMotion()
        n.snap(0f, null)
        var maxNeedle = 0f
        repeat(300) {
            n.step(1f / 30f, 60f, null, reduced = false)
            maxNeedle = maxOf(maxNeedle, n.needle)
        }
        assertTrue(maxNeedle > 60f)
        assertEquals(60f, n.needle, 0.1f)
    }

    @Test
    fun reducedMotionJumpsStraightToTheValue() {
        val m = SpeedoMotion()
        m.snap(0f, 80)
        m.step(1f / 30f, 95f, 80, reduced = true)
        assertEquals(95f, m.speed, 0f)
        assertEquals(95f, m.needle, 0f)
        assertEquals(1f, m.overT, 0f)
        assertEquals(0f, m.dash, 0f)
    }
}
