package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.cameras.ReportKind
import com.vikash.ampli.drive.fuel.FuelFilter
import com.vikash.ampli.drive.fuel.FuelVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Hey Ampli, nearest CNG pump" / "police aage hai": English, Hindi, Hinglish and Haryanvi. */
class FuelRadarVoiceTest {

    private fun fuel(text: String, lang: String = "hi") = (VoiceIntents.parse(text, lang) as? VoiceIntent.FindFuel)?.filter

    private fun report(text: String, lang: String = "hi") = (VoiceIntents.parse(text, lang) as? VoiceIntent.ReportRoad)?.kind

    @Test
    fun fuelQuestions() {
        assertEquals(FuelFilter.Cng, fuel("Hey Ampli, nearest CNG pump"))
        assertEquals(FuelFilter.Cng, fuel("nearest CNG pump", "en"))
        assertEquals(FuelFilter.Cng, fuel("CNG pump kahan hai"))
        assertEquals(FuelFilter.Cng, fuel("सीएनजी पंप कहाँ है"))
        assertEquals(FuelFilter.Cng, fuel("सीएनजी पंप कित सै"))
        assertEquals(FuelFilter.Cng, fuel("take me to the nearest cng station", "en"))
        assertEquals(FuelFilter.Petrol, fuel("petrol pump"))
        assertEquals(FuelFilter.Petrol, fuel("पेट्रोल पंप कहाँ है"))
        assertEquals(FuelFilter.Petrol, fuel("where is the nearest petrol pump", "en"))
        assertEquals(FuelFilter.Diesel, fuel("diesel kahan milega"))
        assertEquals(FuelFilter.Diesel, fuel("डीज़ल पंप"))
        assertEquals(FuelFilter.Ev, fuel("charging station", "en"))
        assertEquals(FuelFilter.Ev, fuel("चार्जिंग स्टेशन कहाँ है"))
        assertEquals(FuelFilter.Ev, fuel("find an EV charger", "en"))
        assertEquals(FuelFilter.All, fuel("nearest fuel station", "en"))
        assertEquals(FuelFilter.All, fuel("पंप कित सै"))
        assertEquals(FuelFilter.All, fuel("tel bharwana hai"))
    }

    @Test
    fun notFuel() {
        assertNull(fuel("tyre mein hawa bharwani hai air pump"))
        assertNull(fuel("play pump it up", "en"))
        assertTrue(VoiceIntents.parse("take me to Rohtak", "en") is VoiceIntent.Navigate)
        assertTrue(VoiceIntents.parse("phone charging", "en") !is VoiceIntent.FindFuel)
    }

    @Test
    fun roadReports() {
        assertEquals(ReportKind.Police, report("police aage hai"))
        assertEquals(ReportKind.Police, report("Hey Ampli, पुलिस आगे है"))
        assertEquals(ReportKind.Police, report("आगे पुलिस खड़ी सै"))
        assertEquals(ReportKind.Police, report("naka laga hai"))
        assertEquals(ReportKind.Police, report("police check ahead", "en"))
        assertEquals(ReportKind.Accident, report("accident ahead", "en"))
        assertEquals(ReportKind.Accident, report("आगे एक्सीडेंट हो गया"))
        assertEquals(ReportKind.Accident, report("aage hadsa hua hai"))
        assertEquals(ReportKind.Jam, report("aage jam hai"))
        assertEquals(ReportKind.Jam, report("आगे जाम है"))
        assertEquals(ReportKind.Jam, report("traffic jam ahead", "en"))
        assertEquals(ReportKind.MobileCamera, report("mobile camera aage hai"))
        assertEquals(ReportKind.MobileCamera, report("मोबाइल कैमरा आगे है"))
        assertEquals(ReportKind.MobileCamera, report("speed gun here", "en"))
    }

    @Test
    fun anAccidentOfOurOwnIsStillSos() {
        assertEquals(VoiceIntent.Sos, VoiceIntents.parse("accident", "en"))
        assertEquals(VoiceIntent.Sos, VoiceIntents.parse("accident ho gaya help", "hi"))
        assertEquals(VoiceIntent.Sos, VoiceIntents.parse("accident ahead call ambulance", "en"))
        assertEquals(VoiceIntent.ReportRoad(ReportKind.Accident), VoiceIntents.parse("accident ahead", "en"))
    }

    @Test
    fun notReports() {
        // Questions, places and calls.
        assertNull(report("aage jam hai kya"))
        assertNull(report("traffic kaisa hai"))
        assertNull(report("police station le chalo"))
        assertNull(report("take me to the police station", "en"))
        assertNull(report("police ko phone karo"))
        // The next fixed camera is still its own question.
        assertEquals(VoiceIntent.NextCamera, VoiceIntents.parse("agla camera", "hi"))
        assertEquals(VoiceIntent.NextCamera, VoiceIntents.parse("next speed camera", "en"))
    }

    @Test
    fun offlineCommandsAndCatalogParse() {
        val fuelAndRadar = VoskCommands.COMMANDS.map { VoiceIntents.parse(it, "en") }
        assertTrue(fuelAndRadar.any { it is VoiceIntent.FindFuel })
        assertTrue(fuelAndRadar.any { it is VoiceIntent.ReportRoad })
        val hindi = VoskCommands.HINDI_COMMANDS.map { VoiceIntents.parse(it, "hi") }
        assertTrue(hindi.any { it == VoiceIntent.FindFuel(FuelFilter.Cng) })
        assertTrue(hindi.any { it == VoiceIntent.ReportRoad(ReportKind.Police) })
    }

    @Test
    fun spokenDistanceOnlyWhenClose() {
        assertEquals(2_000, FuelVoice.spokenDistance(1_900.0)!!.meters)
        assertEquals(1_000, FuelVoice.spokenDistance(1_100.0)!!.meters)
        // 7 km would be said as 5 or 10: show it on the card instead.
        assertNull(FuelVoice.spokenDistance(7_000.0))
        assertNull(FuelVoice.spokenDistance(1_300.0))
        assertNull(FuelVoice.spokenDistance(0.0))
    }

    @Test
    fun answerClips() {
        val picker = LinePicker(kotlin.random.Random(1))
        val pick = { e: HaryanviLines.Event -> picker.pickIndexed(e) }
        val near = FuelVoice.clips(FuelFilter.Cng, 1_950.0, pick)
        assertEquals("dist_2000m", near[0])
        assertTrue(near[1].startsWith("fuel_cng_tail_"))
        val far = FuelVoice.clips(FuelFilter.Ev, 7_000.0, pick)
        assertTrue(far.single().startsWith("fuel_ev_found_"))
        assertTrue(FuelVoice.clips(FuelFilter.Diesel, 800.0, pick)[1].startsWith("fuel_pump_tail_"))
        assertTrue(FuelVoice.clips(FuelFilter.All, null, pick).single().startsWith("fuel_none_"))
        // Every clip it can ask for is in the voice pack manifest.
        val ids = VoiceClips.manifest.map { it.id }.toSet()
        for (e in listOf(HaryanviLines.Event.FuelCngTail, HaryanviLines.Event.FuelPumpFound, HaryanviLines.Event.PoliceAhead, HaryanviLines.Event.ReportSaved)) {
            for (i in HaryanviLines.forEvent(e)!!.indices) assertTrue(VoiceClips.id(e, i), VoiceClips.id(e, i) in ids)
        }
    }

    @Test
    fun newLinesAreRecordable() {
        val radar = HaryanviLines.Event.entries.filter { it.group == HaryanviLines.Group.Radar || it.name.startsWith("Fuel") }
        assertEquals(15, radar.size)
        for (e in radar) {
            val lines = HaryanviLines.forEvent(e)!!
            assertEquals(4, lines.size)
            for (l in lines) assertFalse(l.deva, l.deva.contains('%'))
        }
    }
}
