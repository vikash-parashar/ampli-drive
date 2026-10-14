package com.vikash.ampli.drive.nav.route

import com.vikash.ampli.drive.nav.Maneuver
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/**
 * One manoeuvre the driver is told about (OSRM "continue straight onto a new name" steps are
 * folded away, so this list is what the banner and the voice walk through).
 *
 * @param alongM where on the route the manoeuvre happens, in metres from the start.
 * @param name the road the driver is on after the manoeuvre ("MG Road"), or its [ref] ("NH 48").
 */
data class RouteStep(
    val maneuver: Maneuver,
    val exitNumber: Int?,
    val location: LatLon,
    val name: String?,
    val ref: String?,
    val alongM: Double,
    /** OSRM's own words, for Diagnostics: "turn/left", "roundabout/right". */
    val osrmType: String,
    val osrmModifier: String?,
) {
    /** Street label for the banner: name, else ref ("NH 48"). */
    val label: String? get() = name ?: ref
}

/** OSRM step span, for remaining-time sums: [startM, startM + distanceM) took [durationS]. */
data class TimedSpan(val startM: Double, val distanceM: Double, val durationS: Double)

/**
 * A driving route: [points] is the full geometry, [cumM] the distance along it at each point,
 * [steps] the announced manoeuvres (the last one is [Maneuver.Arrive]), [maxspeedKmh] the posted
 * limit per geometry segment when the server sends it (null entries = unknown).
 */
class Route(
    val points: List<LatLon>,
    val steps: List<RouteStep>,
    val spans: List<TimedSpan>,
    val distanceM: Double,
    val durationS: Double,
    val maxspeedKmh: List<Int?>?,
) {
    val cumM: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + Geo.distanceM(points[i - 1], points[i])
    }

    /** Geometry length; OSRM's [distanceM] can differ by a few metres. */
    val lengthM: Double get() = if (cumM.isEmpty()) 0.0 else cumM[cumM.size - 1]

    val destination: LatLon get() = points.last()
}

/** Small-distance geodesy, good to well under a metre over a few kilometres. */
object Geo {
    private const val R = 6_371_000.0

    private fun rad(d: Double) = d * PI / 180.0

    /** Haversine distance in metres. */
    fun distanceM(a: LatLon, b: LatLon): Double {
        val dLat = rad(b.lat - a.lat)
        val dLon = rad(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * R * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Initial bearing from [a] to [b], 0..360 (0 = north). */
    fun bearing(a: LatLon, b: LatLon): Double {
        val y = sin(rad(b.lon - a.lon)) * cos(rad(b.lat))
        val x = cos(rad(a.lat)) * sin(rad(b.lat)) - sin(rad(a.lat)) * cos(rad(b.lat)) * cos(rad(b.lon - a.lon))
        val deg = atan2(y, x) * 180.0 / PI
        return (deg + 360.0) % 360.0
    }

    /**
     * Closest point to [p] on segment [a]-[b]: returns (t in 0..1, distance in metres), using a
     * flat projection around [a] (segments are short).
     */
    fun project(p: LatLon, a: LatLon, b: LatLon): Pair<Double, Double> {
        val k = cos(rad(a.lat))
        val bx = rad(b.lon - a.lon) * k * R
        val by = rad(b.lat - a.lat) * R
        val px = rad(p.lon - a.lon) * k * R
        val py = rad(p.lat - a.lat) * R
        val len2 = bx * bx + by * by
        val t = if (len2 <= 1e-9) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
        val dx = px - t * bx
        val dy = py - t * by
        return t to sqrt(dx * dx + dy * dy)
    }

    fun lerp(a: LatLon, b: LatLon, t: Double) = LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
}
