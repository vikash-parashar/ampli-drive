package com.vikash.ampli.drive.voice.tts

import com.vikash.ampli.drive.voice.TtsNotice
import com.vikash.ampli.drive.voice.tts.BundledTts.Engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledTtsTest {
    private val both = setOf(BundledTts.HINDI, BundledTts.ENGLISH)

    @Test
    fun systemWithTheLanguageWins() {
        assertEquals(Engine.System, BundledTts.pick(systemReady = true, systemHasLanguage = true, bundledHasLanguage = true, bundledReady = true))
    }

    @Test
    fun noSystemEngineUsesBundled() {
        // The TS7: no TTS engine at all.
        assertEquals(Engine.Bundled, BundledTts.pick(systemReady = false, systemHasLanguage = false, bundledHasLanguage = true, bundledReady = true))
        // Bundled voice in another language still beats silence.
        assertEquals(Engine.Bundled, BundledTts.pick(systemReady = false, systemHasLanguage = false, bundledHasLanguage = false, bundledReady = true))
    }

    @Test
    fun systemWithoutHindiPrefersBundledHindi() {
        assertEquals(Engine.Bundled, BundledTts.pick(systemReady = true, systemHasLanguage = false, bundledHasLanguage = true, bundledReady = true))
    }

    @Test
    fun systemWithoutTheLanguageStillBeatsNothing() {
        // Bundled voices not installed yet: the system engine reads it in its default voice, as before.
        assertEquals(Engine.System, BundledTts.pick(systemReady = true, systemHasLanguage = false, bundledHasLanguage = false, bundledReady = false))
        // A bundled voice of another language does not beat a working system engine.
        assertEquals(Engine.System, BundledTts.pick(systemReady = true, systemHasLanguage = false, bundledHasLanguage = false, bundledReady = true))
    }

    @Test
    fun nothingAtAll() {
        assertEquals(Engine.None, BundledTts.pick(systemReady = false, systemHasLanguage = false, bundledHasLanguage = false, bundledReady = false))
    }

    @Test
    fun coversHindiFamilyAndEnglish() {
        assertTrue(BundledTts.covers("hi", both))
        assertTrue(BundledTts.covers("bgc", both)) // Haryanvi
        assertTrue(BundledTts.covers("en", both))
        assertFalse(BundledTts.covers("ta", both))
        assertFalse(BundledTts.covers("hi", setOf(BundledTts.ENGLISH)))
        assertFalse(BundledTts.covers("en", emptySet()))
    }

    @Test
    fun devanagariNeedsTheHindiVoice() {
        assertEquals("hi", BundledTts.voiceFor("टोल 135 रुपये", "en", both))
        assertNull(BundledTts.voiceFor("टोल 135 रुपये", "en", setOf(BundledTts.ENGLISH)))
    }

    @Test
    fun latinTextFollowsTheLineLanguage() {
        assertEquals("en", BundledTts.voiceFor("Toll 135 rupees", "en", both))
        assertEquals("hi", BundledTts.voiceFor("Aage daayen mudo", "hi", both))
        // Only the other voice installed: it reads it rather than nothing.
        assertEquals("hi", BundledTts.voiceFor("Next song", "en", setOf(BundledTts.HINDI)))
        assertEquals("en", BundledTts.voiceFor("Aage daayen mudo", "hi", setOf(BundledTts.ENGLISH)))
        assertNull(BundledTts.voiceFor("Hello", "en", emptySet()))
    }

    @Test
    fun chunksCutAtSentenceEnds() {
        assertEquals(
            listOf("आगे दाएं मुड़ें।", "टोल 135 रुपये।"),
            BundledTts.chunks("आगे दाएं मुड़ें। टोल 135 रुपये।"),
        )
        assertEquals(listOf("Turn right.", "Then left!", "Okay?"), BundledTts.chunks("Turn right. Then left! Okay?"))
    }

    @Test
    fun chunksKeepNumbersAndAbbreviationsWhole() {
        assertEquals(listOf("Fuel 1.5 litres, Rs.135 paid."), BundledTts.chunks("Fuel 1.5 litres, Rs.135 paid."))
    }

    @Test
    fun chunksSplitLongTextAndJoinScraps() {
        val long = (1..60).joinToString(" ") { "word$it" }
        val pieces = BundledTts.chunks(long)
        assertTrue(pieces.size > 1)
        assertTrue(pieces.all { it.length <= BundledTts.MAX_CHUNK })
        assertEquals(long, pieces.joinToString(" "))
        // A lone "!" or "1." joins the sentence before it.
        assertEquals(listOf("Done. !"), BundledTts.chunks("Done. !"))
        assertTrue(BundledTts.chunks("   ").isEmpty())
    }

    @Test
    fun homeCardWaitsForTheBundledVoice() {
        assertTrue(TtsNotice.showCard(ttsMissing = true, shownBefore = false, moving = false, bundledComing = false))
        assertFalse(TtsNotice.showCard(ttsMissing = true, shownBefore = false, moving = false, bundledComing = true))
        // Once the bundled voice is ready nothing is missing, so no card.
        assertFalse(TtsNotice.showCard(ttsMissing = false, shownBefore = false, moving = false))
    }

    @Test
    fun voiceSpecsAreConsistent() {
        assertEquals(setOf("hi", "en"), TtsVoiceSpec.entries.flatMap { it.languages }.toSet())
        TtsVoiceSpec.entries.forEach {
            assertEquals(128, it.sha512.length)
            assertTrue(it.sha512.all { c -> c in '0'..'9' || c in 'a'..'f' })
            assertTrue(it.assetPath.startsWith("tts/") && it.assetPath.endsWith(".flitevox"))
            assertTrue(it.url.startsWith("https://"))
        }
    }
}
