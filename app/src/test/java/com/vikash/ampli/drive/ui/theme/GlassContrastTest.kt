package com.vikash.ampli.drive.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassContrastTest {

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
    fun sixPalettesMixLightAndDark() {
        assertEquals(6, GlassPalettes.all.size)
        assertTrue(GlassPalettes.all.any { it.light })
        assertTrue(GlassPalettes.all.any { !it.light })
    }

    /** Primary text and the speed number use [GlassPalette.text]: WCAG AA on every glass look. */
    @Test
    fun primaryTextIsAaOnBlurredAndReducedGlass() {
        GlassPalettes.all.forEachIndexed { i, p ->
            val backdrop = p.backdropSamples()
            for ((what, panel) in listOf("panel" to p.panel, "reduced" to p.reducedPanel)) {
                val worst = Contrast.worstOnGlass(p.text, panel, backdrop)
                assertTrue("palette $i $what text $worst", worst >= Contrast.AA_TEXT)
                val muted = Contrast.worstOnGlass(p.muted, panel, backdrop)
                assertTrue("palette $i $what muted $muted", muted >= Contrast.AA_TEXT)
                val accent = Contrast.worstOnGlass(p.accent, panel, backdrop)
                assertTrue("palette $i $what accent $accent", accent >= Contrast.AA_LARGE)
            }
        }
    }

    /** The turn card sits on the map (no blur). Its tint alone must carry the text, even on black or white tiles. */
    @Test
    fun turnCardOverMapIsAa() {
        GlassPalettes.all.forEachIndexed { i, p ->
            val worst = Contrast.worstOnGlass(p.text, p.overMap, listOf(0xFF000000, 0xFFFFFFFF, 0xFF808080))
            assertTrue("palette $i over map $worst", worst >= Contrast.AA_TEXT)
        }
    }

    @Test
    fun accentLabelsReadOnAccentButtons() {
        GlassPalettes.all.forEachIndexed { i, p ->
            val r = Contrast.ratio(p.onAccent, p.accent)
            assertTrue("palette $i onAccent $r", r >= Contrast.AA_TEXT)
        }
    }
}
