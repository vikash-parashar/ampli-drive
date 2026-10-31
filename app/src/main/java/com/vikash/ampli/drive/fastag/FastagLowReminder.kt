package com.vikash.ampli.drive.fastag

/**
 * The Haryanvi "FASTag balance low" line, said after the toll line when the balance the toll SMS
 * reported is under the driver's alert level (FASTag › Low balance alert). At most once a day, so
 * a run of tolls on one highway doesn't nag. Pure logic, unit-tested.
 */
class FastagLowReminder {
    private var lastDay: Long? = null

    /** [day] is a local day number (for example epoch day). True when to say it after this toll. */
    fun afterToll(balance: Double?, lowBalance: Int, day: Long): Boolean {
        if (balance == null || balance >= lowBalance) return false
        if (lastDay == day) return false
        lastDay = day
        return true
    }
}
