package com.vikash.ampli.drive.cameras

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One warning about an enforcement point on the road ahead. */
data class CameraAlert(
    val id: String,
    val kind: Kind,
    /** Straight-line distance from the car to the camera, in metres. */
    val distanceMeters: Int,
    /** Enforced limit at the camera, in km/h, when known. */
    val limitKmh: Int?,
    /** Short human description, for example the road name; may be empty. */
    val description: String,
    /** The car is faster than [limitKmh]: the chip and speedometer turn red. */
    val overLimit: Boolean = false,
    /** Added by the driver with "Report camera here". */
    val userReported: Boolean = false,
    /** Road reports: when it was reported (they expire after two hours). */
    val reportedAt: Long? = null,
    /** Inside an average-speed zone: the average so far, when known. */
    val averageKmh: Int? = null,
) {
    enum class Kind { FixedCamera, AverageSpeedStart, AverageSpeedZone, RedLight, MobileCamera, Police, Accident, Jam }

    companion object {
        /** The alert kind for an enforcement point's [SpeedCamera.type]. */
        fun kindFor(type: String): Kind = when (type) {
            SpeedCamera.TYPE_AVERAGE -> Kind.AverageSpeedStart
            SpeedCamera.TYPE_RED_LIGHT -> Kind.RedLight
            ReportKind.MobileCamera.type -> Kind.MobileCamera
            ReportKind.Police.type -> Kind.Police
            ReportKind.Accident.type -> Kind.Accident
            ReportKind.Jam.type -> Kind.Jam
            else -> Kind.FixedCamera
        }
    }
}

/**
 * Speed-camera and speed-limit engine used by the speed pane.
 *
 * The speed tracker calls [onLocation] for every GPS fix. The UI only reads [current]
 * and [speedLimitKmh]; a null value means "nothing known" and the UI shows "--".
 */
interface CameraAlerts {
    /** The alert to show now, or null when the road ahead is clear or unknown. */
    val current: StateFlow<CameraAlert?>

    /** Posted limit where the car is, or null when unknown. */
    val speedLimitKmh: StateFlow<Int?>

    /** Feed one GPS fix plus the smoothed speed the driver sees. */
    fun onLocation(location: Location, speedKmh: Float)

    /** Drop any alert state, for example when GPS stops. */
    fun reset()
}

/**
 * An engine with no data: it never raises an alert and never claims a speed limit.
 * The app uses [LiveCameraAlerts].
 */
object NoCameraAlerts : CameraAlerts {
    private val none = MutableStateFlow<CameraAlert?>(null)
    private val noLimit = MutableStateFlow<Int?>(null)

    override val current: StateFlow<CameraAlert?> = none.asStateFlow()
    override val speedLimitKmh: StateFlow<Int?> = noLimit.asStateFlow()

    override fun onLocation(location: Location, speedKmh: Float) = Unit

    override fun reset() = Unit
}
