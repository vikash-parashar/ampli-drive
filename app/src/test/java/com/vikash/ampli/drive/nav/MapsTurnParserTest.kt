package com.vikash.ampli.drive.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapsTurnParserTest {

    private fun m(title: String?, text: String?) = MapsTurnParser.parse(title, text)?.maneuver
    private fun d(title: String?, text: String?) = MapsTurnParser.parse(title, text)?.distanceM

    @Test
    fun englishDistanceInTitle() {
        assertEquals(Maneuver.Right, m("450 m", "Turn right onto NH 48"))
        assertEquals(450, d("450 m", "Turn right onto NH 48"))
        assertEquals(Maneuver.Left, m("1.2 km", "Turn left toward Sohna Road"))
        assertEquals(1200, d("1.2 km", "Turn left toward Sohna Road"))
    }

    @Test
    fun englishInstructionInTitle() {
        assertEquals(Maneuver.Left, m("Turn left onto MG Road", "200 m"))
        assertEquals(200, d("Turn left onto MG Road", "200 m"))
        assertEquals(Maneuver.SlightRight, m("300 m · Slight right", null))
        assertEquals(300, d("300 m · Slight right", null))
    }

    @Test
    fun roadNamesDontChangeTheTurn() {
        assertEquals(Maneuver.Left, m("200 m", "Turn left onto Right Bank Road"))
        assertEquals(Maneuver.Right, m("200 m", "Turn right toward Left Lane Market"))
        assertEquals(Maneuver.Continue, m("2 km", "Continue onto Ring Road towards Exit 5"))
    }

    @Test
    fun englishManeuvers() {
        assertEquals(Maneuver.SharpLeft, m("80 m", "Sharp left onto Station Rd"))
        assertEquals(Maneuver.SharpRight, m("80 m", "Sharp right"))
        assertEquals(Maneuver.SlightLeft, m("200 m", "Slight left toward Delhi"))
        assertEquals(Maneuver.UTurn, m("150 m", "Make a U-turn"))
        assertEquals(Maneuver.KeepLeft, m("500 m", "Keep left at the fork"))
        assertEquals(Maneuver.KeepRight, m("500 m", "Keep right to stay on NH 44"))
        assertEquals(Maneuver.Flyover, m("400 m", "Take the flyover"))
        assertEquals(Maneuver.Ramp, m("400 m", "Take the ramp on the left to NH 48"))
        assertEquals(Maneuver.Exit, m("1 km", "Take exit 12 toward Gurugram"))
        assertEquals(Maneuver.Arrive, m("Arrive at destination", null))
        assertEquals(Maneuver.Arrive, m("100 m", "Your destination will be on the right"))
        assertEquals(Maneuver.Continue, m("5 km", "Head north on Mall Road"))
    }

    @Test
    fun roundaboutWithExitNumber() {
        val t = MapsTurnParser.parse("300 m", "At the roundabout, take the 2nd exit onto Sector Road")!!
        assertEquals(Maneuver.Roundabout, t.maneuver)
        assertEquals(2, t.exitNumber)
        assertEquals(300, t.distanceM)
        val words = MapsTurnParser.parse("300 m", "At the roundabout, take the third exit")!!
        assertEquals(3, words.exitNumber)
    }

    @Test
    fun hindiMapsStrings() {
        assertEquals(Maneuver.Left, m("400 मी", "MG रोड पर बाएं मुड़ें"))
        assertEquals(400, d("400 मी", "MG रोड पर बाएं मुड़ें"))
        assertEquals(Maneuver.Right, m("1.5 किमी", "दाएं मुड़ें"))
        assertEquals(1500, d("1.5 किमी", "दाएं मुड़ें"))
        assertEquals(Maneuver.KeepLeft, m("300 मीटर", "बाईं ओर रहें"))
        assertEquals(Maneuver.KeepRight, m("300 मीटर", "दाईं ओर रहें"))
        assertEquals(Maneuver.UTurn, m("200 मी", "यू-टर्न लें"))
        assertEquals(Maneuver.SlightLeft, m("200 मी", "हल्का बाएं मुड़ें"))
        assertEquals(Maneuver.SharpRight, m("100 मी", "तीखा दाएं मुड़ें"))
        assertEquals(Maneuver.Flyover, m("500 मी", "फ़्लाईओवर लें"))
        assertEquals(Maneuver.Exit, m("1 किमी", "निकास लें"))
        assertEquals(Maneuver.Arrive, m("आप अपनी मंज़िल पर पहुंच गए हैं", null))
        assertEquals(Maneuver.Continue, m("2 किमी", "सीधे चलते रहें"))
        val r = MapsTurnParser.parse("250 मी", "गोल चक्कर पर दूसरे निकास से निकलें")!!
        assertEquals(Maneuver.Roundabout, r.maneuver)
        assertEquals(2, r.exitNumber)
    }

    @Test
    fun otherUnitsAndCommas() {
        assertEquals(1300, d("1,3 km", "Turn left"))
        assertEquals(152, d("500 ft", "Turn left"))
        assertEquals(483, d("0.3 mi", "Turn left"))
    }

    @Test
    fun minutesAreNotMetres() {
        assertNull(MapsTurnParser.distanceIn("5 min"))
    }

    @Test
    fun unknownTextGivesNull() {
        assertNull(MapsTurnParser.parse("Google Maps", "Navigation is running"))
        assertNull(MapsTurnParser.parse("", null))
        assertNull(MapsTurnParser.parse(null, null))
    }

    @Test
    fun keyIgnoresDistanceButNotInstruction() {
        val a = MapsTurnParser.parse("450 m", "Turn right onto NH 48")!!
        val b = MapsTurnParser.parse("300 m", "Turn right onto NH 48")!!
        val c = MapsTurnParser.parse("300 m", "Turn right onto Sohna Road")!!
        assertEquals(a.key, b.key)
        assertNotEquals(a.key, c.key)
    }
}
