package com.vikash.ampli.drive.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlin.math.max

/*
 * Big Glance's three cards: the neon speed dial, and two with depth (something drawn behind the
 * big text and something on top of it: the disc over the song's first words, the car's dot over
 * the place name). Low glare: theme fills, the accent for what moves.
 */

/** The speed gauge's geometry and motion. Pure, so it is unit-tested. */
internal object GaugeMath {
    const val VMAX = 220
    const val TICK_STEP = 20

    /** Start of the 270° sweep (bottom left), clockwise from 3 o'clock as Android draws arcs. */
    const val START_DEG = 135f
    const val SWEEP_DEG = 270f

    /** Ember pool size: never more on screen, never allocated per frame. */
    const val MAX_PARTICLES = 120

    /** Idle (parked) redraws at most this often: the slow breathing glow only. */
    const val IDLE_FRAME_NS = 33_000_000L

    val TICKS: List<Int> = (0..VMAX step TICK_STEP).toList()

    fun share(kmh: Float): Float = (kmh / VMAX).coerceIn(0f, 1f)

    /** Angle (degrees, Android arc convention) of [kmh] on the dial. */
    fun angleDeg(kmh: Float): Float = START_DEG + SWEEP_DEG * share(kmh)

    /** New embers a second: a trickle when slow, a blaze near the top. None when parked. */
    fun emberRate(kmh: Float): Float = if (kmh <= 0.5f) 0f else 4f + 90f * share(kmh)

    /** Breathing speed (radians a second): slow when parked, faster with speed. */
    fun pulseRate(kmh: Float): Float = 1.5f + 6f * share(kmh)

    /** Red once over the road's limit, mint otherwise (and without a known limit). */
    fun over(kmh: Int?, limit: Int?): Boolean = kmh != null && limit != null && kmh > limit
}

/** The FASTag pill's mint edge and mark. */
private val NeonMint = Color(0xFF6FD9A8)

/**
 * Speed: the speedometer the driver picked in Settings › Speedometer (Neon Arc by default; see
 * [Speedometer]) with the limit sign, trip, average and GPS under it, and the FASTag balance as a
 * pill at the top right (hidden when none).
 */
@Composable
internal fun GaugeSpeedCard(ui: HomeUi, shape: Shape, modifier: Modifier = Modifier) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val fastag = ui.fastag?.first
    Box(modifier.clip(shape).background(theme.background)) {
        Speedometer(
            style = ui.speedo.style,
            speedKmh = ui.speed?.toFloat(),
            limit = ui.limit,
            tripKm = ui.speedo.tripKm,
            avgKmh = ui.speedo.avgKmh,
            gpsFix = ui.speedo.gpsFix,
            accuracyM = ui.speedo.accuracyM,
            modifier = Modifier.fillMaxSize().padding(top = if (fastag != null) dims.d(7f) else 0.dp),
        )
        // FASTag balance: a pill at the top right that opens the FASTag screen (hidden without one).
        if (fastag != null) {
            TollPill(fastag, ui.onFastag, Modifier.align(Alignment.TopEnd).padding(dims.d(1.6f)))
        }
    }
}

/** "₹1,240" with a generic toll-tag mark (not any official logo) on a dark, mint-edged pill. */
@Composable
private fun TollPill(balance: String, onClick: () -> Unit, modifier: Modifier) {
    val dims = LocalDims.current
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xD9171C33))
            .border(1.dp, NeonMint.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = dims.d(1.6f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1f)),
    ) {
        Canvas(Modifier.size(dims.d(3.6f), dims.d(2.6f))) {
            val sw = 1.6.dp.toPx()
            val w = size.width
            val h = size.height
            drawRoundRect(NeonMint, topLeft = Offset(sw / 2, sw / 2), size = Size(w - sw, h - sw), cornerRadius = CornerRadius(h * 0.25f), style = Stroke(sw))
            drawArc(NeonMint, 200f, 140f, false, Offset(w * 0.18f, h * 0.28f), Size(w * 0.4f, h * 0.55f), style = Stroke(sw, cap = StrokeCap.Round))
            drawCircle(NeonMint, h * 0.11f, Offset(w * 0.74f, h * 0.55f))
        }
        Text(balance, color = Color(0xFFF2F5FA), fontWeight = FontWeight.Bold, fontSize = dims.t(2.6f, min = 15f), maxLines = 1)
    }
}

/** The first two words of a title, one per line ("राम\nमेरे"): the card's big excerpt. */
internal fun titleExcerpt(title: String): String =
    title.split(' ', '\t', '-', '|', '(', ')').filter { w -> w.any { it.isLetterOrDigit() } }.take(2).joinToString("\n")

/**
 * Map: the live map (streets, the car's puck) behind; the place name big (the locality while idle,
 * the next turn's distance while navigating) with the distance or the turn in the accent; the
 * car's dot on top of the text; "Where to? ›" at the bottom left. [map] is null until the map may
 * load (fast start on weak units) and shows its own "Finding location…" / allow-location card.
 */
@Composable
internal fun DepthMapCard(ui: HomeUi, shape: Shape, map: (@Composable () -> Unit)?, modifier: Modifier = Modifier) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    Box(modifier.clip(shape).background(theme.surface)) {
        map?.invoke()
        val t = ui.turn
        val big = t?.distance ?: ui.place
        val sub = if (t != null) t.text else ui.destination
        if (ui.hasFix && big != null) {
            Column(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = dims.d(12f), start = dims.d(2f), end = dims.d(2f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    big.uppercase(),
                    color = theme.text.copy(alpha = 0.92f),
                    fontFamily = theme.font.family,
                    fontWeight = FontWeight.Black,
                    fontSize = dims.t(6.4f, min = 26f),
                    lineHeight = dims.t(6f, min = 25f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                sub?.let {
                    Text(it, color = theme.accent, fontWeight = FontWeight.Bold, fontSize = dims.t(3.2f, min = 18f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // The car's dot on top of the name (the map follows the car, so it sits in the middle).
        if (ui.hasFix && t == null) {
            Box(Modifier.align(Alignment.Center).size(dims.d(5f)).clip(CircleShape).background(theme.accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(dims.d(2.4f)).clip(CircleShape).background(theme.accent).border(2.dp, theme.background, CircleShape))
            }
        }
        if (t != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(dims.d(2f)).size(dims.d(10f)).clip(theme.cardShape(dims, 0.5f)).background(theme.banner), contentAlignment = Alignment.Center) {
                TurnArrow(t, theme.onBanner, Modifier.size(dims.d(7f)))
            }
        }
        Text(
            stringResource(if (t != null) R.string.home_next_turn else R.string.map_you_are_here).uppercase(),
            color = theme.muted,
            fontWeight = FontWeight.SemiBold,
            fontSize = dims.t(2.3f, min = 14f),
            modifier = Modifier.align(Alignment.TopStart).padding(dims.d(2.6f)),
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(dims.d(1.6f))
                .heightIn(min = MinTouch)
                .clip(RoundedCornerShape(50))
                .background(theme.surface.copy(alpha = 0.85f))
                .clickable(onClick = ui.onNav)
                .padding(horizontal = dims.d(2f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.nav_search_title), color = theme.text, fontWeight = FontWeight.ExtraBold, fontSize = dims.t(3.2f, min = 18f))
            // A vector chevron, not "›": some head-unit fonts draw that glyph as an empty box.
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = theme.text, modifier = Modifier.size(dims.d(4f)))
        }
    }
}
