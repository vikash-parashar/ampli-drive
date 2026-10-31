package com.vikash.ampli.drive.prefs

import com.vikash.ampli.drive.ui.theme.ThemeAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The move to Crimson Drive (before it, Ampli Soft Day/Night), and the one-tap picker choices. */
class ThemeDefaultsTest {

    private val none = StoredTheme(auto = null, dayId = null, nightId = null, fixedId = null, legacyMode = null)
    private val known = setOf(
        ThemeDefaults.CRIMSON, ThemeDefaults.SOFT_DAY, ThemeDefaults.SOFT_NIGHT, ThemeDefaults.ROSE, ThemeDefaults.DAYLIGHT,
        "frosted_glass", "cyan_glass", "soft_mint",
    )

    @Test
    fun newInstallStartsOnCrimsonDrive() {
        val c = ThemeDefaults.choiceFor(none)
        assertEquals(ThemeDefaults.CRIMSON_DEFAULT, c)
        assertTrue(ThemeDefaults.isCrimson(c))
        assertFalse(ThemeDefaults.isSoftAuto(c))
        // Day and night alike.
        assertEquals(ThemeDefaults.CRIMSON, ThemeAccess.resolveId(c, night = false, isPro = false, known = known))
        assertEquals(ThemeDefaults.CRIMSON, ThemeAccess.resolveId(c, night = true, isPro = false, known = known))
    }

    @Test
    fun crimsonDriveIsFree() {
        assertTrue(ThemeAccess.isFree(ThemeDefaults.CRIMSON))
        assertEquals(ThemeDefaults.CRIMSON, DrivePrefs.DEFAULT_FIXED)
    }

    /**
     * Anyone still on the untouched "Soft: auto Day/Night" default (PR #23's builds) has no theme key
     * stored, because that default was never written: they move to Crimson Drive like new installs.
     */
    @Test
    fun untouchedSoftDefaultMovesToCrimson() {
        assertEquals(ThemeDefaults.CRIMSON_DEFAULT, ThemeDefaults.choiceFor(none))
    }

    /** Somebody who tapped "Soft: auto Day/Night" in the picker has it stored, and keeps it. */
    @Test
    fun pickedSoftAutoIsKept() {
        val stored = StoredTheme(auto = true, dayId = ThemeDefaults.SOFT_DAY, nightId = ThemeDefaults.SOFT_NIGHT, fixedId = ThemeDefaults.SOFT_NIGHT, legacyMode = null)
        val c = ThemeDefaults.choiceFor(stored)
        assertEquals(ThemeDefaults.SOFT_AUTO, c)
        assertTrue(ThemeDefaults.isSoftAuto(c))
        assertFalse(ThemeDefaults.isCrimson(c))
        assertEquals(ThemeDefaults.SOFT_DAY, ThemeAccess.resolveId(c, night = false, isPro = false, known = known))
        assertEquals(ThemeDefaults.SOFT_NIGHT, ThemeAccess.resolveId(c, night = true, isPro = false, known = known))
    }

    /** A soft theme picked as the fixed theme is kept too. */
    @Test
    fun pickedSoftNightFixedIsKept() {
        val stored = StoredTheme(auto = false, dayId = ThemeDefaults.SOFT_DAY, nightId = ThemeDefaults.SOFT_NIGHT, fixedId = ThemeDefaults.SOFT_NIGHT, legacyMode = null)
        val c = ThemeDefaults.choiceFor(stored)
        assertFalse(ThemeDefaults.isCrimson(c))
        assertEquals(ThemeDefaults.SOFT_NIGHT, ThemeAccess.resolveId(c, night = false, isPro = false, known = known))
    }

    /** Crimson Drive picked in the picker (stored) reads back as the same choice. */
    @Test
    fun pickedCrimsonIsKept() {
        val stored = StoredTheme(auto = false, dayId = "cyan_glass", nightId = ThemeDefaults.ROSE, fixedId = ThemeDefaults.CRIMSON, legacyMode = null)
        val c = ThemeDefaults.choiceFor(stored)
        assertTrue(ThemeDefaults.isCrimson(c))
        assertEquals("cyan_glass", c.dayId)
    }

    @Test
    fun choosingCrimsonTurnsAutoOffAndKeepsSlots() {
        val auto = ThemeChoice(auto = true, dayId = "cyan_glass", nightId = "frosted_glass", fixedId = "frosted_glass")
        val c = ThemeDefaults.crimson(auto)
        assertTrue(ThemeDefaults.isCrimson(c))
        assertEquals("cyan_glass", c.dayId)
        assertEquals("frosted_glass", c.nightId)
        assertEquals(ThemeDefaults.CRIMSON, ThemeAccess.resolveId(c, night = true, isPro = false, known = known))
        // And "Soft: auto Day/Night" from there brings the soft pair back.
        assertTrue(ThemeDefaults.isSoftAuto(ThemeDefaults.softAuto(c)))
    }

    /** Turning Auto on from the default gives the soft Day/Night pair. */
    @Test
    fun autoFromDefaultIsTheSoftPair() {
        val c = ThemeDefaults.CRIMSON_DEFAULT.copy(auto = true)
        assertTrue(ThemeDefaults.isSoftAuto(c))
    }

    /** A Pro theme without Pro, with Auto off, falls back to Crimson Drive. */
    @Test
    fun fixedProFallbackIsCrimson() {
        val c = ThemeChoice(auto = false, dayId = "", nightId = "", fixedId = "cyan_glass")
        assertEquals(ThemeDefaults.CRIMSON, ThemeAccess.resolveId(c, night = true, isPro = false, known = known))
        assertEquals(ThemeDefaults.CRIMSON, ThemeAccess.resolveId(c, night = false, isPro = false, known = known))
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
    fun untouchedUpdateMovesToCrimson() {
        assertEquals(ThemeDefaults.CRIMSON_DEFAULT, ThemeDefaults.choiceFor(none.copy(legacyMode = "Auto")))
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

    /** A Pro theme without Pro, with Auto on, falls back to the soft pair for that time of day. */
    @Test
    fun proFallbackIsSoft() {
        val c = ThemeChoice(auto = true, dayId = "cyan_glass", nightId = "cyan_glass", fixedId = "")
        assertEquals(ThemeDefaults.SOFT_DAY, ThemeAccess.resolveId(c, night = false, isPro = false, known = known))
        assertEquals(ThemeDefaults.SOFT_NIGHT, ThemeAccess.resolveId(c, night = true, isPro = false, known = known))
    }
}
