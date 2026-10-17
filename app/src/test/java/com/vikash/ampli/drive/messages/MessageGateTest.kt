package com.vikash.ampli.drive.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageGateTest {
    private val on = GateSettings(enabled = true, disclosureAccepted = true, isPro = true, allowGroups = false)
    private val t0 = 1_000_000_000L

    private fun msg(sender: String = "Rahul", text: String = "कहाँ पहुँचे?", group: Boolean = false, time: Long = t0) =
        IncomingMessage("com.whatsapp", sender, text, group, time)

    @Test
    fun speaksADirectMessage() {
        val r = MessageGate().decide(msg(), on, t0)
        assertEquals(GateResult.Speak("Rahul", "कहाँ पहुँचे?"), r)
    }

    @Test
    fun offWithoutSettingDisclosureOrPro() {
        val g = MessageGate()
        assertEquals(GateResult.Skip(GateResult.Reason.Off), g.decide(msg(), on.copy(enabled = false), t0))
        assertEquals(GateResult.Skip(GateResult.Reason.NoDisclosure), g.decide(msg(), on.copy(disclosureAccepted = false), t0))
        assertEquals(GateResult.Skip(GateResult.Reason.NotPro), g.decide(msg(), on.copy(isPro = false), t0))
    }

    @Test
    fun groupsSkippedUnlessAllowed() {
        assertEquals(GateResult.Skip(GateResult.Reason.Group), MessageGate().decide(msg(group = true), on, t0))
        assertTrue(MessageGate().decide(msg(group = true), on.copy(allowGroups = true), t0) is GateResult.Speak)
    }

    @Test
    fun oldAndDuplicateMessagesAreSkipped() {
        val g = MessageGate()
        assertEquals(GateResult.Skip(GateResult.Reason.Old), g.decide(msg(time = t0 - 3 * 60_000L), on, t0))
        assertTrue(g.decide(msg(), on, t0) is GateResult.Speak)
        assertEquals(GateResult.Skip(GateResult.Reason.Duplicate), g.decide(msg(), on, t0 + 60_000L))
    }

    @Test
    fun rateLimits() {
        val g = MessageGate()
        assertTrue(g.decide(msg("A", "1"), on, t0) is GateResult.Speak)
        // Another sender 5 s later: too soon.
        assertEquals(GateResult.Skip(GateResult.Reason.RateLimited), g.decide(msg("B", "2", time = t0 + 5_000), on, t0 + 5_000))
        // Same sender 30 s later: per-sender gap.
        assertEquals(GateResult.Skip(GateResult.Reason.RateLimited), g.decide(msg("A", "3", time = t0 + 30_000), on, t0 + 30_000))
        assertTrue(g.decide(msg("B", "4", time = t0 + 30_000), on, t0 + 30_000) is GateResult.Speak)
        // At most six in ten minutes.
        var now = t0 + 30_000
        var spoken = 2
        for (i in 0 until 10) {
            now += 61_000
            if (g.decide(msg("S$i", "m$i", time = now), on, now) is GateResult.Speak) spoken++
        }
        assertTrue("spoke $spoken", spoken <= MessageGate.MAX_PER_WINDOW + 1)
    }

    @Test
    fun cleansSenderAndText() {
        assertEquals("Rahul", MessageGate.cleanSender("Rahul (3 messages)"))
        assertEquals("Rahul", MessageGate.cleanSender("~ Rahul"))
        assertEquals("घर आ जा", MessageGate.cleanText("घर आ जा 😀🙏🏽"))
        assertEquals("देख", MessageGate.cleanText("देख https://example.com/x?y=1"))
        val long = "शब्द ".repeat(80)
        val cut = MessageGate.cleanText(long)
        assertTrue(cut.length <= MessageGate.MAX_CHARS + 1)
        assertTrue(cut.endsWith("…"))
    }

    @Test
    fun phrases() {
        assertEquals("Rahul का मैसेज: कहाँ पहुँचे?", MessagePhrases.full("Rahul", "कहाँ पहुँचे?", hindi = true))
        assertEquals("Message from Rahul: ok", MessagePhrases.full("Rahul", "ok", hindi = false))
        assertEquals("Rahul का मैसेज आया है।", MessagePhrases.full("Rahul", "", hindi = true))
        assertEquals("Rahul: ok", MessagePhrases.afterClip("Rahul", "ok"))
    }
}
