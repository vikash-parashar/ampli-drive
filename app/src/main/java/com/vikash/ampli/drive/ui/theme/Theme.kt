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
import androidx.compose.ui.unit.sp

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
        // Text-field and outlined-button edges: at least 3:1 on every theme.
        outline = t.muted,
        outlineVariant = t.border,
        error = t.warning,
        inverseSurface = t.banner,
        inverseOnSurface = t.onBanner,
    )
}

/** Rounded dialogs and sheets follow the theme radius; Road Sign keeps them square. */
private fun shapesFor(t: DriveTheme): Shapes = if (t.radius <= 0f) {
    Shapes(
        extraSmall = RoundedCornerShape(0.dp), small = RoundedCornerShape(0.dp), medium = RoundedCornerShape(0.dp),
        large = RoundedCornerShape(0.dp), extraLarge = RoundedCornerShape(0.dp),
    )
} else {
    Shapes(
        // Design system: chips 12, buttons 20, cards 24.
        extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp),
    )
}

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
            // Read in one glance: titles 26-28, body 20-24, nothing a driver reads under 18.
            titleLarge = titleLarge.copy(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
            titleMedium = titleMedium.copy(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
            titleSmall = titleSmall.copy(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
            bodyLarge = bodyLarge.copy(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
            bodyMedium = bodyMedium.copy(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 25.sp),
            bodySmall = bodySmall.withFont(f),
            labelLarge = labelLarge.copy(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
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
    val shapes = remember(theme) { shapesFor(theme) }
    CompositionLocalProvider(LocalDriveTheme provides theme) {
        MaterialTheme(colorScheme = scheme, typography = type, shapes = shapes, content = content)
    }
}
