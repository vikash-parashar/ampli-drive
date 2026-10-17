package com.vikash.ampli.drive.car

/**
 * "भाई सीट बेल्ट पहन ले।" Head units have no seat-belt sensor, so this is a gentle reminder once
 * per trip: when the car first starts moving (a new [FatigueTimer] stint), and never again within
 * [quietMs] (a short park and drive on doesn't repeat it). Pure logic, unit-tested.
 */
class SeatBeltReminder(private val quietMs: Long = 45L * 60 * 1000) {
    private var wasDriving = false
    private var lastAt: Long? = null

    /** One sample: [driving] is FatigueTimer.driving after the fix. True when to remind now. */
    fun onSample(now: Long, driving: Boolean): Boolean {
        val started = driving && !wasDriving
        wasDriving = driving
        if (!started) return false
        val last = lastAt
        if (last != null && now - last < quietMs) return false
        lastAt = now
        return true
    }

    /** Ignition on: a new trip, remind again on the first movement. */
    fun newTrip() {
        wasDriving = false
        lastAt = null
    }
}
