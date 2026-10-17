package com.vikash.ampli.drive.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/**
 * Ampli Shell settings: Car Hub as the head unit's whole screen (full screen, startup animation,
 * the Ampli-only app drawer and its PIN, the "make Ampli your home screen" card).
 */
class ShellPrefs(context: Context) {
    private val sp = context.getSharedPreferences("ampli_shell", Context.MODE_PRIVATE)

    private val _fullScreen = MutableStateFlow(sp.getBoolean(KEY_FULL_SCREEN, false))

    /** Hides the status and navigation bars; a swipe from the edge shows them for a moment. */
    val fullScreen: StateFlow<Boolean> = _fullScreen.asStateFlow()

    fun setFullScreen(value: Boolean) {
        sp.edit().putBoolean(KEY_FULL_SCREEN, value).apply()
        _fullScreen.value = value
    }

    private val _startupAnimation = MutableStateFlow(sp.getBoolean(KEY_STARTUP_ANIM, true))

    /** Settings › Startup animation: the Ampli splash after boot / ignition. On by default. */
    val startupAnimation: StateFlow<Boolean> = _startupAnimation.asStateFlow()

    fun setStartupAnimation(value: Boolean) {
        sp.edit().putBoolean(KEY_STARTUP_ANIM, value).apply()
        _startupAnimation.value = value
    }

    private val _drawerApps = MutableStateFlow(sp.getStringSet(KEY_DRAWER_APPS, null)?.toSet())

    /** Packages on the Ampli app list; null until the driver edits it (then the defaults apply). */
    val drawerApps: StateFlow<Set<String>?> = _drawerApps.asStateFlow()

    fun setDrawerApps(packages: Set<String>) {
        sp.edit().putStringSet(KEY_DRAWER_APPS, packages).apply()
        _drawerApps.value = packages
    }

    private val _pinSet = MutableStateFlow(sp.getString(KEY_PIN_HASH, null) != null)

    /** True when "More apps" asks for a 4-digit PIN. */
    val pinSet: StateFlow<Boolean> = _pinSet.asStateFlow()

    fun setPin(pin: String?) {
        if (pin == null) sp.edit().remove(KEY_PIN_HASH).apply() else sp.edit().putString(KEY_PIN_HASH, hash(pin)).apply()
        _pinSet.value = pin != null
    }

    fun checkPin(pin: String): Boolean = sp.getString(KEY_PIN_HASH, null)?.let { it == hash(pin) } ?: true

    /** "Not now" on the Home card; Settings still shows its card until Ampli is the home app. */
    var homeCardDismissed: Boolean
        get() = sp.getBoolean(KEY_HOME_CARD_DISMISSED, false)
        set(value) = sp.edit().putBoolean(KEY_HOME_CARD_DISMISSED, value).apply()

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(("ampli-shell:" + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_FULL_SCREEN = "full_screen"
        private const val KEY_STARTUP_ANIM = "startup_animation"
        private const val KEY_DRAWER_APPS = "drawer_apps"
        private const val KEY_PIN_HASH = "drawer_pin"
        private const val KEY_HOME_CARD_DISMISSED = "home_card_dismissed"

        /** A PIN is exactly four digits. */
        fun validPin(pin: String): Boolean = pin.length == 4 && pin.all { it in '0'..'9' }
    }
}
