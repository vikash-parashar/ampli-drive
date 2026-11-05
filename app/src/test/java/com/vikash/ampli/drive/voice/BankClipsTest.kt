package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Small talk, jokes and plain replies in the recorded voice pack (BankClips). */
class BankClipsTest {

    private val manifest = VoiceClips.manifest.associateBy { it.id }

    @Test
    fun manifestIdsStayUnique() {
        val ids = VoiceClips.manifest.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (e in BankClips.entries) assertTrue(e.id, e.id.matches(Regex("[a-z0-9_]+")))
    }

    @Test
    fun everyChatLineHasAClip() {
        for ((topic, lines) in ChatLines.all) {
            lines.forEachIndexed { i, line ->
                val id = BankClips.chatId(topic, i)
                assertEquals(id, line.deva, manifest.getValue(id).text)
                assertEquals(listOf(id), BankClips.clipsFor(line, ChatLines.Script.Deva))
                // The Latin wording is the same line: the recording says it in Devanagari.
                assertEquals(listOf(id), BankClips.clipsFor(line, ChatLines.Script.Roman))
                assertEquals(emptyList<String>(), BankClips.clipsFor(line, ChatLines.Script.English))
            }
        }
        assertEquals("chat_how_are_you_01", BankClips.chatId(ChatLines.Topic.HowAreYou, 0))
        assertEquals("chat_news_no_hindi_voice_01", BankClips.chatId(ChatLines.Topic.NewsNoHindiVoice, 0))
    }

    @Test
    fun everyJokeHasAClip() {
        Jokes.ALL.forEachIndexed { i, joke ->
            val id = BankClips.jokeId(i)
            assertEquals(joke.deva, manifest.getValue(id).text)
            assertEquals("Jokes", manifest.getValue(id).group)
            assertEquals(listOf(id), BankClips.clipsFor(joke, ChatLines.Script.Deva))
        }
        assertEquals("joke_01", BankClips.jokeId(0))
    }

    @Test
    fun everyDesiReplyWithoutABlankHasAClip() {
        var withBlank = 0
        for ((name, text) in DesiReplies.LINES) {
            val id = BankClips.desiId(name)
            if (text.contains('%')) {
                // Filled in at run time: never recorded, always the phone voice.
                withBlank++
                assertNull(id, manifest[id])
                assertEquals(emptyList<String>(), BankClips.clipsFor(text))
            } else {
                assertEquals(text, manifest.getValue(id).text)
                assertEquals(listOf(id), BankClips.clipsFor(text))
            }
        }
        assertTrue(withBlank > 0)
        assertEquals(listOf("desi_voice_nav_stopped"), BankClips.clipsFor(DesiReplies.LINES.getValue("voice_nav_stopped")))
        assertEquals(emptyList<String>(), BankClips.clipsFor("कोए और बात"))
    }

    @Test
    fun howAreYouNeedsBothClips() {
        val greet = ChatLines.lines(ChatLines.Topic.Morning)[0]
        val answer = ChatLines.lines(ChatLines.Topic.HowAreYou)[1]
        assertEquals(listOf("chat_morning_01", "chat_how_are_you_02"), BankClips.clipsFor(listOf(greet, answer), ChatLines.Script.Deva))
        val unknown = ChatLines.Line("नई बात", "Nayi baat", "Something new")
        assertEquals(emptyList<String>(), BankClips.clipsFor(listOf(greet, unknown), ChatLines.Script.Deva))
        assertEquals(greet.deva + " " + answer.deva, ChatLines.howAreYou(greet, answer, ChatLines.Script.Deva))
    }

    private fun library(voice: VoicePack, vararg ids: String, hash: (String) -> String = { VoiceClips.expectedHashes.getValue(it) }) =
        ClipLibrary(mapOf(voice to ids.associateWith(hash)))

    @Test
    fun playsTheClipWhenThePickedVoiceHasIt() {
        val joke = Jokes.ALL[0]
        val clips = BankClips.clipsFor(joke, ChatLines.Script.Deva)
        val lib = library(VoicePack.Female, "joke_01")
        val spoken = BankClips.recorded(lib, VoicePack.Female, joke.deva, "hi", clips)
        assertNotNull(spoken)
        assertEquals(listOf("joke_01"), spoken!!.clips)
        assertEquals(joke.deva, spoken.text)
        assertEquals(listOf("joke_01"), lib.plan(VoicePack.Female, spoken))
    }

    @Test
    fun fallsBackToTtsWithoutAnUpToDateClip() {
        val joke = Jokes.ALL[0]
        val clips = listOf("joke_01")
        // Recorded for the other voice only.
        assertNull(BankClips.recorded(library(VoicePack.Female, "joke_01"), VoicePack.Male, joke.deva, "hi", clips))
        // Phone voice picked: always TTS.
        assertNull(BankClips.recorded(library(VoicePack.Female, "joke_01"), VoicePack.Tts, joke.deva, "hi", clips))
        // Recorded from older wording (the line was edited since).
        assertNull(BankClips.recorded(library(VoicePack.Male, "joke_01") { "000000000000" }, VoicePack.Male, joke.deva, "hi", clips))
        // Nothing recorded, or a line with no clip (a reply with a place or number in it).
        assertNull(BankClips.recorded(ClipLibrary.EMPTY, VoicePack.Male, joke.deva, "hi", clips))
        assertNull(BankClips.recorded(library(VoicePack.Male, "joke_01"), VoicePack.Male, "इब 5 बजे सैं।", "hi", emptyList()))
    }

    @Test
    fun reviewFileListsEveryRecordedBankLine() {
        val doc = File("../docs/haryanvi-lines.md").readText()
        for (e in BankClips.entries) assertTrue("review file lacks ${e.id}", doc.contains("| ${e.id} | ${e.text} |"))
    }
}
