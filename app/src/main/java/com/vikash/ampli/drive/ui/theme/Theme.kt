package com.vikash.ampli.drive.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

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

private val NightColors = darkColorScheme(
    primary = Ampli.Blush,
    onPrimary = Color(0xFF4C0519),
    primaryContainer = Ampli.Rose,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFCBD5E1),
    secondaryContainer = Ampli.NightRaised2,
    onSecondaryContainer = Color(0xFFF1F5F9),
    tertiary = Ampli.Rose,
    background = Ampli.Night,
    onBackground = Ampli.Text,
    surface = Ampli.Night,
    onSurface = Ampli.Text,
    surfaceVariant = Ampli.NightRaised,
    onSurfaceVariant = Ampli.Muted,
    surfaceContainer = Ampli.NightRaised,
    surfaceContainerHigh = Ampli.NightRaised2,
    outline = Ampli.Slate,
    outlineVariant = Color(0x12FFFFFF),
)

private val DayColors = lightColorScheme(
    primary = Ampli.RoseDeep,
    onPrimary = Color.White,
    primaryContainer = Ampli.Rose,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF475569),
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Ampli.Rose,
    background = Color(0xFFF4F1F3),
    onBackground = Color(0xFF17131A),
    surface = Color(0xFFF4F1F3),
    onSurface = Color(0xFF17131A),
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF5B5560),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDE7EA),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0x14000000),
)

private val DriveTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Night is the default look; [night] = false swaps to the day palette. */
@Composable
fun AmpliDriveTheme(night: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (night) NightColors else DayColors,
        typography = DriveTypography,
        content = content,
    )
}
