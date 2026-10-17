package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.random.Random

class HaryanviLinesTest {

    private val playful = HaryanviLines.Event.entries.filter { !it.safety }

    @Test
    fun everyPlayfulEventHasFourToEightVariants() {
        for (event in playful) {
            val lines = HaryanviLines.forEvent(event)
            assertNotNull("$event has no lines", lines)
            assertTrue("$event has ${lines!!.size}", lines.size in 4..8)
            assertEquals("$event has duplicate lines", lines.size, lines.map { it.deva }.toSet().size)
        }
    }

    @Test
    fun noImmediateRepeats() {
        for (seed in 0..20) {
            val picker = LinePicker(Random(seed))
            for (event in playful) {
                var prev: HaryanviLines.Line? = null
                repeat(200) {
                    val line = picker.pick(event)!!
                    assertNotEquals("$event repeated", prev, line)
                    prev = line
                }
            }
        }
    }

    @Test
    fun everyVariantGetsUsed() {
        val picker = LinePicker(Random(7))
        for (event in playful) {
            val seen = HashSet<HaryanviLines.Line>()
            repeat(400) { seen += picker.pick(event)!! }
            assertEquals(event.name, HaryanviLines.forEvent(event)!!.toSet(), seen)
        }
    }

    @Test
    fun safetyPromptsNeverUsePlayfulLines() {
        val picker = LinePicker()
        for (event in HaryanviLines.Event.entries.filter { it.safety }) {
            assertNull(HaryanviLines.forEvent(event))
            assertNull(picker.pick(event))
            assertFalse(HaryanviLines.allLines.containsKey(event))
            assertEquals(HaryanviLines.Group.Safety, event.group)
        }
        assertTrue(HaryanviLines.Event.Sos.safety)
        assertTrue(HaryanviLines.Event.Crash.safety)
    }

    /** The SOS reply and the crash countdown speak plain strings, never the desi bank. */
    @Test
    fun sosAndCrashPathsNeverTouchTheBank() {
        val src = File("src/main/java/com/vikash/ampli/drive")
        val assistant = File(src, "voice/VoiceAssistant.kt").readText()
        val sos = block(assistant, "VoiceIntent.Sos ->")
        assertTrue(sos, sos.contains("R.string.voice_sos"))
        for (bad in listOf("styled(", "desi", "HaryanviLines", "speakDesi")) assertFalse("SOS uses $bad", sos.contains(bad))

        val monitor = File(src, "car/CarMonitor.kt").readText()
        val crash = block(monitor, "fun triggerCrash()")
        assertTrue(crash.contains("R.string.crash_voice"))
        for (bad in listOf("desi", "HaryanviLines")) assertFalse("Crash uses $bad", crash.contains(bad))
    }

    /** The braces-balanced block that starts at [marker]. */
    private fun block(text: String, marker: String): String {
        val start = text.indexOf(marker)
        assertTrue("$marker not found", start >= 0)
        val open = text.indexOf('{', start)
        var depth = 0
        for (i in open until text.length) {
            if (text[i] == '{') depth++
            if (text[i] == '}') depth--
            if (depth == 0) return text.substring(start, i + 1)
        }
        return text.substring(start)
    }

    @Test
    fun devanagariAndRomanHaveTheSamePlaceholders() {
        val ph = Regex("%\\d\\$[sd]")
        for ((event, lines) in HaryanviLines.allLines) {
            for (l in lines) {
                assertEquals("$event: ${l.roman}", ph.findAll(l.deva).map { it.value }.toSet(), ph.findAll(l.roman).map { it.value }.toSet())
                assertTrue("$event deva in Devanagari", l.deva.any { it in 'ऀ'..'ॿ' })
                assertFalse("$event roman has Devanagari: ${l.roman}", l.roman.any { it in 'ऀ'..'ॿ' })
                assertTrue("$event needs an English meaning: ${l.roman}", l.en.isNotBlank())
            }
        }
    }

    @Test
    fun sampleValuesFillEveryLine() {
        for ((event, lines) in HaryanviLines.allLines) {
            val args = HaryanviLines.sampleArgs(event)
            for (l in lines) {
                for (t in listOf(l.deva, l.roman)) {
                    val s = String.format(Locale.US, t, *args)
                    assertFalse("$event left a placeholder: $s", s.contains('%'))
                }
            }
        }
    }

    @Test
    fun timeOfDay() {
        assertEquals(HaryanviLines.Event.WelcomeNight, HaryanviLines.timeOfDay(3))
        assertEquals(HaryanviLines.Event.WelcomeMorning, HaryanviLines.timeOfDay(4))
        assertEquals(HaryanviLines.Event.WelcomeMorning, HaryanviLines.timeOfDay(11))
        assertEquals(HaryanviLines.Event.WelcomeAfternoon, HaryanviLines.timeOfDay(12))
        assertEquals(HaryanviLines.Event.WelcomeAfternoon, HaryanviLines.timeOfDay(16))
        assertEquals(HaryanviLines.Event.WelcomeEvening, HaryanviLines.timeOfDay(17))
        assertEquals(HaryanviLines.Event.WelcomeEvening, HaryanviLines.timeOfDay(20))
        assertEquals(HaryanviLines.Event.WelcomeNight, HaryanviLines.timeOfDay(21))
        assertEquals(HaryanviLines.Event.WelcomeNight, HaryanviLines.timeOfDay(0))
    }

    @Test
    fun ownersWordsAreInTheBank() {
        fun has(e: HaryanviLines.Event, deva: String) =
            assertTrue("$e lacks $deva", HaryanviLines.forEvent(e)!!.any { it.deva == deva })
        has(HaryanviLines.Event.Welcome, "राम राम जी, आज कित जाणा सै?")
        has(HaryanviLines.Event.Welcome, "राम राम भाई, आज कित का प्लान सै?")
        has(HaryanviLines.Event.Welcome, "और तैयारे? सुणा दे, के हाल सै?")
        has(HaryanviLines.Event.Welcome, "राम राम ताई आले, आज कित की तैयारी सै?")
        has(HaryanviLines.Event.WelcomeNamed, "राम राम %1\$s जी, आज कित का प्लान सै?")
        has(HaryanviLines.Event.Farewell, "चाल फेर मिलांगे।")
        has(HaryanviLines.Event.Rerouting, "रास्ता भटक गया भाई, नया रास्ता देख रया सूं।")
        has(HaryanviLines.Event.SpeedBackUnder, "शाबाश, न्यू ए चला।")
        has(HaryanviLines.Event.WakeReply, "हाँ भाई, बोल।")
        has(HaryanviLines.Event.CommandDone, "ठीक सै, कर दिया।")
        has(HaryanviLines.Event.MusicPlay, "ले, गाणा बजा दिया।")
        has(HaryanviLines.Event.TalkHowAreYou, "बढ़िया सूं, तू सुणा।")
        assertEquals(5, HaryanviLines.forEvent(HaryanviLines.Event.TalkJoke)!!.size)
        // Left and right are the owner's words.
        assertTrue(HaryanviLines.forEvent(HaryanviLines.Event.NavLeft)!!.any { it.deva.contains("उल्टे हाथ") })
        assertTrue(HaryanviLines.forEvent(HaryanviLines.Event.NavRight)!!.any { it.deva.contains("सुल्टे हाथ") })
    }

    @Test
    fun familySafe() {
        // Abuse, crude words, and caste or community names (no community jokes in the bank).
        val bannedRoman = listOf(
            "chod", "chutiya", "chutia", "saala", "sala", "saali", "kamina", "kamine", "kutta", "kutte", "kutiya",
            "gaand", "gand", "lund", "loda", "lauda", "randi", "harami", "haramkhor", "bhosdi", "bhosdike",
            "madarchod", "behenchod", "bhenchod", "mc", "bc", "bsdk", "tatti", "gadha", "ullu", "pagal",
            "bewakoof", "bevkoof", "nalayak", "badtameez", "sharab", "daaru", "daru", "nasha", "beti",
            "jaat", "jat", "chamar", "bania", "baniya", "gujjar", "ahir", "brahmin", "pandit", "dalit",
            "harijan", "dhobi", "nai", "mullah", "mulla", "sardar", "chhakka", "hijra",
        )
        // "nai" is Haryanvi for "to" in the roman lines; checked in Devanagari (नाई) instead.
        val romanWords = bannedRoman - "nai"
        val bannedDeva = listOf(
            "चोद", "चूतिया", "साला", "साली", "कमीना", "कमीने", "कुत्ता", "कुत्ते", "कुतिया", "गांड", "गाँड", "लंड",
            "रंडी", "हरामी", "भोसड़ी", "मादरचोद", "बहनचोद", "तट्टी", "गधा", "उल्लू", "पागल", "बेवकूफ", "नालायक",
            "बदतमीज", "शराब", "दारू", "नशा", "बेटी", "जाट", "चमार", "बनिया", "गुर्जर", "अहीर", "ब्राह्मण", "पंडित",
            "दलित", "हरिजन", "धोबी", "नाई", "मुल्ला", "सरदार", "छक्का", "हिजड़ा",
        )
        for ((event, lines) in HaryanviLines.allLines) {
            for (l in lines) {
                val words = l.roman.lowercase().split(Regex("[^a-z]+")).toSet()
                val enWords = l.en.lowercase().split(Regex("[^a-z]+")).toSet()
                for (b in romanWords) {
                    assertFalse("$event \"${l.roman}\" contains $b", b in words)
                    assertFalse("$event \"${l.en}\" contains $b", b in enWords)
                }
                val devaWords = l.deva.split(Regex("[^\\p{L}\\p{M}]+")).toSet()
                for (b in bannedDeva) assertFalse("$event \"${l.deva}\" contains $b", b in devaWords)
            }
        }
    }

    /** docs/haryanvi-lines.md is the owner's review copy: every line must be in it. */
    @Test
    fun reviewFileListsEveryLine() {
        val doc = File("../docs/haryanvi-lines.md").readText()
        for ((event, lines) in HaryanviLines.allLines) {
            assertTrue("review file lacks event $event", doc.contains("| ${event.title.replace("|", "\\|")} |"))
            for (l in lines) {
                assertTrue("review file lacks: ${l.deva}", doc.contains(l.deva))
                assertTrue("review file lacks: ${l.roman}", doc.contains(l.roman))
            }
        }
    }
}
