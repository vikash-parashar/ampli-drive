package com.vikash.ampli.drive.map

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import com.vikash.ampli.drive.BuildConfig
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.ui.theme.MapLook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion as MlRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/** What Settings › Offline map shows. */
data class OfflineMapState(
    val phase: Phase = Phase.Idle,
    /** The region on the unit (or being fetched), by [OfflineRegion.key]. */
    val regionKey: String? = null,
    val percent: Int = 0,
    val bytes: Long = 0L,
    val error: String? = null,
) {
    enum class Phase { Idle, Checking, Downloading, Paused, Ready, NeedsWifi, NoSpace, Failed }
}

/**
 * One region of the map kept on the unit, in MapLibre's own offline database, so the map the app
 * already shows keeps working without internet (no SIM in the head unit). The download starts
 * only on the driver's tap, only on Wi-Fi, and only when the storage check passes.
 *
 * Tile source: the same OpenFreeMap style the map uses (or OFFLINE_MAP_STYLE_URL, a repository
 * variable, for a self-hosted region file later). Map data © OpenStreetMap contributors (ODbL),
 * OpenMapTiles schema, OpenFreeMap hosting. tile.openstreetmap.org is never used.
 */
class OfflineMapManager(private val app: DriveApp) {
    private val _state = MutableStateFlow(OfflineMapState())
    val state: StateFlow<OfflineMapState> = _state.asStateFlow()

    private var region: MlRegion? = null
    private var loaded = false

    /** The style to keep offline: the build's own one when set, else the map's default style. */
    val styleUrl: String
        get() = BuildConfig.OFFLINE_MAP_STYLE_URL.trim().ifEmpty { MapLook.Liberty.url }

    private fun manager(): OfflineManager? = try {
        MapLibre.getInstance(app.applicationContext)
        OfflineManager.getInstance(app.applicationContext)
    } catch (e: Throwable) {
        Log.w(TAG, "offline manager unavailable", e)
        null
    }

    /** Looks for a region already on the unit (once). */
    fun refresh() {
        if (loaded) return
        loaded = true
        val m = manager() ?: return
        m.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<MlRegion>?) {
                val r = offlineRegions?.firstOrNull() ?: return
                region = r
                val key = String(r.metadata)
                r.getStatus(object : MlRegion.OfflineRegionStatusCallback {
                    override fun onStatus(status: OfflineRegionStatus?) {
                        if (status == null) return
                        _state.value = OfflineMapState(
                            phase = if (status.isComplete) OfflineMapState.Phase.Ready else OfflineMapState.Phase.Paused,
                            regionKey = key,
                            percent = percentOf(status),
                            bytes = status.completedResourceSize,
                        )
                    }

                    override fun onError(error: String?) = Unit
                })
            }

            override fun onError(error: String) {
                Log.w(TAG, "list: $error")
            }
        })
    }

    fun onWifi(): Boolean = try {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } catch (_: Exception) {
        false
    }

    fun freeBytes(): Long = try {
        StatFs(app.filesDir.absolutePath).availableBytes
    } catch (_: Exception) {
        0L
    }

    /** Downloads [r] (replacing any other region). Checks Wi-Fi and storage first. */
    fun download(r: OfflineRegion) {
        if (!onWifi()) {
            _state.value = OfflineMapState(OfflineMapState.Phase.NeedsWifi, r.key)
            return
        }
        if (!OfflineRegions.fits(r, freeBytes())) {
            _state.value = OfflineMapState(OfflineMapState.Phase.NoSpace, r.key)
            return
        }
        val m = manager() ?: run {
            _state.value = OfflineMapState(OfflineMapState.Phase.Failed, r.key, error = "map engine")
            return
        }
        _state.value = OfflineMapState(OfflineMapState.Phase.Checking, r.key)
        val old = region
        if (old != null && String(old.metadata) != r.key) {
            old.setDownloadState(MlRegion.STATE_INACTIVE)
            old.delete(object : MlRegion.OfflineRegionDeleteCallback {
                override fun onDelete() = Unit
                override fun onError(error: String) = Unit
            })
            region = null
        }
        val existing = region
        if (existing != null) {
            start(existing, r.key)
            return
        }
        m.setOfflineMapboxTileCountLimit(OfflineRegions.TILE_LIMIT)
        val bounds = LatLngBounds.Builder()
            .include(LatLng(r.north, r.east))
            .include(LatLng(r.south, r.west))
            .build()
        val pixelRatio = app.resources.displayMetrics.density.coerceIn(1f, 2f)
        val def = OfflineTilePyramidRegionDefinition(styleUrl, bounds, r.minZoom.toDouble(), r.maxZoom.toDouble(), pixelRatio)
        m.createOfflineRegion(def, r.key.toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: MlRegion) {
                region = offlineRegion
                start(offlineRegion, r.key)
            }

            override fun onError(error: String) {
                _state.value = OfflineMapState(OfflineMapState.Phase.Failed, r.key, error = error)
            }
        })
    }

    private fun start(reg: MlRegion, key: String) {
        reg.setObserver(object : MlRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                // Stop at once when the unit leaves Wi-Fi (the phone hotspot may be on mobile data).
                if (!status.isComplete && !onWifi()) {
                    reg.setDownloadState(MlRegion.STATE_INACTIVE)
                    _state.value = OfflineMapState(OfflineMapState.Phase.NeedsWifi, key, percentOf(status), status.completedResourceSize)
                    return
                }
                _state.value = OfflineMapState(
                    phase = if (status.isComplete) OfflineMapState.Phase.Ready else OfflineMapState.Phase.Downloading,
                    regionKey = key,
                    percent = percentOf(status),
                    bytes = status.completedResourceSize,
                )
                if (status.isComplete) reg.setDownloadState(MlRegion.STATE_INACTIVE)
            }

            override fun onError(error: OfflineRegionError) {
                Log.w(TAG, "download: ${error.reason} ${error.message}")
                // Connection hiccups are retried by MapLibre itself; only report other errors.
                if (error.reason != OfflineRegionError.REASON_CONNECTION) {
                    _state.value = _state.value.copy(error = error.message)
                }
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                reg.setDownloadState(MlRegion.STATE_INACTIVE)
                _state.value = OfflineMapState(OfflineMapState.Phase.Failed, key, error = "tile limit $limit")
            }
        })
        reg.setDownloadState(MlRegion.STATE_ACTIVE)
        _state.value = _state.value.copy(phase = OfflineMapState.Phase.Downloading, regionKey = key)
    }

    fun pause() {
        region?.setDownloadState(MlRegion.STATE_INACTIVE)
        _state.value = _state.value.copy(phase = OfflineMapState.Phase.Paused)
    }

    fun delete() {
        val r = region ?: return
        r.setDownloadState(MlRegion.STATE_INACTIVE)
        r.delete(object : MlRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                region = null
                _state.value = OfflineMapState()
            }

            override fun onError(error: String) {
                _state.value = _state.value.copy(error = error)
            }
        })
    }

    private fun percentOf(s: OfflineRegionStatus): Int =
        if (s.requiredResourceCount <= 0L) 0 else (100L * s.completedResourceCount / s.requiredResourceCount).toInt().coerceIn(0, 100)

    private companion object {
        const val TAG = "OfflineMap"
    }
}
