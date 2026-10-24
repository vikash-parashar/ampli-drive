package com.vikash.ampli.drive.car

/** Where the car was left, and when (epoch ms). */
data class ParkedSpot(val lat: Double, val lon: Double, val time: Long, val accuracyM: Float? = null) {
    /** Opens in Google Maps (app or browser) on the driver's phone. */
    val mapsUrl: String get() = ParkingQr.link(lat, lon)
}

/**
 * Decides when a drive has ended. A drive counts once the car has been above [drivingKmh]
 * for [minDriveMs]; it ends when the car stays at or below [parkedKmh] for [parkAfterMs], or
 * when GPS stops (app closed, ignition off, unit asleep: [onStopped]).
 */
class ParkingDetector(
    private val drivingKmh: Float = 15f,
    private val parkedKmh: Float = 3f,
    private val minDriveMs: Long = 60_000L,
    private val parkAfterMs: Long = 120_000L,
) {
    private var drivingSince: Long? = null
    private var armed = false
    private var stillSince: Long? = null

    /** Returns the spot when the car has just parked. */
    fun onSample(now: Long, kmh: Float, lat: Double, lon: Double, accuracyM: Float? = null): ParkedSpot? {
        when {
            kmh >= drivingKmh -> {
                val since = drivingSince ?: now.also { drivingSince = it }
                if (now - since >= minDriveMs) armed = true
                stillSince = null
            }
            kmh <= parkedKmh -> {
                if (!armed) {
                    drivingSince = null
                    return null
                }
                val since = stillSince ?: now.also { stillSince = it }
                if (now - since >= parkAfterMs) return park(lat, lon, now, accuracyM)
            }
            else -> stillSince = null
        }
        return null
    }

    /** GPS stopped after a drive: the last known position is the parking spot. */
    fun onStopped(now: Long, lat: Double?, lon: Double?, accuracyM: Float? = null): ParkedSpot? {
        if (!armed || lat == null || lon == null) return null
        return park(lat, lon, now, accuracyM)
    }

    private fun park(lat: Double, lon: Double, now: Long, accuracyM: Float?): ParkedSpot {
        armed = false
        drivingSince = null
        stillSince = null
        return ParkedSpot(lat, lon, now, accuracyM)
    }
}
