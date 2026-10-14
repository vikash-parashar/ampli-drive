package com.vikash.ampli.drive.nav.route

import kotlin.math.max
import kotlin.math.min

/** Where the car is on the route, after one GPS fix. */
data class RouteProgress(
    /** The fix snapped onto the route line (or the raw fix when off the route). */
    val snapped: LatLon,
    val distanceFromRouteM: Double,
    val alongM: Double,
    /** Index into [Route.steps] of the next manoeuvre. */
    val stepIndex: Int,
    val distanceToStepM: Double,
    val remainingM: Double,
    val remainingS: Double,
    /** Direction of the route where the car is, 0..360. */
    val routeBearing: Double,
    /** Posted limit on this stretch, from the routing server, when known. */
    val limitKmh: Int?,
    val arrived: Boolean,
)

/**
 * Follows the car along a [Route]: snaps each fix to the nearest point of the line, works out
 * the next manoeuvre, the distance to it, and what is left. Plain Kotlin, unit-tested.
 */
class RouteTracker(val route: Route) {
    private var segHint = 0
    private var maxAlong = 0.0

    fun update(p: LatLon): RouteProgress {
        val pts = route.points
        val segCount = pts.size - 1
        // Look near where the car was first: a route that passes the same road twice must not
        // jump ahead. Only a poor match triggers a whole-route search.
        var best = searchSegments(p, max(0, segHint - BACK_SEGMENTS), min(segCount, segHint + AHEAD_SEGMENTS))
        if (best.distance > ON_ROUTE_M) {
            val all = searchSegments(p, 0, segCount)
            if (all.distance < best.distance - 1.0) best = all
        }
        segHint = best.segment
        val a = pts[best.segment]
        val b = pts[best.segment + 1]
        val along = route.cumM[best.segment] + best.t * (route.cumM[best.segment + 1] - route.cumM[best.segment])
        val onRoute = best.distance <= ON_ROUTE_M
        // GPS jitter can pull the snapped point back a little; the step list never goes backwards
        // by more than that, so a passed turn isn't announced again.
        if (onRoute) maxAlong = max(maxAlong, along)
        val effective = if (onRoute) max(along, maxAlong - BACKTRACK_TOLERANCE_M) else along

        val steps = route.steps
        var idx = steps.indexOfFirst { it.alongM + PASSED_M > effective }
        if (idx < 0) idx = steps.size - 1
        val total = route.lengthM
        val remaining = (total - effective).coerceAtLeast(0.0)
        val toStep = (steps[idx].alongM - effective).coerceAtLeast(0.0)
        val snapped = if (onRoute) Geo.lerp(a, b, best.t) else p
        val toEnd = Geo.distanceM(p, route.destination)
        val arrived = remaining <= ARRIVE_M || toEnd <= ARRIVE_M
        return RouteProgress(
            snapped = snapped,
            distanceFromRouteM = best.distance,
            alongM = effective,
            stepIndex = idx,
            distanceToStepM = toStep,
            remainingM = remaining,
            remainingS = remainingSeconds(effective),
            routeBearing = Geo.bearing(a, b),
            limitKmh = route.maxspeedKmh?.getOrNull(best.segment),
            arrived = arrived,
        )
    }

    /** OSRM's time for what is left: whole spans ahead plus the unfinished part of this one. */
    fun remainingSeconds(along: Double): Double {
        var s = 0.0
        for (span in route.spans) {
            val end = span.startM + span.distanceM
            if (end <= along) continue
            s += if (span.startM >= along || span.distanceM <= 0.0) {
                span.durationS
            } else {
                span.durationS * (end - along) / span.distanceM
            }
        }
        return s
    }

    private data class Hit(val segment: Int, val t: Double, val distance: Double)

    private fun searchSegments(p: LatLon, from: Int, to: Int): Hit {
        val pts = route.points
        var best = Hit(from.coerceAtMost(pts.size - 2), 0.0, Double.MAX_VALUE)
        for (i in from until to) {
            val (t, d) = Geo.project(p, pts[i], pts[i + 1])
            if (d < best.distance) best = Hit(i, t, d)
        }
        return best
    }

    companion object {
        /** Further than this from the line counts as off the route. */
        const val ON_ROUTE_M = 50.0
        const val ARRIVE_M = 30.0

        /** A manoeuvre counts as done once the car is this far past it. */
        const val PASSED_M = 8.0
        const val BACKTRACK_TOLERANCE_M = 25.0
        private const val BACK_SEGMENTS = 3
        private const val AHEAD_SEGMENTS = 80
    }
}

/**
 * Off the route: more than [thresholdM] from the line on every fix for [holdMs]. Fixes with a
 * poor accuracy don't count either way. Plain Kotlin, unit-tested.
 */
class OffRouteDetector(
    private val thresholdM: Double = RouteTracker.ON_ROUTE_M,
    private val holdMs: Long = 5_000L,
    private val maxAccuracyM: Float = 60f,
) {
    private var offSinceMs = -1L

    /** True once, when the car has been off the route long enough; then it re-arms. */
    fun onFix(distanceFromRouteM: Double, accuracyM: Float?, nowMs: Long): Boolean {
        if (accuracyM != null && accuracyM > maxAccuracyM) return false
        if (distanceFromRouteM <= thresholdM) {
            offSinceMs = -1L
            return false
        }
        if (offSinceMs < 0) offSinceMs = nowMs
        if (nowMs - offSinceMs >= holdMs) {
            offSinceMs = -1L
            return true
        }
        return false
    }

    fun reset() {
        offSinceMs = -1L
    }
}

/** At most one reroute per [minGapMs], so a long detour doesn't hammer the server. */
class RerouteLimiter(private val minGapMs: Long = 20_000L) {
    private var lastMs = Long.MIN_VALUE

    fun tryAcquire(nowMs: Long): Boolean {
        if (lastMs != Long.MIN_VALUE && nowMs - lastMs < minGapMs) return false
        lastMs = nowMs
        return true
    }

    fun reset() {
        lastMs = Long.MIN_VALUE
    }
}
