package com.vikash.ampli.drive.ui.theme

/**
 * Colours of the home themes, as ARGB longs from the approved Car Hub design boards (night, day
 * and calm palettes of the Main board). Plain Kotlin so the contrast rules can be unit-tested
 * without Android.
 */
data class HomePalette(
    val light: Boolean,
    val background: Long,
    val surface: Long,
    val surfaceHigh: Long,
    val border: Long,
    val text: Long,
    val muted: Long,
    val accent: Long,
    val onAccent: Long,
    /** Accent used as text or an icon on [surface] (the accent itself may be too light for that). */
    val accentText: Long,
    /** The dark "ink" tile of a theme (Bento's speed tile, the Road Sign music block). */
    val ink: Long,
    val onInk: Long,
    val warning: Long,
    val ok: Long,
    val track: Long,
    /** The turn banner and active keys (Soft Indigo at night, Navy by day, teal on Calm). */
    val banner: Long = accent,
    val onBanner: Long = onAccent,
    /** The map's base colour behind tiles that are still loading. */
    val map: Long = background,
    /** The rail's own darker strip and its key fill. */
    val rail: Long = background,
    val railKey: Long = surface,
    val railInk: Long = text,
)

object HomePalettes {
    /** Emergency red: SOS, the crash countdown, a low FASTag balance. The same on every theme. */
    const val SOS = 0xFFD7263DL
    const val ON_SOS = 0xFFFFFFFFL

    /** Red ring of the speed-limit sign. */
    const val LIMIT_RING = 0xFFE1251BL

    /** Midnight Navy with Sage Mint: the default after sunset. Never pure black, never pure white. */
    val Night = HomePalette(
        light = false,
        background = 0xFF0E1222, surface = 0xFF171C30, surfaceHigh = 0xFF222844, border = 0xFF2C3352,
        text = 0xFFE8EAF2, muted = 0xFF9BA3BD,
        accent = 0xFF6FD9A8, onAccent = 0xFF0E1222, accentText = 0xFF6FD9A8,
        ink = 0xFF222844, onInk = 0xFFE8EAF2,
        warning = 0xFFE9A23B, ok = 0xFF5CC98E, track = 0xFF222844,
        banner = 0xFF4C43B8, onBanner = 0xFFFFFFFF, map = 0xFF0A0E1C,
        rail = 0xFF0A0D1A, railKey = 0xFF1A1F36, railInk = 0xFFDDE1EC,
    )

    /** Seal Blue with Navy and Crimson Pink: daytime. */
    val Day = HomePalette(
        light = true,
        background = 0xFFDBF2FC, surface = 0xFFFDFEFF, surfaceHigh = 0xFFC3E3F3, border = 0xFFA8D0E5,
        text = 0xFF072D59, muted = 0xFF3D5B7E,
        accent = 0xFFFD6E88, onAccent = 0xFF072D59, accentText = 0xFFC22F52,
        ink = 0xFF072D59, onInk = 0xFFF2FAFE,
        warning = 0xFF9A4D00, ok = 0xFF1E7A4C, track = 0xFFC3E3F3,
        banner = 0xFF072D59, onBanner = 0xFFFDFEFF, map = 0xFFC9E8F6,
        rail = 0xFFC3E3F3, railKey = 0xFFFDFEFF, railInk = 0xFF072D59,
    )

    /** Calm (Pro): deep teal-grey with warm gold, gentlest at night. */
    val Calm = HomePalette(
        light = false,
        background = 0xFF11171A, surface = 0xFF1A2226, surfaceHigh = 0xFF243035, border = 0xFF2E3B41,
        text = 0xFFE3ECEA, muted = 0xFF9DB0AD,
        accent = 0xFFE8B86B, onAccent = 0xFF11171A, accentText = 0xFFE8B86B,
        ink = 0xFF243035, onInk = 0xFFE3ECEA,
        warning = 0xFFE9A23B, ok = 0xFF5CC98E, track = 0xFF243035,
        banner = 0xFF2F6F6A, onBanner = 0xFFFFFFFF, map = 0xFF0C1113,
        rail = 0xFF0C1113, railKey = 0xFF1A2226, railInk = 0xFFDCE6E4,
    )

    /** Big Glance: three giant tiles in the night palette. */
    val BigGlance = Night

    /** Map First (free, the default) at night; [Day] by day. */
    val MapFirst = Night

    /** Map First's cards over the map: night navy at 90 % (no blur, so it is cheap everywhere). */
    const val MAP_FIRST_CARD = 0xE60E1222L

    /** The same card below Android 12 (no blur there): nearly solid, so text never fights the tiles. */
    const val MAP_FIRST_CARD_SOLID = 0xF20E1222L

    /** Day's cards over the map: near-white at 94 %. */
    const val MAP_FIRST_DAY_CARD = 0xF0FDFEFFL

    /** Calm's cards over the map. */
    const val CALM_CARD = 0xF211171AL

    /** Ampli's speech bubble on the map: the raised night tile with mint for "Ampli". */
    const val MAP_FIRST_BUBBLE = 0xFF222844L
    const val MAP_FIRST_BUBBLE_INK = 0xFFE8EAF2L

    /** Bento Grid: the magazine grid in the day palette, with navy ink tiles. */
    val Bento = Day.copy(border = 0xFF072D59)

    /** Road Sign: thick navy lines and hard shadows on the day palette. */
    val RoadSign = Day.copy(
        background = 0xFFC3E3F3, surfaceHigh = 0xFFDBF2FC, border = 0xFF072D59,
        rail = 0xFF072D59, railKey = 0xFF0E3A6E, railInk = 0xFFF2FAFE,
    )

    /** One entry per palette family (Big Glance and Map First share [Night]). */
    val all: List<HomePalette> = listOf(Night, Day, Calm, Bento, RoadSign)
}
