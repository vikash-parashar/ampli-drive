package com.vikash.ampli.drive.car

/**
 * The Haryanvi night-drive line ("रात हो गी, हळवे चलाइए…"): once, when it is past 11 pm (or
 * before 4 am) and the car has been driving for more than [afterMs] in one stint. Not again for
 * [quietMs], so a long night trip with a tea stop hears it once. Pure logic, unit-tested.
 */
class NightDriveReminder(
    private val afterMs: Long = 30L * 60 * 1000,
    private val quietMs: Long = 6L * 60 * 60 * 1000,
) {
    private var lastAt: Long? = null

    /** One GPS sample: [hour] 0..23 local, [drivingMs] the current stint. True when to say it now. */
    fun onSample(now: Long, hour: Int, drivingMs: Long): Boolean {
        if (!isNight(hour) || drivingMs <= afterMs) return false
        val last = lastAt
        if (last != null && now - last < quietMs) return false
        lastAt = now
        return true
    }

    companion object {
        /** 11 pm to 4 am. */
        fun isNight(hour: Int): Boolean = hour >= 23 || hour < 4
    }
}
