package com.vikash.ampli.drive.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { Auto, Day, Night }

/** Small settings store. SharedPreferences is enough here and costs nothing at startup. */
class DrivePrefs(context: Context) {
    private val sp = context.getSharedPreferences("drive_prefs", Context.MODE_PRIVATE)

    private val _keepScreenOn = MutableStateFlow(sp.getBoolean(KEY_SCREEN_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _themeMode = MutableStateFlow(readTheme())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Package the dock's "Auto" button opens (a phone-link app such as ZLink), or null. */
    private val _autoPackage = MutableStateFlow(sp.getString(KEY_AUTO_PKG, null))
    val autoPackage: StateFlow<String?> = _autoPackage.asStateFlow()

    /** Whether we already asked for location/Bluetooth once (first-launch flow). */
    var askedPermissions: Boolean
        get() = sp.getBoolean(KEY_ASKED, false)
        set(value) { sp.edit().putBoolean(KEY_ASKED, value).apply() }

    fun setKeepScreenOn(value: Boolean) {
        sp.edit().putBoolean(KEY_SCREEN_ON, value).apply()
        _keepScreenOn.value = value
    }

    fun setThemeMode(mode: ThemeMode) {
        sp.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setAutoPackage(pkg: String?) {
        sp.edit().putString(KEY_AUTO_PKG, pkg).apply()
        _autoPackage.value = pkg
    }

    private fun readTheme(): ThemeMode {
        val name = sp.getString(KEY_THEME, ThemeMode.Auto.name)
        return ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.Auto
    }

    private companion object {
        const val KEY_SCREEN_ON = "keep_screen_on"
        const val KEY_THEME = "theme_mode"
        const val KEY_AUTO_PKG = "auto_package"
        const val KEY_ASKED = "asked_permissions"
    }
}
