package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.nav.SavedKind
import com.vikash.ampli.drive.weather.Sky
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/** "Hey Ampli" as a conversation: small talk, the day, the news, jokes and Haryanvi music words. */
class AssistantChatIntentsTest {

    private fun both(phrases: List<String>, want: VoiceIntent) {
        for (lang in listOf("hi", "en")) {
            for (p in phrases) assertEquals("$p ($lang)", want, VoiceIntents.parse(p, lang))
        }
    }

    private fun talk(kind: VoiceIntent.Talk) = VoiceIntent.SmallTalk(kind)

    @Test
    fun howAreYou() = both(
        listOf(
            "hey ampli suna de kya haal hai", "suna de kya haal hai", "kya haal hai", "kaisa hai", "ke haal se", "ke haal sai",
            "sab badhiya ke", "kaise ho", "kya chal raha hai", "सुना दे क्या हाल है", "के हाल से", "कैसा है", "सब बढ़िया के",
            "क्या चल रहा है",
        ),
        talk(VoiceIntent.Talk.HowAreYou),
    )

    @Test
    fun greetingsAnswerByTheTimeOfDay() = both(
        listOf("good morning", "ram ram", "ram ram ji", "namaste", "jai ram ji ki", "राम राम", "गुड मॉर्निंग", "नमस्ते", "जय राम जी की"),
        VoiceIntent.Greeting,
    )

    @Test
    fun whoAreYou() = both(
        listOf("kaun hai tu", "tera naam ke se", "tera naam kya hai", "kon hai tu", "कौन है तू", "तेरा नाम के से", "तेरा नाम क्या है"),
        talk(VoiceIntent.Talk.WhoAreYou),
    )

    @Test
    fun thanks() = both(
        listOf("thank you", "thanks", "dhanyawad", "shukriya", "थैंक यू", "धन्यवाद", "शुक्रिया"),
        talk(VoiceIntent.Talk.Thanks),
    )

    @Test
    fun bored() = both(
        listOf("bore ho raha hoon", "baat kar", "baat karo", "mujhse baat karo", "i am bored", "बोर हो रहा हूँ", "बात कर", "बात करो"),
        talk(VoiceIntent.Talk.Bored),
    )

    @Test
    fun aBareSabBadhiyaStaysAnAnswer() {
        // After "bhai kaisa sai?" a "sab badhiya" is the driver's answer (FollowUp), not a question.
        assertTrue(VoiceIntents.parse("sab badhiya", "hi") is VoiceIntent.Unknown)
        assertEquals(FollowUp.Reply.Fine, FollowUp.reply("sab badhiya"))
    }

    @Test
    fun theDay() {
        both(
            listOf(
                "aaj ka din kaisa hai", "aaj ka din kaisa rahega", "aaj kya hai", "aaj ki tareekh", "hey ampli aaj ka din kaisa hai",
                "how is today", "आज का दिन कैसा है", "आज का दिन कैसा रहेगा", "आज क्या है",
            ),
            VoiceIntent.DayToday,
        )
        // The plain date questions keep their own answer.
        both(listOf("aaj kya date hai", "आज क्या तारीख है", "आज कौन सा दिन है"), VoiceIntent.DateToday)
    }

    @Test
    fun news() {
        both(
            listOf(
                "aaj ki taaza khabar", "aaj ki taaza khabar batao", "taaza khabar sunao", "khabar suna", "khabar sunao",
                "khabar suna de", "news batao", "news sunao", "samachar", "samachar sunao", "tell me the news", "news",
                "आज की ताजा खबर", "आज की ताज़ा खबर", "खबर सुनाओ", "समाचार सुनाओ", "न्यूज सुनाओ",
            ),
            VoiceIntent.News(),
        )
        both(listOf("aur khabar", "agli khabar", "aur khabar suna", "और खबर", "अगली खबर"), VoiceIntent.News(more = true))
    }

    @Test
    fun jokes() = both(
        listOf(
            "chutkula suna", "chutkula suna de", "joke suna", "joke sunao", "hasa de", "hansa de", "koi mazedaar baat suna",
            "ek aur chutkula", "tell me a joke", "चुटकुला सुना", "चुटकला सुना दे", "जोक सुना", "हंसा दे", "कोई मजेदार बात सुना",
        ),
        talk(VoiceIntent.Talk.Joke),
    )

    @Test
    fun oneMore() {
        both(listOf("ek aur", "aur suna", "aur sunao", "ek aur suna", "एक और", "और सुना", "और सुनाओ"), talk(VoiceIntent.Talk.More))
        for (p in listOf("ek aur", "aur suna", "aur", "one more", "aur sunao", "और", "एक और")) assertTrue(p, VoiceIntents.asksForMore(p))
        // Only on its own: "aur gaana chalao" is music, "aur kam" is the volume.
        for (p in listOf("aur gaana chalao", "aur kam", "ghar chalo", "")) assertFalse(p, VoiceIntents.asksForMore(p))
        // A volume follow-up "aur" still goes the volume's way.
        assertEquals(VoiceIntent.VolumeAction.Up, VoiceIntents.volumeFollowUp("one more", VoiceIntent.VolumeAction.Up))
    }

    @Test
    fun musicStopInHaryanvi() {
        both(
            listOf("gaana rok de", "gana rok de", "gaana band kar de", "gaana roko", "gana band karo", "गाना रोक दे", "गाना बंद कर दे"),
            VoiceIntent.Pause,
        )
        // Bare "rok de" / "chup": the music if any is playing, else the talk ends. Never the route.
        both(listOf("rok de", "thamja", "tham ja", "chup kar ja", "roko", "chup", "chup kar", "रोक दे", "थम जा", "चुप कर जा", "चुप"), VoiceIntent.Stop)
        both(listOf("navigation band karo", "rasta band karo", "stop navigation"), VoiceIntent.StopNavigation)
    }

    @Test
    fun musicNextPreviousInHaryanvi() {
        both(listOf("agla gaana", "agla laga de", "aage ka", "agla gaana laga de", "अगला लगा दे", "आगे का"), VoiceIntent.Next)
        both(listOf("pichla gaana", "pichla laga de", "पिछला लगा दे"), VoiceIntent.Previous)
        // "Aage ka rasta" is no song.
        assertNotEquals(VoiceIntent.Next, VoiceIntents.parse("aage ka rasta dikhao", "hi"))
    }

    @Test
    fun volumeInHaryanvi() {
        both(listOf("awaaz badha de", "tez kar de", "आवाज बढ़ा दे", "तेज कर दे"), VoiceIntent.Volume(VoiceIntent.VolumeAction.Up))
        both(listOf("awaaz kam kar de", "dheere kar de", "आवाज कम कर दे", "धीरे कर दे"), VoiceIntent.Volume(VoiceIntent.VolumeAction.Down))
    }

    @Test
    fun musicPlayStillPlays() = both(
        listOf("gaana chala de", "baja de", "suna de", "gana baja de", "गाना चला दे", "बजा दे", "सुना दे"),
        VoiceIntent.Resume,
    )

    @Test
    fun regressions() {
        both(listOf("ghar chalo"), VoiceIntent.GoSaved(SavedKind.Home))
        both(listOf("rohtak le chalo"), VoiceIntent.Navigate("rohtak"))
        both(listOf("gana band karo"), VoiceIntent.Pause)
        both(listOf("kitne baje hain"), VoiceIntent.TimeNow)
        both(listOf("camera hai"), VoiceIntent.CameraHere)
        both(listOf("mausam kaisa hai"), VoiceIntent.Weather(rain = false))
        both(listOf("gaana chalao"), VoiceIntent.Resume)
        assertEquals(VoiceIntent.Navigate("rohtak"), VoiceIntents.parse("navigate to rohtak", "en"))
    }

    /** Every phrase above must be in the offline grammar, or Vosk can never hear it. */
    @Test
    fun everyNewPhraseIsInTheGrammar() {
        val en = VoskCommands.phrases(hindi = false).toSet()
        for (p in listOf(
            "suna de kya haal hai", "ke haal se", "kaisa hai", "sab badhiya ke", "good morning", "ram ram", "namaste",
            "kaun hai tu", "tera naam ke se", "thank you", "dhanyawad", "shukriya", "bore ho raha hoon", "baat kar",
            "aaj ka din kaisa hai", "aaj kya hai", "aaj ki tareekh", "aaj ki taaza khabar", "khabar suna", "news batao",
            "samachar sunao", "aur khabar", "chutkula suna", "joke suna", "hasa de", "koi mazedaar baat suna", "ek aur",
            "aur suna", "gaana rok de", "gaana band kar de", "rok de", "thamja", "chup kar ja", "roko", "agla laga de",
            "aage ka", "pichla laga de", "awaaz badha de", "tez kar de", "awaaz kam kar de", "dheere kar de",
            "gana chala de", "baja de", "suna de",
        )) assertTrue("English grammar lacks $p", p in en)
        val hi = VoskCommands.phrases(hindi = true).toSet()
        for (p in listOf(
            "सुना दे क्या हाल है", "के हाल से", "राम राम", "नमस्ते", "कौन है तू", "धन्यवाद", "बोर हो रहा हूँ", "आज का दिन कैसा है",
            "आज की ताजा खबर", "खबर सुना", "समाचार सुनाओ", "और खबर", "चुटकुला सुना", "हंसा दे", "एक और", "गाना रोक दे", "रोक दे",
            "चुप कर जा", "अगला लगा दे", "आगे का", "पिछला लगा दे", "आवाज बढ़ा दे", "धीरे कर दे", "बजा दे",
        )) assertTrue("Hindi grammar lacks $p", p in hi)
        // After a joke or the news the follow-up grammar hears "ek aur" / "aur suna".
        assertTrue(VoskCommands.followUpPhrases().containsAll(listOf("ek aur", "aur suna", "aur khabar")))
        assertTrue(VoskCommands.followUpPhrases(hindi = true).containsAll(listOf("एक और", "और सुना", "और खबर")))
    }

    @Test
    fun newWordsHaveKnownSpellingsForTheEnglishModel() {
        val known = setOf("kabar", "taza", "ki", "aaj", "suna", "de", "tarikh")
        val a = HinglishSpelling.adapt(listOf("aaj ki taaza khabar", "khabar suna de", "aaj ki tareekh"), known::contains)
        assertTrue(a.phrases.toString(), "aaj ki taza kabar" in a.phrases)
        assertEquals(VoiceIntent.News(), VoiceIntents.parse(HinglishSpelling.restore("aaj ki taza kabar", a.back), "en"))
        assertEquals(VoiceIntent.DayToday, VoiceIntents.parse(HinglishSpelling.restore("aaj ki tarikh", a.back), "en"))
    }
}

class AssistantChatLinesTest {

    @Test
    fun everyTopicHasLinesInAllThreeScripts() {
        for (topic in ChatLines.Topic.entries) {
            val lines = ChatLines.lines(topic)
            assertTrue("$topic has no lines", lines.isNotEmpty())
            for (l in lines) {
                assertTrue("$topic deva: ${l.deva}", l.deva.any { it in 'ऀ'..'ॿ' })
                assertFalse("$topic roman: ${l.roman}", l.roman.any { it in 'ऀ'..'ॿ' })
                assertTrue(l.en.isNotBlank())
            }
        }
        for (t in listOf(ChatLines.Topic.HowAreYou, ChatLines.Topic.WhoAreYou, ChatLines.Topic.Thanks, ChatLines.Topic.Bored, ChatLines.Topic.DayWish)) {
            assertTrue("$t needs variety", ChatLines.lines(t).size >= 4)
        }
    }

    @Test
    fun repliesVaryWithoutImmediateRepeats() {
        for (seed in 0..20) {
            val picker = ChatPicker(Random(seed))
            for (topic in ChatLines.Topic.entries.filter { ChatLines.lines(it).size > 1 }) {
                var prev: ChatLines.Line? = null
                val seen = HashSet<ChatLines.Line>()
                repeat(200) {
                    val l = picker.pick(topic)
                    assertNotEquals("$topic repeated", prev, l)
                    prev = l
                    seen += l
                }
                assertEquals("$topic: every variant gets used", ChatLines.lines(topic).toSet(), seen)
            }
        }
    }

    @Test
    fun howAreYouGreetsByTheTimeOfDay() {
        val p = ChatPicker(Random(1))
        assertTrue(ChatLines.howAreYou(8, ChatLines.Script.Roman, p).let { it.startsWith("Subah") || it.startsWith("Savere") })
        assertTrue(ChatLines.howAreYou(14, ChatLines.Script.Roman, p).contains("opahar"))
        assertTrue(ChatLines.howAreYou(19, ChatLines.Script.Roman, p).let { it.startsWith("Shaam") || it.startsWith("Saanjh") })
        assertTrue(ChatLines.howAreYou(23, ChatLines.Script.Roman, p).contains("aat"))
        assertEquals(ChatLines.Topic.Night, ChatLines.timeOfDay(2))
        assertEquals(ChatLines.Topic.Morning, ChatLines.timeOfDay(4))
    }

    @Test
    fun boredOffersAJoke() {
        // "…chutkula suna doon?": the answer "haan" tells one, so every line must ask.
        for (l in ChatLines.lines(ChatLines.Topic.Bored)) assertTrue(l.deva, FollowUp.expectsAnswer(l.deva))
        assertEquals(FollowUp.Reply.Fine, FollowUp.reply("haan"))
    }

    @Test
    fun dayAnswerHasTheVaarDeityAndRealWeather() {
        val tuesday = LocalDate.of(2026, 11, 3)
        val p = ChatPicker(Random(3))
        val roman = ChatLines.dayAnswer(tuesday, ChatLines.DayWeather.Now(Sky.Clear, 24, rainLikely = false), ChatLines.Script.Roman, p)
        assertTrue(roman, roman.startsWith("Aaj Mangalvaar sai, 3 November."))
        assertTrue(roman, roman.contains("Hanuman ji ka din sai"))
        assertTrue(roman, roman.contains("24 degree"))
        val deva = ChatLines.dayAnswer(tuesday, ChatLines.DayWeather.Now(Sky.Rain, null, rainLikely = true), ChatLines.Script.Deva, p)
        assertTrue(deva, deva.startsWith("आज मंगलवार सै, 3 नवंबर।"))
        assertTrue(deva, deva.contains("हनुमान जी") && deva.contains("छाता"))
        // Saturday: Shani Dev and Hanuman ji; Monday Shiv, Sunday Surya.
        assertTrue(ChatLines.deityLine(java.time.DayOfWeek.SATURDAY, ChatLines.Script.Roman).contains("Shani Dev ar Hanuman ji"))
        assertTrue(ChatLines.deityLine(java.time.DayOfWeek.MONDAY, ChatLines.Script.Deva).contains("शिव जी"))
        assertTrue(ChatLines.deityLine(java.time.DayOfWeek.SUNDAY, ChatLines.Script.Roman).contains("Surya Dev"))
    }

    @Test
    fun dayAnswerNeverMakesUpWeather() {
        val day = LocalDate.of(2026, 11, 4)
        val p = ChatPicker(Random(4))
        for (w in listOf(ChatLines.DayWeather.Offline, ChatLines.DayWeather.NoLocation, ChatLines.DayWeather.Failed)) {
            val s = ChatLines.dayAnswer(day, w, ChatLines.Script.Roman, p)
            assertFalse(s, s.contains("degree"))
            assertFalse(s, s.startsWith("Mausam:") || s.contains("Mausam: "))
            assertTrue(s, s.contains("Budhvaar") && s.contains("Ganesh ji"))
        }
        assertTrue(ChatLines.dayAnswer(day, ChatLines.DayWeather.Offline, ChatLines.Script.Roman, p).contains("net koni chaal raya"))
    }
}

class AssistantJokesTest {

    @Test
    fun aboutFortyOriginalJokes() {
        assertTrue(Jokes.ALL.size >= 40)
        assertEquals(Jokes.ALL.size, Jokes.ALL.map { it.deva }.toSet().size)
        assertEquals(Jokes.ALL.size, Jokes.ALL.map { it.roman }.toSet().size)
        for (j in Jokes.ALL) {
            assertTrue(j.deva, j.deva.any { it in 'ऀ'..'ॿ' })
            assertFalse(j.roman, j.roman.any { it in 'ऀ'..'ॿ' })
            assertTrue(j.en.isNotBlank())
            assertTrue("short enough to listen to while driving: ${j.roman}", j.roman.length <= 180)
        }
    }

    @Test
    fun noRepeatsInASession() {
        for (seed in 0..10) {
            val deck = JokeDeck(random = Random(seed))
            val round = List(Jokes.ALL.size) { deck.next() }
            assertEquals(Jokes.ALL.toSet(), round.toSet())
            var prev = round.last()
            repeat(Jokes.ALL.size * 3) {
                val j = deck.next()
                assertNotEquals(prev, j)
                prev = j
            }
        }
    }

    @Test
    fun familySafe() = assertFamilySafe(Jokes.ALL + ChatLines.all.values.flatten())

    companion object {
        /** The same banned list as the Haryanvi line bank (HaryanviLinesTest), plus politics. */
        fun assertFamilySafe(lines: List<ChatLines.Line>) {
            val bannedRoman = listOf(
                "chod", "chutiya", "chutia", "saala", "sala", "saali", "kamina", "kamine", "kutta", "kutte", "kutiya",
                "gaand", "gand", "lund", "loda", "lauda", "randi", "harami", "haramkhor", "bhosdi", "bhosdike",
                "madarchod", "behenchod", "bhenchod", "mc", "bc", "bsdk", "tatti", "gadha", "ullu", "pagal",
                "bewakoof", "bevkoof", "nalayak", "badtameez", "sharab", "daaru", "daru", "nasha", "beti",
                "jaat", "jat", "chamar", "bania", "baniya", "gujjar", "ahir", "brahmin", "pandit", "dalit",
                "harijan", "dhobi", "mullah", "mulla", "sardar", "chhakka", "hijra",
                "modi", "rahul", "bjp", "congress", "neta", "election", "chunav", "mota", "moti", "kala", "kaali", "ganja",
            )
            val bannedDeva = listOf(
                "चोद", "चूतिया", "साला", "साली", "कमीना", "कमीने", "कुत्ता", "कुत्ते", "कुतिया", "गांड", "गाँड", "लंड",
                "रंडी", "हरामी", "भोसड़ी", "मादरचोद", "बहनचोद", "तट्टी", "गधा", "उल्लू", "पागल", "बेवकूफ", "नालायक",
                "बदतमीज", "शराब", "दारू", "नशा", "बेटी", "जाट", "चमार", "बनिया", "गुर्जर", "अहीर", "ब्राह्मण", "पंडित",
                "दलित", "हरिजन", "धोबी", "नाई", "मुल्ला", "सरदार", "छक्का", "हिजड़ा", "नेता", "चुनाव", "मोटा", "मोटी", "काली",
            )
            for (l in lines) {
                val words = l.roman.lowercase().split(Regex("[^a-z]+")).toSet() + l.en.lowercase().split(Regex("[^a-z]+"))
                // "kala" is "art" in "rukna bhi ek kala sai"; colour words are checked in Devanagari.
                for (b in bannedRoman - "kala") assertFalse("\"${l.roman}\" contains $b", b in words)
                val devaWords = l.deva.split(Regex("[^\\p{L}\\p{M}]+")).toSet()
                for (b in bannedDeva) assertFalse("\"${l.deva}\" contains $b", b in devaWords)
            }
        }
    }
}

class NewsFeedTest {

    /** As PIB's Hindi feed sends it: a byte-order mark, no CDATA, titles only. */
    private val pib = "﻿<?xml version=\"1.0\" encoding=\"utf-8\"?><rss version=\"2.0\"><channel><title>Press Information Bureau </title>" +
        "<link>https://pib.gov.in</link>" +
        "<item><title>खाद्य और सार्वजनिक वितरण विभाग के विशेष अभियान 6 का समापन</title><link>https://pib.gov.in/PressReleaseIframePage.aspx?PRID=1</link></item>" +
        "<item><title>केरलम के राज्यपाल प्रधानमंत्री से मिले</title><link>https://pib.gov.in/PressReleaseIframePage.aspx?PRID=2</link></item>" +
        "<item><title>  भारतीय प्रतिस्पर्धा आयोग ने\n   विलय को मंजूरी दी </title><link>x</link></item>" +
        "<item><title>केरलम के राज्यपाल प्रधानमंत्री से मिले</title><link>dup</link></item>" +
        "<item><title></title><link>empty</link></item>" +
        "<item><title>चौथी खबर</title></item><item><title>पांचवीं खबर</title></item>" +
        "</channel></rss>"

    /** As the newsonair feed sends it: double-escaped quotes, and a CDATA title for good measure. */
    private val air = """<rss version="2.0"><channel><title>Newsonair national feed</title><language>hi</language>
        <item><title>&amp;#8216;लोकतंत्र बचाओ&amp;#8217; पदयात्रा</title><pubDate>Wed, 04 Nov 2026 10:00:00 +0530</pubDate></item>
        <item><title><![CDATA[इस्राएल: नई सरकार पर <b>विचार</b> &amp; चर्चा]]></title></item>
        </channel></rss>"""

    @Test
    fun parsesOnlyItemTitles() {
        val t = NewsFeed.parseTitles(pib)
        assertEquals(
            listOf(
                "खाद्य और सार्वजनिक वितरण विभाग के विशेष अभियान 6 का समापन", "केरलम के राज्यपाल प्रधानमंत्री से मिले",
                "भारतीय प्रतिस्पर्धा आयोग ने विलय को मंजूरी दी", "चौथी खबर", "पांचवीं खबर",
            ),
            t,
        )
        assertFalse(t.any { it.contains("Press Information Bureau") })
        assertEquals(listOf("‘लोकतंत्र बचाओ’ पदयात्रा", "इस्राएल: नई सरकार पर विचार & चर्चा"), NewsFeed.parseTitles(air))
        assertTrue(NewsFeed.parseTitles("not a feed").isEmpty())
        assertTrue(NewsFeed.parseTitles("<html><body>502 Bad Gateway</body></html>").isEmpty())
        assertEquals(2, NewsFeed.parseTitles(pib, max = 2).size)
    }

    @Test
    fun readsThreeAtATimeWithTheSourceFirst() {
        val titles = NewsFeed.parseTitles(pib)
        val first = NewsFeed.batch(titles, 0)
        assertEquals(3, first.size)
        val src = NewsFeed.source("https://pib.gov.in/RssMain.aspx?ModId=6&Lang=2&Regid=3")
        val said = NewsFeed.speech(src, first, ChatLines.Script.Deva, first = true, moreLeft = true)
        assertTrue(said, said.startsWith("पीआईबी की ताज़ा खबर: खाद्य और"))
        assertTrue(said, said.endsWith(ChatLines.lines(ChatLines.Topic.NewsMore).first().deva))
        val next = NewsFeed.batch(titles, 3)
        assertEquals(listOf("चौथी खबर", "पांचवीं खबर"), next)
        val later = NewsFeed.speech(src, next, ChatLines.Script.Deva, first = false, moreLeft = false)
        assertEquals("चौथी खबर। पांचवीं खबर।", later)
        assertTrue(NewsFeed.batch(titles, 5).isEmpty())
        assertEquals("Akashvani ki taaza khabar:", NewsFeed.intro(NewsFeed.source("https://newsonair.gov.in/hi/rss-feeds/"), ChatLines.Script.Roman))
        assertEquals("आकाशवाणी", NewsFeed.source("https://www.newsonair.gov.in/hi/rss-feeds/").deva)
    }

    @Test
    fun cachesForThirtyMinutesWithinSixSeconds() {
        assertEquals(30L * 60 * 1000, NewsFeed.CACHE_MS)
        assertEquals(6_000, NewsFeed.TIMEOUT_MS)
        assertTrue(NewsFeed.fresh(1_000L, 1_000L + 29 * 60_000L))
        assertFalse(NewsFeed.fresh(1_000L, 1_000L + 31 * 60_000L))
        assertFalse(NewsFeed.fresh(0L, 10L))
    }

    @Test
    fun offlineSaysSoHonestly() {
        val lines = ChatLines.lines(ChatLines.Topic.NewsOffline)
        assertTrue(lines.any { it.roman == "Net koni chaal raya, khabar koni mili." })
        for (l in lines + ChatLines.lines(ChatLines.Topic.NewsFailed)) {
            // No headline, no source: just why there is none.
            assertTrue(l.roman, l.roman.contains("khabar", ignoreCase = true) || l.roman.contains("net", ignoreCase = true))
            assertFalse(l.roman, l.roman.contains(":"))
        }
    }
}
