package com.vikash.ampli.drive.tripcost

import com.vikash.ampli.drive.fastag.AnnualPass
import com.vikash.ampli.drive.nav.route.LatLon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pro "trip cost before you drive" (see ampli-drive-notes/trip-cost-plan.md). Off until a toll
 * source with commercial reuse rights is in place: nothing reads [TripCostFlags.ENABLED] as true
 * in a release, and no screen shows the card yet.
 */
object TripCostFlags {
    /** Off by default. Flip only after the toll data licence is settled (plan, section 2). */
    const val ENABLED = false
}

/** One toll plaza with the single-journey fee for a private car / jeep / van. */
data class TollPlaza(
    val id: String,
    val name: String,
    val at: LatLon,
    val carFeeRupees: Int,
    /** NHAI / MoRTH national-highway plaza where the FASTag Annual Pass is accepted. */
    val passEligible: Boolean,
)

/** What the card and the spoken summary show. Rupees are rounded to whole rupees. */
data class TripCost(
    val distanceKm: Double,
    val tolls: Int,
    val tollRupees: Int,
    /** Null when the driver has no mileage or fuel price yet (Expenses screen). */
    val fuelRupees: Int?,
    val fuelLitres: Double?,
    /** Crossings the Annual Pass would cover on this trip. */
    val passTrips: Int,
    /** Toll at pass plazas minus what those crossings cost on the pass; 0 when it doesn't pay. */
    val passSavingRupees: Int,
    /** The driver already has a pass running (the card then says "saves" instead of "would save"). */
    val hasPass: Boolean,
    /** Sample numbers for a preview, never real data. The card labels them as an example. */
    val example: Boolean = false,
) {
    val totalRupees: Int get() = tollRupees + (fuelRupees ?: 0)
}

/** Pure arithmetic, unit-tested. */
object TripCostMath {

    /** What one Annual Pass crossing costs: price / trips (₹3,075 / 200 ≈ ₹15.4). */
    val PASS_RUPEES_PER_TRIP: Double = AnnualPass.PRICE_RUPEES.toDouble() / AnnualPass.TRIPS

    /** A plaza counts when it lies within this many metres of the route line. */
    const val ON_ROUTE_M = 60.0

    fun estimate(
        distanceKm: Double,
        plazas: List<TollPlaza>,
        kmPerLitre: Double?,
        fuelRupeesPerLitre: Double?,
        hasPass: Boolean,
    ): TripCost {
        val tollRupees = plazas.sumOf { it.carFeeRupees }
        val eligible = plazas.filter { it.passEligible }
        val saving = (eligible.sumOf { it.carFeeRupees } - eligible.size * PASS_RUPEES_PER_TRIP).roundToInt().coerceAtLeast(0)
        val litres = if (kmPerLitre != null && kmPerLitre > 0) distanceKm / kmPerLitre else null
        val fuel = if (litres != null && fuelRupeesPerLitre != null && fuelRupeesPerLitre > 0) (litres * fuelRupeesPerLitre).roundToInt() else null
        return TripCost(
            distanceKm = distanceKm,
            tolls = plazas.size,
            tollRupees = tollRupees,
            fuelRupees = fuel,
            fuelLitres = litres,
            passTrips = eligible.size,
            passSavingRupees = saving,
            hasPass = hasPass,
        )
    }

    /** The plazas the route passes, in route order (each once). */
    fun plazasOnRoute(route: List<LatLon>, plazas: List<TollPlaza>, maxM: Double = ON_ROUTE_M): List<TollPlaza> {
        if (route.size < 2) return emptyList()
        val hits = ArrayList<Pair<Int, TollPlaza>>()
        for (p in plazas) {
            var best = -1
            for (i in 0 until route.size - 1) {
                if (distanceToSegmentM(p.at, route[i], route[i + 1]) <= maxM) {
                    best = i
                    break
                }
            }
            if (best >= 0) hits += best to p
        }
        return hits.sortedBy { it.first }.map { it.second }
    }

    /** Equirectangular projection around [p]: plenty for tens of metres. */
    fun distanceToSegmentM(p: LatLon, a: LatLon, b: LatLon): Double {
        val k = cos(p.lat * PI / 180.0)
        fun x(q: LatLon) = (q.lon - p.lon) * k * M_PER_DEG
        fun y(q: LatLon) = (q.lat - p.lat) * M_PER_DEG
        val ax = x(a); val ay = y(a); val bx = x(b); val by = y(b)
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx; val cy = ay + t * dy
        return sqrt(cx * cx + cy * cy)
    }

    /**
     * The one line said when the car starts moving (never shown while moving):
     * "4 टोल, 385 रुपये। पेट्रोल करीब 620 रुपये। Annual Pass से 324 रुपये बचेंगे।"
     */
    fun spoken(cost: TripCost, hindi: Boolean): String {
        val parts = ArrayList<String>()
        if (hindi) {
            parts += if (cost.tolls == 0) "रास्ते में कोई टोल नहीं" else "${cost.tolls} टोल, ${cost.tollRupees} रुपये"
            cost.fuelRupees?.let { parts += "पेट्रोल करीब $it रुपये" }
            if (cost.passSavingRupees > 0) {
                parts += if (cost.hasPass) "Annual Pass से ${cost.passSavingRupees} रुपये बचेंगे" else "Annual Pass होता तो ${cost.passSavingRupees} रुपये बचते"
            }
            return parts.joinToString("। ") + "।"
        }
        parts += if (cost.tolls == 0) "No tolls on this route" else "${cost.tolls} tolls, ${cost.tollRupees} rupees"
        cost.fuelRupees?.let { parts += "fuel about $it rupees" }
        if (cost.passSavingRupees > 0) {
            parts += if (cost.hasPass) "the Annual Pass saves ${cost.passSavingRupees} rupees" else "an Annual Pass would save ${cost.passSavingRupees} rupees"
        }
        return parts.joinToString(". ") + "."
    }

    /** Sample trip for previews only (Gurugram → Jaipur-ish). Marked [TripCost.example]. */
    fun example(): TripCost = TripCost(
        distanceKm = 236.0,
        tolls = 4,
        tollRupees = 385,
        fuelRupees = 1620,
        fuelLitres = 15.7,
        passTrips = 4,
        passSavingRupees = 324,
        hasPass = true,
        example = true,
    )

    private const val M_PER_DEG = 111_320.0
}
