package com.vikash.ampli.drive.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches Open-Meteo hourly visibility, precipitation, weather code and temperature from the
 * configured server. Throws when no weather server is configured (release builds without the paid
 * Open-Meteo plan or a self-hosted one): the caller treats it like being offline.
 */
object WeatherClient {

    /** [points] from [RouteWeather.points]. Throws on network errors. */
    suspend fun fetch(points: List<Triple<Int, Double, Double>>, now: Long = System.currentTimeMillis()): WeatherReport =
        withContext(Dispatchers.IO) {
            val cfg = com.vikash.ampli.drive.config.RemoteConfig.current()
            if (!cfg.weatherConfigured) throw java.io.IOException("Weather server not configured")
            val conn = URL(RouteWeather.url(points, cfg.weatherUrl, cfg.weatherApiKey)).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "AmpliCarHub (Android head unit)")
            try {
                if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                // One location gives an object; several give an array in the same order.
                val objects: List<JSONObject> = body.trimStart().let { text ->
                    if (text.startsWith("[")) JSONArray(text).let { a -> (0 until a.length()).map { a.getJSONObject(it) } } else listOf(JSONObject(text))
                }
                WeatherReport(
                    time = now,
                    points = points.mapIndexedNotNull { i, p -> objects.getOrNull(i)?.let { parse(p.first, it) } },
                )
            } finally {
                conn.disconnect()
            }
        }

    /** Worst of the current hour and the next. */
    private fun parse(aheadKm: Int, o: JSONObject): PointWeather {
        val h = o.optJSONObject("hourly") ?: return PointWeather(aheadKm, null, null, null)
        val vis = h.optJSONArray("visibility")
        val pr = h.optJSONArray("precipitation")
        val wc = h.optJSONArray("weather_code")
        val tc = h.optJSONArray("temperature_2m")
        fun JSONArray?.d(i: Int): Double? = this?.takeIf { i < it.length() && !it.isNull(i) }?.optDouble(i)
        val n = maxOf(vis?.length() ?: 0, pr?.length() ?: 0, wc?.length() ?: 0).coerceAtMost(2)
        var minVis: Double? = null
        var maxPr: Double? = null
        var worstCode: Int? = null
        var maxTemp: Double? = null
        for (i in 0 until n) {
            vis.d(i)?.let { minVis = minOf(minVis ?: it, it) }
            pr.d(i)?.let { maxPr = maxOf(maxPr ?: it, it) }
            wc.d(i)?.toInt()?.let { worstCode = maxOf(worstCode ?: it, it) }
            tc.d(i)?.let { maxTemp = maxOf(maxTemp ?: it, it) }
        }
        return PointWeather(aheadKm, minVis, maxPr, worstCode, maxTemp)
    }
}
