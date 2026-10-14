package com.vikash.ampli.drive.cameras

import android.content.Context
import android.location.Location
import org.json.JSONObject
import kotlin.math.abs

/** One fixed speed camera from assets/cameras.json. */
data class SpeedCamera(val lat: Double, val lon: Double, val road: String?, val limitKmh: Int?)

/** The camera the voice assistant names: [distanceMeters] straight-line from the car. */
data class CameraAhead(val camera: SpeedCamera, val distanceMeters: Float)

/**
 * Minimal camera list for the voice assistant's "when is the next speed camera".
 *
 * TODO: replace assets/cameras.json (empty for now) with the real dataset and share it
 * with the CameraAlerts engine; live speed-camera alerts are a separate task.
 */
class CameraRepository(private val context: Context) {
    private val cameras: List<SpeedCamera> by lazy { load() }

    /**
     * The nearest camera within [maxMeters] that lies ahead of the car: within 60 degrees of
     * the GPS bearing when the car is moving, otherwise simply the nearest one.
     */
    fun nextAhead(here: Location, maxMeters: Float = 30_000f): CameraAhead? {
        val heading = if (here.hasBearing() && here.hasSpeed() && here.speed > 3f) here.bearing else null
        return cameras.asSequence()
            .map { cam ->
                val target = Location("cam").apply { latitude = cam.lat; longitude = cam.lon }
                Triple(cam, here.distanceTo(target), here.bearingTo(target))
            }
            .filter { (_, d, b) -> d <= maxMeters && (heading == null || angle(heading, b) <= 60f) }
            .minByOrNull { it.second }
            ?.let { CameraAhead(it.first, it.second) }
    }

    private fun angle(a: Float, b: Float): Float {
        val d = abs(((a - b) % 360f + 360f) % 360f)
        return if (d > 180f) 360f - d else d
    }

    private fun load(): List<SpeedCamera> = try {
        val json = context.assets.open("cameras.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(json).optJSONArray("cameras")
        if (arr == null) {
            emptyList()
        } else {
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                if (!o.has("lat") || !o.has("lon")) return@mapNotNull null
                SpeedCamera(
                    lat = o.getDouble("lat"),
                    lon = o.getDouble("lon"),
                    road = o.optString("road", "").ifEmpty { null },
                    limitKmh = o.optInt("limitKmh", 0).takeIf { it > 0 },
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
