package com.vikash.ampli.drive.prefs

import android.content.Context
import com.vikash.ampli.drive.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which home-screen themes are chosen. With [auto] on, [dayId] shows from sunrise to sunset and
 * [nightId] after dark; with it off, [fixedId] always shows. Ids come from DriveThemes.
 */
data class ThemeChoice(
    val auto: Boolean,
    val dayId: String,
    val nightId: String,
    val fixedId: String,
)

/** Pro entitlement. [purchased] is the cached Play Billing result; [testUnlock] only counts in debug builds. */
data class ProState(val purchased: Boolean, val testUnlock: Boolean) {
    val isPro: Boolean get() = purchased || (BuildConfig.DEBUG && testUnlock)
}

/** Small settings store. SharedPreferences is enough here and costs nothing at startup. */
class DrivePrefs(context: Context) {
    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _keepScreenOn = MutableStateFlow(sp.getBoolean(KEY_SCREEN_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _theme = MutableStateFlow(readThemeChoice())
    val theme: StateFlow<ThemeChoice> = _theme.asStateFlow()

    private val _pro = MutableStateFlow(
        ProState(
            purchased = sp.getBoolean(KEY_PRO_PURCHASED, false),
            testUnlock = sp.getBoolean(KEY_PRO_TEST, false),
        ),
    )
    val pro: StateFlow<ProState> = _pro.asStateFlow()

    /**
     * BCP-47 tag of the app language ("hi", "zh-CN", "bho"…), or "" for the system language.
     * The voice assistant reads this too, for speech recognition and text-to-speech.
     */
    private val _language = MutableStateFlow(sp.getString(KEY_LANGUAGE, "") ?: "")
    val language: StateFlow<String> = _language.asStateFlow()

    /** Package the dock's "Auto" button opens (a phone-link app such as ZLink), or null. */
    private val _autoPackage = MutableStateFlow(sp.getString(KEY_AUTO_PKG, null))
    val autoPackage: StateFlow<String?> = _autoPackage.asStateFlow()

    /** "Always listen for Hey Ampli": off by default; VoiceService runs only while this is on. */
    private val _voiceAlways = MutableStateFlow(sp.getBoolean(KEY_VOICE_ALWAYS, false))
    val voiceAlways: StateFlow<Boolean> = _voiceAlways.asStateFlow()

    /** When the assistant last said the full greeting (wall-clock ms), so it isn't repeated every time. */
    var lastVoiceGreeting: Long
        get() = sp.getLong(KEY_VOICE_GREETED, 0L)
        set(value) { sp.edit().putLong(KEY_VOICE_GREETED, value).apply() }

    /** Whether we already asked for location/Bluetooth once (first-launch flow). */
    var askedPermissions: Boolean
        get() = sp.getBoolean(KEY_ASKED, false)
        set(value) { sp.edit().putBoolean(KEY_ASKED, value).apply() }

    fun setKeepScreenOn(value: Boolean) {
        sp.edit().putBoolean(KEY_SCREEN_ON, value).apply()
        _keepScreenOn.value = value
    }

    fun setTheme(choice: ThemeChoice) {
        sp.edit()
            .putBoolean(KEY_THEME_AUTO, choice.auto)
            .putString(KEY_THEME_DAY, choice.dayId)
            .putString(KEY_THEME_NIGHT, choice.nightId)
            .putString(KEY_THEME_FIXED, choice.fixedId)
            .apply()
        _theme.value = choice
    }

    fun setProPurchased(value: Boolean) {
        sp.edit().putBoolean(KEY_PRO_PURCHASED, value).apply()
        _pro.value = _pro.value.copy(purchased = value)
    }

    /** Test builds only: lets sideloaded APKs try the Pro themes without Play Billing. */
    fun setProTestUnlock(value: Boolean) {
        if (!BuildConfig.DEBUG) return
        sp.edit().putBoolean(KEY_PRO_TEST, value).apply()
        _pro.value = _pro.value.copy(testUnlock = value)
    }

    fun setLanguage(tag: String) {
        // commit(), not apply(): Android 12 reads this back while the activity is recreated.
        sp.edit().putString(KEY_LANGUAGE, tag).commit()
        _language.value = tag
    }

    fun setAutoPackage(pkg: String?) {
        sp.edit().putString(KEY_AUTO_PKG, pkg).apply()
        _autoPackage.value = pkg
    }

    fun setVoiceAlways(value: Boolean) {
        sp.edit().putBoolean(KEY_VOICE_ALWAYS, value).apply()
        _voiceAlways.value = value
    }

    private fun readThemeChoice(): ThemeChoice {
        // Earlier builds stored only "Auto" / "Day" / "Night".
        val legacy = sp.getString(KEY_THEME_LEGACY, "Auto")
        val legacyFixed = if (legacy == "Day") DEFAULT_DAY else DEFAULT_NIGHT
        return ThemeChoice(
            auto = sp.getBoolean(KEY_THEME_AUTO, legacy == "Auto"),
            dayId = sp.getString(KEY_THEME_DAY, DEFAULT_DAY) ?: DEFAULT_DAY,
            nightId = sp.getString(KEY_THEME_NIGHT, DEFAULT_NIGHT) ?: DEFAULT_NIGHT,
            fixedId = sp.getString(KEY_THEME_FIXED, legacyFixed) ?: legacyFixed,
        )
    }

    companion object {
        /** The two free themes. Keep in sync with DriveThemes. */
        const val DEFAULT_DAY = "daylight_clean"
        const val DEFAULT_NIGHT = "ampli_rose"

        private const val FILE = "drive_prefs"
        private const val KEY_SCREEN_ON = "keep_screen_on"
        private const val KEY_THEME_LEGACY = "theme_mode"
        private const val KEY_THEME_AUTO = "theme_auto"
        private const val KEY_THEME_DAY = "theme_day"
        private const val KEY_THEME_NIGHT = "theme_night"
        private const val KEY_THEME_FIXED = "theme_fixed"
        private const val KEY_PRO_PURCHASED = "pro_purchased"
        private const val KEY_PRO_TEST = "pro_test_unlock"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_AUTO_PKG = "auto_package"
        private const val KEY_ASKED = "asked_permissions"
        private const val KEY_VOICE_ALWAYS = "voice_always_listen"
        private const val KEY_VOICE_GREETED = "voice_last_greeting"

        /** Read before the app object exists (attachBaseContext on Android 12). */
        fun readLanguage(context: Context): String =
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, "") ?: ""
    }
}
