package com.vikash.ampli.drive.cameras

import kotlin.math.roundToInt

/**
 * Challan-bachat counter: counts the over-speed warnings the driver obeyed. Plain Kotlin, fed
 * one sample per GPS fix by [LiveCameraAlerts].
 *
 * A warning is the "slow down" Ampli gives when the car is over a camera's (or an average-speed
 * zone's) limit. It counts as obeyed when the car is at or below the limit within [windowMs],
 * before it has passed the camera. It is a rough, honest estimate: Ampli cannot know whether that
 * camera was switched on, or whether a challan would really have come.
 */
class ChallanSaver(private val windowMs: Long = WINDOW_MS) {

    private data class Pending(val alertId: String, val limitKmh: Int, val at: Long)

    private var pending: Pending? = null

    /** Alerts already counted this pass, so one camera counts once. */
    private val counted = LinkedHashSet<String>()

    /**
     * One GPS sample. [alertId] / [limitKmh] are the camera or zone on screen (null when none);
     * [warnedNow] is true when Ampli has just said "slow down" for it. Returns true when a
     * warning was obeyed at this sample (count one more).
     */
    fun onSample(now: Long, speedKmh: Double, alertId: String?, limitKmh: Int?, warnedNow: Boolean): Boolean {
        val p = pending
        if (warnedNow && alertId != null && limitKmh != null && alertId !in counted) {
            if (p == null || p.alertId != alertId) pending = Pending(alertId, limitKmh, now)
        }
        val cur = pending ?: return false
        // Passed the camera (it left the screen) or another one took over: missed.
        if (alertId != cur.alertId) {
            pending = null
            return false
        }
        if (now - cur.at > windowMs) {
            pending = null
            return false
        }
        if (speedKmh <= cur.limitKmh + TOLERANCE_KMH) {
            pending = null
            counted += cur.alertId
            if (counted.size > 64) counted.remove(counted.first())
            return true
        }
        return false
    }

    fun reset() {
        pending = null
        counted.clear()
    }

    companion object {
        /** Slow down within this long of the warning. */
        const val WINDOW_MS = 20_000L

        /** GPS speed reads a little high; 2 km/h over still counts as obeyed. */
        const val TOLERANCE_KMH = 2

        /**
         * Motor Vehicles Act s.183 (as amended 2019): over-speeding in a light motor vehicle is
         * ₹1,000 to ₹2,000 for a first offence. Ampli shows the range and uses the low end for
         * the one-number total, so it never over-claims.
         */
        const val FINE_LOW_RUPEES = 1_000
        const val FINE_HIGH_RUPEES = 2_000

        fun savedLow(count: Int): Int = count.coerceAtLeast(0) * FINE_LOW_RUPEES

        fun savedHigh(count: Int): Int = count.coerceAtLeast(0) * FINE_HIGH_RUPEES

        /**
         * The saving in kilometres of driving: the low-end saving divided by the running cost
         * per km (fuel and expenses). Null without a cost per km.
         */
        fun kmOfDriving(count: Int, costPerKm: Double?): Int? {
            if (costPerKm == null || costPerKm <= 0.0 || count <= 0) return null
            return (savedLow(count) / costPerKm).roundToInt()
        }
    }
}
