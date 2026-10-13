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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

/** The raised card every pane sits on. */
@Composable
fun Pane(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dims = LocalDims.current
    val shape = RoundedCornerShape(dims.d(2.6f))
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) { content() }
}

/** Small status pill (status row, map overlays). */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dotColor: Color? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    background: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
) {
    val dims = LocalDims.current
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .then(clickMod)
            .padding(horizontal = dims.d(1.8f), vertical = dims.d(0.9f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1f)),
    ) {
        if (dotColor != null) {
            Box(Modifier.size(dims.d(1.2f)).clip(CircleShape).background(dotColor))
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(dims.d(2.8f)))
        }
        Text(
            text,
            fontSize = dims.t(2.4f, min = 13f),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Big rounded action button used in empty states and settings. Always >= 64 dp tall. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
) {
    val dims = LocalDims.current
    val bg = if (primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .heightIn(min = MinTouch)
            .clip(RoundedCornerShape(dims.d(2.2f)))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = dims.d(3f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f), Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(28.dp))
        Text(text, color = fg, fontSize = dims.t(2.8f, min = 16f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
