package com.vikash.ampli.drive.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Ampli brand colors, shared with the Ampli music player. */
object Ampli {
    val Slate = Color(0xFF334155)
    val Rose = Color(0xFFE11D48)
    val RoseDeep = Color(0xFFBE123C)
    val Night = Color(0xFF0F0D14)
    val NightRaised = Color(0xFF1C1820)
    val NightRaised2 = Color(0xFF24202A)
    val Blush = Color(0xFFFDA4AF)
    val Text = Color(0xFFF5F3F7)
    val Muted = Color(0xFFA39FAB)
    val Online = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)

    /** Same diagonal gradient as the app icon. */
    val Gradient: Brush = Brush.linearGradient(listOf(Slate, Rose))
}

/** System font families that need an Android Typeface lookup. */
object DriveFonts {
    /** Roboto Condensed ships on every Android build as "sans-serif-condensed". */
    val Condensed: FontFamily = FontFamily(
        android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL),
    )
}

private fun schemeFor(t: DriveTheme): ColorScheme {
    val base = if (t.isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = t.accentSoft,
        onPrimary = t.onAccent,
        primaryContainer = t.accent,
        onPrimaryContainer = t.onAccent,
        secondary = t.muted,
        secondaryContainer = t.surfaceHigh,
        onSecondaryContainer = t.text,
        tertiary = t.accent2,
        background = t.background,
        onBackground = t.text,
        surface = t.background,
        onSurface = t.text,
        surfaceVariant = t.surface,
        onSurfaceVariant = t.muted,
        surfaceContainer = t.surface,
        surfaceContainerHigh = t.surfaceHigh,
        surfaceContainerHighest = t.surfaceHigh,
        surfaceContainerLow = t.surface,
        surfaceContainerLowest = t.background,
        outline = t.track,
        outlineVariant = t.border,
        error = t.warning,
    ).let { scheme ->
        // Soft themes: text-field and outlined-button borders need 3:1 on the soft base, and
        // dialogs and sheets sit on the same base colour as everything else.
        val edge = t.softEdge
        if (t.cardStyle == CardStyle.Neumorphic && edge != null) {
            scheme.copy(
                outline = edge,
                surfaceContainerHigh = t.surface,
                surfaceContainerHighest = t.surface,
                surfaceContainerLow = t.surface,
                surfaceContainerLowest = t.surface,
                surfaceTint = Color.Transparent,
            )
        } else {
            scheme
        }
    }
}

/** Soft themes: rounder dialogs, sheets and text fields, as in the approved sample. */
private val SoftShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

private fun TextStyle.withFont(family: FontFamily) = copy(fontFamily = family)

private fun typographyFor(t: DriveTheme): Typography {
    val f = t.font.family
    return Typography().run {
        copy(
            displayLarge = displayLarge.withFont(f),
            displayMedium = displayMedium.withFont(f),
            displaySmall = displaySmall.withFont(f),
            headlineLarge = headlineLarge.withFont(f),
            headlineMedium = headlineMedium.withFont(f),
            headlineSmall = headlineSmall.copy(fontFamily = f, fontWeight = FontWeight.Bold),
            titleLarge = titleLarge.copy(fontFamily = f, fontWeight = FontWeight.Bold),
            titleMedium = titleMedium.copy(fontFamily = f, fontWeight = FontWeight.SemiBold),
            titleSmall = titleSmall.withFont(f),
            bodyLarge = bodyLarge.withFont(f),
            bodyMedium = bodyMedium.withFont(f),
            bodySmall = bodySmall.withFont(f),
            labelLarge = labelLarge.withFont(f),
            labelMedium = labelMedium.withFont(f),
            labelSmall = labelSmall.withFont(f),
        )
    }
}

/**
 * Provides [theme] through [LocalDriveTheme] and maps it onto Material colours and type, so
 * plain `Text` picks up the theme's font (Text's default style is bodyLarge).
 */
@Composable
fun AmpliDriveTheme(theme: DriveTheme, content: @Composable () -> Unit) {
    val scheme = remember(theme) { schemeFor(theme) }
    val type = remember(theme) { typographyFor(theme) }
    CompositionLocalProvider(LocalDriveTheme provides theme) {
        if (theme.cardStyle == CardStyle.Neumorphic && theme.softEdge != null) {
            MaterialTheme(colorScheme = scheme, typography = type, shapes = SoftShapes, content = content)
        } else {
            MaterialTheme(colorScheme = scheme, typography = type, content = content)
        }
    }
}
