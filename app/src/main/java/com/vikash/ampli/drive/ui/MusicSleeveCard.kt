package com.vikash.ampli.drive.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.ui.theme.FontChoice
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlin.math.min

/*
 * Home's music card, style C "Sleeve + disc" from the approved music-card board: a record sleeve
 * tilted a little (10° at most, so the title stays sharp) with the title printed on its front (two
 * lines, shrinking, then cut off with "…") and the artist under it. While playing the record slides
 * half out of the sleeve and turns at 20 rpm; on pause it eases to a stop in about a second and
 * slides back in. With reduced motion it never turns. The source chip at the top says where the
 * sound comes from; phone audio without track info is "Phone se gaana" and the phone's name, with
 * "Bluetooth" only in the chip. The 80 px play key is at the bottom right, by the driver.
 */

/** The board's card: the stage is 362 x 262 at full size, the sleeve 252, the record 226. */
private const val STAGE_W = 362f
private const val STAGE_H = 262f
private const val SLEEVE = 252f
private const val DISC = 226f

/** Sleeve and record colours, night and day, from the board. */
private class SleeveColors(
    val sl1: Color,
    val sl2: Color,
    val mark: Color,
    val vinyl: Color,
    val groove: Color,
    val label: Color,
    val labelInk: Color,
    val rim: Color,
)

private val NightSleeve = SleeveColors(
    sl1 = Color(0xFF4C43B8), sl2 = Color(0xFF1E1A5E), mark = Color(0xFF6FD9A8),
    vinyl = Color(0xFF161B36), groove = Color(0xFF232A55), label = Color(0xFF6FD9A8), labelInk = Color(0xFF0E1222), rim = Color(0x2EE8EAF2),
)
private val DaySleeve = SleeveColors(
    sl1 = Color(0xFFFD6E88), sl2 = Color(0xFF072D59), mark = Color(0xFFDBF2FC),
    vinyl = Color(0xFF072D59), groove = Color(0xFF0F3F78), label = Color(0xFFFD6E88), labelInk = Color(0xFF072D59), rim = Color(0x40FFFFFF),
)

@Composable
internal fun SleeveMusicCard(ui: HomeUi, shape: Shape, modifier: Modifier = Modifier) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val m = ui.music
    val reduced = rememberReducedMotion()
    Column(
        modifier.clip(shape).background(theme.surface).clickable(onClick = m.onOpen).padding(dims.d(3f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(1.5f)),
    ) {
        SourceChip(m)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val k = min(maxWidth.value / STAGE_W, maxHeight.value / STAGE_H).coerceAtLeast(0.3f)
            SleeveStage(m, k, reduced, Modifier.align(Alignment.CenterStart).size((STAGE_W * k).dp, (STAGE_H * k).dp))
        }
        m.artist?.let {
            Text(
                it,
                color = theme.muted,
                fontFamily = FontChoice.BarlowSans.family,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                EqBars(
                    playing = m.playing && !reduced,
                    color = if (m.playing) theme.accent else theme.surfaceHigh,
                    unit = dims.d(0.5f),
                    barWidth = 7.dp,
                    gap = 5.dp,
                )
            }
            if (m.controls) PlayKey(m.playing, m.onPlayPause)
        }
    }
}

/** Where the sound comes from: a Bluetooth mark and "BLUETOOTH · ONEPLUS", or the app's icon and name. */
@Composable
private fun SourceChip(m: MusicUi) {
    val theme = LocalDriveTheme.current
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val icon = m.sourceIcon
        when {
            m.bluetooth -> Box(Modifier.size(40.dp).clip(CircleShape).background(theme.banner), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = theme.onBanner, modifier = Modifier.size(22.dp))
            }
            icon != null -> Image(icon, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(22)))
            else -> Box(Modifier.size(40.dp).clip(CircleShape).background(theme.banner), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = theme.onBanner, modifier = Modifier.size(22.dp))
            }
        }
        Text(
            (m.sourceLine ?: stringResource(R.string.home_playing)).uppercase(),
            color = theme.muted,
            fontFamily = FontChoice.Barlow.family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 21.sp,
            letterSpacing = 0.07.em,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The tilted rig: the record behind (sliding out and turning) and the sleeve with the title in front. */
@Composable
private fun SleeveStage(m: MusicUi, k: Float, reduced: Boolean, modifier: Modifier) {
    val theme = LocalDriveTheme.current
    val colors = if (theme.isLight) DaySleeve else NightSleeve
    val density = LocalDensity.current
    // Out while playing, back in on pause (no slide with reduced motion).
    val out by animateFloatAsState(
        targetValue = if (m.playing) 1f else 0f,
        animationSpec = if (reduced || !SpeedoLive.enabled) snap() else tween(700, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)),
        label = "discSlide",
    )
    // The turn: 20 rpm, easing to a stop on pause; layer only, nothing recomposes.
    val spin = remember { DiscSpin() }
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(m.playing, reduced) {
        if (!SpeedoLive.enabled) return@LaunchedEffect
        var last = 0L
        while (!spin.resting(m.playing, reduced)) {
            withFrameNanos { t ->
                if (last != 0L) {
                    spin.step(((t - last) / 1_000_000_000f).coerceAtMost(0.05f), m.playing, reduced)
                    angle.floatValue = spin.angle
                }
                last = t
            }
        }
    }
    Box(
        modifier.graphicsLayer {
            rotationY = -10f
            rotationX = 4f
            transformOrigin = TransformOrigin(0.3f, 0.5f)
            cameraDistance = 900f * density.density
        },
    ) {
        Record(
            colors = colors,
            art = m.art,
            playing = m.playing && !reduced,
            angle = { angle.floatValue },
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(((14f + 18f + 100f * out) * k * density.density).toInt(), (16f * k * density.density).toInt()) }
                .size((DISC * k).dp),
        )
        Sleeve(m, colors, k, Modifier.offset(y = (4f * k).dp).size((SLEEVE * k).dp))
    }
}

/** The record: grooves, the label (the album art when there is some), its hole and two dots so the turn shows. */
@Composable
private fun Record(colors: SleeveColors, art: androidx.compose.ui.graphics.ImageBitmap?, playing: Boolean, angle: () -> Float, modifier: Modifier) {
    // A light sheen sweeping over the grooves while playing; it does not turn with the record.
    val sweep = if (playing) {
        rememberInfiniteTransition(label = "sheen").animateFloat(
            -28f, 28f, infiniteRepeatable(tween(5_000, easing = LinearEasing), RepeatMode.Reverse), label = "sheenSweep",
        )
    } else {
        null
    }
    Box(modifier) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle() }.clip(CircleShape)) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(colors.vinyl, r)
                // Grooves every 3 px from the label out to the rim.
                var gr = r * 0.44f
                val g = 1.dp.toPx()
                while (gr < r - 2f) {
                    drawCircle(colors.groove, gr, style = Stroke(g))
                    gr += 3.dp.toPx()
                }
                drawCircle(colors.vinyl, r * 0.83f, style = Stroke(r * 0.028f))
                drawCircle(colors.rim, r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                drawCircle(colors.labelInk, r * 0.438f)
                drawCircle(colors.label, r * 0.424f)
            }
            if (art != null) {
                Image(
                    art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.align(Alignment.Center).fillMaxSize(0.848f).clip(CircleShape),
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(colors.labelInk, r * 0.037f)
                drawCircle(colors.labelInk.copy(alpha = 0.85f), r * 0.09f, Offset(center.x, center.y - r * 0.43f))
                drawCircle(colors.labelInk.copy(alpha = 0.6f), r * 0.04f, Offset(center.x + r * 0.16f, center.y - r * 0.22f))
            }
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = sweep?.value ?: 0f }) {
            val r = size.minDimension / 2f
            val ring = r * 0.56f
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.06f to Color.Transparent,
                    0.11f to Color.White.copy(alpha = 0.26f),
                    0.16f to Color.Transparent,
                    0.56f to Color.Transparent,
                    0.61f to Color.White.copy(alpha = 0.16f),
                    0.66f to Color.Transparent,
                    1f to Color.Transparent,
                    center = center,
                ),
                r - ring / 2f,
                style = Stroke(ring),
            )
        }
    }
}

/** The sleeve: the board's gradient with rings in a corner, "Now spinning", the title and "Side A". */
@Composable
private fun Sleeve(m: MusicUi, colors: SleeveColors, k: Float, modifier: Modifier) {
    val corner = RoundedCornerShape((10f * k).dp)
    Box(modifier) {
        // Its darker spine, peeking out on the right.
        Box(Modifier.fillMaxSize().offset(x = (4f * k).dp).clip(corner).background(colors.sl2.copy(alpha = 1f).compositeDark()))
        Box(Modifier.fillMaxSize().clip(corner)) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // 150° like the board: from the top left towards the bottom right.
                val dx = 0.5f
                val dy = 0.866f
                val half = (w * dx + h * dy) / 2f
                drawRect(
                    Brush.linearGradient(
                        0f to colors.sl1,
                        0.75f to colors.sl2,
                        1f to colors.sl2,
                        start = Offset(center.x - dx * half, center.y - dy * half),
                        end = Offset(center.x + dx * half, center.y + dy * half),
                    ),
                )
                val s = w / SLEEVE
                val c = Offset(w, 0f)
                drawCircle(colors.mark, 63f * s, c, style = Stroke(6f * s))
                drawCircle(colors.mark, 91.5f * s, c, style = Stroke(3f * s))
                drawCircle(colors.mark, 119f * s, c, style = Stroke(2f * s))
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), startX = w - 10f * s, endX = w),
                    topLeft = Offset(w - 10f * s, 0f),
                    size = Size(10f * s, h),
                )
                // The front's shade, so the title reads on any colour.
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to Color.Black.copy(alpha = 0.3f),
                        1f to Color.Black.copy(alpha = 0.55f),
                        startY = h * 0.38f,
                        endY = h,
                    ),
                    topLeft = Offset(0f, h * 0.38f),
                    size = Size(w, h * 0.62f),
                )
            }
            Text(
                stringResource(R.string.music_sleeve_label).uppercase(),
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = FontChoice.Barlow.family,
                fontWeight = FontWeight.SemiBold,
                fontSize = (14f * k).coerceAtLeast(12f).sp,
                letterSpacing = 0.14.em,
                maxLines = 1,
                modifier = Modifier.padding(start = (16f * k).dp, top = (14f * k).dp),
            )
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = (16f * k).dp, end = (16f * k).dp, bottom = (14f * k).dp)) {
                FitTitle(m.title, maxSp = 34f * k, minSp = (22f * k).coerceAtLeast(18f), width = ((SLEEVE - 32f) * k).dp)
                Text(
                    stringResource(if (m.noTrackInfo) R.string.music_sleeve_phone else R.string.music_sleeve_side).uppercase(),
                    color = Color.White.copy(alpha = 0.8f),
                    fontFamily = FontChoice.Barlow.family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (13f * k).coerceAtLeast(12f).sp,
                    letterSpacing = 0.14.em,
                    maxLines = 1,
                    modifier = Modifier.padding(top = (6f * k).dp),
                )
            }
        }
    }
}

private fun Color.compositeDark(): Color = Color(red * 0.7f, green * 0.7f, blue * 0.7f, alpha)

/** The title on two lines at most: as big as fits between [minSp] and [maxSp], then "…". */
@Composable
private fun FitTitle(title: String, maxSp: Float, minSp: Float, width: Dp) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widthPx = with(density) { width.roundToPx() }.coerceAtLeast(1)
    val size = remember(title, widthPx, maxSp, minSp) {
        var s = maxSp
        while (s > minSp) {
            val r = measurer.measure(
                title,
                TextStyle(fontFamily = FontChoice.Barlow.family, fontWeight = FontWeight.Bold, fontSize = s.sp, lineHeight = (s * 1.04f).sp),
                constraints = Constraints(maxWidth = widthPx),
            )
            if (r.lineCount <= 2) break
            s -= 1f
        }
        s.coerceAtLeast(minSp)
    }
    Text(
        title,
        color = Color.White,
        fontFamily = FontChoice.Barlow.family,
        fontWeight = FontWeight.Bold,
        fontSize = size.sp,
        lineHeight = (size * 1.04f).sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The 80 px play / pause key in the accent; the triangle sits a little right so it looks centred. */
@Composable
private fun PlayKey(playing: Boolean, onClick: () -> Unit) {
    val theme = LocalDriveTheme.current
    Box(
        Modifier.size(80.dp).clip(CircleShape).background(theme.accent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(if (playing) R.string.music_pause else R.string.music_play),
            tint = theme.onAccent,
            modifier = Modifier.size(40.dp).offset(x = if (playing) 0.dp else 2.dp),
        )
    }
}
