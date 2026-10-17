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
    /** True while our own fallback style (RemoteConfig.mapFallbackStyleUrl) is showing. */
    val basic: Boolean = false,
    /** No style could load and no fallback is configured: the pane shows "map tiles unavailable". */
    val unavailable: Boolean = false,
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
