package com.vikash.ampli.drive.cameras

/** The report sheet's four buttons ("Hey Ampli, police aage hai"). */
enum class ReportKind(val type: String) {
    MobileCamera(SpeedCamera.REPORT_PREFIX + "mobile"),
    Police(SpeedCamera.REPORT_PREFIX + "police"),
    Accident(SpeedCamera.REPORT_PREFIX + "accident"),
    Jam(SpeedCamera.REPORT_PREFIX + "jam");

    companion object {
        fun fromType(type: String): ReportKind? = entries.firstOrNull { it.type == type }
    }
}

/**
 * Something the driver saw on the road, saved at the car's position. [dir] is the GPS heading
 * when the car was moving, so the alert only fires on this side of the road. Kept on this unit
 * only (there is no Ampli server yet) and forgotten after [RoadReports.TTL_MS].
 */
data class RoadReport(
    val id: String,
    val kind: ReportKind,
    val lat: Double,
    val lon: Double,
    val dir: Double?,
    val time: Long,
) {
    /** As an enforcement point, so the camera alert engine and the ahead list treat it alike. */
    fun asCamera(): SpeedCamera = SpeedCamera(id, lat, lon, maxspeed = null, dir = dir, type = kind.type, userReported = true)

    fun minutesAgo(now: Long): Int = ((now - time).coerceAtLeast(0L) / 60_000L).toInt()
}

/** Expiry and housekeeping for road reports. Plain Kotlin, unit-tested. */
object RoadReports {
    /** A report is shown for two hours, then dropped. */
    const val TTL_MS = 2L * 60 * 60 * 1000

    /** At most this many are kept; the oldest go first. */
    const val MAX_KEPT = 50

    /** Reports of the same kind closer than this replace each other (a second tap, a repeat by voice). */
    const val MERGE_M = 150.0

    fun isLive(report: RoadReport, now: Long, ttlMs: Long = TTL_MS): Boolean =
        report.time <= now + 60_000L && now - report.time < ttlMs

    /** Live reports, newest first. */
    fun live(reports: List<RoadReport>, now: Long, ttlMs: Long = TTL_MS): List<RoadReport> =
        reports.filter { isLive(it, now, ttlMs) }.sortedByDescending { it.time }

    /**
     * [reports] with [added] in: expired ones dropped, an older one of the same kind within
     * [MERGE_M] replaced, and no more than [MAX_KEPT].
     */
    fun add(reports: List<RoadReport>, added: RoadReport, now: Long): List<RoadReport> {
        val kept = live(reports, now).filterNot {
            it.kind == added.kind && CameraGeometry.distanceM(it.lat, it.lon, added.lat, added.lon) < MERGE_M
        }
        return (listOf(added) + kept).take(MAX_KEPT)
    }

    /** When the next live report expires, for a timer; null when there is none. */
    fun nextExpiry(reports: List<RoadReport>, now: Long): Long? =
        live(reports, now).minOfOrNull { it.time + TTL_MS }
}
