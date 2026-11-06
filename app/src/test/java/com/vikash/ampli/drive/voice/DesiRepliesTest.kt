package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Hey Ampli's plain replies in Haryanvi: same placeholders as the Hindi strings, never safety. */
class DesiRepliesTest {

    private fun strings(folder: String): Map<String, String> {
        val out = HashMap<String, String>()
        val re = Regex("<string name=\"(\\w+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        File("src/main/res/$folder").listFiles()!!.filter { it.name.startsWith("strings") && it.name.endsWith(".xml") }.forEach { f ->
            for (m in re.findAll(f.readText())) out[m.groupValues[1]] = m.groupValues[2]
        }
        return out
    }

    private fun placeholders(s: String) = Regex("%\\d\\$[sd]").findAll(s).map { it.value }.sorted().toList()

    @Test
    fun everyReplyMatchesItsStringResource() {
        val hi = strings("values-hi")
        val en = strings("values")
        for ((name, line) in DesiReplies.LINES) {
            val want = hi[name] ?: en[name]
            assertNotNull("no string resource $name", want)
            assertEquals("$name placeholders", placeholders(want!!), placeholders(line))
            assertTrue("$name is Devanagari: $line", line.any { it in 'ऀ'..'ॿ' })
            assertTrue("$name is short enough to hear in the car", line.length <= 140)
        }
    }

    @Test
    fun safetyRepliesStayPlain() {
        for (name in listOf("voice_sos", "crash_voice")) assertNull(DesiReplies.format(name))
        assertFalse(DesiReplies.LINES.keys.any { it.contains("sos") || it.contains("crash") || it.contains("emergency") })
    }

    @Test
    fun noTextbookHindiLeftInTheReplies() {
        for ((name, line) in DesiReplies.LINES) {
            for (w in listOf("कृपया", "अनुमति", "कीजिए", "दीजिए", "हूँ", "है।", "नहीं मिला", "पुष्टि", "प्रति")) {
                assertFalse("$name still says $w: $line", line.contains(w))
            }
        }
        // Commands the driver repeats back stay word for word, so the parser still knows them.
        assertTrue(DesiReplies.LINES.getValue("power_say_screen_off").contains("रद्द करो"))
        assertTrue(DesiReplies.LINES.getValue("x_camera_saved").contains("कैमरा नहीं है"))
    }

    @Test
    fun fillsTheArgumentsLikeTheString() {
        assertEquals("इब 4:30 बजे सैं।", DesiReplies.format("voice_time_is", "4:30"))
        assertEquals("थारी स्पीड 62 किलोमीटर घंटे की सै।", DesiReplies.format("voice_speed_is", 62))
        assertEquals("एक जगहां मिली सै।", DesiReplies.format("voice_found_one"))
        assertNull(DesiReplies.format("voice_where_to"))
    }

    @Test
    fun onlyForHindiAndHaryanviUnits() {
        assertTrue(DesiReplies.appliesTo("hi"))
        assertTrue(DesiReplies.appliesTo("bgc"))
        assertFalse(DesiReplies.appliesTo("en"))
        assertFalse(DesiReplies.appliesTo("ta"))
    }

    @Test
    fun familySafe() = AssistantJokesTest.assertFamilySafe(
        DesiReplies.LINES.values.map { ChatLines.Line(it, "x", "x") },
    )
}

class RomanSpeechTest {

    @Test
    fun respeltOnlyForTheEnglishVoice() {
        assertEquals("Kay haal seh?", RomanSpeech.forVoice("Ke haal sai?", RomanSpeech.LANG))
        assertEquals("Ke haal sai?", RomanSpeech.forVoice("Ke haal sai?", "hi"))
        assertEquals("Ke haal sai?", RomanSpeech.forVoice("Ke haal sai?", "en"))
        assertEquals(
            "Gaadi tay poochha, sadak pay dhyaan, Tau nay kaha, kar lay jee",
            RomanSpeech.respell("Gaadi te poochha, sadak pe dhyaan, Tau ne kaha, kar le ji"),
        )
        // Longer words that only contain the short ones are left alone, and so is Devanagari.
        assertEquals("Tai ki saathan, Rohtak kay paas", RomanSpeech.respell("Tai ki saathan, Rohtak ke paas"))
        assertEquals("के हाल सै?", RomanSpeech.forVoice("के हाल सै?", RomanSpeech.LANG))
        // Numbers and placeholders are untouched.
        assertEquals("100 metre pay seh", RomanSpeech.respell("100 metre pe sai"))
    }
}
