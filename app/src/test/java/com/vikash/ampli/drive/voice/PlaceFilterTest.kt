package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The geocoder's far-away answers to a misheard "gana suna de" are never offered. */
class PlaceFilterTest {
    private val delhiLat = 28.61
    private val delhiLon = 77.21

    @Test
    fun farResultsAreDroppedWithAFix() {
        // "Суна 3795.7 km", "Cima di Gana Bianca 6060.1 km", "Gana-Marka 8722.2 km".
        for (km in listOf(3795.7, 6060.1, 8722.2, 500.1)) assertFalse("$km", PlaceFilter.keep(0.0, 0.0, km))
        for (km in listOf(0.2, 42.0, 499.9, 500.0)) assertTrue("$km", PlaceFilter.keep(0.0, 0.0, km))
    }

    @Test
    fun withoutAFixOnlyIndia() {
        assertTrue(PlaceFilter.keep(delhiLat, delhiLon, null))
        assertTrue(PlaceFilter.keep(19.07, 72.88, null)) // Mumbai
        assertFalse(PlaceFilter.keep(46.4, 8.9, null)) // the Alps
        assertFalse(PlaceFilter.keep(61.8, 34.3, null)) // Karelia
        assertTrue(PlaceFilter.keep(46.4, 8.9, null, "IN"))
        assertFalse(PlaceFilter.keep(delhiLat, delhiLon, null, "PK"))
    }

    @Test
    fun bboxCoversFiveHundredKmAroundTheCar() {
        val parts = PlaceFilter.bbox(delhiLat, delhiLon).split(',').map { it.toDouble() }
        assertEquals(4, parts.size)
        assertTrue(parts[0] < delhiLon && parts[2] > delhiLon && parts[1] < delhiLat && parts[3] > delhiLat)
        val halfHeightKm = PlaceFilter.distanceKm(delhiLat, delhiLon, parts[3], delhiLon)
        assertEquals(500.0, halfHeightKm, 15.0)
        assertEquals("68.0,6.0,97.5,37.5", PlaceFilter.bbox(null, null))
    }

    @Test
    fun distanceIsGreatCircle() {
        // Delhi to Mumbai, about 1150 km.
        assertEquals(1150.0, PlaceFilter.distanceKm(delhiLat, delhiLon, 19.07, 72.88), 30.0)
    }
}
