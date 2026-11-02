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

/** Mint neon and the over-limit red (fixed, like the SOS red). */
private val NeonMint = Color(0xFF6FD9A8)
private val NeonRed = Color(0xFFFF5C5C)
private val LimitRing = Color(0xFFD7263D)
private val LimitMarker = Color(0xFFFFB547)

/** A soft round glow, drawn once per colour and stamped for every ember (cheaper than gradients per ember). */
private fun glowSprite(color: Color): ImageBitmap {
    val size = 64
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val argb = color.toArgb()
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    p.shader = android.graphics.RadialGradient(
        size / 2f, size / 2f, size / 2f,
        intArrayOf(argb, (argb and 0x00FFFFFF) or 0x66000000, argb and 0x00FFFFFF),
        floatArrayOf(0f, 0.35f, 1f),
        android.graphics.Shader.TileMode.CLAMP,
    )
    c.drawCircle(size / 2f, size / 2f, size / 2f, p)
    return bmp.asImageBitmap()
}

/** The ember pool: parallel arrays, reused for the card's whole life. */
private class Embers {
    val x = FloatArray(GaugeMath.MAX_PARTICLES)
    val y = FloatArray(GaugeMath.MAX_PARTICLES)
    val vx = FloatArray(GaugeMath.MAX_PARTICLES)
    val vy = FloatArray(GaugeMath.MAX_PARTICLES)
    val life = FloatArray(GaugeMath.MAX_PARTICLES)
    val r = FloatArray(GaugeMath.MAX_PARTICLES)
    var carry = 0f

    /** Moves the embers [dt] seconds on and lights new ones along the lit arc of a dial [radius] px. */
    fun step(dt: Float, kmh: Float, cx: Float, cy: Float, radius: Float) {
        val s = GaugeMath.share(kmh)
        val fade = dt * (1.2f - 0.4f * s)
        for (i in 0 until GaugeMath.MAX_PARTICLES) {
            if (life[i] <= 0f) continue
            life[i] -= fade
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            vy[i] -= radius * 0.25f * dt
        }
        carry += GaugeMath.emberRate(kmh) * dt
        var i = 0
        while (carry >= 1f && i < GaugeMath.MAX_PARTICLES) {
            if (life[i] <= 0f) {
                val a = Math.toRadians(GaugeMath.angleDeg(kotlin.random.Random.nextFloat() * kmh).toDouble())
                val ca = kotlin.math.cos(a).toFloat()
                val sa = kotlin.math.sin(a).toFloat()
                val out = radius * (0.1f + 0.5f * s)
                x[i] = cx + ca * radius
                y[i] = cy + sa * radius
                vx[i] = ca * out + (kotlin.random.Random.nextFloat() - 0.5f) * radius * 0.15f
                vy[i] = sa * out - radius * (0.15f + 0.5f * s * kotlin.random.Random.nextFloat())
                life[i] = 1f
                r[i] = radius * (0.03f + 0.07f * s * kotlin.random.Random.nextFloat())
                carry -= 1f
            }
            i++
        }
        if (carry > 1f) carry = 1f
    }
}

/**
 * Speed: a neon dial 0..220 km/h (ticks every 20, a 270° sweep), lit up to the current speed with
 * embers rising off the lit arc. Embers, glow and breathing grow with speed; parked, only a slow,
 * faint breathing glow. Over the road's limit everything turns red. In the middle the huge number
 * and "km/h"; below it the road's limit sign (hidden when unknown); the FASTag balance as a pill at
 * the top right (hidden when none). One Canvas, a fixed ember pool and pre-drawn glow sprites: no allocation per frame;
 * 30 fps when parked, nothing at all while the screen is off.
 */
@Composable
internal fun GaugeSpeedCard(ui: HomeUi, shape: Shape, modifier: Modifier = Modifier) {
    val theme = LocalDriveTheme.current
    val kmhNow by rememberUpdatedState((ui.speed ?: 0).toFloat())
    val over = GaugeMath.over(ui.speed, ui.limit)
    val hue = if (over) NeonRed else NeonMint
    val mintSprite = remember { glowSprite(NeonMint) }
    val redSprite = remember { glowSprite(NeonRed) }
    val embers = remember { Embers() }
    // The animation clock (seconds) and a tick that the draw step reads, so frames only redraw.
    val clock = remember { mutableFloatStateOf(0f) }
    val frame = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val geo = remember { FloatArray(3) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        // Robolectric previews: one still frame (an endless clock would never let a test settle).
        if (!GaugeLive.enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            var last = 0L
            while (true) {
                withFrameNanos { t ->
                    val idle = kmhNow <= 0.5f
                    if (last == 0L) {
                        last = t
                    } else if (!idle || t - last >= GaugeMath.IDLE_FRAME_NS) {
                        val dt = ((t - last) / 1_000_000_000f).coerceAtMost(0.05f)
                        last = t
                        clock.floatValue += dt
                        if (geo[2] > 0f) embers.step(dt, kmhNow, geo[0], geo[1], geo[2])
                        frame.longValue++
                    }
                }
            }
        }
    }
    val fastag = ui.fastag?.first
    val limit = ui.limit
    val text = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER } }
    val bold = remember { android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD) }
    val tickLabels = remember { GaugeMath.TICKS.map { it.toString() } }
    val ink = theme.text
    val unit = stringResource(R.string.speed_unit)
    val speedText = (ui.speed ?: 0).toString()
    val dims = LocalDims.current
    Box(modifier.clip(shape).background(Color(0xFF060A12))) {
    Canvas(Modifier.fillMaxSize()) {
        frame.longValue // redraw on every animation frame
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.5f
        val radius = minOf(w, h) * 0.36f
        geo[0] = cx
        geo[1] = cy
        geo[2] = radius
        val kmh = kmhNow
        val s = GaugeMath.share(kmh)
        val pulse = 0.6f + 0.4f * kotlin.math.sin(clock.floatValue * GaugeMath.pulseRate(kmh))
        // Background halo: faint when parked, stronger with speed.
        val sprite = if (over) redSprite else mintSprite
        val halo = (radius * 3f).toInt()
        drawImage(
            sprite,
            dstOffset = androidx.compose.ui.unit.IntOffset((cx - halo / 2f).toInt(), (cy - halo / 2f).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(halo, halo),
            alpha = ((0.06f + 0.22f * s) * (0.7f + 0.3f * pulse)).coerceIn(0f, 1f),
        )
        // Embers, added light.
        for (i in 0 until GaugeMath.MAX_PARTICLES) {
            val life = embers.life[i]
            if (life <= 0f) continue
            val rr = embers.r[i] * (1.5f - life) * 3f
            drawImage(
                sprite,
                dstOffset = androidx.compose.ui.unit.IntOffset((embers.x[i] - rr).toInt(), (embers.y[i] - rr).toInt()),
                dstSize = androidx.compose.ui.unit.IntSize((rr * 2).toInt().coerceAtLeast(1), (rr * 2).toInt().coerceAtLeast(1)),
                alpha = (0.8f * life).coerceIn(0f, 1f),
                blendMode = androidx.compose.ui.graphics.BlendMode.Plus,
            )
        }
        // The ring: a dim track, then the lit arc with a layered glow (no blur filters on 8.1).
        val tl = Offset(cx - radius, cy - radius)
        val sz = Size(radius * 2, radius * 2)
        drawArc(hue.copy(alpha = 0.22f), GaugeMath.START_DEG, GaugeMath.SWEEP_DEG, false, tl, sz, style = Stroke(radius * 0.05f, cap = StrokeCap.Round))
        val lit = (GaugeMath.SWEEP_DEG * s).coerceAtLeast(0.6f)
        val glow = (0.35f + 0.65f * s) * pulse
        drawArc(hue.copy(alpha = 0.10f * glow), GaugeMath.START_DEG, lit, false, tl, sz, style = Stroke(radius * 0.26f, cap = StrokeCap.Round))
        drawArc(hue.copy(alpha = 0.20f * glow), GaugeMath.START_DEG, lit, false, tl, sz, style = Stroke(radius * 0.16f, cap = StrokeCap.Round))
        drawArc(hue, GaugeMath.START_DEG, lit, false, tl, sz, style = Stroke(radius * 0.07f, cap = StrokeCap.Round))
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            // Ticks and labels every 20.
            text.typeface = bold
            text.textSize = radius * 0.13f
            text.setShadowLayer(0f, 0f, 0f, 0)
            for (k in tickLabels.indices) {
                val v = GaugeMath.TICKS[k]
                val a = Math.toRadians(GaugeMath.angleDeg(v.toFloat()).toDouble())
                val ca = kotlin.math.cos(a).toFloat()
                val sa = kotlin.math.sin(a).toFloat()
                val on = v <= kmh
                drawLine(if (on) hue else ink.copy(alpha = 0.4f), Offset(cx + ca * radius * 0.86f, cy + sa * radius * 0.86f), Offset(cx + ca * radius * 0.76f, cy + sa * radius * 0.76f), 2.dp.toPx())
                text.color = ink.copy(alpha = 0.85f).toArgb()
                nc.drawText(tickLabels[k], cx + ca * radius * 1.2f, cy + sa * radius * 1.2f + text.textSize * 0.35f, text)
            }
            // The limit's marker on the ring.
            if (limit != null) {
                val a = Math.toRadians(GaugeMath.angleDeg(limit.toFloat()).toDouble())
                drawCircle(LimitMarker, radius * 0.04f, Offset(cx + kotlin.math.cos(a).toFloat() * radius * 0.86f, cy + kotlin.math.sin(a).toFloat() * radius * 0.86f))
            }
            // The number, glowing, and km/h.
            text.textSize = radius * 0.55f
            text.color = Color(0xFFF2F5FA).toArgb()
            text.setShadowLayer(radius * 0.12f, 0f, 0f, hue.toArgb())
            nc.drawText(speedText, cx, cy + radius * 0.14f, text)
            text.setShadowLayer(0f, 0f, 0f, 0)
            text.textSize = radius * 0.12f
            text.color = ink.copy(alpha = 0.6f).toArgb()
            nc.drawText(unit, cx, cy + radius * 0.34f, text)
            // The road's limit sign, centred under km/h (hidden when the limit is unknown).
            if (limit != null) {
                val sy = cy + radius * 0.68f
                val sr = radius * 0.17f
                drawCircle(Color.White, sr, Offset(cx, sy))
                drawCircle(LimitRing, sr * 0.88f, Offset(cx, sy), style = Stroke(sr * 0.25f))
                text.textSize = sr * 0.85f
                text.color = 0xFF111111.toInt()
                nc.drawText(limit.toString(), cx, sy + sr * 0.3f, text)
            }
        }
    }
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

/** Live animation on a real unit; a still frame under Robolectric (screen previews, tests). */
private object GaugeLive {
    val enabled: Boolean = android.os.Build.FINGERPRINT != "robolectric"
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
