package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The conversation loop after Ampli speaks: wait for the line, listen, understand the answer. */
class FollowUpTest {

    private fun w(vararg pairs: Pair<String, Double>) = pairs.map { HeardWord(it.first, it.second) }

    // ------------------------------------------------------------ completion of each output path

    @Test
    fun clipLineEndsAtItsKnownLengthWhenStateEndedIsLost() {
        // Two clips, 3.4 s in all: the microphone may open 1.2 s later at most.
        assertEquals(3_400L + SpeechCompletion.CLIP_GRACE_MS, SpeechCompletion.clipDeadlineMs(3_400L, 2))
        // Far sooner than the old fixed allowance for two clips.
        assertTrue(SpeechCompletion.clipDeadlineMs(3_400L, 2) < SpeechCompletion.clipDeadlineMs(null, 2))
    }

    @Test
    fun clipLineWithUnknownLengthKeepsThePerClipAllowance() {
        assertEquals(13_000L, SpeechCompletion.clipDeadlineMs(null, 1))
        assertEquals(21_000L, SpeechCompletion.clipDeadlineMs(null, 2))
        assertEquals(13_000L, SpeechCompletion.clipDeadlineMs(0L, 0))
        assertEquals(SpeechCompletion.CLIP_MAX_MS, SpeechCompletion.clipDeadlineMs(600_000L, 3))
    }

    @Test
    fun systemTtsEndsWhenTheEngineGoesQuietWithoutOnDone() {
        // Seen speaking, now quiet for 400 ms: over.
        assertTrue(SpeechCompletion.ttsFinished(elapsedMs = 2_500, chars = 40, sawSpeaking = true, quietMs = 400))
        // Seen speaking, quiet only a moment (a pause between sentences): not yet.
        assertFalse(SpeechCompletion.ttsFinished(elapsedMs = 2_500, chars = 40, sawSpeaking = true, quietMs = 100))
    }

    @Test
    fun systemTtsNeverSeenSpeakingWaitsForTheEstimatedLength() {
        val chars = 50
        val estimate = SpeechCompletion.ttsEstimateMs(chars)
        assertFalse(SpeechCompletion.ttsFinished(estimate - 1, chars, sawSpeaking = false, quietMs = estimate - 1))
        assertTrue(SpeechCompletion.ttsFinished(estimate, chars, sawSpeaking = false, quietMs = estimate))
    }

    @Test
    fun systemTtsHardDeadlineAlwaysEndsTheWait() {
        val chars = 30
        val deadline = SpeechCompletion.ttsDeadlineMs(chars)
        // Engine claims to be speaking forever: the deadline still ends it.
        assertTrue(SpeechCompletion.ttsFinished(deadline, chars, sawSpeaking = true, quietMs = 0))
        assertFalse(SpeechCompletion.ttsFinished(deadline - 1, chars, sawSpeaking = true, quietMs = 0))
        assertTrue(SpeechCompletion.ttsEstimateMs(chars) < deadline)
    }

    // ------------------------------------------------------------------ when to keep listening

    @Test
    fun questionLinesExpectAnAnswer() {
        assertTrue(FollowUp.expectsAnswer("जय बाबा की भाई! कित चालणा सै आज?"))
        assertTrue(FollowUp.expectsAnswer("भाई कैसा सै, सब राजी खुशी? परिवार में सब ठीक?"))
        assertTrue(FollowUp.expectsAnswer("How is your day going? Where do you want to go today? "))
        assertTrue(FollowUp.expectsAnswer("Bol tau, ke seva karun?\""))
        assertFalse(FollowUp.expectsAnswer("ठीक सै, कर दिया।"))
        // A question in the middle is not a question to the driver.
        assertFalse(FollowUp.expectsAnswer("Rohtak? Ibe talash karun."))
        assertFalse(FollowUp.expectsAnswer(""))
        assertFalse(FollowUp.expectsAnswer(null))
    }

    @Test
    fun everyGreetingThatAsksIsFollowedByListening() {
        val asking = HaryanviLines.forEvent(HaryanviLines.Event.Greeting).orEmpty().count { FollowUp.expectsAnswer(it.deva) }
        assertTrue(asking >= 3)
    }

    @Test
    fun afterAnyLineTheFollowUpGrammarIsUsed() {
        assertEquals(ListenMode.FollowUp, FollowUp.mode(destination = false, spokeLine = true))
        assertEquals(ListenMode.Commands, FollowUp.mode(destination = false, spokeLine = false))
        assertEquals(ListenMode.Free, FollowUp.mode(destination = true, spokeLine = true))
    }

    @Test
    fun cooldownDoesNotBlockTheFollowUp() {
        val now = 100_000L
        val lastAction = now - 1_000L
        // The wake word rests…
        assertTrue(SpeechGate.coolingDown(lastAction, now))
        // …but the answer to Ampli's question is still listened for.
        assertTrue(FollowUp.mayListen(sessionActive = true, inCall = false, lastActionAt = lastAction, now = now))
        assertFalse(FollowUp.mayListen(sessionActive = true, inCall = true, lastActionAt = 0L, now = now))
        assertFalse(FollowUp.mayListen(sessionActive = false, inCall = false, lastActionAt = 0L, now = now))
    }

    @Test
    fun silenceAfterALineClosesWithOneShortLine() {
        assertEquals(FollowUp.Silence.CloseWithLine, FollowUp.onSilence(spokeLine = true))
        assertEquals(FollowUp.Silence.CloseQuietly, FollowUp.onSilence(spokeLine = false))
    }

    // --------------------------------------------------------------------------- grammar union

    @Test
    fun followUpGrammarHoldsCommandsRepliesAndSmallTalk() {
        for (hindi in listOf(false, true)) {
            val all = VoskCommands.followUpPhrases(hindi).toSet()
            assertTrue(all.containsAll(VoskCommands.phrases(hindi)))
            assertTrue(all.containsAll(VoskCommands.replyPhrases(hindi)))
            assertTrue(all.containsAll(if (hindi) FollowUp.HINDI_CHAT else FollowUp.CHAT))
            assertEquals(all.size, VoskCommands.followUpPhrases(hindi).size)
            assertTrue(VoskCommands.followUpGrammarJson(hindi).endsWith("\"[unk]\"]"))
        }
        val en = VoskCommands.followUpPhrases()
        for (p in listOf("haan", "nahi", "gaana chalao", "ghar chalo", "le chalo", "badhiya", "theek hai")) {
            assertTrue(p, p in en)
        }
        assertTrue("हाँ" in VoskCommands.followUpPhrases(true))
        assertTrue("गाना चलाओ" in VoskCommands.followUpPhrases(true))
    }

    @Test
    fun followUpCommandsStillParseAsCommands() {
        assertEquals(VoiceIntent.Resume, VoiceIntents.parse("gaana chalao", "en"))
        assertTrue(VoiceIntents.parse("rohtak le chalo", "en") is VoiceIntent.Navigate)
    }

    @Test
    fun smallTalkAnswersAreUnderstood() {
        for (p in FollowUp.CHAT + FollowUp.HINDI_CHAT) {
            assertTrue(p, FollowUp.reply(p) != FollowUp.Reply.Other)
        }
        assertEquals(FollowUp.Reply.Fine, FollowUp.reply("haan"))
        assertEquals(FollowUp.Reply.Fine, FollowUp.reply("sab badhiya"))
        assertEquals(FollowUp.Reply.Fine, FollowUp.reply("हाँ जी"))
        assertEquals(FollowUp.Reply.No, FollowUp.reply("nahi"))
        assertEquals(FollowUp.Reply.No, FollowUp.reply("kuch nahi"))
        assertEquals(FollowUp.Reply.No, FollowUp.reply("रहने दे"))
        assertEquals(FollowUp.Reply.Other, FollowUp.reply("rohtak"))
        assertEquals(FollowUp.Reply.Other, FollowUp.reply(""))
    }

    // ------------------------------------------------------------------------------ thresholds

    @Test
    fun followUpIsGentlerThanCommands() {
        val haan = w("haan" to 0.5)
        assertFalse(SpeechGate.accept("haan", haan, ListenMode.Replies))
        assertFalse(SpeechGate.accept("haan", haan, ListenMode.Commands))
        assertTrue(SpeechGate.accept("haan", haan, ListenMode.FollowUp))
        // An answer with a breath around it.
        assertTrue(SpeechGate.accept("[unk] gaana chalao", w("[unk]" to 0.4, "gaana" to 0.55, "chalao" to 0.6), ListenMode.FollowUp))
        // Still not noise.
        assertFalse(SpeechGate.accept("[unk]", w("[unk]" to 1.0), ListenMode.FollowUp))
        assertFalse(SpeechGate.accept("[unk] [unk] [unk] ok", w("[unk]" to 0.9, "[unk]" to 0.9, "[unk]" to 0.9, "ok" to 0.9), ListenMode.FollowUp))
        assertFalse(SpeechGate.accept("haan", w("haan" to 0.3), ListenMode.FollowUp))
    }

    @Test
    fun launchAndPlaceGatesRelaxOnlyInFollowUp() {
        val q = SpeechQuality(known = 2, unknown = 0, meanConf = 0.65)
        assertFalse(SpeechGate.mayLaunch(q))
        assertTrue(SpeechGate.mayLaunch(q, followUp = true))
        assertFalse(SpeechGate.mayLaunch(SpeechQuality(2, 1, 0.9), followUp = true))
        assertFalse(SpeechGate.destinationOk("rohtak", SpeechQuality(1, 0, 0.65)))
        assertTrue(SpeechGate.destinationOk("rohtak", SpeechQuality(1, 0, 0.65), followUp = true))
        assertFalse(SpeechGate.destinationOk("rohtak", SpeechQuality(1, 0, 0.5), followUp = true))
    }

    @Test
    fun unknownWordEndsAFollowUpListenEarly() {
        val unk = w("[unk]" to 1.0)
        assertTrue(FollowUp.unclearEndsListen(ListenMode.FollowUp, unk, 0.6))
        // A click or a breath.
        assertFalse(FollowUp.unclearEndsListen(ListenMode.FollowUp, unk, 0.1))
        // Outside a follow-up "[unk]" stays noise, as before.
        assertFalse(FollowUp.unclearEndsListen(ListenMode.Commands, unk, 0.6))
        assertFalse(FollowUp.unclearEndsListen(ListenMode.FollowUp, w("[unk]" to 1.0, "haan" to 0.9), 0.6))
    }

    // ------------------------------------------- Roman Hindi on the English model (no Hindi model)

    @Test
    fun missingHinglishWordsAreSwappedForKnownSpellings() {
        // A model that knows "gana" and "chalo"-like spellings but not "gaana" / "chalao".
        val vocab = setOf("gana", "chalaw", "play", "music", "le", "ghar", "chalo")
        val a = HinglishSpelling.adapt(listOf("gaana chalao", "ghar chalo", "play music"), vocab::contains)
        assertTrue(a.phrases.contains("gana chalaw"))
        assertTrue(a.phrases.contains("ghar chalo"))
        assertTrue(a.phrases.contains("play music"))
        // What the model hears is turned back into the phrase the parser knows.
        assertEquals("gaana chalao", HinglishSpelling.restore("gana chalaw", a.back))
        assertEquals(VoiceIntent.Resume, VoiceIntents.parse(HinglishSpelling.restore("gana chalaw", a.back), "en"))
        assertEquals("ghar chalo", HinglishSpelling.restore("ghar chalo", a.back))
    }

    @Test
    fun aVariantThatIsAnotherPhrasesWordIsNeverUsed() {
        // "chala" would do for "chalao", but turning "chala" back would break nothing only if no
        // phrase uses it; here one does, so the word stays (Vosk drops it, as before).
        val vocab = setOf("chala", "gana")
        val a = HinglishSpelling.adapt(listOf("gaana chalao", "chala"), vocab::contains)
        assertTrue(a.phrases.contains("gana chalao"))
        assertEquals("chala", HinglishSpelling.restore("chala", a.back))
    }

    @Test
    fun knownWordsAndAFullVocabularyChangeNothing() {
        val all = VoskCommands.followUpPhrases()
        val a = HinglishSpelling.adapt(all, { true })
        assertEquals(all, a.phrases)
        assertTrue(a.back.isEmpty())
        assertEquals("next song", HinglishSpelling.restore("next song", a.back))
    }
}
