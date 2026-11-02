package com.vikash.ampli.drive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vikash.ampli.drive.ui.theme.CardStyle
import com.vikash.ampli.drive.ui.theme.DriveTheme

/*
 * Theme painting for cards. Only flat fills, borders and one offset shadow: no blur, no bitmaps,
 * no setShadowLayer, so it costs the same on an Android 8.1 SC7731E unit as on a phone.
 */

/** Card shape for this theme. [scale] shrinks the corner radius for smaller elements. */
fun DriveTheme.cardShape(dims: Dims, scale: Float = 1f): Shape =
    if (radius <= 0f) RectangleShape else RoundedCornerShape(dims.d(radius * scale))

/** Shape for buttons and chips: rounded on rounded themes, square on Road Sign. */
fun DriveTheme.buttonShape(dims: Dims): Shape =
    if (radius <= 0f) RectangleShape else RoundedCornerShape(dims.d(radius * 5f / 6f))

/** Small labels: upper-case on themes whose design uses caps. */
fun DriveTheme.label(text: String): String = if (upperLabels) text.uppercase() else text

/** Road Sign's hard shadow offset (CSS `box-shadow: 10px 10px 0 #111` at 1280x800). */
val BRUTAL_SHADOW: Dp = 6.dp

/**
 * Paints a card for the active theme: fill, border and, on Road Sign, the hard black shadow.
 * [blur] is kept for callers written for the old glass themes; nothing is ever blurred now.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.themedCard(
    theme: DriveTheme,
    shape: Shape,
    fill: Brush = Brush.linearGradient(listOf(theme.surface, theme.surface)),
    border: Color = theme.border,
    accentEdge: Boolean = true,
    blur: Boolean = true,
): Modifier {
    var m = this
    if (theme.cardStyle == CardStyle.Brutal) m = m.hardShadow(shape, theme.border, BRUTAL_SHADOW)
    m = m.clip(shape).background(fill, shape)
    if (theme.borderWidth > 0.dp && border.alpha > 0f) m = m.border(theme.borderWidth, border, shape)
    return m
}

/** A flat offset shadow (CSS `box-shadow: 6px 6px 0`): one path fill, no blur. */
fun Modifier.hardShadow(shape: Shape, color: Color, offset: Dp = BRUTAL_SHADOW): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val o = offset.toPx()
    translate(o, o) { drawPath(path, color) }
}

/** The theme's background colour behind the whole screen. */
fun Modifier.themeBackground(theme: DriveTheme): Modifier = background(theme.background)
