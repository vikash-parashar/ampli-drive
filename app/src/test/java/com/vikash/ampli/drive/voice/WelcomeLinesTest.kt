package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.prefs.VoiceStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeLinesTest {

    private fun haryanvi(name: String?, count: Int, hindiVoice: Boolean = true, hour: Int = 9) =
        WelcomeLines.select(VoiceStyle.Haryanvi, name, count, hindiVoice, hindiScreen = true, hour = hour)

    private fun lines(e: HaryanviLines.Event) = HaryanviLines.forEvent(e)!!

    @Test
    fun firstNamedWelcomeIsTheOwnersWording() {
        val w = haryanvi("Vikash", 0)
        assertTrue(w.text, w.text.startsWith("राम राम Vikash जी, आज कित का प्लान सै?"))
        assertEquals("hi", w.language)
        assertEquals("Ram Ram Vikash Ji 🙏", w.card)
        assertTrue(w.named)
    }

    @Test
    fun withoutANameTheWelcomesRotate() {
        val texts = (0..9).map { haryanvi(null, it).text }
        for (t in texts) assertFalse(t, t.contains("%"))
        for (i in 1 until texts.size) assertTrue("repeat at $i", texts[i] != texts[i - 1])
        // Every general opener gets its turn, the owner's new lines included.
        for (opener in lines(HaryanviLines.Event.Welcome)) {
            assertTrue("never said: ${opener.deva}", texts.any { it.startsWith(opener.deva) })
        }
        for (count in 0..5) {
            val w = haryanvi("", count)
            assertFalse(w.named)
            assertEquals("Ram Ram 🙏", w.card)
        }
    }

    @Test
    fun withANameEveryOtherStartGreetsByName() {
        val ws = (0..7).map { haryanvi("Vikash", it) }
        for ((i, w) in ws.withIndex()) {
            assertEquals("start $i", i % 2 == 0, w.named)
            assertEquals(w.named, w.text.contains("Vikash"))
        }
        for (i in 1 until ws.size) assertTrue("repeat at $i", ws[i].text != ws[i - 1].text)
    }

    @Test
    fun timeOfDayLineFollowsTheOpener() {
        fun timeLine(hour: Int, count: Int): String {
            val w = haryanvi(null, count, hour = hour)
            val opener = lines(HaryanviLines.Event.Welcome).first { w.text.startsWith(it.deva) }
            return w.text.removePrefix(opener.deva).trim()
        }
        for (count in 0..4) {
            assertTrue(lines(HaryanviLines.Event.WelcomeMorning).any { it.deva == timeLine(7, count) })
            assertTrue(lines(HaryanviLines.Event.WelcomeAfternoon).any { it.deva == timeLine(14, count) })
            assertTrue(lines(HaryanviLines.Event.WelcomeEvening).any { it.deva == timeLine(19, count) })
            assertTrue(lines(HaryanviLines.Event.WelcomeNight).any { it.deva == timeLine(23, count) })
            assertTrue(lines(HaryanviLines.Event.WelcomeNight).any { it.deva == timeLine(2, count) })
        }
        assertTrue(timeLine(7, 0) != timeLine(7, 1))
    }

    @Test
    fun romanLinesWithEnglishVoiceWhenNoHindiVoice() {
        val named = haryanvi("Vikash", 0, hindiVoice = false)
        assertTrue(named.text, named.text.startsWith("Ram Ram Vikash Ji, aaj kit ka plan hai?"))
        assertEquals("en-IN", named.language)
        val plain = haryanvi(null, 3, hindiVoice = false)
        assertFalse(plain.text.any { it in 'ऀ'..'ॿ' })
        assertEquals("en-IN", plain.language)
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
