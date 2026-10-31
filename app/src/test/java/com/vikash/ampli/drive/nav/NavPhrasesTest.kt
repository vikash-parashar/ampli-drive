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

    private fun hinted(m: Maneuver, now: Boolean, d: Int?, hint: TurnHint, exit: Int? = null) =
        NavPrompt(ParsedTurn(m, d, exit, "k", hint), now, d)

    private fun only(e: HaryanviLines.Event, roman: Boolean = false): String =
        HaryanviLines.forEvent(e)!!.single().let { if (roman) it.roman else it.deva }

    @Test
    fun aHintTakesThePlaceOfTheTurnLineAhead() {
        val picker = LinePicker(Random(6))
        val p = NavPhrases.desiPhrase(hinted(Maneuver.Left, false, 400, TurnHint.PetrolPumpLeft), picker, roman = false)!!
        assertTrue(p.text, p.text.contains("400 मीटर"))
        assertTrue(p.text, p.text.endsWith(only(HaryanviLines.Event.NavPumpLeft)))
        assertEquals(TurnHint.PetrolPumpLeft, p.hint)
        // Recorded voice: "<distance> <tail> <pump line>".
        assertEquals("dist_400m", p.clips.first())
        assertEquals("nav_pump_left_01", p.clips.last())

        val roman = NavPhrases.desi(hinted(Maneuver.Roundabout, false, 300, TurnHint.RoundaboutSecondExit, exit = 2), picker, roman = true)!!
        assertTrue(roman, roman.contains("300 metre") && roman.endsWith(only(HaryanviLines.Event.NavRoundaboutSecondExit, roman = true)))
        assertTrue(NavPhrases.desi(hinted(Maneuver.Ramp, false, 500, TurnHint.HighwayEnter), picker, roman = false)!!
            .endsWith(only(HaryanviLines.Event.NavHighwayEnter)))
    }

    @Test
    fun wholeSentenceHintsTakeNoDistance() {
        val picker = LinePicker(Random(7))
        assertEquals(
            only(HaryanviLines.Event.NavHighwayExitRight),
            NavPhrases.desi(hinted(Maneuver.Exit, false, 600, TurnHint.HighwayExitRight), picker, roman = false),
        )
        assertEquals(
            only(HaryanviLines.Event.NavLaneLeft),
            NavPhrases.desi(hinted(Maneuver.Left, false, 500, TurnHint.LaneLeft), picker, roman = false),
        )
    }

    @Test
    fun theNowPromptKeepsThePlainTurnLine() {
        val picker = LinePicker(Random(8))
        val lefts = HaryanviLines.forEvent(HaryanviLines.Event.NavLeft)!!.map { it.deva }
        repeat(10) {
            val p = NavPhrases.desiPhrase(hinted(Maneuver.Left, true, 100, TurnHint.PetrolPumpLeft), picker, roman = false)!!
            assertTrue(p.text, lefts.any { p.text.endsWith(it) })
            assertEquals(null, p.hint)
        }
    }

    @Test
    fun arrivingOnTheRightSaysTheMetres() {
        val picker = LinePicker(Random(9))
        val p = NavPhrases.desiPhrase(hinted(Maneuver.Arrive, false, 150, TurnHint.ArrivingRight), picker, roman = false)!!
        assertEquals("बस आ गे, 150 मीटर पै सुल्टे हाथ थारी मंजिल सै।", p.text)
        assertEquals(TurnHint.ArrivingRight, p.hint)
        assertTrue(p.clips.isEmpty()) // a number: never recorded
        assertEquals(
            "Bas aa ge, 100 meetar pai sulte haath thaari manzil sai.",
            NavPhrases.desi(hinted(Maneuver.Arrive, false, 100, TurnHint.ArrivingRight), picker, roman = true),
        )
        // Already there, or the recorded voice: the usual arrived line.
        val arrived = HaryanviLines.forEvent(HaryanviLines.Event.NavArrive)!!.map { it.deva }
        assertTrue(NavPhrases.desi(hinted(Maneuver.Arrive, false, 0, TurnHint.ArrivingRight), picker, roman = false) in arrived)
        val recorded = NavPhrases.desiPhrase(hinted(Maneuver.Arrive, false, 150, TurnHint.ArrivingRight), picker, roman = false, recordedOnly = true)!!
        assertTrue(recorded.text in arrived)
        assertEquals(null, recorded.hint)
    }

    @Test
    fun aRecordedVoiceWithoutTheClipSaysThePlainLine() {
        // Round-3 lines wait for their clips: nothing recorded means the plain turn line.
        val picker = LinePicker(Random(10)) { { id: String -> !id.startsWith("nav_pump_left") } }
        val lefts = HaryanviLines.forEvent(HaryanviLines.Event.NavLeft)!!.map { it.deva }
        val p = NavPhrases.desiPhrase(hinted(Maneuver.Left, false, 400, TurnHint.PetrolPumpLeft), picker, roman = false, recordedOnly = true)!!
        assertTrue(p.text, lefts.any { p.text.endsWith(it) })
        assertEquals(null, p.hint)
    }

    @Test
    fun distances() {
        assertEquals("400 मीटर", NavPhrases.distance(400, roman = false))
        assertEquals("2 kilometre", NavPhrases.distance(2000, roman = true))
        assertEquals("1.5 किलोमीटर", NavPhrases.distance(1500, roman = false))
    }
}
