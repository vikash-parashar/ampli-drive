package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.prefs.VoiceStyle
import com.vikash.ampli.drive.voice.HaryanviLines.Event as E
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class VoiceClipsTest {

    private fun libraryWith(voice: VoicePack, vararg ids: String) =
        ClipLibrary(mapOf(voice to ids.associateWith { VoiceClips.expectedHashes.getValue(it) }))

    @Test
    fun idsAreStableAndUnique() {
        assertEquals("welcome_01", VoiceClips.id(E.Welcome, 0))
        assertEquals("nav_sharp_left_03", VoiceClips.id(E.NavSharpLeft, 2))
        assertEquals("dist_400m", VoiceClips.bucket(400).id)
        val ids = VoiceClips.manifest.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (e in VoiceClips.manifest) {
            assertTrue(e.id, e.id.matches(Regex("[a-z0-9_]+")))
            assertFalse("${e.id} has a blank: ${e.text}", e.text.contains('%'))
        }
    }

    @Test
    fun manifestSkipsNamesAndNumbersButKeepsStandIns() {
        val events = VoiceClips.manifest.map { it.event }.toSet()
        for (e in listOf(E.Searching, E.StartingNav, E.RouteFound, E.NavSoon, E.SpeedLimit,
            E.CameraAhead, E.CameraAheadNoLimit, E.TollPaid, E.TollPaidNoBalance, E.BreakReminder, E.MusicPlayNamed)) {
            assertFalse("$e should not be recorded", e.name in events)
        }
        for (e in listOf(E.Welcome, E.SearchingPlain, E.TollPaidPlain, E.NavSoonTail, E.CameraAheadTail, E.NavRoundaboutExit)) {
            assertTrue("$e missing", e.name in events)
        }
        assertFalse(events.contains(E.Sos.name))
        assertFalse(events.contains(E.Crash.name))
        // Roundabout exits are whole sentences with the number in words.
        val exit3 = VoiceClips.manifest.first { it.id == "nav_roundabout_exit_01_x3" }
        assertTrue(exit3.text, exit3.text.contains("तीन"))
        assertEquals(VoiceClips.DISTANCES.size, VoiceClips.manifest.count { it.event == "Distance" })
    }

    @Test
    fun distanceRoundsToNearestRecordedBucket() {
        assertEquals(50, VoiceClips.bucket(0).meters)
        assertEquals(50, VoiceClips.bucket(60).meters)
        assertEquals(100, VoiceClips.bucket(110).meters)
        assertEquals(400, VoiceClips.bucket(400).meters)
        assertEquals(450, VoiceClips.bucket(437).meters)
        assertEquals(500, VoiceClips.bucket(540).meters)
        assertEquals(600, VoiceClips.bucket(560).meters)
        // A tie goes to the shorter distance.
        assertEquals(500, VoiceClips.bucket(550).meters)
        assertEquals(1000, VoiceClips.bucket(1100).meters)
        assertEquals(1500, VoiceClips.bucket(1300).meters)
        assertEquals(4000, VoiceClips.bucket(4400).meters)
        assertEquals(10000, VoiceClips.bucket(25000).meters)
        assertEquals("चार सौ मीटर", VoiceClips.bucket(410).deva)
    }

    @Test
    fun clipLookupNeedsTheCurrentText() {
        val lib = libraryWith(VoicePack.Male, "welcome_01")
        assertTrue(lib.has(VoicePack.Male, "welcome_01"))
        assertFalse(lib.has(VoicePack.Female, "welcome_01"))
        assertFalse(lib.has(VoicePack.Male, "welcome_02"))
        // A clip recorded from an older wording is not played.
        val stale = ClipLibrary(mapOf(VoicePack.Male to mapOf("welcome_01" to "000000000000")))
        assertFalse(stale.has(VoicePack.Male, "welcome_01"))
        assertEquals(0, stale.count(VoicePack.Male))
        // Unknown ids never count.
        val unknown = ClipLibrary(mapOf(VoicePack.Male to mapOf("nope_01" to "x")))
        assertEquals(0, unknown.count(VoicePack.Male))
        assertEquals(1, lib.count(VoicePack.Male))
    }

    @Test
    fun fallsBackToTtsWhenAnyClipIsMissing() {
        val lib = libraryWith(VoicePack.Male, "welcome_01", "welcome_morning_01")
        val both = Spoken("a b", "hi", listOf("welcome_01", "welcome_morning_01"))
        assertEquals(both.clips, lib.plan(VoicePack.Male, both))
        assertNull(lib.plan(VoicePack.Tts, both))
        assertNull(lib.plan(VoicePack.Female, both))
        assertNull(lib.plan(VoicePack.Male, both.copy(clips = listOf("welcome_01", "welcome_morning_02"))))
        assertNull(lib.plan(VoicePack.Male, Spoken("plain", "hi")))
        assertNull(ClipLibrary.EMPTY.plan(VoicePack.Male, both))
    }

    @Test
    fun joiningKeepsClipsOnlyWhenBothHaveThem() {
        val a = Spoken("a", "hi", listOf("x"))
        val b = Spoken("b", "hi", listOf("y"))
        assertEquals(listOf("x", "y"), (a + b).clips)
        assertEquals("a b", (a + b).text)
        assertTrue((a + Spoken("c", "hi")).clips.isEmpty())
    }

    @Test
    fun voiceSettingDefault() {
        assertEquals(VoicePack.Tts, VoicePack.resolve(null, maleClips = 0))
        assertEquals(VoicePack.Male, VoicePack.resolve(null, maleClips = 300))
        assertEquals(VoicePack.Tts, VoicePack.resolve(VoicePack.Tts, maleClips = 300))
        assertEquals(VoicePack.Female, VoicePack.resolve(VoicePack.Female, maleClips = 0))
        assertNull(VoicePack.fromKey(null))
        assertNull(VoicePack.fromKey("robot"))
        assertEquals(VoicePack.Female, VoicePack.fromKey("female"))
        assertFalse(VoicePack.Tts.human)
    }

    @Test
    fun standInsForNamesAndNumbers() {
        val picker = LinePicker(Random(3))
        fun clips(e: HaryanviLines.Event, vararg args: Any): List<String> =
            VoiceClips.clipsFor(picker.pickIndexed(e)!!, args) { picker.pickIndexed(it) }

        assertTrue(clips(E.TollPaid, "145", "855").single().startsWith("toll_paid_plain_"))
        assertTrue(clips(E.TollPaidNoBalance, "145").single().startsWith("toll_paid_plain_"))
        assertTrue(clips(E.Searching, "Rohtak").single().startsWith("searching_plain_"))
        assertTrue(clips(E.MusicPlayNamed, "song").single().startsWith("music_play_0"))
        assertTrue(clips(E.SpeedLimit, 60).single().startsWith("speed_limit_plain_"))
        val camera = clips(E.CameraAhead, 300, 60)
        assertEquals("dist_300m", camera[0])
        assertTrue(camera[1].startsWith("camera_ahead_tail_"))
        assertEquals("dist_450m", clips(E.CameraAheadNoLimit, 437)[0])
        assertTrue(clips(E.NavRoundaboutExit, 2).single().endsWith("_x2"))
        assertTrue(clips(E.NavRoundaboutExit, 9).single().startsWith("nav_roundabout_0"))
        assertTrue(clips(E.Farewell).single().startsWith("farewell_"))
        // Every clip id handed out is in the manifest.
        val known = VoiceClips.expectedHashes.keys
        for (e in HaryanviLines.Event.entries.filter { !it.safety }) {
            repeat(10) {
                for (id in clips(e, *HaryanviLines.sampleArgs(e))) assertTrue("$e → $id", id in known)
            }
        }
    }

    @Test
    fun welcomeNeverSaysTheNameButTheCardDoes() {
        for (count in 0..7) {
            for (hindi in listOf(true, false)) {
                val w = WelcomeLines.select(VoiceStyle.Haryanvi, "Vikash", count, hindiVoice = hindi, hindiScreen = true)
                assertFalse(w.text, w.text.contains("Vikash"))
                assertEquals("Ram Ram Vikash Ji 🙏", w.card)
                assertEquals(2, w.clips.size)
                assertTrue(w.clips[0].startsWith("welcome_0"))
                for (id in w.clips) assertTrue(id, id in VoiceClips.expectedHashes)
            }
            val std = WelcomeLines.select(VoiceStyle.Standard, "Vikash", count, hindiVoice = true, hindiScreen = true)
            assertFalse(std.text.contains("Vikash"))
            assertTrue(std.card.contains("Vikash"))
        }
    }

    @Test
    fun seatBeltOncePerTrip() {
        val r = com.vikash.ampli.drive.car.SeatBeltReminder(quietMs = 1000)
        assertFalse(r.onSample(0, driving = false))
        assertTrue(r.onSample(10, driving = true))
        assertFalse(r.onSample(20, driving = true))
        // A short park and drive on: quiet.
        assertFalse(r.onSample(30, driving = false))
        assertFalse(r.onSample(40, driving = true))
        // Much later, a new stint: remind again.
        assertFalse(r.onSample(2000, driving = false))
        assertTrue(r.onSample(2100, driving = true))
        // Ignition on starts a new trip.
        r.newTrip()
        assertTrue(r.onSample(2200, driving = true))
    }

    @Test
    fun hashMatchesTheWorkflowScript() {
        // scripts/voice_pack.py uses hashlib.sha1(text.encode()).hexdigest()[:12].
        assertEquals("a9993e364706", VoiceClips.hash("abc"))
    }
}
