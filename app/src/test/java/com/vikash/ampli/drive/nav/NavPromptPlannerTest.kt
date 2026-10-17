package com.vikash.ampli.drive.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavPromptPlannerTest {

    private fun turn(m: Maneuver, d: Int?, key: String = "k") = ParsedTurn(m, d, null, key)

    @Test
    fun speaksOnceFarAndOnceNow() {
        val p = NavPromptPlanner()
        assertNull(p.onTurn(turn(Maneuver.Left, 1500)))
        val far = p.onTurn(turn(Maneuver.Left, 560))
        assertNotNull(far)
        assertFalse(far!!.now)
        assertEquals(550, far.distanceM)
        for (d in listOf(500, 400, 300, 200, 150)) assertNull("at $d", p.onTurn(turn(Maneuver.Left, d)))
        val now = p.onTurn(turn(Maneuver.Left, 110))
        assertTrue(now!!.now)
        assertNull(p.onTurn(turn(Maneuver.Left, 60)))
        assertNull(p.onTurn(turn(Maneuver.Left, 10)))
    }

    @Test
    fun firstSeenCloseSpeaksNowOnly() {
        val p = NavPromptPlanner()
        assertTrue(p.onTurn(turn(Maneuver.Right, 90))!!.now)
        assertNull(p.onTurn(turn(Maneuver.Right, 40)))
    }

    @Test
    fun newManeuverResets() {
        val p = NavPromptPlanner()
        assertNotNull(p.onTurn(turn(Maneuver.Left, 500, "a")))
        assertNotNull(p.onTurn(turn(Maneuver.Right, 500, "b")))
    }

    @Test
    fun sameTextAgainAfterADistanceJumpIsANewTurn() {
        val p = NavPromptPlanner()
        assertNotNull(p.onTurn(turn(Maneuver.Left, 500)))
        assertNotNull(p.onTurn(turn(Maneuver.Left, 100)))
        assertNotNull(p.onTurn(turn(Maneuver.Left, 580)))
    }

    @Test
    fun noDistanceSaysItOnce() {
        val p = NavPromptPlanner()
        assertNotNull(p.onTurn(turn(Maneuver.UTurn, null)))
        assertNull(p.onTurn(turn(Maneuver.UTurn, null)))
    }

    @Test
    fun arriveOnlyWhenClose() {
        val p = NavPromptPlanner()
        assertNull(p.onTurn(turn(Maneuver.Arrive, 400)))
        assertNotNull(p.onTurn(turn(Maneuver.Arrive, 140)))
        assertNull(p.onTurn(turn(Maneuver.Arrive, 50)))
    }

    @Test
    fun continueOnlyForLongStretches() {
        val p = NavPromptPlanner()
        assertNull(p.onTurn(turn(Maneuver.Continue, 400, "c1")))
        assertNotNull(p.onTurn(turn(Maneuver.Continue, 5000, "c2")))
        assertNull(p.onTurn(turn(Maneuver.Continue, 4800, "c2")))
    }

    @Test
    fun resetForgetsTheTurn() {
        val p = NavPromptPlanner()
        assertNotNull(p.onTurn(turn(Maneuver.Left, 500)))
        p.reset()
        assertNotNull(p.onTurn(turn(Maneuver.Left, 500)))
    }

    @Test
    fun rounding() {
        assertEquals(450, NavPromptPlanner.roundDistance(437))
        assertEquals(1300, NavPromptPlanner.roundDistance(1260))
        assertEquals(100, NavPromptPlanner.roundDistance(110))
    }

    @Test
    fun overLimitNagHoldsAndCoolsDown() {
        val n = OverLimitNag()
        assertEquals(OverLimitNag.Level.None, n.onSpeed(0, 70f, 60))
        assertEquals(OverLimitNag.Level.None, n.onSpeed(2_000, 70f, 60))
        assertEquals(OverLimitNag.Level.SlowDown, n.onSpeed(3_000, 70f, 60))
        assertEquals(OverLimitNag.Level.None, n.onSpeed(10_000, 70f, 60))
        // Slowed down, then over again: a fresh warning only after the cooldown.
        assertEquals(OverLimitNag.Level.None, n.onSpeed(40_000, 55f, 60))
        assertEquals(OverLimitNag.Level.None, n.onSpeed(50_000, 70f, 60))
        assertEquals(OverLimitNag.Level.SlowDown, n.onSpeed(63_000, 70f, 60))
    }

    @Test
    fun sustainedOverspeedEasesOff() {
        val n = OverLimitNag()
        n.onSpeed(0, 80f, 60)
        assertEquals(OverLimitNag.Level.SlowDown, n.onSpeed(3_000, 80f, 60))
        assertEquals(OverLimitNag.Level.None, n.onSpeed(20_000, 80f, 60))
        assertEquals(OverLimitNag.Level.EaseOff, n.onSpeed(33_000, 80f, 60))
        assertEquals(OverLimitNag.Level.None, n.onSpeed(50_000, 80f, 60))
        assertEquals(OverLimitNag.Level.EaseOff, n.onSpeed(63_000, 80f, 60))
    }

    @Test
    fun overLimitNagIgnoresSmallOverAndUnknownLimit() {
        val n = OverLimitNag()
        for (t in 0L..20_000L step 1000) {
            assertEquals(OverLimitNag.Level.None, n.onSpeed(t, 62f, 60))
            assertEquals(OverLimitNag.Level.None, n.onSpeed(t, 90f, null))
            assertEquals(OverLimitNag.Level.None, n.onSpeed(t, null, 60))
        }
    }

    @Test
    fun praiseOnceWhenBackUnderTheLimitAfterAWarning() {
        val p = BackUnderPraise()
        // No warning yet: no praise.
        assertEquals(false, p.onSpeed(0, 50f, 60))
        p.onWarned(1_000)
        assertEquals(false, p.onSpeed(2_000, 70f, 60))
        assertEquals(false, p.onSpeed(3_000, 62f, null))
        assertEquals(true, p.onSpeed(4_000, 58f, 60))
        // Only once per warning.
        assertEquals(false, p.onSpeed(5_000, 50f, 60))
    }

    @Test
    fun noPraiseLongAfterTheWarning() {
        val p = BackUnderPraise()
        p.onWarned(0)
        assertEquals(false, p.onSpeed(BackUnderPraise.WINDOW_MS + 1, 50f, 60))
        assertEquals(false, p.onSpeed(BackUnderPraise.WINDOW_MS + 2_000, 50f, 60))
    }
}
