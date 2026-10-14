package com.vikash.ampli.drive.car

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

/** Vehicle papers that expire. */
enum class PaperKind { Puc, Insurance, Rc, Licence, FastagKyc }

data class Paper(val kind: PaperKind, val expiry: LocalDate?)

/**
 * A maintenance job repeated every [everyKm] and/or every [everyMonths], whichever comes first.
 * [lastKm] / [lastDate] are when it was last done; unknown means "not yet scheduled".
 */
data class ServiceItem(
    val id: String,
    val name: String,
    val everyKm: Int?,
    val everyMonths: Int?,
    val lastKm: Double?,
    val lastDate: LocalDate?,
)

enum class DueLevel { Unknown, Ok, Soon, Overdue }

/** [daysLeft] / [kmLeft] are negative when overdue, null when that limit doesn't apply. */
data class DueStatus(val level: DueLevel, val daysLeft: Long? = null, val kmLeft: Double? = null)

/** Something to warn about on the home chip, in a notification or by voice. */
data class DueItem(val title: String, val status: DueStatus, val paper: PaperKind? = null)

/** Pure due-date and due-km arithmetic for papers and service. */
object Reminders {
    const val SOON_DAYS = 15L
    const val SOON_KM = 500.0

    fun paper(p: Paper, today: LocalDate): DueStatus {
        val expiry = p.expiry ?: return DueStatus(DueLevel.Unknown)
        val days = ChronoUnit.DAYS.between(today, expiry)
        return DueStatus(levelForDays(days), daysLeft = days)
    }

    fun service(s: ServiceItem, today: LocalDate, odometerKm: Double): DueStatus {
        val kmLeft = if (s.everyKm != null && s.everyKm > 0 && s.lastKm != null) s.lastKm + s.everyKm - odometerKm else null
        val daysLeft = if (s.everyMonths != null && s.everyMonths > 0 && s.lastDate != null) {
            ChronoUnit.DAYS.between(today, s.lastDate.plusMonths(s.everyMonths.toLong()))
        } else {
            null
        }
        if (kmLeft == null && daysLeft == null) return DueStatus(DueLevel.Unknown)
        val byKm = kmLeft?.let { levelForKm(it) } ?: DueLevel.Unknown
        val byDays = daysLeft?.let { levelForDays(it) } ?: DueLevel.Unknown
        return DueStatus(maxOf(byKm, byDays), daysLeft = daysLeft, kmLeft = kmLeft)
    }

    /** The service coming up soonest by distance (then by date), for "how many km to next service". */
    fun nextService(items: List<ServiceItem>, today: LocalDate, odometerKm: Double): Pair<ServiceItem, DueStatus>? =
        items.map { it to service(it, today, odometerKm) }
            .filter { it.second.level != DueLevel.Unknown }
            .minWithOrNull(
                compareBy<Pair<ServiceItem, DueStatus>>({ it.second.kmLeft ?: Double.MAX_VALUE }, { it.second.daysLeft ?: Long.MAX_VALUE }),
            )

    /** Everything due within 15 days / 500 km or overdue, most urgent first. */
    fun dueItems(
        papers: List<Paper>,
        services: List<ServiceItem>,
        today: LocalDate,
        odometerKm: Double,
        paperName: (PaperKind) -> String,
    ): List<DueItem> {
        val out = ArrayList<DueItem>()
        for (p in papers) {
            val s = paper(p, today)
            if (s.level >= DueLevel.Soon) out += DueItem(paperName(p.kind), s, p.kind)
        }
        for (item in services) {
            val s = service(item, today, odometerKm)
            if (s.level >= DueLevel.Soon) out += DueItem(item.name, s)
        }
        return out.sortedWith(compareByDescending<DueItem> { it.status.level }.thenBy { urgency(it.status) })
    }

    private fun urgency(s: DueStatus): Double {
        // Rough common scale: 1 day ≈ 40 km of driving.
        val d = s.daysLeft?.toDouble() ?: Double.MAX_VALUE
        val k = s.kmLeft?.let { it / 40.0 } ?: Double.MAX_VALUE
        return minOf(d, k)
    }

    private fun levelForDays(days: Long) = when {
        days < 0 -> DueLevel.Overdue
        days <= SOON_DAYS -> DueLevel.Soon
        else -> DueLevel.Ok
    }

    private fun levelForKm(km: Double) = when {
        km < 0 -> DueLevel.Overdue
        km <= SOON_KM -> DueLevel.Soon
        else -> DueLevel.Ok
    }
}

/**
 * Odometer built from GPS distance. Skips poor fixes and jumps, and ignores crawling noise
 * so a parked car doesn't "drive" while the position wanders.
 */
class OdometerAccumulator(
    private val maxAccuracyM: Float = 35f,
    private val minSpeedKmh: Float = 4f,
    private val maxSpeedMs: Double = 70.0,
) {
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastTime = 0L
    private var has = false

    /** Returns metres travelled since the previous accepted fix. */
    fun onFix(lat: Double, lon: Double, accuracyM: Float?, speedKmh: Float, timeMs: Long): Double {
        if (accuracyM != null && accuracyM > maxAccuracyM) return 0.0
        if (!has) {
            set(lat, lon, timeMs)
            return 0.0
        }
        val dt = (timeMs - lastTime) / 1000.0
        if (dt <= 0.0) return 0.0
        val d = com.vikash.ampli.drive.cameras.CameraGeometry.distanceM(lastLat, lastLon, lat, lon)
        if (dt > 120.0 || d / dt > maxSpeedMs) {
            // Long gap or teleport: restart from here rather than guess the path.
            set(lat, lon, timeMs)
            return 0.0
        }
        if (speedKmh < minSpeedKmh) return 0.0
        set(lat, lon, timeMs)
        return max(0.0, d)
    }

    fun reset() {
        has = false
    }

    private fun set(lat: Double, lon: Double, t: Long) {
        lastLat = lat
        lastLon = lon
        lastTime = t
        has = true
    }
}
