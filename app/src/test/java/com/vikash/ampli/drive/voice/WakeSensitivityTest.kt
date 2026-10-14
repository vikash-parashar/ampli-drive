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
    fun eagerAcceptsSoundAlikesAndPartials() {
        val t = WakeSensitivity.tuning(5)
        assertTrue(WakeSensitivity.acceptsPartial("[unk] hey ample", t))
        assertTrue(WakeSensitivity.acceptsFinal("hi amply", listOf("hi" to 0.2, "amply" to 0.1), t))
    }

    @Test
    fun unkWordsAroundThePhraseAreIgnored() {
        val t = WakeSensitivity.tuning(3)
        val words = listOf("[unk]" to 0.1, "ok" to 0.8, "ampli" to 0.7)
        assertTrue(WakeSensitivity.acceptsFinal("[unk] ok ampli", words, t))
        assertFalse(WakeSensitivity.acceptsFinal("[unk]", listOf("[unk]" to 1.0), t))
        assertFalse(WakeSensitivity.acceptsFinal("ampli hey", listOf("ampli" to 1.0, "hey" to 1.0), t))
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
