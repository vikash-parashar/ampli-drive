package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HaryanviLinesTest {

    @Test
    fun noImmediateRepeats() {
        for (seed in 0..20) {
            val picker = LinePicker(Random(seed))
            for (event in HaryanviLines.Event.entries.filter { !it.safety }) {
                var prev: HaryanviLines.Line? = null
                repeat(200) {
                    val line = picker.pick(event)!!
                    assertNotEquals("$event repeated", prev, line)
                    prev = line
                }
            }
        }
    }

    @Test
    fun everyVariantGetsUsed() {
        val picker = LinePicker(Random(7))
        for (event in HaryanviLines.Event.entries.filter { !it.safety }) {
            val seen = HashSet<HaryanviLines.Line>()
            repeat(300) { seen += picker.pick(event)!! }
            assertEquals(event.name, HaryanviLines.forEvent(event)!!.toSet(), seen)
        }
    }

    @Test
    fun safetyPromptsNeverUsePlayfulLines() {
        val picker = LinePicker()
        for (event in HaryanviLines.Event.entries.filter { it.safety }) {
            assertNull(HaryanviLines.forEvent(event))
            assertNull(picker.pick(event))
            assertFalse(HaryanviLines.allLines.containsKey(event))
        }
        assertTrue(HaryanviLines.Event.Sos.safety)
        assertTrue(HaryanviLines.Event.Crash.safety)
    }

    @Test
    fun threeToFiveVariantsPerEvent() {
        for ((event, lines) in HaryanviLines.allLines) {
            assertTrue("$event has ${lines.size}", lines.size in 3..5)
        }
    }

    @Test
    fun devanagariAndRomanHaveTheSamePlaceholders() {
        val ph = Regex("%\\d\\$[sd]")
        for ((event, lines) in HaryanviLines.allLines) {
            for (l in lines) {
                assertEquals("$event: ${l.roman}", ph.findAll(l.deva).map { it.value }.toSet(), ph.findAll(l.roman).map { it.value }.toSet())
                assertTrue("$event deva in Devanagari", l.deva.any { it in 'ऀ'..'ॿ' })
            }
        }
    }

    @Test
    fun familySafe() {
        val banned = listOf("beti", "teri beti", "chod", "chutiya", "saala", "sala", "kamina", "kutta", "gaand", "gand", "lund", "randi", "harami", "bhosdi")
        for ((_, lines) in HaryanviLines.allLines) {
            for (l in lines) {
                val words = l.roman.lowercase().split(Regex("[^a-z]+"))
                for (b in banned) {
                    assertFalse("\"${l.roman}\" contains $b", if (' ' in b) l.roman.lowercase().contains(b) else b in words)
                }
                assertFalse(l.deva.contains("बेटी"))
            }
        }
    }
}
