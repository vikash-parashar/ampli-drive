package com.vikash.ampli.drive.location

/**
 * Average speed over the moving time of the current trip: distance (speed × time between fixes)
 * divided by the time spent above [MOVING_KMH]. Waiting at lights doesn't pull it down. A stop
 * longer than [NEW_TRIP_GAP_MS] (or [reset], on ignition on) starts a new trip. Plain Kotlin.
 */
class TripAverage {
    private var movingMs = 0L
    private var distanceKm = 0.0
    private var lastAtMs = -1L
    private var lastMovingAtMs = -1L

    /** Average in km/h, or null until the car has moved for [MIN_MOVING_MS]. */
    val averageKmh: Float?
        get() = if (movingMs < MIN_MOVING_MS) null else (distanceKm / (movingMs / 3_600_000.0)).toFloat()

    fun onSpeed(atMs: Long, kmh: Float) {
        if (lastMovingAtMs >= 0 && atMs - lastMovingAtMs > NEW_TRIP_GAP_MS) reset()
        val prev = lastAtMs
        lastAtMs = atMs
        if (kmh >= MOVING_KMH) lastMovingAtMs = atMs
        if (prev < 0) return
        val dt = atMs - prev
        // Gaps (tunnel, GPS dropout) count only up to MAX_STEP_MS.
        if (dt <= 0 || kmh < MOVING_KMH) return
        val step = dt.coerceAtMost(MAX_STEP_MS)
        movingMs += step
        distanceKm += kmh * step / 3_600_000.0
    }

    fun reset() {
        movingMs = 0L
        distanceKm = 0.0
        lastAtMs = -1L
        lastMovingAtMs = -1L
    }

    companion object {
        const val MOVING_KMH = 3f
        const val MIN_MOVING_MS = 30_000L
        const val MAX_STEP_MS = 5_000L
        const val NEW_TRIP_GAP_MS = 15 * 60_000L
    }
}
