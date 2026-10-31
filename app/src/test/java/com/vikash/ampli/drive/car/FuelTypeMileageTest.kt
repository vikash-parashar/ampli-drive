package com.vikash.ampli.drive.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A bi-fuel (petrol + CNG) car: each fuel's mileage between its own full fills. */
class FuelTypeMileageTest {
    private fun fill(t: Long, q: Double, rs: Double, km: Double, fuel: FuelType) = FuelFill(t, q, rs, km, fullTank = true, fuel = fuel)

    private val fills = listOf(
        fill(1, 8.0, 700.0, 47_000.0, FuelType.Cng),
        fill(2, 30.0, 2_850.0, 47_100.0, FuelType.Petrol),
        fill(3, 8.5, 748.0, 47_200.0, FuelType.Cng),
        fill(4, 8.0, 704.0, 47_390.0, FuelType.Cng),
        fill(5, 28.4, 2_698.0, 47_990.0, FuelType.Petrol),
    )

    @Test
    fun perFuelSegments() {
        assertEquals(listOf(200.0 / 8.5, 190.0 / 8.0), Mileage.trend(fills, FuelType.Cng))
        assertEquals(890.0 / 28.4, Mileage.average(fills, FuelType.Petrol)!!, 1e-9)
        assertNull(Mileage.average(fills, FuelType.Diesel))
    }

    @Test
    fun mainFuelIsTheMostFilled() {
        assertEquals(FuelType.Cng, Mileage.mainFuel(fills))
        assertNull(Mileage.mainFuel(emptyList()))
        // A tie goes to the earlier entry (petrol before CNG).
        assertEquals(FuelType.Petrol, Mileage.mainFuel(fills.take(2)))
    }

    @Test
    fun trendKeepsTheLastSix() {
        val many = (0..9).map { fill(it.toLong(), 10.0, 1000.0, 1000.0 + it * 150.0, FuelType.Petrol) }
        assertEquals(6, Mileage.trend(many, FuelType.Petrol).size)
        assertEquals(15.0, Mileage.trend(many, FuelType.Petrol).last(), 1e-9)
        assertEquals(FuelType.Cng, FuelType.fromKey("cng"))
        assertEquals(FuelType.Petrol, FuelType.fromKey(null))
    }
}
