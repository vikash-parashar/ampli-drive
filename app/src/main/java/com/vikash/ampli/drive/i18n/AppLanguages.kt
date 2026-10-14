package com.vikash.ampli.drive.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.vikash.ampli.drive.prefs.DrivePrefs
import java.util.Locale

/** One entry in the language picker; [nativeName] is written in the language's own script. */
data class AppLanguage(val tag: String, val nativeName: String)

/**
 * In-app language switching without AppCompat.
 * - Android 13+: the system's per-app language (LocaleManager), which also shows in
 *   Settings › Apps › Ampli Drive › Language thanks to res/xml/locales_config.xml.
 * - Android 12: the choice is saved in DrivePrefs and applied by wrapping the base context
 *   (see [wrap]) in DriveApp and MainActivity; the activity is recreated to pick it up.
 *
 * Keep this list in sync with res/xml/locales_config.xml and the values-* folders.
 */
object AppLanguages {
    val all: List<AppLanguage> = listOf(
        AppLanguage("en", "English"),
        AppLanguage("hi", "हिन्दी"),
        AppLanguage("bn", "বাংলা"),
        AppLanguage("mr", "मराठी"),
        AppLanguage("te", "తెలుగు"),
        AppLanguage("ta", "தமிழ்"),
        AppLanguage("gu", "ગુજરાતી"),
        AppLanguage("kn", "ಕನ್ನಡ"),
        AppLanguage("ml", "മലയാളം"),
        AppLanguage("or", "ଓଡ଼ିଆ"),
        AppLanguage("pa", "ਪੰਜਾਬੀ"),
        AppLanguage("as", "অসমীয়া"),
        AppLanguage("ur", "اردو"),
        AppLanguage("mai", "मैथिली"),
        AppLanguage("bho", "भोजपुरी"),
        AppLanguage("bgc", "हरियाणवी"),
        AppLanguage("kok", "कोंकणी"),
        AppLanguage("ne", "नेपाली"),
        AppLanguage("de", "Deutsch"),
        AppLanguage("fr", "Français"),
        AppLanguage("zh-CN", "中文（简体）"),
    )

    /**
     * Switches the app language; [tag] "" means the system language.
     * Returns true when the caller must recreate the activity (Android 12 only).
     */
    fun apply(context: Context, tag: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return true
            manager.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            return false
        }
        return true
    }

    /**
     * The language in effect: on Android 13+ the system setting wins (the driver may have changed
     * it in system Settings), otherwise the saved choice. Matches by language when the region differs.
     */
    fun current(context: Context, saved: String): String {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val list = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (list == null || list.isEmpty) "" else list[0].toLanguageTag()
        } else {
            saved
        }
        if (tag.isEmpty()) return ""
        all.firstOrNull { it.tag.equals(tag, ignoreCase = true) }?.let { return it.tag }
        val lang = Locale.forLanguageTag(tag).language
        return all.firstOrNull { Locale.forLanguageTag(it.tag).language == lang }?.tag ?: tag
    }

    /** Android 12: a context whose resources use the saved language. No-op on 13+. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = DrivePrefs.readLanguage(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
