package com.vikash.ampli.drive.ui.theme

/**
 * Colours of Crimson Drive (ARGB), the same red, black and green as Ampli MP3's Car mode
 * (music-player ui/CarModeScreen.kt). Plain Kotlin so CrimsonContrastTest can check them.
 *
 * Crimson #A31C1B is only ever a fill under white (play, "Go", the turn tile); as a line on the
 * charcoal it would be 2.3:1, so lines, the gauge, progress and the route use [CRIMSON_HI].
 * The deep green #1F5C3F is the Hey Ampli key's fill, with the brighter [GREEN_EDGE] around it.
 */
object CrimsonPalette {
    /** Forest background, with a soft green glow top-left ([FOREST_GLOW]) and darker edges. */
    const val FOREST = 0xFF06110CL
    const val FOREST_GLOW = 0xFF0B1D14L
    const val FOREST_EDGE = 0xFF030806L

    /** Charcoal panes and keys, with their soft light and dark shadows. */
    const val CHARCOAL = 0xFF171717L
    const val HIGHLIGHT = 0xFF232323L
    const val SHADOW = 0xFF050505L

    const val INK = 0xFFF2F5F3L
    const val MUTED = 0xFF9DB3A6L

    /** Pale green: active and selected text, the live state. */
    const val PALE = 0xFFC9DDD0L

    /** Crimson fill, its radial highlight and its dark edge. */
    const val CRIMSON = 0xFFA31C1BL
    const val CRIMSON_HI = 0xFFD42A27L
    const val CRIMSON_DEEP = 0xFF6E1010L

    /** Hey Ampli key and live states. */
    const val GREEN = 0xFF1F5C3FL
    const val GREEN_EDGE = 0xFF2E7D57L

    /** Hairline edge on tappable keys (the sample's #2a2a2a is only 1.2:1, too faint). */
    const val EDGE = 0xFF627168L

    const val OK = 0xFF5DBB8CL
    const val WARNING = 0xFFFBBF24L
    const val TRACK = 0xFF0A0A0AL
    const val ON_FILL = 0xFFFFFFFFL

    /** Bottom of the car picture, under the greeting (rgba(3,8,6,.92) in the sample). */
    const val HERO_FADE = 0xEB030806L
}
