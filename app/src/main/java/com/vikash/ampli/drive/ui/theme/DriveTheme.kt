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

/** The four home layouts (Car Hub style studies 3, 4, 6 and 8). */
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
    Barlow(FontFamily(Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold), Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold))),

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

    /** Glow behind big numbers, or null (none of the four themes uses one). */
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
)

object DriveThemes {

    /** Android 8.x head units (SC7731E class): Map First's cards go solid there. */
    private val OLD_UNIT = Build.VERSION.SDK_INT < Build.VERSION_CODES.P

    /** Style 4, the default (free): the map fills the screen, slate cards in the corners. */
    val MapFirst: DriveTheme = HomePalettes.MapFirst.let { p ->
        val card = c(if (OLD_UNIT) HomePalettes.MAP_FIRST_CARD_SOLID else HomePalettes.MAP_FIRST_CARD)
        theme(
            id = ThemeDefaults.MAP_FIRST, name = "Map First", nameRes = R.string.theme_map_first, style = HomeStyle.MapFirst, p = p,
            radius = 3.75f, cardStyle = CardStyle.Glass, borderWidth = 0.dp,
            font = FontChoice.Sora, numberFont = FontChoice.Sora, numberWeight = FontWeight.Bold,
            labelSpacing = 0f, upperLabels = false,
            map = MapLook.Positron, mapTint = 0x1AE8D9B5,
            turnBackground = solid(card), turnText = c(p.text), turnMuted = c(p.muted),
            turnTile = solid(c(p.accent)), turnTileText = c(p.onAccent), turnBorder = Color.Transparent,
            overMap = card,
        )
    }

    /** Style 3: three giant tiles on true black, for night driving. */
    val BigGlance: DriveTheme = HomePalettes.BigGlance.let { p ->
        theme(
            id = ThemeDefaults.BIG_GLANCE, name = "Big Glance", nameRes = R.string.theme_big_glance, style = HomeStyle.BigGlance, p = p,
            radius = 5f, cardStyle = CardStyle.Solid, borderWidth = 0.dp,
            font = FontChoice.Archivo, numberFont = FontChoice.BigShoulders, numberWeight = FontWeight.Black,
            labelSpacing = 0.16f, upperLabels = true,
            map = MapLook.Dark, mapTint = 0x1A000000,
            turnBackground = solid(c(p.surface)), turnText = c(p.text), turnMuted = c(p.muted),
            turnTile = solid(c(p.surfaceHigh)), turnTileText = c(p.accent), turnBorder = Color.Transparent,
            overMap = c(0xF2141418),
        )
    }

    /** Style 6: a magazine grid where tile size equals importance. */
    val Bento: DriveTheme = HomePalettes.Bento.let { p ->
        theme(
            id = ThemeDefaults.BENTO, name = "Bento Grid", nameRes = R.string.theme_bento, style = HomeStyle.Bento, p = p,
            radius = 3.75f, cardStyle = CardStyle.Solid, borderWidth = 3.dp,
            font = FontChoice.Bricolage, numberFont = FontChoice.Bricolage, numberWeight = FontWeight.ExtraBold,
            labelSpacing = 0.1f, upperLabels = false,
            map = MapLook.Positron, mapTint = 0x00000000,
            turnBackground = solid(c(p.accent)), turnText = c(p.onAccent), turnMuted = c(0xFFDCE4FF),
            turnTile = solid(c(0x33FFFFFF)), turnTileText = c(p.onAccent), turnBorder = Color.Transparent,
            overMap = c(0xF2FFFFFF),
        )
    }

    /** Style 8: Indian road-sign language at full volume. */
    val RoadSign: DriveTheme = HomePalettes.RoadSign.let { p ->
        theme(
            id = ThemeDefaults.ROAD_SIGN, name = "Neo-Brutal Road Sign", nameRes = R.string.theme_road_sign, style = HomeStyle.RoadSign, p = p,
            radius = 0f, cardStyle = CardStyle.Brutal, borderWidth = 4.dp,
            font = FontChoice.Barlow, numberFont = FontChoice.ArchivoBlack, numberWeight = FontWeight.Normal,
            labelSpacing = 0.08f, upperLabels = true,
            map = MapLook.Liberty, mapTint = 0x00000000,
            turnBackground = solid(c(p.accent)), turnText = c(p.onAccent), turnMuted = c(0xFFDCE8F7),
            turnTile = solid(Color.Transparent), turnTileText = c(p.onAccent), turnBorder = c(p.border),
            overMap = c(0xFFFFFFFF),
        )
    }

    /** Picker order: the free default first. */
    val all: List<DriveTheme> = listOf(MapFirst, BigGlance, Bento, RoadSign)

    fun byId(id: String?): DriveTheme? = all.firstOrNull { it.id == id }

    /**
     * The theme to show now: the driver's choice, Big Glance after sunset when "Auto night" is on
     * (Pro), and Map First for anything unknown or locked.
     */
    fun resolve(choice: ThemeChoice, night: Boolean, isPro: Boolean): DriveTheme =
        byId(ThemeAccess.resolveId(choice, night, isPro)) ?: MapFirst
}

/** The active theme. Every composable reads colours and shapes from here. */
val LocalDriveTheme = staticCompositionLocalOf { DriveThemes.MapFirst }
