package com.vikash.ampli.drive.voice

import android.location.Location
import com.vikash.ampli.drive.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One destination the driver can pick. [distanceKm] is straight-line, null without a GPS fix. */
data class Place(
    val name: String,
    val area: String?,
    val lat: Double,
    val lon: Double,
    val distanceKm: Double?,
)

/**
 * Destination search through Photon (free OpenStreetMap geocoding with location bias).
 *
 * TODO: point [ENDPOINT] at our own Photon box before launch. The business plan budgets a
 * self-hosted instance; the public komoot server is for light testing only and has no SLA.
 */
object PlaceSearch {
    private const val ENDPOINT = "https://photon.komoot.io/api/"
    private const val FETCH = 5
    const val KEEP = 3

    /** Throws IOException on network trouble; an empty list means nothing matched. */
    suspend fun search(query: String, near: Location?): List<Place> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val bias = if (near != null) "&lat=${near.latitude}&lon=${near.longitude}" else ""
        val url = URL("$ENDPOINT?q=$q$bias&limit=$FETCH")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", "AmpliCarHub/${BuildConfig.VERSION_NAME} (Android head-unit launcher)")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) throw java.io.IOException("Photon HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parse(body, near)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String, near: Location?): List<Place> {
        val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
        val out = ArrayList<Place>()
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            val lon = coords.optDouble(0, Double.NaN)
            val lat = coords.optDouble(1, Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue
            val p = f.optJSONObject("properties") ?: JSONObject()
            val street = p.str("street")?.let { s -> p.str("housenumber")?.let { "$it $s" } ?: s }
            val name = p.str("name") ?: street ?: p.str("city") ?: continue
            val area = listOfNotNull(p.str("district") ?: p.str("locality"), p.str("city"))
                .distinct()
                .filter { it != name }
                .joinToString(", ")
                .ifEmpty { null }
            val km = near?.let {
                val r = FloatArray(1)
                Location.distanceBetween(it.latitude, it.longitude, lat, lon, r)
                r[0] / 1000.0
            }
            out += Place(name, area, lat, lon, km)
        }
        // Same place twice (a node and a way): keep the first.
        return out
            .distinctBy { it.name.lowercase() + "|" + (it.area ?: "") }
            .sortedBy { it.distanceKm ?: Double.MAX_VALUE }
            .take(KEEP)
    }

    private fun JSONObject.str(key: String): String? = optString(key, "").trim().ifEmpty { null }
}
