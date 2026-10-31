package com.vikash.ampli.drive.cameras

/** The parts of fuel finder and radar that the Free / Pro split talks about. */
enum class RadarFeature {
    FuelFinder,
    FixedCameras,
    RedLightCameras,
    AverageSpeedZones,
    Reports,
    AheadList,
}

/**
 * The Free / Pro split for fuel finder and radar, in one place so it is easy to change.
 *
 * Proposed: the fuel / CNG / EV finder and fixed-camera alerts are free; red-light cameras,
 * average-speed zone tracking, road reports and the "Coming up on this road" list are Pro.
 * (Average-speed camera points from OpenStreetMap still alert for everyone, as they did before
 * as fixed cameras; only the zone countdown and average speed are Pro.)
 */
object RadarTier {
    val PRO: Set<RadarFeature> = setOf(
        RadarFeature.RedLightCameras,
        RadarFeature.AverageSpeedZones,
        RadarFeature.Reports,
        RadarFeature.AheadList,
    )

    fun allowed(feature: RadarFeature, isPro: Boolean): Boolean = isPro || feature !in PRO

    /** Which feature an enforcement point belongs to, by its [SpeedCamera.type]. */
    fun featureFor(type: String): RadarFeature = when {
        type == SpeedCamera.TYPE_RED_LIGHT -> RadarFeature.RedLightCameras
        type.startsWith(SpeedCamera.REPORT_PREFIX) -> RadarFeature.Reports
        else -> RadarFeature.FixedCameras
    }

    /** True when the driver may be alerted about a point of [type]. */
    fun alerts(type: String, isPro: Boolean): Boolean = allowed(featureFor(type), isPro)
}
