package com.vikash.ampli.drive.ui.theme

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.prefs.ThemeChoice
import com.vikash.ampli.drive.prefs.ThemeDefaults

/**
 * How a card is painted. [Glass]: Map First's slate cards over the map (translucent, never blurred).
 * [Brutal]: Road Sign's thick black border with a hard offset shadow.
 */
enum class CardStyle { Solid, Glass, Brutal }

/** The four home layouts (Car Hub style studies 3, 4, 6 and 8); Calm uses the Map First layout. */
enum class HomeStyle { BigGlance, MapFirst, Bento, RoadSign }

/**
 * Fonts bundled in res/font (SIL Open Font License 1.1, see assets/licenses/fonts.txt). Each
 * family carries only the weights its theme uses; Devanagari and other scripts fall back to the
 * system font automatically.
 */
enum class FontChoice(val family: FontFamily) {
    Archivo(FontFamily(Font(R.font.archivo_bold, FontWeight.Bold), Font(R.font.archivo_extrabold, FontWeight.ExtraBold))),
    Sora(FontFamily(Font(R.font.sora_regular, FontWeight.Normal), Font(R.font.sora_semibold, FontWeight.SemiBold), Font(R.font.sora_bold, FontWeight.Bold))),
    Bricolage(FontFamily(Font(R.font.bricolage_semibold, FontWeight.SemiBold), Font(R.font.bricolage_extrabold, FontWeight.ExtraBold))),
    /** Barlow Condensed: every big number (speed, distance, rupees, clock). */
    Barlow(
        FontFamily(
            Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
            Font(R.font.barlow_condensed_bold, FontWeight.Bold),
            Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
        ),
    ),

    /** Barlow: words on every theme. */
    BarlowSans(
        FontFamily(
            Font(R.font.barlow_medium, FontWeight.Medium),
            Font(R.font.barlow_medium, FontWeight.Normal),
            Font(R.font.barlow_semibold, FontWeight.SemiBold),
            Font(R.font.barlow_bold, FontWeight.Bold),
            Font(R.font.barlow_bold, FontWeight.ExtraBold),
        ),
    ),

    /** Hind: words when the app language is written in Devanagari (it carries Latin too). */
    Hind(
        FontFamily(
            Font(R.font.hind_medium, FontWeight.Medium),
            Font(R.font.hind_medium, FontWeight.Normal),
            Font(R.font.hind_semibold, FontWeight.SemiBold),
            Font(R.font.hind_semibold, FontWeight.Bold),
            Font(R.font.hind_semibold, FontWeight.ExtraBold),
        ),
    ),

    /** Big Glance's giant numbers. */
    BigShoulders(FontFamily(Font(R.font.big_shoulders_black, FontWeight.Black))),

    /** Road Sign's numbers and headings. */
    ArchivoBlack(FontFamily(Font(R.font.archivo_black, FontWeight.Normal))),
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
 * One home-screen theme. Every screen reads its colours, shapes and type from here, so Settings,
 * Fuel, Radar, the Car hub, Help, navigation, dialogs and the More sheet all follow the theme.
 * Sizes such as [radius] are in layout units (1 unit = 1% of screen height).
 */
@Immutable
data class DriveTheme(
    val id: String,
    val name: String,
    @StringRes val nameRes: Int,
    val isLight: Boolean,
    val style: HomeStyle,

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
    /** Accent for text, icons and selected chips on [surface]. */
    val accentSoft: Color,
    val accent2: Color,
    val warning: Color,
    val ok: Color,

    // Gauges and progress
    val track: Color,
    val gauge: List<Color>,

    // Turn card (Home, navigation screen)
    val turnBackground: Brush,
    val turnText: Color,
    val turnMuted: Color,
    val turnTile: Brush,
    val turnTileText: Color,
    val turnBorder: Color,

    // Selected / primary keys
    val dockActive: Color,
    val onDockActive: Color,
    val limitRing: Color,

    // Shape and type
    val radius: Float,
    val cardStyle: CardStyle,
    val font: FontChoice,
    /** Big numbers: speed, distance, clock. */
    val numberFont: FontChoice,
    val numberWeight: FontWeight,
    /** Extra letter spacing for small labels, in em. */
    val labelSpacing: Float,
    val upperLabels: Boolean,

    // Map
    val map: MapLook,
    /** Colour laid over the map tiles, or Transparent. */
    val mapTint: Color,
    /** The car and the route line. */
    val mapPuck: Color,

    /** The theme's dark "ink" tile and the text on it. */
    val ink: Color,
    val onInk: Color,

    /** Card behind text over the live map (Map First's slate; solid on Android 8.x). */
    val overMap: Color,

    /** Turn banner and the active rail key (Soft Indigo at night, Navy by day, teal on Calm). */
    val banner: Color = accent,
    val onBanner: Color = onAccent,
    /** Map colour behind tiles that are still loading. */
    val mapBase: Color = background,
    /** The left rail: its strip, its key fill and the key ink. */
    val rail: Color = background,
    val railKey: Color = surface,
    val railInk: Color = text,

    /** Glow behind big numbers, or null (none of the themes uses one). */
    val glow: Color? = null,

    // Emergency red (SOS, crash countdown, low FASTag balance): the same on every theme on purpose,
    // so drivers and responders recognise it, and never one of the theme accents.
    val danger: Color = Color(HomePalettes.SOS),
    val onDanger: Color = Color(HomePalettes.ON_SOS),
) {
    /** Pro themes need the one-time purchase. The free set lives in [ThemeAccess.FREE_IDS]. */
    val isPro: Boolean get() = !ThemeAccess.isFree(id)
}

private fun c(argb: Long) = Color(argb)
private fun solid(color: Color): Brush = Brush.linearGradient(listOf(color, color))

/** Builds a theme from its palette; [overMap] is where Map First's cards differ on old units. */
private fun theme(
    id: String,
    name: String,
    @StringRes nameRes: Int,
    style: HomeStyle,
    p: HomePalette,
    radius: Float,
    cardStyle: CardStyle,
    borderWidth: Dp,
    font: FontChoice,
    numberFont: FontChoice,
    numberWeight: FontWeight,
    labelSpacing: Float,
    upperLabels: Boolean,
    map: MapLook,
    mapTint: Long,
    turnBackground: Brush,
    turnText: Color,
    turnMuted: Color,
    turnTile: Brush,
    turnTileText: Color,
    turnBorder: Color,
    overMap: Color,
): DriveTheme = DriveTheme(
    id = id, name = name, nameRes = nameRes, isLight = p.light, style = style,
    background = c(p.background), surface = c(p.surface), surfaceHigh = c(p.surfaceHigh),
    border = c(p.border), borderWidth = borderWidth, text = c(p.text), muted = c(p.muted),
    accent = c(p.accent), onAccent = c(p.onAccent), accentSoft = c(p.accentText), accent2 = c(p.accent),
    warning = c(p.warning), ok = c(p.ok),
    track = c(p.track), gauge = listOf(c(p.accent), c(p.accentText)),
    turnBackground = turnBackground, turnText = turnText, turnMuted = turnMuted,
    turnTile = turnTile, turnTileText = turnTileText, turnBorder = turnBorder,
    dockActive = c(p.accent), onDockActive = c(p.onAccent), limitRing = c(HomePalettes.LIMIT_RING),
    radius = radius, cardStyle = cardStyle, font = font, numberFont = numberFont, numberWeight = numberWeight,
    labelSpacing = labelSpacing, upperLabels = upperLabels,
    map = map, mapTint = c(mapTint), mapPuck = c(p.accent),
    ink = c(p.ink), onInk = c(p.onInk), overMap = overMap,
    banner = c(p.banner), onBanner = c(p.onBanner), mapBase = c(p.map),
    rail = c(p.rail), railKey = c(p.railKey), railInk = c(p.railInk),
)

object DriveThemes {

    /** Below Android 12 nothing can blur behind a card: Map First's cards go solid there. */
    private val OLD_UNIT = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    /** Card radius 24 px and buttons 20 px at 800 px tall (1 unit = 8 px). */
    private const val RADIUS = 3f

    private fun mapFirst(p: HomePalette, card: Long, id: String, name: String, @StringRes nameRes: Int, map: MapLook): DriveTheme =
        theme(
            id = id, name = name, nameRes = nameRes, style = HomeStyle.MapFirst, p = p,
            radius = RADIUS, cardStyle = CardStyle.Glass, borderWidth = 2.dp,
            font = FontChoice.BarlowSans, numberFont = FontChoice.Barlow, numberWeight = FontWeight.Bold,
            labelSpacing = 0.08f, upperLabels = false,
            map = map, mapTint = 0x00000000,
            turnBackground = solid(c(p.banner)), turnText = c(p.onBanner), turnMuted = c(p.onBanner).copy(alpha = 0.82f),
            turnTile = solid(c(p.onBanner).copy(alpha = 0.14f)), turnTileText = c(p.onBanner), turnBorder = Color.Transparent,
            overMap = c(card),
        )

    /** Map First at night (the free default after sunset): Midnight Navy, Sage Mint, Soft Indigo. */
    val MapFirst: DriveTheme = mapFirst(
        HomePalettes.Night,
        if (OLD_UNIT) HomePalettes.MAP_FIRST_CARD_SOLID else HomePalettes.MAP_FIRST_CARD,
        ThemeDefaults.MAP_FIRST, "Map First", R.string.theme_map_first, MapLook.Dark,
    )

    /** Map First by day: Seal Blue, Navy and Crimson Pink. Switches to [MapFirst] at sunset. */
    val MapFirstDay: DriveTheme = mapFirst(
        HomePalettes.Day, HomePalettes.MAP_FIRST_DAY_CARD,
        ThemeDefaults.MAP_FIRST, "Map First", R.string.theme_map_first, MapLook.Positron,
    )

    /** Calm (Pro): the Map First layout in teal-grey and warm gold. */
    val Calm: DriveTheme = mapFirst(
        HomePalettes.Calm, HomePalettes.CALM_CARD,
        ThemeDefaults.CALM, "Calm", R.string.theme_calm, MapLook.Dark,
    )

    /** Style 3: three giant tiles in the night palette. */
    val BigGlance: DriveTheme = HomePalettes.BigGlance.let { p ->
        theme(
            id = ThemeDefaults.BIG_GLANCE, name = "Big Glance", nameRes = R.string.theme_big_glance, style = HomeStyle.BigGlance, p = p,
            radius = RADIUS, cardStyle = CardStyle.Solid, borderWidth = 2.dp,
            font = FontChoice.BarlowSans, numberFont = FontChoice.Barlow, numberWeight = FontWeight.Bold,
            labelSpacing = 0.12f, upperLabels = true,
            map = MapLook.Dark, mapTint = 0x00000000,
            turnBackground = solid(c(p.banner)), turnText = c(p.onBanner), turnMuted = c(p.onBanner).copy(alpha = 0.82f),
            turnTile = solid(c(p.onBanner).copy(alpha = 0.14f)), turnTileText = c(p.onBanner), turnBorder = Color.Transparent,
            overMap = c(HomePalettes.MAP_FIRST_CARD_SOLID),
        )
    }

    /** Style 6: a magazine grid where tile size equals importance, in the day palette. */
    val Bento: DriveTheme = HomePalettes.Bento.let { p ->
        theme(
            id = ThemeDefaults.BENTO, name = "Bento Grid", nameRes = R.string.theme_bento, style = HomeStyle.Bento, p = p,
            radius = RADIUS, cardStyle = CardStyle.Solid, borderWidth = 2.dp,
            font = FontChoice.BarlowSans, numberFont = FontChoice.Barlow, numberWeight = FontWeight.Bold,
            labelSpacing = 0.08f, upperLabels = false,
            map = MapLook.Positron, mapTint = 0x00000000,
            turnBackground = solid(c(p.banner)), turnText = c(p.onBanner), turnMuted = c(p.onBanner).copy(alpha = 0.82f),
            turnTile = solid(c(p.onBanner).copy(alpha = 0.14f)), turnTileText = c(p.onBanner), turnBorder = Color.Transparent,
            overMap = c(HomePalettes.MAP_FIRST_DAY_CARD),
        )
    }

    /** Style 8: road-sign language (thick navy lines, hard shadows) on the day palette. */
    val RoadSign: DriveTheme = HomePalettes.RoadSign.let { p ->
        theme(
            id = ThemeDefaults.ROAD_SIGN, name = "Neo-Brutal Road Sign", nameRes = R.string.theme_road_sign, style = HomeStyle.RoadSign, p = p,
            radius = 0f, cardStyle = CardStyle.Brutal, borderWidth = 4.dp,
            font = FontChoice.BarlowSans, numberFont = FontChoice.Barlow, numberWeight = FontWeight.ExtraBold,
            labelSpacing = 0.08f, upperLabels = true,
            map = MapLook.Liberty, mapTint = 0x00000000,
            turnBackground = solid(c(p.banner)), turnText = c(p.onBanner), turnMuted = c(p.onBanner).copy(alpha = 0.82f),
            turnTile = solid(Color.Transparent), turnTileText = c(p.onBanner), turnBorder = c(p.border),
            overMap = c(0xFFFDFEFF),
        )
    }

    /** Picker order: the free default first, then Calm. */
    val all: List<DriveTheme> = listOf(MapFirst, Calm, BigGlance, Bento, RoadSign)

    fun byId(id: String?): DriveTheme? = all.firstOrNull { it.id == id }

    /**
     * The theme to show now: the driver's choice (Map First in its day or night colours by the
     * sun), Big Glance after sunset when "Auto night" is on (Pro), and Map First for anything
     * unknown or locked.
     */
    fun resolve(choice: ThemeChoice, night: Boolean, isPro: Boolean): DriveTheme {
        val id = ThemeAccess.resolveId(choice, night, isPro)
        if (id == ThemeDefaults.MAP_FIRST) return if (night) MapFirst else MapFirstDay
        return byId(id) ?: MapFirst
    }
}

/** The active theme. Every composable reads colours and shapes from here. */
val LocalDriveTheme = staticCompositionLocalOf { DriveThemes.MapFirst }
