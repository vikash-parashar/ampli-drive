package com.vikash.ampli.drive.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartLogTest {

    @Test
    fun keepsTheNewestTen() {
        var list = emptyList<StartEntry>()
        for (i in 1..15) list = StartLogRules.add(list, StartEntry(i.toLong(), "boot $i", i % 2 == 0, ""))
        assertEquals(StartLogRules.MAX, list.size)
        assertEquals("boot 15", list.first().trigger)
        assertEquals("boot 6", list.last().trigger)
    }

    @Test
    fun survivesEncodeAndDecode() {
        val list = listOf(
            StartEntry(1_700_000_000_000L, "boot (boot count 42)", true, ""),
            StartEntry(1_700_000_100_000L, "broadcast ACC_ON", false, "audio output was not ready | tried twice\nagain"),
        )
        val back = StartLogRules.decode(StartLogRules.encode(list))
        assertEquals(2, back.size)
        assertEquals(list[0], back[0])
        assertEquals("broadcast ACC_ON", back[1].trigger)
        assertEquals(false, back[1].greeted)
        assertTrue(!back[1].reason.contains('\n'))
    }

    @Test
    fun badTextGivesAnEmptyLog() {
        assertTrue(StartLogRules.decode(null).isEmpty())
        assertTrue(StartLogRules.decode("").isEmpty())
        assertTrue(StartLogRules.decode("garbage\nmore|garbage").isEmpty())
    }
}
