package com.vikash.ampli.drive.car

import java.time.LocalDate
import kotlin.math.roundToInt

/** One service reminder to say at car start: by distance ([kmLeft]) or by date ([daysLeft]). */
data class ServiceReminder(
    val id: String,
    val name: String,
    val overdue: Boolean,
    /** Set when the distance is the nearer limit, rounded to 10 km. */
    val kmLeft: Int?,
    /** Set when the date is the nearer limit. */
    val daysLeft: Long?,
)

/**
 * Which service jobs (oil change, tyre rotation, coolant…) to say at car start. Pure, unit-tested.
 * Uses the same due arithmetic as the Papers screen ([Reminders.service]); says at most
 * [MAX_PER_START] per start, each at most once a day (overdue ones every few days), so the start
 * stays short. Papers have their own reminder ([PaperVoicePolicy]).
 */
object ServiceVoicePolicy {
    const val MAX_PER_START = 1
    const val OVERDUE_REPEAT_DAYS = 3L

    /** Rough common scale (as [Reminders]): 1 day ≈ 40 km of driving. */
    private const val KM_PER_DAY = 40.0

    fun pick(
        items: List<ServiceItem>,
        today: LocalDate,
        odometerKm: Double,
        lastSaid: Map<String, Long>,
    ): List<ServiceReminder> {
        val day = today.toEpochDay()
        val out = ArrayList<Pair<Double, ServiceReminder>>()
        for (item in items) {
            val s = Reminders.service(item, today, odometerKm)
            if (s.level < DueLevel.Soon) continue
            val overdue = s.level == DueLevel.Overdue
            val last = lastSaid[item.id]
            if (last != null && day - last < if (overdue) OVERDUE_REPEAT_DAYS else 1L) continue
            val km = s.kmLeft
            val days = s.daysLeft
            val byKm = km != null && (days == null || km / KM_PER_DAY < days)
            val r = if (byKm) {
                ServiceReminder(item.id, item.name, overdue, kmLeft = roundKm(km!!), daysLeft = null)
            } else {
                ServiceReminder(item.id, item.name, overdue, kmLeft = null, daysLeft = days)
            }
            val urgency = minOf(days?.toDouble() ?: Double.MAX_VALUE, km?.let { it / KM_PER_DAY } ?: Double.MAX_VALUE)
            out += urgency to r
        }
        return out.sortedBy { it.first }.map { it.second }.take(MAX_PER_START)
    }

    /** 437 → 440, -12 → -10: a spoken distance needs no single kilometres. */
    fun roundKm(km: Double): Int = ((km / 10.0).roundToInt() * 10)
}
