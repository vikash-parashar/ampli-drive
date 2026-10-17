package com.vikash.ampli.drive.system

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ampli Shell: Car Hub as the head unit's own screen, without root, on Android 8+.
 * Every intent here is started inside try/catch, so a head unit missing a settings page only
 * falls through to the next option.
 */
object Shell {

    // ------------------------------------------------------------------ default home

    /** Android 10+ asks with the system's own "Default home app" dialog (RoleManager). */
    fun homeRoleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val rm = context.getSystemService(RoleManager::class.java) ?: return null
            if (!rm.isRoleAvailable(RoleManager.ROLE_HOME) || rm.isRoleHeld(RoleManager.ROLE_HOME)) null
            else rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ boot splash

    /** How long the Ampli startup animation plays before Home and the welcome. */
    const val SPLASH_MS = 2_600L

    private val _splash = MutableStateFlow(0L)

    /** Non-zero while the startup animation should show (the time it was asked for). */
    val splash: StateFlow<Long> = _splash.asStateFlow()

    /** Boot or ignition noticed with the launcher in front. Returns true when the splash plays. */
    fun requestSplash(enabled: Boolean): Boolean {
        if (!enabled) return false
        _splash.value = System.currentTimeMillis()
        return true
    }

    fun splashDone() {
        _splash.value = 0L
    }

    // ------------------------------------------------------------------ quick controls

    enum class Control { Bluetooth, Wifi, Hotspot, Equalizer, Brightness, Volume, Display, Sound }

    fun open(context: Context, control: Control): Boolean = when (control) {
        Control.Bluetooth -> start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        Control.Wifi -> (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && start(context, Intent(PANEL_WIFI))) ||
            start(context, Intent(Settings.ACTION_WIFI_SETTINGS))
        Control.Hotspot -> start(context, Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings"))) ||
            start(context, Intent(Settings.ACTION_WIRELESS_SETTINGS)) ||
            start(context, Intent(Settings.ACTION_SETTINGS))
        Control.Equalizer -> openEqualizer(context)
        Control.Brightness -> start(context, Intent(Settings.ACTION_DISPLAY_SETTINGS))
        Control.Volume -> (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && start(context, Intent(PANEL_VOLUME))) || showVolume(context)
        Control.Display -> start(context, Intent(Settings.ACTION_DISPLAY_SETTINGS)) || start(context, Intent(Settings.ACTION_SETTINGS))
        Control.Sound -> start(context, Intent(Settings.ACTION_SOUND_SETTINGS)) || start(context, Intent(Settings.ACTION_SETTINGS))
    }

    // Settings.Panel.* (API 29), as literals so older head units never touch the class.
    private const val PANEL_WIFI = "android.settings.panel.action.WIFI"
    private const val PANEL_VOLUME = "android.settings.panel.action.VOLUME"

    /**
     * Head-unit vendors' own EQ / audio apps, best first (FYT, TopWay/TS, Microntek/MTC, Joying,
     * PX5/PX6 and generic Allwinner units). Installed apps whose name says EQ are tried after.
     */
    val EQ_PACKAGES = listOf(
        "com.syu.eq",
        "com.syu.audio",
        "com.fyt.eq",
        "com.ts.eq",
        "com.ts.dsp",
        "com.tw.eq",
        "com.microntek.eq",
        "com.microntek.audio",
        "com.mtc.eq",
        "com.android.eq",
        "com.yf.eq",
        "com.hzbhd.eq",
        "com.autochips.eq",
    )
    val EQ_LABELS = listOf("equalizer", "equaliser", "eq", "dsp", "sound effect", "audio effect", "音效", "均衡器", "इक्वलाइज़र")

    private fun openEqualizer(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in EQ_PACKAGES) {
            val launch = try { pm.getLaunchIntentForPackage(pkg) } catch (_: Exception) { null } ?: continue
            if (start(context, launch)) return true
        }
        val byLabel = try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).firstOrNull { ri ->
                val l = ri.loadLabel(pm)?.toString()?.lowercase().orEmpty()
                EQ_LABELS.any { label -> l == label || l.contains("$label ") || l.contains(" $label") || (label.length > 3 && l.contains(label)) }
            }
        } catch (_: Exception) {
            null
        }
        if (byLabel != null && start(context, Intent(Intent.ACTION_MAIN).setComponent(ComponentName(byLabel.activityInfo.packageName, byLabel.activityInfo.name)))) return true
        val panel = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        return start(context, panel) || start(context, Intent(Settings.ACTION_SOUND_SETTINGS))
    }

    /** Below Android 10 (no volume panel): the system volume slider. */
    private fun showVolume(context: Context): Boolean = try {
        val am = context.getSystemService(AudioManager::class.java)
        am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        am != null
    } catch (_: Exception) {
        start(context, Intent(Settings.ACTION_SOUND_SETTINGS))
    }

    /** Live tile states that need no special permission. */
    data class Status(
        val wifiOn: Boolean?,
        val wifiConnected: Boolean,
        /** Only when Android tells us (location allowed); null otherwise. */
        val ssid: String?,
        val brightnessPct: Int?,
        val volumePct: Int?,
    )

    @Suppress("DEPRECATION")
    fun status(context: Context): Status {
        val wifi = try { context.applicationContext.getSystemService(WifiManager::class.java) } catch (_: Exception) { null }
        val wifiOn = try { wifi?.isWifiEnabled } catch (_: Exception) { null }
        val connected = try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } catch (_: Exception) {
            false
        }
        val ssid = if (!connected) null else try {
            wifi?.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" && it != "0x" }
        } catch (_: Exception) {
            null
        }
        val brightness = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100 / 255
        } catch (_: Exception) {
            null
        }
        val volume = try {
            val am = context.getSystemService(AudioManager::class.java)
            am?.let { it.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / it.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
        } catch (_: Exception) {
            null
        }
        return Status(wifiOn, connected, ssid, brightness?.coerceIn(0, 100), volume)
    }

    // ------------------------------------------------------------------ Ampli app drawer

    /**
     * The driver's Ampli app list before they edit it: the map, the music app, the phone-link app
     * and the other Ampli apps that are installed.
     */
    fun defaultDrawer(context: Context, installed: List<AppEntry>, musicPackage: String?): Set<String> {
        val have = installed.map { it.packageName }.toSet()
        val picks = linkedSetOf<String>()
        listOf(Launchers.GOOGLE_MAPS, "com.waze", "com.mapmyindia.app.beta", "com.mmi.maps").firstOrNull { it in have }?.let { picks += it }
            ?: AppResolver.bestGuess(context, AppRole.Nav)?.packageName?.let { picks += it }
        (musicPackage?.takeIf { it in have } ?: AppResolver.bestGuess(context, AppRole.Music)?.packageName)?.let { picks += it }
        Launchers.PHONE_LINK_PACKAGES.firstOrNull { it in have }?.let { picks += it }
            ?: AppResolver.bestGuess(context, AppRole.Mirror)?.packageName?.let { picks += it }
        picks += have.filter { it.startsWith("com.vikash.ampli") }
        return picks
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }
}
