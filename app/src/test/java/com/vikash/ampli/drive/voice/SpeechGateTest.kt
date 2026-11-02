package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.calls.VoiceReplies
import com.vikash.ampli.drive.nav.SavedKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechGateTest {
    private fun w(vararg pairs: Pair<String, Double>) = pairs.map { HeardWord(it.first, it.second) }

    @Test
    fun mostlyUnknownCommandIsNoise() {
        // Music forced onto "navigate" between unknown sounds.
        assertFalse(SpeechGate.accept("[unk] navigate [unk]", w("[unk]" to 0.5, "navigate" to 0.9, "[unk]" to 0.5), ListenMode.Commands))
        assertTrue(SpeechGate.accept("[unk] next song", w("[unk]" to 0.5, "next" to 0.9, "song" to 0.8), ListenMode.Commands))
        assertFalse(SpeechGate.accept("[unk]", w("[unk]" to 1.0), ListenMode.Commands))
        assertFalse(SpeechGate.accept("", emptyList(), ListenMode.Commands))
    }

    @Test
    fun lowConfidenceCommandIsNoise() {
        assertFalse(SpeechGate.accept("bluetooth settings", w("bluetooth" to 0.4, "settings" to 0.5), ListenMode.Commands))
        assertTrue(SpeechGate.accept("bluetooth settings", w("bluetooth" to 0.9, "settings" to 0.8), ListenMode.Commands))
    }

    @Test
    fun repliesIgnoreUnknownAroundTheAnswer() {
        // "haan" over the ringtone.
        val words = w("[unk]" to 0.3, "[unk]" to 0.3, "haan" to 0.85, "[unk]" to 0.3)
        assertTrue(SpeechGate.accept("[unk] [unk] haan [unk]", words, ListenMode.Replies))
        assertFalse(SpeechGate.accept("haan", w("haan" to 0.5), ListenMode.Replies))
    }

    @Test
    fun freeSpeechDropsFillersAndMumbles() {
        assertFalse(SpeechGate.accept("the", w("the" to 0.9), ListenMode.Free))
        assertFalse(SpeechGate.accept("uh the", w("uh" to 0.9, "the" to 0.9), ListenMode.Free))
        assertFalse(SpeechGate.accept("rohtak", w("rohtak" to 0.3), ListenMode.Free))
        assertTrue(SpeechGate.accept("the rohtak", w("the" to 0.8, "rohtak" to 0.7), ListenMode.Free))
    }

    @Test
    fun withoutWordListTheTextDecides() {
        assertTrue(SpeechGate.accept("next song", emptyList(), ListenMode.Commands))
        assertFalse(SpeechGate.accept("[unk]", emptyList(), ListenMode.Commands))
    }

    @Test
    fun qualityCountsKnownAndUnknown() {
        val q = SpeechGate.quality(w("[unk]" to 0.1, "open" to 0.8, "maps" to 0.6))
        assertEquals(2, q.known)
        assertEquals(1, q.unknown)
        assertEquals(0.7, q.meanConf, 1e-9)
    }

    @Test
    fun appOpeningIntentsAreMarked() {
        assertTrue(SpeechGate.launches(VoiceIntent.OpenMaps))
        assertTrue(SpeechGate.launches(VoiceIntent.BluetoothSettings))
        assertTrue(SpeechGate.launches(VoiceIntent.OpenAndroidAuto))
        assertTrue(SpeechGate.launches(VoiceIntent.GoSaved(SavedKind.Home)))
        assertTrue(SpeechGate.launches(VoiceIntent.Navigate("rohtak")))
        // "Navigate" alone only asks "kahan chalna hai?".
        assertFalse(SpeechGate.launches(VoiceIntent.Navigate("")))
        assertFalse(SpeechGate.launches(VoiceIntent.Next))
        assertFalse(SpeechGate.launches(VoiceIntent.Sos))
        assertFalse(SpeechGate.launches(VoiceIntent.TimeNow))
    }

    @Test
    fun launchingNeedsClearWords() {
        assertTrue(SpeechGate.mayLaunch(null))
        assertTrue(SpeechGate.mayLaunch(SpeechQuality(known = 2, unknown = 0, meanConf = 0.8)))
        assertFalse(SpeechGate.mayLaunch(SpeechQuality(known = 2, unknown = 1, meanConf = 0.95)))
        assertFalse(SpeechGate.mayLaunch(SpeechQuality(known = 2, unknown = 0, meanConf = 0.7)))
        assertTrue(SpeechGate.mayLaunch(SpeechQuality(known = 1, unknown = 0, meanConf = 0.9)))
        assertFalse(SpeechGate.mayLaunch(SpeechQuality(known = 1, unknown = 0, meanConf = 0.8)))
        assertFalse(SpeechGate.mayLaunch(SpeechQuality(known = 0, unknown = 0, meanConf = 0.0)))
    }

    @Test
    fun destinationNeedsLettersAndClarity() {
        assertFalse(SpeechGate.destinationOk("a", null))
        assertTrue(SpeechGate.destinationOk("rohtak", null))
        assertTrue(SpeechGate.destinationOk("rohtak", SpeechQuality(1, 0, 0.8)))
        assertFalse(SpeechGate.destinationOk("rohtak", SpeechQuality(1, 0, 0.6)))
    }

    @Test
    fun cooldownAfterAnAction() {
        assertFalse(SpeechGate.coolingDown(0L, 10_000L))
        assertTrue(SpeechGate.coolingDown(10_000L, 12_000L))
        assertFalse(SpeechGate.coolingDown(10_000L, 10_000L + SpeechGate.ACTION_COOLDOWN_MS))
        // Clock went backwards (reboot): not cooling.
        assertFalse(SpeechGate.coolingDown(10_000L, 5_000L))
    }

    @Test
    fun everyReplyPhraseMeansSomething() {
        for ((hindi, lang) in listOf(false to "en", true to "hi")) {
            for (p in VoskCommands.replyPhrases(hindi)) {
                val understood = VoiceReplies.call(p) != VoiceReplies.Call.Unknown ||
                    VoiceReplies.message(p) != VoiceReplies.Message.Unknown ||
                    VoiceReplies.yesNo(p) != VoiceReplies.YesNo.Unknown ||
                    VoiceIntents.parse(p, lang).let { it == VoiceIntent.Cancel || it == VoiceIntent.Stop }
                assertTrue(p, understood)
            }
        }
        assertTrue(VoskCommands.replyGrammarJson().endsWith("\"[unk]\"]"))
    }

    @Test
    fun unspokenLinesHoldBriefly() {
        assertEquals(SpeechPacing.SILENT_MIN_MS, SpeechPacing.silentHoldMs(0))
        assertEquals(SpeechPacing.SILENT_MAX_MS, SpeechPacing.silentHoldMs(500))
        assertTrue(SpeechPacing.silentHoldMs(40) in SpeechPacing.SILENT_MIN_MS..SpeechPacing.SILENT_MAX_MS)
    }

    @Test
    fun ttsCardOnlyOnceAndParked() {
        assertTrue(TtsNotice.showCard(ttsMissing = true, shownBefore = false, moving = false))
        assertFalse(TtsNotice.showCard(ttsMissing = true, shownBefore = true, moving = false))
        assertFalse(TtsNotice.showCard(ttsMissing = true, shownBefore = false, moving = true))
        assertFalse(TtsNotice.showCard(ttsMissing = false, shownBefore = false, moving = false))
    }

    @Test
    fun noSystemRecognizerGoesStraightOffline() {
        assertTrue(RecognizerFallback.straightOffline(systemPresent = false, deadTurns = 0, offlineReady = true))
        assertFalse(RecognizerFallback.straightOffline(systemPresent = true, deadTurns = 0, offlineReady = true))
        assertTrue(RecognizerFallback.straightOffline(systemPresent = true, deadTurns = RecognizerFallback.DEAD_AFTER, offlineReady = true))
        assertFalse(RecognizerFallback.straightOffline(systemPresent = false, deadTurns = 0, offlineReady = false))
    }
}
