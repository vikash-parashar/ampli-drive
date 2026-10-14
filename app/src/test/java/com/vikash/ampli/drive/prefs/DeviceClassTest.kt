package com.vikash.ampli.drive.prefs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceClassTest {

    private val gib = 1024L * 1024L * 1024L

    @Test
    fun lowRamFlagAlwaysCounts() {
        assertTrue(DeviceClass.isLowEnd(isLowRamDevice = true, totalMemBytes = 8 * gib))
    }

    @Test
    fun twoGigabyteUnitsAreLowEnd() {
        // What "2 GB" head units actually report.
        assertTrue(DeviceClass.isLowEnd(isLowRamDevice = false, totalMemBytes = (1.85 * gib).toLong()))
        assertTrue(DeviceClass.isLowEnd(isLowRamDevice = false, totalMemBytes = 2 * gib))
        assertTrue(DeviceClass.isLowEnd(isLowRamDevice = false, totalMemBytes = 1 * gib))
    }

    @Test
    fun threeGigabytesAndUpAreNot() {
        assertFalse(DeviceClass.isLowEnd(isLowRamDevice = false, totalMemBytes = (2.75 * gib).toLong()))
        assertFalse(DeviceClass.isLowEnd(isLowRamDevice = false, totalMemBytes = 4 * gib))
    }

    @Test
    fun unknownMemoryIsNotLowEnd() {
        assertFalse(DeviceClass.isLowEnd(isLowRamDevice = false, totalMemBytes = 0))
    }
}
