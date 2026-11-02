package com.vikash.ampli.drive.fuel

import com.vikash.ampli.drive.cameras.CameraGeometry

/**
 * "CNG pe line lambi hai": what the driver said about the queue at a pump, at [time]. Kept on
 * this unit only (no Ampli server; see [ReportSync]) and forgotten after [PumpQueues.TTL_MS].
 */
data class QueueReport(val stationId: Int, val long: Boolean, val time: Long, val fuel: FuelFilter = FuelFilter.All) {
    fun minutesAgo(now: Long): Int = ((now - time).coerceAtLeast(0L) / 60_000L).toInt()
}

/** Queue reports: expiry and lookup. Plain Kotlin, unit-tested. */
object PumpQueues {
    /** A queue changes fast: 45 minutes, then it is dropped. */
    const val TTL_MS = 45L * 60_000L

    /** "Line lambi hai" applies to the nearest pump within this of the car. */
    const val AT_PUMP_M = 400.0

    const val MAX_KEPT = 40

    fun isLive(r: QueueReport, now: Long): Boolean = r.time <= now + 60_000L && now - r.time < TTL_MS

    /** [reports] with [added] in: expired ones dropped, the older one for that pump replaced. */
    fun add(reports: List<QueueReport>, added: QueueReport, now: Long): List<QueueReport> =
        (listOf(added) + reports.filter { isLive(it, now) && it.stationId != added.stationId }).take(MAX_KEPT)

    /** The newest live report for a pump, or null. */
    fun statusFor(reports: List<QueueReport>, stationId: Int, now: Long): QueueReport? =
        reports.filter { it.stationId == stationId && isLive(it, now) }.maxByOrNull { it.time }

    /** The pump the driver is at: the nearest of [hits] within [AT_PUMP_M]. */
    fun pumpAt(hits: List<FuelHit>): FuelHit? = hits.filter { it.distanceM <= AT_PUMP_M }.minByOrNull { it.distanceM }
}

/**
 * Which side of the road a pump is on, for a car driving on [heading]. India keeps left, so on a
 * divided highway the pumps on the left are the ones the car can pull into; one on the right
 * means a U-turn or a cut, and one behind means turning back (the Nawgati complaint).
 */
enum class PumpSide { AheadSameSide, AheadOtherSide, Behind, Unknown }

/** Ranking pumps for a moving car. Plain Kotlin, unit-tested. */
object DrivingSide {
    /** Below this speed (or with no GPS bearing) the plain nearest-first order stays. */
    const val MIN_KMH = 25.0

    /** Ahead means within this many degrees of the heading. */
    const val AHEAD_DEG = 75.0

    /** Pumps this close are "right here" whichever side: no penalty. */
    const val HERE_M = 150.0

    /** Effective-distance factors: a pump across the divider or behind costs a detour. */
    const val OTHER_SIDE_FACTOR = 1.8
    const val BEHIND_FACTOR = 3.0

    fun side(lat: Double, lon: Double, heading: Double?, speedKmh: Double, pLat: Double, pLon: Double): PumpSide {
        if (heading == null || speedKmh < MIN_KMH) return PumpSide.Unknown
        val bearing = CameraGeometry.bearingDeg(lat, lon, pLat, pLon)
        // Signed angle from the heading: negative = left of the road, positive = right.
        val rel = ((bearing - heading + 540.0) % 360.0) - 180.0
        return when {
            kotlin.math.abs(rel) > AHEAD_DEG -> PumpSide.Behind
            rel <= 0.0 -> PumpSide.AheadSameSide
            else -> PumpSide.AheadOtherSide
        }
    }

    /**
     * [hits] in the order a driver on [heading] would want them: same side ahead first, then the
     * other carriageway, then behind, each by distance. Standing still, nearest first.
     */
    fun rank(hits: List<FuelHit>, lat: Double, lon: Double, heading: Double?, speedKmh: Double): List<Pair<FuelHit, PumpSide>> {
        val sided = hits.map { it to side(lat, lon, heading, speedKmh, it.station.lat, it.station.lon) }
        return sided.sortedBy { (h, s) -> effective(h.distanceM, s) }
    }

    fun effective(distanceM: Double, side: PumpSide): Double = when {
        distanceM <= HERE_M -> distanceM
        side == PumpSide.AheadOtherSide -> distanceM * OTHER_SIDE_FACTOR
        side == PumpSide.Behind -> distanceM * BEHIND_FACTOR
        else -> distanceM
    }
}
