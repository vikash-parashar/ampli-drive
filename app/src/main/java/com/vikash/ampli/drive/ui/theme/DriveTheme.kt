package com.vikash.ampli.drive.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.prefs.ThemeDefaults
import com.vikash.ampli.drive.prefs.ThemeChoice

/** How a card (pane, turn card, dock button) is painted. */
enum class CardStyle { Glass, Solid, Outline, Neumorphic, NeonGlow, Skewed, Pop }

/** Full-screen pattern drawn behind (or, for scanlines, over) the home screen. */
enum class Decoration {
    None, Vignette, Carbon, Synthwave, Jaali, Scanlines, Stripes, Holi,

    /** Colourful gradient with soft blobs, for glass panels to blur ([DriveTheme.glass]). */
    Glass,
}

/** Order and size of the three home panes. */
enum class HomeLayout { MapSpeedMusic, SpeedMapMusic, MusicSpeedMap, MapStacked, MapTopRow, GiantSpeed }

/** Picker groups. */
enum class ThemeFamily { Classic, Glass, Saffron, Soft }

/** How the speed pane draws the speed. */
enum class SpeedStyle { Arc, Number, Tach, Tape, Giant }

/** Bundled system fonts only (no font downloads): the feel comes from weight and spacing. */
enum class FontChoice(val family: FontFamily) {
    Sans(FontFamily.SansSerif),
    Condensed(DriveFonts.Condensed),
    Mono(FontFamily.Monospace),
    Serif(FontFamily.Serif),
}

/** OpenFreeMap vector styles (free, no key). */
enum class MapLook(val url: String) {
    Liberty("https://tiles.openfreemap.org/styles/liberty"),
    Bright("https://tiles.openfreemap.org/styles/bright"),
    Positron("https://tiles.openfreemap.org/styles/positron"),
    Dark("https://tiles.openfreemap.org/styles/dark"),
    Fiord("https://tiles.openfreemap.org/styles/fiord"),
}

/**
 * One home-screen look. Colours come from the matching `.dN` block of
 * research/ampli-drive-10-designs.html. Sizes such as [radius] are in layout units
 * (1 unit = 1% of screen height), like everything else in the UI.
 */
@Immutable
data class DriveTheme(
    val id: String,
    val number: Int,
    val name: String,
    val isLight: Boolean,

    // Surfaces and text
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val borderWidth: Dp,
    val text: Color,
    val muted: Color,

    // Accent
    val accent: Color,
    val onAccent: Color,
    /** Softer accent for icons, labels and selected chips. */
    val accentSoft: Color,
    /** Second neon colour (Synthwave cyan); equals [accent] elsewhere. */
    val accent2: Color,
    val warning: Color,
    val ok: Color,

    // Gauge and progress
    val track: Color,
    val gauge: List<Color>,
    /** Glow behind the speed number and arc (neon themes), or null. */
    val glow: Color?,

    // Turn card
    val turnBackground: Brush,
    val turnText: Color,
    val turnMuted: Color,
    val turnTile: Brush,
    val turnTileText: Color,
    val turnBorder: Color,

    // Speed pane override (Desi Saffron paints it saffron)
    val speedSurface: Color?,
    val speedText: Color?,

    // Dock
    val dockActive: Color,
    val onDockActive: Color,
    val limitRing: Color,

    // Shape, type, decoration
    val radius: Float,
    val cardStyle: CardStyle,
    val font: FontChoice,
    val numberWeight: FontWeight,
    val italicNumbers: Boolean,
    /** Extra letter spacing for small labels, in em. */
    val labelSpacing: Float,
    val upperLabels: Boolean,
    val decoration: Decoration,
    val layout: HomeLayout,
    val speedStyle: SpeedStyle,

    // Map
    val map: MapLook,
    /** Colour laid over the map tiles, or Transparent. */
    val mapTint: Color,
    val mapPuck: Color,

    // Neumorphic shadows
    val shadowDark: Color = Color.Transparent,
    val shadowLight: Color = Color.Transparent,
    /**
     * Soft themes: hairline edge on tappable items, at least 3:1 against [surface] (the soft
     * shadows alone are about 1.5:1). Null uses [muted].
     */
    val softEdge: Color? = null,

    // Families (picker grouping), short code shown on the card ("01", "S2", "N4"…)
    val family: ThemeFamily = ThemeFamily.Classic,
    val code: String = "%02d".format(number),
    /** Script greeting shown next to the clock (Saffron family), e.g. "शुभ यात्रा". Not translated. */
    val greeting: String? = null,
    /** Stroke/fill colour of the background pattern (jaali, stripes). */
    val patternColor: Color = Color(0x29FF9933),

    // Music card override: Saffron themes put it on cream "paper" with dark ink.
    val musicSurface: Color? = null,
    val musicText: Color = text,
    val musicMuted: Color = muted,
    val musicButton: Color = surfaceHigh,
    val musicTrack: Color = track,
    val musicProgress: Color = accent2,

    // Emergency red (Help button, SOS, low FASTag balance). The same on every theme on purpose:
    // drivers and responders should recognise it at a glance.
    val danger: Color = Color(0xFFD7263D),
    val onDanger: Color = Color.White,

    /** Frosted-glass colours for the Glass family, or null. Panels then blur the backdrop. */
    val glass: GlassPalette? = null,
    /** Translated name, where there is one; otherwise [name] is shown. */
    @StringRes val nameRes: Int? = null,
) {
    /** Pro themes need the one-time purchase. The free set lives in [ThemeAccess.FREE_IDS]. */
    val isPro: Boolean get() = !ThemeAccess.isFree(id)

    /**
     * The same glass theme without blur ("Reduce glass effects", or no blur on this device):
     * panels get a stronger tint so text keeps its contrast. Other themes are returned as they are.
     */
    fun withoutBlur(): DriveTheme {
        val g = glass ?: return this
        return copy(surface = Color(g.reducedPanel))
    }

    /** The theme as seen inside the music card (paper colours where the theme has them). */
    fun forMusicCard(): DriveTheme = if (musicSurface == null) {
        this
    } else {
        copy(
            surface = musicSurface, text = musicText, muted = musicMuted, surfaceHigh = musicButton,
            track = musicTrack, accent2 = musicProgress, accentSoft = musicProgress, border = Color.Transparent,
        )
    }
}

private fun c(argb: Long) = Color(argb)
private fun solid(color: Color): Brush = Brush.linearGradient(listOf(color, color))

object DriveThemes {

    val MidnightGlass = DriveTheme(
        id = "midnight_glass", number = 1, name = "Midnight Glass", isLight = false,
        background = c(0xFF070B14), surface = c(0x940A1222), surfaceHigh = c(0x14FFFFFF),
        border = c(0x29A0DCFF), borderWidth = 1.dp, text = c(0xFFE6F1FF), muted = c(0xFF9CB3D4),
        accent = c(0xFF22D3EE), onAccent = c(0xFF04131A), accentSoft = c(0xFFBFE9F5), accent2 = c(0xFF22D3EE),
        warning = c(0xFFFBBF24), ok = c(0xFF34D399),
        track = c(0x1AFFFFFF), gauge = listOf(c(0xFF0891B2), c(0xFF22D3EE)), glow = c(0xFF22D3EE),
        turnBackground = solid(c(0x940A1222)), turnText = c(0xFFE6F1FF), turnMuted = c(0xFFBFE9F5),
        turnTile = Brush.linearGradient(listOf(c(0xFF22D3EE), c(0xFF0891B2))), turnTileText = c(0xFF04131A),
        turnBorder = c(0x29A0DCFF),
        speedSurface = null, speedText = null,
        dockActive = c(0x2422D3EE), onDockActive = c(0xFF22D3EE), limitRing = c(0xFFE21B1B),
        radius = 4f, cardStyle = CardStyle.Glass, font = FontChoice.Sans, numberWeight = FontWeight.SemiBold,
        italicNumbers = false, labelSpacing = 0f, upperLabels = false,
        decoration = Decoration.Vignette, layout = HomeLayout.MapSpeedMusic, speedStyle = SpeedStyle.Arc,
        map = MapLook.Fiord, mapTint = c(0x1A0B1322), mapPuck = c(0xFF22D3EE),
    )

    val AmpliRose = DriveTheme(
        id = ThemeDefaults.ROSE, number = 2, name = "Ampli Rose", isLight = false,
        background = c(0xFF0F0D14), surface = c(0xFF1C1820), surfaceHigh = c(0xFF2A2530),
        border = c(0xFF2A2530), borderWidth = 1.dp, text = c(0xFFF5F3F7), muted = c(0xFFA39FAB),
        accent = c(0xFFE11D48), onAccent = Color.White, accentSoft = c(0xFFFDA4AF), accent2 = c(0xFFFB7185),
        warning = c(0xFFFBBF24), ok = c(0xFF22C55E),
        track = c(0xFF2E2833), gauge = listOf(c(0xFFE11D48), c(0xFFFDA4AF)), glow = null,
        turnBackground = Brush.linearGradient(listOf(c(0xFFE11D48), c(0xFF7C1D4F))), turnText = Color.White,
        turnMuted = c(0xFFFFE4E8), turnTile = solid(c(0x2EFFFFFF)), turnTileText = Color.White,
        turnBorder = Color.Transparent,
        speedSurface = null, speedText = null,
        dockActive = c(0xFFE11D48), onDockActive = Color.White, limitRing = c(0xFFE21B1B),
        radius = 4.3f, cardStyle = CardStyle.Solid, font = FontChoice.Sans, numberWeight = FontWeight.ExtraBold,
        italicNumbers = false, labelSpacing = 0.06f, upperLabels = false,
        decoration = Decoration.None, layout = HomeLayout.SpeedMapMusic, speedStyle = SpeedStyle.Arc,
        map = MapLook.Dark, mapTint = c(0x14E11D48), mapPuck = c(0xFFFDA4AF),
    )

    val DaylightClean = DriveTheme(
        id = ThemeDefaults.DAYLIGHT, number = 3, name = "Daylight Clean", isLight = true,
        background = c(0xFFEEF1F5), surface = Color.White, surfaceHigh = c(0xFFEEF1F5),
        border = c(0xFFCDD3DE), borderWidth = 1.5.dp, text = c(0xFF0B1220), muted = c(0xFF4A5568),
        accent = c(0xFF1D4ED8), onAccent = Color.White, accentSoft = c(0xFF1D4ED8), accent2 = c(0xFF1D4ED8),
        warning = c(0xFFD97706), ok = c(0xFF15803D),
        track = c(0xFFE2E7EF), gauge = listOf(c(0xFF1D4ED8), c(0xFF1D4ED8)), glow = null,
        turnBackground = solid(c(0xFF0B1220)), turnText = Color.White, turnMuted = c(0xFFC9D4EA),
        turnTile = solid(c(0xFF1D4ED8)), turnTileText = Color.White, turnBorder = Color.Transparent,
        speedSurface = null, speedText = null,
        dockActive = c(0xFF1D4ED8), onDockActive = Color.White, limitRing = c(0xFFE21B1B),
        radius = 3.7f, cardStyle = CardStyle.Solid, font = FontChoice.Sans, numberWeight = FontWeight.ExtraBold,
        italicNumbers = false, labelSpacing = 0.1f, upperLabels = true,
        decoration = Decoration.None, layout = HomeLayout.MapStacked, speedStyle = SpeedStyle.Number,
        map = MapLook.Liberty, mapTint = Color.Transparent, mapPuck = c(0xFF1D4ED8),
    )

    val SportRed = DriveTheme(
        id = "sport_red", number = 4, name = "Sport Red", isLight = false,
        background = c(0xFF101012), surface = c(0xEB141417), surfaceHigh = c(0xFF1F1F23),
        border = c(0xFF2C2C31), borderWidth = 1.dp, text = c(0xFFF4F4F5), muted = c(0xFFA1A1AA),
        accent = c(0xFFE10600), onAccent = Color.White, accentSoft = c(0xFFFF6A63), accent2 = c(0xFFFF1E1E),
        warning = c(0xFFFFD400), ok = c(0xFF22C55E),
        track = c(0xFF2C2C31), gauge = listOf(c(0xFFE10600), c(0xFFFF1E1E)), glow = c(0xFFFF1E1E),
        turnBackground = solid(c(0xFFE10600)), turnText = Color.White, turnMuted = c(0xE6FFFFFF),
        turnTile = solid(Color.Transparent), turnTileText = Color.White, turnBorder = Color.Transparent,
        speedSurface = null, speedText = null,
        dockActive = c(0xFFE10600), onDockActive = Color.White, limitRing = c(0xFFE21B1B),
        radius = 0f, cardStyle = CardStyle.Skewed, font = FontChoice.Condensed, numberWeight = FontWeight.ExtraBold,
        italicNumbers = true, labelSpacing = 0.2f, upperLabels = true,
        decoration = Decoration.Carbon, layout = HomeLayout.MapSpeedMusic, speedStyle = SpeedStyle.Tach,
        map = MapLook.Dark, mapTint = c(0x10FF1E1E), mapPuck = Color.White,
    )

    val Synthwave = DriveTheme(
        id = "retro_synthwave", number = 5, name = "Retro Synthwave", isLight = false,
        background = c(0xFF12002A), surface = c(0xCC12002A), surfaceHigh = c(0x1F00F0FF),
        border = c(0xFFFF2E97), borderWidth = 2.dp, text = c(0xFFF7E8FF), muted = c(0xFFE0B3FF),
        accent = c(0xFFFF2E97), onAccent = c(0xFF12002A), accentSoft = c(0xFFFFB3DC), accent2 = c(0xFF00F0FF),
        warning = c(0xFFFFB800), ok = c(0xFF00F0FF),
        track = c(0xFF3D1C8F), gauge = listOf(c(0xFFFF2E97), c(0xFFFFD319)), glow = c(0xFFFF2E97),
        turnBackground = solid(c(0xC712002A)), turnText = Color.White, turnMuted = c(0xFFFFB3DC),
        turnTile = solid(Color.Transparent), turnTileText = c(0xFF00F0FF), turnBorder = c(0xFF00F0FF),
        speedSurface = null, speedText = c(0xFFFFE3F3),
        dockActive = c(0x2600F0FF), onDockActive = c(0xFF00F0FF), limitRing = c(0xFFFF3B3B),
        radius = 2.4f, cardStyle = CardStyle.NeonGlow, font = FontChoice.Sans, numberWeight = FontWeight.Black,
        italicNumbers = false, labelSpacing = 0.12f, upperLabels = true,
        decoration = Decoration.Synthwave, layout = HomeLayout.MapSpeedMusic, speedStyle = SpeedStyle.Number,
        map = MapLook.Dark, mapTint = c(0x33B8236F), mapPuck = c(0xFFFFD319),
    )

    val MinimalMono = DriveTheme(
        id = "minimal_mono", number = 6, name = "Minimal Mono", isLight = false,
        background = c(0xFF0A0A0A), surface = c(0xFF0A0A0A), surfaceHigh = c(0xFF1A1A1A),
        border = c(0xFF2A2A2A), borderWidth = 1.dp, text = c(0xFFFAFAFA), muted = c(0xFFA3A3A3),
        accent = c(0xFFFAFAFA), onAccent = c(0xFF0A0A0A), accentSoft = c(0xFFFAFAFA), accent2 = c(0xFFFFE600),
        warning = c(0xFFFFE600), ok = c(0xFFFAFAFA),
        track = c(0xFF333333), gauge = listOf(c(0xFFFAFAFA), c(0xFFFAFAFA)), glow = null,
        turnBackground = solid(c(0xFF0A0A0A)), turnText = c(0xFFFAFAFA), turnMuted = c(0xFFA3A3A3),
        turnTile = solid(Color.Transparent), turnTileText = c(0xFFFAFAFA), turnBorder = c(0xFF2A2A2A),
        speedSurface = null, speedText = null,
        dockActive = c(0xFF1A1A1A), onDockActive = c(0xFFFAFAFA), limitRing = c(0xFFFFE600),
        radius = 0f, cardStyle = CardStyle.Outline, font = FontChoice.Sans, numberWeight = FontWeight.Black,
        italicNumbers = false, labelSpacing = 0.2f, upperLabels = true,
        decoration = Decoration.None, layout = HomeLayout.GiantSpeed, speedStyle = SpeedStyle.Giant,
        map = MapLook.Dark, mapTint = c(0x1A0A0A0A), mapPuck = c(0xFFFFE600),
    )

    // 07 and 08 became the first members of their families (ids kept so saved choices survive).
    val DesiSaffron = saffron(
        id = "desi_saffron", code = "S1", name = "Saffron Night", light = false, greeting = "शुभ यात्रा",
        bg = 0xFF0B3B40, fg = 0xFFFFF4E0, fg2 = 0xFFFFC48A, acc = 0xFFFF9933, onacc = 0xFF2A1300, acc2 = 0xFFFFB766,
        deep = 0xFF0F5257, ondeep = 0xFFFFF4E0, deep2 = 0xFFBFE3E0,
        paper = 0xFFFFF4E0, ink = 0xFF3A1A10, ink2 = 0xFF7A5A44, btn = 0xFFF3E2C4, trackC = 0xFFF0DDC0, progc = 0xFF0F5257,
        appbg = 0x14FFF4E0, appline = 0x24FFF4E0, onbg = 0xFFFFF4E0, onfg = 0xFF0B3B40,
        layout = HomeLayout.MapSpeedMusic, map = MapLook.Liberty, mapTint = 0x14FF9933,
        decoration = Decoration.Jaali, pattern = 0x21FF9933,
    )

    val Neumorphic = soft(
        id = "neumorphic_soft", code = "N1", name = "Soft Cloud", light = true,
        nb = 0xFFE3E7EE, lt = 0xFFFFFFFF, dk = 0xFFBEC4D1, fg = 0xFF353D52, mut = 0xFF7D869A, mut2 = 0xFF5F687D,
        acc = 0xFF4C6EF5, onacc = 0xFFFFFFFF, warn = 0xFFF08C3A, trk = 0xFFD3D8E1,
        layout = HomeLayout.SpeedMapMusic, map = MapLook.Positron,
    )

    val HudGreen = DriveTheme(
        id = "hud_green", number = 9, name = "HUD Green", isLight = false,
        background = c(0xFF020A06), surface = c(0xC7020A06), surfaceHigh = c(0x1F39FF88),
        border = c(0xFF1F7A4A), borderWidth = 2.dp, text = c(0xFF39FF88), muted = c(0xFF2FBF6C),
        accent = c(0xFF39FF88), onAccent = c(0xFF020A06), accentSoft = c(0xFF39FF88), accent2 = c(0xFF39FF88),
        warning = c(0xFFFFB020), ok = c(0xFF39FF88),
        track = c(0xFF0B2A1A), gauge = listOf(c(0xFF39FF88), c(0xFF39FF88)), glow = c(0xFF39FF88),
        turnBackground = solid(c(0xCC020A06)), turnText = c(0xFF39FF88), turnMuted = c(0xFF2FBF6C),
        turnTile = solid(Color.Transparent), turnTileText = c(0xFF39FF88), turnBorder = c(0xFF39FF88),
        speedSurface = null, speedText = null,
        dockActive = c(0x1F39FF88), onDockActive = c(0xFF39FF88), limitRing = c(0xFFFFB020),
        radius = 0f, cardStyle = CardStyle.Outline, font = FontChoice.Mono, numberWeight = FontWeight.Normal,
        italicNumbers = false, labelSpacing = 0.04f, upperLabels = true,
        decoration = Decoration.Scanlines, layout = HomeLayout.SpeedMapMusic, speedStyle = SpeedStyle.Tape,
        map = MapLook.Dark, mapTint = c(0x2E0E5A30), mapPuck = c(0xFF39FF88),
    )

    // ------------------------------------------------ Saffron family (research/ampli-drive-saffron-soft-variants.html .s1–.s6)

    val SaffronDay = saffron(
        id = "saffron_day", code = "S2", name = "Saffron Day", light = true, greeting = "ਸ਼ੁਭ ਯਾਤਰਾ",
        bg = 0xFFFFF6E6, fg = 0xFF2A1A0C, fg2 = 0xFF8A5A2B, acc = 0xFFFF9933, onacc = 0xFF2A1300, acc2 = 0xFFFFC27A,
        deep = 0xFF0F5257, ondeep = 0xFFFFFFFF, deep2 = 0xFFCDEAE7,
        paper = 0xFFFFFFFF, ink = 0xFF2A1A0C, ink2 = 0xFF6B4A2E, btn = 0xFFF6E9D2, trackC = 0xFFF0E0C6, progc = 0xFF0F5257,
        appbg = 0xFFFFFFFF, appline = 0xFFEAD6B5, onbg = 0xFF0F5257, onfg = 0xFFFFFFFF,
        layout = HomeLayout.MusicSpeedMap, map = MapLook.Liberty, mapTint = 0x0FFF9933,
        decoration = Decoration.Jaali, pattern = 0x170F5257,
    )

    val RajasthaniPink = saffron(
        id = "rajasthani_pink", code = "S3", name = "Rajasthani Pink", light = true, greeting = "શુભ યાત્રા",
        bg = 0xFFF6D9D6, fg = 0xFF4A1027, fg2 = 0xFF8E3A55, acc = 0xFFF28C28, onacc = 0xFF2A1300, acc2 = 0xFFFFD08A,
        deep = 0xFFC2185B, ondeep = 0xFFFFFFFF, deep2 = 0xFFFFE0EA,
        paper = 0xFFFFF5EC, ink = 0xFF4A1027, ink2 = 0xFF7A4A55, btn = 0xFFF8E3D6, trackC = 0xFFF1D7C8, progc = 0xFFC2185B,
        appbg = 0xFFFFF5EC, appline = 0x40C2185B, onbg = 0xFFF28C28, onfg = 0xFF2A1300,
        layout = HomeLayout.MapStacked, map = MapLook.Liberty, mapTint = 0x10C2185B,
        decoration = Decoration.Jaali, pattern = 0x29C2185B,
    )

    val KeralaGreen = saffron(
        id = "kerala_green", code = "S4", name = "Kerala Green", light = false, greeting = "ಶುಭ ಪ್ರಯಾಣ",
        bg = 0xFF1F4D2B, fg = 0xFFF5EBD3, fg2 = 0xFFD9C78F, acc = 0xFFD4A93A, onacc = 0xFF1F1600, acc2 = 0xFFF2C94C,
        deep = 0xFF0E2A16, ondeep = 0xFFF5EBD3, deep2 = 0xFFB9D3A8,
        paper = 0xFFF5EBD3, ink = 0xFF1F2A14, ink2 = 0xFF5A5A3A, btn = 0xFFEADCB8, trackC = 0xFFE2D3A8, progc = 0xFF1F6B35,
        appbg = 0x12F5EBD3, appline = 0x59D4A93A, onbg = 0xFFF5EBD3, onfg = 0xFF0E2A16,
        layout = HomeLayout.MapTopRow, map = MapLook.Dark, mapTint = 0x33215A2F,
        decoration = Decoration.Stripes, pattern = 0x0FD4A93A,
    )

    val BanarasiRoyal = saffron(
        id = "banarasi_royal", code = "S5", name = "Banarasi Royal", light = false, greeting = "இனிய பயணம்",
        bg = 0xFF3A0A16, fg = 0xFFFCEFD9, fg2 = 0xFFE9C77B, acc = 0xFFC9A24A, onacc = 0xFF2A0710, acc2 = 0xFFF4D58D,
        deep = 0xFF2A0710, ondeep = 0xFFFCEFD9, deep2 = 0xFFE9C77B,
        paper = 0xFF5C1426, ink = 0xFFFCEFD9, ink2 = 0xFFE2C2A8, btn = 0xFF741C33, trackC = 0xFF2A0710, progc = 0xFFE9C77B,
        appbg = 0x14C9A24A, appline = 0x61C9A24A, onbg = 0xFFC9A24A, onfg = 0xFF2A0710,
        layout = HomeLayout.MapSpeedMusic, map = MapLook.Dark, mapTint = 0x333A0A16,
        decoration = Decoration.Jaali, pattern = 0x2EC9A24A, border = 0xFFC9A24A,
    )

    val HoliPop = saffron(
        id = "holi_pop", code = "S6", name = "Holi Pop", light = true, greeting = "শুভ যাত্রা",
        bg = 0xFFFFF8EE, fg = 0xFF1E1030, fg2 = 0xFF5A4A70, acc = 0xFFFF3D8B, onacc = 0xFFFFFFFF, acc2 = 0xFFFFD23F,
        deep = 0xFF2F7BFF, ondeep = 0xFFFFFFFF, deep2 = 0xFFE6EEFF,
        paper = 0xFFFFFFFF, ink = 0xFF1E1030, ink2 = 0xFF5A4A70, btn = 0xFFF3EEFA, trackC = 0xFFEDE6F5, progc = 0xFFFF3D8B,
        appbg = 0xFFFFFFFF, appline = 0xFF1E1030, onbg = 0xFF1E1030, onfg = 0xFFFFFFFF,
        layout = HomeLayout.SpeedMapMusic, map = MapLook.Bright, mapTint = 0x00000000,
        decoration = Decoration.Holi, pattern = 0x8CFF3D8B, border = 0xFF1E1030, cardStyle = CardStyle.Pop,
        speedFill = 0xFFFFD23F, speedInk = 0xFF1E1030,
    )

    // ------------------------------------------------ Soft family (.n1–.n6)

    /** Ampli Soft Day: the default day theme (approved neumorphic sample). */
    val AmpliSoftDay = ampliSoft(
        id = ThemeDefaults.SOFT_DAY, code = "A1", name = "Ampli Soft Day", p = SoftPalettes.Day,
        map = MapLook.Positron,
    )

    /** Ampli Soft Night: the default night theme, dark grey soft surfaces that don't glare. */
    val AmpliSoftNight = ampliSoft(
        id = ThemeDefaults.SOFT_NIGHT, code = "A2", name = "Ampli Soft Night", p = SoftPalettes.Night,
        map = MapLook.Dark,
    )

    val SoftNight = soft(
        id = "soft_night", code = "N2", name = "Soft Night", light = false,
        nb = 0xFF23272E, lt = 0xFF2E333B, dk = 0xFF16191E, fg = 0xFFE4E8EE, mut = 0xFF8E98A8, mut2 = 0xFFAAB3C0,
        acc = 0xFF2DD4BF, onacc = 0xFF0B2723, warn = 0xFFF5A524, trk = 0xFF2B3038,
        layout = HomeLayout.SpeedMapMusic, map = MapLook.Dark,
    )

    val SoftMint = soft(
        id = "soft_mint", code = "N3", name = "Soft Mint", light = true,
        nb = 0xFFDDEEE6, lt = 0xFFF7FFFB, dk = 0xFFB3CBBF, fg = 0xFF24423A, mut = 0xFF5F7F72, mut2 = 0xFF4A6A5E,
        acc = 0xFF0F9D7A, onacc = 0xFFFFFFFF, warn = 0xFFE86F4C, trk = 0xFFC8DDD3,
        layout = HomeLayout.MapSpeedMusic, map = MapLook.Positron, mapTint = 0x140F9D7A,
    )

    val SoftPeach = soft(
        id = "soft_peach", code = "N4", name = "Soft Peach", light = true,
        nb = 0xFFF1E6DA, lt = 0xFFFFF8F0, dk = 0xFFD3C2AF, fg = 0xFF4A3A2C, mut = 0xFF8C7560, mut2 = 0xFF6E5A47,
        acc = 0xFFD9673A, onacc = 0xFFFFFFFF, warn = 0xFF4A7FBF, trk = 0xFFE3D5C5,
        layout = HomeLayout.MapTopRow, map = MapLook.Positron, mapTint = 0x14D9673A,
    )

    val SoftLavender = soft(
        id = "soft_lavender", code = "N5", name = "Soft Lavender", light = true,
        nb = 0xFFE6E3F0, lt = 0xFFFFFFFF, dk = 0xFFC3BDD6, fg = 0xFF3A3452, mut = 0xFF7A7392, mut2 = 0xFF5D5675,
        acc = 0xFF7C5CE0, onacc = 0xFFFFFFFF, warn = 0xFFE8833A, trk = 0xFFD6D1E6,
        layout = HomeLayout.MusicSpeedMap, map = MapLook.Positron, mapTint = 0x147C5CE0,
    )

    val SoftGlass = soft(
        id = "soft_glass", code = "N6", name = "Soft Glass Hybrid", light = true,
        nb = 0xFFE4E9F0, lt = 0xFFFFFFFF, dk = 0xFFBFC8D6, fg = 0xFF2E3A4E, mut = 0xFF6C7A90, mut2 = 0xFF56647A,
        acc = 0xFF0EA5E9, onacc = 0xFFFFFFFF, warn = 0xFFF97362, trk = 0xFFD3DAE4,
        layout = HomeLayout.SpeedMapMusic, map = MapLook.Liberty,
    ).let {
        // Frosted white glass panels over the map side instead of soft plastic.
        it.copy(
            surface = Color(0x6BFFFFFF), surfaceHigh = Color(0x99FFFFFF), border = Color(0xB3FFFFFF), borderWidth = 1.dp,
            cardStyle = CardStyle.Glass, turnBackground = solid(Color(0xD9FFFFFF)),
        )
    }

    // ------------------------------------------------ Glass family (iOS-style frosted and tinted glass)

    val FrostedGlass = glass(
        id = ThemeAccess.FROSTED_GLASS, code = "G1", name = "Frosted Glass", nameRes = R.string.theme_frosted_glass,
        p = GlassPalettes.Frosted, layout = HomeLayout.MapSpeedMusic, map = MapLook.Positron,
    )

    val MidnightFrost = glass(
        id = ThemeAccess.MIDNIGHT_FROST, code = "G2", name = "Midnight Frost", nameRes = R.string.theme_midnight_frost,
        p = GlassPalettes.MidnightFrost, layout = HomeLayout.MapSpeedMusic, map = MapLook.Dark,
    )

    val RoseGlass = glass(
        id = "rose_glass", code = "G3", name = "Ampli Rose Glass", nameRes = R.string.theme_rose_glass,
        p = GlassPalettes.Rose, layout = HomeLayout.SpeedMapMusic, map = MapLook.Dark,
    )

    val CyanGlass = glass(
        id = "cyan_glass", code = "G4", name = "Cyan Glass", nameRes = R.string.theme_cyan_glass,
        p = GlassPalettes.Cyan, layout = HomeLayout.MapStacked, map = MapLook.Positron,
    )

    val VioletGlass = glass(
        id = "violet_glass", code = "G5", name = "Violet Glass", nameRes = R.string.theme_violet_glass,
        p = GlassPalettes.Violet, layout = HomeLayout.MapSpeedMusic, map = MapLook.Fiord,
    )

    val SunsetGlass = glass(
        id = "sunset_glass", code = "G6", name = "Sunset Glass", nameRes = R.string.theme_sunset_glass,
        p = GlassPalettes.Sunset, layout = HomeLayout.MusicSpeedMap, map = MapLook.Liberty,
    )

    /**
     * Picker order: Classic (01–06, 09), Glass family, Saffron family, Soft family.
     * Luxury Gold (design 10) is not shipped.
     */
    val all: List<DriveTheme> = listOf(
        MidnightGlass, AmpliRose, DaylightClean, SportRed, Synthwave, MinimalMono, HudGreen,
        FrostedGlass, MidnightFrost, RoseGlass, CyanGlass, VioletGlass, SunsetGlass,
        DesiSaffron, SaffronDay, RajasthaniPink, KeralaGreen, BanarasiRoyal, HoliPop,
        AmpliSoftDay, AmpliSoftNight,
        Neumorphic, SoftNight, SoftMint, SoftPeach, SoftLavender, SoftGlass,
    )

    fun byId(id: String?): DriveTheme? = all.firstOrNull { it.id == id }

    /**
     * The theme to show now. A Pro theme without Pro (refund, test switch turned off) falls
     * back to the free theme for that time of day.
     */
    fun resolve(choice: ThemeChoice, night: Boolean, isPro: Boolean): DriveTheme =
        byId(ThemeAccess.resolveId(choice, night, isPro, ids)) ?: if (night) AmpliSoftNight else AmpliSoftDay

    private val ids: Set<String> = all.mapTo(HashSet()) { it.id }
}

/** Glass family: translucent tinted panels that blur a colourful backdrop, thin bright edges. */
private fun glass(
    id: String, code: String, name: String, @StringRes nameRes: Int, p: GlassPalette,
    layout: HomeLayout, map: MapLook,
): DriveTheme {
    val text = c(p.text)
    val accent = c(p.accent)
    return DriveTheme(
        id = id, number = 0, name = name, isLight = p.light,
        background = c(p.top), surface = c(p.panel), surfaceHigh = if (p.light) c(0x73FFFFFF) else c(0x1FFFFFFF),
        border = c(p.edgeTop), borderWidth = 1.dp, text = text, muted = c(p.muted),
        accent = accent, onAccent = c(p.onAccent), accentSoft = accent, accent2 = c(p.gauge2),
        warning = if (p.light) c(0xFFB45309) else c(0xFFFBBF24), ok = if (p.light) c(0xFF15803D) else c(0xFF4ADE80),
        track = if (p.light) c(0x1F0B1220) else c(0x26FFFFFF), gauge = listOf(accent, c(p.gauge2)), glow = null,
        turnBackground = solid(c(p.overMap)), turnText = text, turnMuted = c(p.muted),
        turnTile = Brush.linearGradient(listOf(accent, c(p.gauge2))), turnTileText = c(p.onAccent),
        turnBorder = c(p.edgeTop),
        speedSurface = null, speedText = null,
        dockActive = accent, onDockActive = c(p.onAccent), limitRing = Color(0xFFE21B1B),
        radius = 5f, cardStyle = CardStyle.Glass, font = FontChoice.Sans, numberWeight = FontWeight.SemiBold,
        italicNumbers = false, labelSpacing = 0.02f, upperLabels = false,
        decoration = Decoration.Glass, layout = layout, speedStyle = SpeedStyle.Arc,
        map = map, mapTint = Color.Transparent, mapPuck = accent,
        family = ThemeFamily.Glass, code = code,
        glass = p, nameRes = nameRes,
    )
}

/** Saffron family: a saffron speed card, a deep turn card, cream "paper" music card, script greeting. */
private fun saffron(
    id: String, code: String, name: String, light: Boolean, greeting: String,
    bg: Long, fg: Long, fg2: Long, acc: Long, onacc: Long, acc2: Long,
    deep: Long, ondeep: Long, deep2: Long,
    paper: Long, ink: Long, ink2: Long, btn: Long, trackC: Long, progc: Long,
    appbg: Long, appline: Long, onbg: Long, onfg: Long,
    layout: HomeLayout, map: MapLook, mapTint: Long,
    decoration: Decoration, pattern: Long,
    border: Long = appline, cardStyle: CardStyle = CardStyle.Solid,
    speedFill: Long = acc, speedInk: Long = onacc,
): DriveTheme = DriveTheme(
    id = id, number = 0, name = name, isLight = light,
    background = c(bg), surface = c(deep), surfaceHigh = c(appbg),
    border = c(border), borderWidth = if (cardStyle == CardStyle.Pop) 3.dp else 2.dp, text = c(fg), muted = c(fg2),
    accent = c(acc), onAccent = c(onacc), accentSoft = c(acc2), accent2 = c(acc),
    warning = c(acc2), ok = Color(0xFF22A05A),
    track = c(trackC), gauge = listOf(c(acc), c(acc2)), glow = null,
    turnBackground = solid(c(deep)), turnText = c(ondeep), turnMuted = c(deep2),
    turnTile = solid(c(acc)), turnTileText = c(onacc), turnBorder = c(border),
    speedSurface = c(speedFill), speedText = c(speedInk),
    dockActive = c(onbg), onDockActive = c(onfg), limitRing = Color(0xFFE21B1B),
    radius = if (cardStyle == CardStyle.Pop) 4f else 4.7f, cardStyle = cardStyle, font = FontChoice.Sans,
    numberWeight = FontWeight.ExtraBold, italicNumbers = false, labelSpacing = 0.08f, upperLabels = true,
    decoration = decoration, layout = layout, speedStyle = SpeedStyle.Number,
    map = map, mapTint = c(mapTint), mapPuck = c(progc),
    family = ThemeFamily.Saffron, code = code, greeting = greeting, patternColor = c(pattern),
    musicSurface = c(paper), musicText = c(ink), musicMuted = c(ink2), musicButton = c(btn),
    musicTrack = c(trackC), musicProgress = c(progc),
)

/** Soft (neumorphic) family: one base colour, double soft shadows, dial speedometer. */
private fun soft(
    id: String, code: String, name: String, light: Boolean,
    nb: Long, lt: Long, dk: Long, fg: Long, mut: Long, mut2: Long,
    acc: Long, onacc: Long, warn: Long, trk: Long,
    layout: HomeLayout, map: MapLook, mapTint: Long = 0x00000000,
): DriveTheme = DriveTheme(
    id = id, number = 0, name = name, isLight = light,
    background = c(nb), surface = c(nb), surfaceHigh = c(nb),
    border = Color.Transparent, borderWidth = 0.dp, text = c(fg), muted = c(mut),
    accent = c(acc), onAccent = c(onacc), accentSoft = c(acc), accent2 = c(acc),
    warning = c(warn), ok = Color(0xFF3BA776),
    track = c(trk), gauge = listOf(c(acc), c(acc).copy(alpha = 0.7f)), glow = null,
    turnBackground = solid(c(nb)), turnText = c(fg), turnMuted = c(mut2),
    turnTile = solid(c(acc)), turnTileText = c(onacc), turnBorder = Color.Transparent,
    speedSurface = null, speedText = null,
    dockActive = c(nb), onDockActive = c(acc), limitRing = Color(0xFFE21B1B),
    radius = 4.6f, cardStyle = CardStyle.Neumorphic, font = FontChoice.Sans, numberWeight = FontWeight.Black,
    italicNumbers = false, labelSpacing = 0.08f, upperLabels = false,
    decoration = Decoration.None, layout = layout, speedStyle = SpeedStyle.Arc,
    map = map, mapTint = c(mapTint), mapPuck = c(acc),
    shadowDark = c(dk), shadowLight = c(lt),
    family = ThemeFamily.Soft, code = code,
)

/** Ampli Soft Day / Night: the soft family look from a [SoftPalette], with a smaller radius. */
private fun ampliSoft(id: String, code: String, name: String, p: SoftPalette, map: MapLook): DriveTheme = soft(
    id = id, code = code, name = name, light = p.light,
    nb = p.base, lt = p.highlight, dk = p.shadow, fg = p.ink, mut = p.muted, mut2 = p.muted,
    acc = p.accent, onacc = p.onAccent, warn = p.warning, trk = p.track,
    layout = HomeLayout.MapSpeedMusic, map = map, mapTint = p.accent and 0x00FFFFFF or 0x0C000000,
).copy(
    accentSoft = c(p.accentText), ok = c(p.ok), onDockActive = c(p.accentText),
    turnTile = solid(c(p.accentText)), turnTileText = c(if (p.light) 0xFFFFFFFF else p.onAccent),
    radius = 2.8f, softEdge = c(p.edge), danger = c(p.danger),
)

/** The active theme. Every home-screen composable reads colours and shapes from here. */
val LocalDriveTheme = staticCompositionLocalOf { DriveThemes.AmpliRose }
