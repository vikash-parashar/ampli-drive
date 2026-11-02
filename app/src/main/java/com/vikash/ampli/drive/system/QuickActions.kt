package com.vikash.ampli.drive.system

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import com.vikash.ampli.drive.DriveApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The quick panel's tiles and sliders and the rail's volume keys. Every call is wrapped so a
 * head unit missing an API only falls back to its settings page.
 */
object QuickActions {

    // ---------------------------------------------------------------- volume (rail + slider)

    /** Music volume now; null when Android gives none. */
    fun volume(context: Context): RailVolume.Level? = try {
        val am = context.getSystemService(AudioManager::class.java)
        am?.let {
            RailVolume.Level(
                it.getStreamVolume(AudioManager.STREAM_MUSIC),
                it.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                isMuted(it) || mutedByZero,
            )
        }
    } catch (_: Exception) {
        null
    }

    /** Level before a mute on units whose mute does not stick (set to 0 instead). */
    @Volatile private var levelBeforeMute: Int? = null
    @Volatile private var mutedByZero = false

    private fun isMuted(am: AudioManager): Boolean = try {
        am.isStreamMute(AudioManager.STREAM_MUSIC)
    } catch (_: Exception) {
        false
    }

    /**
     * One step up or down, the way the unit's own hardware keys do it:
     * adjustSuggestedStreamVolume with the default stream and the system volume panel. Many head
     * units (TS7 and other FYT-style units) route the car's amplifier volume through that path
     * and ignore setStreamVolume(STREAM_MUSIC). When the reading did not move and the unit is
     * rooted, `input keyevent 24 / 25` is sent instead (off the main thread), and remembered so
     * later taps go straight there.
     */
    fun stepVolume(context: Context, delta: Int): RailVolume.Level? {
        val am = context.getSystemService(AudioManager::class.java) ?: return null
        if (delta == 0) return volume(context)
        val up = delta > 0
        if (preferRootKeys) {
            rootKey(if (up) KEY_VOLUME_UP else KEY_VOLUME_DOWN)
            return volume(context)?.let { RailVolume.step(it, delta) }
        }
        return try {
            val before = volume(context)
            am.adjustSuggestedStreamVolume(
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                AudioManager.FLAG_SHOW_UI,
            )
            val after = volume(context)
            val atEdge = before != null && ((up && before.level >= before.max) || (!up && before.level <= 0))
            if (before != null && after != null && after.level == before.level && after.muted == before.muted && !atEdge && PowerControl.rooted()) {
                preferRootKeys = true
                rootKey(if (up) KEY_VOLUME_UP else KEY_VOLUME_DOWN)
                return RailVolume.step(before, delta)
            }
            after
        } catch (_: SecurityException) {
            // Do Not Disturb can refuse volume changes.
            null
        } catch (_: Exception) {
            null
        }
    }

    /** The adjust path did nothing on this unit once and root works: use the key events. */
    @Volatile private var preferRootKeys = false

    private const val KEY_VOLUME_UP = 24
    private const val KEY_VOLUME_DOWN = 25
    private const val KEY_VOLUME_MUTE = 164

    /** One `su -c input keyevent N` at a time, off the main thread; held-key repeats queue up to two. */
    private val rootKeys = java.util.concurrent.ThreadPoolExecutor(
        1, 1, 30L, java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.ArrayBlockingQueue(2),
        java.util.concurrent.ThreadPoolExecutor.DiscardPolicy(),
    ).apply { allowCoreThreadTimeOut(true) }

    private fun rootKey(code: Int) {
        rootKeys.execute {
            try {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "input keyevent $code"))
                if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) p.destroy()
            } catch (_: Exception) {
            }
        }
    }

    fun setVolume(context: Context, level: Int): RailVolume.Level? {
        val am = context.getSystemService(AudioManager::class.java) ?: return null
        return try {
            val cur = volume(context)
            if (cur?.muted == true && level > 0) unmute(context)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, level.coerceIn(0, cur?.max ?: level), 0)
            volume(context)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Mute toggle, as the hardware mute key does it (ADJUST_TOGGLE_MUTE on the default stream).
     * When nothing changed: `input keyevent 164` on a rooted unit, else the old mute / un-mute.
     * Returns the level afterwards.
     */
    fun toggleMute(context: Context): RailVolume.Level? {
        val cur = volume(context) ?: return null
        val am = context.getSystemService(AudioManager::class.java) ?: return null
        if (!mutedByZero) {
            try {
                am.adjustSuggestedStreamVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
            } catch (_: Exception) {
            }
            val after = volume(context)
            if (after != null && after.muted != cur.muted) return after
            if (PowerControl.rooted()) {
                rootKey(KEY_VOLUME_MUTE)
                return cur.copy(muted = !cur.muted)
            }
        }
        if (cur.muted) unmute(context) else mute(context)
        return volume(context)
    }

    private fun mute(context: Context) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        try {
            levelBeforeMute = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            if (!isMuted(am)) {
                // Some ROMs ignore ADJUST_MUTE: go to 0 and remember the level.
                am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                mutedByZero = true
            }
        } catch (_: Exception) {
        }
    }

    private fun unmute(context: Context) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val now = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val target = RailVolume.unmuteLevel(now, max, levelBeforeMute)
            if (target != now) am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } catch (_: Exception) {
        }
        mutedByZero = false
        levelBeforeMute = null
    }

    // ---------------------------------------------------------------- Wi-Fi and Bluetooth

    /**
     * Wi-Fi on/off. Android 8 and 9 let an app switch it (CHANGE_WIFI_STATE); from Android 10 the
     * Wi-Fi panel opens instead. Returns the new state, or null when a settings page opened.
     */
    @Suppress("DEPRECATION")
    fun toggleWifi(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                val wm = context.applicationContext.getSystemService(WifiManager::class.java)
                if (wm != null) {
                    val next = !wm.isWifiEnabled
                    if (wm.setWifiEnabled(next)) return next
                }
            } catch (_: Exception) {
            }
        }
        Shell.open(context, Shell.Control.Wifi)
        return null
    }

    fun openWifiSettings(context: Context) {
        start(context, Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    /**
     * Bluetooth on/off. Up to Android 12 the adapter can be switched (BLUETOOTH_ADMIN; Android 12
     * also needs "Nearby devices"); from Android 13 Android only lets the driver turn it on from
     * its own dialog, and off from Settings. Returns the new state, or null when a page opened.
     */
    @Suppress("DEPRECATION", "MissingPermission")
    fun toggleBluetooth(context: Context): Boolean? {
        val adapter = try {
            BluetoothAdapter.getDefaultAdapter()
        } catch (_: Exception) {
            null
        }
        if (adapter == null) {
            Shell.open(context, Shell.Control.Bluetooth)
            return null
        }
        val on = try {
            adapter.isEnabled
        } catch (_: Exception) {
            null
        }
        val canSwitch = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || BtPermissions.hasConnect(context))
        if (canSwitch && on != null) {
            val ok = try {
                if (on) adapter.disable() else adapter.enable()
            } catch (_: SecurityException) {
                false
            } catch (_: Exception) {
                false
            }
            if (ok) return !on
        }
        if (on == false && start(context, Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))) return null
        Shell.open(context, Shell.Control.Bluetooth)
        return null
    }

    fun openBluetoothSettings(context: Context) {
        start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    // ---------------------------------------------------------------- brightness

    /** Brightness in percent: the system setting, or this window's own level when set. */
    fun brightnessPercent(context: Context, window: Int?): Int? = window ?: try {
        BrightnessMath.percent(Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
    } catch (_: Exception) {
        null
    }

    /**
     * Sets brightness to [percent]. With "Modify system settings" allowed, the system level (auto
     * brightness off); otherwise only Car Hub's window. Returns true when the system level changed.
     */
    fun setBrightness(activity: Activity?, context: Context, percent: Int): Boolean {
        val p = percent.coerceIn(1, 100)
        if (ScreenBrightness.allowed(context)) {
            try {
                val cr = context.contentResolver
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (p * BrightnessMath.MAX / 100).coerceAtLeast(BrightnessMath.MIN))
                setWindowBrightness(activity, null)
                return true
            } catch (_: Exception) {
            }
        }
        setWindowBrightness(activity, p / 100f)
        return false
    }

    /** This window's backlight: 0..1, or null to follow the system again. */
    fun setWindowBrightness(activity: Activity?, value: Float?) {
        val w = activity?.window ?: return
        try {
            val lp = w.attributes
            lp.screenBrightness = value ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            w.attributes = lp
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- clear memory

    /**
     * Stops the background processes of other installed (non-system) apps, as Android itself does
     * when memory runs low; they restart when needed. Ampli's own apps, the keyboard, the speech
     * engine and the music app are left alone, and Android never stops a foreground app or a
     * playing music service this way. Returns roughly how much memory came free, in bytes.
     */
    suspend fun clearMemory(context: Context): Long = withContext(Dispatchers.IO) {
        val am = context.getSystemService(ActivityManager::class.java) ?: return@withContext 0L
        val before = availMem(am)
        val keep = keepPackages(context)
        val pm = context.packageManager
        val apps = try {
            pm.getInstalledApplications(0)
        } catch (_: Exception) {
            emptyList()
        }
        for (info in apps) {
            if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            if (info.packageName in keep || info.packageName.startsWith("com.vikash.ampli")) continue
            try {
                am.killBackgroundProcesses(info.packageName)
            } catch (_: Exception) {
            }
        }
        delay(1_200L)
        (availMem(am) - before).coerceAtLeast(0L)
    }

    private fun availMem(am: ActivityManager): Long = try {
        ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
    } catch (_: Exception) {
        0L
    }

    private fun keepPackages(context: Context): Set<String> {
        val keep = mutableSetOf(context.packageName)
        val cr = context.contentResolver
        try {
            Settings.Secure.getString(cr, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')?.let { keep += it }
        } catch (_: Exception) {
        }
        try {
            Settings.Secure.getString(cr, "tts_default_synth")?.let { keep += it }
        } catch (_: Exception) {
        }
        (context.applicationContext as? DriveApp)?.prefs?.musicPackage?.value?.let { keep += it }
        return keep
    }

    /** "1.2 GB" / "340 MB". */
    fun sizeLabel(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format(java.util.Locale.ROOT, "%.1f GB", mb / 1024.0) else "${mb.toLong()} MB"
    }

    // ---------------------------------------------------------------- misc

    /** Settings › Display › Screen saver (Daydream) on units that have it. */
    fun openDreamSettings(context: Context) {
        start(context, Intent(Settings.ACTION_DREAM_SETTINGS)) || start(context, Intent(Settings.ACTION_DISPLAY_SETTINGS))
    }

    fun openAndroidSettings(context: Context) {
        start(context, Intent(Settings.ACTION_SETTINGS))
    }

    fun hasStorageWrite(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }
}
