package com.vikash.ampli.drive.overlay

import com.vikash.ampli.drive.overlay.QuickStripLogic.Swipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickStripLogicTest {
    @Test
    fun stripCoversTheMiddleHalfAt1280x800() {
        // Typical 8.1 head unit: 1280 px wide at mdpi.
        val s = QuickStripLogic.strip(1280, 1f)
        assertEquals(320, s.x)
        assertEquals(640, s.width)
        assertEquals(14, s.height)
        // Corners (vendor status-bar buttons) stay free.
        assertFalse(s.contains(100f, 5f))
        assertFalse(s.contains(1200f, 5f))
        assertFalse(s.contains(319f, 5f))
        assertTrue(s.contains(320f, 5f))
        assertTrue(s.contains(959f, 13f))
        assertFalse(s.contains(960f, 5f))
        // Below the strip is the app's own content.
        assertFalse(s.contains(640f, 14f))
    }

    @Test
    fun stripScalesWithDensity() {
        val s = QuickStripLogic.strip(1024, 1.5f)
        assertEquals(256, s.x)
        assertEquals(512, s.width)
        assertEquals(21, s.height)
        assertEquals(1, QuickStripLogic.strip(1, 0.01f).height)
    }

    @Test
    fun opensOnlyPastTheThresholdDownwards() {
        val d = 1f
        assertEquals(Swipe.Pending, QuickStripLogic.classify(0f, 30f, d))
        assertEquals(Swipe.Pending, QuickStripLogic.classify(0f, 48f, d))
        assertEquals(Swipe.Open, QuickStripLogic.classify(0f, 49f, d))
        assertEquals(Swipe.Open, QuickStripLogic.classify(20f, 60f, d))
        // At 2x density 49 px is only 24.5 dp.
        assertEquals(Swipe.Pending, QuickStripLogic.classify(0f, 49f, 2f))
        assertEquals(Swipe.Open, QuickStripLogic.classify(0f, 97f, 2f))
    }

    @Test
    fun tapsSidewaysAndUpwardDoNotOpen() {
        val d = 1f
        assertEquals(Swipe.Pending, QuickStripLogic.classify(3f, 2f, d)) // a tap's jitter
        assertEquals(Swipe.Cancel, QuickStripLogic.classify(30f, 5f, d)) // sideways
        assertEquals(Swipe.Cancel, QuickStripLogic.classify(-30f, 20f, d))
        assertEquals(Swipe.Cancel, QuickStripLogic.classify(0f, -20f, d)) // upward
        // Diagonal but long: still needs to be mostly downward.
        assertEquals(Swipe.Pending, QuickStripLogic.classify(45f, 55f, d))
        assertEquals(Swipe.Cancel, QuickStripLogic.classify(80f, 60f, d))
    }

    @Test
    fun trackerOpensOnceAndStopsAfterCancel() {
        val t = QuickStripLogic.Tracker(1f)
        t.down(600f, 5f)
        assertEquals(Swipe.Pending, t.move(600f, 30f))
        assertEquals(Swipe.Open, t.move(602f, 60f))
        assertEquals(Swipe.Cancel, t.move(602f, 200f)) // already opened: no second open
        t.up()

        t.down(600f, 5f)
        assertEquals(Swipe.Cancel, t.move(650f, 8f)) // sideways first
        assertEquals(Swipe.Cancel, t.move(650f, 200f)) // then down: stays cancelled
        t.up()
        // Moves without a down are ignored.
        assertEquals(Swipe.Cancel, t.move(0f, 500f))
    }

    @Test
    fun closeSwipe() {
        assertFalse(QuickStripLogic.isCloseSwipe(-40f, 1f))
        assertTrue(QuickStripLogic.isCloseSwipe(-49f, 1f))
        assertFalse(QuickStripLogic.isCloseSwipe(60f, 1f))
    }

    @Test
    fun hiddenOnHubHomeCrashAndWhilePanelOpen() {
        assertTrue(QuickStripLogic.visible(true, true, false, false, hubInFront = false, hubScreenBlocks = true))
        // Car Hub's Home in front: it has its own pull-down.
        assertFalse(QuickStripLogic.visible(true, true, false, false, hubInFront = true, hubScreenBlocks = true))
        // Car Hub in front on another screen (Settings): the strip works there too.
        assertTrue(QuickStripLogic.visible(true, true, false, false, hubInFront = true, hubScreenBlocks = false))
        assertFalse(QuickStripLogic.visible(true, true, panelOpen = true, crash = false, hubInFront = false, hubScreenBlocks = false))
        assertFalse(QuickStripLogic.visible(true, true, panelOpen = false, crash = true, hubInFront = false, hubScreenBlocks = false))
        assertFalse(QuickStripLogic.visible(false, true, false, false, false, false))
        assertFalse(QuickStripLogic.visible(true, canOverlay = false, panelOpen = false, crash = false, hubInFront = false, hubScreenBlocks = false))
    }
}
