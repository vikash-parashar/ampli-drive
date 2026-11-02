package com.vikash.ampli.drive.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home themes' palettes: body text at least 4.5:1 on every surface it sits on, white
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
    fun nightDayAndCalmPalettes() {
        assertEquals(5, HomePalettes.all.size)
        // The approved design colours, exactly.
        assertEquals(0xFF0E1222L, HomePalettes.Night.background)
        assertEquals(0xFF6FD9A8L, HomePalettes.Night.accent)
        assertEquals(0xFF4C43B8L, HomePalettes.Night.banner)
        assertEquals(0xFFDBF2FCL, HomePalettes.Day.background)
        assertEquals(0xFFFD6E88L, HomePalettes.Day.accent)
        assertEquals(0xFF072D59L, HomePalettes.Day.text)
        assertEquals(0xFFE8B86BL, HomePalettes.Calm.accent)
    }

    @Test
    fun noPureBlackOrWhiteSurfaces() {
        for (p in HomePalettes.all) {
            for (c in listOf(p.background, p.surface, p.surfaceHigh, p.text)) {
                assertNotEquals(0xFF000000L, c)
                assertNotEquals(0xFFFFFFFFL, c)
            }
        }
    }

    @Test
    fun textReadsOnEverySurface() {
        for (p in HomePalettes.all) {
            for ((name, bg) in listOf("background" to p.background, "surface" to p.surface)) {
                check("text on $name ($p)", p.text, bg)
                check("muted on $name ($p)", p.muted, bg)
            }
            check("text on raised ($p)", p.text, p.surfaceHigh)
            check("muted on raised ($p)", p.muted, p.surfaceHigh)
            check("accent text on surface ($p)", p.accentText, p.surface)
            check("label on the accent key ($p)", p.onAccent, p.accent)
            check("ink tile text ($p)", p.onInk, p.ink)
            check("turn banner text ($p)", p.onBanner, p.banner)
            check("rail key ink ($p)", p.railInk, p.railKey)
        }
    }

    @Test
    fun mapFirstCardsReadOverTheMap() {
        val p = HomePalettes.MapFirst
        // Worst case under the tinted card: the lightest map colour (near-white roads).
        for (card in listOf(HomePalettes.MAP_FIRST_CARD, HomePalettes.MAP_FIRST_CARD_SOLID)) {
            val bg = Contrast.over(card, 0xFFFFFFFF)
            check("text on Map First card", p.text, bg)
            check("muted on Map First card", p.muted, bg)
        }
        // Day cards: worst case over the darkest day map colour (navy route line).
        val day = HomePalettes.Day
        val dayBg = Contrast.over(HomePalettes.MAP_FIRST_DAY_CARD, 0xFF072D59)
        check("text on Day card", day.text, dayBg)
        check("muted on Day card", day.muted, dayBg)
        val calm = HomePalettes.Calm
        val calmBg = Contrast.over(HomePalettes.CALM_CARD, 0xFFFFFFFF)
        check("text on Calm card", calm.text, calmBg)
        check("muted on Calm card", calm.muted, calmBg)
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
