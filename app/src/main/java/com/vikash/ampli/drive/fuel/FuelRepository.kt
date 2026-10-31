package com.vikash.ampli.drive.fuel

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What the fuel screen shows under the list. [count] is 0 when the snapshot is missing. */
data class FuelStats(val ready: Boolean, val count: Int, val dataDate: String?)

/**
 * The offline fuel, CNG and EV-charger snapshot: assets/fuel.bin, built by scripts/fetch_fuel.py
 * from OpenStreetMap (amenity=fuel and amenity=charging_station in India) during the CI build.
 * That data is © OpenStreetMap contributors under the ODbL 1.0 (attribution in Settings and on
 * the fuel screen; the derived file stays under the ODbL).
 *
 * Loaded once, off the main thread, on first use; lookups run on a background thread and return
 * at most a screenful of stations, so nothing big ever reaches Compose state.
 */
class FuelRepository(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var loading: Deferred<FuelSnapshot>? = null

    /** The filter "Hey Ampli, nearest CNG pump" asked for; the fuel screen opens on it. */
    val requestedFilter = MutableStateFlow(FuelFilter.All)

    private val _stats = MutableStateFlow(FuelStats(ready = false, count = 0, dataDate = null))
    val stats: StateFlow<FuelStats> = _stats.asStateFlow()

    private fun snapshot(): Deferred<FuelSnapshot> = synchronized(lock) {
        loading ?: scope.async { load() }.also { loading = it }
    }

    /** Starts loading in the background; safe to call often. */
    fun ensureLoaded() {
        snapshot()
    }

    /** Up to [limit] stations for [filter], nearest first (see [FuelFinder.nearby]). */
    suspend fun nearby(lat: Double, lon: Double, filter: FuelFilter, limit: Int = 20): List<FuelHit> {
        val s = snapshot().await()
        return withContext(Dispatchers.Default) { FuelFinder.nearby(s, lat, lon, filter, limit) }
    }

    /**
     * Runs [block] over the snapshot on a background thread (loading it first if needed): Car Hub's
     * navigation looks up pumps near each turn of a new route this way.
     */
    suspend fun <T> withSnapshot(block: (FuelSnapshot) -> T): T {
        val s = snapshot().await()
        return withContext(Dispatchers.Default) { block(s) }
    }

    private fun load(): FuelSnapshot {
        val snap = try {
            appContext.assets.open(ASSET).use { FuelSnapshot.read(it) }
        } catch (_: Exception) {
            FuelSnapshot.EMPTY
        }
        val date = try {
            val meta = appContext.assets.open(META_ASSET).bufferedReader().use { it.readText() }
            JSONObject(meta).optString("generated", "").ifEmpty { null }
        } catch (_: Exception) {
            null
        }
        _stats.value = FuelStats(ready = true, count = snap.size, dataDate = date)
        return snap
    }

    private companion object {
        const val ASSET = "fuel.bin"
        const val META_ASSET = "fuel_meta.json"
    }
}
