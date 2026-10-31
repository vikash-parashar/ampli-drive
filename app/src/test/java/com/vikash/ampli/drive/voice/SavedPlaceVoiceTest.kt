package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.nav.SavedKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedPlaceVoiceTest {

    private fun saved(text: String, lang: String = "hi") = (VoiceIntents.parse(text, lang) as? VoiceIntent.GoSaved)?.kind

    @Test
    fun homeInHinglishHindiHaryanviAndEnglish() {
        for (p in listOf(
            "ghar chalo", "ghar le chalo", "Ghar le chal", "mujhe ghar le chalo", "go home", "take me home",
            "navigate home", "hey ampli ghar chalo", "घर चलो", "घर ले चलो", "मुझे घर ले चलो", "घरां चाल",
        )) {
            assertEquals(p, SavedKind.Home, saved(p))
        }
        assertEquals(SavedKind.Home, saved("go home", "en"))
        assertEquals(SavedKind.Home, saved("take me home", "en"))
    }

    @Test
    fun office() {
        for (p in listOf("office chalo", "office le chalo", "go to office", "go to the office", "take me to work", "ऑफिस चलो", "दफ्तर ले चलो")) {
            assertEquals(p, SavedKind.Office, saved(p))
        }
        assertEquals(SavedKind.Office, saved("go to office", "en"))
    }

    @Test
    fun otherPlacesStayNormalSearches() {
        assertEquals(VoiceIntent.Navigate("office depot"), VoiceIntents.parse("go to office depot", "en"))
        assertEquals(VoiceIntent.Navigate("रोहतक"), VoiceIntents.parse("रोहतक ले चलो", "hi"))
        assertNull(saved("call home"))
        assertNull(saved("play ghar song"))
    }

    @Test
    fun answerAfterWhereTo() {
        assertEquals(SavedKind.Home, VoiceIntents.savedKind(VoiceIntents.destinationFrom("ghar", "hi")))
        assertEquals(SavedKind.Office, VoiceIntents.savedKind(VoiceIntents.destinationFrom("office please", "en")))
        assertNull(VoiceIntents.savedKind(VoiceIntents.destinationFrom("Phoenix Mall", "en")))
    }

    @Test
    fun newGrammarPhrasesParse() {
        for (p in listOf("go home", "go to office", "take me to office", "ghar chalo", "office chalo")) {
            assertEquals(p, true, p in VoskCommands.COMMANDS)
        }
        for (p in listOf("घर चलो", "ऑफिस चलो", "ऑफिस ले चलो", "दफ्तर चलो")) {
            assertEquals(p, true, p in VoskCommands.HINDI_COMMANDS)
            assertEquals(p, true, saved(p) != null)
        }
    }
}
