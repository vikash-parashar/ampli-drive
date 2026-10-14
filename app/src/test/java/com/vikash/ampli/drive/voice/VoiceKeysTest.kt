package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceKeysTest {
    @Test
    fun keyCodesMatchAndroid() {
        // android.view.KeyEvent values.
        assertEquals(79, VoiceKeys.KEYCODE_HEADSETHOOK)
        assertEquals(84, VoiceKeys.KEYCODE_SEARCH)
        assertEquals(85, VoiceKeys.KEYCODE_MEDIA_PLAY_PAUSE)
        assertEquals(231, VoiceKeys.KEYCODE_VOICE_ASSIST)
    }

    @Test
    fun voiceAndLongPressKeys() {
        assertTrue(VoiceKeys.isVoiceKey(231))
        assertTrue(VoiceKeys.isVoiceKey(84))
        assertFalse(VoiceKeys.isVoiceKey(79))
        assertTrue(VoiceKeys.isLongPressVoiceKey(79))
        assertTrue(VoiceKeys.isLongPressVoiceKey(85))
        assertFalse(VoiceKeys.isLongPressVoiceKey(231))
    }

    @Test
    fun assistIntents() {
        assertTrue(VoiceKeys.isVoiceAction("android.intent.action.VOICE_COMMAND"))
        assertTrue(VoiceKeys.isVoiceAction("android.intent.action.ASSIST"))
        assertTrue(VoiceKeys.isVoiceAction("android.speech.action.WEB_SEARCH"))
        // "Web search" on selected text in another app is not a voice key.
        assertFalse(VoiceKeys.isVoiceAction("android.speech.action.WEB_SEARCH", "petrol pump"))
        assertFalse(VoiceKeys.isVoiceAction("android.intent.action.MAIN"))
        assertFalse(VoiceKeys.isVoiceAction(null))
    }
}
