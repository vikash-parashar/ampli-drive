package com.vikash.ampli.drive.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerPlanTest {

    @Test
    fun onlyScreenOffIsOffered() {
        // Restart and Power off were removed on purpose.
        assertEquals(listOf(PowerAction.ScreenOff), PowerAction.entries.toList())
    }

    @Test
    fun suDetection() {
        assertTrue(PowerPlan.hasSu { it == "/system/xbin/su" })
        assertTrue(PowerPlan.hasSu { it == "/system/bin/su" })
        assertFalse(PowerPlan.hasSu { false })
    }

    @Test
    fun screenOffLocksOnlyWhenChosenAndGranted() {
        assertEquals(ScreenOffRoute.DeviceAdmin, PowerPlan.screenOffRoute(adminActive = true, adminWanted = true))
        assertEquals(ScreenOffRoute.Overlay, PowerPlan.screenOffRoute(adminActive = true, adminWanted = false))
        assertEquals(ScreenOffRoute.Overlay, PowerPlan.screenOffRoute(adminActive = false, adminWanted = true))
        assertEquals(ScreenOffRoute.Overlay, PowerPlan.screenOffRoute(adminActive = false, adminWanted = false))
    }

    @Test
    fun countdown() {
        assertEquals(5, PowerPlan.VOICE_SCREEN_OFF_S)
        assertEquals(5, PowerPlan.secondsLeft(5, 0))
        assertEquals(5, PowerPlan.secondsLeft(5, 999))
        assertEquals(4, PowerPlan.secondsLeft(5, 1_000))
        assertEquals(1, PowerPlan.secondsLeft(5, 4_500))
        assertEquals(0, PowerPlan.secondsLeft(5, 5_000))
        assertEquals(0, PowerPlan.secondsLeft(5, 60_000))
        assertEquals(5, PowerPlan.secondsLeft(5, -20))
    }
}
