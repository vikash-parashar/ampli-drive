package com.vikash.ampli.drive.sync

import com.vikash.ampli.drive.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * One thing a driver reported (police, camera, a pump queue), as it would travel between units.
 * [kind] is "police", "mobile_camera", "camera", "camera_gone", "accident", "jam", "queue_long"
 * or "queue_short"; [stationId] only for queues.
 */
data class SharedReport(
    val kind: String,
    val lat: Double,
    val lon: Double,
    val dir: Double?,
    val time: Long,
    val stationId: Int? = null,
)

/**
 * Sharing road reports and pump queues between drivers. Ampli has no server for this (no AWS,
 * nothing paid), so every report stays on the unit that made it and [ReportSync.forBuild] is
 * [Off] unless the build sets REPORT_SYNC_URL (a CI repository variable). The HTTP shape below
 * is only a contract for a server someone may run later; nothing here assumes one exists.
 */
interface ReportSync {
    val enabled: Boolean

    /** Sends one report; false when it could not (offline, no server). Never throws. */
    suspend fun push(report: SharedReport): Boolean

    /** Reports from other drivers within [radiusKm] of a point; empty when off or offline. */
    suspend fun pull(lat: Double, lon: Double, radiusKm: Int): List<SharedReport>

    /** The default: local only. */
    object Off : ReportSync {
        override val enabled = false
        override suspend fun push(report: SharedReport) = false
        override suspend fun pull(lat: Double, lon: Double, radiusKm: Int) = emptyList<SharedReport>()
    }

    companion object {
        /** [Off] unless BuildConfig.REPORT_SYNC_URL is set. */
        fun forBuild(): ReportSync = BuildConfig.REPORT_SYNC_URL.trim().takeIf { it.startsWith("https://") }?.let { HttpReportSync(it) } ?: Off
    }
}

/**
 * Client for a server at [baseUrl] that speaks: POST {base}/reports with one [SharedReport] as
 * JSON, and GET {base}/reports?lat=..&lon=..&r=<km> returning a JSON array of them. HTTPS only,
 * short timeouts, no user id or phone number is ever sent.
 */
class HttpReportSync(private val baseUrl: String) : ReportSync {
    override val enabled = true

    override suspend fun push(report: SharedReport): Boolean = withContext(Dispatchers.IO) {
        try {
            val c = (URL(baseUrl.trimEnd('/') + "/reports").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            c.outputStream.use { it.write(toJson(report).toString().toByteArray()) }
            val ok = c.responseCode in 200..299
            c.disconnect()
            ok
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun pull(lat: Double, lon: Double, radiusKm: Int): List<SharedReport> = withContext(Dispatchers.IO) {
        try {
            val url = URL(baseUrl.trimEnd('/') + "/reports?lat=%.4f&lon=%.4f&r=%d".format(java.util.Locale.US, lat, lon, radiusKm))
            val c = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            val text = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            val arr = JSONArray(text)
            (0 until minOf(arr.length(), 500)).mapNotNull { i -> fromJson(arr.optJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun toJson(r: SharedReport): JSONObject = JSONObject()
        .put("kind", r.kind).put("lat", r.lat).put("lon", r.lon)
        .put("dir", r.dir ?: JSONObject.NULL).put("time", r.time)
        .put("station", r.stationId ?: JSONObject.NULL)

    private fun fromJson(o: JSONObject?): SharedReport? {
        if (o == null) return null
        val lat = o.optDouble("lat", Double.NaN)
        val lon = o.optDouble("lon", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return null
        return SharedReport(
            kind = o.optString("kind"),
            lat = lat,
            lon = lon,
            dir = if (o.isNull("dir")) null else o.optDouble("dir"),
            time = o.optLong("time"),
            stationId = if (o.isNull("station")) null else o.optInt("station"),
        )
    }

    private companion object {
        const val TIMEOUT_MS = 8_000
    }
}
