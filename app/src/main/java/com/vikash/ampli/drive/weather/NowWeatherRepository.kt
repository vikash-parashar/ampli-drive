package com.vikash.ampli.drive.weather

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.vikash.ampli.drive.BuildConfig
import com.vikash.ampli.drive.config.RemoteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The current weather for the voice answer: cached, offline-aware. */
class NowWeatherRepository(context: Context) {
    private val appContext = context.applicationContext
    private val cache = HashMap<String, NowWeather>()

    sealed interface Result {
        data class Ok(val weather: NowWeather, val stale: Boolean = false) : Result
        data object Offline : Result
        data object Failed : Result
    }

    suspend fun get(lat: Double, lon: Double, now: Long = System.currentTimeMillis()): Result {
        val key = NowWeatherCache.key(lat, lon)
        val cached = synchronized(cache) { cache[key] }
        if (NowWeatherCache.fresh(cached, now)) return Result.Ok(cached!!)
        if (!online()) {
            return if (NowWeatherCache.usableOffline(cached, now)) Result.Ok(cached!!, stale = true) else Result.Offline
        }
        val fetched = try {
            fetch(lat, lon, now)
        } catch (_: IOException) {
            null
        } catch (_: Exception) {
            null
        }
        if (fetched != null) {
            synchronized(cache) { cache[key] = fetched }
            return Result.Ok(fetched)
        }
        if (NowWeatherCache.usableOffline(cached, now)) return Result.Ok(cached!!, stale = true)
        return if (online()) Result.Failed else Result.Offline
    }

    private fun online(): Boolean = try {
        val cm = appContext.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: Exception) {
        // No permission to ask: try the request and let it fail.
        true
    }

    private suspend fun fetch(lat: Double, lon: Double, now: Long): NowWeather? = withContext(Dispatchers.IO) {
        val cfg = RemoteConfig.current()
        if (NowWeatherApi.useOpenMeteo(cfg.weatherUrl)) {
            val om = try {
                get(NowWeatherApi.openMeteoUrl(cfg.weatherUrl, lat, lon, cfg.weatherApiKey))?.let { NowWeatherApi.parseOpenMeteo(it, now) }
            } catch (_: Exception) {
                null
            }
            if (om != null) return@withContext om
        }
        get(NowWeatherApi.metUrl(lat, lon))?.let { NowWeatherApi.parseMet(it, now) }
    }

    private fun get(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", NowWeatherApi.userAgent(BuildConfig.VERSION_NAME))
        conn.setRequestProperty("Accept", "application/json")
        return try {
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
