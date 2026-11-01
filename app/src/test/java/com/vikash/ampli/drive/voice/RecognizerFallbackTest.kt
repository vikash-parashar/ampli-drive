package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizerFallbackTest {

    @Test
    fun quietStubFallsThroughToOffline() {
        assertTrue(RecognizerFallback.fallThrough(SystemAttempt.QuietNoActivity, offlineReady = true))
        assertTrue(RecognizerFallback.fallThrough(SystemAttempt.Failed, offlineReady = true))
    }

    @Test
    fun workingEngineKeepsItsResult() {
        // Google heard the driver (or heard sound but no words): nothing changes on phones.
        assertFalse(RecognizerFallback.fallThrough(SystemAttempt.Heard, offlineReady = true))
        assertFalse(RecognizerFallback.fallThrough(SystemAttempt.QuietWithActivity, offlineReady = true))
        assertFalse(RecognizerFallback.fallThrough(SystemAttempt.LanguageMissing, offlineReady = true))
    }

    @Test
    fun noFallThroughWithoutOfflineModel() {
        for (a in SystemAttempt.entries) assertFalse(a.name, RecognizerFallback.fallThrough(a, offlineReady = false))
    }

    @Test
    fun skipsSystemAfterTwoDeadTurns() {
        var dead = 0
        dead = RecognizerFallback.nextDeadTurns(dead, SystemAttempt.QuietNoActivity)
        assertFalse(RecognizerFallback.skipSystem(dead, offlineReady = true))
        dead = RecognizerFallback.nextDeadTurns(dead, SystemAttempt.Failed)
        assertTrue(RecognizerFallback.skipSystem(dead, offlineReady = true))
        // Never skip it when there is nothing offline to use instead.
        assertFalse(RecognizerFallback.skipSystem(dead, offlineReady = false))
    }

    @Test
    fun anySignOfLifeResetsTheCount() {
        assertEquals(0, RecognizerFallback.nextDeadTurns(1, SystemAttempt.Heard))
        assertEquals(0, RecognizerFallback.nextDeadTurns(1, SystemAttempt.QuietWithActivity))
        assertEquals(1, RecognizerFallback.nextDeadTurns(1, SystemAttempt.LanguageMissing))
        assertEquals(2, RecognizerFallback.nextDeadTurns(1, SystemAttempt.QuietNoActivity))
    }

    @Test
    fun eachStartGivesTheSystemEngineOneMoreTry() {
        assertEquals(0, RecognizerFallback.restoredDeadTurns(0))
        assertEquals(1, RecognizerFallback.restoredDeadTurns(1))
        assertEquals(RecognizerFallback.DEAD_AFTER - 1, RecognizerFallback.restoredDeadTurns(5))
        assertEquals(0, RecognizerFallback.restoredDeadTurns(-3))
        // One more dead turn after a restart and it is skipped again.
        val afterOneMore = RecognizerFallback.nextDeadTurns(RecognizerFallback.restoredDeadTurns(5), SystemAttempt.QuietNoActivity)
        assertTrue(RecognizerFallback.skipSystem(afterOneMore, offlineReady = true))
    }

    @Test
    fun afterGreetingCommandsWinOverWhereTo() {
        // Heard by the offline grammar: these are commands, so no "where to?".
        for (p in listOf("gaana chalao", "awaaz badhao", "cng pump kahan hai", "le chalo", "play music", "गाना चलाओ", "आवाज बढ़ाओ")) {
            assertFalse(p, RecognizerFallback.offlineNeedsWhereTo(p, "hi"))
        }
        // Silence ("[unk]" only) or an unknown word: ask for the place and listen freely.
        assertTrue(RecognizerFallback.offlineNeedsWhereTo(null, "hi"))
        assertTrue(RecognizerFallback.offlineNeedsWhereTo("", "hi"))
        assertTrue(RecognizerFallback.offlineNeedsWhereTo("rohtak", "hi"))
        assertTrue(RecognizerFallback.offlineNeedsWhereTo("badhao", "en"))
    }

    @Test
    fun parsedCommandBeatsDestination() {
        // While a place is expected, VoiceIntents still parses commands first (VoiceAssistant only
        // treats Unknown as a place name).
        assertEquals(VoiceIntent.Resume, VoiceIntents.parse("gaana chalao", "hi"))
        assertEquals(VoiceIntent.Volume(VoiceIntent.VolumeAction.Up), VoiceIntents.parse("awaaz badhao", "hi"))
        assertEquals(VoiceIntent.FindFuel(com.vikash.ampli.drive.fuel.FuelFilter.Cng), VoiceIntents.parse("CNG pump kahan hai", "hi"))
        assertEquals(VoiceIntent.Navigate("rohtak"), VoiceIntents.parse("Rohtak le chalo", "hi"))
        assertTrue(VoiceIntents.parse("Rohtak", "hi") is VoiceIntent.Unknown)
    }

    @Test
    fun hindiModelDownloadsByItselfOnlyWhenNeeded() {
        assertTrue(RecognizerFallback.wantHindiModel("hi", systemWorks = false, removedByUser = false))
        assertFalse(RecognizerFallback.wantHindiModel("hi", systemWorks = true, removedByUser = false))
        assertFalse(RecognizerFallback.wantHindiModel("hi", systemWorks = false, removedByUser = true))
        assertFalse(RecognizerFallback.wantHindiModel("en", systemWorks = false, removedByUser = false))
        assertFalse(RecognizerFallback.wantHindiModel("ta", systemWorks = false, removedByUser = false))
    }

    @Test
    fun offlineEnginesAreMarked() {
        assertTrue(VoiceEngine.OfflineEnglish.offline)
        assertTrue(VoiceEngine.OfflineHindi.offline)
        assertFalse(VoiceEngine.System.offline)
        assertFalse(VoiceEngine.None.offline)
    }
}
