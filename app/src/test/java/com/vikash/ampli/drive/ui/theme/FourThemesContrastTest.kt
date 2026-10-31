package com.vikash.ampli.drive.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four home themes' palettes: body text at least 4.5:1 on every surface it sits on, white
 * (or black) on the accent keys, and SOS a fixed red with white text, apart from every accent.
 * Plain JVM: [HomePalettes] holds plain ARGB values.
 */
class FourThemesContrastTest {

    private fun check(what: String, fg: Long, bg: Long, min: Double = Contrast.AA_TEXT) {
        val r = Contrast.ratio(fg, bg)
        assertTrue("$what: %.2f < $min".format(r), r >= min)
    }

    @Test
    fun ratioMatchesKnownValues() {
        assertEquals(21.0, Contrast.ratio(0xFF000000, 0xFFFFFFFF), 0.01)
        assertEquals(1.0, Contrast.ratio(0xFF777777, 0xFF777777), 0.001)
        // #767676 on white is the classic 4.54:1 AA threshold grey.
        assertEquals(4.54, Contrast.ratio(0xFF767676, 0xFFFFFFFF), 0.01)
    }

    @Test
    fun overBlendsByAlpha() {
        assertEquals(0xFFFFFFFF, Contrast.over(0x00000000, 0xFFFFFFFF))
        assertEquals(0xFF000000, Contrast.over(0xFF000000, 0xFFFFFFFF))
        assertEquals(0xFF7F7F7F, Contrast.over(0x80000000, 0xFFFFFFFF))
    }

    @Test
    fun exactlyFourPalettes() {
        assertEquals(4, HomePalettes.all.size)
    }

    @Test
    fun textReadsOnEverySurface() {
        for (p in HomePalettes.all) {
            for ((name, bg) in listOf("background" to p.background, "surface" to p.surface)) {
                check("text on $name ($p)", p.text, bg)
                check("muted on $name ($p)", p.muted, bg)
            }
            check("label on the accent key ($p)", p.onAccent, p.accent)
            check("ink tile text ($p)", p.onInk, p.ink)
        }
    }

    @Test
    fun mapFirstCardsReadOverTheMap() {
        val p = HomePalettes.MapFirst
        // Worst case under the tinted card: the lightest map colour (Positron's near-white roads).
        for (card in listOf(HomePalettes.MAP_FIRST_CARD, HomePalettes.MAP_FIRST_CARD_SOLID)) {
            val bg = Contrast.over(card, 0xFFFFFFFF)
            check("text on Map First card", p.text, bg)
            check("muted on Map First card", p.muted, bg)
        }
        check("Ampli bubble text", HomePalettes.MAP_FIRST_BUBBLE_INK, HomePalettes.MAP_FIRST_BUBBLE)
        check("Ampli label in the bubble", p.accent, HomePalettes.MAP_FIRST_BUBBLE)
    }

    @Test
    fun sosIsTheSameRedEverywhereAndReadable() {
        check("white on SOS", HomePalettes.ON_SOS, HomePalettes.SOS)
        assertEquals(0xFFD7263DL, HomePalettes.SOS)
        for (p in HomePalettes.all) assertNotEquals("SOS must not be a theme accent", HomePalettes.SOS, p.accent)
    }
}
