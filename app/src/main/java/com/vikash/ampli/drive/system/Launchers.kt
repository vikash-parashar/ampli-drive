package com.vikash.ampli.drive.system

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.service.DriveNotificationListener

/** Every intent the launcher fires, in one place. All return true when something opened. */
object Launchers {
    const val GOOGLE_MAPS = DriveNotificationListener.GOOGLE_MAPS

    /**
     * Phone-link (CarPlay / Android Auto mirroring) apps, best first: the Phone link button, voice
     * "connect my phone" and the Ampli app list all use this one list. Head units ship many
     * variants; keep the manifest's <queries> in step.
     */
    val PHONE_LINK_PACKAGES = listOf(
        "com.zjinnova.zlink",
        "cn.manstep.phonemirrorBox",
        "com.suding.speedplay",
        "com.autokit",
        "com.autokit.app",
        "com.carlinkit",
        "net.easyconn",
        "net.easyconn.carman.wws",
        "com.carbit.phonelink",
        "com.syu.carlink",
        "com.fyt.phonelink",
        "com.ts.phonelink",
        "com.tw.carplay",
        "com.ex.phonelink",
        "com.google.android.projection.gearhead",
    )

    /** Installed apps whose name says phone link, when none of [PHONE_LINK_PACKAGES] is there. */
    val PHONE_LINK_LABELS = listOf(
        "android auto", "carplay", "car play", "zlink", "phone link", "phonelink", "autokit", "carlink",
        "carlinkit", "easyconn", "easy connection", "easyconnection", "tlink", "t-link", "phone mirror",
    )

    fun openNavigation(context: Context, location: Location?): Boolean {
        if (openPackage(context, GOOGLE_MAPS, quiet = true)) return true
        // No Google Maps: let any map app take a plain geo: link.
        val geo = if (location != null) {
            "geo:${location.latitude},${location.longitude}"
        } else {
            "geo:0,0?q="
        }
        return start(context, Intent(Intent.ACTION_VIEW, Uri.parse(geo)), R.string.error_no_navigation_app)
    }

    fun openPackage(context: Context, packageName: String, quiet: Boolean = false): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            if (!quiet) toast(context, R.string.error_app_not_installed)
            return false
        }
        return start(context, intent, if (quiet) null else R.string.error_cannot_open_app)
    }

    fun openComponent(context: Context, packageName: String, activityName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(packageName, activityName))
            .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return start(context, intent, R.string.error_cannot_open_app)
    }

    /** Phone button: see [AppResolver.openPhone] (dialer, BT phone app, or the app picker). */
    fun openDialer(context: Context): Boolean = AppResolver.openPhone(context)

    /** Music button: see [AppResolver.openMusic] (the driver's pick, else Ampli MP3 Player). */
    fun openMusic(context: Context): Boolean = AppResolver.openMusic(context, autoplay = true)

    /** Returns false when nothing is set up, so the caller can show the app picker. */
    fun openPhoneLink(context: Context, pinnedPackage: String?): Boolean {
        if (pinnedPackage != null && openPackage(context, pinnedPackage, quiet = true)) return true
        if (PHONE_LINK_PACKAGES.any { openPackage(context, it, quiet = true) }) return true
        val byLabel = phoneLinkByLabel(context) ?: return false
        return openComponent(context, byLabel.packageName, byLabel.name)
    }

    @Suppress("DEPRECATION")
    private fun phoneLinkByLabel(context: Context): android.content.pm.ActivityInfo? = try {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .firstOrNull { ri ->
                val label = ri.loadLabel(pm)?.toString()?.lowercase().orEmpty()
                ri.activityInfo.packageName != context.packageName && PHONE_LINK_LABELS.any { label.contains(it) }
            }?.activityInfo
    } catch (_: Exception) {
        null
    }

    /**
     * "Allow access" buttons: where Android 13+ may refuse the switch (sideloaded APK), show the
     * in-app steps first; otherwise (Play installs, Android 12 and older) open the switch directly.
     */
    fun allowNotificationAccess(context: Context) {
        if (RestrictedSettings.mayBlock(context)) RestrictedSettings.showBefore() else openNotificationAccess(context)
    }

    fun openNotificationAccess(context: Context) {
        // Android 13+: if access is still off on return, show the "restricted settings" guide.
        RestrictedSettings.arm(context)
        val component = ComponentName(context, DriveNotificationListener::class.java)
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        if (start(context, detail, null)) return
        if (start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), null)) return
        openAppDetails(context)
    }

    fun openHomeSettings(context: Context) {
        if (start(context, Intent(Settings.ACTION_HOME_SETTINGS), null)) return
        if (start(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS), null)) return
        start(context, Intent(Settings.ACTION_SETTINGS), R.string.error_open_default_apps)
    }

    fun openAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
        start(context, intent, R.string.error_cannot_open_settings)
    }

    fun openLocationSettings(context: Context) {
        start(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), R.string.error_cannot_open_settings)
    }

    @Suppress("DEPRECATION")
    fun isDefaultHome(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == context.packageName
    }

    private fun start(context: Context, intent: Intent, @StringRes failMessage: Int?): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        if (failMessage != null) toast(context, failMessage)
        false
    } catch (_: SecurityException) {
        if (failMessage != null) toast(context, failMessage)
        false
    }

    private fun toast(context: Context, @StringRes message: Int) {
        Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
    }
}
