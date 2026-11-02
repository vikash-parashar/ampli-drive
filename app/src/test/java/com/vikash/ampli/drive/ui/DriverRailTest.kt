package com.vikash.ampli.drive.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverRailTest {
    @Test
    fun railOrderTopToBottom() {
        assertEquals(
            listOf("Back", "Home", "Apps", "PlayPause", "VolUp", "VolDown", "Mute", "Controls"),
            RailSpec.ORDER.map { it.name },
        )
    }

    @Test
    fun eightKeysFitWithRoomToSpare() {
        // Keys, gaps and padding leave a gap above Controls at any screen height.
        assertTrue(RailSpec.usedUnits() < 100f)
        // 1280x800: 80 px wide, at least 80 px tall; the rail is 104 px.
        assertTrue(RailSpec.keyHeightPx(800) >= 80f)
        assertEquals(80f, RailSpec.keyWidthPx(800), 0.5f)
        assertEquals(104f, 800 * RAIL_UNITS / 100f, 0.5f)
        // 1024x600 still has room for all eight.
        assertTrue(RailSpec.keyHeightPx(600) * RailSpec.ORDER.size < 600f)
    }

    @Test
    fun barsStillWhenPausedAndMovingWhenPlaying() {
        for (i in EqBarMotion.REST.indices) {
            assertEquals(EqBarMotion.REST[i], EqBarMotion.height(i, 0.37f, playing = false), 0f)
            for (k in 0..20) {
                val h = EqBarMotion.height(i, k / 20f, playing = true)
                assertTrue(h >= EqBarMotion.MAX * EqBarMotion.MIN_SHARE - 0.001f && h <= EqBarMotion.MAX + 0.001f)
            }
            // The loop has no seam.
            assertEquals(EqBarMotion.height(i, 0f, true), EqBarMotion.height(i, 1f, true), 0.001f)
        }
        // They do move.
        assertNotEquals(EqBarMotion.height(0, 0.1f, true), EqBarMotion.height(0, 0.3f, true), 0.01f)
    }

    @Test
    fun gaugeDialAndEmbers() {
        assertEquals((0..220 step 20).toList(), GaugeMath.TICKS)
        assertEquals(135f, GaugeMath.angleDeg(0f), 0f)
        assertEquals(405f, GaugeMath.angleDeg(220f), 0.001f)
        assertEquals(405f, GaugeMath.angleDeg(300f), 0.001f)
        assertEquals(270f, GaugeMath.angleDeg(110f), 0.001f)
        // Parked: no embers, slow breathing; more of both with speed.
        assertEquals(0f, GaugeMath.emberRate(0f), 0f)
        assertTrue(GaugeMath.emberRate(120f) > GaugeMath.emberRate(30f))
        assertTrue(GaugeMath.pulseRate(100f) > GaugeMath.pulseRate(0f))
        // Red only over a known limit.
        assertTrue(GaugeMath.over(92, 80))
        assertTrue(!GaugeMath.over(80, 80) && !GaugeMath.over(92, null) && !GaugeMath.over(null, 80))
    }

    @Test
    fun excerptIsTheFirstTwoWords() {
        assertEquals("1\nHour", titleExcerpt("1 Hour Nonstop मेरे तन में भी राम"))
        assertEquals("राम\nमेरे", titleExcerpt("राम मेरे घर आना"))
        assertEquals("Shiv", titleExcerpt("Shiv"))
        assertEquals("Om\nNamah", titleExcerpt("  Om - Namah  Shivay "))
    }

    @Test
    fun labelsShrinkButNotForever() {
        assertTrue(FitTextRule.next(16f) < 16f)
        var s = 16f
        repeat(100) { s = FitTextRule.next(s) }
        assertEquals(FitTextRule.MIN_SP, s, 0f)
    }
}
