package com.vikash.ampli.drive.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.cameras.CameraAlert
import com.vikash.ampli.drive.cameras.CameraAlerts
import com.vikash.ampli.drive.location.GpsState
import com.vikash.ampli.drive.ui.theme.Ampli
import kotlin.math.roundToInt

private const val GAUGE_MAX_KMH = 160f
private const val ARC_START = 135f
private const val ARC_SWEEP = 270f

@Composable
fun SpeedPane(gps: GpsState, cameraAlerts: CameraAlerts, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val limit by cameraAlerts.speedLimitKmh.collectAsStateWithLifecycle()
    val alert by cameraAlerts.current.collectAsStateWithLifecycle()
    val speed = gps.speedKmh

    Pane(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(dims.d(2f))) {
            val badge = maxOf(dims.d(10f), 52.dp)
            val gaugeSize: Dp = minOf(maxWidth, maxHeight - badge - dims.d(1.5f)).coerceAtLeast(80.dp)
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dims.d(1.5f), Alignment.CenterVertically),
            ) {
                SpeedGauge(speedKmh = speed, limitKmh = limit, size = gaugeSize)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
                ) {
                    LimitBadge(limitKmh = limit, size = badge)
                    if (!dims.compact) {
                        Column {
                            Text(
                                if (limit != null && speed != null && speed > limit!!) {
                                    "${(speed - limit!!).roundToInt()} over limit"
                                } else {
                                    "Speed limit"
                                },
                                fontSize = dims.t(2.6f),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                if (limit == null) "Not known here" else "$limit zone",
                                fontSize = dims.t(2.3f, min = 12f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            alert?.let { CameraBanner(it, Modifier.align(Alignment.TopCenter).fillMaxWidth()) }
        }
    }
}

/** Arc gauge drawn with Canvas (no images, cheap on low-end GPUs). */
@Composable
private fun SpeedGauge(speedKmh: Float?, limitKmh: Int?, size: Dp) {
    val dims = LocalDims.current
    val fraction by animateFloatAsState(
        targetValue = ((speedKmh ?: 0f) / GAUGE_MAX_KMH).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "speed",
    )
    val track = MaterialTheme.colorScheme.outline
    val numberColor = MaterialTheme.colorScheme.primary
    val over = limitKmh != null && speedKmh != null && speedKmh > limitKmh

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.07f
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = ARC_START,
                sweepAngle = ARC_SWEEP,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (fraction > 0.001f) {
                drawArc(
                    brush = Brush.linearGradient(listOf(Ampli.Rose, Ampli.Blush)),
                    startAngle = ARC_START,
                    sweepAngle = ARC_SWEEP * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                speedKmh?.roundToInt()?.toString() ?: "--",
                fontSize = dims.t(13f * (size / (dims.u * 40f)).coerceIn(0.6f, 1.2f), min = 36f),
                fontWeight = FontWeight.ExtraBold,
                color = if (over) Ampli.Rose else numberColor,
                maxLines = 1,
            )
            Text(
                "KM/H",
                fontSize = dims.t(2.6f, min = 12f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = dims.t(0.3f, min = 1f),
            )
        }
    }
}

/** Round road-sign badge. Shows "--" until a real limit source exists. */
@Composable
private fun LimitBadge(limitKmh: Int?, size: Dp) {
    val dims = LocalDims.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .border(size * 0.13f, Ampli.Rose, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            limitKmh?.toString() ?: "--",
            color = Color(0xFF111111),
            fontWeight = FontWeight.ExtraBold,
            fontSize = dims.t(3.6f, min = 16f),
            maxLines = 1,
        )
    }
}

@Composable
private fun CameraBanner(alert: CameraAlert, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val title = buildString {
        append(
            when (alert.kind) {
                CameraAlert.Kind.FixedCamera -> "Speed camera"
                CameraAlert.Kind.AverageSpeedStart -> "Average-speed zone"
                CameraAlert.Kind.AverageSpeedZone -> "In average-speed zone"
            },
        )
        append(" · ${alert.distanceMeters} m")
        alert.limitKmh?.let { append(" · Limit $it") }
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(dims.d(2.2f)))
            .background(Brush.horizontalGradient(listOf(Ampli.RoseDeep, Ampli.Rose)))
            .padding(dims.d(1.6f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = dims.t(2.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(alert.description, color = Color.White.copy(alpha = 0.9f), fontSize = dims.t(2.1f, min = 12f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
