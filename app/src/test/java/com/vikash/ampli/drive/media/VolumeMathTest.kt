package com.vikash.ampli.drive.media

import com.vikash.ampli.drive.voice.VoiceIntent.VolumeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeMathTest {
    @Test
    fun stepIsAboutATenth() {
        assertEquals(2, VolumeMath.step(15))
        assertEquals(3, VolumeMath.step(30))
        assertEquals(1, VolumeMath.step(7))
    }

    @Test
    fun upDownStayInRange() {
        assertEquals(9, VolumeMath.target(VolumeAction.Up, 7, 15))
        assertEquals(15, VolumeMath.target(VolumeAction.Up, 14, 15))
        assertEquals(0, VolumeMath.target(VolumeAction.Down, 1, 15))
        assertEquals(15, VolumeMath.target(VolumeAction.Max, 3, 15))
        assertNull(VolumeMath.target(VolumeAction.Mute, 3, 15))
        assertNull(VolumeMath.target(VolumeAction.Show, 3, 15))
    }

    @Test
    fun spokenLevelIsTenthsOrPercent() {
        assertEquals(8, VolumeMath.levelFor(5, 15)) // half of 15, rounded
        assertEquals(15, VolumeMath.levelFor(10, 15))
        assertEquals(0, VolumeMath.levelFor(0, 30))
        assertEquals(15, VolumeMath.levelFor(50, 30)) // "volume 50" is percent
        assertEquals(30, VolumeMath.levelFor(100, 30))
        assertEquals(8, VolumeMath.target(VolumeAction.Set, 0, 15, 5))
        assertEquals(5, VolumeMath.tenths(8, 15))
        assertEquals(10, VolumeMath.tenths(15, 15))
    }
}
