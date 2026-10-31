package com.vikash.ampli.drive.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The move to the four themes: every theme saved by an earlier build lands on Map First. */
class ThemeDefaultsTest {

    @Test
    fun exactlyFourThemes() {
        assertEquals(listOf("map_first", "big_glance", "bento_grid", "road_sign"), ThemeDefaults.IDS)
    }

    @Test
    fun newInstallStartsOnMapFirstWithAutoNightOff() {
        val c = ThemeDefaults.choiceFor(StoredTheme(id = null, autoNight = null))
        assertEquals(ThemeChoice(ThemeDefaults.MAP_FIRST, autoNight = false), c)
        assertEquals(ThemeDefaults.DEFAULT, c)
    }

    @Test
    fun oldThemesMoveToMapFirst() {
        // Crimson Drive, the Soft family, Glass, Saffron and Classic ids from earlier builds.
        val old = listOf(
            "crimson_drive", "ampli_soft_day", "ampli_soft_night", "soft_mint", "frosted_glass", "midnight_glass",
            "saffron_night", "ampli_rose", "daylight_clean", "sport_red", "synthwave", "minimal_mono",
        )
        for (id in old) {
            val stored = StoredTheme(id = null, autoNight = null, legacyIds = listOf(id))
            assertEquals(id, ThemeChoice(ThemeDefaults.MAP_FIRST, false), ThemeDefaults.choiceFor(stored))
            assertTrue(ThemeDefaults.migratedFromOld(stored))
            // Even if an old id somehow sits in the new key, it is not shown.
            assertEquals(ThemeDefaults.MAP_FIRST, ThemeDefaults.choiceFor(StoredTheme(id = id, autoNight = null)).id)
        }
    }

    @Test
    fun savedNewThemeIsKept() {
        for (id in ThemeDefaults.IDS) {
            assertEquals(ThemeChoice(id, autoNight = true), ThemeDefaults.choiceFor(StoredTheme(id = id, autoNight = true)))
            assertFalse(ThemeDefaults.migratedFromOld(StoredTheme(id = id, autoNight = false, legacyIds = listOf("crimson_drive"))))
        }
    }

    @Test
    fun nightThemeIsBigGlance() {
        assertEquals(ThemeDefaults.BIG_GLANCE, ThemeDefaults.NIGHT_ID)
    }
}
