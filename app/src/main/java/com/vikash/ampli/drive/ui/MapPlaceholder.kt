package com.vikash.ampli.drive.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

/**
 * Screen previews only: the map pane draws [MapPlaceholder] with the route instead of the live
 * map, which cannot render off a device. Never set in the app.
 */
internal val LocalMapPreview = staticCompositionLocalOf { false }

private val MapGround = Color(0xFFE6DFCF)
private val MapBlock = Color(0xFFDDD4C1)
private val MapRoad = Color(0xFFF6F2E9)
private val MapRoadEdge = Color(0xFFD6C7A3)
private val MapWater = Color(0xFFC6D1D3)
private val MapPark = Color(0xFFD3D9BE)

/**
 * Map First's drawn map: streets, a river and a park in the map's own colours, drawn once with
 * no tiles and no animation. It sits behind the cards until the live map has a location (and in
 * screen previews). [route] adds a route line and the car, as while navigating.
 */
@Composable
fun MapPlaceholder(modifier: Modifier = Modifier, route: Boolean = false, routeColor: Color = Color(0xFF6B3BFF)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(MapGround)
        // City blocks between the streets.
        val cols = listOf(0.02f to 0.17f, 0.22f to 0.33f, 0.38f to 0.47f, 0.53f to 0.75f, 0.80f to 0.98f)
        val rows = listOf(0.02f to 0.14f, 0.20f to 0.36f, 0.49f to 0.62f, 0.70f to 0.98f)
        val r = CornerRadius(h * 0.012f)
        for ((x0, x1) in cols) for ((y0, y1) in rows) {
            drawRoundRect(MapBlock, Offset(w * x0, h * y0), Size(w * (x1 - x0), h * (y1 - y0)), r)
        }
        // A park.
        drawRoundRect(MapPark, Offset(w * 0.53f, h * 0.49f), Size(w * 0.22f, h * 0.13f), CornerRadius(h * 0.03f))
        // The river across the lower half.
        val river = Path().apply {
            moveTo(0f, h * 0.80f)
            cubicTo(w * 0.25f, h * 0.70f, w * 0.45f, h * 0.84f, w * 0.62f, h * 0.73f)
            cubicTo(w * 0.78f, h * 0.63f, w * 0.90f, h * 0.70f, w, h * 0.64f)
        }
        drawPath(river, MapWater, style = Stroke(width = h * 0.05f, cap = StrokeCap.Round))
        // Streets.
        val minor = h * 0.035f
        for (x in listOf(0.195f, 0.355f, 0.50f, 0.775f)) drawLine(MapRoad, Offset(w * x, 0f), Offset(w * x, h), minor)
        for (y in listOf(0.17f, 0.66f)) drawLine(MapRoad, Offset(0f, h * y), Offset(w, h * y), minor)
        drawLine(MapRoad, Offset(w * 0.66f, 0f), Offset(w * 0.80f, h * 0.24f), minor, cap = StrokeCap.Round)
        // The main road, with its edges.
        val major = h * 0.075f
        drawLine(MapRoadEdge, Offset(0f, h * 0.425f), Offset(w, h * 0.425f), major + h * 0.012f)
        drawLine(MapRoad, Offset(0f, h * 0.425f), Offset(w, h * 0.425f), major)

        if (route) {
            val line = h * 0.016f
            val turn = Offset(w * 0.5f, h * 0.425f)
            val car = Offset(w * 0.5f, h * 0.89f)
            val path = Path().apply {
                moveTo(0f, turn.y)
                lineTo(turn.x, turn.y)
                lineTo(turn.x, h)
            }
            drawPath(path, routeColor, style = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // The turn ahead.
            drawCircle(Color.White, line * 1.5f, turn)
            drawCircle(routeColor, line * 1.5f, turn, style = Stroke(width = line * 0.55f))
            // The car: a white disc in a soft halo, with the heading arrow.
            drawCircle(routeColor.copy(alpha = 0.22f), h * 0.065f, car)
            drawCircle(Color.White, h * 0.042f, car)
            val s = h * 0.026f
            val arrow = Path().apply {
                moveTo(car.x, car.y - s)
                lineTo(car.x + s * 0.75f, car.y + s * 0.8f)
                lineTo(car.x, car.y + s * 0.35f)
                lineTo(car.x - s * 0.75f, car.y + s * 0.8f)
                close()
            }
            drawPath(arrow, routeColor)
        }
    }
}

/**
 * Map First without a location yet: the reason and its one action on a solid card over the
 * drawn map (never loose text on the map).
 */
@Composable
internal fun LocationPromptCard(text: String, button: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Column(
        modifier
            .widthIn(max = dims.d(46f))
            .clip(theme.cardShape(dims))
            .background(theme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = dims.d(3.4f), vertical = dims.d(3f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(maxOf(dims.d(7f), 36.dp)))
        Text(
            text,
            color = theme.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = dims.t(3f, min = 22f),
            lineHeight = dims.t(3.8f, min = 28f),
            textAlign = TextAlign.Center,
        )
        BigButton(text = button, onClick = onClick)
    }
}
