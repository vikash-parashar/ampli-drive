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
import androidx.compose.ui.graphics.Path
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
 * Big Glance's three cards with depth: something drawn behind the big text and something on top
 * of it (the road and the ego car around the speed, the disc over the song's first words, the
 * car's dot over the place name). Low glare: theme fills, the theme accent for what moves.
 */

/** The speed card's road. Pure, so the flow and the perspective are unit-tested. */
internal object RoadMotion {
    /** Road cycles (one dash spacing) per second for each km/h: 60 km/h ≈ 0.7 dashes a second. */
    const val FLOW_PER_KMH = 0.012f

    /** Dashes on the centre line at once. */
    const val DASHES = 8

    /** Length of one dash along the road (0..1 before the perspective). */
    const val DASH_LEN = 0.06f

    /** Horizon, as a share of the card's height. */
    const val HORIZON = 0.38f

    /** Redraw at most this often while moving (a weak ARMv7 unit needs no more). */
    const val FRAME_NS = 33_000_000L

    /** The road's position after [dtSec] at [kmh]; never moves at 0 (or below). */
    fun advance(offset: Float, kmh: Int, dtSec: Float): Float {
        if (kmh <= 0 || dtSec <= 0f) return offset
        val next = offset + kmh * FLOW_PER_KMH * dtSec
        return next - kotlin.math.floor(next)
    }

    /**
     * Where dash [i] sits: its near and far edges as depth 0 (horizon) .. 1 (bottom of the card),
     * squared so dashes grow and speed up as they come closer.
     */
    fun dashDepth(i: Int, offset: Float): Pair<Float, Float> {
        var p = i.toFloat() / DASHES + offset
        p -= kotlin.math.floor(p)
        val far = p * p
        val nearP = (p + DASH_LEN).coerceAtMost(1f)
        return far to nearP * nearP
    }
}

/**
 * Speed: the road in perspective with lane dashes flowing towards the driver (speed-proportional,
 * still at 0 km/h), the huge number over the road and the ego car, seen from behind, on top of the
 * number. Frames run only while moving and the screen is on.
 */
@Composable
internal fun RoadSpeedCard(ui: HomeUi, shape: Shape, modifier: Modifier = Modifier) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val kmh = ui.speed ?: 0
    val speedNow by rememberUpdatedState(kmh)
    val offset = remember { mutableFloatStateOf(0f) }
    val moving = kmh > 0
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last == 0L) {
                    last = t
                } else if (t - last >= RoadMotion.FRAME_NS) {
                    offset.floatValue = RoadMotion.advance(offset.floatValue, speedNow, (t - last) / 1_000_000_000f)
                    last = t
                }
            }
        }
    }
    val bg = if (ui.over) theme.danger else theme.surface
    val fg = if (ui.over) theme.onDanger else theme.text
    val road = if (ui.over) Color.Black.copy(alpha = 0.18f) else theme.surfaceHigh
    val edge = fg.copy(alpha = 0.22f)
    val dash = if (ui.over) theme.onDanger.copy(alpha = 0.8f) else theme.accent
    val path = remember { Path() }
    BoxWithConstraints(modifier.clip(shape).background(bg)) {
        val h = maxHeight
        // Background: the road.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val ht = size.height
            val hy = ht * RoadMotion.HORIZON
            val cx = w / 2f
            val topHalf = w * 0.03f
            val bottomHalf = w * 0.55f
            path.reset()
            path.moveTo(cx - topHalf, hy)
            path.lineTo(cx + topHalf, hy)
            path.lineTo(cx + bottomHalf, ht)
            path.lineTo(cx - bottomHalf, ht)
            path.close()
            drawPath(path, road)
            val sw = 2.dp.toPx()
            drawLine(edge, Offset(cx - topHalf, hy), Offset(cx - bottomHalf, ht), sw)
            drawLine(edge, Offset(cx + topHalf, hy), Offset(cx + bottomHalf, ht), sw)
            val o = offset.floatValue
            val unit = 1.dp.toPx()
            for (i in 0 until RoadMotion.DASHES) {
                val (z1, z2) = RoadMotion.dashDepth(i, o)
                val y1 = hy + (ht - hy) * z1
                val y2 = hy + (ht - hy) * z2
                val w1 = unit * (1f + 5f * z1)
                val w2 = unit * (1f + 5f * z2)
                path.reset()
                path.moveTo(cx - w1, y1)
                path.lineTo(cx + w1, y1)
                path.lineTo(cx + w2, y2)
                path.lineTo(cx - w2, y2)
                path.close()
                drawPath(path, dash)
            }
        }
        // Middle: the number, about 42% of the card tall, its baseline near 78% down.
        val numberSize = with(LocalDensity.current) { minOf(h * 0.42f, maxWidth * 0.5f).toSp() }
        Text(
            ui.speed?.toString() ?: "0",
            color = if (ui.speed == null) fg.copy(alpha = 0.35f) else fg.copy(alpha = 0.95f),
            fontFamily = theme.numberFont.family,
            fontWeight = FontWeight.Black,
            fontSize = numberSize,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = h * 0.34f),
        )
        // On top: the ego car from behind, with a soft shadow.
        val body = fg.copy(alpha = 0.92f)
        val glass = bg.copy(alpha = 0.9f)
        val lamp = Color(0xFFB8323F)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val ht = size.height
            val cw = max(w * 0.2f, 40.dp.toPx())
            val ch = ht * 0.16f
            val cx = w / 2f
            val cy = ht * 0.7f
            drawOval(Color.Black.copy(alpha = 0.45f), topLeft = Offset(cx - cw * 0.62f, cy + ch * 0.86f), size = Size(cw * 1.24f, ch * 0.32f))
            drawRoundRect(body, topLeft = Offset(cx - cw / 2f, cy), size = Size(cw, ch), cornerRadius = CornerRadius(cw * 0.25f))
            drawRoundRect(glass, topLeft = Offset(cx - cw * 0.34f, cy + ch * 0.12f), size = Size(cw * 0.68f, ch * 0.32f), cornerRadius = CornerRadius(cw * 0.08f))
            val lw = cw * 0.16f
            val lh = ch * 0.13f
            drawRoundRect(lamp, topLeft = Offset(cx - cw * 0.44f, cy + ch * 0.58f), size = Size(lw, lh), cornerRadius = CornerRadius(lh / 2f))
            drawRoundRect(lamp, topLeft = Offset(cx + cw * 0.44f - lw, cy + ch * 0.58f), size = Size(lw, lh), cornerRadius = CornerRadius(lh / 2f))
        }
        Text(
            stringResource(R.string.speed_label).uppercase() + " · " + stringResource(R.string.speed_unit),
            color = fg.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold,
            fontSize = dims.t(2.4f, min = 14f),
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopStart).padding(dims.d(2.6f)),
        )
        if (ui.limit != null) {
            LimitBadge(ui.limit, dims.d(10f), Modifier.align(Alignment.TopEnd).padding(dims.d(2f)), dark = true)
        }
        ui.rpm?.let {
            Text(
                stringResource(R.string.speed_rpm, it),
                color = fg.copy(alpha = 0.7f),
                fontSize = dims.t(2.4f, min = 14f),
                modifier = Modifier.align(Alignment.BottomStart).padding(dims.d(2.6f)),
            )
        }
    }
}

/** The first two words of a title, one per line ("राम\nमेरे"): the card's big excerpt. */
internal fun titleExcerpt(title: String): String =
    title.split(' ', '\t', '-', '|', '(', ')').filter { w -> w.any { it.isLetterOrDigit() } }.take(2).joinToString("\n")

/**
 * Music: the source badge (Ampli MP3's icon, or Bluetooth and the phone's name), the title's first
 * words big with the disc (album art when there is some) over them, turning only while playing,
 * the small equaliser bars, then title and artist.
 */
@Composable
internal fun DepthMusicCard(ui: HomeUi, shape: Shape, modifier: Modifier = Modifier) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val m = ui.music
    Column(
        modifier.clip(shape).background(theme.surface).clickable(onClick = m.onOpen).padding(dims.d(2.6f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(1f)),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
            SourceBadge(m, Modifier.size(dims.d(4.4f)))
            Text(
                (m.sourceLine ?: stringResource(R.string.home_playing)).uppercase(),
                color = theme.muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = dims.t(2.3f, min = 14f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (m.controls) {
                Box(
                    Modifier.size(maxOf(dims.d(9f), MinTouch)).clip(CircleShape).border(2.dp, theme.accent, CircleShape).clickable(onClick = m.onPlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (m.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (m.playing) R.string.music_pause else R.string.music_play),
                        tint = theme.accent,
                        modifier = Modifier.size(maxOf(dims.d(9f), MinTouch) * 0.5f),
                    )
                }
            }
        }
        // Behind: the excerpt. On top: the disc.
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Text(
                titleExcerpt(m.title),
                color = theme.text.copy(alpha = 0.92f),
                fontFamily = theme.font.family,
                fontWeight = FontWeight.Black,
                fontSize = dims.t(8.4f, min = 30f),
                lineHeight = dims.t(8f, min = 29f),
                maxLines = 2,
                overflow = TextOverflow.Clip,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            SpinningDisc(m, Modifier.align(Alignment.CenterEnd).padding(end = dims.d(1f)).size(dims.d(20f)))
        }
        EqBars(
            playing = m.playing,
            color = if (m.playing) theme.accent else theme.surfaceHigh.copy(alpha = 1f),
            unit = dims.d(0.5f),
            barWidth = dims.d(1.3f),
            gap = dims.d(0.7f),
        )
        Text(m.title, color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(3.2f, min = 18f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        m.artist?.let { Text(it, color = theme.muted, fontSize = dims.t(2.5f, min = 15f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** Ampli MP3's (or the playing app's) icon, a Bluetooth mark for phone audio, else a note. */
@Composable
private fun SourceBadge(m: MusicUi, modifier: Modifier) {
    val corner = RoundedCornerShape(22)
    when {
        m.bluetooth -> Box(modifier.clip(CircleShape).background(Color(0xFF1E6FE8)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize(0.7f))
        }
        m.sourceIcon != null -> Image(m.sourceIcon, contentDescription = null, modifier = modifier.clip(corner))
        else -> Box(
            modifier.clip(corner).background(Brush.linearGradient(listOf(Color(0xFFFF5C8A), Color(0xFFB0154A)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize(0.7f))
        }
    }
}

/** The disc: album art in a circle, or a drawn record; it turns (layer only, no recomposition) while playing. */
@Composable
private fun SpinningDisc(m: MusicUi, modifier: Modifier) {
    val theme = LocalDriveTheme.current
    val turn = if (m.playing) {
        rememberInfiniteTransition(label = "disc").animateFloat(
            0f, 360f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "discTurn",
        )
    } else {
        null
    }
    Box(
        modifier
            .graphicsLayer { rotationZ = turn?.value ?: 0f }
            .clip(CircleShape),
    ) {
        val art = m.art
        if (art != null) {
            Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val hole = theme.background
            val ring = theme.accent
            val groove = theme.banner
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(groove, r)
                drawCircle(Color.Black.copy(alpha = 0.25f), r * 0.86f, style = Stroke(r * 0.02f))
                drawCircle(Color.Black.copy(alpha = 0.25f), r * 0.72f, style = Stroke(r * 0.02f))
                drawCircle(ring, r * 0.4f)
                // A notch, so the turn is visible.
                drawCircle(hole, r * 0.06f, center = Offset(center.x + r * 0.26f, center.y))
                drawCircle(hole, r * 0.12f)
            }
        }
    }
}

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
