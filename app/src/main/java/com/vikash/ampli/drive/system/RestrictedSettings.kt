package com.vikash.ampli.drive.system

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android 13+ "restricted settings": for a sideloaded APK the notification-access switch is
 * refused ("App was denied access… restricted permission") until the driver taps App info › ⋮ ›
 * "Allow restricted settings". Installs from Play are not blocked.
 *
 * Every "Allow access" button goes through [Launchers.openNotificationAccess], which arms this.
 * When the driver comes back and access is still off, the in-app guide shows the steps.
 */
object RestrictedSettings {
    /** Restricted settings exist from Android 13. Below that the old behaviour stays. */
    val applies: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private val _armed = MutableStateFlow(false)

    /** True after the driver was sent to notification access and hasn't got it yet. */
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    private const val PLAY_STORE = "com.android.vending"

    /** True when Google Play installed (or last updated) this app: Android does not restrict those installs. */
    @Suppress("DEPRECATION")
    fun installedFromPlay(context: Context): Boolean = try {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).let { it.installingPackageName ?: it.initiatingPackageName }
        } else {
            pm.getInstallerPackageName(context.packageName)
        }
        installer == PLAY_STORE
    } catch (_: Exception) {
        false
    }

    /** Called when the notification-access screen is opened. Play installs never need the guide. */
    fun arm(context: Context) {
        if (applies && !installedFromPlay(context) && !hasNotificationAccess(context)) _armed.value = true
    }

    fun disarm() {
        _armed.value = false
    }
}
