package com.vikash.ampli.drive.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** The music card's equaliser bars: still while paused, moving while the session plays. Pure. */
internal object EqBarMotion {
    /** Bar heights at rest (the still picture), in Dims units. */
    val REST = floatArrayOf(5f, 10f, 7f, 12f, 4f, 9f, 6f)

    /** Whole cycles per [PERIOD_MS] for each bar, so the loop has no seam. */
    private val CYCLES = intArrayOf(2, 3, 2, 1, 3, 2, 3)

    const val PERIOD_MS = 2_400

    /** Shortest bar while playing, as a share of the tallest. */
    const val MIN_SHARE = 0.25f

    val MAX: Float = REST.max()

    /** Height of bar [index] (Dims units) at loop position [phase] (0..1). */
    fun height(index: Int, phase: Float, playing: Boolean): Float {
        val rest = REST[index]
        if (!playing) return rest
        val offset = rest / MAX
        val wave = 0.5f + 0.5f * sin(2.0 * PI * (CYCLES[index] * phase + offset)).toFloat()
        return MAX * (MIN_SHARE + (1f - MIN_SHARE) * wave)
    }
}

/**
 * The bars, drawn on one canvas. While [playing] an infinite transition moves them; the phase is
 * read only in the draw step, so Home redraws but does not recompose. Paused: no animation at all.
 */
@Composable
internal fun EqBars(playing: Boolean, color: Color, unit: Dp, barWidth: Dp, gap: Dp, modifier: Modifier = Modifier) {
    val phase: State<Float> = if (playing) {
        rememberInfiniteTransition(label = "eq").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(EqBarMotion.PERIOD_MS, easing = LinearEasing)),
            label = "eqPhase",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val n = EqBarMotion.REST.size
    Canvas(modifier.size(barWidth * n + gap * (n - 1), unit * EqBarMotion.MAX)) {
        val bw = barWidth.toPx()
        val g = gap.toPx()
        val u = unit.toPx()
        val r = minOf(4.dp.toPx(), bw / 2f)
        val p = phase.value
        for (i in 0 until n) {
            val h = EqBarMotion.height(i, p, playing) * u
            val x = i * (bw + g)
            val top = size.height - h
            drawRoundRect(color, topLeft = Offset(x, top), size = Size(bw, h), cornerRadius = CornerRadius(r, r))
            // Square bottom corners.
            if (h > r) drawRect(color, topLeft = Offset(x, size.height - r), size = Size(bw, r))
        }
    }
}
