package com.vikash.ampli.drive.nav.route

import com.vikash.ampli.drive.nav.Maneuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OsrmManeuversTest {

    private fun m(type: String, mod: String?, exit: Int? = null, road: String? = "Some Road", prev: String? = "Other Road") =
        OsrmManeuvers.map(type, mod, exit, road, prev)

    @Test
    fun turnsFollowTheModifier() {
        assertEquals(Maneuver.Left to null, m("turn", "left"))
        assertEquals(Maneuver.Right to null, m("turn", "right"))
        assertEquals(Maneuver.SlightLeft to null, m("turn", "slight left"))
        assertEquals(Maneuver.SlightRight to null, m("continue", "slight right"))
        assertEquals(Maneuver.SharpLeft to null, m("turn", "sharp left"))
        assertEquals(Maneuver.SharpRight to null, m("end of road", "sharp right"))
        assertEquals(Maneuver.UTurn to null, m("continue", "uturn"))
        assertEquals(Maneuver.Right to null, m("end of road", "right"))
        assertNull(m("turn", "straight"))
        assertNull(m("continue", null))
    }

    @Test
    fun quietSteps() {
        assertNull(m("depart", "right"))
        assertNull(m("new name", "straight"))
        assertNull(m("new name", "slight left"))
        assertNull(m("exit roundabout", "right"))
        assertNull(m("exit rotary", "left"))
        assertNull(m("notification", "straight"))
        assertNull(m("use lane", "straight"))
        assertNull(m("fork", "straight"))
        assertNull(m("merge", "straight"))
        // A real turn hidden in a name change still counts.
        assertEquals(Maneuver.Left to null, m("new name", "left"))
    }

    @Test
    fun arriveRampExitRoundabout() {
        assertEquals(Maneuver.Arrive to null, m("arrive", "left"))
        assertEquals(Maneuver.Ramp to null, m("on ramp", "slight right"))
        assertEquals(Maneuver.Exit to null, m("off ramp", "slight left"))
        assertEquals(Maneuver.Roundabout to 3, m("roundabout", "right", exit = 3))
        assertEquals(Maneuver.Roundabout to 1, m("rotary", "straight", exit = 1))
        assertEquals(Maneuver.Roundabout to null, m("roundabout", "right", exit = null))
        assertEquals(Maneuver.Right to null, m("roundabout turn", "right"))
    }

    @Test
    fun forksAndMergesKeepASide() {
        assertEquals(Maneuver.KeepLeft to null, m("fork", "slight left"))
        assertEquals(Maneuver.KeepRight to null, m("fork", "slight right"))
        assertEquals(Maneuver.KeepLeft to null, m("merge", "slight left"))
        assertEquals(Maneuver.KeepRight to null, m("merge", "slight right"))
    }

    @Test
    fun flyoverByName() {
        assertEquals(Maneuver.Flyover to null, m("fork", "slight left", road = "Rajiv Chowk Flyover"))
        assertEquals(Maneuver.Flyover to null, m("on ramp", "slight right", road = "IFFCO Chowk flyover"))
        assertEquals(Maneuver.Flyover to null, m("new name", "straight", road = "Signature Fly-over"))
        assertEquals(Maneuver.Flyover to null, m("continue", "straight", road = "धौला कुआं फ्लाईओवर"))
        // Already on the flyover: nothing new.
        assertNull(m("new name", "straight", road = "Rajiv Chowk Flyover", prev = "Rajiv Chowk Flyover"))
        // A hard turn onto a flyover is still a turn.
        assertEquals(Maneuver.Left to null, m("turn", "left", road = "Rajiv Chowk Flyover"))
        // An on-ramp that isn't a flyover is a ramp.
        assertEquals(Maneuver.Ramp to null, m("on ramp", "slight left", road = "NH 48"))
    }
}
