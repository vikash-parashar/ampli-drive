package com.vikash.ampli.drive.prefs

/**
 * Theme values exactly as stored in SharedPreferences: null where a key was never written.
 * [legacyMode] is the "Auto" / "Day" / "Night" setting of the earliest builds.
 */
data class StoredTheme(
    val auto: Boolean?,
    val dayId: String?,
    val nightId: String?,
    val fixedId: String?,
    val legacyMode: String?,
)

/**
 * The default theme and the move to it. Plain Kotlin, unit-tested (ThemeDefaultsTest).
 *
 * New installs start on Crimson Drive (Ampli MP3's Car mode colours and the car on Home), all day.
 * Updated installs that never touched the theme picker move to it too, because they were only
 * ever showing the old default: first Ampli Rose / Daylight Clean, then "Soft: auto Day/Night".
 * Anybody who picked something keeps it, "Soft: auto Day/Night" included: the picker always writes
 * the theme keys, so a stored key means a choice. Nothing is written by the move itself.
 */
object ThemeDefaults {
    /** Crimson Drive: the default since the Car mode look. */
    const val CRIMSON = "crimson_drive"

    /** Ampli Soft Day / Night: the default pair before Crimson Drive; still the Auto fallbacks. */
    const val SOFT_DAY = "ampli_soft_day"
    const val SOFT_NIGHT = "ampli_soft_night"

    /** The defaults before the soft look (still free themes). */
    const val ROSE = "ampli_rose"
    const val DAYLIGHT = "daylight_clean"

    /** "Soft: auto Day/Night". */
    val SOFT_AUTO = ThemeChoice(auto = true, dayId = SOFT_DAY, nightId = SOFT_NIGHT, fixedId = SOFT_NIGHT)

    /**
     * The default: Crimson Drive day and night (Auto off). Turning Auto on later gives the soft
     * Day/Night pair, so the Day/Night slots hold something that suits each half of the day.
     */
    val CRIMSON_DEFAULT = ThemeChoice(auto = false, dayId = SOFT_DAY, nightId = SOFT_NIGHT, fixedId = CRIMSON)

    /** True when no theme setting was ever saved (a new install, or an install on the old default). */
    fun neverChosen(s: StoredTheme): Boolean =
        s.auto == null && s.dayId == null && s.nightId == null && s.fixedId == null &&
            (s.legacyMode == null || s.legacyMode == "Auto")

    /** The choice to use for what is stored. */
    fun choiceFor(s: StoredTheme): ThemeChoice {
        if (neverChosen(s)) return CRIMSON_DEFAULT
        // A stored choice keeps the meaning it had: missing slots get the defaults of that time.
        val legacyFixed = if (s.legacyMode == "Day") DAYLIGHT else ROSE
        return ThemeChoice(
            auto = s.auto ?: (s.legacyMode == null || s.legacyMode == "Auto"),
            dayId = s.dayId ?: DAYLIGHT,
            nightId = s.nightId ?: ROSE,
            fixedId = s.fixedId ?: legacyFixed,
        )
    }

    /** True when [c] shows Crimson Drive all day (Auto off, Crimson Drive in the fixed slot). */
    fun isCrimson(c: ThemeChoice): Boolean = !c.auto && c.fixedId == CRIMSON

    /** The choice after tapping Crimson Drive at the top of the picker (Day/Night slots kept). */
    fun crimson(c: ThemeChoice): ThemeChoice = c.copy(auto = false, fixedId = CRIMSON)

    /** True when [c] is "Soft: auto Day/Night". */
    fun isSoftAuto(c: ThemeChoice): Boolean = c.auto && c.dayId == SOFT_DAY && c.nightId == SOFT_NIGHT

    /** The choice after tapping "Soft: auto Day/Night" (the fixed slot is kept for "Auto" off). */
    fun softAuto(c: ThemeChoice): ThemeChoice = c.copy(auto = true, dayId = SOFT_DAY, nightId = SOFT_NIGHT)
}
