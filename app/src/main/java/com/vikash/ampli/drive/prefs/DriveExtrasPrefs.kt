package com.vikash.ampli.drive.prefs

import android.content.Context
import com.vikash.ampli.drive.nav.SavedRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Settings and small state for the drive extras: resume on start, the phone hotspot, the offline
 * map region, the challan-bachat counter, the last music app and the unfinished route. One small
 * SharedPreferences file, read once.
 */
class DriveExtrasPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ------------------------------------------------------------- Resume on start (default on)

    private val _resumeMusic = MutableStateFlow(sp.getBoolean(KEY_RESUME_MUSIC, true))

    /** At boot / ACC on: play the music that was playing when the car was switched off. */
    val resumeMusic: StateFlow<Boolean> = _resumeMusic.asStateFlow()

    fun setResumeMusic(v: Boolean) = put(KEY_RESUME_MUSIC, v).also { _resumeMusic.value = v }

    private val _resumeRoute = MutableStateFlow(sp.getBoolean(KEY_RESUME_ROUTE, true))

    /** At boot / ACC on: continue an in-app route that was not finished. */
    val resumeRoute: StateFlow<Boolean> = _resumeRoute.asStateFlow()

    fun setResumeRoute(v: Boolean) = put(KEY_RESUME_ROUTE, v).also { _resumeRoute.value = v }

    /** The in-app route in progress, saved so a power cut does not lose it. */
    var savedRoute: SavedRoute?
        get() = try {
            sp.getString(KEY_ROUTE, null)?.let { s ->
                val o = JSONObject(s)
                SavedRoute(
                    name = o.getString("name"),
                    area = o.optString("area", "").ifEmpty { null },
                    lat = o.getDouble("lat"),
                    lon = o.getDouble("lon"),
                    savedAt = o.getLong("at"),
                )
            }
        } catch (_: Exception) {
            null
        }
        set(v) {
            val s = v?.let {
                JSONObject().put("name", it.name).put("area", it.area ?: "").put("lat", it.lat).put("lon", it.lon).put("at", it.savedAt).toString()
            }
            sp.edit().putString(KEY_ROUTE, s).apply()
        }

    // ------------------------------------------------------------------------- Phone hotspot

    private val _hotspotSsid = MutableStateFlow(sp.getString(KEY_HOTSPOT_SSID, null))

    /** The phone's hotspot (a saved Wi-Fi network), or null. */
    val hotspotSsid: StateFlow<String?> = _hotspotSsid.asStateFlow()

    fun setHotspotSsid(v: String?) {
        sp.edit().putString(KEY_HOTSPOT_SSID, v).apply()
        _hotspotSsid.value = v
    }

    private val _hotspotPhone = MutableStateFlow(sp.getString(KEY_HOTSPOT_PHONE, null))

    /** The Bluetooth phone whose arrival means "join the hotspot now", or null. */
    val hotspotPhone: StateFlow<String?> = _hotspotPhone.asStateFlow()

    fun setHotspotPhone(v: String?) {
        sp.edit().putString(KEY_HOTSPOT_PHONE, v).apply()
        _hotspotPhone.value = v
    }

    private val _hotspotAuto = MutableStateFlow(sp.getBoolean(KEY_HOTSPOT_AUTO, true))

    /** Join the hotspot by itself (once one is chosen). */
    val hotspotAuto: StateFlow<Boolean> = _hotspotAuto.asStateFlow()

    fun setHotspotAuto(v: Boolean) = put(KEY_HOTSPOT_AUTO, v).also { _hotspotAuto.value = v }

    // --------------------------------------------------------------------------- Offline map

    private val _offlineRegion = MutableStateFlow(sp.getString(KEY_OFFLINE_REGION, null))

    /** Settings › Offline map: the chosen region key (OfflineRegions), null = the default. */
    val offlineRegion: StateFlow<String?> = _offlineRegion.asStateFlow()

    fun setOfflineRegion(v: String) {
        sp.edit().putString(KEY_OFFLINE_REGION, v).apply()
        _offlineRegion.value = v
    }

    // ------------------------------------------------------------------- Challan-bachat counter

    private val _challanCount = MutableStateFlow(sp.getInt(KEY_CHALLAN, 0))

    /** Over-speed warnings the driver obeyed (ChallanSaver), since [challanSince]. */
    val challanCount: StateFlow<Int> = _challanCount.asStateFlow()

    val challanSince: Long get() = sp.getLong(KEY_CHALLAN_SINCE, 0L)

    fun addChallanSaved() {
        val n = _challanCount.value + 1
        val e = sp.edit().putInt(KEY_CHALLAN, n)
        if (sp.getLong(KEY_CHALLAN_SINCE, 0L) == 0L) e.putLong(KEY_CHALLAN_SINCE, System.currentTimeMillis())
        e.apply()
        _challanCount.value = n
    }

    fun resetChallan() {
        sp.edit().putInt(KEY_CHALLAN, 0).putLong(KEY_CHALLAN_SINCE, System.currentTimeMillis()).apply()
        _challanCount.value = 0
    }

    private fun put(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()

    private companion object {
        const val FILE = "drive_extras"
        const val KEY_RESUME_MUSIC = "resume_music"
        const val KEY_RESUME_ROUTE = "resume_route"
        const val KEY_LAST_MUSIC = "last_music_pkg"
        const val KEY_ROUTE = "saved_route"
        const val KEY_HOTSPOT_SSID = "hotspot_ssid"
        const val KEY_HOTSPOT_PHONE = "hotspot_phone"
        const val KEY_HOTSPOT_AUTO = "hotspot_auto"
        const val KEY_OFFLINE_REGION = "offline_region"
        const val KEY_CHALLAN = "challan_saved"
        const val KEY_CHALLAN_SINCE = "challan_since"
    }
}
