package com.vikash.ampli.drive.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Kinds of app Car Hub opens without knowing the package: dock buttons and the car-start actions. */
enum class AppRole { Music, Phone, Mirror, Nav }

/**
 * Plain-Kotlin rules for "does this app look like a music player / a phone app", by its label and
 * package. Head units name their own apps "Multimedia", "BT music", "BT phone", "电话"… and none
 * of them answer the standard intents, so labels are the most reliable hint. Unit-tested.
 */
object AppMatch {

    /** 0 = not this kind of app; higher = better match. 50 and up is good enough to open on its own. */
    fun score(role: AppRole, label: String, packageName: String): Int {
        val l = label.lowercase(Locale.ROOT).trim()
        val p = packageName.lowercase(Locale.ROOT)
        return when (role) {
            AppRole.Music -> musicScore(l, p)
            AppRole.Phone -> phoneScore(l, p)
            AppRole.Mirror -> mirrorScore(l, p)
            AppRole.Nav -> navScore(l, p)
        }
    }

    const val GOOD_ENOUGH = 50

    private val MUSIC_EXACT = setOf("music", "multimedia", "media", "music player", "media player", "音乐", "संगीत", "म्यूजिक")
    private val STREAMING = listOf("spotify", "gaana", "jiosaavn", "saavn", "wynk", "apple music", "amazon music", "youtube music", "yt music", "musicolet")

    private fun musicScore(l: String, p: String): Int {
        // Video and camera inputs often contain "media" too.
        if (l.contains("video") || l.contains("av in") || l.contains("camera") || l.contains("radio")) return 0
        var s = when {
            l in MUSIC_EXACT -> 100
            l.contains("multimedia") -> 95
            // "BT music" needs a phone connected, so a local player beats it.
            (l.contains("bt ") || l.startsWith("bt") || l.contains("bluetooth")) && l.contains("music") -> 70
            l.contains("music") || l.contains("音乐") || l.contains("संगीत") -> 85
            l.contains("media") || l.contains("player") || l.contains("audio") || l.contains("song") || l.contains("mp3") -> 60
            STREAMING.any { l.contains(it) } -> 55
            else -> 0
        }
        if (p == "com.android.music") s = maxOf(s, 90)
        if (p.contains(".music") || p.endsWith(".media") || p.contains("musicplayer") || p.contains(".mediaplayer") || p.contains(".audio")) {
            s = maxOf(s, 45)
        }
        return s
    }

    /** Phone-mirroring apps head units ship: ZLink, AutoKit, CarbitLink, TLink, Carlink, Android Auto. */
    private val MIRROR_PACKAGES = mapOf(
        "com.zjinnova.zlink" to 100,
        "cn.manstep.phonemirrorbox" to 100,
        "com.google.android.projection.gearhead" to 95,
        "com.autokit" to 95, "com.autokit.app" to 95,
        "com.carlinkit" to 95,
        "com.carbit.phonelink" to 95, "com.carbit.carlink" to 95,
        "net.easyconn" to 90, "net.easyconn.carman.wws" to 90,
        "com.suding.speedplay" to 90,
        "com.syu.carlink" to 90, "com.fyt.phonelink" to 90, "com.ts.phonelink" to 90,
        "com.tw.carplay" to 90, "com.ex.phonelink" to 90,
    )
    private val MIRROR_LABELS = listOf(
        "zlink", "z-link", "autokit", "auto kit", "carbitlink", "carbit", "tlink", "t-link", "carlink", "car link",
        "android auto", "carplay", "car play", "phonelink", "phone link", "easyconnection", "easy connection",
        "speedplay", "mirror", "手机互联",
    )

    private fun mirrorScore(l: String, p: String): Int {
        var s = MIRROR_PACKAGES[p] ?: 0
        if (MIRROR_LABELS.any { l.contains(it) } || l == "aa") s = maxOf(s, 90)
        if (p.contains("phonelink") || p.contains("carlink") || p.contains("zlink") || p.contains("mirror")) s = maxOf(s, 70)
        return s
    }

    private val NAV_LABELS = listOf("maps", "navigation", "navi", "waze", "mapmyindia", "mappls", "osmand", "sygic", "here wego", "导航", "地图")

    private fun navScore(l: String, p: String): Int {
        if (p == "com.google.android.apps.maps") return 100
        if (p == "com.waze") return 90
        var s = if (NAV_LABELS.any { l.contains(it) }) 75 else 0
        if (p.contains("navi") || p.contains(".maps") || p.contains("osmand") || p.contains("mapmyindia")) s = maxOf(s, 60)
        return s
    }

    private fun phoneScore(l: String, p: String): Int {
        // Phone-mirroring apps ("Phone link", ZLink) are for the Auto button, not for calls.
        if (l.contains("link") || l.contains("mirror") || l.contains("carplay") || l.contains("android auto")) return 0
        if (l.contains("music")) return 0
        var s = when {
            l in setOf("bt phone", "bluetooth phone", "phone", "dialer", "dialler", "电话", "蓝牙电话", "फ़ोन", "फोन") -> 100
            l.contains("bt phone") || l.contains("bluetooth phone") || l.contains("bt dial") || l.contains("电话") || l.contains("拨号") -> 95
            l.contains("dialer") || l.contains("dialler") -> 90
            l.contains("phone") || l.contains("dial") || l.contains("call") -> 70
            l.contains("contacts") -> 20
            else -> 0
        }
        if (p.contains("dialer")) s = maxOf(s, 80)
        if (p == "com.syu.bt") s = maxOf(s, 85)
        return s
    }
}

/**
 * Asks the UI (DriveRoot) to show the app picker for a role. Anything that can't find an app
 * (the dock, the Help panel, the voice assistant) calls [ask]; the dialog calls [done].
 */
object AppChooser {
    /** [settingOnly]: asked from Settings, so the pick is saved without opening the app. */
    data class Request(val role: AppRole, val number: String? = null, val settingOnly: Boolean = false)

    private val _request = MutableStateFlow<Request?>(null)
    val request: StateFlow<Request?> = _request.asStateFlow()

    fun ask(role: AppRole, number: String? = null, settingOnly: Boolean = false) {
        _request.value = Request(role, number, settingOnly)
    }

    fun done() {
        _request.value = null
    }
}

/** Finds and opens the music app and the phone app, with the driver's choice remembered. */
object AppResolver {

    /** Launchable apps, best matches for [role] first. Runs on a background thread. */
    @Suppress("DEPRECATION")
    fun candidates(context: Context, role: AppRole): List<Pair<AppEntry, Int>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            pm.queryIntentActivities(intent, 0)
                .asSequence()
                .filter { it.activityInfo.packageName != context.packageName }
                .map {
                    AppEntry(
                        packageName = it.activityInfo.packageName,
                        activityName = it.activityInfo.name,
                        label = it.loadLabel(pm)?.toString() ?: it.activityInfo.packageName,
                    )
                }
                .distinctBy { it.packageName + "/" + it.activityName }
                .map { it to AppMatch.score(role, it.label, it.packageName) }
                .sortedWith(compareByDescending<Pair<AppEntry, Int>> { it.second }.thenBy { it.first.label.lowercase() })
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun bestGuess(context: Context, role: AppRole): AppEntry? =
        candidates(context, role).firstOrNull { it.second >= AppMatch.GOOD_ENOUGH }?.first

    /** Label of an installed package, for Settings ("Music app · Multimedia"). */
    fun label(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    /**
     * Music button. In order: the driver's pick, the app playing now (or the last one that had a
     * media session), Android's default music app, an app that plays audio files, then a
     * label/package match ("Multimedia", "BT music"…). Nothing found: the picker opens.
     */
    fun openMusic(context: Context, currentPackage: String?): Boolean {
        val app = context.applicationContext as DriveApp
        app.prefs.musicPackage.value?.let { if (Launchers.openPackage(context, it, quiet = true)) return true }
        val sessionPkg = currentPackage
            ?: app.mediaHub.activePackages().firstOrNull()
            ?: app.prefs.lastMediaPackage
        if (sessionPkg != null && sessionPkg != context.packageName && Launchers.openPackage(context, sessionPkg, quiet = true)) return true
        if (openFirstResolving(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))) return true
        val audio = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://media/external/audio/media/1"), "audio/*")
        packageFor(context, audio)?.let { if (Launchers.openPackage(context, it, quiet = true)) return true }
        bestGuess(context, AppRole.Music)?.let {
            if (Launchers.openComponent(context, it.packageName, it.activityName)) return true
        }
        toast(context, context.getString(R.string.choose_music_app))
        AppChooser.ask(AppRole.Music)
        return false
    }

    /** Phone button: the driver's pick, the system dialer, the head unit's BT phone app, else the picker. */
    fun openPhone(context: Context): Boolean {
        val app = context.applicationContext as DriveApp
        app.prefs.phonePackage.value?.let { if (Launchers.openPackage(context, it, quiet = true)) return true }
        if (startQuiet(context, Intent(Intent.ACTION_DIAL))) return true
        if (Launchers.openPackage(context, FYT_BT_PACKAGE, quiet = true)) return true
        bestGuess(context, AppRole.Phone)?.let {
            if (Launchers.openComponent(context, it.packageName, it.activityName)) return true
        }
        toast(context, context.getString(R.string.choose_phone_app))
        AppChooser.ask(AppRole.Phone)
        return false
    }

    /**
     * Help panel and SOS: [number] in a dialer. Head-unit BT phone apps rarely take a tel: link,
     * so when they don't, the app opens with the number copied and shown, ready to type.
     */
    fun dial(context: Context, number: String): Boolean {
        val app = context.applicationContext as DriveApp
        val tel = Uri.fromParts("tel", number, null)
        val picked = app.prefs.phonePackage.value
        if (picked != null) {
            if (startQuiet(context, Intent(Intent.ACTION_DIAL, tel).setPackage(picked))) return true
            if (Launchers.openPackage(context, picked, quiet = true)) {
                showNumber(context, number)
                return true
            }
        }
        if (startQuiet(context, Intent(Intent.ACTION_DIAL, tel))) return true
        val guess = if (context.packageManager.getLaunchIntentForPackage(FYT_BT_PACKAGE) != null) {
            FYT_BT_PACKAGE
        } else {
            bestGuess(context, AppRole.Phone)?.packageName
        }
        if (guess != null) {
            if (startQuiet(context, Intent(Intent.ACTION_DIAL, tel).setPackage(guess))) return true
            if (Launchers.openPackage(context, guess, quiet = true)) {
                showNumber(context, number)
                return true
            }
        }
        showNumber(context, number)
        AppChooser.ask(AppRole.Phone, number)
        return false
    }

    /** After the picker: open what the driver chose (with the number shown, for a call). */
    fun openChosen(context: Context, entry: AppEntry, number: String?) {
        if (number != null) {
            val tel = Uri.fromParts("tel", number, null)
            if (startQuiet(context, Intent(Intent.ACTION_DIAL, tel).setPackage(entry.packageName))) return
            showNumber(context, number)
        }
        Launchers.openComponent(context, entry.packageName, entry.activityName)
    }

    private fun showNumber(context: Context, number: String) {
        try {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("number", number))
        } catch (_: Exception) {
        }
        Toast.makeText(context, context.getString(R.string.dial_number_hint, number), Toast.LENGTH_LONG).show()
    }

    @Suppress("DEPRECATION")
    private fun packageFor(context: Context, intent: Intent): String? = try {
        context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .firstOrNull { it != context.packageName && it != "android" }
    } catch (_: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun openFirstResolving(context: Context, intent: Intent): Boolean {
        val found = try {
            context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        } catch (_: Exception) {
            false
        }
        return found && startQuiet(context, intent)
    }

    private fun startQuiet(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    /** FYT head units keep the dialer inside their Bluetooth app. */
    const val FYT_BT_PACKAGE = "com.syu.bt"
}
