package com.vikash.ampli.drive.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RailVolumeTest {

    @Test
    fun holdTiming() {
        assertEquals(400L, VolumeHold.delayBefore(1))
        assertEquals(130L, VolumeHold.delayBefore(2))
        assertEquals(130L, VolumeHold.delayBefore(9))
        assertEquals(1, VolumeHold.stepsAfter(0))
        assertEquals(1, VolumeHold.stepsAfter(399))
        assertEquals(2, VolumeHold.stepsAfter(400))
        assertEquals(2, VolumeHold.stepsAfter(529))
        assertEquals(3, VolumeHold.stepsAfter(530))
        // A one-second hold: the tap, the 400 ms repeat and four more at 130 ms.
        assertEquals(6, VolumeHold.stepsAfter(1_000))
        assertEquals(1_600L, VolumeHold.POPUP_MS)
    }

    @Test
    fun stepsClampAndUnmute() {
        val l = RailVolume.Level(9, 15, muted = true)
        val up = RailVolume.step(l, 1)
        assertEquals(10, up.level)
        assertFalse(up.muted)
        assertEquals(15, RailVolume.step(RailVolume.Level(15, 15, false), 1).level)
        assertEquals(0, RailVolume.step(RailVolume.Level(0, 15, false), -1).level)
        assertFalse(RailVolume.step(RailVolume.Level(0, 15, true), -1).muted)
    }

    @Test
    fun unmuteNeverStaysSilent() {
        assertEquals(7, RailVolume.unmuteLevel(7, 15))
        assertEquals(9, RailVolume.unmuteLevel(0, 15, remembered = 9))
        assertEquals(5, RailVolume.unmuteLevel(0, 15))
        assertEquals(1, RailVolume.unmuteLevel(0, 2))
    }

    @Test
    fun popupBar() {
        assertEquals(15, RailVolume.segments(15))
        assertEquals(15, RailVolume.segments(30))
        assertEquals(7, RailVolume.segments(7))
        assertEquals(9, RailVolume.filled(9, 15))
        assertEquals(15, RailVolume.filled(30, 30))
        assertEquals(8, RailVolume.filled(15, 30))
        assertEquals(0, RailVolume.filled(0, 30))
        assertEquals(0, RailVolume.filled(3, 0))
    }
}
