package com.vikash.ampli.drive.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunDefaultsTest {

    @Test
    fun voiceStyleIsHaryanviOnlyWhenUnset() {
        assertEquals(VoiceStyle.Haryanvi, FirstRunDefaults.voiceStyle(null))
        // A driver who picked a style keeps it.
        assertEquals(VoiceStyle.Standard, FirstRunDefaults.voiceStyle("standard"))
        assertEquals(VoiceStyle.Haryanvi, FirstRunDefaults.voiceStyle("haryanvi"))
    }

    @Test
    fun navPromptsAreOnOnlyWhenUnset() {
        assertTrue(FirstRunDefaults.navPrompts(null))
        assertFalse(FirstRunDefaults.navPrompts(false))
        assertTrue(FirstRunDefaults.navPrompts(true))
    }

    @Test
    fun hindiOnlyForAFreshInstallWithNoLanguageChoice() {
        assertEquals("hi", FirstRunDefaults.language(stored = null, freshInstall = true))
        // An update that followed the system language keeps following it.
        assertNull(FirstRunDefaults.language(stored = null, freshInstall = false))
        // A stored choice wins, including an explicit "System default" ("").
        assertNull(FirstRunDefaults.language(stored = "", freshInstall = true))
        assertNull(FirstRunDefaults.language(stored = "ta", freshInstall = true))
    }

    @Test
    fun wakeWordOnOnlyForAFreshInstallWithNothingStored() {
        assertEquals(true, FirstRunDefaults.voiceAlways(stored = null, freshInstall = true))
        // An explicit off (or on) is never overridden.
        assertNull(FirstRunDefaults.voiceAlways(stored = false, freshInstall = true))
        assertNull(FirstRunDefaults.voiceAlways(stored = true, freshInstall = true))
        // Updated installs keep what they had.
        assertNull(FirstRunDefaults.voiceAlways(stored = null, freshInstall = false))
    }
}
