package com.vikash.ampli.drive.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import java.util.Locale

/** A vendor app found for "Car settings". */
data class CarSettingsApp(val packageName: String, val activityName: String?, val label: String)

/** One launcher entry as [CarSettingsMatch] sees it. */
data class LauncherCandidate(val packageName: String, val activityName: String, val label: String, val system: Boolean)

/**
 * Which launcher entry is the head unit's own "Car settings" (vehicle, CAN bus, steering keys,
 * reversing camera, factory menu). Pure, unit-tested (SystemSettingsTest).
 */
object CarSettingsMatch {

    /**
     * Car-settings apps of FYT / TS7, MTCD and similar Chinese units, best first. Opened directly
     * when installed, even without a launcher icon. Package names vary by firmware, so the label
     * search below covers the rest.
     */
    val KNOWN = listOf(
        "com.syu.carsettings",
        "com.syu.settings",
        "com.syu.setting",
        "com.fyt.settings",
        "com.fyt.carsettings",
        "com.fyt.car.settings",
        "com.ts.carsettings",
        "com.ts.settings",
        "com.microntek.carsettings",
        "com.android.carsettings",
        "com.yf.carsettings",
        "com.car.settings",
        "com.carsettings",
    )

    private val CAR_WORDS = listOf("car setting", "carsetting", "vehicle", "汽车", "车辆", "车载", "car info", "car config", "gaadi", "गाड़ी")
    private val PACKAGE_WORDS = listOf("carsetting", "car.setting", "car_setting", "vehiclesetting", "vehicle.setting", "carset")

    /** Android's own settings and Google's apps are "Android settings", never "Car settings". */
    fun excluded(pkg: String): Boolean =
        pkg == "com.android.settings" || pkg.startsWith("com.google.") || pkg.startsWith("com.vikash.ampli") ||
            pkg == "com.android.car.settings"

    /** 3 = known package, 2 = car words, 1 = a vendor system app called "Settings", 0 = no. */
    fun score(c: LauncherCandidate): Int {
        val pkg = c.packageName.lowercase(Locale.ROOT)
        if (excluded(pkg)) return 0
        if (pkg in KNOWN) return 3
        val label = c.label.lowercase(Locale.ROOT)
        if (CAR_WORDS.any { it in label } || PACKAGE_WORDS.any { it in pkg }) return if (c.system) 2 else 0
        val vendor = !pkg.startsWith("com.android.") && !pkg.startsWith("android")
        if (c.system && vendor && ("setting" in label || "设置" in label)) return 1
        return 0
    }

    /** The best candidate, or null when the unit has none (the button then stays hidden). */
    fun pick(candidates: List<LauncherCandidate>): LauncherCandidate? =
        candidates.map { it to score(it) }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
}

/** "Android settings" and "Car settings" inside Ampli (Apps, Controls, Settings). */
object SystemSettings {

    fun openAndroid(context: Context): Boolean = start(context, Intent(Settings.ACTION_SETTINGS))

    @Volatile private var cached: CarSettingsApp? = null
    @Volatile private var looked = false

    /** The head unit's car-settings app, or null. Looked up once per run (cheap afterwards). */
    @Suppress("DEPRECATION")
    fun carSettings(context: Context): CarSettingsApp? {
        if (looked) return cached
        val pm = context.packageManager
        val found = try {
            // Known packages first: some have no launcher icon.
            CarSettingsMatch.KNOWN.firstNotNullOfOrNull { pkg ->
                val launch = try {
                    pm.getLaunchIntentForPackage(pkg)
                } catch (_: Exception) {
                    null
                }
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) {
                    null
                }
                if (launch != null && label != null) CarSettingsApp(pkg, launch.component?.className, label) else null
            } ?: run {
                val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map {
                    LauncherCandidate(
                        packageName = it.activityInfo.packageName,
                        activityName = it.activityInfo.name,
                        label = try {
                            it.loadLabel(pm)?.toString().orEmpty()
                        } catch (_: Exception) {
                            ""
                        },
                        system = (it.activityInfo.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0,
                    )
                }
                CarSettingsMatch.pick(list)?.let { CarSettingsApp(it.packageName, it.activityName, it.label) }
            }
        } catch (_: Exception) {
            null
        }
        cached = found
        looked = true
        return found
    }

    fun openCar(context: Context): Boolean {
        val app = carSettings(context) ?: return false
        val intent = if (app.activityName != null) {
            Intent(Intent.ACTION_MAIN).setComponent(ComponentName(app.packageName, app.activityName))
        } else {
            context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
        }
        return start(context, intent)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }
}
