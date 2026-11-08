package com.vikash.ampli.drive.ui

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.prefs.SpeedoStyle
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Settings › Speedometer: Neon Arc (default), Halo Ring, Road Strip and Retro Dial, as on the
 * approved speedometer board. One Canvas, Barlow Condensed numbers, no blur (glows are wide,
 * low-alpha strokes), the static parts drawn once to a bitmap (Retro Dial's dial face, Neon Arc's
 * numerals, Road Strip's road), at most 30 frames a second while moving and a few when parked,
 * nothing while the screen is off. The colour follows the speed band; over the limit it turns red
 * and pulses ("Dheere karo"). With reduced motion (Android's "Remove animations") nothing pulses
 * or moves: the red stays steady.
 *
 * The gauge sits on the left and the limit sign ("Seema 80"), trip, average and the GPS dot on the
 * right, next to the driver (right-hand drive); in a tall card they sit under the gauge.
 * Everything shown comes from the GPS and the camera database: before a fix the number is a dim
 * 0 and the GPS dot is grey; an unknown limit hides the sign.
 */

/** Android's animation scale is 0 ("Remove animations"): read again whenever the app resumes. */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var reduced by remember { mutableStateOf(readReducedMotion(context)) }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) reduced = readReducedMotion(context) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    return reduced
}

private fun readReducedMotion(context: android.content.Context): Boolean = try {
    android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (_: Exception) {
    false
}

/** Live frames on a real unit; one still frame under Robolectric (screen previews, tests). */
internal object SpeedoLive {
    val enabled: Boolean = android.os.Build.FINGERPRINT != "robolectric"
}

private const val MOVING_FRAME_NS = 33_000_000L
private const val PARKED_FRAME_NS = 66_000_000L

/** The design boxes of the four gauges, in the board's pixels: left, top, width, height. */
private val NEON_BOX = floatArrayOf(110f, 100f, 680f, 590f)
private val HALO_BOX = floatArrayOf(288f, 150f, 580f, 580f)
private val ROAD_BOX = floatArrayOf(580f, 100f, 590f, 600f)
private val RETRO_BOX = floatArrayOf(105f, 91f, 690f, 690f)

/** The info column on the board: 250 wide, 508 tall with the limit sign. */
private const val INFO_W = 250f
private const val INFO_H = 508f
private const val INFO_H_NO_SIGN = 282f

/** Barlow Condensed (numbers, labels) for the Canvas text, with a plain bold fallback. */
private class SpeedoText(context: android.content.Context) {
    private fun font(id: Int, fallback: Int): Typeface = try {
        androidx.core.content.res.ResourcesCompat.getFont(context, id) ?: Typeface.create(Typeface.DEFAULT, fallback)
    } catch (_: Exception) {
        Typeface.create(Typeface.DEFAULT, fallback)
    }

    val bold: Typeface = font(R.font.barlow_condensed_bold, Typeface.BOLD)
    val semi: Typeface = font(R.font.barlow_condensed_semibold, Typeface.NORMAL)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = Rect()

    /** [s] centred on ([x], [y]) by its own ink (like the board), or starting / ending at [x]. */
    fun draw(nc: android.graphics.Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean, align: Paint.Align = Paint.Align.CENTER) {
        paint.typeface = if (bold) this.bold else semi
        paint.textSize = size
        paint.color = color
        paint.textAlign = align
        paint.getTextBounds(s, 0, s.length, bounds)
        nc.drawText(s, x, y - bounds.exactCenterY(), paint)
    }

    fun width(s: String, size: Float, bold: Boolean): Float {
        paint.typeface = if (bold) this.bold else semi
        paint.textSize = size
        return paint.measureText(s)
    }
}

/** A bitmap drawn once and reused until [key] changes (size, limit, day or night). */
private class Layer {
    var key: Any? = null
    var bitmap: ImageBitmap? = null

    fun get(key: Any, w: Int, h: Int, density: androidx.compose.ui.unit.Density, draw: DrawScope.() -> Unit): ImageBitmap? {
        if (w <= 0 || h <= 0) return null
        if (key != this.key || bitmap == null) {
            val bmp = ImageBitmap(w, h)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, ComposeCanvas(bmp), Size(w.toFloat(), h.toFloat()), draw)
            bitmap = bmp
            this.key = key
        }
        return bitmap
    }
}

private fun Color.mixWith(other: Color, t: Float): Color =
    if (t <= 0f) this else Color(red + (other.red - red) * t, green + (other.green - green) * t, blue + (other.blue - blue) * t, alpha + (other.alpha - alpha) * t)

/** The words the speedometer shows, read once per composition. */
private class SpeedoWords(
    val unit: String,
    val bands: Array<String>,
    val limitCaption: String?,
    val trip: String,
    val avg: String,
    val gps: String,
    val km: String,
)

/**
 * The speedometer in [style], filling [modifier]. [speedKmh] is the smoothed GPS speed (null
 * before a fix), [limit] the road's limit (null when unknown), [tripKm] and [avgKmh] this trip's
 * distance and moving average (null until known), [gpsFix] whether a fresh GPS fix drives it and
 * [accuracyM] that fix's accuracy.
 */
@Composable
internal fun Speedometer(
    style: SpeedoStyle,
    speedKmh: Float?,
    limit: Int?,
    tripKm: Float?,
    avgKmh: Float?,
    gpsFix: Boolean,
    accuracyM: Int?,
    modifier: Modifier = Modifier,
) {
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val reduced = rememberReducedMotion()
    val target by rememberUpdatedState(speedKmh ?: 0f)
    val limitNow by rememberUpdatedState(limit)
    val reducedNow by rememberUpdatedState(reduced)
    val motion = remember { SpeedoMotion().also { it.snap(speedKmh ?: 0f, limit) } }
    val frame = remember { mutableLongStateOf(0L) }
    val text = remember { SpeedoText(context) }
    val numeralLayer = remember { Layer() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        if (!SpeedoLive.enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = 0L
            while (true) {
                val settled = motion.settled(target, limitNow)
                if (reducedNow && settled) {
                    // Nothing moves with reduced motion: redraw only when the speed changes.
                    motion.snap(target, limitNow)
                    frame.longValue++
                    delay(250)
                    last = 0L
                    continue
                }
                withFrameNanos { t ->
                    if (last == 0L) {
                        last = t
                        return@withFrameNanos
                    }
                    val parked = target < 0.5f && settled && !SpeedoMath.over(motion.speed, limitNow)
                    val gap = if (parked) PARKED_FRAME_NS else MOVING_FRAME_NS
                    if (t - last >= gap) {
                        val dt = ((t - last) / 1_000_000_000f).coerceAtMost(0.1f)
                        last = t
                        motion.step(dt, target, limitNow, reducedNow)
                        frame.longValue++
                    }
                }
            }
        }
    }
    val words = SpeedoWords(
        unit = stringResource(R.string.speed_unit),
        bands = arrayOf(
            stringResource(R.string.speedo_band_calm),
            stringResource(R.string.speedo_band_cruise),
            stringResource(R.string.speedo_band_fast),
            stringResource(R.string.speedo_band_over),
        ),
        limitCaption = limit?.let { stringResource(R.string.speedo_limit_caption, it) },
        trip = stringResource(R.string.speedo_trip),
        avg = stringResource(R.string.speedo_avg),
        gps = stringResource(R.string.speedo_gps),
        km = stringResource(R.string.speedo_km),
    )
    val tripText = tripKm?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "0.0"
    val avgText = avgKmh?.roundToInt()?.toString() ?: "–"
    val accText = if (gpsFix && accuracyM != null) "±$accuracyM m" else ""
    val numbers = remember { Array(400) { it.toString() } }
    val light = theme.isLight
    val pal = SpeedoMath.palette(light)
    val gk = SpeedoMath.glow(light)
    val ink = theme.text
    val dim = theme.muted
    val track = theme.track
    val panel = theme.surface
    val bg = theme.background
    val over = Color(pal.over)
    val calm = Color(pal.calm)
    val noFix = speedKmh == null
    val minLabelPx = with(density) { 13.sp.toPx() }

    Canvas(modifier.fillMaxSize()) {
        frame.longValue // redraw on every animation frame
        if (!SpeedoLive.enabled || reduced) motion.snap(target, limit)
        val v = motion.speed
        val shown = v.roundToInt().coerceIn(0, numbers.size - 1)
        val col = Color(SpeedoMath.bandColor(v, motion.overT, pal))
        val band = SpeedoMath.band(v, limit)
        val p = SpeedoMath.pulse(motion.clock, reduced)
        val w = size.width
        val h = size.height
        val landscape = w >= h * 1.15f
        // Gauge and info areas.
        val infoW: Float
        val infoH: Float
        val gx: Float
        val gy: Float
        val gw: Float
        val gh: Float
        val ix: Float
        val iy: Float
        if (landscape) {
            infoW = min(w * 0.36f, h * 0.55f)
            infoH = h
            gx = 0f; gy = 0f; gw = w - infoW; gh = h
            ix = w - infoW; iy = 0f
        } else {
            infoH = min(h * 0.4f, w * 0.62f)
            infoW = w
            gx = 0f; gy = 0f; gw = w; gh = h - infoH
            ix = 0f; iy = h - infoH
        }
        val box = when (style) {
            SpeedoStyle.NeonArc -> NEON_BOX
            SpeedoStyle.HaloRing -> HALO_BOX
            SpeedoStyle.RoadStrip -> ROAD_BOX
            SpeedoStyle.RetroDial -> RETRO_BOX
        }
        val s = min(gw / box[2], gh / box[3]) * 0.96f
        val ox = gx + (gw - box[2] * s) / 2f - box[0] * s
        val oy = gy + (gh - box[3] * s) / 2f - box[1] * s
        val numberColor = if (noFix) ink.copy(alpha = 0.35f) else ink
        val bandText = words.bands[band.ordinal]
        when (style) {
            SpeedoStyle.NeonArc -> drawNeon(s, ox, oy, v, limit, col, gk, track, over, dim, numberColor, words.unit, bandText, numbers[shown], motion.overT, p, text, numeralLayer, light)
            SpeedoStyle.HaloRing -> drawHalo(s, ox, oy, v, limit, col, gk, track, over, dim, numberColor, words.unit, bandText, numbers[shown], motion.overT, p, motion.clock, reduced, text)
            SpeedoStyle.RoadStrip -> drawRoad(s, ox, oy, v, limit, col, gk, track, over, ink, dim, numberColor, words.unit, bandText, numbers[shown], motion.overT, p, motion.dash, reduced, text, numeralLayer, light, words.limitCaption)
            SpeedoStyle.RetroDial -> drawRetro(s, ox, oy, v, motion.needle, limit, col, gk, track, over, ink, dim, bg, numberColor, words.unit, bandText, numbers[shown], motion.overT, p, text, numeralLayer, light)
        }
        // The limit sign, trip, average and GPS.
        val ripple = if (gpsFix) SpeedoMath.ripple(motion.clock, reduced) else 0f
        drawInfo(
            ix, iy, infoW, infoH, landscape, limit, words, tripText, avgText, accText, gpsFix, ripple,
            motion.overT, p, panel, track, ink, dim, over, calm, text, minLabelPx,
        )
    }
}

// ------------------------------------------------------------------------------------ helpers

private fun DrawScope.inDesign(s: Float, ox: Float, oy: Float, block: DrawScope.() -> Unit) {
    withTransform({
        translate(ox, oy)
        scale(s, s, Offset.Zero)
    }, block)
}

private fun DrawScope.arc(color: Color, cx: Float, cy: Float, r: Float, startDeg: Float, sweepDeg: Float, width: Float, cap: StrokeCap = StrokeCap.Butt) {
    drawArc(color, startDeg, sweepDeg, false, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = Stroke(width, cap = cap))
}

/** The band pill under the number ("Cruise"); it glows with the pulse over the limit. */
private fun DrawScope.pill(cx: Float, y: Float, label: String, col: Color, overT: Float, p: Float, text: SpeedoText) {
    val tw = text.width(label, 30f, true) + 44f
    val ph = 50f
    val tl = Offset(cx - tw / 2f, y - ph / 2f)
    drawRoundRect(col.copy(alpha = (0.16f + 0.22f * overT * p).coerceIn(0f, 1f)), tl, Size(tw, ph), CornerRadius(25f))
    drawRoundRect(col.copy(alpha = 0.85f), tl, Size(tw, ph), CornerRadius(25f), style = Stroke(2f))
    drawIntoCanvas { text.draw(it.nativeCanvas, label, cx, y, 30f, col.toArgb(), true) }
}

private fun rad(deg: Float): Float = Math.toRadians(deg.toDouble()).toFloat()

// ------------------------------------------------------------------------------------ Neon Arc

private const val NEON_CX = 450f
private const val NEON_CY = 440f
private const val NEON_R = 286f

private fun DrawScope.drawNeon(
    s: Float, ox: Float, oy: Float, v: Float, limit: Int?, col: Color, gk: Float, track: Color, over: Color, dim: Color,
    numberColor: Color, unit: String, band: String, number: String, overT: Float, p: Float, text: SpeedoText, layer: Layer, light: Boolean,
) {
    val cx = NEON_CX
    val cy = NEON_CY
    val r = NEON_R
    val a0 = 135f
    val sw = 270f
    val n = SpeedoMath.NEON_SEGMENTS
    val f = SpeedoMath.share(v)
    val step = sw / n
    val ah = a0 + sw * f
    // Numerals and the limit marker: drawn once.
    val bx = NEON_BOX[0]
    val by = NEON_BOX[1]
    val lw = (NEON_BOX[2] * s).toInt()
    val lh = (NEON_BOX[3] * s).toInt()
    val bmp = layer.get(listOf("neon", lw, lh, limit, light, dim.toArgb()), lw, lh, this) {
        withTransform({
            scale(s, s, Offset.Zero)
            translate(-bx, -by)
        }) {
            drawIntoCanvas { c ->
                for (k in 0..7) {
                    val value = k * 20
                    val a = rad(a0 + sw * value / SpeedoMath.VMAX)
                    text.draw(c.nativeCanvas, value.toString(), cx + cos(a) * (r - 64f), cy + sin(a) * (r - 64f), 28f, dim.toArgb(), false)
                }
            }
            if (limit != null) {
                val al = rad(a0 + sw * min(limit.toFloat(), SpeedoMath.VMAX) / SpeedoMath.VMAX)
                val r1 = r + 28f
                val r2 = r + 48f
                val path = Path().apply {
                    moveTo(cx + cos(al) * r1, cy + sin(al) * r1)
                    lineTo(cx + cos(al - 0.035f) * r2, cy + sin(al - 0.035f) * r2)
                    lineTo(cx + cos(al + 0.035f) * r2, cy + sin(al + 0.035f) * r2)
                    close()
                }
                drawPath(path, over)
            }
        }
    }
    inDesign(s, ox, oy) {
        if (f > 0.002f) arc(col.copy(alpha = ((0.07f + 0.08f * f) * gk).coerceIn(0f, 1f)), cx, cy, r, a0, ah - a0, 84f)
        val white = Color.White
        val overTrack = track.mixWith(over, 0.28f)
        for (i in 0 until n) {
            val start = a0 + i * step + step * 0.16f
            val heat = SpeedoMath.neonHeat(i, v)
            val c = when {
                heat != null -> col.mixWith(white, 0.28f * heat * gk).copy(alpha = 0.5f + 0.5f * heat)
                limit != null && SpeedoMath.slotValue(i, n) > limit -> overTrack
                else -> track
            }
            arc(c, cx, cy, r, start, step * 0.68f, 40f)
        }
        if (f > 0.002f) {
            // The comet's tail and its bright head.
            for (k in 0 until 14) {
                val t = 1f - k / 14f
                val e = ah - k * step * 0.7f
                if (e <= a0) break
                val st = max(a0, e - step * 0.7f)
                arc(col.copy(alpha = (0.16f * t * gk).coerceIn(0f, 1f)), cx, cy, r, st, e - st, 40f + 26f * t)
            }
            val ha = rad(ah)
            val hx = cx + cos(ha) * r
            val hy = cy + sin(ha) * r
            drawCircle(
                Brush.radialGradient(
                    0f to col.mixWith(white, 0.6f).copy(alpha = 0.95f),
                    0.35f to col.copy(alpha = (0.55f * gk + 0.2f).coerceIn(0f, 1f)),
                    1f to col.copy(alpha = 0f),
                    center = Offset(hx, hy),
                    radius = 52f,
                ),
                52f,
                Offset(hx, hy),
            )
        }
    }
    if (bmp != null) drawImage(bmp, Offset(ox + bx * s, oy + by * s))
    inDesign(s, ox, oy) {
        drawIntoCanvas { c ->
            text.draw(c.nativeCanvas, number, cx, cy - 22f, 230f, numberColor.toArgb(), true)
            text.draw(c.nativeCanvas, unit, cx, cy + 84f, 34f, dim.toArgb(), false)
        }
        pill(cx, cy + 150f, band, col, overT, p, text)
    }
}

// ------------------------------------------------------------------------------------ Halo Ring

private fun DrawScope.drawHalo(
    s: Float, ox: Float, oy: Float, v: Float, limit: Int?, col: Color, gk: Float, track: Color, over: Color, dim: Color,
    numberColor: Color, unit: String, band: String, number: String, overT: Float, p: Float, clock: Float, reduced: Boolean, text: SpeedoText,
) {
    val cx = 578f
    val cy = 440f
    val r = 232f
    val f = SpeedoMath.share(v)
    val breathe = if (reduced) 0f else 0.06f * sin(clock * 2.5f)
    val w = 12f + 30f * f
    val a0 = -90f
    inDesign(s, ox, oy) {
        arc(track, cx, cy, r, 0f, 360f, 12f)
        val halo = ((0.05f + 0.14f * f + breathe * f + 0.18f * overT * p) * gk).coerceIn(0f, 1f)
        arc(col.copy(alpha = halo * 0.6f), cx, cy, r, 0f, 360f, w + 70f)
        if (f > 0.003f) {
            val sweep = 360f * f
            arc(col.copy(alpha = ((0.12f + 0.18f * f) * gk).coerceIn(0f, 1f)), cx, cy, r, a0, sweep, w + 40f, StrokeCap.Round)
            arc(col.copy(alpha = ((0.25f + 0.25f * f) * gk).coerceIn(0f, 1f)), cx, cy, r, a0, sweep, w + 14f, StrokeCap.Round)
            arc(col.mixWith(Color.White, 0.2f * gk), cx, cy, r, a0, sweep, w, StrokeCap.Round)
        }
        if (limit != null) {
            val al = rad(a0 + 360f * min(limit.toFloat(), SpeedoMath.VMAX) / SpeedoMath.VMAX)
            val dot = Offset(cx + cos(al) * r, cy + sin(al) * r)
            drawCircle(Color.White, 7f, dot)
            drawCircle(over, 7f, dot, style = Stroke(4f))
            drawIntoCanvas { c ->
                text.draw(c.nativeCanvas, limit.toString(), cx + cos(al) * (r + w / 2f + 30f), cy + sin(al) * (r + w / 2f + 30f), 26f, over.toArgb(), true)
            }
        }
        drawIntoCanvas { c ->
            text.draw(c.nativeCanvas, number, cx, cy - 24f, 240f, numberColor.toArgb(), true)
            text.draw(c.nativeCanvas, unit, cx, cy + 86f, 34f, dim.toArgb(), false)
        }
        pill(cx, cy + 148f, band, col, overT, p, text)
    }
}

// ------------------------------------------------------------------------------------ Road Strip

private const val ROAD_TOP = 410f
private const val ROAD_BOT = 690f
private const val ROAD_BW = 480f
private const val ROAD_TW = 110f
private const val ROAD_CX = 896f

private fun DrawScope.drawRoad(
    s: Float, ox: Float, oy: Float, v: Float, limit: Int?, col: Color, gk: Float, track: Color, over: Color, ink: Color, dim: Color,
    numberColor: Color, unit: String, band: String, number: String, overT: Float, p: Float, dash: Float, reduced: Boolean,
    text: SpeedoText, layer: Layer, light: Boolean, limitCaption: String?,
) {
    val pcx = ROAD_CX
    val top = ROAD_TOP
    val bot = ROAD_BOT
    val bw = ROAD_BW
    val tw = ROAD_TW
    val f = SpeedoMath.share(v)
    fun ys(x: Float) = SpeedoMath.roadY(x, top, bot)
    fun half(y: Float) = SpeedoMath.roadHalf(y, top, bot, bw, tw)
    // The road and its speed ticks: drawn once.
    val bx = ROAD_BOX[0]
    val by = ROAD_BOX[1]
    val lw = (ROAD_BOX[2] * s).toInt()
    val lh = (ROAD_BOX[3] * s).toInt()
    val bmp = layer.get(listOf("road", lw, lh, light, track.toArgb(), dim.toArgb()), lw, lh, this) {
        withTransform({
            scale(s, s, Offset.Zero)
            translate(-bx, -by)
        }) {
            val road = Path().apply {
                moveTo(pcx - bw / 2f, bot); lineTo(pcx - tw / 2f, top); lineTo(pcx + tw / 2f, top); lineTo(pcx + bw / 2f, bot); close()
            }
            drawPath(road, track.copy(alpha = 0.7f))
            for (tick in intArrayOf(0, 40, 80, 120)) {
                val y = ys(tick / SpeedoMath.VMAX)
                val hh = half(y)
                drawIntoCanvas { c -> text.draw(c.nativeCanvas, tick.toString(), pcx - hh - 26f, y, 22f, dim.toArgb(), false, Paint.Align.RIGHT) }
                drawLine(dim, Offset(pcx - hh - 18f, y), Offset(pcx - hh - 6f, y), 3f)
            }
        }
    }
    if (bmp != null) drawImage(bmp, Offset(ox + bx * s, oy + by * s))
    inDesign(s, ox, oy) {
        drawIntoCanvas { c ->
            text.draw(c.nativeCanvas, number, pcx, 206f, 200f, numberColor.toArgb(), true)
            text.draw(c.nativeCanvas, unit, pcx, 302f, 32f, dim.toArgb(), false)
        }
        pill(pcx, 356f, band, col, overT, p, text)
        if (f > 0.002f) {
            val yf = ys(f)
            val hf = half(yf)
            val fill = Path().apply {
                moveTo(pcx - bw / 2f, bot); lineTo(pcx - hf, yf); lineTo(pcx + hf, yf); lineTo(pcx + bw / 2f, bot); close()
            }
            drawPath(
                fill,
                Brush.verticalGradient(
                    0f to col.copy(alpha = (0.6f * gk + 0.25f).coerceIn(0f, 1f)),
                    1f to col.copy(alpha = (0.18f * gk + 0.1f).coerceIn(0f, 1f)),
                    startY = yf,
                    endY = bot,
                ),
            )
            for (sgn in intArrayOf(-1, 1)) {
                val a = Offset(pcx + sgn * bw / 2f, bot)
                val b = Offset(pcx + sgn * hf, yf)
                drawLine(col.copy(alpha = (0.22f * gk).coerceIn(0f, 1f)), a, b, 16f, StrokeCap.Round)
                drawLine(col, a, b, 5f, StrokeCap.Round)
            }
            drawLine(col.mixWith(Color.White, 0.4f * gk), Offset(pcx - hf, yf), Offset(pcx + hf, yf), 6f, StrokeCap.Round)
        }
        // Lane dashes, scrolling with speed (still with reduced motion).
        val doff = if (reduced) 0f else (dash / 900f) % 0.125f
        val dashColor = ink.copy(alpha = 0.55f)
        var st = -0.125f + doff
        while (st < 1f) {
            val s0 = max(0f, st)
            val s1 = min(1f, st + 0.06f)
            if (s1 > s0) {
                val y0 = ys(s0)
                val y1 = ys(s1)
                val w0 = 7f * (half(y0) / (bw / 2f)) + 1f
                val w1 = 7f * (half(y1) / (bw / 2f)) + 1f
                val q = Path().apply { moveTo(pcx - w0, y0); lineTo(pcx - w1, y1); lineTo(pcx + w1, y1); lineTo(pcx + w0, y0); close() }
                drawPath(q, dashColor)
            }
            st += 0.125f
        }
        // The limit painted on the road as a line: "Seema 80".
        if (limit != null) {
            val yl = ys(SpeedoMath.share(limit.toFloat()))
            val hl = half(yl)
            drawLine(over, Offset(pcx - hl - 4f, yl), Offset(pcx + hl + 4f, yl), 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            if (limitCaption != null) {
                drawIntoCanvas { c -> text.draw(c.nativeCanvas, limitCaption, pcx + hl + 16f, yl, 24f, over.toArgb(), true, Paint.Align.LEFT) }
            }
        }
    }
}

// ------------------------------------------------------------------------------------ Retro Dial

private fun DrawScope.drawRetro(
    s: Float, ox: Float, oy: Float, v: Float, needle: Float, limit: Int?, col: Color, gk: Float, track: Color, over: Color, ink: Color, dim: Color,
    bg: Color, numberColor: Color, unit: String, band: String, number: String, overT: Float, p: Float, text: SpeedoText, layer: Layer, light: Boolean,
) {
    val cx = 450f
    val cy = 436f
    val r = 296f
    val a0 = 150f
    val sw = 240f
    val f = SpeedoMath.share(v)
    val face = if (light) Color(0xFFF4FBFE) else Color(0xFF0A0D1A)
    // The dial face's ticks and numerals: drawn once to a bitmap (about 0.4 MB on the TS7).
    val bx = RETRO_BOX[0]
    val by = RETRO_BOX[1]
    val lw = (RETRO_BOX[2] * s).toInt()
    val lh = (RETRO_BOX[3] * s).toInt()
    val bmp = layer.get(listOf("retro", lw, lh, limit, light, ink.toArgb()), lw, lh, this) {
        withTransform({
            scale(s, s, Offset.Zero)
            translate(-bx, -by)
        }) {
            var value = 0
            while (value <= 140) {
                val a = rad(a0 + sw * value / SpeedoMath.VMAX)
                val maj = value % 20 == 0
                val mid = value % 10 == 0
                val len = if (maj) 30f else if (mid) 20f else 11f
                val c = if (limit != null && value > limit) over.copy(alpha = 0.85f) else ink.copy(alpha = if (maj) 0.9f else 0.5f)
                drawLine(
                    c,
                    Offset(cx + cos(a) * (r - 14f), cy + sin(a) * (r - 14f)),
                    Offset(cx + cos(a) * (r - 14f - len), cy + sin(a) * (r - 14f - len)),
                    if (maj) 5f else if (mid) 3f else 2f,
                )
                if (maj) {
                    drawIntoCanvas { cv -> text.draw(cv.nativeCanvas, value.toString(), cx + cos(a) * (r - 74f), cy + sin(a) * (r - 74f), 34f, ink.copy(alpha = 0.9f).toArgb(), true) }
                }
                value += 5
            }
        }
    }
    inDesign(s, ox, oy) {
        // The LED ring: lit to the speed in the band colour; the over-limit LEDs red, blinking when reached.
        for (i in 0 until SpeedoMath.RETRO_LEDS) {
            val lv = SpeedoMath.slotValue(i, SpeedoMath.RETRO_LEDS)
            val a = rad(a0 + sw * lv / SpeedoMath.VMAX)
            val pos = Offset(cx + cos(a) * (r + 30f), cy + sin(a) * (r + 30f))
            val beyond = limit != null && lv > limit
            val lit = lv <= v
            val c = if (beyond) over else col
            val al = if (beyond) (if (lit) 0.45f + 0.55f * p else 0.25f) else (if (lit) 1f else 0.12f)
            if (lit) drawCircle(c.copy(alpha = (0.22f * al * gk).coerceIn(0f, 1f)), 15f, pos)
            drawCircle(if (lit || beyond) c.copy(alpha = al.coerceIn(0f, 1f)) else track, 7.5f, pos)
        }
        // The face, backlit in the band colour.
        drawCircle(track, r + 6f, Offset(cx, cy))
        drawCircle(face, r, Offset(cx, cy))
        drawCircle(
            Brush.radialGradient(
                0f to col.copy(alpha = ((0.10f + 0.22f * f + 0.15f * overT * p) * gk).coerceIn(0f, 1f)),
                0.75f to col.copy(alpha = ((0.04f + 0.08f * f) * gk).coerceIn(0f, 1f)),
                1f to col.copy(alpha = 0f),
                center = Offset(cx, cy),
                radius = r,
            ),
            r,
            Offset(cx, cy),
        )
    }
    if (bmp != null) drawImage(bmp, Offset(ox + bx * s, oy + by * s))
    inDesign(s, ox, oy) {
        // The digital window under the hub.
        drawRoundRect(bg.copy(alpha = 0.85f), Offset(cx - 128f, cy + 108f), Size(256f, 140f), CornerRadius(22f))
        drawRoundRect(col.copy(alpha = 0.6f), Offset(cx - 128f, cy + 108f), Size(256f, 140f), CornerRadius(22f), style = Stroke(2f))
        drawIntoCanvas { c ->
            text.draw(c.nativeCanvas, number, cx, cy + 168f, 124f, numberColor.toArgb(), true)
            text.draw(c.nativeCanvas, unit, cx, cy + 226f, 24f, dim.toArgb(), false)
            text.draw(c.nativeCanvas, band, cx, cy - 96f, 28f, col.toArgb(), true)
        }
        // The needle (a spring, so it settles with a slight bounce) and the hub.
        val na = rad(a0 + sw * (needle / SpeedoMath.VMAX).coerceIn(-0.01f, 1.02f))
        rotateRad(na, Offset(cx, cy)) {
            val path = Path().apply {
                moveTo(cx - 34f, cy - 9f); lineTo(cx + r - 30f, cy - 2.5f); lineTo(cx + r - 30f, cy + 2.5f); lineTo(cx - 34f, cy + 9f); close()
            }
            drawPath(path, col.copy(alpha = (0.18f * gk).coerceIn(0f, 1f)), style = Stroke(16f))
            drawPath(path, col.mixWith(Color.White, 0.25f * gk))
        }
        drawCircle(track, 26f, Offset(cx, cy))
        drawCircle(ink, 13f, Offset(cx, cy))
    }
}

// ------------------------------------------------------------------------------------ info

/**
 * The limit sign with "Seema 80", the trip, the average and the GPS dot: a column right of the
 * gauge (or a row under it in a tall card), in the board's chips, or plain lines when the card is
 * too small for chips to stay readable.
 */
private fun DrawScope.drawInfo(
    x: Float, y: Float, w: Float, h: Float, column: Boolean, limit: Int?, words: SpeedoWords,
    trip: String, avg: String, acc: String, gpsFix: Boolean, ripple: Float, overT: Float, p: Float,
    panel: Color, track: Color, ink: Color, dim: Color, over: Color, calm: Color, text: SpeedoText, minLabelPx: Float,
) {
    if (column) {
        val needH = if (limit != null) INFO_H else INFO_H_NO_SIGN
        val si = min(w / INFO_W, h * 0.94f / needH)
        if (21f * si >= minLabelPx) {
            val cw = INFO_W * si
            val left = x + (w - cw) / 2f
            var top = y + (h - needH * si) / 2f
            if (limit != null) {
                limitSign(left + cw / 2f, top + 78f * si, 78f * si, limit, overT, p, over, text)
                words.limitCaption?.let { cap -> drawIntoCanvas { text.draw(it.nativeCanvas, cap, left + cw / 2f, top + 186f * si, 26f * si, dim.toArgb(), false) } }
                top += 226f * si
            }
            chip(left, top, cw, 92f * si, si, words.trip, trip, words.km, panel, track, ink, dim, text)
            chip(left, top + 108f * si, cw, 92f * si, si, words.avg, avg, words.unit, panel, track, ink, dim, text)
            gpsChip(left, top + 216f * si, cw, 66f * si, si, words.gps, acc, gpsFix, ripple, panel, track, ink, dim, calm, text)
        } else {
            infoLines(x, y, w, h, limit, words, trip, avg, acc, gpsFix, ripple, overT, p, ink, dim, over, calm, text, minLabelPx)
        }
        return
    }
    // A row under the gauge: chips on the left, the sign on the right (the driver's side).
    val signW = if (limit != null) w * 0.38f else 0f
    val chipsW = w - signW
    val gap = 12f
    val sc = min((chipsW - 2 * gap) / INFO_W, (h - 2 * gap) / (92f + 92f + 66f + 2 * 16f))
    if (21f * sc >= minLabelPx) {
        val cw = INFO_W * sc
        val left = x + gap + (chipsW - 2 * gap - cw) / 2f
        val top = y + (h - (92f + 92f + 66f + 32f) * sc) / 2f
        chip(left, top, cw, 92f * sc, sc, words.trip, trip, words.km, panel, track, ink, dim, text)
        chip(left, top + 108f * sc, cw, 92f * sc, sc, words.avg, avg, words.unit, panel, track, ink, dim, text)
        gpsChip(left, top + 216f * sc, cw, 66f * sc, sc, words.gps, acc, gpsFix, ripple, panel, track, ink, dim, calm, text)
    } else {
        infoLines(x, y, chipsW, h, null, words, trip, avg, acc, gpsFix, ripple, overT, p, ink, dim, over, calm, text, minLabelPx)
    }
    if (limit != null) {
        val r = min(signW * 0.34f, h * 0.3f)
        val cx = x + chipsW + signW / 2f
        val cy = y + h / 2f - r * 0.35f
        limitSign(cx, cy, r, limit, overT, p, over, text)
        words.limitCaption?.let { cap ->
            drawIntoCanvas { text.draw(it.nativeCanvas, cap, cx, cy + r + max(minLabelPx * 1.2f, r * 0.42f), max(minLabelPx * 1.15f, r * 0.33f), dim.toArgb(), false) }
        }
    }
}

/** The small-card fallback: the sign, then "Trip 23.4 km", "Avg 41 km/h" and the GPS dot as text lines. */
private fun DrawScope.infoLines(
    x: Float, y: Float, w: Float, h: Float, limit: Int?, words: SpeedoWords, trip: String, avg: String, acc: String,
    gpsFix: Boolean, ripple: Float, overT: Float, p: Float, ink: Color, dim: Color, over: Color, calm: Color, text: SpeedoText, minLabelPx: Float,
) {
    val value = minLabelPx * 1.5f
    val label = minLabelPx
    val row = value * 1.45f
    val signR = if (limit != null) min(w * 0.3f, (h - row * 3.6f) * 0.36f).coerceAtLeast(0f) else 0f
    val signBlock = if (limit != null && signR > 12f) signR * 2f + label * 1.8f else 0f
    var top = y + (h - signBlock - row * 3f) / 2f
    val cx = x + w / 2f
    if (signBlock > 0f && limit != null) {
        limitSign(cx, top + signR, signR, limit, overT, p, over, text)
        words.limitCaption?.let { cap -> drawIntoCanvas { text.draw(it.nativeCanvas, cap, cx, top + signR * 2f + label * 0.9f, label, dim.toArgb(), false) } }
        top += signBlock
    }
    val left = x + max(8f, (w - text.width("${words.avg}  888 ${words.unit}", value, true)) / 2f)
    drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        val lw = max(text.width(words.trip.uppercase(), label, false), text.width(words.avg.uppercase(), label, false)) + label * 0.6f
        text.draw(nc, words.trip.uppercase(), left, top + row * 0.5f, label, dim.toArgb(), false, Paint.Align.LEFT)
        text.draw(nc, "$trip ${words.km}", left + lw, top + row * 0.5f, value, ink.toArgb(), true, Paint.Align.LEFT)
        text.draw(nc, words.avg.uppercase(), left, top + row * 1.5f, label, dim.toArgb(), false, Paint.Align.LEFT)
        text.draw(nc, "$avg ${words.unit}", left + lw, top + row * 1.5f, value, ink.toArgb(), true, Paint.Align.LEFT)
    }
    val gy = top + row * 2.5f
    val dot = label * 0.42f
    if (gpsFix && ripple > 0f) drawCircle(calm.copy(alpha = 0.35f * (1f - ripple)), dot + dot * 1.7f * ripple, Offset(left + dot, gy))
    drawCircle(if (gpsFix) calm else dim.copy(alpha = 0.5f), dot, Offset(left + dot, gy))
    drawIntoCanvas { c ->
        text.draw(c.nativeCanvas, words.gps, left + dot * 2f + label * 0.5f, gy, value * 0.9f, ink.toArgb(), true, Paint.Align.LEFT)
        if (acc.isNotEmpty()) text.draw(c.nativeCanvas, acc, left + dot * 2f + label * 0.5f + text.width(words.gps, value * 0.9f, true) + label * 0.6f, gy, label, dim.toArgb(), false, Paint.Align.LEFT)
    }
}

/** The round limit sign: white with a red ring (the speedometer's red, never the SOS red); a red glow pulses over the limit. */
private fun DrawScope.limitDisc(cx: Float, cy: Float, r: Float, overT: Float, p: Float, over: Color) {
    if (overT > 0.01f) drawCircle(over.copy(alpha = (0.28f * overT * (0.4f + 0.6f * p)).coerceIn(0f, 1f)), r + r * 0.13f + r * 0.13f * p, Offset(cx, cy))
    drawCircle(Color.White, r, Offset(cx, cy))
    drawCircle(over, r - r * 0.085f, Offset(cx, cy), style = Stroke(r * 0.17f))
}

private fun DrawScope.limitSign(cx: Float, cy: Float, r: Float, limit: Int, overT: Float, p: Float, over: Color, text: SpeedoText) {
    limitDisc(cx, cy, r, overT, p, over)
    drawIntoCanvas { text.draw(it.nativeCanvas, limit.toString(), cx, cy, r * (if (limit >= 100) 0.78f else 0.95f), 0xFF111522.toInt(), true) }
}

private fun DrawScope.chip(
    x: Float, y: Float, w: Float, h: Float, k: Float, label: String, value: String, unit: String?,
    panel: Color, track: Color, ink: Color, dim: Color, text: SpeedoText,
) {
    drawRoundRect(panel, Offset(x, y), Size(w, h), CornerRadius(18f * k))
    drawRoundRect(track, Offset(x, y), Size(w, h), CornerRadius(18f * k), style = Stroke(1.5f * k))
    drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        text.draw(nc, label.uppercase(), x + 20f * k, y + 24f * k, 21f * k, dim.toArgb(), false, Paint.Align.LEFT)
        text.draw(nc, value, x + 20f * k, y + h - 28f * k, 40f * k, ink.toArgb(), true, Paint.Align.LEFT)
        if (unit != null) {
            val vw = text.width(value, 40f * k, true)
            text.draw(nc, unit, x + 28f * k + vw, y + h - 26f * k, 22f * k, dim.toArgb(), false, Paint.Align.LEFT)
        }
    }
}

private fun DrawScope.gpsChip(
    x: Float, y: Float, w: Float, h: Float, k: Float, label: String, acc: String, fix: Boolean, ripple: Float,
    panel: Color, track: Color, ink: Color, dim: Color, calm: Color, text: SpeedoText,
) {
    drawRoundRect(panel, Offset(x, y), Size(w, h), CornerRadius(18f * k))
    drawRoundRect(track, Offset(x, y), Size(w, h), CornerRadius(18f * k), style = Stroke(1.5f * k))
    val cx = x + 30f * k
    val cy = y + h / 2f
    if (fix && ripple > 0f) drawCircle(calm.copy(alpha = 0.35f * (1f - ripple)), (8f + 14f * ripple) * k, Offset(cx, cy))
    drawCircle(if (fix) calm else dim.copy(alpha = 0.5f), 8f * k, Offset(cx, cy))
    drawIntoCanvas { c ->
        text.draw(c.nativeCanvas, label, cx + 22f * k, cy, 26f * k, ink.toArgb(), true, Paint.Align.LEFT)
        if (acc.isNotEmpty()) text.draw(c.nativeCanvas, acc, x + w - 18f * k, cy, 22f * k, dim.toArgb(), false, Paint.Align.RIGHT)
    }
}
