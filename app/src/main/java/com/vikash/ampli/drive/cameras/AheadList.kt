package com.vikash.ampli.drive.cameras

import com.vikash.ampli.drive.nav.route.LatLon
import kotlin.math.cos
import kotlin.math.sqrt

/** What a row of "Coming up on this road" is about. */
enum class AheadKind { FixedCamera, RedLight, AverageZone, MobileCamera, Police, Accident, Jam }

/** One row of "Coming up on this road". [reportedAt] is set for road reports. */
data class AheadItem(
    val id: String,
    val kind: AheadKind,
    val distanceM: Double,
    val limitKmh: Int?,
    /** Average-speed zones: the zone's length. */
    val zoneLengthM: Double? = null,
    val name: String = "",
    val reportedAt: Long? = null,
    val userReported: Boolean = false,
)

/**
 * Builds the "Coming up on this road" list. Plain Kotlin, unit-tested.
 *
 * With a route on Car Hub's map, points within [CORRIDOR_M] of the route line count, ordered by
 * the distance along the route. Without one, points in a cone around the GPS heading count, by
 * straight-line distance; standing still, everything near the car does.
 */
object AheadList {
    const val CORRIDOR_M = 60.0
    const val ROUTE_MAX_M = 50_000.0
    const val HEADING_MAX_M = 15_000.0
    const val HEADING_CONE_DEG = 25.0
    const val PARKED_MAX_M = 5_000.0
    const val MAX_ROWS = 12

    fun kindOf(type: String): AheadKind = when (type) {
        SpeedCamera.TYPE_RED_LIGHT -> AheadKind.RedLight
        ReportKind.MobileCamera.type -> AheadKind.MobileCamera
        ReportKind.Police.type -> AheadKind.Police
        ReportKind.Accident.type -> AheadKind.Accident
        ReportKind.Jam.type -> AheadKind.Jam
        else -> AheadKind.FixedCamera
    }

    /**
     * Points ahead along [route] (geometry with its cumulative distances [cumM]) from [carAlongM]
     * metres into it. A point with a known direction must face the way the route goes there.
     */
    fun alongRoute(
        route: List<LatLon>,
        cumM: DoubleArray,
        carAlongM: Double,
        cameras: Collection<SpeedCamera>,
        zones: Collection<SpeedZone>,
        reportTimes: Map<String, Long> = emptyMap(),
        corridorM: Double = CORRIDOR_M,
        maxAheadM: Double = ROUTE_MAX_M,
    ): List<AheadItem> {
        if (route.size < 2) return emptyList()
        val out = ArrayList<AheadItem>()
        for (c in cameras) {
            val p = project(route, cumM, c.lat, c.lon, carAlongM, carAlongM + maxAheadM) ?: continue
            if (p.offsetM > corridorM) continue
            if (c.dir != null && CameraGeometry.angleDiff(c.dir, p.segmentBearing) > CameraGeometry.DIR_TOLERANCE_DEG) continue
            val ahead = p.alongM - carAlongM
            if (ahead <= 0.0) continue
            out += item(c, ahead, reportTimes)
        }
        for (z in zones) {
            val p = project(route, cumM, z.startLat, z.startLon, carAlongM, carAlongM + maxAheadM) ?: continue
            if (p.offsetM > corridorM) continue
            // The end must come later on the route, so the zone is driven the right way.
            val e = project(route, cumM, z.endLat, z.endLon, p.alongM, p.alongM + z.lengthM * 2 + 2_000.0) ?: continue
            if (e.offsetM > corridorM || e.alongM <= p.alongM) continue
            val ahead = p.alongM - carAlongM
            if (ahead <= 0.0) continue
            out += AheadItem(z.id, AheadKind.AverageZone, ahead, z.maxspeed, zoneLengthM = e.alongM - p.alongM, name = z.name)
        }
        return out.sortedBy { it.distanceM }.take(MAX_ROWS)
    }

    /** Points ahead of a car without a route: within the heading cone, or near it when parked. */
    fun byHeading(
        lat: Double,
        lon: Double,
        heading: Double?,
        cameras: Collection<SpeedCamera>,
        zones: Collection<SpeedZone>,
        reportTimes: Map<String, Long> = emptyMap(),
        coneDeg: Double = HEADING_CONE_DEG,
        maxM: Double = HEADING_MAX_M,
    ): List<AheadItem> {
        val out = ArrayList<AheadItem>()
        fun ahead(pLat: Double, pLon: Double, dir: Double?): Double? {
            val d = CameraGeometry.distanceM(lat, lon, pLat, pLon)
            if (heading == null) return d.takeIf { it <= PARKED_MAX_M }
            if (d > maxM) return null
            if (CameraGeometry.angleDiff(heading, CameraGeometry.bearingDeg(lat, lon, pLat, pLon)) > coneDeg) return null
            if (dir != null && CameraGeometry.angleDiff(heading, dir) > CameraGeometry.DIR_TOLERANCE_DEG) return null
            return d
        }
        for (c in cameras) {
            val d = ahead(c.lat, c.lon, c.dir) ?: continue
            out += item(c, d, reportTimes)
        }
        for (z in zones) {
            if (heading != null && CameraGeometry.angleDiff(heading, z.bearing) > 60.0) continue
            val d = ahead(z.startLat, z.startLon, null) ?: continue
            out += AheadItem(z.id, AheadKind.AverageZone, d, z.maxspeed, zoneLengthM = z.lengthM, name = z.name)
        }
        return out.sortedBy { it.distanceM }.take(MAX_ROWS)
    }

    private fun item(c: SpeedCamera, distanceM: Double, reportTimes: Map<String, Long>) = AheadItem(
        id = c.id,
        kind = kindOf(c.type),
        distanceM = distanceM,
        limitKmh = c.maxspeed,
        reportedAt = reportTimes[c.id],
        userReported = c.userReported && !c.type.startsWith(SpeedCamera.REPORT_PREFIX),
    )

    /** Where a point falls on the route: [alongM] from the start, [offsetM] off the line. */
    data class Projection(val alongM: Double, val offsetM: Double, val segmentBearing: Double)

    /**
     * The closest projection of the point onto the route between [fromAlongM] and [toAlongM]
     * (local flat-earth maths per segment, fine at road scale). Null when no segment is in range.
     */
    fun project(route: List<LatLon>, cumM: DoubleArray, lat: Double, lon: Double, fromAlongM: Double, toAlongM: Double): Projection? {
        var best: Projection? = null
        val mPerDegLat = 111_320.0
        // Skip straight to the first segment that reaches fromAlongM.
        val found = cumM.binarySearch(fromAlongM, 0, minOf(cumM.size, route.size))
        val start = (if (found >= 0) found else -found - 1).minus(1).coerceAtLeast(0)
        for (i in start until route.size - 1) {
            if (cumM[i + 1] < fromAlongM) continue
            if (cumM[i] > toAlongM) break
            val a = route[i]
            val b = route[i + 1]
            val mPerDegLon = mPerDegLat * cos(Math.toRadians((a.lat + b.lat) / 2))
            val bx = (b.lon - a.lon) * mPerDegLon
            val by = (b.lat - a.lat) * mPerDegLat
            val px = (lon - a.lon) * mPerDegLon
            val py = (lat - a.lat) * mPerDegLat
            val len2 = bx * bx + by * by
            val t = if (len2 <= 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
            val dx = px - t * bx
            val dy = py - t * by
            val off = sqrt(dx * dx + dy * dy)
            val along = cumM[i] + t * (cumM[i + 1] - cumM[i])
            if (along < fromAlongM || along > toAlongM) continue
            if (best == null || off < best.offsetM) {
                best = Projection(along, off, CameraGeometry.bearingDeg(a.lat, a.lon, b.lat, b.lon))
            }
        }
        return best
    }
}
