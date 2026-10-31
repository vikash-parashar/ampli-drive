package com.vikash.ampli.drive.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TripRecorderTest {
    private val t0 = 1_700_000_000_000L

    /** 1 fix per second at [kmh], [metres] each. */
    private fun drive(r: TripRecorder, fromS: Int, toS: Int, kmh: Float, metres: Double) {
        for (s in fromS until toS) r.onSample(t0 + s * 1000L, kmh, 28.6 + s * 1e-5, 77.2, metres)
    }

    @Test
    fun recordsDistanceDurationAndTopSpeed() {
        val r = TripRecorder()
        r.onSample(t0, 5f, 28.6, 77.2, 0.0) // not yet a trip
        assertNull(r.active)
        drive(r, 1, 301, 60f, 16.0) // 5 min, 4.8 km
        r.onSample(t0 + 301_000L, 85f, 28.7, 77.2, 20.0)
        drive(r, 302, 400, 0f, 0.0) // standing at the end
        val trip = r.finish(t0 + 520_000L, 28.71, 77.21)
        assertNotNull(trip)
        trip!!
        // The first moving fix starts the trip; its step from the previous fix is not counted.
        assertEquals(299 * 16.0 + 20.0, trip.distanceM, 0.01)
        assertEquals(t0 + 1000L, trip.startTime)
        assertEquals(85f, trip.topKmh, 0.01f)
        // Ends at the last moving fix, not when parking was confirmed.
        assertEquals(t0 + 301_000L, trip.endTime)
        assertEquals(300_000L, trip.durationMs)
        assertEquals(57.648, trip.avgKmh, 0.01)
        assertEquals(28.71, trip.endLat, 1e-9)
        assertNull(r.active)
    }

    @Test
    fun dropsShortShunts() {
        val r = TripRecorder()
        drive(r, 0, 30, 20f, 5.0)
        assertNull(r.finish(t0 + 40_000L, null, null))
    }

    @Test
    fun aLongGapClosesTheOldTrip() {
        val r = TripRecorder()
        drive(r, 0, 200, 50f, 14.0)
        val closed = r.onSample(t0 + 200_000L + 60 * 60_000L, 40f, 28.9, 77.3, 0.0)
        assertNotNull(closed)
        assertEquals(t0 + 199_000L, closed!!.endTime)
        assertNotNull(r.active)
    }

    @Test
    fun restoreKeepsARecentTripAndClosesAnOldOne() {
        val a = ActiveTrip(t0, 28.6, 77.2, t0 + 600_000L, t0 + 600_000L, 28.7, 77.3, 9000.0, 70f)
        val fresh = TripRecorder()
        assertNull(fresh.restore(a, t0 + 700_000L))
        assertNotNull(fresh.active)
        val stale = TripRecorder()
        val trip = stale.restore(a, t0 + 10 * 3_600_000L)
        assertEquals(9000.0, trip!!.distanceM, 0.01)
        assertNull(stale.active)
    }
}

class TripMathTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int) = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun trip(start: Long, km: Double) = Trip(start, start + 3_600_000L, 0.0, 0.0, 0.0, 0.0, km * 1000.0, 80f)

    @Test
    fun weekStartsOnMonday() {
        // Wednesday 22 Oct 2025; Monday was the 20th.
        val now = at(2025, 10, 22, 18)
        val trips = listOf(trip(at(2025, 10, 22, 9), 64.0), trip(at(2025, 10, 20, 8), 12.5), trip(at(2025, 10, 19, 20), 100.0))
        val w = TripMath.thisWeek(trips, now, zone)
        assertEquals(2, w.count)
        assertEquals(76.5, w.distanceKm, 1e-9)
        assertEquals(2 * 3_600_000L, w.durationMs)
    }

    @Test
    fun fuelCostNeedsMileageAndPrice() {
        assertEquals(640.0, TripMath.fuelCost(64.0, 10.0, 100.0)!!, 1e-9)
        assertNull(TripMath.fuelCost(64.0, null, 100.0))
        assertNull(TripMath.fuelCost(64.0, 10.0, null))
        assertNull(TripMath.fuelCost(64.0, 0.0, 100.0))
    }

    @Test
    fun priceFromTheLatestFill() {
        val fills = listOf(FuelFill(1L, 30.0, 3000.0, 1000.0, true), FuelFill(2L, 20.0, 2100.0, 1400.0, true))
        assertEquals(105.0, TripMath.pricePerLitre(fills)!!, 1e-9)
        assertNull(TripMath.pricePerLitre(emptyList()))
    }

    @Test
    fun keepsTheNewest200() {
        var list = emptyList<Trip>()
        for (i in 0 until 210) list = TripMath.add(list, trip(i * 10_000_000L, 1.0))
        assertEquals(TripMath.KEEP, list.size)
        assertEquals(209 * 10_000_000L, list.first().startTime)
        assertEquals(10 * 10_000_000L, list.last().startTime)
    }
}
