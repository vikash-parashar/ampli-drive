package com.vikash.ampli.drive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vikash.ampli.drive.ui.theme.CardStyle
import com.vikash.ampli.drive.ui.theme.DriveTheme
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

/** Smallest touch target for anything a driver taps. */
val MinTouch: Dp = 64.dp

/**
 * Layout unit taken from the mockup: 1 [u] = 1% of the usable screen height, so the same
 * proportions work on 1024x600, 1280x720 and 1920x720.
 */
@Immutable
data class Dims(
    val u: Dp,
    /** sp value of one [u]. */
    val uSp: Float,
    /** Narrower than 1.72:1 (1024x600, 1280x800): panes rebalance and secondary text hides. */
    val compact: Boolean,
) {
    fun d(x: Float): Dp = u * x

    /** Text size of [x] units, never below [min] so it stays readable from the driver's seat. */
    fun t(x: Float, min: Float = 14f): TextUnit = (uSp * x).coerceAtLeast(min).sp
}

val LocalDims = staticCompositionLocalOf { Dims(u = 6.dp, uSp = 6f, compact = false) }

/** Style for big numbers (speed, distance, clock): theme weight, italics and neon glow. */
fun DriveTheme.numberStyle(color: Color = text, glowColor: Color? = glow): TextStyle = TextStyle(
    color = color,
    fontFamily = font.family,
    fontWeight = numberWeight,
    fontStyle = if (italicNumbers) FontStyle.Italic else FontStyle.Normal,
    shadow = glowColor?.let { Shadow(color = it.copy(alpha = 0.8f), blurRadius = 24f) },
)

/** Style for small caption labels (KM/H, SPEED LIMIT…). */
fun DriveTheme.labelStyle(color: Color = muted): TextStyle = TextStyle(
    color = color,
    fontFamily = font.family,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = labelSpacing.em,
)

/** The raised card every pane sits on, painted in the active theme's card style. */
@Composable
fun Pane(
    modifier: Modifier = Modifier,
    fill: Color? = null,
    blur: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = theme.cardShape(dims)
    val color = fill ?: theme.surface
    Box(
        modifier = modifier.themedCard(theme, shape, fill = Brush.linearGradient(listOf(color, color)), blur = blur),
    ) { content() }
}

/** Small status pill (status row, map overlays). */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dotColor: Color? = null,
    iconTint: Color? = null,
    background: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = if (theme.radius <= 0f || theme.cardStyle == CardStyle.Skewed) RectangleShape else CircleShape
    if (theme.isSoft) {
        SoftPill(text, modifier, icon, dotColor, iconTint, background, onClick)
        return
    }
    if (onClick != null) {
        // A tappable pill gets a full 64 dp touch area around the slimmer visible pill.
        Box(modifier.heightIn(min = MinTouch).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Pill(text, Modifier, icon, dotColor, iconTint, background, onClick = null)
        }
        return
    }
    val clickMod = Modifier
    Row(
        modifier = modifier
            .clip(shape)
            // Pills don't blur (they often sit on the map), so glass themes use the stronger tint.
            .background(
                (background ?: theme.surface).let { bg ->
                    val glass = theme.glass
                    if (glass != null && bg == theme.surface) Color(glass.reducedPanel) else bg
                },
            )
            .border(1.dp, theme.border, shape)
            .then(clickMod)
            .padding(horizontal = dims.d(1.8f), vertical = dims.d(0.9f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1f)),
    ) {
        if (dotColor != null) {
            Box(Modifier.size(dims.d(1.2f)).clip(CircleShape).background(dotColor))
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint ?: theme.text, modifier = Modifier.size(dims.d(2.8f)))
        }
        Text(
            text,
            fontSize = dims.t(2.4f, min = 16f),
            color = theme.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Soft pill: a status chip sunk into the surface (like the sample's weather and phone chips),
 * or, when tappable, a raised key that presses in. [background] tints only non-soft themes,
 * except emergency red, which always stays a solid red pill with white text.
 */
@Composable
private fun SoftPill(
    text: String,
    modifier: Modifier,
    icon: ImageVector?,
    dotColor: Color?,
    iconTint: Color?,
    background: Color?,
    onClick: (() -> Unit)?,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val danger = background == theme.danger
    val surface = when {
        danger -> Modifier.clip(CircleShape).background(theme.danger)
        onClick != null -> Modifier.softClickable(theme, CircleShape, onClick)
        else -> Modifier.softWell(theme, CircleShape, SoftDepth.Pressed)
    }
    val content = if (danger) theme.onDanger else theme.text
    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.heightIn(min = MinTouch) else Modifier)
            .then(surface)
            .padding(horizontal = dims.d(1.8f), vertical = dims.d(0.9f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1f)),
    ) {
        if (dotColor != null) {
            Box(Modifier.size(dims.d(1.2f)).clip(CircleShape).background(dotColor))
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (danger) content else iconTint ?: content, modifier = Modifier.size(dims.d(2.8f)))
        }
        Text(text, fontSize = dims.t(2.4f, min = 16f), color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Big action button used in empty states and settings. Always >= 64 dp tall. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val bg = if (primary) theme.accent else theme.surfaceHigh
    val fg = if (primary) theme.onAccent else theme.text
    val shape = theme.buttonShape(dims)
    if (theme.isSoft) {
        // Raised key that presses in; the primary action is marked by accent text, not a fill.
        // Crimson Drive fills it crimson instead, with white text.
        val keys = theme.keys?.takeIf { primary }
        val ink = when {
            keys != null -> keys.onPrimary
            primary -> theme.accentSoft
            else -> theme.text
        }
        Row(
            modifier = modifier
                .heightIn(min = MinTouch)
                .then(if (keys != null) Modifier.primaryKey(theme, keys, shape, onClick) else Modifier.softClickable(theme, shape, onClick))
                .padding(horizontal = dims.d(3f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f), Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(28.dp))
            Text(text, color = ink, fontSize = dims.t(2.8f, min = 16f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Row(
        modifier = modifier
            .heightIn(min = MinTouch)
            .clip(shape)
            .background(bg)
            .then(if (!primary && theme.cardStyle == CardStyle.Outline) Modifier.border(1.dp, theme.border, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = dims.d(3f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f), Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(28.dp))
        Text(text, color = fg, fontSize = dims.t(2.8f, min = 16f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

