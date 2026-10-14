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

    /** Phone-link apps the "Auto" button tries when the driver hasn't picked one. */
    val PHONE_LINK_PACKAGES = listOf(
        "com.zjinnova.zlink",
        "com.google.android.projection.gearhead",
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

    /** Music button: see [AppResolver.openMusic] (the driver's pick, the playing app, Multimedia…). */
    fun openMusic(context: Context, currentPackage: String?): Boolean = AppResolver.openMusic(context, currentPackage)

    /** Returns false when nothing is set up, so the caller can show the app picker. */
    fun openPhoneLink(context: Context, pinnedPackage: String?): Boolean {
        if (pinnedPackage != null && openPackage(context, pinnedPackage, quiet = true)) return true
        return PHONE_LINK_PACKAGES.any { openPackage(context, it, quiet = true) }
    }

    fun openNotificationAccess(context: Context) {
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
