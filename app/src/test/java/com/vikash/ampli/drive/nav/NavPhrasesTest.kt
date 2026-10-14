package com.vikash.ampli.drive.nav

import com.vikash.ampli.drive.voice.HaryanviLines
import com.vikash.ampli.drive.voice.LinePicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NavPhrasesTest {

    private fun prompt(m: Maneuver, now: Boolean, d: Int?, exit: Int? = null) = NavPrompt(ParsedTurn(m, d, exit, "k"), now, d)

    @Test
    fun everyManeuverHasLines() {
        for (m in Maneuver.entries) {
            val e = NavPhrases.event(m, null)
            assertFalse(e.safety)
            assertNotNull(m.name, HaryanviLines.forEvent(e))
        }
        assertNotNull(HaryanviLines.forEvent(NavPhrases.event(Maneuver.Roundabout, 2)))
    }

    @Test
    fun farPromptCarriesTheDistance() {
        val picker = LinePicker(Random(1))
        repeat(20) {
            val deva = NavPhrases.desi(prompt(Maneuver.Left, false, 400), picker, roman = false)!!
            assertTrue(deva, deva.contains("400 मीटर"))
            val roman = NavPhrases.desi(prompt(Maneuver.Right, false, 1500), picker, roman = true)!!
            assertTrue(roman, roman.contains("1.5 kilometre"))
        }
    }

    @Test
    fun nowPromptHasNoDistance() {
        val picker = LinePicker(Random(2))
        val nowPrefixes = HaryanviLines.forEvent(HaryanviLines.Event.NavNow)!!.map { it.deva }
        repeat(20) {
            val s = NavPhrases.desi(prompt(Maneuver.UTurn, true, 100), picker, roman = false)!!
            assertTrue(s, nowPrefixes.any { s.startsWith(it) })
            assertFalse(s, s.contains("मीटर"))
        }
    }

    @Test
    fun roundaboutExitNumberIsFilledIn() {
        val picker = LinePicker(Random(3))
        repeat(10) {
            val s = NavPhrases.desi(prompt(Maneuver.Roundabout, false, 300, exit = 2), picker, roman = true)!!
            assertTrue(s, s.contains("2 number"))
            assertFalse(s, s.contains("%"))
        }
    }

    @Test
    fun arriveTakesNoPrefix() {
        val picker = LinePicker(Random(4))
        val lines = HaryanviLines.forEvent(HaryanviLines.Event.NavArrive)!!.map { it.deva }
        repeat(10) {
            val s = NavPhrases.desi(prompt(Maneuver.Arrive, false, 100), picker, roman = false)!!
            assertTrue(s, s in lines)
        }
    }

    @Test
    fun sameManeuverNeverRepeatsTheLineBackToBack() {
        val picker = LinePicker(Random(5))
        var prev: String? = null
        repeat(100) {
            val s = NavPhrases.desi(prompt(Maneuver.Left, true, 100), picker, roman = false)!!
            val body = HaryanviLines.forEvent(HaryanviLines.Event.NavLeft)!!.first { s.endsWith(it.deva) }.deva
            assertNotEquals(prev, body)
            prev = body
        }
    }

    @Test
    fun distances() {
        assertEquals("400 मीटर", NavPhrases.distance(400, roman = false))
        assertEquals("2 kilometre", NavPhrases.distance(2000, roman = true))
        assertEquals("1.5 किलोमीटर", NavPhrases.distance(1500, roman = false))
    }
}
