package com.vikash.ampli.drive.ui.theme

import com.vikash.ampli.drive.prefs.ThemeChoice
import com.vikash.ampli.drive.prefs.ThemeDefaults

/**
 * Which themes are free and which theme shows for a saved choice. Plain Kotlin (no Compose) so
 * the rules can be unit-tested; [DriveThemes] and the picker both read from here.
 */
object ThemeAccess {

    /**
     * The free themes. Every other theme needs Pro. One place to change the Free / Pro split:
     * the owner's call (31 Oct 2026) is Map First free, Big Glance, Bento Grid and Road Sign Pro.
     */
    val FREE_IDS: Set<String> = setOf(ThemeDefaults.MAP_FIRST)

    fun isFree(id: String): Boolean = id in FREE_IDS

    /** "Auto night" (Big Glance after sunset) is a Pro feature; free drivers stay on Map First. */
    fun autoNightAvailable(isPro: Boolean): Boolean = isPro

    /**
     * The id of the theme to show now. An unknown id, or a Pro theme without Pro (refund, test
     * switch turned off), falls back to Map First. With "Auto night" on (Pro only) Big Glance shows
     * between sunset and sunrise.
     */
    fun resolveId(choice: ThemeChoice, night: Boolean, isPro: Boolean): String {
        if (night && choice.autoNight && autoNightAvailable(isPro)) return ThemeDefaults.NIGHT_ID
        val id = choice.id
        return if (id !in ThemeDefaults.IDS || (!isFree(id) && !isPro)) ThemeDefaults.MAP_FIRST else id
    }

    /** True when tapping [id] in the picker should open the Pro sheet instead. */
    fun locked(id: String, isPro: Boolean): Boolean = !isFree(id) && !isPro

    /** The choice after the driver taps theme [id] in the picker ("Auto night" is kept). */
    fun select(choice: ThemeChoice, id: String): ThemeChoice = choice.copy(id = id)
}
