package com.vikash.ampli.drive.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Crimson Drive: text at least 4.5:1 on the charcoal panes and on the forest background; key
 * edges, icons, the gauge and the focus ring at least 3:1; white on the crimson and green fills.
 * SOS keeps the usual emergency red with white text, clearly apart from the crimson.
 * Runs under Robolectric because [DriveThemes] needs Android to initialise.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class CrimsonContrastTest {

    private fun check(what: String, fg: Long, bg: Long, min: Double) {
        val r = Contrast.ratio(fg, bg)
        assertTrue("$what: %.2f < $min".format(r), r >= min)
    }

    @Test
    fun textIsReadable() {
        val p = CrimsonPalette
        for ((bgName, bg) in listOf("charcoal" to p.CHARCOAL, "forest" to p.FOREST, "forest glow" to p.FOREST_GLOW)) {
            check("ink on $bgName", p.INK, bg, Contrast.AA_TEXT)
            check("muted on $bgName", p.MUTED, bg, Contrast.AA_TEXT)
            check("pale green (active) on $bgName", p.PALE, bg, Contrast.AA_TEXT)
            check("ok on $bgName", p.OK, bg, Contrast.AA_TEXT)
            check("warning on $bgName", p.WARNING, bg, Contrast.AA_TEXT)
        }
        // Greeting on the car picture's faded bottom (92% of #030806 over a black-to-green picture).
        val fadeOverGreen = Contrast.over(p.HERO_FADE, 0xFF1F5C3FL)
        check("ink on the hero fade", p.INK, fadeOverGreen, Contrast.AA_TEXT)
        check("pale on the hero fade", p.PALE, fadeOverGreen, Contrast.AA_TEXT)
    }

    @Test
    fun filledKeysAreReadable() {
        val p = CrimsonPalette
        // The crimson gradient runs from CRIMSON_HI through CRIMSON to CRIMSON_DEEP: white on all three.
        for (fill in listOf(p.CRIMSON_HI, p.CRIMSON, p.CRIMSON_DEEP)) check("white on crimson", p.ON_FILL, fill, Contrast.AA_TEXT)
        check("white on green (Hey Ampli)", p.ON_FILL, p.GREEN, Contrast.AA_TEXT)
    }

    @Test
    fun edgesAndGraphicsReachThreeToOne() {
        val p = CrimsonPalette
        check("tappable key edge", p.EDGE, p.CHARCOAL, Contrast.AA_LARGE)
        check("crimson key edge / gauge / route / focus ring", p.CRIMSON_HI, p.CHARCOAL, Contrast.AA_LARGE)
        check("crimson key edge on forest", p.CRIMSON_HI, p.FOREST, Contrast.AA_LARGE)
        check("green key edge", p.GREEN_EDGE, p.CHARCOAL, Contrast.AA_LARGE)
        check("green key edge on forest", p.GREEN_EDGE, p.FOREST, Contrast.AA_LARGE)
    }

    /** The theme uses these palette values, so the checks above cover what is on screen. */
    @Test
    fun themeUsesThePalette() {
        val t = DriveThemes.CrimsonDrive
        val keys = t.keys!!
        assertTrue(t.text.value == androidx.compose.ui.graphics.Color(CrimsonPalette.INK).value)
        assertTrue(t.muted == androidx.compose.ui.graphics.Color(CrimsonPalette.MUTED))
        assertTrue(t.accent == androidx.compose.ui.graphics.Color(CrimsonPalette.CRIMSON_HI))
        assertTrue(t.accentSoft == androidx.compose.ui.graphics.Color(CrimsonPalette.PALE))
        assertTrue(t.surface == androidx.compose.ui.graphics.Color(CrimsonPalette.CHARCOAL))
        assertTrue(t.background == androidx.compose.ui.graphics.Color(CrimsonPalette.FOREST))
        assertTrue(t.softEdge == androidx.compose.ui.graphics.Color(CrimsonPalette.EDGE))
        assertTrue(keys.primary == androidx.compose.ui.graphics.Color(CrimsonPalette.CRIMSON))
        assertTrue(keys.primaryEdge == androidx.compose.ui.graphics.Color(CrimsonPalette.CRIMSON_HI))
        assertTrue(keys.voice == androidx.compose.ui.graphics.Color(CrimsonPalette.GREEN))
        assertTrue(keys.voiceEdge == androidx.compose.ui.graphics.Color(CrimsonPalette.GREEN_EDGE))
        assertTrue(t.isSoft())
        assertTrue(!t.isPro)
    }

    private fun DriveTheme.isSoft() = cardStyle == CardStyle.Neumorphic

    /**
     * SOS, crash alerts and the Help key keep the emergency red every other theme uses, with white
     * text, and it stays a different colour from the crimson accent: ΔE of 15 or more against the
     * crimson fill and its dark edge, 10 or more against the small highlight at the top of the
     * gradient (and the thin gauge / route lines), and visibly brighter than the crimson fill.
     */
    @Test
    fun sosStaysBrightRedAndApartFromCrimson() {
        val t = DriveThemes.CrimsonDrive
        val emergency = DriveThemes.AmpliRose.danger
        assertTrue("Crimson Drive must use the shared emergency red", t.danger == emergency)
        assertTrue(t.onDanger == androidx.compose.ui.graphics.Color.White)
        val danger = 0xFFD7263DL
        assertTrue(emergency == androidx.compose.ui.graphics.Color(danger))
        check("SOS text on red", 0xFFFFFFFFL, danger, Contrast.AA_TEXT)
        check("red icon on charcoal", danger, CrimsonPalette.CHARCOAL, Contrast.AA_LARGE)
        for ((name, crimson, min) in listOf(
            Triple("crimson fill", CrimsonPalette.CRIMSON, 15.0),
            Triple("crimson dark edge", CrimsonPalette.CRIMSON_DEEP, 15.0),
            Triple("crimson highlight", CrimsonPalette.CRIMSON_HI, 10.0),
        )) {
            val d = Contrast.deltaE(danger, crimson)
            assertTrue("SOS red vs $name: ΔE %.1f < $min".format(d), d >= min)
        }
        // The SOS red is brighter than the crimson fill, so a crimson key never reads as SOS.
        assertTrue(Contrast.luminance(danger) > Contrast.luminance(CrimsonPalette.CRIMSON) * 1.5)
    }
}
