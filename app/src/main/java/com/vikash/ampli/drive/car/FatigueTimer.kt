package com.vikash.ampli.drive.car

/**
 * Break reminder clock. Pure logic: feed it one sample per GPS fix.
 *
 * A "stint" starts when the car first goes faster than [movingKmh]. Short stops (traffic,
 * toll plazas, a quick tea) don't end it; a stop of [breakMs] or longer does, and so does a gap
 * of [breakMs] with no samples at all (the unit was off). Once the stint reaches [thresholdMs]
 * the reminder fires, then again every [snoozeMs] until the driver takes a break.
 */
class FatigueTimer(
    var thresholdMs: Long = DEFAULT_THRESHOLD_MS,
    private val movingKmh: Float = 10f,
    private val breakMs: Long = 10L * MINUTE,
    private val snoozeMs: Long = 20L * MINUTE,
) {
    private var stintStart: Long? = null
    private var lastMoving: Long? = null
    private var lastSample: Long? = null
    private var nextReminderAt: Long? = null

    /** True while a stint is running (driving, or a short stop within one). */
    val driving: Boolean get() = stintStart != null

    /** Continuous driving time at [now], short stops included; 0 when parked. */
    fun drivingMs(now: Long): Long = stintStart?.let { (now - it).coerceAtLeast(0L) } ?: 0L

    /**
     * One sample. [speedKmh] null means no speed known (no fix).
     * Returns true when the reminder should be shown and spoken now.
     */
    fun onSample(now: Long, speedKmh: Float?): Boolean {
        val prev = lastSample
        if (prev != null && now - prev >= breakMs) reset()
        lastSample = now

        val moving = speedKmh != null && speedKmh > movingKmh
        if (moving) {
            if (stintStart == null) stintStart = now
            lastMoving = now
        } else {
            val lm = lastMoving
            if (lm != null && now - lm >= breakMs) reset()
            return false
        }
        val start = stintStart ?: return false
        val due = nextReminderAt ?: (start + thresholdMs)
        if (now >= due) {
            // Ignored or snoozed reminders come back after the snooze time.
            nextReminderAt = now + snoozeMs
            return true
        }
        return false
    }

    /** "Snooze 20 min". */
    fun snooze(now: Long) {
        if (stintStart != null) nextReminderAt = now + snoozeMs
    }

    /** The driver took a break (or the threshold changed): start counting afresh. */
    fun reset() {
        stintStart = null
        lastMoving = null
        nextReminderAt = null
    }

    companion object {
        const val MINUTE = 60_000L
        const val DEFAULT_THRESHOLD_MS = 120L * MINUTE

        /** Settings choices, in minutes (1 h to 4 h). */
        val THRESHOLD_CHOICES_MIN = listOf(60, 90, 120, 180, 240)
    }
}
