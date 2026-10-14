package com.vikash.ampli.drive.map

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the glance map is doing, for Settings › About › Diagnostics and the "Basic map" note. */
data class MapStatus(
    val styleUrl: String? = null,
    val phase: Phase = Phase.NotStarted,
    /** True while the simple OpenStreetMap raster style is showing instead of the vector one. */
    val basic: Boolean = false,
    val textureMode: Boolean = true,
    /** At least one frame drawn by MapLibre since the map view was made. */
    val drewFrame: Boolean = false,
    val lastError: String? = null,
    val errorCount: Int = 0,
    /** Errors that look like network trouble (HTTP, TLS, DNS, timeouts). */
    val netErrorCount: Int = 0,
) {
    enum class Phase { NotStarted, LoadingStyle, StyleLoaded, MapLoaded, Failed }
}

object MapDiagnostics {
    const val TAG = "AmpliMap"

    private val _status = MutableStateFlow(MapStatus())
    val status: StateFlow<MapStatus> = _status.asStateFlow()

    fun update(change: (MapStatus) -> MapStatus) = _status.update(change)

    private val NET_HINTS = listOf("http", "ssl", "tls", "certificate", "connection", "timeout", "timed out", "resolve", "unreachable", "network")

    fun error(message: String) {
        Log.w(TAG, message)
        val net = message.lowercase().let { m -> NET_HINTS.any { m.contains(it) } }
        _status.update {
            it.copy(
                lastError = message.take(200),
                errorCount = it.errorCount + 1,
                netErrorCount = it.netErrorCount + if (net) 1 else 0,
            )
        }
    }
}

/**
 * A raster style with standard OpenStreetMap tiles, used when the vector style can't load in
 * time (old CA store, blocked host, GL trouble with vector tiles). Tiles are requested with
 * MapLibre's user agent, which names the app as the OSM tile policy asks; it is a fallback,
 * not the normal path, so traffic to tile.openstreetmap.org stays small.
 */
const val BASIC_ATTRIBUTION = "© OpenStreetMap contributors"

const val BASIC_STYLE_JSON = """{
  "version": 8,
  "name": "Ampli basic",
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "maxzoom": 19,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [
    {"id": "background", "type": "background", "paint": {"background-color": "#e8e4dc"}},
    {"id": "osm", "type": "raster", "source": "osm"}
  ]
}"""
