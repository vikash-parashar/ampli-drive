package com.vikash.ampli.drive.cameras

import com.vikash.ampli.drive.car.FuelDiary
import com.vikash.ampli.drive.car.FuelFill
import com.vikash.ampli.drive.car.FuelType
import com.vikash.ampli.drive.fuel.DrivingSide
import com.vikash.ampli.drive.fuel.FuelHit
import com.vikash.ampli.drive.fuel.FuelStation
import com.vikash.ampli.drive.fuel.PumpQueues
import com.vikash.ampli.drive.fuel.PumpSide
import com.vikash.ampli.drive.fuel.QueueReport
import com.vikash.ampli.drive.map.OfflineRegions
import com.vikash.ampli.drive.nav.SavedRoute
import com.vikash.ampli.drive.nav.UnfinishedRoute
import com.vikash.ampli.drive.system.HotspotLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plain-Kotlin parts of the nine drive features. */
class NineFeaturesLogicTest {

    private val t0 = 1_700_000_000_000L

    /** Metres north of 28.9 N as degrees. */
    private fun dLat(m: Double) = m / 111_320.0

    @Test
    fun policeReportsLastTwoHoursAndMobileCamerasOne() {
        val police = RoadReport("p", ReportKind.Police, 28.9, 77.0, 90.0, t0)
        val mobile = RoadReport("m", ReportKind.MobileCamera, 28.9, 77.0, 90.0, t0)
        assertTrue(RoadReports.isLive(police, t0 + 119 * 60_000L))
        assertFalse(RoadReports.isLive(police, t0 + 120 * 60_000L))
        assertTrue(RoadReports.isLive(mobile, t0 + 59 * 60_000L))
        assertFalse(RoadReports.isLive(mobile, t0 + 60 * 60_000L))
        assertEquals(listOf("p"), RoadReports.live(listOf(police, mobile), t0 + 90 * 60_000L).map { it.id })
        assertEquals(t0 + 60 * 60_000L, RoadReports.nextExpiry(listOf(police, mobile), t0))
    }

    @Test
    fun reportsAlertOnlyInTheirDirection() {
        // Reported driving east (90°) 400 m ahead; a car driving east sees it, one driving west does not.
        val r = RoadReport("p", ReportKind.Police, 28.9, 77.0 + 400.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(28.9))), 90.0, t0)
        val east = CameraGeometry.nearestAhead(listOf(r.asCamera()), 28.9, 77.0, 90.0, 60.0, 1500.0)
        assertNotNull(east)
        val west = CameraGeometry.nearestAhead(listOf(r.asCamera()), 28.9, 77.0 + 0.01, 270.0, 60.0, 1500.0)
        assertNull(west)
    }

    @Test
    fun goneClearsOnlyNearbyReportsOfThatKind() {
        val near = RoadReport("a", ReportKind.Police, 28.9 + dLat(100.0), 77.0, null, t0)
        val far = RoadReport("b", ReportKind.Police, 28.9 + dLat(2000.0), 77.0, null, t0)
        val jam = RoadReport("c", ReportKind.Jam, 28.9, 77.0, null, t0)
        assertEquals(listOf("a"), RoadReports.near(listOf(near, far, jam), setOf(ReportKind.Police), 28.9, 77.0).map { it.id })
    }

    @Test
    fun challanCounterCountsObeyedWarningsOnce() {
        val c = ChallanSaver()
        assertFalse(c.onSample(t0, 72.0, "cam1", 60, warnedNow = true))
        assertFalse(c.onSample(t0 + 3_000, 66.0, "cam1", 60, warnedNow = false))
        assertTrue(c.onSample(t0 + 6_000, 61.0, "cam1", 60, warnedNow = false))
        // Same camera again in this pass: not counted twice.
        assertFalse(c.onSample(t0 + 7_000, 70.0, "cam1", 60, warnedNow = true))
        assertFalse(c.onSample(t0 + 8_000, 55.0, "cam1", 60, warnedNow = false))
    }

    @Test
    fun challanCounterMissesWhenTooLateOrPastTheCamera() {
        val late = ChallanSaver()
        late.onSample(t0, 80.0, "cam", 60, warnedNow = true)
        late.onSample(t0 + 10_000, 75.0, "cam", 60, warnedNow = false)
        assertFalse(late.onSample(t0 + 25_000, 58.0, "cam", 60, warnedNow = false))
        val passed = ChallanSaver()
        passed.onSample(t0, 80.0, "cam", 60, warnedNow = true)
        assertFalse(passed.onSample(t0 + 5_000, 50.0, null, null, warnedNow = false))
        assertEquals(3_000, ChallanSaver.savedLow(3))
        assertEquals(6_000, ChallanSaver.savedHigh(3))
        assertEquals(500, ChallanSaver.kmOfDriving(3, 6.0))
        assertNull(ChallanSaver.kmOfDriving(3, null))
    }

    @Test
    fun pumpQueueReportsExpireAndReplace() {
        val a = QueueReport(7, long = true, time = t0)
        val b = QueueReport(7, long = false, time = t0 + 60_000)
        val list = PumpQueues.add(listOf(a), b, t0 + 60_000)
        assertEquals(1, list.size)
        assertFalse(PumpQueues.statusFor(list, 7, t0 + 60_000)!!.long)
        assertNull(PumpQueues.statusFor(list, 7, t0 + 60_000 + PumpQueues.TTL_MS))
    }

    @Test
    fun pumpsOnTheSameSideAheadComeFirst() {
        // Car at 28.9, 77.0 driving north (0°). India keeps left: west of the road is "same side".
        val lonM = 1.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(28.9)))
        fun hit(id: Int, north: Double, east: Double): FuelHit {
            val lat = 28.9 + dLat(north)
            val lon = 77.0 + east * lonM
            return FuelHit(FuelStation(id, lat, lon, 1, ""), CameraGeometry.distanceM(28.9, 77.0, lat, lon))
        }
        val behind = hit(1, -800.0, -20.0)
        val otherSide = hit(2, 1000.0, 40.0)
        val sameSide = hit(3, 1500.0, -40.0)
        val ranked = DrivingSide.rank(listOf(behind, otherSide, sameSide), 28.9, 77.0, 0.0, 70.0)
        assertEquals(listOf(3, 2, 1), ranked.map { it.first.station.id })
        assertEquals(PumpSide.AheadSameSide, ranked[0].second)
        assertEquals(PumpSide.AheadOtherSide, ranked[1].second)
        assertEquals(PumpSide.Behind, ranked[2].second)
        // Parked: nearest first.
        assertEquals(listOf(1, 2, 3), DrivingSide.rank(listOf(behind, otherSide, sameSide), 28.9, 77.0, null, 0.0).map { it.first.station.id })
    }

    @Test
    fun voiceFillsUseTheLastPriceOrADefault() {
        val first = FuelDiary.fill(1000.0, null, FuelType.Petrol, emptyList(), 1000.0, t0)!!
        assertEquals(1000.0, first.rupees, 0.0)
        assertEquals(1000.0 / 95.0, first.litres, 0.01)
        assertTrue(first.fullTank)
        val known = listOf(FuelFill(t0, 10.0, 1000.0, 900.0, true, FuelType.Petrol))
        val byLitres = FuelDiary.fill(null, 20.0, FuelType.Petrol, known, 1300.0, t0 + 1)!!
        assertEquals(2000.0, byLitres.rupees, 0.0)
        assertNull(FuelDiary.fill(null, null, FuelType.Petrol, known, 1300.0, t0))
        assertNull(FuelDiary.fill(5.0, null, FuelType.Petrol, known, 1300.0, t0))
        // 400 km on 20 litres = 20 km/l.
        assertEquals(20.0, FuelDiary.lastKmPerLitre(known + byLitres, FuelType.Petrol)!!, 0.01)
        assertEquals(FuelType.Cng, FuelDiary.fuelFor(null, listOf(FuelFill(t0, 8.0, 720.0, 1.0, true, FuelType.Cng))))
    }

    @Test
    fun offlineRegionsFitTheTileLimitAndStorageCheck() {
        for (r in OfflineRegions.ALL) {
            val n = OfflineRegions.tileCount(r)
            assertTrue("${r.key}: $n tiles", n in 100..OfflineRegions.TILE_LIMIT)
        }
        assertTrue(OfflineRegions.tileCount(OfflineRegions.aroundMe(28.9, 77.0)) <= OfflineRegions.TILE_LIMIT)
        val r = OfflineRegions.DELHI_NCR
        assertFalse(OfflineRegions.fits(r, 100_000_000L))
        assertTrue(OfflineRegions.fits(r, 4_000_000_000L))
        assertEquals(OfflineRegions.HARYANA_NCR, OfflineRegions.byKey(null))
        assertEquals(1, OfflineRegions.tileCount(OfflineRegions.HARYANA_NCR.copy(minZoom = 0, maxZoom = 0)))
    }

    @Test
    fun hotspotJoinsOnlyWhenItMakesSense() {
        assertEquals("Vikash Phone", HotspotLogic.unquote("\"Vikash Phone\""))
        assertTrue(HotspotLogic.same("\"Vikash Phone\"", "Vikash Phone"))
        assertTrue(HotspotLogic.shouldConnect("Vikash Phone", null, onOtherWifi = false, seen = true, lastTryAt = 0L, now = t0))
        assertFalse(HotspotLogic.shouldConnect("Vikash Phone", "\"Vikash Phone\"", false, true, 0L, t0))
        assertFalse(HotspotLogic.shouldConnect("Vikash Phone", "\"Home WiFi\"", onOtherWifi = true, seen = true, lastTryAt = 0L, now = t0))
        assertFalse(HotspotLogic.shouldConnect("Vikash Phone", null, false, true, t0 - 10_000L, t0))
        assertFalse(HotspotLogic.shouldConnect(null, null, false, true, 0L, t0))
        assertTrue(HotspotLogic.isPairedPhone("Vikash's Galaxy", " vikash's galaxy "))
    }

    @Test
    fun unfinishedRouteResumesOnlyWhenFreshAndFarAway() {
        val saved = SavedRoute("Rohtak", null, 28.9 + dLat(30_000.0), 77.0, t0)
        assertTrue(UnfinishedRoute.shouldResume(saved, t0 + 30 * 60_000L, 28.9, 77.0))
        assertTrue(UnfinishedRoute.shouldResume(saved, t0 + 30 * 60_000L, null, null))
        assertFalse(UnfinishedRoute.shouldResume(saved, t0 + UnfinishedRoute.MAX_AGE_MS + 1, 28.9, 77.0))
        assertFalse(UnfinishedRoute.shouldResume(saved, t0 + 60_000L, saved.lat, saved.lon))
        assertFalse(UnfinishedRoute.shouldResume(null, t0, null, null))
    }
}
