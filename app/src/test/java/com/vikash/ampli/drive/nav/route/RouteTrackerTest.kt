package com.vikash.ampli.drive.nav.route

import com.vikash.ampli.drive.nav.Maneuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTrackerTest {

    private fun route(): Route = (OsrmParser.parse(
        javaClass.classLoader!!.getResource("osrm/route_sample.json")!!.readText(),
    ) as OsrmParse.Ok).route

    /** [m] metres north of [p] (positive) or south. */
    private fun north(p: LatLon, m: Double) = LatLon(p.lat + m / 111_195.0, p.lon)

    @Test
    fun startOfRouteTargetsTheFirstTurn() {
        val r = route()
        val t = RouteTracker(r)
        val p = t.update(r.points[0])
        assertEquals(0, p.stepIndex)
        assertEquals(Maneuver.Left, r.steps[p.stepIndex].maneuver)
        assertEquals(r.cumM[2], p.distanceToStepM, 1.0)
        assertEquals(r.lengthM, p.remainingM, 1.0)
        assertEquals(130.2, p.remainingS, 0.5)
        assertEquals(50, p.limitKmh)
        assertEquals(90.0, p.routeBearing, 1.0)
        assertFalse(p.arrived)
    }

    @Test
    fun snapsANoisyFixOntoTheLine() {
        val r = route()
        val t = RouteTracker(r)
        // 20 m north of the middle of the first segment.
        val mid = Geo.lerp(r.points[0], r.points[1], 0.5)
        val p = t.update(north(mid, 20.0))
        assertEquals(20.0, p.distanceFromRouteM, 0.5)
        assertEquals(mid.lat, p.snapped.lat, 1e-6)
        assertEquals(mid.lon, p.snapped.lon, 1e-6)
        assertEquals(r.cumM[1] / 2, p.alongM, 1.0)
    }

    @Test
    fun stepAdvancesOncePassed() {
        val r = route()
        val t = RouteTracker(r)
        t.update(r.points[0])
        t.update(r.points[1])
        // Just before the left turn: still the left turn.
        val before = t.update(Geo.lerp(r.points[1], r.points[2], 0.95))
        assertEquals(0, before.stepIndex)
        assertTrue(before.distanceToStepM < 10)
        // 30 m into the new road: next is the roundabout.
        val after = t.update(north(r.points[2], 30.0))
        assertEquals(1, after.stepIndex)
        assertEquals(Maneuver.Roundabout, r.steps[after.stepIndex].maneuver)
        assertEquals(r.steps[1].alongM - after.alongM, after.distanceToStepM, 0.5)
        assertNull(after.limitKmh)
    }

    @Test
    fun jitterBackwardsDoesNotRepeatAPassedTurn() {
        val r = route()
        val t = RouteTracker(r)
        t.update(r.points[0])
        t.update(north(r.points[2], 30.0))
        // A fix that falls back 15 m: still past the turn.
        val p = t.update(north(r.points[2], 15.0))
        assertEquals(1, p.stepIndex)
    }

    @Test
    fun remainingTimeShrinksAlongTheRoute() {
        val r = route()
        val t = RouteTracker(r)
        val a = t.update(r.points[0]).remainingS
        val b = t.update(r.points[3]).remainingS
        val c = t.update(r.points[7]).remainingS
        assertTrue(a > b && b > c)
        assertEquals(20.0, c, 0.5) // only the flyover span is left
    }

    @Test
    fun arrivesNearTheEnd() {
        val r = route()
        val t = RouteTracker(r)
        t.update(r.points[0])
        t.update(r.points[7])
        val near = t.update(Geo.lerp(r.points[8], r.points[9], 0.9))
        assertTrue(near.arrived)
        assertEquals(Maneuver.Arrive, r.steps[near.stepIndex].maneuver)
    }

    @Test
    fun farFromTheLineIsOffRoute() {
        val r = route()
        val t = RouteTracker(r)
        val p = t.update(north(r.points[1], -120.0))
        assertTrue(p.distanceFromRouteM > RouteTracker.ON_ROUTE_M)
    }

    @Test
    fun offRouteNeedsFiveSecondsAboveFiftyMetres() {
        val d = OffRouteDetector()
        assertFalse(d.onFix(80.0, 10f, 0))
        assertFalse(d.onFix(80.0, 10f, 2_000))
        assertFalse(d.onFix(80.0, 10f, 4_999))
        assertTrue(d.onFix(80.0, 10f, 5_000))
        // Re-armed: needs another 5 s.
        assertFalse(d.onFix(80.0, 10f, 6_000))
        assertFalse(d.onFix(80.0, 10f, 10_999))
        assertTrue(d.onFix(80.0, 10f, 11_000))
    }

    @Test
    fun backOnRouteResetsTheClock() {
        val d = OffRouteDetector()
        assertFalse(d.onFix(80.0, 10f, 0))
        assertFalse(d.onFix(20.0, 10f, 3_000))
        assertFalse(d.onFix(80.0, 10f, 4_000))
        assertFalse(d.onFix(80.0, 10f, 8_000))
        assertTrue(d.onFix(80.0, 10f, 9_000))
    }

    @Test
    fun poorAccuracyFixesDontCount() {
        val d = OffRouteDetector()
        assertFalse(d.onFix(80.0, 10f, 0))
        assertFalse(d.onFix(300.0, 150f, 6_000))
        assertTrue(d.onFix(80.0, 10f, 6_000))
    }

    @Test
    fun reroutesAreRateLimited() {
        val l = RerouteLimiter(20_000)
        assertTrue(l.tryAcquire(1_000))
        assertFalse(l.tryAcquire(5_000))
        assertFalse(l.tryAcquire(20_999))
        assertTrue(l.tryAcquire(21_000))
    }
}
