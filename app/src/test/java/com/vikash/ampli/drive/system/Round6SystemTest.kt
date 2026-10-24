package com.vikash.ampli.drive.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouriteCallsLogicTest {

    @Test
    fun fourAtMostAndTheSameNumberReplaces() {
        var list = emptyList<FavouriteContact>()
        for (n in listOf("Papa" to "+91 98123 45678", "Mummy" to "9812300000", "Bhai" to "9812311111", "Didi" to "9812322222")) {
            list = FavouriteCallsLogic.add(list, FavouriteContact(n.first, n.second))!!
        }
        assertNull(FavouriteCallsLogic.add(list, FavouriteContact("Chacha", "9812333333")))
        val renamed = FavouriteCallsLogic.add(list, FavouriteContact("Pitaji", "098123 45678"))!!
        assertEquals(4, renamed.size)
        assertEquals("Pitaji", renamed.first().name)
        assertEquals("P", renamed.first().initial)
    }

    @Test
    fun blankNameFallsBackToTheNumber() {
        val list = FavouriteCallsLogic.add(emptyList(), FavouriteContact("  ", "100"))!!
        assertEquals("100", list.single().name)
    }
}

class StartupAndMemoryTest {

    @Test
    fun coldStartIsMeasuredFromProcessStart() {
        val r = StartupTiming.measure(processStart = 1_000L, activityCreated = 1_400L, firstFrame = 2_900L)
        assertEquals(StartupTiming.Kind.Cold, r.kind)
        assertEquals(1_900L, r.ms)
    }

    @Test
    fun processStartedLongBeforeTheActivityCountsAsWarm() {
        // Drive monitor started the process at boot; Home opened a minute later.
        val r = StartupTiming.measure(processStart = 1_000L, activityCreated = 61_000L, firstFrame = 61_700L)
        assertEquals(StartupTiming.Kind.Warm, r.kind)
        assertEquals(700L, r.ms)
    }

    @Test
    fun cachesAreReleasedFromUiHiddenUp() {
        assertFalse(MemoryTrim.shouldRelease(5)) // RUNNING_MODERATE
        assertFalse(MemoryTrim.shouldRelease(15)) // RUNNING_CRITICAL
        assertTrue(MemoryTrim.shouldRelease(20)) // UI_HIDDEN
        assertTrue(MemoryTrim.shouldRelease(80)) // COMPLETE
    }
}
