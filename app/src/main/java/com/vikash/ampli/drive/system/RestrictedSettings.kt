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
 * "Allow access" buttons go through [Launchers.allowNotificationAccess]: where Android may refuse,
 * the in-app guide first shows the steps ([before]). Opening the switch ([Launchers.openNotificationAccess])
 * arms the guide; when the driver comes back and access is still off, it shows again.
 *
 * The armed flag is saved, not only kept in memory: phones such as OnePlus often kill the app
 * while the driver is in Settings, and the guide must still show when the app comes back.
 */
object RestrictedSettings {
    /** Restricted settings exist from Android 13. Below that the old behaviour stays. */
    val applies: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private const val PREFS = "restricted_settings"
    private const val KEY_ARMED = "armed"

    private val _armed = MutableStateFlow(false)

    /** True after the driver was sent to notification access and hasn't got it yet. */
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    private val _before = MutableStateFlow(false)

    /** True while the steps are shown before the driver is sent to the switch. */
    val before: StateFlow<Boolean> = _before.asStateFlow()

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

    /** Android may refuse the notification-access switch: Android 13+, not from Play, access still off. */
    fun mayBlock(context: Context): Boolean =
        applies && !installedFromPlay(context) && !hasNotificationAccess(context)

    /** Called when the notification-access screen is opened. Play installs never need the guide. */
    fun arm(context: Context) {
        _before.value = false
        if (mayBlock(context)) setArmed(context, true)
    }

    fun disarm(context: Context) {
        _before.value = false
        setArmed(context, false)
    }

    /** Re-reads the saved flag: the app may have been restarted while the driver was in Settings. */
    fun restore(context: Context) {
        _armed.value = prefs(context).getBoolean(KEY_ARMED, false)
    }

    /** Shows the steps before the switch ("it will be refused once, then App info › ⋮"). */
    fun showBefore() {
        _before.value = true
    }

    fun hideBefore() {
        _before.value = false
    }

    private fun setArmed(context: Context, value: Boolean) {
        _armed.value = value
        prefs(context).edit().putBoolean(KEY_ARMED, value).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
