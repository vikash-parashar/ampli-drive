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
 * New installs start on "Soft: auto Day/Night": Ampli Soft Day from sunrise to sunset and
 * Ampli Soft Night after dark (SunTimes from the GPS position, or the clock before the first fix).
 * Updated installs that never touched the theme picker move to it too, because they were only
 * ever showing the old default. Anybody who picked something keeps it: the picker always writes
 * the theme keys, so a stored key means a choice.
 */
object ThemeDefaults {
    const val SOFT_DAY = "ampli_soft_day"
    const val SOFT_NIGHT = "ampli_soft_night"

    /** The defaults before the soft look (still free themes). */
    const val ROSE = "ampli_rose"
    const val DAYLIGHT = "daylight_clean"

    /** "Soft: auto Day/Night". */
    val SOFT_AUTO = ThemeChoice(auto = true, dayId = SOFT_DAY, nightId = SOFT_NIGHT, fixedId = SOFT_NIGHT)

    /** True when no theme setting was ever saved (a new install, or an install on the old default). */
    fun neverChosen(s: StoredTheme): Boolean =
        s.auto == null && s.dayId == null && s.nightId == null && s.fixedId == null &&
            (s.legacyMode == null || s.legacyMode == "Auto")

    /** The choice to use for what is stored. */
    fun choiceFor(s: StoredTheme): ThemeChoice {
        if (neverChosen(s)) return SOFT_AUTO
        // A stored choice keeps the meaning it had: missing slots get the defaults of that time.
        val legacyFixed = if (s.legacyMode == "Day") DAYLIGHT else ROSE
        return ThemeChoice(
            auto = s.auto ?: (s.legacyMode == null || s.legacyMode == "Auto"),
            dayId = s.dayId ?: DAYLIGHT,
            nightId = s.nightId ?: ROSE,
            fixedId = s.fixedId ?: legacyFixed,
        )
    }

    /** True when [c] is "Soft: auto Day/Night". */
    fun isSoftAuto(c: ThemeChoice): Boolean = c.auto && c.dayId == SOFT_DAY && c.nightId == SOFT_NIGHT

    /** The choice after tapping "Soft: auto Day/Night" (the fixed slot is kept for "Auto" off). */
    fun softAuto(c: ThemeChoice): ThemeChoice = c.copy(auto = true, dayId = SOFT_DAY, nightId = SOFT_NIGHT)
}
