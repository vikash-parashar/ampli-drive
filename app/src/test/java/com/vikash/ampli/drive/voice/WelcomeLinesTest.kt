package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.prefs.VoiceStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeLinesTest {

    private fun haryanvi(name: String?, count: Int, hindiVoice: Boolean = true) =
        WelcomeLines.select(VoiceStyle.Haryanvi, name, count, hindiVoice, hindiScreen = true)

    @Test
    fun namedLineIsTheOwnersWording() {
        val w = haryanvi("Vikash", 0)
        assertEquals("राम राम Vikash जी, आज कित का प्लान सै?", w.text)
        assertEquals("hi", w.language)
        assertEquals("Ram Ram Vikash Ji 🙏", w.card)
        assertTrue(w.named)
    }

    @Test
    fun noNameAlwaysSaysTaiAale() {
        for (count in 0..5) {
            for (name in listOf(null, "", "   ")) {
                val w = haryanvi(name, count)
                assertEquals("राम राम ताई आले, आज कित की तैयारी सै?", w.text)
                assertEquals("Ram Ram Tai Aale 🙏", w.card)
                assertFalse(w.named)
            }
        }
    }

    @Test
    fun alternatesBetweenTheTwoLinesWhenANameIsSet() {
        val texts = (0..5).map { haryanvi("Vikash", it).text }
        assertEquals(WelcomeLines.NAMED_DEVA.format("Vikash"), texts[0])
        assertEquals(WelcomeLines.TAI_DEVA, texts[1])
        assertEquals(WelcomeLines.NAMED_DEVA.format("Vikash"), texts[2])
        assertEquals(WelcomeLines.TAI_DEVA, texts[3])
        for (i in 1 until texts.size) assertTrue("repeat at $i", texts[i] != texts[i - 1])
    }

    @Test
    fun romanLinesWithEnglishVoiceWhenNoHindiVoice() {
        val named = haryanvi("Vikash", 0, hindiVoice = false)
        assertEquals("Ram Ram Vikash Ji, aaj kit ka plan hai?", named.text)
        assertEquals("en-IN", named.language)
        val tai = haryanvi(null, 0, hindiVoice = false)
        assertEquals("Ram Ram Tai Aale, aaj kit ki taiyaari hai?", tai.text)
        assertEquals("en-IN", tai.language)
    }

    @Test
    fun standardStyleGetsAPlainWelcome() {
        val hi = WelcomeLines.select(VoiceStyle.Standard, "Vikash", 1, hindiVoice = true, hindiScreen = true)
        assertEquals(WelcomeLines.STANDARD_HI_NAMED.format("Vikash"), hi.text)
        assertEquals("hi", hi.language)
        val en = WelcomeLines.select(VoiceStyle.Standard, "Vikash", 1, hindiVoice = true, hindiScreen = false)
        assertEquals("Welcome, Vikash. Have a safe drive.", en.text)
        assertEquals("en-IN", en.language)
        val noVoice = WelcomeLines.select(VoiceStyle.Standard, null, 0, hindiVoice = false, hindiScreen = true)
        assertEquals(WelcomeLines.STANDARD_EN, noVoice.text)
        // Standard never uses the Haryanvi lines, whatever the count.
        for (count in 0..3) {
            val w = WelcomeLines.select(VoiceStyle.Standard, "Vikash", count, hindiVoice = true, hindiScreen = true)
            assertFalse(w.text.contains("राम राम"))
            assertFalse(w.text.contains("Ram Ram"))
        }
    }

    @Test
    fun hindiScreenLanguages() {
        assertTrue(WelcomeLines.isHindiScreen("hi"))
        assertTrue(WelcomeLines.isHindiScreen("hi-IN"))
        assertTrue(WelcomeLines.isHindiScreen("bgc"))
        assertFalse(WelcomeLines.isHindiScreen("en-IN"))
        assertFalse(WelcomeLines.isHindiScreen("ta"))
    }

    @Test
    fun ownerNameIsCleaned() {
        assertEquals("Vikash", OwnerName.clean("  Vikash  "))
        assertEquals("Vikash", OwnerName.clean("Vikash ji"))
        assertEquals("विकास", OwnerName.clean("विकास जी"))
        assertEquals("Ram Kumar", OwnerName.clean("Ram   Kumar"))
        assertEquals("", OwnerName.clean(null))
        assertEquals("", OwnerName.clean("Ji"))
        assertTrue(OwnerName.clean("x".repeat(80)).length <= OwnerName.MAX_LENGTH)
        // The welcome adds its own "Ji": no "Ji Ji".
        assertEquals("Ram Ram Vikash Ji 🙏", haryanvi("Vikash Ji", 0).card)
    }

    @Test
    fun nameFromSpeech() {
        assertEquals("Vikash", OwnerName.fromSpeech("my name is Vikash"))
        assertEquals("Vikash", OwnerName.fromSpeech("Mera naam Vikash hai"))
        assertEquals("विकास", OwnerName.fromSpeech("मेरा नाम विकास है"))
        assertEquals("Vikash", OwnerName.fromSpeech("Vikash"))
        assertEquals("Naamdev", OwnerName.fromSpeech("Naamdev"))
    }
}
