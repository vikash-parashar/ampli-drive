package com.vikash.ampli.drive.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vikash.ampli.drive.ui.theme.CardStyle
import com.vikash.ampli.drive.ui.theme.DriveTheme
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

/**
 * A tappable tile or row: clip, background, optional border and a click. On Road Sign the border
 * is the theme's thick black line. [active] marks a selected tile with the accent edge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.themedTap(
    shape: Shape,
    background: Color,
    border: Color? = null,
    borderWidth: Dp = 1.dp,
    active: Boolean = false,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val theme = LocalDriveTheme.current
    val edge = when {
        active -> theme.accentSoft
        theme.cardStyle == CardStyle.Brutal -> theme.border
        else -> border
    }
    val width = if (theme.cardStyle == CardStyle.Brutal) maxOf(borderWidth, 3.dp) else borderWidth
    var m = clip(shape).background(background, shape)
    if (edge != null) m = m.border(if (active) maxOf(width, 2.dp) else width, edge, shape)
    return if (onLongClick != null) {
        m.combinedClickable(enabled = enabled, role = role, onLongClick = onLongClick, onClick = onClick)
    } else {
        m.clickable(enabled = enabled, role = role, onClick = onClick)
    }
}

/** A non-tappable card, sheet or well. [sunk] is kept for older callers and draws the same. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.themedSurface(
    theme: DriveTheme,
    shape: Shape,
    background: Color,
    border: Color? = null,
    borderWidth: Dp = 1.dp,
    sunk: Boolean = false,
): Modifier {
    var m = clip(shape).background(background, shape)
    val edge = if (theme.cardStyle == CardStyle.Brutal) theme.border else border
    if (edge != null) m = m.border(if (theme.cardStyle == CardStyle.Brutal) maxOf(borderWidth, 3.dp) else borderWidth, edge, shape)
    return m
}

/** The Material slider in the theme's accent colour. */
@Composable
fun ThemedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
) {
    val theme = LocalDriveTheme.current
    val colors = androidx.compose.material3.SliderDefaults.colors(
        thumbColor = theme.accent,
        activeTrackColor = theme.accent,
        inactiveTrackColor = theme.track,
    )
    androidx.compose.material3.Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, colors = colors, modifier = modifier)
}
