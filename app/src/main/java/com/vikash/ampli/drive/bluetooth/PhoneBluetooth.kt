package com.vikash.ampli.drive.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.IntentCompat
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.MainActivity
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.system.AppEntry
import com.vikash.ampli.drive.system.BtPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** A paired Bluetooth device as the Phone Link sheet shows it. */
data class BtDevice(
    val address: String,
    val name: String,
    val connected: Boolean,
    val phone: Boolean,
    /** Connected for songs (A2DP sink) / for calls (hands-free), when the unit tells us; null = unknown. */
    val music: Boolean? = null,
    val calls: Boolean? = null,
    /** Phone battery in percent, when the Bluetooth stack reports it. */
    val battery: Int? = null,
)

/**
 * The driver's phone over Bluetooth, inside Car Hub: paired devices and which one is connected,
 * "Phone music" controls (media keys the unit forwards to the phone over AVRCP), auto-play when
 * the phone connects, and "Switch device".
 *
 * Only public broadcasts and guarded reflection are used, because a normal app cannot connect
 * Bluetooth profiles on a head unit. Connection state comes from the ACL broadcasts (a manifest
 * receiver, the drive-monitor service and the status chip all feed [onAcl]) plus the hidden
 * BluetoothDevice.isConnected() where the ROM allows it.
 */
class PhoneBluetooth(private val app: DriveApp) {
    private val adapter: BluetoothAdapter? = try {
        app.getSystemService(BluetoothManager::class.java)?.adapter
    } catch (_: Exception) {
        null
    }
    private val audio: AudioManager? = app.getSystemService(AudioManager::class.java)
    private val scope = MainScope()

    /** ACL state seen at runtime: address → connected. Wins over reflection. */
    private val acl = ConcurrentHashMap<String, Boolean>()

    private val _devices = MutableStateFlow<List<BtDevice>>(emptyList())
    val devices: StateFlow<List<BtDevice>> = _devices.asStateFlow()

    /** What auto-play did last, for Settings › Diagnostics (plain English on purpose). */
    private val _lastAutoPlay = MutableStateFlow<String?>(null)
    val lastAutoPlay: StateFlow<String?> = _lastAutoPlay.asStateFlow()

    val supported: Boolean get() = adapter != null

    private val _sheet = MutableStateFlow(false)

    /** Voice "connect my phone" asks Home to show the Phone Link sheet. */
    val sheetRequested: StateFlow<Boolean> = _sheet.asStateFlow()

    fun requestSheet() {
        _sheet.value = true
    }

    fun sheetShown() {
        _sheet.value = false
    }

    @Volatile private var a2dpSink: BluetoothProfile? = null
    @Volatile private var hfClient: BluetoothProfile? = null
    @Volatile private var proxiesAsked = false

    /** When the driver last paused phone music (elapsed clock), so auto-play doesn't fight them. */
    @Volatile private var lastUserPauseAt = 0L
    @Volatile private var lastAutoPlayAt = 0L

    /** First connected phone's name (null when none), for the music card and the sheet. */
    fun connectedPhone(list: List<BtDevice> = _devices.value): BtDevice? = list.firstOrNull { it.connected && it.phone }
        ?: list.firstOrNull { it.connected }

    fun hasPermission(): Boolean = BtPermissions.hasConnect(app)

    /** Re-reads the paired devices. Cheap; call when the sheet opens or a link changes. */
    @SuppressLint("MissingPermission")
    fun refresh() {
        val a = adapter
        if (a == null || !hasPermission()) {
            _devices.value = emptyList()
            return
        }
        ensureProxies()
        val bonded = try {
            if (a.isEnabled) a.bondedDevices.orEmpty() else emptySet()
        } catch (_: Exception) {
            emptySet()
        }
        val sink = connectedOn(a2dpSink)
        val hf = connectedOn(hfClient)
        _devices.value = bonded.map { d ->
            val addr = d.address ?: ""
            val music = sink?.contains(addr)
            val calls = hf?.contains(addr)
            val connected = acl[addr] ?: reflectConnected(d) ?: (music == true || calls == true)
            BtDevice(
                address = addr,
                name = nameOf(d) ?: addr,
                connected = connected || music == true || calls == true,
                phone = PhoneBtRules.isPhoneClass(majorOf(d)),
                music = music,
                calls = calls,
                battery = if (connected) batteryOf(d) else null,
            )
        }.sortedWith(compareByDescending<BtDevice> { it.connected }.thenByDescending { it.phone }.thenBy { it.name.lowercase() })
    }

    /**
     * An ACL link came up or went down. Several receivers may report the same change; only a
     * real change counts. A phone connecting starts [autoPlay].
     */
    fun onAcl(device: BluetoothDevice?, connected: Boolean) {
        val addr = try {
            device?.address
        } catch (_: Exception) {
            null
        } ?: return
        val before = acl.put(addr, connected)
        if (before == connected) return
        scope.launch {
            refresh()
            if (connected) autoPlay(device!!)
        }
    }

    // ------------------------------------------------------------------------- phone music

    /** Play/pause, next, previous: a media key that the unit's Bluetooth stack sends to the phone. */
    fun key(code: Int) {
        val am = audio ?: return
        if ((code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && am.isMusicActive) || code == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            lastUserPauseAt = SystemClock.elapsedRealtime()
        }
        sendKey(code)
    }

    private fun sendKey(code: Int) {
        val am = audio ?: return
        try {
            val now = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
        } catch (_: Exception) {
        }
    }

    /** "Play phone music" in the Phone Link sheet: play, and the unit's BT music screen if silent. */
    fun playPhoneMusic(context: Context) {
        scope.launch {
            sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            delay(PhoneBtRules.SOUND_WAIT_MS)
            if (audio?.isMusicActive == true) return@launch
            openVendorMusic(context, bringBack = MainActivity.inFront)
        }
    }

    private suspend fun autoPlay(device: BluetoothDevice) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAutoPlayAt in 0 until 30_000L) return
        fun decide() = PhoneBtRules.autoPlay(
            enabled = app.prefs.phoneAutoPlay.value,
            phoneClass = PhoneBtRules.isPhoneClass(majorOf(device)),
            inCall = app.speaker.inCall(),
            musicActive = audio?.isMusicActive == true,
            lastUserPauseAtMs = lastUserPauseAt,
            nowMs = SystemClock.elapsedRealtime(),
        )
        val first = decide()
        if (first != PhoneBtRules.AutoPlay.Play) {
            _lastAutoPlay.value = "skipped: ${first.name}"
            return
        }
        lastAutoPlayAt = now
        // A2DP comes up a few seconds after the ACL link.
        delay(PhoneBtRules.A2DP_WAIT_MS)
        val second = decide()
        if (second != PhoneBtRules.AutoPlay.Play || acl[device.address] == false) {
            _lastAutoPlay.value = "skipped after wait: ${second.name}"
            return
        }
        sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        delay(PhoneBtRules.SOUND_WAIT_MS)
        if (audio?.isMusicActive == true) {
            _lastAutoPlay.value = "played (media key)"
            return
        }
        // Silent: many units route phone audio only while their own BT music screen is open.
        // Only when Car Hub is in front, so Google Maps is never covered while driving.
        if (!MainActivity.inFront) {
            _lastAutoPlay.value = "no sound; Car Hub not in front, BT app not opened"
            return
        }
        val opened = openVendorMusic(app, bringBack = true)
        _lastAutoPlay.value = if (opened) "no sound: opened the unit's Bluetooth music, pressed play" else "no sound; no Bluetooth app found"
    }

    /**
     * Opens the unit's own Bluetooth app on its music screen, presses play again, then brings Car
     * Hub back after ~2 s when [bringBack]. False when the unit has no such app.
     */
    private suspend fun openVendorMusic(context: Context, bringBack: Boolean): Boolean {
        val vendor = withContext(Dispatchers.IO) { VendorBt.find(app) } ?: return false
        if (!VendorBt.openMusic(context, vendor)) return false
        delay(1_500L)
        sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        if (bringBack) {
            delay(2_000L)
            try {
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                )
            } catch (_: Exception) {
            }
        }
        return true
    }

    // ------------------------------------------------------------------------ switch device

    enum class SwitchStart { Asked, NotAllowed }

    /**
     * Asks the unit to connect [address] for songs and calls (hidden connect() on the A2DP-sink and
     * hands-free proxies). Most ROMs refuse a normal app ([SwitchStart.NotAllowed]); the sheet then
     * opens the unit's Bluetooth app. Even [SwitchStart.Asked] is not "connected": the sheet waits
     * for the device's state to change.
     */
    @SuppressLint("MissingPermission")
    fun switchTo(address: String): SwitchStart {
        val a = adapter ?: return SwitchStart.NotAllowed
        if (!hasPermission()) return SwitchStart.NotAllowed
        ensureProxies()
        val device = try {
            a.getRemoteDevice(address)
        } catch (_: Exception) {
            return SwitchStart.NotAllowed
        }
        var asked = false
        for (proxy in listOfNotNull(a2dpSink, hfClient)) {
            try {
                val m = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                if (m.invoke(proxy, device) == true) asked = true
            } catch (_: Throwable) {
                // SecurityException (needs a system permission) or no such method on this ROM.
            }
        }
        return if (asked) SwitchStart.Asked else SwitchStart.NotAllowed
    }

    /** "Pick <name> here": the unit's Bluetooth app, else Android's Bluetooth settings. */
    fun openPairingScreen(context: Context, deviceName: String?) {
        val vendor = VendorBt.find(app)
        val opened = vendor != null && com.vikash.ampli.drive.system.Launchers.openComponent(context, vendor.packageName, vendor.activityName)
        if (!opened) openBluetoothSettings(context)
        if (deviceName != null) {
            Toast.makeText(context, context.getString(R.string.plink_pick_here, deviceName), Toast.LENGTH_LONG).show()
        }
    }

    fun openBluetoothSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    // ----------------------------------------------------------------------------- helpers

    /** Hidden profile ids: BluetoothProfile.A2DP_SINK and HEADSET_CLIENT (Android 5+). */
    private fun ensureProxies() {
        val a = adapter ?: return
        if (proxiesAsked || !hasPermission()) return
        proxiesAsked = true
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                when (profile) {
                    PROFILE_A2DP_SINK -> a2dpSink = proxy
                    PROFILE_HEADSET_CLIENT -> hfClient = proxy
                }
                scope.launch { refresh() }
            }

            override fun onServiceDisconnected(profile: Int) {
                when (profile) {
                    PROFILE_A2DP_SINK -> a2dpSink = null
                    PROFILE_HEADSET_CLIENT -> hfClient = null
                }
            }
        }
        for (p in listOf(PROFILE_A2DP_SINK, PROFILE_HEADSET_CLIENT)) {
            try {
                a.getProfileProxy(app, listener, p)
            } catch (_: Throwable) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectedOn(proxy: BluetoothProfile?): Set<String>? = try {
        proxy?.connectedDevices?.mapNotNull { it.address }?.toSet()
    } catch (_: Throwable) {
        null
    }

    private fun reflectConnected(d: BluetoothDevice): Boolean? = try {
        BluetoothDevice::class.java.getMethod("isConnected").invoke(d) as? Boolean
    } catch (_: Throwable) {
        null
    }

    private fun batteryOf(d: BluetoothDevice): Int? = try {
        (BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(d) as? Int)?.takeIf { it in 0..100 }
    } catch (_: Throwable) {
        null
    }

    @SuppressLint("MissingPermission")
    private fun majorOf(d: BluetoothDevice): Int? = try {
        d.bluetoothClass?.majorDeviceClass
    } catch (_: Throwable) {
        null
    }

    @SuppressLint("MissingPermission")
    private fun nameOf(d: BluetoothDevice): String? = try {
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) d.alias else null) ?: d.name
    } catch (_: Throwable) {
        null
    }

    companion object {
        private const val PROFILE_A2DP_SINK = 11
        private const val PROFILE_HEADSET_CLIENT = 16

        /** Reads the device out of an ACL broadcast. */
        fun deviceFrom(intent: Intent): BluetoothDevice? = try {
            IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } catch (_: Exception) {
            null
        }
    }
}

/** The head unit's own Bluetooth app (phone + BT music), found by label and package. */
object VendorBt {

    @Suppress("DEPRECATION")
    fun find(context: Context): AppEntry? = try {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .asSequence()
            .map {
                AppEntry(it.activityInfo.packageName, it.activityInfo.name, it.loadLabel(pm)?.toString() ?: it.activityInfo.packageName)
            }
            .map { it to PhoneBtRules.vendorBtScore(it.label, it.packageName, context.packageName) }
            .filter { it.second >= PhoneBtRules.VENDOR_GOOD_ENOUGH }
            .sortedByDescending { it.second }
            .firstOrNull()?.first
    } catch (_: Exception) {
        null
    }

    /** Exported activities of [pkg] (names), for its music screen. */
    @Suppress("DEPRECATION")
    fun exportedActivities(context: Context, pkg: String): List<String> = try {
        context.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities
            ?.filter { it.exported && it.enabled }
            ?.map { it.name }
            .orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    /** Its music screen when one is exported, else the app itself. */
    fun openMusic(context: Context, app: AppEntry): Boolean {
        val music = PhoneBtRules.musicActivity(exportedActivities(context, app.packageName))
        if (music != null) {
            try {
                context.startActivity(
                    Intent().setComponent(ComponentName(app.packageName, music)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return true
            } catch (_: Exception) {
            }
        }
        return try {
            context.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName(app.packageName, app.activityName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * ACL connected / disconnected. These two broadcasts are exempt from Android 8's implicit
 * broadcast limits, so this works even when Car Hub is not running (auto-play on connect).
 */
class BtAclReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? DriveApp ?: return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> app.phoneBt.onAcl(PhoneBluetooth.deviceFrom(intent), true)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> app.phoneBt.onAcl(PhoneBluetooth.deviceFrom(intent), false)
        }
    }
}
