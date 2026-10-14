package com.vikash.ampli.drive.ui.theme

import com.vikash.ampli.drive.prefs.DrivePrefs
import com.vikash.ampli.drive.prefs.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeAccessTest {

    private val known = setOf(
        "midnight_glass", DrivePrefs.DEFAULT_NIGHT, DrivePrefs.DEFAULT_DAY, "sport_red",
        ThemeAccess.FROSTED_GLASS, ThemeAccess.MIDNIGHT_FROST, "rose_glass", "cyan_glass",
        ThemeAccess.SAFFRON_NIGHT, "saffron_day", "soft_glass",
    )

    @Test
    fun exactlyFiveFreeThemes() {
        assertEquals(
            setOf("ampli_rose", "daylight_clean", "frosted_glass", "midnight_frost", "desi_saffron"),
            ThemeAccess.FREE_IDS,
        )
    }

    @Test
    fun otherThemesArePro() {
        for (id in listOf("midnight_glass", "sport_red", "rose_glass", "cyan_glass", "violet_glass", "sunset_glass", "saffron_day", "soft_glass")) {
            assertFalse(id, ThemeAccess.isFree(id))
        }
    }

    @Test
    fun freeThemesShowWithoutPro() {
        val auto = ThemeChoice(auto = true, dayId = ThemeAccess.FROSTED_GLASS, nightId = ThemeAccess.SAFFRON_NIGHT, fixedId = "")
        assertEquals(ThemeAccess.FROSTED_GLASS, ThemeAccess.resolveId(auto, night = false, isPro = false, known = known))
        assertEquals(ThemeAccess.SAFFRON_NIGHT, ThemeAccess.resolveId(auto, night = true, isPro = false, known = known))
        val fixed = ThemeChoice(auto = false, dayId = "", nightId = "", fixedId = ThemeAccess.MIDNIGHT_FROST)
        assertEquals(ThemeAccess.MIDNIGHT_FROST, ThemeAccess.resolveId(fixed, night = false, isPro = false, known = known))
    }

    @Test
    fun proThemeWithoutProFallsBackToFreeDefaultForTimeOfDay() {
        val auto = ThemeChoice(auto = true, dayId = "cyan_glass", nightId = "rose_glass", fixedId = "")
        assertEquals(DrivePrefs.DEFAULT_DAY, ThemeAccess.resolveId(auto, night = false, isPro = false, known = known))
        assertEquals(DrivePrefs.DEFAULT_NIGHT, ThemeAccess.resolveId(auto, night = true, isPro = false, known = known))
        val fixed = ThemeChoice(auto = false, dayId = "", nightId = "", fixedId = "sport_red")
        assertEquals(DrivePrefs.DEFAULT_NIGHT, ThemeAccess.resolveId(fixed, night = false, isPro = false, known = known))
    }

    @Test
    fun proThemeWithProIsKept() {
        val auto = ThemeChoice(auto = true, dayId = "cyan_glass", nightId = "rose_glass", fixedId = "")
        assertEquals("cyan_glass", ThemeAccess.resolveId(auto, night = false, isPro = true, known = known))
        assertEquals("rose_glass", ThemeAccess.resolveId(auto, night = true, isPro = true, known = known))
    }

    @Test
    fun unknownIdFallsBack() {
        val auto = ThemeChoice(auto = true, dayId = "removed_theme", nightId = "removed_theme", fixedId = "")
        assertEquals(DrivePrefs.DEFAULT_DAY, ThemeAccess.resolveId(auto, night = false, isPro = true, known = known))
        assertEquals(DrivePrefs.DEFAULT_NIGHT, ThemeAccess.resolveId(auto, night = true, isPro = true, known = known))
    }

    @Test
    fun defaultsAreFree() {
        assertTrue(ThemeAccess.isFree(DrivePrefs.DEFAULT_DAY))
        assertTrue(ThemeAccess.isFree(DrivePrefs.DEFAULT_NIGHT))
    }

    /** Head-unit report: with Pro on, picking a Pro theme must change the theme on screen. */
    @Test
    fun proSelectionShowsInFixedMode() {
        val fixed = ThemeChoice(auto = false, dayId = DrivePrefs.DEFAULT_DAY, nightId = DrivePrefs.DEFAULT_NIGHT, fixedId = DrivePrefs.DEFAULT_NIGHT)
        for (night in listOf(false, true)) {
            val picked = ThemeAccess.select(fixed, "cyan_glass", forNight = night)
            assertEquals("cyan_glass", ThemeAccess.resolveId(picked, night = night, isPro = true, known = known))
        }
    }

    @Test
    fun proSelectionShowsInAutoModeForTheSlotShowingNow() {
        val auto = ThemeChoice(auto = true, dayId = DrivePrefs.DEFAULT_DAY, nightId = DrivePrefs.DEFAULT_NIGHT, fixedId = "")
        for (night in listOf(false, true)) {
            // The picker's chip points at the slot showing now.
            val picked = ThemeAccess.select(auto, "cyan_glass", forNight = night)
            assertEquals("cyan_glass", ThemeAccess.resolveId(picked, night = night, isPro = true, known = known))
            // And the other slot keeps its own theme.
            assertEquals(
                if (night) DrivePrefs.DEFAULT_DAY else DrivePrefs.DEFAULT_NIGHT,
                ThemeAccess.resolveId(picked, night = !night, isPro = true, known = known),
            )
        }
    }

    @Test
    fun proSelectionWithoutProFallsBack() {
        val fixed = ThemeChoice(auto = false, dayId = "", nightId = "", fixedId = DrivePrefs.DEFAULT_NIGHT)
        val picked = ThemeAccess.select(fixed, "cyan_glass", forNight = true)
        assertEquals(DrivePrefs.DEFAULT_NIGHT, ThemeAccess.resolveId(picked, night = true, isPro = false, known = known))
    }
}
