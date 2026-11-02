package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.cameras.ReportKind
import com.vikash.ampli.drive.car.FuelType
import com.vikash.ampli.drive.fuel.FuelFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The voice side of the nine drive features: police / camera reports and taking them back,
 * pump queues, the petrol diary, the challan counter, break snooze and the phone hotspot.
 * English, Roman Hindi (Hinglish) and Devanagari, as both Vosk models write them.
 */
class NineFeaturesVoiceTest {

    private fun p(text: String, lang: String = "hi") = VoiceIntents.parse(text, lang)

    @Test
    fun policeAndNakaReportsAreDirectionAwareRoadReports() {
        for (s in listOf("aage police hai", "police hai", "naka hai", "aage naka hai", "naka laga hai", "पुलिस है", "आगे पुलिस है", "नाका है", "आगे नाका है")) {
            assertEquals(s, VoiceIntent.ReportRoad(ReportKind.Police), p(s))
        }
        assertEquals(VoiceIntent.ReportRoad(ReportKind.Police), p("police ahead", "en"))
    }

    @Test
    fun cameraStatementsSaveACameraButQuestionsAskForTheNextOne() {
        for (s in listOf("camera hai", "aage camera hai", "camera laga hai", "yahan camera hai", "कैमरा है", "आगे कैमरा है", "कैमरा लगा है")) {
            assertEquals(s, VoiceIntent.CameraHere, p(s))
        }
        assertEquals(VoiceIntent.CameraHere, p("camera here", "en"))
        assertEquals(VoiceIntent.NextCamera, p("next camera", "en"))
        assertEquals(VoiceIntent.NextCamera, p("speed camera", "en"))
        assertEquals(VoiceIntent.NextCamera, p("कैमरा"))
        assertEquals(VoiceIntent.NextCamera, p("अगला कैमरा"))
        assertEquals(VoiceIntent.NextCamera, p("camera kitni door hai"))
        assertEquals(VoiceIntent.NextCamera, p("camera hai kya"))
        // A mobile camera is still the one-hour report.
        assertEquals(VoiceIntent.ReportRoad(ReportKind.MobileCamera), p("mobile camera aage hai"))
    }

    @Test
    fun takingReportsBack() {
        val camera = VoiceIntent.ReportGone(VoiceIntent.Gone.Camera)
        val police = VoiceIntent.ReportGone(VoiceIntent.Gone.Police)
        assertEquals(camera, p("camera nahi hai"))
        assertEquals(camera, p("कैमरा नहीं है"))
        assertEquals(camera, p("no camera here", "en"))
        assertEquals(police, p("police hat gayi"))
        assertEquals(police, p("naka hat gaya"))
        assertEquals(police, p("पुलिस हट गई"))
        assertEquals(police, p("police gone", "en"))
        assertEquals(VoiceIntent.ReportGone(VoiceIntent.Gone.Jam), p("jam khul gaya"))
        assertEquals(VoiceIntent.ReportGone(VoiceIntent.Gone.Jam), p("जाम खुल गया"))
        // A question is not a report.
        assertFalse(p("camera nahi hai kya") is VoiceIntent.ReportGone)
    }

    @Test
    fun pumpQueues() {
        assertEquals(VoiceIntent.PumpQueue(true, FuelFilter.Cng), p("CNG pe line lambi hai"))
        assertEquals(VoiceIntent.PumpQueue(true, FuelFilter.Cng), p("cng par lambi line hai"))
        assertEquals(VoiceIntent.PumpQueue(true, FuelFilter.Cng), p("सीएनजी पे लंबी लाइन है"))
        assertEquals(VoiceIntent.PumpQueue(true, FuelFilter.All), p("line lambi hai"))
        assertEquals(VoiceIntent.PumpQueue(true, FuelFilter.All), p("लाइन लंबी है"))
        assertEquals(VoiceIntent.PumpQueue(true, FuelFilter.Cng), p("long queue at cng", "en"))
        assertEquals(VoiceIntent.PumpQueue(false, FuelFilter.Cng), p("CNG khali hai"))
        assertEquals(VoiceIntent.PumpQueue(false, FuelFilter.Cng), p("सीएनजी खाली है"))
        assertEquals(VoiceIntent.PumpQueue(false, FuelFilter.All), p("pump khali hai"))
        assertEquals(VoiceIntent.PumpQueue(false, FuelFilter.All), p("पंप खाली है"))
        assertEquals(VoiceIntent.PumpQueue(false, FuelFilter.All), p("line nahi hai"))
        assertEquals(VoiceIntent.PumpQueue(false, FuelFilter.All), p("no queue at the pump", "en"))
        // Still the fuel finder.
        assertEquals(VoiceIntent.FindFuel(FuelFilter.Cng), p("CNG pump kahan hai"))
        // Traffic, not a pump.
        assertFalse(p("aage lambi line hai") is VoiceIntent.PumpQueue)
    }

    @Test
    fun petrolDiary() {
        assertEquals(VoiceIntent.LogFuel(1000.0, null, FuelType.Petrol), p("petrol bharwaya 1000 ka"))
        assertEquals(VoiceIntent.LogFuel(1000.0, null, FuelType.Petrol), p("1000 ka petrol"))
        assertEquals(VoiceIntent.LogFuel(1000.0, null, FuelType.Petrol), p("one thousand ka petrol", "en"))
        assertEquals(VoiceIntent.LogFuel(1000.0, null, FuelType.Petrol), p("हजार का पेट्रोल"))
        assertEquals(VoiceIntent.LogFuel(2000.0, null, FuelType.Petrol), p("दो हजार का पेट्रोल"))
        assertEquals(VoiceIntent.LogFuel(500.0, null, FuelType.Petrol), p("पांच सौ का पेट्रोल"))
        assertEquals(VoiceIntent.LogFuel(500.0, null, FuelType.Cng), p("पांच सौ की सीएनजी"))
        assertEquals(VoiceIntent.LogFuel(1500.0, null, FuelType.Petrol), p("dedh hazaar ka petrol dalwaya"))
        assertEquals(VoiceIntent.LogFuel(null, 50.0, null), p("pachaas litre"))
        assertEquals(VoiceIntent.LogFuel(null, 50.0, null), p("पचास लीटर"))
        assertEquals(VoiceIntent.LogFuel(null, 30.0, FuelType.Petrol), p("30 litre petrol bharwaya"))
        assertEquals(VoiceIntent.LogFuel(null, 8.0, FuelType.Cng), p("8 kilo cng bharwayi"))
        assertEquals(VoiceIntent.LogFuel(null, null, FuelType.Petrol), p("petrol bharwaya"))
        assertEquals(VoiceIntent.LogFuel(null, null, FuelType.Petrol), p("पेट्रोल भरवाया"))
        assertEquals(VoiceIntent.LogFuel(null, null, FuelType.Cng), p("सीएनजी भरवाई"))
        assertEquals(VoiceIntent.LogFuel(null, null, FuelType.Petrol), p("petrol filled", "en"))
        // Wanting fuel is still the finder; volume stays volume.
        assertEquals(VoiceIntent.FindFuel(FuelFilter.Petrol), p("petrol bharwana hai"))
        assertTrue(p("volume five", "en") is VoiceIntent.Volume)
        assertFalse(p("take me to sector 2", "en") is VoiceIntent.LogFuel)
    }

    @Test
    fun challanBreakHotspotParking() {
        assertEquals(VoiceIntent.ChallanSaved, p("kitna challan bachaya"))
        assertEquals(VoiceIntent.ChallanSaved, p("कितना चालान बचाया"))
        assertEquals(VoiceIntent.ChallanSaved, p("how much did i save", "en"))
        assertEquals(VoiceIntent.SnoozeBreak, p("break baad mein"))
        assertEquals(VoiceIntent.SnoozeBreak, p("ब्रेक बाद में"))
        assertEquals(VoiceIntent.SnoozeBreak, p("remind me later", "en"))
        assertEquals(VoiceIntent.ConnectHotspot, p("hotspot jodo"))
        assertEquals(VoiceIntent.ConnectHotspot, p("phone ka internet chalao"))
        assertEquals(VoiceIntent.ConnectHotspot, p("हॉटस्पॉट जोड़ो"))
        assertFalse(p("hotspot band karo") is VoiceIntent.ConnectHotspot)
        assertEquals(VoiceIntent.WhereParked, p("gaadi kahan khadi hai"))
        assertEquals(VoiceIntent.WhereParked, p("गाड़ी कहाँ खड़ी है"))
    }

    @Test
    fun spokenAmounts() {
        assertEquals(1000.0, SpokenNumbers.parse("1000")!!, 0.0)
        assertEquals(1000.0, SpokenNumbers.parse("१०००")!!, 0.0)
        assertEquals(1500.0, SpokenNumbers.parse("one thousand five hundred")!!, 0.0)
        assertEquals(2500.0, SpokenNumbers.parse("dhai hazaar")!!, 0.0)
        assertEquals(350.0, SpokenNumbers.parse("saadhe teen sau")!!, 0.0)
        assertEquals(350.0, SpokenNumbers.parse("teen sau pachaas")!!, 0.0)
        assertEquals(2500.0, SpokenNumbers.parse("दो हजार पांच सौ")!!, 0.0)
        assertEquals(150.0, SpokenNumbers.parse("डेढ़ सौ")!!, 0.0)
        assertEquals(45.0, SpokenNumbers.parse("forty five litre")!!, 0.0)
        assertEquals(SpokenNumbers.Unit.Litres, SpokenNumbers.amount("pachaas litre")!!.unit)
        assertEquals(SpokenNumbers.Unit.Kilos, SpokenNumbers.amount("aath kilo")!!.unit)
        assertNull(SpokenNumbers.parse("gaana chala do"))
        assertNull(SpokenNumbers.parse("kar do"))
    }

    @Test
    fun everyNewGrammarPhraseParses() {
        val newEnglish = VoskCommands.HINGLISH_COMMANDS + VoskCommands.COMMANDS
        for (s in newEnglish) assertFalse(s, p(s) is VoiceIntent.Unknown)
        for (s in VoskCommands.HINDI_COMMANDS) assertFalse(s, p(s) is VoiceIntent.Unknown)
    }
}
