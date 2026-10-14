package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoskCommandsTest {

    @Test
    fun everyCommandPhraseParses() {
        for (p in VoskCommands.COMMANDS) {
            assertFalse(p, VoiceIntents.parse(p, "en") is VoiceIntent.Unknown)
        }
    }

    @Test
    fun choicesParseWhileAListIsShowing() {
        for (p in VoskCommands.CHOICES) {
            assertTrue(p, VoiceIntents.parse(p, "en", expectingChoice = true) is VoiceIntent.ChooseOption)
        }
    }

    @Test
    fun grammarEndsWithUnknownToken() {
        assertTrue(VoskCommands.grammarJson().contains("\"[unk]\""))
    }

    @Test
    fun cleanDropsUnknownTokens() {
        assertEquals("next song", VoskCommands.clean("[unk] next  song [unk]"))
        assertEquals("", VoskCommands.clean("[unk]"))
    }
}
