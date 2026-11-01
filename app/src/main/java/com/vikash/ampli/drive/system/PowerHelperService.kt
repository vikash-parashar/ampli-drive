package com.vikash.ampli.drive.system

import android.accessibilityservice.AccessibilityService
import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * "Ampli power helper": an accessibility service that only presses Android's own keys for the
 * driver, because a head unit without buttons has no other way to reach them: the power menu
 * (Restart, Power off), recent apps (long press on Apps) and, on Android 9+, a screenshot.
 *
 * It asks for no events and cannot read window content (res/xml/power_helper.xml), so it sees
 * nothing on the screen and sends nothing anywhere. Off until the driver turns it on in Android's
 * Accessibility settings from the one-time setup card.
 */
class PowerHelperService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event types are requested; nothing to do.
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: PowerHelperService? = null

        /** True while Android has the helper running. */
        val connected: Boolean get() = instance != null

        private fun act(action: Int): Boolean = try {
            instance?.performGlobalAction(action) ?: false
        } catch (_: Exception) {
            false
        }

        /** Android's power menu (Restart / Power off are tapped there by the driver). */
        fun powerDialog(): Boolean = act(GLOBAL_ACTION_POWER_DIALOG)

        fun recents(): Boolean = act(GLOBAL_ACTION_RECENTS)

        /** Android 9+ only; Android 8 uses the screen-capture prompt instead (ScreenShot). */
        fun screenshot(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && act(GLOBAL_ACTION_TAKE_SCREENSHOT)

        /** True when the driver switched the helper on in Accessibility settings. */
        fun enabled(context: Context): Boolean {
            val mine = ComponentName(context, PowerHelperService::class.java)
            val list = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            } catch (_: Exception) {
                null
            } ?: return connected
            return connected || list.split(':').any { ComponentName.unflattenFromString(it.trim()) == mine }
        }

        /** Android's Accessibility settings, where "Ampli power helper" is switched on. */
        fun openSettings(context: Context): Boolean = try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Settings › Power › Turn the display fully off: device admin with the force-lock policy only
 * (res/xml/device_admin.xml), so Screen off can lock the screen. It cannot wipe, read or change
 * anything else. Off unless the driver turns it on.
 */
class AmpliDeviceAdmin : DeviceAdminReceiver()
