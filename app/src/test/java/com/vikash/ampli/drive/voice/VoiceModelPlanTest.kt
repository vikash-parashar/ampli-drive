package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.voice.VoiceModelPlan.Part
import com.vikash.ampli.drive.voice.VoiceModelPlan.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModelPlanTest {

    @Test
    fun freshHindiInstallFetchesBothModels() {
        // FirstRunDefaults stores "hi" on a fresh install.
        assertEquals(VoiceModelPlan.Want(english = true, hindi = true), VoiceModelPlan.wanted("hi", "hi", englishRemoved = false, hindiRemoved = false))
    }

    @Test
    fun unsetLanguageAlsoFetchesHindi() {
        // Following the system language (even an English one): Haryanvi drivers still get Hindi.
        assertEquals(VoiceModelPlan.Want(english = true, hindi = true), VoiceModelPlan.wanted("", "en", englishRemoved = false, hindiRemoved = false))
    }

    @Test
    fun hindiFamilyLanguagesFetchHindi() {
        for (tag in listOf("hi", "bgc", "mai", "bho")) {
            val parser = VoiceLocales.parserLanguage(tag)
            assertTrue(tag, VoiceModelPlan.wanted(tag, parser, englishRemoved = false, hindiRemoved = false).hindi)
        }
    }

    @Test
    fun otherChosenLanguagesFetchOnlyEnglish() {
        for (tag in listOf("en", "ta", "de", "zh-CN")) {
            val w = VoiceModelPlan.wanted(tag, VoiceLocales.parserLanguage(tag), englishRemoved = false, hindiRemoved = false)
            assertTrue(tag, w.english)
            assertFalse(tag, w.hindi)
        }
    }

    @Test
    fun aModelTheDriverRemovedStaysRemoved() {
        assertEquals(VoiceModelPlan.Want(english = true, hindi = false), VoiceModelPlan.wanted("hi", "hi", englishRemoved = false, hindiRemoved = true))
        assertEquals(VoiceModelPlan.Want(english = false, hindi = true), VoiceModelPlan.wanted("hi", "hi", englishRemoved = true, hindiRemoved = false))
        assertEquals(VoiceModelPlan.Want(english = false, hindi = false), VoiceModelPlan.wanted("", "en", englishRemoved = true, hindiRemoved = true))
    }

    @Test
    fun retriesStartShortAndGrowToTheMaximum() {
        assertEquals(0L, VoiceModelPlan.retryDelayMs(0))
        assertEquals(30_000L, VoiceModelPlan.retryDelayMs(1))
        var last = 0L
        for (n in 1..10) {
            val d = VoiceModelPlan.retryDelayMs(n)
            assertTrue("delay must not shrink at $n", d >= last)
            last = d
        }
        assertEquals(VoiceModelPlan.MAX_RETRY_MS, VoiceModelPlan.retryDelayMs(50))
    }

    @Test
    fun backOffAfterAFailure() {
        val t = 1_000_000_000L
        assertFalse("never failed", VoiceModelPlan.backingOff(0, 0L, t))
        assertTrue(VoiceModelPlan.backingOff(1, t - 10_000L, t))
        assertFalse(VoiceModelPlan.backingOff(1, t - 31_000L, t))
        assertTrue(VoiceModelPlan.backingOff(9, t - 60 * 60_000L, t))
        assertFalse(VoiceModelPlan.backingOff(9, t - VoiceModelPlan.MAX_RETRY_MS - 1, t))
    }

    @Test
    fun networkBackSkipsTheBackOffOnlyForTheFirstFailures() {
        assertFalse(VoiceModelPlan.retryOnNetwork(0))
        assertTrue(VoiceModelPlan.retryOnNetwork(1))
        assertTrue(VoiceModelPlan.retryOnNetwork(VoiceModelPlan.NETWORK_RETRIES - 1))
        assertFalse(VoiceModelPlan.retryOnNetwork(VoiceModelPlan.NETWORK_RETRIES))
    }

    @Test
    fun modelPercentCoversDownloadThenUnpack() {
        assertEquals(0, VoiceModelPlan.modelPercent(Phase.Downloading, 0, 0, fromAssets = false))
        assertEquals(85, VoiceModelPlan.modelPercent(Phase.Downloading, 100, 0, fromAssets = false))
        assertEquals(85, VoiceModelPlan.modelPercent(Phase.Unpacking, 0, 0, fromAssets = false))
        assertEquals(100, VoiceModelPlan.modelPercent(Phase.Unpacking, 0, 100, fromAssets = false))
        // The Play asset pack: unpacking is the whole job.
        assertEquals(40, VoiceModelPlan.modelPercent(Phase.Unpacking, 0, 40, fromAssets = true))
        assertEquals(100, VoiceModelPlan.modelPercent(Phase.Ready, 0, 0, fromAssets = false))
        assertEquals(0, VoiceModelPlan.modelPercent(Phase.Failed, 70, 0, fromAssets = false))
    }

    @Test
    fun stripShowsOnlyWhileWorkingAndWeighsBySize() {
        assertNull(VoiceModelPlan.stripPercent(emptyList()))
        assertNull(VoiceModelPlan.stripPercent(listOf(Part(36, Phase.Ready, 100), Part(42, Phase.Ready, 100))))
        // Waiting for a network: nothing moves, so no strip.
        assertNull(VoiceModelPlan.stripPercent(listOf(Part(36, Phase.Missing, 0), Part(42, Phase.Failed, 0))))
        assertEquals(40, VoiceModelPlan.stripPercent(listOf(Part(50, Phase.Downloading, 80), Part(50, Phase.Missing, 0))))
        // English ready, Hindi half way: (36*100 + 42*50) / 78 = 73.
        assertEquals(73, VoiceModelPlan.stripPercent(listOf(Part(36, Phase.Ready, 100), Part(42, Phase.Unpacking, 50))))
        // Never shows 100 while still working.
        assertEquals(99, VoiceModelPlan.stripPercent(listOf(Part(36, Phase.Unpacking, 100))))
    }
}
