package com.vikash.ampli.drive.ui.theme

import com.vikash.ampli.drive.prefs.DrivePrefs
import com.vikash.ampli.drive.prefs.ThemeChoice

/**
 * Which themes are free and which theme shows for a saved choice. Plain Kotlin (no Compose) so
 * the rules can be unit-tested; [DriveThemes] and the picker both read from here.
 */
object ThemeAccess {

    const val FROSTED_GLASS = "frosted_glass"
    const val MIDNIGHT_FROST = "midnight_frost"
    const val SAFFRON_NIGHT = "desi_saffron"

    /** The five free themes. Every other theme needs Pro. */
    val FREE_IDS: Set<String> = setOf(
        DrivePrefs.DEFAULT_NIGHT, // Ampli Rose
        DrivePrefs.DEFAULT_DAY, // Daylight Clean
        FROSTED_GLASS,
        MIDNIGHT_FROST,
        SAFFRON_NIGHT, // S1
    )

    fun isFree(id: String): Boolean = id in FREE_IDS

    /**
     * The id of the theme to show now. An unknown id, or a Pro theme without Pro (refund, test
     * switch turned off), falls back to the free default for that time of day: Daylight Clean by
     * day in Auto mode, Ampli Rose otherwise.
     */
    fun resolveId(choice: ThemeChoice, night: Boolean, isPro: Boolean, known: Set<String>): String {
        val id = when {
            !choice.auto -> choice.fixedId
            night -> choice.nightId
            else -> choice.dayId
        }
        val fallback = if (choice.auto && !night) DrivePrefs.DEFAULT_DAY else DrivePrefs.DEFAULT_NIGHT
        return if (id !in known || (!isFree(id) && !isPro)) fallback else id
    }

    /**
     * The choice after the driver taps theme [id] in the picker.
     *
     * Fixed mode: [id] always shows. Auto mode: [id] goes into the slot the picker's Day/Night chip
     * points at ([forNight]). The picker points the chip at the slot showing *now*, so a tap changes
     * the screen straight away; before this, the chip could stay on "Day" after dark, the tap
     * only changed the day theme, and nothing on screen changed (head-unit report, build 21).
     */
    fun select(choice: ThemeChoice, id: String, forNight: Boolean): ThemeChoice = when {
        !choice.auto -> choice.copy(fixedId = id)
        forNight -> choice.copy(nightId = id)
        else -> choice.copy(dayId = id)
    }
}
