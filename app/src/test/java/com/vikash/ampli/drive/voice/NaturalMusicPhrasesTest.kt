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

    /** What the head unit heard in the field and answered with "samjhya na" or a place list. */
    private val fieldReports = listOf("gana Baja", "gana chala de", "gana baja de", "gana Suna De")

    @Test
    fun fieldReportsPlayMusic() {
        for (lang in listOf("en", "hi")) {
            for (p in fieldReports) {
                assertEquals("$p ($lang)", VoiceIntent.Resume, VoiceIntents.parse(p, lang))
                assertTrue("$p ($lang)", VoskCommands.phrases(hindi = false).contains(p.lowercase()))
            }
        }
    }

    @Test
    fun ganaSunaDeIsNeverAPlaceSearch() {
        for (lang in listOf("en", "hi")) {
            for (p in listOf("gana suna de", "Gana Suna De", "गाना सुना दे", "gana suna de please", "gaana sunaa de")) {
                val intent = VoiceIntents.parse(p, lang)
                assertFalse("$p ($lang): $intent", intent is VoiceIntent.Navigate || intent is VoiceIntent.Unknown)
                assertEquals("$p ($lang)", VoiceIntent.Resume, intent)
            }
        }
        // The assistant's own guards: music words never go to the geocoder or to "where to?".
        for (p in listOf("gana suna de", "suna gana", "song", "गाना", "गाने", "भजन", "bhajan", "music", "gaane")) {
            assertTrue(p, VoiceIntents.mentionsMusic(p))
            assertFalse(p, RecognizerFallback.offlineNeedsWhereTo(p, "hi"))
        }
        assertFalse(VoiceIntents.mentionsMusic("connaught place"))
    }

    private val nouns = listOf("gana", "gaana", "gaane", "gane", "song", "songs", "music", "gaana koi", "koi gana")
    private val verbs = listOf(
        "baja", "bajao", "baja de", "baja do", "bajaa de", "chala", "chalao", "chala de", "chala do", "chalaa de",
        "suna", "sunao", "suna de", "suna do", "sunaa de", "laga", "lagao", "laga de", "laga do",
        "play", "play karo", "chalu karo", "shuru karo",
    )

    @Test
    fun everyNounWithEveryVerbPlaysMusic() {
        for (lang in listOf("en", "hi")) {
            for (n in nouns) for (v in verbs) {
                for (p in listOf("$n $v", "$v $n", "${n.uppercase()} ${v.replaceFirstChar { it.uppercase() }}")) {
                    assertEquals("$p ($lang)", VoiceIntent.Resume, VoiceIntents.parse(p, lang))
                }
            }
            for (v in verbs) {
                assertEquals("bhajan $v ($lang)", VoiceIntent.PlayLocal("bhajan"), VoiceIntents.parse("bhajan $v", lang))
            }
        }
    }

    @Test
    fun devanagariFormsPlayMusic() {
        val hindi = listOf(
            "गाना बजा", "गाना बजा दे", "गाना बजा दो", "गाना बजाओ", "गाना चला", "गाना चला दे", "गाना चला दो", "गाना चलाओ",
            "गाना सुना", "गाना सुना दे", "गाना सुना दो", "गाना सुनाओ", "गाना लगा", "गाना लगा दे", "गाना लगा दो",
            "गाने सुनाओ", "गाने बजा दे", "गाने चला दे", "गाने लगा दे", "कोई गाना सुना दे", "गाना प्ले करो",
            "गाना चालू करो", "गाना शुरू करो", "म्यूजिक चला दे", "म्यूजिक बजा", "सॉन्ग लगा दे", "सॉन्ग बजा",
        )
        for (lang in listOf("en", "hi")) {
            for (p in hindi) assertEquals("$p ($lang)", VoiceIntent.Resume, VoiceIntents.parse(p, lang))
            for (p in listOf("भजन बजा", "भजन सुना दे", "भजन चला दे", "भजन लगा दे")) {
                assertEquals("$p ($lang)", VoiceIntent.PlayLocal("भजन"), VoiceIntents.parse(p, lang))
            }
        }
        for (p in VoskCommands.MUSIC_PLAY_HINDI) {
            assertTrue(p, VoskCommands.phrases(hindi = true).contains(p))
        }
    }

    @Test
    fun verbAloneAfterTheChimePlaysMusic() {
        for (p in listOf("baja de", "chala de", "suna de", "laga de", "bajao", "chalao", "बजा दे", "चला दे", "सुना दे", "लगा दे")) {
            assertEquals(p, VoiceIntent.Resume, VoiceIntents.parse(p, "hi"))
        }
    }

    @Test
    fun theMusicMatrixIsInTheEnglishGrammar() {
        val g = VoskCommands.phrases(hindi = false)
        for (n in nouns + "bhajan") for (v in verbs) assertTrue("$n $v", g.contains("$n $v"))
        for (p in listOf("baja de", "chala de", "suna de", "laga de")) assertTrue(p, g.contains(p))
    }

    @Test
    fun musicWordsDoNotStealOtherCommands() {
        assertEquals(VoiceIntent.Pause, VoiceIntents.parse("gana band karo", "hi"))
        assertEquals(VoiceIntent.Next, VoiceIntents.parse("agla gana chala de", "hi"))
        assertEquals(VoiceIntent.GoSaved(com.vikash.ampli.drive.nav.SavedKind.Home), VoiceIntents.parse("ghar chalo", "hi"))
        assertEquals(VoiceIntent.CameraHere, VoiceIntents.parse("camera laga hai", "hi"))
        assertTrue(VoiceIntents.parse("kitne baje hain", "hi") is VoiceIntent.TimeNow)
        assertTrue(VoiceIntents.parse("rohtak le chalo", "hi") is VoiceIntent.Navigate)
    }

    @Test
    fun newMusicSpellingsAreSwappedForKnownOnes() {
        // A model that knows "bajaa" and "dey" but not "baja" / "de".
        val known = setOf("gana", "bajaa", "dey", "chala", "suna", "song")
        val a = HinglishSpelling.adapt(listOf("gana baja de", "song suna de"), known::contains)
        assertTrue(a.phrases.toString(), a.phrases.contains("gana bajaa dey"))
        assertEquals(VoiceIntent.Resume, VoiceIntents.parse(HinglishSpelling.restore("gana bajaa dey", a.back), "en"))
        assertEquals(VoiceIntent.Resume, VoiceIntents.parse(HinglishSpelling.restore("song suna dey", a.back), "en"))
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
