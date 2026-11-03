package com.vikash.ampli.drive.voice

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Which place-search results are worth offering (pure, unit-tested). A spoken place is a drive
 * away: with a GPS fix, anything farther than [MAX_KM] is dropped ("Суна 3795 km", "Cima di Gana
 * Bianca 6060 km" were the geocoder's answers to a misheard "gana suna de"); without a fix only
 * places inside India are kept.
 */
object PlaceFilter {
    const val MAX_KM = 500.0

    // India's bounding box (min lon, min lat, max lon, max lat), generous at the borders.
    const val INDIA_MIN_LON = 68.0
    const val INDIA_MIN_LAT = 6.0
    const val INDIA_MAX_LON = 97.5
    const val INDIA_MAX_LAT = 37.5

    /** True when a result at [lat], [lon] ([km] from the car, null without a fix) may be offered. */
    fun keep(lat: Double, lon: Double, km: Double?, countryCode: String? = null): Boolean {
        if (km != null) return km <= MAX_KM
        if (countryCode != null) return countryCode.equals("IN", ignoreCase = true)
        return inIndia(lat, lon)
    }

    fun inIndia(lat: Double, lon: Double): Boolean =
        lat in INDIA_MIN_LAT..INDIA_MAX_LAT && lon in INDIA_MIN_LON..INDIA_MAX_LON

    /**
     * The geocoder's bbox parameter ("minLon,minLat,maxLon,maxLat"): [MAX_KM] around the car, or
     * India without a fix.
     */
    fun bbox(lat: Double?, lon: Double?): String {
        if (lat == null || lon == null) return "$INDIA_MIN_LON,$INDIA_MIN_LAT,$INDIA_MAX_LON,$INDIA_MAX_LAT"
        val dLat = MAX_KM / 111.0
        val dLon = MAX_KM / (111.0 * max(cos(Math.toRadians(lat)), 0.1))
        fun f(v: Double) = String.format(java.util.Locale.ROOT, "%.4f", v)
        return listOf(
            f(max(lon - dLon, -180.0)), f(max(lat - dLat, -90.0)),
            f(min(lon + dLon, 180.0)), f(min(lat + dLat, 90.0)),
        ).joinToString(",")
    }

    /** Great-circle distance in km. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dp / 2).let { it * it } + cos(p1) * cos(p2) * kotlin.math.sin(dl / 2).let { it * it }
        return 2 * 6371.0 * asin(min(1.0, sqrt(a)))
    }
}
