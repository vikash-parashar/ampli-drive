package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** "Hey Ampli, save kar": the dashcam keeps the current clip; nothing else is taken over. */
class DashcamVoiceTest {
    @Test
    fun saveCommands() {
        assertEquals(VoiceIntent.DashcamSave, VoiceIntents.parse("save kar"))
        assertEquals(VoiceIntent.DashcamSave, VoiceIntents.parse("video save karo", "hi"))
        assertEquals(VoiceIntent.DashcamSave, VoiceIntents.parse("save the video"))
        assertEquals(VoiceIntent.DashcamSave, VoiceIntents.parse("सेव कर", "hi"))
        assertEquals(VoiceIntent.DashcamSave, VoiceIntents.parse("क्लिप सेव कर दे", "hi"))
    }

    @Test
    fun sosAndMusicStillWin() {
        assertEquals(VoiceIntent.Sos, VoiceIntents.parse("help ambulance"))
        assertNotEquals(VoiceIntent.DashcamSave, VoiceIntents.parse("play save your tears"))
        assertNotEquals(VoiceIntent.DashcamSave, VoiceIntents.parse("navigate to connaught place"))
    }
}
