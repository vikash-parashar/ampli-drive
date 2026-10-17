package com.vikash.ampli.drive.config

import android.content.Context
import com.vikash.ampli.drive.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.HttpURLConnection
import java.net.URL

/**
 * A small JSON file on our own server (BuildConfig.REMOTE_CONFIG_URL) that can move the routing,
 * place-search, weather and map-fallback servers without an app update. Fetched at most every
 * [REFRESH_MS] with a plain GET (no device id, no install id, no analytics: only the app version
 * in the User-Agent), cached in SharedPreferences, and layered over the BuildConfig values, which
 * stay the fallback when the file is missing, unreachable or invalid.
 */
object RemoteConfig {
    /** What BuildConfig gives this build (CI repository variables; demo servers in debug builds only). */
    val builtIn: Endpoints = Endpoints(
        routingBaseUrl = BuildConfig.ROUTING_BASE_URL,
        geocoderUrl = BuildConfig.GEOCODER_URL,
        weatherUrl = BuildConfig.WEATHER_URL,
        weatherApiKey = BuildConfig.WEATHER_API_KEY,
        mapFallbackStyleUrl = BuildConfig.MAP_FALLBACK_STYLE_URL,
    )

    private val _endpoints = MutableStateFlow(builtIn)
    val endpoints: StateFlow<Endpoints> = _endpoints.asStateFlow()

    /** The endpoints in use now. Safe from any thread. */
    fun current(): Endpoints = _endpoints.value

    /** Last fetch result, for Settings › About › Diagnostics. */
    @Volatile var lastResult: String = if (BuildConfig.REMOTE_CONFIG_URL.isBlank()) "not configured" else "not fetched yet"
        private set

    private const val FILE = "remote_config"
    private const val KEY_JSON = "json"
    private const val KEY_AT = "fetched_at"
    const val REFRESH_MS = 12L * 60 * 60 * 1000

    @Volatile private var started = false

    /** Reads the cache and, when it is stale, refreshes it on a background thread. Idempotent. */
    fun init(context: Context) {
        if (started) return
        started = true
        val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        sp.getString(KEY_JSON, null)?.let { Endpoints.parse(it) }?.let { _endpoints.value = builtIn.overlay(it) }
        val url = BuildConfig.REMOTE_CONFIG_URL
        if (url.isBlank() || !Endpoints.isHttps(url)) return
        if (System.currentTimeMillis() - sp.getLong(KEY_AT, 0L) < REFRESH_MS) return
        Thread({
            lastResult = try {
                val body = fetch(url)
                val parsed = Endpoints.parse(body)
                if (parsed == null) {
                    "invalid JSON"
                } else {
                    sp.edit().putString(KEY_JSON, body).putLong(KEY_AT, System.currentTimeMillis()).apply()
                    _endpoints.value = builtIn.overlay(parsed)
                    "ok"
                }
            } catch (e: Exception) {
                e.javaClass.simpleName + ": " + (e.message ?: "")
            }
        }, "ampli-remote-config").apply { isDaemon = true }.start()
    }

    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", "AmpliCarHub/${BuildConfig.VERSION_NAME}")
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            // The file is a few hundred bytes; refuse anything absurd.
            if (conn.contentLengthLong > 64 * 1024) throw java.io.IOException("too large")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return text
        } finally {
            conn.disconnect()
        }
    }
}
