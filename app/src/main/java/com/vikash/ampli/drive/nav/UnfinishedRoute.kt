package com.vikash.ampli.drive.nav

import com.vikash.ampli.drive.cameras.CameraGeometry

/** The in-app route that was running when the car was switched off. */
data class SavedRoute(val name: String, val area: String?, val lat: Double, val lon: Double, val savedAt: Long)

/** Whether to continue a saved route at the next start. Plain Kotlin, unit-tested. */
object UnfinishedRoute {
    /** A route older than this was a different day's trip: forget it. */
    const val MAX_AGE_MS = 6L * 60 * 60 * 1000

    /** Already this close to the destination: nothing to continue. */
    const val ARRIVED_M = 500.0

    /**
     * Continue [saved] now? Not when it is too old (or the clock jumped back), nor when the car
     * already stands near the destination. With no GPS fix yet ([lat] null) it continues.
     */
    fun shouldResume(saved: SavedRoute?, now: Long, lat: Double?, lon: Double?): Boolean {
        if (saved == null) return false
        if (now < saved.savedAt - 60_000L || now - saved.savedAt > MAX_AGE_MS) return false
        if (lat != null && lon != null && CameraGeometry.distanceM(lat, lon, saved.lat, saved.lon) < ARRIVED_M) return false
        return true
    }
}
