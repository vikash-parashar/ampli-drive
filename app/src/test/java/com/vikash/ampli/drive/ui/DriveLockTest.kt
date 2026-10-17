package com.vikash.ampli.drive.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveLockTest {
    @Test
    fun speedDecidesWhenKnown() {
        assertTrue(DriveLock.moving(40f, 0L, 1_000L))
        assertFalse(DriveLock.moving(3f, 900L, 1_000L))
    }

    @Test
    fun missingSpeedRightAfterMovingIsMoving() {
        assertTrue(DriveLock.moving(null, lastMovingAtMs = 100_000L, nowMs = 160_000L))
    }

    @Test
    fun missingSpeedLongAfterMovingIsParked() {
        assertFalse(DriveLock.moving(null, lastMovingAtMs = 100_000L, nowMs = 100_000L + DriveLock.STALE_MS + 1))
    }

    @Test
    fun neverMovedStaysEditable() {
        assertFalse(DriveLock.moving(null, lastMovingAtMs = 0L, nowMs = 5_000L))
    }
}
