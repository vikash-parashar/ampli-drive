package com.vikash.ampli.drive.ui.theme

import com.vikash.ampli.drive.prefs.ThemeChoice
import com.vikash.ampli.drive.prefs.ThemeDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Free / Pro: only Map First is free; Big Glance, Bento Grid and Road Sign need Pro. */
class ThemeAccessTest {

    @Test
    fun onlyMapFirstIsFree() {
        assertEquals(setOf(ThemeDefaults.MAP_FIRST), ThemeAccess.FREE_IDS)
        for (id in listOf(ThemeDefaults.BIG_GLANCE, ThemeDefaults.BENTO, ThemeDefaults.ROAD_SIGN, ThemeDefaults.CALM)) {
            assertFalse("$id should be Pro", ThemeAccess.isFree(id))
        }
    }

    @Test
    fun proThemesAreLockedWithoutPro() {
        assertFalse(ThemeAccess.locked(ThemeDefaults.MAP_FIRST, isPro = false))
        for (id in listOf(ThemeDefaults.BIG_GLANCE, ThemeDefaults.BENTO, ThemeDefaults.ROAD_SIGN, ThemeDefaults.CALM)) {
            assertTrue(ThemeAccess.locked(id, isPro = false))
            assertFalse(ThemeAccess.locked(id, isPro = true))
        }
    }

    @Test
    fun aProThemeWithoutProFallsBackToMapFirst() {
        val bento = ThemeChoice(ThemeDefaults.BENTO)
        assertEquals(ThemeDefaults.MAP_FIRST, ThemeAccess.resolveId(bento, night = false, isPro = false))
        assertEquals(ThemeDefaults.BENTO, ThemeAccess.resolveId(bento, night = false, isPro = true))
    }

    @Test
    fun unknownIdShowsMapFirst() {
        assertEquals(ThemeDefaults.MAP_FIRST, ThemeAccess.resolveId(ThemeChoice("crimson_drive"), night = false, isPro = true))
    }

    @Test
    fun autoNightIsProOnly() {
        assertFalse(ThemeAccess.autoNightAvailable(isPro = false))
        assertTrue(ThemeAccess.autoNightAvailable(isPro = true))
        val choice = ThemeChoice(ThemeDefaults.ROAD_SIGN, autoNight = true)
        assertEquals(ThemeDefaults.BIG_GLANCE, ThemeAccess.resolveId(choice, night = true, isPro = true))
        assertEquals(ThemeDefaults.ROAD_SIGN, ThemeAccess.resolveId(choice, night = false, isPro = true))
        // Free: Map First day and night, even if the switch was left on from a refunded Pro.
        val free = ThemeChoice(ThemeDefaults.MAP_FIRST, autoNight = true)
        assertEquals(ThemeDefaults.MAP_FIRST, ThemeAccess.resolveId(free, night = true, isPro = false))
    }

    @Test
    fun autoNightOffKeepsTheChoiceAtNight() {
        val choice = ThemeChoice(ThemeDefaults.BENTO, autoNight = false)
        assertEquals(ThemeDefaults.BENTO, ThemeAccess.resolveId(choice, night = true, isPro = true))
    }

    @Test
    fun selectKeepsAutoNight() {
        val c = ThemeAccess.select(ThemeChoice(ThemeDefaults.MAP_FIRST, autoNight = true), ThemeDefaults.BENTO)
        assertEquals(ThemeChoice(ThemeDefaults.BENTO, autoNight = true), c)
    }
}
