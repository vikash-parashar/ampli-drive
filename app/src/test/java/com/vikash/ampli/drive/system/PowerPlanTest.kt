package com.vikash.ampli.drive.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerPlanTest {

    @Test
    fun rootComesBeforeTheHelper() {
        assertEquals(listOf(PowerRoute.Root, PowerRoute.Helper), PowerPlan.routes(rootAvailable = true, helperOn = true))
        assertEquals(listOf(PowerRoute.Root), PowerPlan.routes(rootAvailable = true, helperOn = false))
        assertEquals(listOf(PowerRoute.Helper), PowerPlan.routes(rootAvailable = false, helperOn = true))
        assertTrue(PowerPlan.routes(rootAvailable = false, helperOn = false).isEmpty())
    }

    @Test
    fun firstStep() {
        // Screen off never waits and never needs setup.
        for (root in listOf(true, false)) for (helper in listOf(true, false)) {
            assertEquals(PowerStep.Now, PowerPlan.firstStep(PowerAction.ScreenOff, root, helper, rootNoticeSeen = false))
        }
        for (a in listOf(PowerAction.Restart, PowerAction.PowerOff)) {
            assertEquals(PowerStep.SetupHelper, PowerPlan.firstStep(a, rootAvailable = false, helperOn = false, rootNoticeSeen = false))
            assertEquals(PowerStep.Countdown, PowerPlan.firstStep(a, rootAvailable = false, helperOn = true, rootNoticeSeen = false))
            assertEquals(PowerStep.RootNotice, PowerPlan.firstStep(a, rootAvailable = true, helperOn = false, rootNoticeSeen = false))
            assertEquals(PowerStep.Countdown, PowerPlan.firstStep(a, rootAvailable = true, helperOn = false, rootNoticeSeen = true))
        }
    }

    @Test
    fun rootCommands() {
        assertEquals("reboot", PowerPlan.rootCommand(PowerAction.Restart))
        assertEquals("reboot -p", PowerPlan.rootCommand(PowerAction.PowerOff))
        assertNull(PowerPlan.rootCommand(PowerAction.ScreenOff))
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
        assertEquals(5, PowerPlan.COUNTDOWN_S)
        assertEquals(5, PowerPlan.secondsLeft(5, 0))
        assertEquals(5, PowerPlan.secondsLeft(5, 999))
        assertEquals(4, PowerPlan.secondsLeft(5, 1_000))
        assertEquals(1, PowerPlan.secondsLeft(5, 4_500))
        assertEquals(0, PowerPlan.secondsLeft(5, 5_000))
        assertEquals(0, PowerPlan.secondsLeft(5, 60_000))
        assertEquals(5, PowerPlan.secondsLeft(5, -20))
    }
}
