package com.vikash.ampli.drive.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class MileageTest {
    private val zone = ZoneOffset.UTC
    private fun t(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    private val fills = listOf(
        FuelFill(t(2026, 8, 1), litres = 30.0, rupees = 3000.0, odometerKm = 10_000.0, fullTank = true),
        FuelFill(t(2026, 8, 10), litres = 10.0, rupees = 1000.0, odometerKm = 10_200.0, fullTank = false),
        FuelFill(t(2026, 8, 20), litres = 20.0, rupees = 2000.0, odometerKm = 10_450.0, fullTank = true),
        FuelFill(t(2026, 9, 5), litres = 25.0, rupees = 2600.0, odometerKm = 10_850.0, fullTank = true),
    )

    @Test
    fun fullToFullSegments() {
        val segs = Mileage.segments(fills)
        assertEquals(2, segs.size)
        assertEquals(450.0 / 30.0, segs[0].kmPerLitre, 1e-9) // partial fill counted in
        assertEquals(400.0 / 25.0, segs[1].kmPerLitre, 1e-9)
        assertEquals(850.0 / 55.0, Mileage.average(fills)!!, 1e-9)
    }

    @Test
    fun fillsBeforeFirstFullTankAreIgnored() {
        val list = listOf(FuelFill(1L, 5.0, 500.0, 9_900.0, false)) + fills
        assertEquals(2, Mileage.segments(list).size)
        assertNull(Mileage.average(fills.take(1)))
    }

    @Test
    fun costPerKmIncludesTollsAndService() {
        val expenses = listOf(
            Expense(t(2026, 8, 15), ExpenseKind.Toll, 250.0, fromFastag = true),
            Expense(t(2026, 8, 25), ExpenseKind.Service, 4000.0),
            Expense(t(2026, 10, 1), ExpenseKind.Other, 999.0), // after the last fill: not counted
        )
        val perKm = Mileage.costPerKm(fills, expenses)!!
        assertEquals((1000.0 + 2000.0 + 2600.0 + 250.0 + 4000.0) / 850.0, perKm, 1e-9)
        assertNull(Mileage.costPerKm(fills.take(1), expenses))
    }

    @Test
    fun monthlyTotals() {
        val expenses = listOf(Expense(t(2026, 9, 10), ExpenseKind.Toll, 150.0), Expense(t(2026, 9, 11), ExpenseKind.Repair, 500.0))
        val m = Mileage.monthly(fills, expenses, now = t(2026, 10, 14), months = 3, zone = zone)
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), m.map { it.month })
        assertEquals(6000.0, m[0].fuel, 1e-9)
        assertEquals(2600.0, m[1].fuel, 1e-9)
        assertEquals(150.0, m[1].toll, 1e-9)
        assertEquals(500.0, m[1].service, 1e-9)
        assertEquals(0.0, m[2].total, 1e-9)
    }

    @Test
    fun csvQuotesNotes() {
        val csv = Mileage.csv(fills.take(1), listOf(Expense(t(2026, 8, 2), ExpenseKind.Other, 50.5, "Wash, \"premium\"")), zone)
        val lines = csv.trim().split("\r\n")
        assertEquals("Date,Type,Amount (INR),Litres,Odometer (km),Full tank,Note", lines[0])
        assertEquals("2026-08-02 00:00,Other,50.50,,,,\"Wash, \"\"premium\"\"\"", lines[1])
        assertTrue(lines[2].startsWith("2026-08-01 00:00,Fuel,3000,30,10000,yes"))
    }

    @Test
    fun parkingDetector() {
        val p = ParkingDetector()
        assertNull(p.onSample(0L, 40f, 1.0, 2.0))
        assertNull(p.onSample(70_000L, 40f, 1.0, 2.0))
        assertNull(p.onSample(80_000L, 0f, 1.1, 2.1))
        val spot = p.onSample(200_000L, 0f, 1.1, 2.1)!!
        assertEquals(1.1, spot.lat, 1e-9)
        // Already parked: no second spot until the car drives again.
        assertNull(p.onSample(400_000L, 0f, 1.1, 2.1))
        assertNull(p.onStopped(500_000L, 1.1, 2.1))
        assertEquals("https://maps.google.com/?q=1.100000,2.100000", spot.mapsUrl)
    }

    @Test
    fun parkingOnStopAfterDrive() {
        val p = ParkingDetector()
        p.onSample(0L, 50f, 1.0, 2.0)
        p.onSample(90_000L, 50f, 1.0, 2.0)
        assertEquals(3.0, p.onStopped(100_000L, 3.0, 4.0)!!.lat, 1e-9)
    }
}
