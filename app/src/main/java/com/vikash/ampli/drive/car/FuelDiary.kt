package com.vikash.ampli.drive.car

import kotlin.math.roundToInt

/**
 * "Petrol bharwaya 1000 ka", "pachaas litre": turning a spoken fill into a [FuelFill] for the
 * mileage diary. Plain Kotlin, unit-tested.
 *
 * Assumptions (also shown in the app):
 * - Only rupees said: the litres come from the price of the last fill of that fuel, else from
 *   [defaultPrice] (Haryana pump prices, late 2026, rounded; the driver can fix it in Expenses).
 * - Only litres said: the rupees come from the same price.
 * - A fill by voice counts as a full tank, so mileage works from GPS kilometres between fills
 *   (fill-to-fill). Partial top-ups make one segment read high and the next low.
 */
object FuelDiary {

    /** Rough pump prices used only until the driver has logged a fill with both numbers. */
    fun defaultPrice(fuel: FuelType): Double = when (fuel) {
        FuelType.Petrol -> 95.0
        FuelType.Diesel -> 88.0
        FuelType.Cng -> 90.0
    }

    /** ₹ per litre (per kg for CNG) of the newest fill of [fuel] with sensible numbers, or null. */
    fun lastPrice(fills: List<FuelFill>, fuel: FuelType): Double? =
        fills.filter { it.fuel == fuel && it.litres > 0.5 && it.rupees > 0.0 }
            .maxByOrNull { it.time }
            ?.let { it.rupees / it.litres }
            ?.takeIf { it in 30.0..300.0 }

    /** The fuel a spoken fill is for when the driver did not say: the car's usual one. */
    fun fuelFor(said: FuelType?, fills: List<FuelFill>): FuelType = said ?: Mileage.mainFuel(fills) ?: FuelType.Petrol

    /** What a spoken fill becomes; null when neither amount was said or one is out of range. */
    fun fill(
        rupees: Double?,
        litres: Double?,
        fuel: FuelType,
        fills: List<FuelFill>,
        odometerKm: Double,
        now: Long,
    ): FuelFill? {
        val price = lastPrice(fills, fuel) ?: defaultPrice(fuel)
        val r = rupees ?: litres?.let { it * price } ?: return null
        val l = litres ?: (r / price)
        if (r !in 50.0..50_000.0 || l !in 0.5..200.0) return null
        return FuelFill(
            time = now,
            litres = (l * 100).roundToInt() / 100.0,
            rupees = r.roundToInt().toDouble(),
            odometerKm = odometerKm,
            fullTank = true,
            fuel = fuel,
        )
    }

    /** Fill-to-fill km/l of the newest two fills of the fill's fuel: GPS km since the previous fill over this fill's litres. */
    fun lastKmPerLitre(fills: List<FuelFill>, fuel: FuelType): Double? {
        val same = fills.filter { it.fuel == fuel }.sortedByDescending { it.time }
        if (same.size < 2) return null
        val km = same[0].odometerKm - same[1].odometerKm
        if (km <= 1.0 || same[0].litres <= 0.0) return null
        return km / same[0].litres
    }

    /** This calendar month's fuel spend in rupees ([monthStart] = epoch ms of the 1st, 00:00). */
    fun monthSpend(fills: List<FuelFill>, monthStart: Long): Double = fills.filter { it.time >= monthStart }.sumOf { it.rupees }
}
