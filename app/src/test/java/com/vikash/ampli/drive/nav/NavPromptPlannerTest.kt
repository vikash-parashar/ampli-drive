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
        assertFalse(n.onSpeed(0, 70f, 60))
        assertFalse(n.onSpeed(2_000, 70f, 60))
        assertTrue(n.onSpeed(3_000, 70f, 60))
        assertFalse(n.onSpeed(10_000, 70f, 60))
        assertFalse(n.onSpeed(40_000, 55f, 60))
        assertFalse(n.onSpeed(70_000, 70f, 60))
        assertTrue(n.onSpeed(73_500, 70f, 60))
    }

    @Test
    fun overLimitNagIgnoresSmallOverAndUnknownLimit() {
        val n = OverLimitNag()
        for (t in 0L..20_000L step 1000) {
            assertFalse(n.onSpeed(t, 62f, 60))
            assertFalse(n.onSpeed(t, 90f, null))
            assertFalse(n.onSpeed(t, null, 60))
        }
    }
}
