package com.vikash.ampli.drive.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.LruCache
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vikash.ampli.drive.system.MemoryTrim
import com.vikash.ampli.drive.ui.theme.CardStyle
import com.vikash.ampli.drive.ui.theme.DriveTheme
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Soft ("neumorphic") surfaces for the Soft family and the Ampli Soft Day / Night themes.
 *
 * Head units such as the SC7731E run Android 8.1: Paint.setShadowLayer on shapes is not
 * hardware-accelerated before Android 9 (it simply draws nothing), and Modifier.shadow can't do
 * coloured light/dark shadows. So the double shadow is rendered once, in software, into a small
 * nine-slice bitmap per (corner, depth, colours, size class) and then stretched with nine
 * drawImage calls per frame. No per-frame blur, no RenderEffect, the same path on every API level.
 */

/** True when the theme paints soft (neumorphic) surfaces, so shared components should too. */
val DriveTheme.isSoft: Boolean get() = cardStyle == CardStyle.Neumorphic

/**
 * Hairline edge for tappable soft items. Neumorphic shadows alone are about 1.5:1, so tappable
 * items also get this edge, at least 3:1 against the base colour (WCAG 1.4.11).
 */
val DriveTheme.softEdgeColor: Color get() = softEdge ?: muted

/** Shadow depth, as in the approved sample (CSS px = dp on a 1280x800 mdpi head unit). */
enum class SoftDepth(val offset: Dp, val blur: Dp, val inset: Boolean) {
    /** Big raised panels: `7px 7px 16px`. */
    Panel(7.dp, 16.dp, false),

    /** Buttons, tiles and chips: `5px 5px 11px`. */
    Key(5.dp, 11.dp, false),

    /** Small raised parts (switch thumbs, slider knobs). */
    Thumb(3.dp, 7.dp, false),

    /** A key pressed in, or the active/selected state: `inset 4px 4px 9px`. */
    Pressed(4.dp, 9.dp, true),

    /** Wells: map, search fields, tracks, status chips: `inset 5px 5px 11px`. */
    Well(5.dp, 11.dp, true),

    /** Thin wells (progress bars, switch tracks). */
    Groove(2.dp, 5.dp, true),
}

/** One cached nine-slice template. [margin] is how far the shadow reaches outside the shape. */
internal class SoftTemplate(val image: ImageBitmap, val margin: Int, val sliceX: Int, val sliceY: Int)

/** Cache of rendered shadow templates. Bounded, so a few odd sizes can't grow memory. */
internal object SoftShadowCache {
    private data class Key(
        val inset: Boolean, val w: Int, val h: Int, val corner: Int, val offset: Int, val blur: Int,
        val base: Int, val dark: Int, val light: Int,
    )

    /** ~3 MB: dozens of templates (a panel template is about 200x200 px). */
    private const val MAX_BYTES = 3 * 1024 * 1024

    private val cache = object : LruCache<Key, SoftTemplate>(MAX_BYTES) {
        override fun sizeOf(key: Key, value: SoftTemplate): Int = value.image.width * value.image.height * 4
    }

    /** How many templates were rendered, and the time spent on it (Diagnostics › Start-up). */
    @Volatile var renders: Int = 0
        private set

    @Volatile var renderNanos: Long = 0L
        private set

    /** Bytes held now. */
    val bytes: Int get() = cache.size()

    init {
        // Dropped with the other caches when Android asks for memory back; redrawn on demand.
        MemoryTrim.add { cache.evictAll() }
    }

    fun get(
        inset: Boolean, width: Float, height: Float, corner: Float, offset: Float, blur: Float,
        base: Color, dark: Color, light: Color,
    ): SoftTemplate? {
        val w = width.roundToInt()
        val h = height.roundToInt()
        if (w <= 0 || h <= 0) return null
        val o = offset.roundToInt().coerceAtLeast(0)
        val b = blur.roundToInt().coerceAtLeast(0)
        val r = corner.roundToInt().coerceIn(0, min(w, h) / 2)
        val extent = o + b
        // The part near each edge that is not uniform; the middle row/column is stretched.
        val k = r + extent
        val tw = min(w, 2 * k + 1)
        val th = min(h, 2 * k + 1)
        // A circle (corner = half the size) only matches its own size; others share templates.
        val key = Key(inset, tw, th, r, o, b, base.toArgb(), dark.toArgb(), light.toArgb())
        cache.get(key)?.let { return it }
        val started = System.nanoTime()
        val t = if (inset) renderInset(tw, th, r, o, b, key.base, key.dark, key.light) else renderRaised(tw, th, r, o, b, key.base, key.dark, key.light)
        renderNanos += System.nanoTime() - started
        renders++
        cache.put(key, t)
        return t
    }

    /** CSS blur B is a Gaussian with sigma B/2; BlurMaskFilter takes a radius (sigma = 0.577r + 0.5). */
    private fun blurFilter(blur: Int): BlurMaskFilter? {
        if (blur <= 1) return null
        val sigma = blur / 2f
        return BlurMaskFilter(max(0.5f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
    }

    private fun renderRaised(tw: Int, th: Int, r: Int, o: Int, b: Int, base: Int, dark: Int, light: Int): SoftTemplate {
        val m = o + b
        val bmp = Bitmap.createBitmap(tw + 2 * m, th + 2 * m, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val rect = RectF(m.toFloat(), m.toFloat(), (m + tw).toFloat(), (m + th).toFloat())
        val rf = r.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = blurFilter(b) }
        p.color = dark
        c.save(); c.translate(o.toFloat(), o.toFloat()); c.drawRoundRect(rect, rf, rf, p); c.restore()
        p.color = light
        c.save(); c.translate(-o.toFloat(), -o.toFloat()); c.drawRoundRect(rect, rf, rf, p); c.restore()
        p.maskFilter = null
        p.color = base
        c.drawRoundRect(rect, rf, rf, p)
        return SoftTemplate(bmp.asImageBitmap(), m, m + tw / 2, m + th / 2)
    }

    private fun renderInset(tw: Int, th: Int, r: Int, o: Int, b: Int, base: Int, dark: Int, light: Int): SoftTemplate {
        val bmp = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val rect = RectF(0f, 0f, tw.toFloat(), th.toFloat())
        val rf = r.toFloat()
        c.drawRoundRect(rect, rf, rf, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = base })
        // Everything outside the shape, blurred and shifted, lands inside it as an inner shadow.
        val pad = (o + b * 2 + 4).toFloat()
        val frame = android.graphics.Path().apply {
            fillType = android.graphics.Path.FillType.EVEN_ODD
            addRect(-pad, -pad, tw + pad, th + pad, android.graphics.Path.Direction.CW)
            addRoundRect(rect, rf, rf, android.graphics.Path.Direction.CW)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = blurFilter(b)
            // Only where the shape already is (keeps the rounded edge anti-aliased).
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        p.color = dark
        c.save(); c.translate(o.toFloat(), o.toFloat()); c.drawPath(frame, p); c.restore()
        p.color = light
        c.save(); c.translate(-o.toFloat(), -o.toFloat()); c.drawPath(frame, p); c.restore()
        return SoftTemplate(bmp.asImageBitmap(), 0, tw / 2, th / 2)
    }
}

/** Draws [t] stretched over a [w] x [h] box (plus the shadow margin) with nine drawImage calls. */
internal fun DrawScope.drawSoftTemplate(t: SoftTemplate, w: Int, h: Int, alpha: Float) {
    if (alpha <= 0f) return
    val m = t.margin
    val iw = t.image.width
    val ih = t.image.height
    val dw = w + 2 * m
    val dh = h + 2 * m
    val sx = t.sliceX
    val sy = t.sliceY
    // Source and destination columns: left slice, stretched middle, right slice.
    val srcX = intArrayOf(0, sx, iw - sx)
    val srcW = intArrayOf(sx, iw - 2 * sx, sx)
    val dstX = intArrayOf(-m, -m + sx, -m + dw - sx)
    val dstW = intArrayOf(sx, dw - 2 * sx, sx)
    val srcY = intArrayOf(0, sy, ih - sy)
    val srcH = intArrayOf(sy, ih - 2 * sy, sy)
    val dstY = intArrayOf(-m, -m + sy, -m + dh - sy)
    val dstH = intArrayOf(sy, dh - 2 * sy, sy)
    for (row in 0..2) {
        if (srcH[row] <= 0 || dstH[row] <= 0) continue
        for (col in 0..2) {
            if (srcW[col] <= 0 || dstW[col] <= 0) continue
            drawImage(
                t.image,
                srcOffset = IntOffset(srcX[col], srcY[row]),
                srcSize = IntSize(srcW[col], srcH[row]),
                dstOffset = IntOffset(dstX[col], dstY[row]),
                dstSize = IntSize(dstW[col], dstH[row]),
                alpha = alpha,
            )
        }
    }
}

private fun cornerOf(outline: Outline): Float = when (outline) {
    is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
    else -> 0f
}

/**
 * Paints a soft surface (fill and shadows) behind the content: raised, or sunk in for wells.
 * With [pressed] (0 = up, 1 = pressed in), a raised surface sinks in as the value grows; the
 * value is read only while drawing, so a press redraws without recomposing.
 */
fun Modifier.softSurface(
    theme: DriveTheme,
    shape: Shape,
    depth: SoftDepth = SoftDepth.Panel,
    pressed: State<Float>? = null,
    fill: Color = theme.surface,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val corner = cornerOf(outline)
    val w = size.width.roundToInt()
    val h = size.height.roundToInt()
    val base = if (fill.alpha < 1f) theme.surface else fill
    fun tpl(d: SoftDepth) = SoftShadowCache.get(
        d.inset, size.width, size.height, corner, d.offset.toPx(), d.blur.toPx(), base, theme.shadowDark, theme.shadowLight,
    )
    val main = tpl(depth)
    val sunk = if (pressed != null && !depth.inset) tpl(SoftDepth.Pressed) else null
    onDrawBehind {
        val p = pressed?.value ?: 0f
        if (main != null && p < 1f) drawSoftTemplate(main, w, h, if (sunk == null) 1f else 1f - p)
        if (sunk != null && p > 0f) drawSoftTemplate(sunk, w, h, p)
    }
}

/** A focus ring outside the shape (D-pad and rotary-knob focus on head units). */
fun Modifier.focusRing(focused: State<Boolean>, shape: Shape, color: Color, gap: Dp = 3.dp, width: Dp = 2.5.dp): Modifier =
    drawWithContent {
        drawContent()
        if (focused.value) {
            val g = gap.toPx()
            val ring = shape.createOutline(Size(size.width + 2 * g, size.height + 2 * g), layoutDirection, this)
            translate(-g, -g) { drawOutline(ring, color, style = Stroke(width.toPx())) }
        }
    }

/**
 * A tappable soft key: raised, presses in on touch (with a short haptic tick), stays pressed in
 * while [active], shows a focus ring for D-pad/rotary focus and a hairline edge for contrast.
 * Use it only when [DriveTheme.isSoft]; other themes keep their own look.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.softClickable(
    theme: DriveTheme,
    shape: Shape,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    depth: SoftDepth = SoftDepth.Key,
    onLongClick: (() -> Unit)? = null,
    edge: Boolean = true,
    fill: Color = theme.surface,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused = interaction.collectIsFocusedAsState()
    val sink = animateFloatAsState(if (pressed || active) 1f else 0f, tween(durationMillis = 140), label = "softPress")
    val view = LocalView.current
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    return this
        .focusRing(focused, shape, theme.accent)
        .softSurface(theme, shape, depth, sink, fill)
        .then(if (edge) Modifier.border(1.dp, theme.softEdgeColor.copy(alpha = if (enabled) 1f else 0.5f), shape) else Modifier)
        .clip(shape)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/** A non-tappable well (search field, status chip, progress track). */
fun Modifier.softWell(theme: DriveTheme, shape: Shape, depth: SoftDepth = SoftDepth.Well): Modifier =
    softSurface(theme, shape, depth).clip(shape)

/**
 * Soft switch: an inset track with a raised thumb that slides right when on. The accent
 * colour marks only the "on" state (a dot in the thumb and an accent edge on the track).
 */
@Composable
fun SoftSwitch(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalDriveTheme.current
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState()
    val pos by animateFloatAsState(if (checked) 1f else 0f, tween(durationMillis = 160), label = "softSwitch")
    val view = LocalView.current
    val track = CircleShape
    val edge = if (checked) theme.accent else theme.softEdgeColor
    Box(
        modifier
            .size(60.dp, 34.dp)
            .focusRing(focused, track, theme.accent)
            .softSurface(theme, track, SoftDepth.Groove)
            .border(1.5.dp, edge.copy(alpha = if (enabled) 1f else 0.5f), track)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interaction,
                indication = null,
                onValueChange = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onChange(it)
                },
            )
            .drawWithCache {
                val d = size.height - 8.dp.toPx()
                val thumb = SoftShadowCache.get(
                    false, d, d, d / 2f, SoftDepth.Thumb.offset.toPx(), SoftDepth.Thumb.blur.toPx(),
                    theme.surface, theme.shadowDark, theme.shadowLight,
                )
                val di = d.roundToInt()
                val dot = d * 0.22f
                onDrawBehind {
                    val x = 4.dp.toPx() + (size.width - d - 8.dp.toPx()) * pos
                    translate(x, 4.dp.toPx()) {
                        if (thumb != null) drawSoftTemplate(thumb, di, di, 1f)
                        if (pos > 0.5f) drawCircle(theme.accent, radius = dot, center = androidx.compose.ui.geometry.Offset(d / 2f, d / 2f), alpha = if (enabled) 1f else 0.5f)
                    }
                }
            },
    )
}

/** Rounded shape for soft keys, from the theme radius. */
fun DriveTheme.softKeyShape(dims: Dims): Shape = RoundedCornerShape(dims.d(radius * 0.75f))

/**
 * A tappable tile or row: on soft themes a [softClickable] key; on every other theme exactly the
 * chain screens used before (clip, background, optional border, clickable), so they look the same.
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
    if (theme.isSoft) {
        return softClickable(theme, shape, onClick, active = active, enabled = enabled, role = role, onLongClick = onLongClick)
    }
    var m = clip(shape).background(background, shape)
    if (border != null) m = m.border(borderWidth, border, shape)
    return if (onLongClick != null) {
        m.combinedClickable(enabled = enabled, role = role, onLongClick = onLongClick, onClick = onClick)
    } else {
        m.clickable(enabled = enabled, role = role, onClick = onClick)
    }
}

/**
 * A non-tappable card, sheet or well: on soft themes raised ([sunk] = false) or sunk in; on every
 * other theme the old clip + background + optional border.
 */
fun Modifier.themedSurface(
    theme: DriveTheme,
    shape: Shape,
    background: Color,
    border: Color? = null,
    borderWidth: Dp = 1.dp,
    sunk: Boolean = false,
    depth: SoftDepth = if (sunk) SoftDepth.Well else SoftDepth.Panel,
): Modifier {
    if (theme.isSoft) return softSurface(theme, shape, depth).clip(shape)
    var m = clip(shape).background(background, shape)
    if (border != null) m = m.border(borderWidth, border, shape)
    return m
}

/**
 * Soft themes: sinks an OutlinedTextField into the surface. The well sits under the field's
 * outline (which starts 8 dp down, below the floating label) and uses the same 14 dp corner as
 * the soft Material shapes. Other themes: unchanged.
 */
@Composable
fun Modifier.softField(): Modifier {
    val theme = LocalDriveTheme.current
    if (!theme.isSoft) return this
    return this.then(
        Modifier.drawWithCache {
            val top = 8.dp.toPx()
            val w = size.width
            val h = (size.height - top).coerceAtLeast(1f)
            val r = 14.dp.toPx()
            val t = SoftShadowCache.get(
                true, w, h, r, SoftDepth.Well.offset.toPx(), SoftDepth.Well.blur.toPx(),
                theme.surface, theme.shadowDark, theme.shadowLight,
            )
            val wi = w.roundToInt()
            val hi = h.roundToInt()
            onDrawBehind { if (t != null) translate(0f, top) { drawSoftTemplate(t, wi, hi, 1f) } }
        },
    )
}

/**
 * Slider for the active theme. Soft themes: an inset groove with a cyan fill up to the value and
 * a raised round thumb. Other themes: the Material slider in the accent colour, as before.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ThemedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
) {
    val theme = LocalDriveTheme.current
    val colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = theme.accent, activeTrackColor = theme.accent)
    if (!theme.isSoft) {
        androidx.compose.material3.Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, colors = colors, modifier = modifier)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState()
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        colors = colors,
        interactionSource = interaction,
        modifier = modifier,
        thumb = {
            Box(
                Modifier
                    .size(30.dp)
                    .focusRing(focused, CircleShape, theme.accent)
                    .softSurface(theme, CircleShape, SoftDepth.Thumb)
                    .border(1.dp, theme.softEdgeColor, CircleShape),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(theme.accent))
            }
        },
        track = { state ->
            val span = (state.valueRange.endInclusive - state.valueRange.start).takeIf { it > 0f } ?: 1f
            val fraction = ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .softWell(theme, CircleShape, SoftDepth.Groove)
                    .border(1.dp, theme.softEdgeColor, CircleShape),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(theme.accent),
                )
            }
        },
    )
}
