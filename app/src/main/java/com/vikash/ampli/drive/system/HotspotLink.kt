package com.vikash.ampli.drive.system

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.vikash.ampli.drive.DriveApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings › Phone hotspot: the head unit has no SIM, so its internet is the driver's phone
 * hotspot. The driver picks that hotspot (a Wi-Fi network already saved on the unit) once, and
 * optionally the phone's Bluetooth name. When the phone connects over Bluetooth, or the hotspot
 * shows up in a Wi-Fi scan, Car Hub asks Wi-Fi to join it ([HotspotLogic] decides when).
 *
 * Works on Android 8 / 9 head units (WifiManager.enableNetwork). From Android 10 apps may no
 * longer list or join saved networks: there the card opens Wi-Fi settings instead.
 * Cheap: no polling, only the system's own scan broadcasts and the Bluetooth hook.
 */
class HotspotLink(private val app: DriveApp) {
    private val wifi: WifiManager? = try {
        app.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    } catch (_: Exception) {
        null
    }
    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private var lastTryAt = 0L

    /** Wi-Fi joining by an app works here (Android 8 / 9). */
    val supported: Boolean get() = wifi != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    private val _status = MutableStateFlow<String?>(null)

    /** The last thing that happened, for the settings card ("Joining Vikash's Phone…"). */
    val status: StateFlow<String?> = _status.asStateFlow()

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val ssid = app.extras.hotspotSsid.value ?: return
            val seen = scanSees(ssid)
            if (seen) tryConnect(seen = true, reason = "scan")
        }
    }

    fun start() {
        if (started || wifi == null) return
        started = true
        try {
            ContextCompat.registerReceiver(
                app, scanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_EXPORTED,
            )
        } catch (_: Exception) {
        }
        // At start (boot / ACC on) the phone may already be near: one try a little later.
        main.postDelayed({ tryConnect(seen = app.extras.hotspotSsid.value?.let { scanSees(it) } == true, reason = "start") }, 20_000L)
    }

    /** PhoneBluetooth: a Bluetooth device connected. */
    fun onBluetoothConnected(device: BluetoothDevice?) {
        val name = try {
            device?.name
        } catch (_: SecurityException) {
            null
        }
        val chosen = app.extras.hotspotPhone.value
        // No phone chosen: any phone connecting is a hint the driver's hotspot may be up.
        if (chosen != null && !HotspotLogic.isPairedPhone(chosen, name)) return
        // The phone needs a moment to switch its hotspot on.
        main.postDelayed({ tryConnect(seen = true, reason = "bluetooth") }, 5_000L)
        try {
            @Suppress("DEPRECATION")
            wifi?.startScan()
        } catch (_: Exception) {
        }
    }

    /** "Hotspot jodo" or the Connect button: join now if possible. Returns false when it can't. */
    fun connectNow(): Boolean {
        lastTryAt = 0L
        return tryConnect(seen = true, reason = "asked", force = true)
    }

    /** Saved Wi-Fi networks on the unit (Android 8 / 9), for the picker. */
    @Suppress("DEPRECATION")
    fun savedNetworks(): List<String> = try {
        wifi?.configuredNetworks.orEmpty().mapNotNull { HotspotLogic.unquote(it.SSID) }.distinct().sorted()
    } catch (_: SecurityException) {
        emptyList()
    }

    /** Paired Bluetooth devices' names, for the picker. */
    fun pairedPhones(): List<String> = try {
        if (!BtPermissions.hasConnect(app)) {
            emptyList()
        } else {
            app.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
                .mapNotNull { d -> d.name?.takeIf { it.isNotBlank() } }.distinct().sorted()
        }
    } catch (_: SecurityException) {
        emptyList()
    }

    /** The Wi-Fi network the unit is on now, or null. */
    @Suppress("DEPRECATION")
    fun currentSsid(): String? = try {
        HotspotLogic.unquote(wifi?.connectionInfo?.ssid)
    } catch (_: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun scanSees(ssid: String): Boolean = try {
        wifi?.scanResults.orEmpty().any { HotspotLogic.same(it.SSID, ssid) }
    } catch (_: SecurityException) {
        false
    }

    @Suppress("DEPRECATION")
    private fun tryConnect(seen: Boolean, reason: String, force: Boolean = false): Boolean {
        val w = wifi ?: return false
        val ssid = app.extras.hotspotSsid.value ?: return false
        if (!force && !app.extras.hotspotAuto.value) return false
        val current = currentSsid()
        val now = System.currentTimeMillis()
        val onOther = current != null && !HotspotLogic.same(current, ssid)
        if (!HotspotLogic.shouldConnect(ssid, current, onOtherWifi = onOther && !force, seen = seen, lastTryAt = lastTryAt, now = now)) {
            if (HotspotLogic.same(current, ssid)) _status.value = ssid
            return HotspotLogic.same(current, ssid)
        }
        lastTryAt = now
        if (!supported) return false
        return try {
            if (!w.isWifiEnabled) w.setWifiEnabled(true)
            val configs = w.configuredNetworks.orEmpty()
            val target = configs.firstOrNull { HotspotLogic.same(it.SSID, ssid) } ?: return false
            _status.value = ssid
            val ok = w.enableNetwork(target.networkId, true)
            w.reconnect()
            // enableNetwork(…, true) leaves the other saved networks disabled on some units:
            // switch them back on after the join, so the home Wi-Fi still works later.
            main.postDelayed({
                try {
                    for (c in w.configuredNetworks.orEmpty()) if (c.networkId != target.networkId) w.enableNetwork(c.networkId, false)
                } catch (_: Exception) {
                }
            }, 20_000L)
            android.util.Log.i(TAG, "join hotspot ($reason): $ok")
            ok
        } catch (e: Exception) {
            android.util.Log.w(TAG, "join hotspot failed", e)
            false
        }
    }

    private companion object {
        const val TAG = "HotspotLink"
    }
}
