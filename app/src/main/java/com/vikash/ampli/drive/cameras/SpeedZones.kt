package com.vikash.ampli.drive.cameras

/**
 * An average-speed section (OSM relation type=enforcement + enforcement=average_speed): the
 * cameras at [startLat]/[startLon] and [endLat]/[endLon] time the car between them.
 * From assets/speed_zones.json, © OpenStreetMap contributors, ODbL 1.0.
 */
data class SpeedZone(
    val id: String,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val maxspeed: Int?,
    val name: String = "",
) {
    /** Straight line from start to end; the road is a little longer. */
    val lengthM: Double get() = CameraGeometry.distanceM(startLat, startLon, endLat, endLon)

    /** Direction of travel through the zone. */
    val bearing: Double get() = CameraGeometry.bearingDeg(startLat, startLon, endLat, endLon)
}

/**
 * Follows the car through average-speed zones. Plain Kotlin, unit-tested.
 *
 * Approaching: the start is ahead (within [alertM]) and the car drives the zone's way. Inside:
 * the car passed the start; it shows the distance to the end and the average speed so far (the
 * straight-line distance from the start over the time since). The zone ends at the end camera,
 * or when the car leaves the corridor between the two.
 */
class ZoneTracker(var alertM: Double = 500.0) {

    enum class Phase { Approaching, Inside }

    data class State(
        val zone: SpeedZone,
        val phase: Phase,
        /** To the start while approaching, to the end inside. */
        val distanceM: Double,
        /** Average so far, inside only, once there is enough of it to mean something. */
        val averageKmh: Int?,
    ) {
        val overLimit: Boolean get() = averageKmh != null && zone.maxspeed != null && averageKmh > zone.maxspeed
    }

    sealed interface Event {
        data class Started(val zone: SpeedZone) : Event
        data class Ended(val zone: SpeedZone) : Event
    }

    data class Result(val state: State?, val events: List<Event>)

    private var inside: SpeedZone? = null
    private var enteredAt = 0L

    fun update(lat: Double, lon: Double, heading: Double?, speedKmh: Double, nowMs: Long, zones: Collection<SpeedZone>): Result {
        val events = ArrayList<Event>(1)
        inside?.let { z ->
            val toEnd = CameraGeometry.distanceM(lat, lon, z.endLat, z.endLon)
            val fromStart = CameraGeometry.distanceM(lat, lon, z.startLat, z.startLon)
            val corridor = fromStart + toEnd <= z.lengthM * CORRIDOR_FACTOR + CORRIDOR_SLACK_M
            if (toEnd <= REACHED_M || !corridor) {
                inside = null
                events += Event.Ended(z)
            } else {
                val elapsedS = (nowMs - enteredAt) / 1000.0
                val avg = if (elapsedS >= MIN_AVERAGE_S && fromStart >= MIN_AVERAGE_M) (fromStart / elapsedS * 3.6).toInt() else null
                return Result(State(z, Phase.Inside, toEnd, avg), events)
            }
        }
        if (heading == null || speedKmh < CameraGeometry.SLOW_KMH) return Result(null, events)
        var best: State? = null
        for (z in zones) {
            // Only zones driven the right way.
            if (CameraGeometry.angleDiff(heading, z.bearing) > ZONE_HEADING_DEG) continue
            val toStart = CameraGeometry.distanceM(lat, lon, z.startLat, z.startLon)
            val toEnd = CameraGeometry.distanceM(lat, lon, z.endLat, z.endLon)
            val bearingToStart = CameraGeometry.bearingDeg(lat, lon, z.startLat, z.startLon)
            val startAhead = toStart > REACHED_M && CameraGeometry.angleDiff(heading, bearingToStart) <= CameraGeometry.CONE_DEG
            if (startAhead && toStart <= alertM) {
                if (best == null || toStart < best.distanceM) best = State(z, Phase.Approaching, toStart, null)
                continue
            }
            // At the start camera, or just past it and heading for the end.
            val passed = toStart <= REACHED_M ||
                (toStart <= JUST_PASSED_M && toEnd < z.lengthM && !startAhead)
            if (passed && toEnd > REACHED_M) {
                inside = z
                enteredAt = nowMs
                events += Event.Started(z)
                return Result(State(z, Phase.Inside, toEnd, null), events)
            }
        }
        return Result(best, events)
    }

    fun reset() {
        inside = null
    }

    companion object {
        const val REACHED_M = 60.0
        const val JUST_PASSED_M = 250.0
        const val ZONE_HEADING_DEG = 60.0
        const val CORRIDOR_FACTOR = 1.35
        const val CORRIDOR_SLACK_M = 400.0
        const val MIN_AVERAGE_S = 20.0
        const val MIN_AVERAGE_M = 200.0
    }
}
