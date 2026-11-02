package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeSensitivityTest {
    @Test
    fun strictAcceptsOnlyRealPhrasesWithHighConfidence() {
        val t = WakeSensitivity.tuning(1)
        assertFalse(t.acceptPartial)
        assertFalse("hey amply" in t.phrases)
        assertTrue(WakeSensitivity.acceptsFinal("hey ampli", listOf("hey" to 0.95, "ampli" to 0.9), t))
        assertFalse(WakeSensitivity.acceptsFinal("hey ampli", listOf("hey" to 0.7, "ampli" to 0.6), t))
        assertFalse(WakeSensitivity.acceptsPartial("hey ampli", t))
    }

    @Test
    fun eagerAcceptsSoundAlikesAndTwoWordPartials() {
        val t = WakeSensitivity.tuning(5)
        assertTrue(WakeSensitivity.acceptsPartial("[unk] hey ample", t))
        assertTrue(WakeSensitivity.acceptsFinal("hi amply", listOf("hi" to 0.5, "amply" to 0.4), t))
        // Even the eagerest level wants some confidence now.
        assertFalse(WakeSensitivity.acceptsFinal("hi amply", listOf("hi" to 0.2, "amply" to 0.1), t))
    }

    @Test
    fun partialOfJustTheNameNeverWakes() {
        for (level in 1..5) {
            assertFalse("level $level", WakeSensitivity.acceptsPartial("ampli", WakeSensitivity.tuning(level)))
            assertFalse("level $level", WakeSensitivity.acceptsPartial("[unk] amply [unk]", WakeSensitivity.tuning(level)))
        }
    }

    @Test
    fun defaultLevelIgnoresPartials() {
        val t = WakeSensitivity.tuning(3)
        assertFalse(t.acceptPartial)
        assertFalse(WakeSensitivity.acceptsPartial("hey ampli", t))
    }

    @Test
    fun unkWordsAroundThePhraseAreIgnored() {
        val t = WakeSensitivity.tuning(3)
        val words = listOf("[unk]" to 0.1, "ok" to 0.8, "ampli" to 0.7)
        assertTrue(WakeSensitivity.acceptsFinal("[unk] ok ampli", words, t))
        assertFalse(WakeSensitivity.acceptsFinal("[unk]", listOf("[unk]" to 1.0), t))
        val t2 = WakeSensitivity.tuning(2)
        assertFalse(WakeSensitivity.acceptsFinal("ampli hey", listOf("ampli" to 1.0, "hey" to 1.0), t2))
    }

    @Test
    fun justTheNameNeedsAClearWordAtTheStart() {
        val t = WakeSensitivity.tuning(3)
        assertTrue("ampli" in t.phrases)
        assertTrue(WakeSensitivity.acceptsFinal("ampli", listOf("ampli" to 0.9), t))
        assertTrue(WakeSensitivity.acceptsFinal("ampli [unk] [unk]", listOf("ampli" to 0.85, "[unk]" to 0.5, "[unk]" to 0.5), t))
        assertTrue(WakeSensitivity.acceptsFinal("[unk] ampli", listOf("[unk]" to 0.2, "ampli" to 0.82), t))
        // Not clear enough for one short word.
        assertFalse(WakeSensitivity.acceptsFinal("ampli", listOf("ampli" to 0.7), t))
        // In the middle of someone's talk.
        assertFalse(
            WakeSensitivity.acceptsFinal(
                "[unk] [unk] ampli [unk]",
                listOf("[unk]" to 0.4, "[unk]" to 0.4, "ampli" to 0.95, "[unk]" to 0.4),
                t,
            ),
        )
        // Without a word list the name alone is not trusted.
        assertFalse(WakeSensitivity.acceptsFinal("ampli", emptyList(), t))
        // Strict levels still need "hey / ok / hello Ampli".
        assertFalse(WakeSensitivity.acceptsFinal("ampli", listOf("ampli" to 1.0), WakeSensitivity.tuning(2)))
    }

    @Test
    fun soloThresholdIsAboveThePhraseThreshold() {
        for (level in 3..5) {
            val t = WakeSensitivity.tuning(level)
            assertTrue("level $level", t.soloConfidence > t.minConfidence)
        }
    }

    @Test
    fun levelsAreClampedAndGrammarIsJson() {
        assertEquals(WakeSensitivity.tuning(1), WakeSensitivity.tuning(-4))
        assertEquals(WakeSensitivity.tuning(5), WakeSensitivity.tuning(99))
        val g = WakeSensitivity.tuning(1).grammarJson
        assertEquals("""["hey ampli", "ok ampli", "okay ampli", "hello ampli", "[unk]"]""", g)
    }

    @Test
    fun finalWithoutWordListFallsBackToText() {
        assertTrue(WakeSensitivity.acceptsFinal("hey ampli", emptyList(), WakeSensitivity.tuning(1)))
    }
}
