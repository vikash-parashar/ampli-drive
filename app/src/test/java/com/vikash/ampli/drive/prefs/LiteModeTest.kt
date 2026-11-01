package com.vikash.ampli.drive.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteModeTest {

    @Test
    fun autoFollowsTheDeviceClass() {
        assertTrue(LiteMode.resolve(LiteMode.Choice.Auto, lowEnd = true))
        assertFalse(LiteMode.resolve(LiteMode.Choice.Auto, lowEnd = false))
    }

    @Test
    fun forcedChoiceWinsOverTheDeviceClass() {
        assertTrue(LiteMode.resolve(LiteMode.Choice.On, lowEnd = false))
        assertFalse(LiteMode.resolve(LiteMode.Choice.Off, lowEnd = true))
    }

    @Test
    fun switchCyclesThroughAllThree() {
        var c = LiteMode.Choice.Auto
        val seen = mutableListOf<LiteMode.Choice>()
        repeat(3) {
            c = LiteMode.next(c)
            seen += c
        }
        assertEquals(listOf(LiteMode.Choice.On, LiteMode.Choice.Off, LiteMode.Choice.Auto), seen)
    }

    @Test
    fun storedKeysReadBackAndUnknownIsAuto() {
        for (c in LiteMode.Choice.entries) assertEquals(c, LiteMode.Choice.from(c.key))
        assertEquals(LiteMode.Choice.Auto, LiteMode.Choice.from(null))
        assertEquals(LiteMode.Choice.Auto, LiteMode.Choice.from("turbo"))
    }

    @Test
    fun offUntilTheAppDecides() {
        // Screen previews and Robolectric tests never run DriveApp's decision: they draw as designed.
        assertFalse(LiteMode.enabled)
    }
}
