package com.vikash.ampli.drive.cameras

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One enforcement point. [dir] is the bearing the camera enforces (the direction of travel it
 * watches), or null when unknown. [type] is "speed_camera", "average_speed" or "user".
 *
 * OSM-derived points come from OpenStreetMap and are © OpenStreetMap contributors, ODbL 1.0.
 */
data class SpeedCamera(
    val id: String,
    val lat: Double,
    val lon: Double,
    val maxspeed: Int?,
    val dir: Double?,
    val type: String,
    val userReported: Boolean = false,
)

/** A camera with its straight-line distance from the car. */
data class CameraAhead(val camera: SpeedCamera, val distanceMeters: Double)

/** Plain-Kotlin geometry for camera alerts, so it runs in JVM unit tests. */
object CameraGeometry {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Search radius around the car for live alerts. */
    const val LOOKAHEAD_M = 1_500.0

    /** Below this speed (or with no GPS bearing) only cameras right next to the car count. */
    const val SLOW_KMH = 10.0
    const val NEAR_M = 150.0

    /** The camera must lie within this many degrees of the car's heading. */
    const val CONE_DEG = 30.0

    /** A camera with a known direction must face the car's direction of travel within this. */
    const val DIR_TOLERANCE_DEG = 45.0

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Initial bearing from point 1 to point 2, 0..360 degrees clockwise from north. */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Smallest angle between two bearings, 0..180. */
    fun angleDiff(a: Double, b: Double): Double {
        val d = abs(((a - b) % 360.0 + 360.0) % 360.0)
        return if (d > 180.0) 360.0 - d else d
    }

    /**
     * Whether a camera [distanceM] away at [bearingToCam] is on the road ahead of a car driving at
     * [speedKmh] on [heading]. Slow or bearing-less cars only count cameras within [NEAR_M].
     */
    fun isAhead(
        distanceM: Double,
        bearingToCam: Double,
        heading: Double?,
        speedKmh: Double,
        camDir: Double?,
        maxDistanceM: Double = LOOKAHEAD_M,
        coneDeg: Double = CONE_DEG,
    ): Boolean {
        if (heading == null || speedKmh < SLOW_KMH) return distanceM <= NEAR_M
        if (distanceM > maxDistanceM) return false
        if (angleDiff(heading, bearingToCam) > coneDeg) return false
        if (camDir != null && angleDiff(heading, camDir) > DIR_TOLERANCE_DEG) return false
        return true
    }

    /**
     * Nearest camera ahead among [candidates]. With no usable heading it returns the nearest
     * camera within [maxDistanceM] in any direction (used by the voice "next camera" answer).
     */
    fun nearestAhead(
        candidates: Collection<SpeedCamera>,
        lat: Double,
        lon: Double,
        heading: Double?,
        speedKmh: Double,
        maxDistanceM: Double,
        coneDeg: Double = CONE_DEG,
        anyDirectionWhenUnknown: Boolean = false,
    ): CameraAhead? {
        var best: CameraAhead? = null
        for (cam in candidates) {
            val d = distanceM(lat, lon, cam.lat, cam.lon)
            if (d > maxDistanceM) continue
            val ok = if (anyDirectionWhenUnknown && (heading == null || speedKmh < SLOW_KMH)) {
                true
            } else {
                isAhead(d, bearingDeg(lat, lon, cam.lat, cam.lon), heading, speedKmh, cam.dir, maxDistanceM, coneDeg)
            }
            if (ok && (best == null || d < best.distanceMeters)) best = CameraAhead(cam, d)
        }
        return best
    }
}

/**
 * Grid index over lat/lon buckets of [CELL_DEG] (about 5.5 km), so a GPS fix looks at a handful
 * of buckets instead of every camera in India. Immutable; build a new one when the data changes.
 */
class CameraIndex(cameras: List<SpeedCamera>) {
    private val cells = HashMap<Long, MutableList<SpeedCamera>>()
    val size: Int = cameras.size

    init {
        for (c in cameras) cells.getOrPut(key(cell(c.lat), cell(c.lon))) { ArrayList(4) }.add(c)
    }

    /** Cameras within roughly [radiusM] of the point (bucket-accurate; callers check distance). */
    fun near(lat: Double, lon: Double, radiusM: Double): List<SpeedCamera> {
        if (cells.isEmpty()) return emptyList()
        val metresPerCellLat = CELL_DEG * 111_320.0
        val metresPerCellLon = metresPerCellLat * cos(Math.toRadians(lat)).coerceAtLeast(0.05)
        val dy = ceil(radiusM / metresPerCellLat).toInt()
        val dx = ceil(radiusM / metresPerCellLon).toInt()
        val cy = cell(lat)
        val cx = cell(lon)
        val out = ArrayList<SpeedCamera>()
        for (y in cy - dy..cy + dy) {
            for (x in cx - dx..cx + dx) {
                cells[key(y, x)]?.let { out.addAll(it) }
            }
        }
        return out
    }

    private fun cell(deg: Double): Int = floor(deg / CELL_DEG).toInt()

    private fun key(y: Int, x: Int): Long = (y.toLong() shl 32) or (x.toLong() and 0xffffffffL)

    companion object {
        const val CELL_DEG = 0.05
    }
}
