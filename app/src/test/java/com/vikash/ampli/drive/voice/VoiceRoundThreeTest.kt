package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.voice.HaryanviLines.Event as E
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.random.Random

/** Round 3: the owner's approved navigation lines, word for word, and how they are recorded. */
class VoiceRoundThreeTest {

    /** The owner's list ("%d" written as "%1$d"), with the event each one is wired to. */
    private val owners = listOf(
        E.NavDistanceLeft to "बस %1\$d किलोमीटर बचे, घणा ना सै इब।",
        E.NavHalfway to "आधा रास्ता हो ग्या भाई, बाकी आधा मौज मैं।",
        E.NavDistanceLeft to "%1\$d किलोमीटर और, फेर पहुँच गे समझ।",
        E.NavTenMinutesLeft to "बस दस मिनट का खेल सै, पहुँचण आळे सां।",
        E.NavKeepStraight to "सीधा चालता रह, आगले %1\$d किलोमीटर कोए मोड़ कोनी।",
        E.NavKeepStraight to "नाक की सीध मैं चाल, मैं बता द्यूंगा कद मुड़णा सै।",
        E.NavPumpLeft to "पेट्रोल पंप तै उल्टे हाथ ले लिए।",
        E.NavCngPumpRight to "सीएनजी पंप कै बाद सुल्टे हाथ मुड़ ले।",
        E.NavRoundaboutSecondExit to "गोल चक्कर आ रया सै, दूसरा रास्ता पकड़ लिए।",
        E.NavRoundaboutThirdExit to "चक्कर पै तीसरा निकास, घूम कै सीधा।",
        E.NavHighwayEnter to "हाईवे पै चढ़ ले भाई, इब खुली सड़क सै।",
        E.NavHighwayExitRight to "आग्गै हाईवे तै उतरणा सै, सुल्टे हाथ आळी लेन मैं आ ज्या।",
        E.NavLaneLeft to "उल्टे हाथ आळी लेन पकड़ ले, आग्गै मुड़णा सै।",
        E.NavLaneMiddle to "बीच आळी लेन मैं रह, दोनूं कान्ही रास्ता बंटैगा।",
        E.NavArrivingRight to "बस आ गे, %1\$d मीटर पै सुल्टे हाथ थारी मंजिल सै।",
        E.NavArrive to "पहुँच गे भाई! गाड्डी ढंग तै लगा दिए।",
        E.NavArrive to "मंजिल आ गी, जय बाबा की! आज का गेड़ा बढ़िया रया।",
    )

    @Test
    fun ownersWordsAreInTheBank() {
        for ((event, deva) in owners) {
            assertTrue("$event lacks $deva", HaryanviLines.forEvent(event)!!.any { it.deva == deva })
        }
        // Lines with nothing in the app to trigger them are left out.
        val all = HaryanviLines.allLines.values.flatten().map { it.deva }
        for (bad in listOf("आग्गै ब्रेकर सै", "ब्रेकर ब्रेकर", "फ्लाईओवर पै ना चढ़िए", "नीचे जाम सै", "जाम खुल ग्या", "ढाबे तै", "फास्टैग आळी लेन")) {
            assertFalse(bad, all.any { it.contains(bad) })
        }
    }

    @Test
    fun roundThreeEventsAndTheirClips() {
        assertEquals(
            setOf(
                E.NavDistanceLeft, E.NavHalfway, E.NavTenMinutesLeft, E.NavKeepStraight, E.NavPumpLeft, E.NavCngPumpRight,
                E.NavRoundaboutSecondExit, E.NavRoundaboutThirdExit, E.NavHighwayEnter, E.NavHighwayExitRight,
                E.NavLaneLeft, E.NavLaneMiddle, E.NavArrivingRight,
            ),
            HaryanviLines.roundThreeOnly,
        )
        for (e in HaryanviLines.roundThreeOnly) {
            assertEquals(HaryanviLines.Group.Navigation, e.group)
            assertEquals(0, HaryanviLines.firstRoundCount(e))
        }
        // The arrived lines continue NavArrive's ids.
        assertEquals("nav_arrive_12", VoiceClips.id(E.NavArrive, HaryanviLines.forEvent(E.NavArrive)!!.indexOfFirst { it.deva == owners[15].second }))
        // Lines without a number go to the next recording run; lines with one never do.
        val ids = VoiceClips.expectedHashes.keys
        for ((event, deva) in owners) {
            val i = HaryanviLines.forEvent(event)!!.indexOfFirst { it.deva == deva }
            assertEquals(deva, !deva.contains('%'), VoiceClips.id(event, i) in ids)
        }
    }

    @Test
    fun aRecordedVoiceWaitsForTheClip() {
        val nothingRecorded = LinePicker(Random(1)) { { _: String -> false } }
        assertEquals(null, nothingRecorded.pickIndexed(E.NavHalfway))
        // The number line can be picked, but has no clip (NavPrompter then stays quiet or says the usual line).
        val p = nothingRecorded.pickIndexed(E.NavKeepStraight)!!
        assertTrue(p.line.deva.contains('%'))
        assertTrue(VoiceClips.clipsFor(p, arrayOf(8)) { nothingRecorded.pickIndexed(it) }.isEmpty())
        val recorded = LinePicker(Random(1)) { { id: String -> id == "nav_halfway_01" } }
        assertEquals("nav_halfway_01", recorded.pickIndexed(E.NavHalfway)!!.id)
    }

    @Test
    fun numbersFillIn() {
        val line = HaryanviLines.forEvent(E.NavDistanceLeft)!!.first()
        assertEquals("बस 5 किलोमीटर बचे, घणा ना सै इब।", String.format(Locale.US, line.deva, 5))
        assertEquals("Bas 5 kilometre bache, ghana na sai ib.", String.format(Locale.US, line.roman, 5))
    }
}
