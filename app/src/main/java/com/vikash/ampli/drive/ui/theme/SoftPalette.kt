package com.vikash.ampli.drive.ui.theme

/**
 * Colours of the Ampli Soft themes (ARGB), from the approved neumorphic sample. Plain Kotlin so
 * SoftContrastTest can check them. Where a sample colour was used as text and fell below 4.5:1,
 * a darker shade of it is used for text ([muted], [accentText], [ok], [warning]); the bright
 * [accent] stays for icons, strokes, the focus ring and the live/active state (3:1 or more).
 */
data class SoftPalette(
    val light: Boolean,
    val base: Long,
    val highlight: Long,
    val shadow: Long,
    val ink: Long,
    val muted: Long,
    val accent: Long,
    val accentText: Long,
    val onAccent: Long,
    /** Hairline edge on tappable items, at least 3:1 against [base]. */
    val edge: Long,
    val ok: Long,
    val warning: Long,
    val track: Long,
    /**
     * Emergency red (SOS, crash, Help). The usual #D7263D on Day; a hair brighter on Night so red
     * icons on the dark grey still reach 3:1 while white on red stays above 4.5:1.
     */
    val danger: Long = 0xFFD7263D,
)

object SoftPalettes {
    /** Sample: base #e3e9ee, highlight #fff, shadow #b9c4cf, ink #22313c, muted #6a7c89, accent #0a8fa3. */
    val Day = SoftPalette(
        light = true,
        base = 0xFFE3E9EE, highlight = 0xFFFFFFFF, shadow = 0xFFB9C4CF,
        ink = 0xFF22313C, muted = 0xFF566876,
        accent = 0xFF0A8FA3, accentText = 0xFF0A6F80, onAccent = 0xFFFFFFFF,
        edge = 0xFF738591, ok = 0xFF15703F, warning = 0xFF9A4708, track = 0xFFCFD8E0,
    )

    /** Sample: base #232b33, highlight #2d3741, shadow #161c22, ink #e3eef3, muted #8ea2ae, accent #4fd8ea. */
    val Night = SoftPalette(
        light = false,
        base = 0xFF232B33, highlight = 0xFF2D3741, shadow = 0xFF161C22,
        ink = 0xFFE3EEF3, muted = 0xFF8EA2AE,
        accent = 0xFF4FD8EA, accentText = 0xFF4FD8EA, onAccent = 0xFF0B2A30,
        edge = 0xFF6F8291, ok = 0xFF4ADE80, warning = 0xFFFBBF24, track = 0xFF1B2229,
        danger = 0xFFDC2C43,
    )

    val all = listOf(Day, Night)
}
