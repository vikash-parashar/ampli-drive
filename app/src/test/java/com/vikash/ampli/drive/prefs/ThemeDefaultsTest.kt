package com.vikash.ampli.drive.prefs

import com.vikash.ampli.drive.ui.theme.ThemeAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The move to Ampli Soft Day/Night, and the "Soft: auto Day/Night" choice. */
class ThemeDefaultsTest {

    private val none = StoredTheme(auto = null, dayId = null, nightId = null, fixedId = null, legacyMode = null)
    private val known = setOf(
        ThemeDefaults.SOFT_DAY, ThemeDefaults.SOFT_NIGHT, ThemeDefaults.ROSE, ThemeDefaults.DAYLIGHT,
        "frosted_glass", "cyan_glass", "soft_mint",
    )

    @Test
    fun newInstallStartsOnSoftAuto() {
        assertEquals(ThemeDefaults.SOFT_AUTO, ThemeDefaults.choiceFor(none))
        assertTrue(ThemeDefaults.isSoftAuto(ThemeDefaults.choiceFor(none)))
    }

    @Test
    fun defaultsAreTheSoftPair() {
        assertEquals(ThemeDefaults.SOFT_DAY, DrivePrefs.DEFAULT_DAY)
        assertEquals(ThemeDefaults.SOFT_NIGHT, DrivePrefs.DEFAULT_NIGHT)
        assertTrue(ThemeAccess.isFree(ThemeDefaults.SOFT_DAY))
        assertTrue(ThemeAccess.isFree(ThemeDefaults.SOFT_NIGHT))
    }

    /** An updated install that never opened the picker was only showing the old default: it moves. */
    @Test
    fun untouchedUpdateMovesToSoft() {
        assertEquals(ThemeDefaults.SOFT_AUTO, ThemeDefaults.choiceFor(none.copy(legacyMode = "Auto")))
    }

    @Test
    fun earliestBuildsDayOrNightChoiceIsKept() {
        val day = ThemeDefaults.choiceFor(none.copy(legacyMode = "Day"))
        assertFalse(day.auto)
        assertEquals(ThemeDefaults.DAYLIGHT, day.fixedId)
        val night = ThemeDefaults.choiceFor(none.copy(legacyMode = "Night"))
        assertFalse(night.auto)
        assertEquals(ThemeDefaults.ROSE, night.fixedId)
    }

    @Test
    fun pickedThemeIsKept() {
        val picked = StoredTheme(auto = false, dayId = ThemeDefaults.DAYLIGHT, nightId = ThemeDefaults.ROSE, fixedId = "frosted_glass", legacyMode = null)
        val c = ThemeDefaults.choiceFor(picked)
        assertEquals(ThemeChoice(false, ThemeDefaults.DAYLIGHT, ThemeDefaults.ROSE, "frosted_glass"), c)
        assertEquals("frosted_glass", ThemeAccess.resolveId(c, night = true, isPro = false, known = known))
    }

    /** Even a picker choice that equals the old defaults is a choice: it stays Daylight / Rose. */
    @Test
    fun explicitOldDefaultPairIsKept() {
        val picked = StoredTheme(auto = true, dayId = ThemeDefaults.DAYLIGHT, nightId = ThemeDefaults.ROSE, fixedId = ThemeDefaults.ROSE, legacyMode = null)
        val c = ThemeDefaults.choiceFor(picked)
        assertFalse(ThemeDefaults.isSoftAuto(c))
        assertEquals(ThemeDefaults.DAYLIGHT, ThemeAccess.resolveId(c, night = false, isPro = false, known = known))
        assertEquals(ThemeDefaults.ROSE, ThemeAccess.resolveId(c, night = true, isPro = false, known = known))
    }

    @Test
    fun partlyStoredChoiceFillsOldDefaults() {
        val c = ThemeDefaults.choiceFor(none.copy(nightId = "soft_mint"))
        assertTrue(c.auto)
        assertEquals(ThemeDefaults.DAYLIGHT, c.dayId)
        assertEquals("soft_mint", c.nightId)
    }

    @Test
    fun softAutoFollowsDayAndNight() {
        val c = ThemeDefaults.SOFT_AUTO
        assertEquals(ThemeDefaults.SOFT_DAY, ThemeAccess.resolveId(c, night = false, isPro = false, known = known))
        assertEquals(ThemeDefaults.SOFT_NIGHT, ThemeAccess.resolveId(c, night = true, isPro = false, known = known))
    }

    @Test
    fun choosingSoftAutoTurnsAutoOnAndKeepsFixedSlot() {
        val fixed = ThemeChoice(auto = false, dayId = "cyan_glass", nightId = "frosted_glass", fixedId = "frosted_glass")
        val c = ThemeDefaults.softAuto(fixed)
        assertTrue(ThemeDefaults.isSoftAuto(c))
        assertEquals("frosted_glass", c.fixedId)
        assertEquals(ThemeDefaults.SOFT_DAY, ThemeAccess.resolveId(c, night = false, isPro = false, known = known))
        assertEquals(ThemeDefaults.SOFT_NIGHT, ThemeAccess.resolveId(c, night = true, isPro = false, known = known))
    }

    @Test
    fun softAutoIsNotSetWhenOneSlotDiffers() {
        assertFalse(ThemeDefaults.isSoftAuto(ThemeDefaults.SOFT_AUTO.copy(nightId = "soft_mint")))
        assertFalse(ThemeDefaults.isSoftAuto(ThemeDefaults.SOFT_AUTO.copy(auto = false)))
    }

    /** A Pro theme without Pro falls back to the soft pair for that time of day. */
    @Test
    fun proFallbackIsSoft() {
        val c = ThemeChoice(auto = true, dayId = "cyan_glass", nightId = "cyan_glass", fixedId = "")
        assertEquals(ThemeDefaults.SOFT_DAY, ThemeAccess.resolveId(c, night = false, isPro = false, known = known))
        assertEquals(ThemeDefaults.SOFT_NIGHT, ThemeAccess.resolveId(c, night = true, isPro = false, known = known))
    }
}
