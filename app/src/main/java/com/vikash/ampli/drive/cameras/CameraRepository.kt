package com.vikash.ampli.drive.cameras

import android.content.Context
import android.location.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** What Settings › Speed cameras shows. */
data class CameraStats(val loaded: Int, val reported: Int, val dataDate: String?, val ready: Boolean)

/**
 * The offline camera database: assets/cameras.json plus the driver's own reports.
 *
 * assets/cameras.json is generated from OpenStreetMap (highway=speed_camera nodes, nodes with
 * enforcement=maxspeed, and the device members of type=enforcement relations) by
 * scripts/fetch_cameras.py during the CI build. That data is © OpenStreetMap contributors and
 * licensed under the ODbL 1.0: keep the attribution in Settings, and the derived database stays
 * under the ODbL (share-alike).
 *
 * Format: a JSON array of {"id", "lat", "lon", "maxspeed" (km/h or null), "dir" (bearing or null),
 * "type"}; assets/cameras_meta.json holds {"generated": "YYYY-MM-DD", ...}.
 */
class CameraRepository(context: Context) {
    private val appContext = context.applicationContext
    private val reportsPrefs = appContext.getSharedPreferences(REPORTS_FILE, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    @Volatile private var index = CameraIndex(emptyList())
    private var bundled: List<SpeedCamera>? = null
    private var dataDate: String? = null
    private var started = false

    private val _stats = MutableStateFlow(CameraStats(0, 0, null, ready = false))
    val stats: StateFlow<CameraStats> = _stats.asStateFlow()

    /** Starts loading on a background thread; queries return nothing until it is done. */
    fun ensureLoaded() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        scope.launch { rebuild() }
    }

    /** Cameras within about [radiusM] of the point (bucket-accurate). */
    fun near(lat: Double, lon: Double, radiusM: Double): List<SpeedCamera> = index.near(lat, lon, radiusM)

    /**
     * For the voice assistant: the nearest camera within [maxMeters] ahead. [bearing] is the GPS
     * heading, or null when standing still; then the nearest camera in any direction counts.
     * Uses a ±45° cone because roads bend over a few kilometres.
     */
    fun nextCamera(location: Location, bearing: Float?, maxMeters: Double = VOICE_RANGE_M): CameraAhead? {
        val candidates = index.near(location.latitude, location.longitude, maxMeters)
        return CameraGeometry.nearestAhead(
            candidates,
            location.latitude,
            location.longitude,
            heading = bearing?.toDouble(),
            speedKmh = if (bearing != null) CameraGeometry.SLOW_KMH else 0.0,
            maxDistanceM = maxMeters,
            coneDeg = VOICE_CONE_DEG,
            anyDirectionWhenUnknown = true,
        )
    }

    /**
     * Saves a camera at the car's position. The GPS bearing becomes the camera's direction when
     * the car is moving, so it only alerts on this side of the road.
     *
     * TODO: upload reports (with confirm/deny votes) to the Ampli backend once it exists.
     */
    fun report(location: Location, speedKmh: Float?): SpeedCamera {
        val moving = location.hasBearing() && (speedKmh ?: 0f) >= CameraGeometry.SLOW_KMH
        val cam = SpeedCamera(
            id = "u${System.currentTimeMillis()}",
            lat = location.latitude,
            lon = location.longitude,
            maxspeed = null,
            dir = if (moving) location.bearing.toDouble() else null,
            type = "user",
            userReported = true,
        )
        synchronized(lock) {
            val arr = readReportsJson()
            arr.put(
                JSONObject()
                    .put("id", cam.id)
                    .put("lat", cam.lat)
                    .put("lon", cam.lon)
                    .put("dir", cam.dir ?: JSONObject.NULL)
                    .put("time", System.currentTimeMillis()),
            )
            reportsPrefs.edit().putString(KEY_REPORTS, arr.toString()).apply()
        }
        scope.launch { rebuild() }
        return cam
    }

    private fun rebuild() {
        synchronized(lock) {
            val osm = bundled ?: loadBundled().also { bundled = it }
            val mine = loadReports()
            index = CameraIndex(osm + mine)
            _stats.value = CameraStats(osm.size, mine.size, dataDate, ready = true)
        }
    }

    private fun loadBundled(): List<SpeedCamera> {
        dataDate = try {
            val meta = appContext.assets.open(META_ASSET).bufferedReader().use { it.readText() }
            JSONObject(meta).optString("generated", "").ifEmpty { null }
        } catch (_: Exception) {
            null
        }
        return try {
            val text = appContext.assets.open(ASSET).bufferedReader().use { it.readText() }.trim()
            // Older builds wrapped the list as {"cameras": [...]}.
            val arr = if (text.startsWith("{")) JSONObject(text).optJSONArray("cameras") ?: JSONArray() else JSONArray(text)
            (0 until arr.length()).mapNotNull { i -> parse(arr.optJSONObject(i), userReported = false) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadReports(): List<SpeedCamera> {
        val arr = readReportsJson()
        return (0 until arr.length()).mapNotNull { i -> parse(arr.optJSONObject(i), userReported = true) }
    }

    private fun readReportsJson(): JSONArray = try {
        JSONArray(reportsPrefs.getString(KEY_REPORTS, "[]") ?: "[]")
    } catch (_: Exception) {
        JSONArray()
    }

    private fun parse(o: JSONObject?, userReported: Boolean): SpeedCamera? {
        if (o == null || !o.has("lat") || !o.has("lon")) return null
        val lat = o.optDouble("lat", Double.NaN)
        val lon = o.optDouble("lon", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return null
        return SpeedCamera(
            id = o.optString("id", "").ifEmpty { "c${lat}_$lon" },
            lat = lat,
            lon = lon,
            maxspeed = if (o.isNull("maxspeed")) null else o.optInt("maxspeed", 0).takeIf { it in 5..200 },
            dir = if (o.isNull("dir")) null else o.optDouble("dir", Double.NaN).takeIf { !it.isNaN() },
            type = if (userReported) "user" else o.optString("type", "speed_camera"),
            userReported = userReported,
        )
    }

    private companion object {
        const val ASSET = "cameras.json"
        const val META_ASSET = "cameras_meta.json"
        const val REPORTS_FILE = "camera_reports"
        const val KEY_REPORTS = "reports"
        const val VOICE_RANGE_M = 20_000.0
        const val VOICE_CONE_DEG = 45.0
    }
}
