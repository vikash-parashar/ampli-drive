package com.vikash.ampli.drive.ui.theme

/**
 * Colours of the four home themes, as ARGB longs taken from the approved Car Hub style studies
 * (styles 3, 4, 6 and 8). Plain Kotlin so the contrast rules can be unit-tested without Android.
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
    /** Accent used as text or an icon on [surface] (the accent itself may be too dark for that). */
    val accentText: Long,
    /** The dark "ink" tile of a light theme (Bento's speed tile, the Road Sign music block). */
    val ink: Long,
    val onInk: Long,
    val warning: Long,
    val ok: Long,
    val track: Long,
)

object HomePalettes {
    /** Emergency red: SOS, the crash countdown, a low FASTag balance. The same on every theme. */
    const val SOS = 0xFFD7263DL
    const val ON_SOS = 0xFFFFFFFFL

    /** Red ring of the speed-limit sign. */
    const val LIMIT_RING = 0xFFE1251BL

    /** Style 3: three giant tiles on true black. */
    val BigGlance = HomePalette(
        light = false,
        background = 0xFF000000, surface = 0xFF141418, surfaceHigh = 0xFF1C1C21, border = 0xFF2B2934,
        text = 0xFFE9E7F2, muted = 0xFF9A97A8,
        accent = 0xFFB9A6FF, onAccent = 0xFF000000, accentText = 0xFFB9A6FF,
        ink = 0xFF141418, onInk = 0xFFE9E7F2,
        warning = 0xFFFBBF24, ok = 0xFF4ADE80, track = 0xFF2B2934,
    )

    /** Style 4: the map fills the screen; frosted slate cards float in the corners. */
    val MapFirst = HomePalette(
        light = false,
        background = 0xFF17222A, surface = 0xFF223039, surfaceHigh = 0xFF2C3C46, border = 0xFF3A4C57,
        text = 0xFFF5F2EB, muted = 0xFFB7C0C6,
        accent = 0xFF6B3BFF, onAccent = 0xFFFFFFFF, accentText = 0xFFB9A2FF,
        ink = 0xFF17222A, onInk = 0xFFF5F2EB,
        warning = 0xFFFBBF24, ok = 0xFF4ADE80, track = 0xFF3A4C57,
    )

    /** Map First's cards over the map: slate at 80 % (no blur, so it is cheap everywhere). */
    const val MAP_FIRST_CARD = 0xCC17222AL

    /** The same card on Android 8.x head units: nearly solid, so text never fights the tiles. */
    const val MAP_FIRST_CARD_SOLID = 0xF217222AL

    /** Ampli's speech bubble on the map. */
    const val MAP_FIRST_BUBBLE = 0xFFFFFDF8L
    const val MAP_FIRST_BUBBLE_INK = 0xFF1D2A33L

    /** Style 6: a magazine grid of tiles in heavy grotesk. */
    val Bento = HomePalette(
        light = true,
        background = 0xFFEEECE6, surface = 0xFFFFFFFF, surfaceHigh = 0xFFDCD9D0, border = 0xFF141414,
        text = 0xFF141414, muted = 0xFF5C5A55,
        accent = 0xFF1F4BFF, onAccent = 0xFFFFFFFF, accentText = 0xFF1F4BFF,
        ink = 0xFF141414, onInk = 0xFFEEECE6,
        warning = 0xFFB45309, ok = 0xFF15803D, track = 0xFFDCD9D0,
    )

    /** Style 8: Indian road-sign language: hazard yellow, 6 px black borders, hard shadows. */
    val RoadSign = HomePalette(
        light = true,
        background = 0xFFFFD100, surface = 0xFFFFFFFF, surfaceHigh = 0xFFFFF3B0, border = 0xFF111111,
        text = 0xFF111111, muted = 0xFF3D3500,
        accent = 0xFF0057B8, onAccent = 0xFFFFFFFF, accentText = 0xFF0057B8,
        ink = 0xFF111111, onInk = 0xFFFFD100,
        warning = 0xFF9A3412, ok = 0xFF15803D, track = 0xFFE5E5E5,
    )

    val all: List<HomePalette> = listOf(BigGlance, MapFirst, Bento, RoadSign)
}
