package com.vikash.ampli.drive.tripcost

import com.vikash.ampli.drive.nav.route.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripCostMathTest {
    private val route = listOf(LatLon(28.40, 77.00), LatLon(28.40, 77.10), LatLon(28.50, 77.10))

    private fun plaza(id: String, at: LatLon, fee: Int, pass: Boolean = true) = TollPlaza(id, id, at, fee, pass)

    @Test
    fun flagIsOff() {
        assertFalse(TripCostFlags.ENABLED)
    }

    @Test
    fun findsPlazasOnTheRouteInOrder() {
        val a = plaza("A", LatLon(28.45, 77.1002), 100) // ~20 m off the second leg
        val b = plaza("B", LatLon(28.4001, 77.05), 80) // ~11 m off the first leg
        val far = plaza("F", LatLon(28.42, 77.05), 50) // ~2 km away
        assertEquals(listOf("B", "A"), TripCostMath.plazasOnRoute(route, listOf(a, far, b)).map { it.id })
    }

    @Test
    fun estimatesTollsFuelAndPassSaving() {
        val plazas = listOf(plaza("A", route[0], 120), plaza("B", route[1], 95), plaza("S", route[2], 60, pass = false))
        val c = TripCostMath.estimate(distanceKm = 150.0, plazas = plazas, kmPerLitre = 15.0, fuelRupeesPerLitre = 95.0, hasPass = true)
        assertEquals(3, c.tolls)
        assertEquals(275, c.tollRupees)
        assertEquals(950, c.fuelRupees)
        assertEquals(2, c.passTrips)
        // 215 at pass plazas minus 2 × ₹15.375.
        assertEquals(184, c.passSavingRupees)
        assertEquals(1225, c.totalRupees)
    }

    @Test
    fun noMileageMeansNoFuelFigure() {
        val c = TripCostMath.estimate(50.0, emptyList(), kmPerLitre = null, fuelRupeesPerLitre = 100.0, hasPass = false)
        assertNull(c.fuelRupees)
        assertEquals(0, c.passSavingRupees)
    }

    @Test
    fun spokenSummary() {
        val c = TripCostMath.example()
        assertTrue(c.example)
        assertEquals("4 टोल, 385 रुपये। पेट्रोल करीब 1620 रुपये। Annual Pass से 324 रुपये बचेंगे।", TripCostMath.spoken(c, hindi = true))
        assertEquals(
            "4 tolls, 385 rupees. fuel about 1620 rupees. the Annual Pass saves 324 rupees.",
            TripCostMath.spoken(c, hindi = false),
        )
    }
}
