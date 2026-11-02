package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How drivers really ask for music when they answer Ampli ("gana sunao", "गाना लगाओ"). */
class NaturalMusicPhrasesTest {
    private val play = listOf(
        "gana sunao", "gaana sunao", "gaane sunao", "gane sunao", "gaana suna do", "gana suna do",
        "gaana lagao", "gana lagao", "gaane lagao", "gaana chala do", "gana chala do", "gaane chalao",
        "gaana baja do", "gana bajao", "gaane bajao", "koi gaana chalao", "koi gaana sunao", "koi gana lagao",
        "song chalao", "song lagao", "song sunao", "song bajao", "song chala do",
        "music sunao", "music lagao", "music bajao", "music chala do", "music suna do",
        "गाना सुनाओ", "गाने सुनाओ", "गाना सुना दो", "गाने सुना दो", "गाना लगाओ", "गाने लगाओ",
        "गाना चला दो", "गाने चलाओ", "गाना बजा दो", "गाने बजाओ", "कोई गाना चलाओ", "कोई गाना सुनाओ", "कोई गाना लगाओ",
        "म्यूजिक चलाओ", "म्यूजिक बजाओ", "म्यूजिक लगाओ", "म्यूजिक सुनाओ", "सॉन्ग चलाओ", "सॉन्ग लगाओ",
        "गाना बजाओ", "गाना चलाओ", "प्ले म्यूजिक", "म्यूजिक प्ले करो", "गाना प्ले करो", "प्ले करो",
    )

    @Test
    fun playPhrasesPlayMusicInEveryLanguage() {
        for (lang in listOf("en", "hi")) {
            for (p in play) assertEquals("$p ($lang)", VoiceIntent.Resume, VoiceIntents.parse(p, lang))
        }
    }

    @Test
    fun bhajanPlaysABhajan() {
        for (p in listOf("bhajan sunao", "bhajan chalao", "bhajan lagao", "bhajan bajao")) {
            assertEquals(p, VoiceIntent.PlayLocal("bhajan"), VoiceIntents.parse(p, "en"))
        }
        for (p in listOf("भजन सुनाओ", "भजन चलाओ", "भजन लगाओ", "भजन बजाओ")) {
            assertEquals(p, VoiceIntent.PlayLocal("भजन"), VoiceIntents.parse(p, "hi"))
        }
    }

    @Test
    fun stopNextPreviousAndVolume() {
        for (p in listOf(
            "gaana band kar", "gaana band kar do", "gana band karo", "gaane band karo", "gaana rok do",
            "music band kar do", "music roko", "song band karo", "song roko",
            "गाना बंद कर", "गाना बंद कर दो", "गाने बंद करो", "गाना रोक दो", "म्यूजिक बंद करो", "म्यूजिक बंद कर दो", "पॉज़ करो",
        )) {
            assertEquals(p, VoiceIntent.Pause, VoiceIntents.parse(p, "hi"))
        }
        for (p in listOf(
            "agla chalao", "agla gaana chalao", "agla song", "agla laga do", "next gaana", "agla gana lagao",
            "अगला चलाओ", "अगला गाना चलाओ", "अगला लगाओ", "अगला गाना लगाओ", "नेक्स्ट सॉन्ग", "नेक्स्ट",
        )) {
            assertEquals(p, VoiceIntent.Next, VoiceIntents.parse(p, "hi"))
        }
        for (p in listOf("pichla chalao", "pichla song", "pichla gaana chalao", "पिछला चलाओ", "पिछला गाना चलाओ")) {
            assertEquals(p, VoiceIntent.Previous, VoiceIntents.parse(p, "hi"))
        }
        val up = VoiceIntent.Volume(VoiceIntent.VolumeAction.Up)
        val down = VoiceIntent.Volume(VoiceIntent.VolumeAction.Down)
        for (p in listOf("awaaz badha do", "awaz badha do", "awaaz tez kar do", "volume badha do",
            "आवाज बढ़ा दो", "आवाज तेज करो", "आवाज तेज कर दो", "वॉल्यूम बढ़ाओ", "वॉल्यूम बढ़ा दो", "वॉल्यूम अप")) {
            assertEquals(p, up, VoiceIntents.parse(p, "hi"))
        }
        for (p in listOf("awaaz kam kar do", "awaaz dheemi karo", "volume kam kar do",
            "आवाज कम कर दो", "आवाज धीमी करो", "वॉल्यूम कम करो", "वॉल्यूम डाउन")) {
            assertEquals(p, down, VoiceIntents.parse(p, "hi"))
        }
        assertEquals(VoiceIntent.Stop, VoiceIntents.parse("स्टॉप", "hi"))
    }

    @Test
    fun theOwnersAnswerIsInTheGrammars() {
        assertTrue(VoskCommands.followUpPhrases(hindi = false).contains("gana sunao"))
        assertTrue(VoskCommands.followUpPhrases(hindi = true).contains("गाना सुनाओ"))
    }

    @Test
    fun everyHindiGrammarPhraseIsUnderstood() {
        for (p in VoskCommands.phrases(hindi = true)) {
            val understood = VoiceIntents.parse(p, "hi") !is VoiceIntent.Unknown ||
                VoiceIntents.parse(p, "hi", expectingChoice = true) is VoiceIntent.ChooseOption ||
                VoiceIntents.volumeFollowUp(p, VoiceIntent.VolumeAction.Up) != null
            assertTrue(p, understood)
            // English app language too: offline-only units answer Ampli through the Hindi model.
            val understoodEn = VoiceIntents.parse(p, "en") !is VoiceIntent.Unknown ||
                VoiceIntents.parse(p, "en", expectingChoice = true) is VoiceIntent.ChooseOption ||
                VoiceIntents.volumeFollowUp(p, VoiceIntent.VolumeAction.Up) != null
            assertTrue("$p (en)", understoodEn)
        }
    }

    @Test
    fun newWordsGetKnownSpellings() {
        // An English model that knows "gana" and "sunaw" but not "gaana" / "sunao".
        val known = setOf("gana", "sunaw", "lagaw", "koy", "bajan", "song", "music", "play")
        val a = HinglishSpelling.adapt(listOf("gaana sunao", "koi gaana lagao", "bhajan sunao"), known::contains)
        assertTrue(a.phrases.toString(), a.phrases.contains("gana sunaw"))
        assertTrue(a.phrases.toString(), a.phrases.contains("koy gana lagaw"))
        assertTrue(a.phrases.toString(), a.phrases.contains("bajan sunaw"))
        assertEquals("bhajan sunao", HinglishSpelling.restore("bajan sunaw", a.back))
        assertEquals(VoiceIntent.Resume, VoiceIntents.parse(HinglishSpelling.restore("gana sunaw", a.back), "en"))
    }
}

class MicStatsTest {
    @Test
    fun digitalSilenceIsSpotted() {
        val m = MicStats()
        repeat(20) { m.add(ShortArray(1600), 1600) }
        assertTrue(m.deadSilence(16_000))
        assertEquals(0, m.rms)
    }

    @Test
    fun aRealMicIsNotSilence() {
        val m = MicStats()
        val buf = ShortArray(1600) { i -> if (i % 2 == 0) 300 else -300 }
        repeat(20) { m.add(buf, 1600) }
        assertFalse(m.deadSilence(16_000))
        assertEquals(300, m.peak)
        assertEquals(300, m.rms)
        assertTrue(m.describe(16_000), m.describe(16_000).startsWith("peak 300 rms 300 over 2.0 s"))
    }

    @Test
    fun tooShortToJudge() {
        val m = MicStats()
        m.add(ShortArray(100), 100)
        assertFalse(m.deadSilence(16_000))
    }
}

class VoiceDebugLogTest {
    @Test
    fun keepsTheLastEventsOldestFirst() {
        VoiceDebugLog.clear()
        var t = 0L
        VoiceDebugLog.now = { t }
        try {
            repeat(VoiceDebugLog.MAX + 5) { i ->
                t = i * 1000L
                VoiceDebugLog.add("event $i")
            }
            val e = VoiceDebugLog.entries()
            assertEquals(VoiceDebugLog.MAX, e.size)
            assertEquals("event 5", e.first().text)
            assertEquals("event ${VoiceDebugLog.MAX + 4}", e.last().text)
            assertEquals(e, VoiceDebugLog.flow.value)
            assertTrue(VoiceDebugLog.text().lines().last().endsWith("  event ${VoiceDebugLog.MAX + 4}"))
            VoiceDebugLog.add("two\nlines")
            assertEquals("two lines", VoiceDebugLog.entries().last().text)
        } finally {
            VoiceDebugLog.now = { System.currentTimeMillis() }
            VoiceDebugLog.clear()
        }
    }
}
