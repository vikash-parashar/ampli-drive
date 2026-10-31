package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.car.NightDriveReminder
import com.vikash.ampli.drive.fastag.FastagLowReminder
import com.vikash.ampli.drive.voice.HaryanviLines.Event as E
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Round-2 Haryanvi lines (lines-v2): ids, the recorded-clip gate, per-voice wording, new events. */
class VoiceRoundTwoTest {

    private fun roundTwo(e: E): List<Int> {
        val lines = HaryanviLines.forEvent(e) ?: return emptyList()
        return (HaryanviLines.firstRoundCount(e) until lines.size).toList()
    }

    @Test
    fun roundTwoContinuesTheIdsAndKeepsFirstRoundClips() {
        assertEquals(8, HaryanviLines.firstRoundCount(E.Welcome))
        assertTrue(roundTwo(E.Welcome).isNotEmpty())
        assertEquals("welcome_09", VoiceClips.id(E.Welcome, roundTwo(E.Welcome).first()))
        // Every first-round clip id still means the same text (lines.json only grew).
        val manifest = VoiceClips.manifest.associateBy { it.id }
        for ((event, lines) in HaryanviLines.allLines) {
            if (VoiceClips.recordedEvent(event) != event) continue
            for (i in 0 until HaryanviLines.firstRoundCount(event)) {
                val line = lines[i]
                if (!VoiceClips.recordable(line) || event == E.NavRoundaboutExit) continue
                assertEquals("$event $i", line.deva, manifest.getValue(VoiceClips.id(event, i)).text)
            }
        }
    }

    @Test
    fun roundTwoLinesAreListedForTheNextRecordingRun() {
        val ids = VoiceClips.manifest.map { it.id }.toSet()
        var listed = 0
        for ((event, lines) in HaryanviLines.allLines) {
            if (VoiceClips.recordedEvent(event) != event) continue
            for (i in roundTwo(event)) {
                if (!VoiceClips.recordable(lines[i])) continue
                val id = VoiceClips.id(event, i)
                if (lines[i].devaF != null) {
                    assertTrue(id, "${id}_m" in ids && "${id}_f" in ids)
                } else {
                    assertTrue(id, id in ids)
                }
                listed++
            }
        }
        assertTrue("only $listed round-2 clips", listed > 100)
    }

    @Test
    fun recordedVoiceNeverPicksAnUnrecordedRoundTwoLine() {
        val nothingRecorded = LinePicker(Random(3)) { { _: String -> false } }
        for (event in HaryanviLines.Event.entries.filter { !it.safety && HaryanviLines.firstRoundCount(it) > 0 }) {
            val lines = HaryanviLines.forEvent(event)!!
            repeat(200) {
                val p = nothingRecorded.pickIndexed(event)!!
                assertTrue(
                    "$event picked unrecorded ${p.id}",
                    p.index < HaryanviLines.firstRoundCount(event) || !VoiceClips.recordable(lines[p.index]),
                )
            }
        }
    }

    @Test
    fun aRecordedRoundTwoClipJoinsTheRotation() {
        val first = roundTwo(E.ParkingSaved).first()
        val recorded = setOf(VoiceClips.id(E.ParkingSaved, first))
        val picker = LinePicker(Random(9)) { { id: String -> id in recorded } }
        val seen = HashSet<Int>()
        repeat(400) { seen += picker.pickIndexed(E.ParkingSaved)!!.index }
        assertEquals((0 until HaryanviLines.firstRoundCount(E.ParkingSaved)).toSet() + first, seen)

        // The phone voice (no check) uses every line.
        val phone = LinePicker(Random(9)) { null }
        val all = HashSet<Int>()
        repeat(400) { all += phone.pickIndexed(E.ParkingSaved)!!.index }
        assertEquals(HaryanviLines.forEvent(E.ParkingSaved)!!.indices.toSet(), all)
    }

    @Test
    fun theGateFollowsTheClipLibrary() {
        val event = E.JamAhead
        val i = roundTwo(event).first()
        val id = VoiceClips.id(event, i)
        val stale = ClipLibrary(mapOf(VoicePack.Male to mapOf(id to "000000000000")))
        val fresh = ClipLibrary(mapOf(VoicePack.Male to mapOf(id to VoiceClips.expectedHashes.getValue(id))))
        assertFalse(i in VoiceGate.usable(event) { stale.has(VoicePack.Male, it) })
        assertTrue(i in VoiceGate.usable(event) { fresh.has(VoicePack.Male, it) })
        assertFalse(i in VoiceGate.usable(event) { fresh.has(VoicePack.Female, it) })
        assertTrue(i in VoiceGate.usable(event, null))
    }

    @Test
    fun linesWordedPerVoicePlayTheirOwnClip() {
        val (event, index) = HaryanviLines.allLines.entries.firstNotNullOf { (e, lines) ->
            lines.indexOfFirst { it.devaF != null }.takeIf { it >= 0 }?.let { e to it }
        }
        val line = HaryanviLines.forEvent(event)!![index]
        val id = VoiceClips.id(event, index)
        assertTrue(line.deva != line.devaF)
        assertEquals(line.devaF, line.devaFor(VoicePack.Female))
        assertEquals(line.deva, line.devaFor(VoicePack.Male))
        assertEquals(line.deva, line.devaFor(VoicePack.Tts))
        assertEquals(line.romanF, line.romanFor(VoicePack.Female))

        val male = VoiceClips.manifest.single { it.id == "${id}_m" }
        val female = VoiceClips.manifest.single { it.id == "${id}_f" }
        assertEquals("male", male.voice)
        assertEquals("female", female.voice)
        assertEquals(line.devaF, female.text)
        assertNull(VoiceClips.manifest.firstOrNull { it.id == id })

        val lib = ClipLibrary(mapOf(VoicePack.Female to mapOf("${id}_f" to female.hash)))
        assertTrue(lib.has(VoicePack.Female, id))
        assertFalse(lib.has(VoicePack.Male, id))
        assertEquals(listOf("${id}_f"), lib.plan(VoicePack.Female, Spoken(line.devaF!!, "hi", listOf(id))))
        assertNull(lib.plan(VoicePack.Male, Spoken(line.deva, "hi", listOf(id))))
        // voice/lines.json says which voice records it.
        val json = File("../voice/lines.json").readText()
        assertTrue(json.contains("\"id\": \"${id}_f\", \"event\": \"${event.name}\""))
        assertTrue(json.contains("\"voice\": \"female\""))
    }

    @Test
    fun perVoiceWordingIsFamilySafeAndReviewed() {
        val doc = File("../docs/haryanvi-lines.md").readText()
        for ((event, lines) in HaryanviLines.allLines) {
            for (l in lines) {
                val f = l.devaF ?: continue
                val rf = l.romanF
                assertNotNull("$event female roman", rf)
                assertTrue(f.any { it in 'ऀ'..'ॿ' })
                assertFalse(rf!!.any { it in 'ऀ'..'ॿ' })
                assertTrue("review file lacks: $f", doc.contains(f))
                assertTrue("review file lacks: $rf", doc.contains(rf))
                for (bad in listOf("पागल", "उल्लू", "गधा", "बेटी", "शराब", "दारू")) assertFalse(f, f.contains(bad))
            }
        }
    }

    @Test
    fun fastagLowBalanceAndNightDriveEvents() {
        assertEquals(VoiceClips.recordedEvent(E.FastagLowBalance), E.FastagLowBalancePlain)
        val picker = LinePicker(Random(4))
        repeat(20) {
            val p = picker.pickIndexed(E.FastagLowBalance)!!
            val clips = VoiceClips.clipsFor(p, arrayOf("90")) { picker.pickIndexed(it) }
            assertTrue(clips.single(), clips.single().startsWith("fastag_low_balance_plain_"))
        }
        assertTrue(HaryanviLines.forEvent(E.FastagLowBalance)!!.any { it.deva.contains("%1\$s") })
        // Night drive lines are all recordable as they are.
        val night = HaryanviLines.forEvent(E.NightDrive)!!
        assertTrue(night.size >= 4)
        for (i in night.indices) assertTrue(VoiceClips.id(E.NightDrive, i) in VoiceClips.expectedHashes)
        // SMS are never read out. Calls are announced only by the opt-in round-4 CallAnnounce /
        // CallUnknown events (Settings › Announce calls), never by a generic "incoming" event.
        val names = HaryanviLines.Event.entries.map { it.name }
        for (bad in listOf("CallIncoming", "SmsIncoming")) assertFalse(bad, bad in names)
    }

    @Test
    fun fastagLowOnceADayUnderTheAlertLevel() {
        val r = FastagLowReminder()
        assertFalse(r.afterToll(null, 200, 1))
        assertFalse(r.afterToll(250.0, 200, 1))
        assertTrue(r.afterToll(90.0, 200, 1))
        assertFalse(r.afterToll(40.0, 200, 1))
        assertTrue(r.afterToll(40.0, 200, 2))
    }

    @Test
    fun nightDriveOnceAfterElevenAndHalfAnHour() {
        val min = 60_000L
        val r = NightDriveReminder(afterMs = 30 * min, quietMs = 6 * 60 * min)
        assertFalse(r.onSample(0, hour = 22, drivingMs = 90 * min))
        assertFalse(r.onSample(0, hour = 23, drivingMs = 20 * min))
        assertTrue(r.onSample(0, hour = 23, drivingMs = 31 * min))
        assertFalse(r.onSample(10 * min, hour = 23, drivingMs = 41 * min))
        // After a tea stop, the same night: still quiet.
        assertFalse(r.onSample(90 * min, hour = 1, drivingMs = 35 * min))
        // The next night.
        assertTrue(r.onSample(24 * 60 * min, hour = 2, drivingMs = 45 * min))
        assertTrue(NightDriveReminder.isNight(3))
        assertFalse(NightDriveReminder.isNight(4))
    }
}
