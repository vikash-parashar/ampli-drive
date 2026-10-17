package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SmallTalkIntentsTest {

    private fun talk(text: String, lang: String = "hi") = VoiceIntents.parse(text, lang)

    @Test
    fun howAreYou() {
        for (t in listOf("के हाल सै", "क्या हाल है", "कैसे हो", "how are you", "ke haal sai", "hey ampli kaise ho")) {
            assertEquals(t, VoiceIntent.SmallTalk(VoiceIntent.Talk.HowAreYou), talk(t))
        }
    }

    @Test
    fun whoAreYou() {
        for (t in listOf("कौन सै तू", "तू कौन है", "who are you", "what is your name", "tera naam kya hai")) {
            assertEquals(t, VoiceIntent.SmallTalk(VoiceIntent.Talk.WhoAreYou), talk(t))
        }
    }

    @Test
    fun jokeBeatsPlay() {
        // "सुनाओ" is also a play word: a joke request must not search for a song called "चुटकुला".
        for (t in listOf("चुटकला सुणा", "चुटकुला सुनाओ", "tell me a joke", "ek chutkula sunao")) {
            assertEquals(t, VoiceIntent.SmallTalk(VoiceIntent.Talk.Joke), talk(t))
        }
    }

    @Test
    fun commandsStillWin() {
        assertEquals(VoiceIntent.Navigate("rohtak"), VoiceIntents.parse("navigate to rohtak", "en"))
        assertEquals(VoiceIntent.Greeting, VoiceIntents.parse("namaste", "hi"))
    }
}
