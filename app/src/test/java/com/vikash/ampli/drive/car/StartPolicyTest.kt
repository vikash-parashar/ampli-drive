package com.vikash.ampli.drive.car

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartPolicyTest {

    @Test
    fun runsOncePerStart() {
        assertTrue(StartPolicy.shouldRun(0L, 1_000_000L))
        assertFalse(StartPolicy.shouldRun(1_000_000L, 1_000_000L + 60_000L))
        assertTrue(StartPolicy.shouldRun(1_000_000L, 1_000_000L + StartPolicy.MIN_GAP_MS))
        // Clock moved backwards (no RTC battery on many units): run.
        assertTrue(StartPolicy.shouldRun(5_000_000L, 1_000_000L))
    }

    @Test
    fun bootDetection() {
        assertTrue(StartPolicy.isNewBoot(lastBootCount = 41, bootCount = 42, uptimeMs = 3_600_000L))
        assertFalse(StartPolicy.isNewBoot(lastBootCount = 42, bootCount = 42, uptimeMs = 10_000L))
        // No BOOT_COUNT on this ROM: a fresh uptime means a boot.
        assertTrue(StartPolicy.isNewBoot(lastBootCount = -1, bootCount = -1, uptimeMs = 60_000L))
        assertFalse(StartPolicy.isNewBoot(lastBootCount = -1, bootCount = -1, uptimeMs = 3_600_000L))
    }

    @Test
    fun phoneConnectDoesNotGreet() {
        assertTrue(StartPolicy.greets(StartTrigger.Boot))
        assertTrue(StartPolicy.greets(StartTrigger.Ignition))
        assertFalse(StartPolicy.greets(StartTrigger.PhoneConnected))
    }
}
