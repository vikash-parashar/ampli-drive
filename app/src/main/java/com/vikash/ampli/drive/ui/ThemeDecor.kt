package com.vikash.ampli.drive.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vikash.ampli.drive.ui.theme.CardStyle
import com.vikash.ampli.drive.ui.theme.Decoration
import com.vikash.ampli.drive.ui.theme.DriveTheme
import kotlin.math.max

/*
 * Theme painting: card surfaces (glass, solid, outline, neumorphic, neon, skewed) and the
 * full-screen decorations (vignette, carbon fibre, synthwave sun, jaali, scanlines).
 * Everything is drawn with Canvas calls or tiny tiled bitmaps, so it stays cheap on 2 GB units.
 */

/** Card shape for this theme. [scale] shrinks the corner radius for smaller elements. */
fun DriveTheme.cardShape(dims: Dims, scale: Float = 1f): Shape = when {
    cardStyle == CardStyle.Skewed -> CutCornerShape(bottomEnd = dims.d(3.2f * scale))
    radius <= 0f -> RectangleShape
    else -> RoundedCornerShape(dims.d(radius * scale))
}

/** Parallelogram used for Sport Red buttons (CSS skewX(-12deg)). */
fun skewShape(slant: Float = 0.2f): Shape = GenericShape { size, _ ->
    val dx = size.height * slant
    moveTo(dx, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - dx, size.height)
    lineTo(0f, size.height)
    close()
}

/** Shape for buttons and chips: pills on rounded themes, squares on flat ones. */
fun DriveTheme.buttonShape(dims: Dims): Shape = when {
    cardStyle == CardStyle.Skewed -> skewShape()
    radius <= 0f -> RectangleShape
    else -> RoundedCornerShape(dims.d(radius * 0.6f))
}

/** Small labels: upper-case on themes whose design uses caps. */
fun DriveTheme.label(text: String): String = if (upperLabels) text.uppercase() else text

/** Paints a card for the active theme: shadow or glow, fill, border and accents. */
fun Modifier.themedCard(
    theme: DriveTheme,
    shape: Shape,
    fill: Brush = Brush.linearGradient(listOf(theme.surface, theme.surface)),
    border: Color = theme.border,
    accentEdge: Boolean = true,
): Modifier {
    var m = this
    when (theme.cardStyle) {
        CardStyle.Neumorphic -> m = m.softShadow(shape, theme.surface, theme.shadowDark, theme.shadowLight)
        CardStyle.NeonGlow -> m = m.neonGlow(shape, border)
        CardStyle.Glass -> m = m.softShadow(shape, Color.Transparent, Color(0x99000000), Color.Transparent, 6.dp, 14.dp)
        CardStyle.Pop -> m = m.hardShadow(shape, theme.accent2)
        else -> Unit
    }
    m = m.clip(shape).background(fill, shape)
    if (theme.cardStyle == CardStyle.Glass) {
        // Light catching the top edge of the glass.
        m = m.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent)), shape)
    }
    if (theme.borderWidth > 0.dp && border.alpha > 0f) m = m.border(theme.borderWidth, border, shape)
    if (theme.cardStyle == CardStyle.Skewed && accentEdge) {
        m = m.drawWithContent {
            drawContent()
            drawRect(theme.accent, size = Size(size.width, 3.dp.toPx()))
        }
    }
    return m
}

/** Neumorphic double shadow: dark below-right, light above-left (CSS `--up`). */
fun Modifier.softShadow(
    shape: Shape,
    fill: Color,
    dark: Color,
    light: Color,
    offset: Dp = 7.dp,
    blur: Dp = 14.dp,
): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }.asAndroidPath()
    val o = offset.toPx()
    val b = blur.toPx()
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            // A shadow layer needs an opaque paint to cast from; the card covers it afterwards.
            color = if (fill.alpha > 0.99f) fill.toArgb() else android.graphics.Color.BLACK
        }
        if (dark.alpha > 0f) {
            paint.setShadowLayer(b, o, o, dark.toArgb())
            if (fill.alpha <= 0.99f) paint.alpha = 1
            canvas.nativeCanvas.drawPath(path, paint)
        }
        if (light.alpha > 0f) {
            paint.setShadowLayer(b, -o, -o, light.toArgb())
            canvas.nativeCanvas.drawPath(path, paint)
        }
    }
}

/** Holi Pop: a flat offset shadow in a bright colour (CSS `box-shadow: 6px 6px 0`). */
fun Modifier.hardShadow(shape: Shape, color: Color, offset: Dp = 6.dp): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val o = offset.toPx()
    translate(o, o) { drawPath(path, color) }
}

/** Neon tube glow: a few widening, fading strokes around the shape. */
fun Modifier.neonGlow(shape: Shape, color: Color): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    drawPath(path, color.copy(alpha = 0.08f), style = Stroke(width = 18.dp.toPx()))
    drawPath(path, color.copy(alpha = 0.14f), style = Stroke(width = 10.dp.toPx()))
    drawPath(path, color.copy(alpha = 0.25f), style = Stroke(width = 5.dp.toPx()))
}

/** Background colour plus the theme's pattern, drawn behind the whole screen. */
@Composable
fun Modifier.themeBackground(theme: DriveTheme): Modifier {
    val density = LocalDensity.current.density
    val pattern = remember(theme.decoration, theme.patternColor, density) {
        patternBrush(theme.decoration, density, theme.patternColor.toArgb())
    }
    return drawBehind {
        drawRect(theme.background)
        when (theme.decoration) {
            Decoration.Vignette -> {
                drawRect(
                    Brush.radialGradient(
                        0f to Color(0x2622D3EE), 0.45f to Color.Transparent, 1f to Color.Transparent,
                        center = Offset(size.width * 0.55f, size.height * 0.45f),
                        radius = max(size.width, size.height) * 0.5f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        0.4f to Color.Transparent, 1f to Color(0xB303060C),
                        center = Offset(size.width * 0.55f, size.height * 0.45f),
                        radius = max(size.width, size.height) * 0.7f,
                    ),
                )
            }
            Decoration.Carbon -> {
                pattern?.let { drawRect(it) }
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x1AFF1E1E), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.45f),
                        radius = max(size.width, size.height) * 0.45f,
                    ),
                )
            }
            Decoration.Synthwave -> drawSynthwave()
            Decoration.Jaali -> pattern?.let { drawRect(it) }
            Decoration.Stripes -> {
                // Kerala Green: fine diagonal gold threads plus two soft light pools.
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFF2E6B3C), Color.Transparent),
                        center = Offset(size.width * 0.15f, 0f),
                        radius = size.width * 0.5f,
                    ),
                )
                val stepPx = 26.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(theme.patternColor, Offset(x, size.height), Offset(x + size.height * 0.53f, 0f), strokeWidth = 1.5f.dp.toPx())
                    x += stepPx
                }
            }
            Decoration.Holi -> {
                // Holi Pop: colour-powder splashes in the corners.
                val r = max(size.width, size.height) * 0.22f
                val splashes = listOf(
                    Offset(size.width * 0.06f, size.height * 0.1f) to Color(0x8CFF3D8B),
                    Offset(size.width * 0.95f, size.height * 0.08f) to Color(0x8CFFD23F),
                    Offset(size.width * 0.9f, size.height * 0.95f) to Color(0x7300B36B),
                    Offset(size.width * 0.05f, size.height * 0.92f) to Color(0x732F7BFF),
                    Offset(size.width * 0.5f, size.height * 1.02f) to Color(0x597B3FF2),
                )
                for ((c, col) in splashes) {
                    drawCircle(Brush.radialGradient(listOf(col, Color.Transparent), center = c, radius = r), radius = r, center = c)
                }
            }
            Decoration.Scanlines, Decoration.None -> Unit
        }
    }
}

/** Scanlines and CRT vignette go over the content (HUD Green); other themes draw nothing here. */
@Composable
fun Modifier.themeOverlay(theme: DriveTheme): Modifier {
    if (theme.decoration != Decoration.Scanlines) return this
    val density = LocalDensity.current.density
    val lines = remember(density) { patternBrush(Decoration.Scanlines, density) }
    return drawWithContent {
        drawContent()
        lines?.let { drawRect(it) }
        drawRect(
            Brush.radialGradient(
                0.55f to Color.Transparent, 1f to Color(0x99000000),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = max(size.width, size.height) * 0.62f,
            ),
        )
    }
}

private fun DrawScope.drawSynthwave() {
    val w = size.width
    val h = size.height
    val horizon = h * 0.532f
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF0D0221),
            0.30f to Color(0xFF1E0B3D),
            0.50f to Color(0xFF4A1366),
            0.53f to Color(0xFFB8236F),
            0.532f to Color(0xFF12002A),
            1f to Color(0xFF12002A),
        ),
    )
    // Stars
    val stars = listOf(0.12f to 0.08f, 0.30f to 0.24f, 0.72f to 0.06f, 0.88f to 0.16f, 0.52f to 0.03f, 0.63f to 0.18f, 0.06f to 0.30f, 0.94f to 0.36f)
    for ((x, y) in stars) drawCircle(Color.White.copy(alpha = 0.7f), radius = 1.5f.dp.toPx(), center = Offset(w * x, h * y))

    // Striped sun sitting on the horizon
    val r = h * 0.19f
    val cx = w / 2f
    val cy = horizon - r * 0.55f
    drawCircle(
        Brush.radialGradient(listOf(Color(0x66FF2E97), Color.Transparent), center = Offset(cx, cy), radius = r * 1.6f),
        radius = r * 1.6f,
        center = Offset(cx, cy),
    )
    val sun = Brush.verticalGradient(
        0.1f to Color(0xFFFFD319), 0.45f to Color(0xFFFF901F), 0.85f to Color(0xFFFF2E97),
        startY = cy - r, endY = cy + r,
    )
    val bands = listOf(0f to 0.52f, 0.56f to 0.64f, 0.68f to 0.76f, 0.81f to 0.88f, 0.94f to 1f)
    for ((a, b) in bands) {
        val top = cy - r + 2 * r * a
        val bottom = minOf(cy - r + 2 * r * b, horizon)
        if (bottom <= top) continue
        clipRect(top = top, bottom = bottom) { drawCircle(sun, radius = r, center = Offset(cx, cy)) }
    }

    // Perspective grid on the ground
    val grid = Color(0x8CFF2E97)
    val stroke = 1.5f.dp.toPx()
    var y = horizon
    var gap = 4.dp.toPx()
    while (y < h) {
        drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = stroke)
        y += gap
        gap *= 1.35f
    }
    val lines = 18
    for (i in -lines..lines) {
        val xBottom = cx + i * (w / lines) * 1.4f
        drawLine(grid, Offset(cx + i * (w / lines) * 0.06f, horizon), Offset(xBottom, h), strokeWidth = stroke)
    }
    drawRect(
        Brush.verticalGradient(listOf(Color(0x55B8236F), Color.Transparent), startY = horizon, endY = horizon + h * 0.1f),
        topLeft = Offset(0f, horizon),
        size = Size(w, h * 0.1f),
    )
}

/** Tiny tiled bitmaps for repeating patterns. */
private fun patternBrush(decoration: Decoration, density: Float, tint: Int = 0x29FF9933): Brush? {
    val bitmap = when (decoration) {
        Decoration.Carbon -> {
            val cell = max(2, (4 * density).toInt())
            Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = android.graphics.Canvas(bmp)
                val paint = android.graphics.Paint().apply { color = 0xFF19191C.toInt() }
                canvas.drawColor(0xFF101012.toInt())
                canvas.drawRect(0f, 0f, cell.toFloat(), cell.toFloat(), paint)
                canvas.drawRect(cell.toFloat(), cell.toFloat(), cell * 2f, cell * 2f, paint)
            }
        }
        Decoration.Scanlines -> {
            val step = max(3, (4 * density).toInt())
            Bitmap.createBitmap(1, step, Bitmap.Config.ARGB_8888).also { bmp ->
                bmp.setPixel(0, step - 1, 0x1439FF88)
                if (density >= 2f) bmp.setPixel(0, step - 2, 0x0A39FF88)
            }
        }
        Decoration.Jaali -> jaaliTile((56 * density).toInt().coerceAtLeast(32), tint)
        else -> null
    } ?: return null
    return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/** The Desi Saffron lattice: four petals and quarter circles at the corners (56 px tile in CSS). */
private fun jaaliTile(px: Int, color: Int): Bitmap {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val s = px / 56f
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.3f * s
        this.color = color
    }
    val p = android.graphics.Path()
    fun petal(x0: Float, y0: Float, c1x: Float, c1y: Float, c2x: Float, c2y: Float, d1x: Float, d1y: Float, d2x: Float, d2y: Float) {
        p.moveTo(x0 * s, y0 * s)
        p.cubicTo(c1x * s, c1y * s, c2x * s, c2y * s, 28 * s, 28 * s)
        p.cubicTo(d1x * s, d1y * s, d2x * s, d2y * s, x0 * s, y0 * s)
    }
    petal(28f, 10f, 34f, 17f, 34f, 21f, 22f, 21f, 22f, 17f)
    petal(28f, 46f, 34f, 39f, 34f, 35f, 22f, 35f, 22f, 39f)
    petal(10f, 28f, 17f, 22f, 21f, 22f, 21f, 34f, 17f, 34f)
    petal(46f, 28f, 39f, 22f, 35f, 22f, 35f, 34f, 39f, 34f)
    canvas.drawPath(p, paint)
    for ((x, y) in listOf(0f to 0f, 56f to 0f, 0f to 56f, 56f to 56f)) canvas.drawCircle(x * s, y * s, 5 * s, paint)
    return bmp
}
