package com.vikash.ampli.drive.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleLogicTest {
    @Test
    fun redOnlyAboveTheLimit() {
        assertTrue(BubbleLogic.model(81.4f, 80, 300).over)
        assertFalse(BubbleLogic.model(80f, 80, 300).over)
        assertFalse(BubbleLogic.model(120f, null, null).over)
        assertNull(BubbleLogic.model(null, 80, 300).speed)
        assertEquals(81, BubbleLogic.model(81.4f, 80, 300).speed)
    }

    @Test
    fun distances() {
        assertEquals("450 m", BubbleLogic.distanceText(456))
        assertEquals("1.2 km", BubbleLogic.distanceText(1240))
    }

    @Test
    fun clampKeepsBubbleOnScreen() {
        assertEquals(904 to 200, BubbleLogic.clamp(-1, -1, 120, 120, 1024, 600))
        assertEquals(0 to 0, BubbleLogic.clamp(-0, 0, 120, 120, 1024, 600))
        assertEquals(904 to 480, BubbleLogic.clamp(5000, 5000, 120, 120, 1024, 600))
    }

    @Test
    fun smallMovesAreTaps() {
        assertTrue(BubbleLogic.isTap(3f, -4f))
        assertFalse(BubbleLogic.isTap(30f, 0f))
    }
}
