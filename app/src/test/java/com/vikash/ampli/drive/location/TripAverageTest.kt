package com.vikash.ampli.drive.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripAverageTest {

    @Test
    fun averageIgnoresStops() {
        val t = TripAverage()
        var at = 0L
        // 60 s at 60 km/h, 60 s stopped, 60 s at 30 km/h: average over moving time is 45.
        repeat(61) { t.onSpeed(at, 60f); at += 1000 }
        repeat(60) { t.onSpeed(at, 0f); at += 1000 }
        repeat(60) { t.onSpeed(at, 30f); at += 1000 }
        assertEquals(45f, t.averageKmh!!, 1.0f)
    }

    @Test
    fun nullUntilMovingLongEnough() {
        val t = TripAverage()
        t.onSpeed(0, 50f)
        t.onSpeed(1000, 50f)
        assertNull(t.averageKmh)
    }

    @Test
    fun longStopStartsANewTrip() {
        val t = TripAverage()
        var at = 0L
        repeat(61) { t.onSpeed(at, 100f); at += 1000 }
        at += TripAverage.NEW_TRIP_GAP_MS + 1
        repeat(61) { t.onSpeed(at, 20f); at += 1000 }
        assertEquals(20f, t.averageKmh!!, 0.5f)
    }
}
