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

class VoskHindiCommandsTest {

    @Test
    fun everyHindiPhraseParses() {
        for (p in VoskCommands.HINDI_COMMANDS) {
            assertFalse(p, VoiceIntents.parse(p, "hi") is VoiceIntent.Unknown)
        }
    }

    @Test
    fun hindiChoicesParseWhileAListIsShowing() {
        for (p in VoskCommands.HINDI_CHOICES) {
            assertTrue(p, VoiceIntents.parse(p, "hi", expectingChoice = true) is VoiceIntent.ChooseOption)
        }
    }

    @Test
    fun hindiGrammarIsDevanagari() {
        val g = VoskCommands.grammarJson(hindi = true)
        assertTrue(g.contains("\"अगला गाना\""))
        assertFalse(g.contains("\"next song\""))
        assertTrue(g.endsWith("\"[unk]\"]"))
    }

    @Test
    fun hinglishPhrasesParseInEveryHindiFamilyAndEnglish() {
        // The English model serves Hindi-speaking drivers until the Hindi model is downloaded.
        for (p in VoskCommands.HINGLISH_COMMANDS) {
            assertFalse(p, VoiceIntents.parse(p, "en") is VoiceIntent.Unknown)
            assertFalse(p, VoiceIntents.parse(p, "hi") is VoiceIntent.Unknown)
        }
    }

    @Test
    fun everyEnglishGrammarPhraseIsUnderstood() {
        // Each phrase is a command, a choice (while a list shows) or a volume follow-up.
        for (p in VoskCommands.phrases(hindi = false)) {
            val understood = VoiceIntents.parse(p, "hi") !is VoiceIntent.Unknown ||
                VoiceIntents.parse(p, "hi", expectingChoice = true) is VoiceIntent.ChooseOption ||
                VoiceIntents.volumeFollowUp(p, VoiceIntent.VolumeAction.Up) != null
            assertTrue(p, understood)
        }
    }

    @Test
    fun englishGrammarHoldsTheFieldCommands() {
        val g = VoskCommands.grammarJson()
        for (p in listOf("gaana chalao", "awaaz badhao", "le chalo", "cng pump kahan hai", "play music")) {
            assertTrue(p, g.contains("\"$p\""))
        }
        assertTrue(g.endsWith("\"[unk]\"]"))
        val phrases = VoskCommands.phrases()
        assertEquals(phrases.size, phrases.toSet().size)
    }

    @Test
    fun englishGrammarStaysRoman() {
        // The English model cannot hear Devanagari words; they would only be dropped.
        for (p in VoskCommands.phrases(hindi = false)) {
            assertTrue(p, p.all { it.code < 128 })
        }
    }
}
