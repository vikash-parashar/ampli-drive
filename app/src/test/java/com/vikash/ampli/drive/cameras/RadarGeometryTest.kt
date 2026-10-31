package com.vikash.ampli.drive.cameras

import com.vikash.ampli.drive.nav.route.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Coming up on this road", road-report expiry, average-speed zones and the Free / Pro split. */
class RadarGeometryTest {

    /** Metres to degrees of latitude. */
    private fun dLat(m: Double) = m / 111_195.0

    private fun cam(id: String, northM: Double, eastM: Double = 0.0, type: String = SpeedCamera.TYPE_SPEED, limit: Int? = 80, dir: Double? = null) =
        SpeedCamera(id, 28.9 + dLat(northM), 77.0 + eastM / (111_195.0 * Math.cos(Math.toRadians(28.9))), limit, dir, type)

    /** A straight route due north from (28.9, 77.0), 30 km long, one point per km. */
    private val route = (0..30).map { LatLon(28.9 + dLat(it * 1_000.0), 77.0) }
    private val cum = DoubleArray(route.size) { it * 1_000.0 }

    @Test
    fun aheadAlongRouteIsSortedByDistanceAlongTheRoad() {
        val items = AheadList.alongRoute(
            route, cum, carAlongM = 2_000.0,
            cameras = listOf(
                cam("far", 12_000.0, type = SpeedCamera.TYPE_RED_LIGHT, limit = null),
                cam("near", 2_350.0),
                cam("behind", 1_000.0),
                cam("offroad", 5_000.0, eastM = 400.0),
                cam("wrongway", 6_000.0, dir = 180.0),
                ReportKind.Police.let { RoadReport("r1", it, 28.9 + dLat(18_000.0), 77.0, null, 1L).asCamera() },
            ),
            zones = listOf(SpeedZone("z", 28.9 + dLat(6_100.0), 77.0, 28.9 + dLat(15_100.0), 77.0, 100, "Murthal")),
            reportTimes = mapOf("r1" to 1L),
        )
        assertEquals(listOf("near", "z", "far", "r1"), items.map { it.id })
        assertEquals(350.0, items[0].distanceM, 5.0)
        assertEquals(AheadKind.AverageZone, items[1].kind)
        assertEquals(9_000.0, items[1].zoneLengthM!!, 20.0)
        assertEquals(AheadKind.RedLight, items[2].kind)
        assertEquals(AheadKind.Police, items[3].kind)
        assertEquals(1L, items[3].reportedAt)
    }

    @Test
    fun zoneDrivenTheOtherWayIsNotAhead() {
        val items = AheadList.alongRoute(
            route, cum, 0.0, emptyList(),
            listOf(SpeedZone("back", 28.9 + dLat(9_000.0), 77.0, 28.9 + dLat(3_000.0), 77.0, 100)),
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun aheadByHeadingUsesTheConeAndDirection() {
        val cams = listOf(cam("ahead", 3_000.0), cam("side", 0.0, eastM = 3_500.0), cam("behind", -2_000.0), cam("faces", 4_000.0, dir = 180.0))
        val moving = AheadList.byHeading(28.9, 77.0, heading = 0.0, cameras = cams, zones = emptyList())
        assertEquals(listOf("ahead"), moving.map { it.id })
        // Parked: everything within 5 km, nearest first.
        val parked = AheadList.byHeading(28.9, 77.0, heading = null, cameras = cams, zones = emptyList())
        assertEquals(listOf("behind", "ahead", "side", "faces"), parked.map { it.id })
    }

    @Test
    fun projectionFindsTheOffset() {
        val p = AheadList.project(route, cum, 28.9 + dLat(4_500.0), 77.0005, 0.0, 30_000.0)!!
        assertEquals(4_500.0, p.alongM, 5.0)
        assertEquals(48.7, p.offsetM, 2.0)
        assertEquals(0.0, p.segmentBearing, 0.5)
        assertNull(AheadList.project(route, cum, 28.9, 77.0, 31_000.0, 40_000.0))
    }

    @Test
    fun reportsExpireAfterTwoHours() {
        val t0 = 1_000_000_000L
        val r = RoadReport("a", ReportKind.Jam, 28.9, 77.0, null, t0)
        assertTrue(RoadReports.isLive(r, t0))
        assertTrue(RoadReports.isLive(r, t0 + RoadReports.TTL_MS - 1))
        assertFalse(RoadReports.isLive(r, t0 + RoadReports.TTL_MS))
        // A clock that jumped back does not keep a report from the future forever.
        assertFalse(RoadReports.isLive(r.copy(time = t0 + 10 * 60_000L), t0))
        assertEquals(t0 + RoadReports.TTL_MS, RoadReports.nextExpiry(listOf(r), t0))
        assertNull(RoadReports.nextExpiry(listOf(r), t0 + RoadReports.TTL_MS))
        assertEquals(20, r.minutesAgo(t0 + 20 * 60_000L))
    }

    @Test
    fun addingReplacesTheSameReportNearbyAndDropsExpired() {
        val t0 = 1_000_000_000L
        val old = RoadReport("old", ReportKind.Police, 28.9, 77.0, null, t0 - RoadReports.TTL_MS - 1)
        val first = RoadReport("p1", ReportKind.Police, 28.9, 77.0, null, t0)
        val jam = RoadReport("j1", ReportKind.Jam, 28.9, 77.0, null, t0)
        val again = RoadReport("p2", ReportKind.Police, 28.9 + dLat(50.0), 77.0, null, t0 + 1_000)
        var list = RoadReports.add(listOf(old), first, t0)
        list = RoadReports.add(list, jam, t0)
        list = RoadReports.add(list, again, t0 + 1_000)
        assertEquals(listOf("p2", "j1"), list.map { it.id })
        // Never more than MAX_KEPT.
        var many = emptyList<RoadReport>()
        repeat(RoadReports.MAX_KEPT + 5) { i -> many = RoadReports.add(many, RoadReport("m$i", ReportKind.Jam, 28.9 + dLat(i * 500.0), 77.0, null, t0 + i), t0 + i) }
        assertEquals(RoadReports.MAX_KEPT, many.size)
        assertEquals("m${RoadReports.MAX_KEPT + 4}", many.first().id)
    }

    @Test
    fun reportsBecomeAlertPoints() {
        val c = RoadReport("x", ReportKind.MobileCamera, 28.9, 77.0, 90.0, 5L).asCamera()
        assertEquals(ReportKind.MobileCamera.type, c.type)
        assertEquals(ReportKind.MobileCamera, ReportKind.fromType(c.type))
        assertEquals(90.0, c.dir!!, 0.0)
        assertEquals(AheadKind.MobileCamera, AheadList.kindOf(c.type))
    }

    @Test
    fun zoneTrackerWalksThroughAZone() {
        val zone = SpeedZone("z", 28.9 + dLat(1_000.0), 77.0, 28.9 + dLat(10_000.0), 77.0, 100)
        val t = ZoneTracker(alertM = 500.0)
        // 2 km before: nothing yet.
        assertNull(t.update(28.9 - dLat(1_000.0), 77.0, 0.0, 90.0, 0L, listOf(zone)).state)
        // 400 m before the start: approaching.
        val near = t.update(28.9 + dLat(600.0), 77.0, 0.0, 90.0, 10_000L, listOf(zone))
        assertEquals(ZoneTracker.Phase.Approaching, near.state!!.phase)
        assertEquals(400.0, near.state!!.distanceM, 5.0)
        // At the start camera: inside, announced once.
        val start = t.update(28.9 + dLat(1_020.0), 77.0, 0.0, 90.0, 30_000L, listOf(zone))
        assertEquals(ZoneTracker.Phase.Inside, start.state!!.phase)
        assertTrue(start.events.single() is ZoneTracker.Event.Started)
        // 3 km in after 100 s: 108 km/h average, over the 100 limit.
        val mid = t.update(28.9 + dLat(4_000.0), 77.0, 0.0, 110.0, 130_000L, listOf(zone))
        assertTrue(mid.events.isEmpty())
        assertEquals(6_000.0, mid.state!!.distanceM, 10.0)
        assertEquals(107.0, mid.state!!.averageKmh!!.toDouble(), 2.0)
        assertTrue(mid.state!!.overLimit)
        // Reaching the end camera ends it.
        val end = t.update(28.9 + dLat(9_990.0), 77.0, 0.0, 90.0, 400_000L, listOf(zone))
        assertTrue(end.events.single() is ZoneTracker.Event.Ended)
        assertNull(end.state)
    }

    @Test
    fun zoneTrackerIgnoresTheOppositeDirectionAndLeavingTheCorridor() {
        val zone = SpeedZone("z", 28.9 + dLat(1_000.0), 77.0, 28.9 + dLat(10_000.0), 77.0, 100)
        val t = ZoneTracker()
        assertNull(t.update(28.9 + dLat(1_300.0), 77.0, 180.0, 90.0, 0L, listOf(zone)).state)
        t.update(28.9 + dLat(1_000.0), 77.0, 0.0, 90.0, 0L, listOf(zone))
        // Turned off onto a side road, now 5 km east of the zone.
        val left = t.update(28.9 + dLat(2_000.0), 77.052, 90.0, 60.0, 60_000L, listOf(zone))
        assertTrue(left.events.single() is ZoneTracker.Event.Ended)
    }

    @Test
    fun freeAndProSplit() {
        assertTrue(RadarTier.allowed(RadarFeature.FuelFinder, isPro = false))
        assertTrue(RadarTier.alerts(SpeedCamera.TYPE_SPEED, isPro = false))
        assertTrue(RadarTier.alerts(SpeedCamera.TYPE_AVERAGE, isPro = false))
        assertTrue(RadarTier.alerts(SpeedCamera.TYPE_USER, isPro = false))
        assertFalse(RadarTier.alerts(SpeedCamera.TYPE_RED_LIGHT, isPro = false))
        assertFalse(RadarTier.alerts(ReportKind.Police.type, isPro = false))
        assertFalse(RadarTier.allowed(RadarFeature.AheadList, isPro = false))
        assertFalse(RadarTier.allowed(RadarFeature.AverageSpeedZones, isPro = false))
        for (f in RadarFeature.entries) assertTrue(RadarTier.allowed(f, isPro = true))
    }
}
