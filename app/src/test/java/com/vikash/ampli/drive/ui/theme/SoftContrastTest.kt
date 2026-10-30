package com.vikash.ampli.drive.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ampli Soft Day / Night: text at least 4.5:1 on the soft base; icons, the tappable-item edge
 * and the focus ring at least 3:1. SOS keeps white on emergency red.
 */
class SoftContrastTest {

    private fun check(what: String, fg: Long, bg: Long, min: Double) {
        val r = Contrast.ratio(fg, bg)
        assertTrue("$what: %.2f < $min".format(r), r >= min)
    }

    @Test
    fun softPalettesMeetContrast() {
        for (p in SoftPalettes.all) {
            val n = if (p.light) "Day" else "Night"
            check("$n text", p.ink, p.base, Contrast.AA_TEXT)
            check("$n muted text", p.muted, p.base, Contrast.AA_TEXT)
            check("$n accent text", p.accentText, p.base, Contrast.AA_TEXT)
            check("$n ok text", p.ok, p.base, Contrast.AA_TEXT)
            check("$n warning text", p.warning, p.base, Contrast.AA_TEXT)
            check("$n accent icon / focus ring", p.accent, p.base, Contrast.AA_LARGE)
            check("$n tappable edge", p.edge, p.base, Contrast.AA_LARGE)
            check("$n icon on accent", p.onAccent, p.accent, Contrast.AA_LARGE)
        }
    }

    /** SOS and crash alerts: white on red at 4.5:1 or more, and red icons at 3:1 on the soft base. */
    @Test
    fun sosStaysReadable() {
        for (p in SoftPalettes.all) {
            check("SOS text on red", 0xFFFFFFFF, p.danger, Contrast.AA_TEXT)
            check("red icon on base", p.danger, p.base, Contrast.AA_LARGE)
        }
    }
}
