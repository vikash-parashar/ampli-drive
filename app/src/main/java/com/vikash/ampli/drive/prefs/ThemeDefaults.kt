package com.vikash.ampli.drive.prefs

/**
 * Theme values exactly as stored in SharedPreferences: null where a key was never written.
 * [id] / [autoNight] are this version's keys; [legacyIds] are whatever the earlier theme keys
 * (theme_fixed, theme_day, theme_night, theme_mode) still hold, only to tell an old install apart.
 */
data class StoredTheme(
    val id: String?,
    val autoNight: Boolean?,
    val legacyIds: List<String> = emptyList(),
)

/**
 * The four home themes and the move to them. Plain Kotlin, unit-tested (ThemeDefaultsTest).
 *
 * Car Hub ships five themes: Map First (the default, day and night colours by the sun), Big
 * Glance, Bento Grid, Neo-Brutal Road Sign and Calm. Every theme id saved by an earlier build (Crimson Drive, the Soft family,
 * Glass, Saffron, Classic…) maps to the default; nothing is written by the move itself.
 */
object ThemeDefaults {
    const val MAP_FIRST = "map_first"
    const val BIG_GLANCE = "big_glance"
    const val BENTO = "bento_grid"
    const val ROAD_SIGN = "road_sign"

    /** Calm (Pro): the Map First layout in teal-grey and warm gold. */
    const val CALM = "calm"

    /** Every theme this build knows. */
    val IDS: List<String> = listOf(MAP_FIRST, BIG_GLANCE, BENTO, ROAD_SIGN, CALM)

    /** The default: Map First, "Auto night" off. */
    val DEFAULT = ThemeChoice(id = MAP_FIRST, autoNight = false)

    /** The theme switched to after sunset when "Auto night" is on. */
    const val NIGHT_ID = BIG_GLANCE

    /** The choice to use for what is stored: a known id is kept, anything else is the default. */
    fun choiceFor(s: StoredTheme): ThemeChoice = ThemeChoice(
        id = s.id?.takeIf { it in IDS } ?: MAP_FIRST,
        autoNight = s.autoNight ?: false,
    )

    /** True when this install still carries a theme saved by an earlier build (Crimson, Soft…). */
    fun migratedFromOld(s: StoredTheme): Boolean = s.id == null && s.legacyIds.isNotEmpty()
}
