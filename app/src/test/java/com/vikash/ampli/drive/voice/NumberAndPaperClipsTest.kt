package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NumberAndPaperClipsTest {
    private val picker = LinePicker(Random(5))
    private fun clips(e: HaryanviLines.Event, vararg args: Any) = VoiceClips.clipsFor(picker.pickIndexed(e)!!, args) { picker.pickIndexed(it) }

    @Test
    fun numbersOneToTenAreRecorded() {
        val ids = VoiceClips.expectedHashes.keys
        for (n in 1..10) assertTrue("num_$n", VoiceClips.countdownClip(n)!! in ids)
        assertNull(VoiceClips.countdownClip(0))
        assertNull(VoiceClips.countdownClip(11))
        assertEquals("पाँच", VoiceClips.manifest.first { it.id == "num_5" }.text)
    }

    @Test
    fun paperReminderIsNamePlusTail() {
        val due = clips(HaryanviLines.Event.PaperDue, "बीमा", "insurance")
        assertEquals("paper_insurance", due[0])
        assertTrue(due[1].startsWith("paper_due_tail_"))
        val expired = clips(HaryanviLines.Event.PaperExpired, "PUC", "puc")
        assertTrue(expired[1].startsWith("paper_expired_tail_"))
        assertTrue(clips(HaryanviLines.Event.PaperDue, "x", "unknown").isEmpty())
    }

    @Test
    fun passTripsUseNumberClipsUpToTen() {
        val c = clips(HaryanviLines.Event.PassTripsLow, 7)
        assertEquals("num_7", c[0])
        assertTrue(c[1].startsWith("pass_trips_tail_"))
        assertTrue(clips(HaryanviLines.Event.PassTripsLow, 25).isEmpty())
    }

    @Test
    fun everyPaperKindHasARecordedName() {
        for (k in com.vikash.ampli.drive.car.PaperKind.entries) {
            assertTrue(k.name, k.name.lowercase() in VoiceClips.PAPER_NAMES)
        }
    }
}
