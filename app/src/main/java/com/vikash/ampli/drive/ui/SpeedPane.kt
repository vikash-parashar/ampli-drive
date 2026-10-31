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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.cameras.CameraAlert
import com.vikash.ampli.drive.cameras.CameraAlerts
import com.vikash.ampli.drive.location.GpsState
import com.vikash.ampli.drive.ui.theme.CardStyle
import com.vikash.ampli.drive.ui.theme.DriveTheme
import com.vikash.ampli.drive.ui.theme.FontChoice
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.ui.theme.SpeedStyle
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val GAUGE_MAX_KMH = 160f
private const val ARC_START = 135f
private const val ARC_SWEEP = 270f

/** Speed, speed limit and camera alerts, drawn in the active theme's speed style. */
@Composable
fun SpeedPane(gps: GpsState, cameraAlerts: CameraAlerts, modifier: Modifier = Modifier, rpm: Double? = null) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val limit by cameraAlerts.speedLimitKmh.collectAsStateWithLifecycle()
    val alert by cameraAlerts.current.collectAsStateWithLifecycle()
    val speed = gps.speedKmh

    Pane(modifier, fill = theme.speedSurface) {
        Column(Modifier.fillMaxSize().padding(dims.d(2f))) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                when (theme.speedStyle) {
                    SpeedStyle.Arc -> ArcSpeed(speed, limit, maxWidth, maxHeight)
                    SpeedStyle.Number -> NumberSpeed(speed, limit, maxHeight)
                    SpeedStyle.Tach -> TachSpeed(speed, limit, maxWidth, maxHeight)
                    SpeedStyle.Tape -> TapeSpeed(speed, limit, maxHeight)
                    SpeedStyle.Giant -> GiantSpeed(speed, limit, maxHeight)
                }
                alert?.let { CameraBanner(it, Modifier.align(Alignment.TopCenter).fillMaxWidth()) }
            }
            // Trip average and engine RPM; RPM needs the OBD-II dongle, so say so instead of a blank.
            Text(
                stringResource(R.string.speed_avg, gps.avgKmh?.roundToInt()?.toString() ?: "--") + "  ·  " +
                    (rpm?.let { stringResource(R.string.speed_rpm, it.roundToInt()) } ?: stringResource(R.string.speed_rpm_connect)),
                style = theme.labelStyle(theme.paneMuted),
                fontSize = dims.t(2.1f, min = 16f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun animatedFraction(speedKmh: Float?): Float {
    val fraction by animateFloatAsState(
        targetValue = ((speedKmh ?: 0f) / GAUGE_MAX_KMH).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "speed",
    )
    return fraction
}

private fun speedText(speedKmh: Float?): String = speedKmh?.roundToInt()?.toString() ?: "--"

private fun isOver(speedKmh: Float?, limitKmh: Int?) = limitKmh != null && speedKmh != null && speedKmh > limitKmh

/** Text colour inside the speed pane (Desi Saffron's pane is saffron with dark text). */
private val DriveTheme.paneText: Color get() = speedText ?: text
private val DriveTheme.paneMuted: Color get() = if (speedText != null && speedSurface != null) speedText.copy(alpha = 0.72f) else muted

// ---------------------------------------------------------------- Arc (01, 02, 08)

@Composable
private fun ArcSpeed(speed: Float?, limit: Int?, maxWidth: Dp, maxHeight: Dp) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
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
            if (!dims.compact) LimitCaption(speed, limit, theme)
        }
    }
}

@Composable
private fun LimitCaption(speed: Float?, limit: Int?, theme: DriveTheme) {
    val dims = LocalDims.current
    Column {
        Text(
            if (isOver(speed, limit)) {
                stringResource(R.string.speed_over_limit, (speed!! - limit!!).roundToInt())
            } else {
                stringResource(R.string.speed_limit)
            },
            fontSize = dims.t(2.6f, min = 16f),
            fontWeight = FontWeight.SemiBold,
            color = if (isOver(speed, limit)) theme.limitRing else theme.paneText,
        )
        Text(
            if (limit == null) stringResource(R.string.speed_limit_unknown) else stringResource(R.string.speed_zone, limit),
            fontSize = dims.t(2.3f, min = 16f),
            color = theme.paneMuted,
        )
    }
}

/** Arc gauge drawn with Canvas (no images, cheap on low-end GPUs). */
@Composable
private fun SpeedGauge(speedKmh: Float?, limitKmh: Int?, size: Dp) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val fraction = animatedFraction(speedKmh)
    val over = isOver(speedKmh, limitKmh)
    val neumorphic = theme.cardStyle == CardStyle.Neumorphic

    Box(
        Modifier
            .size(size)
            .then(
                if (neumorphic) Modifier.softSurface(theme, CircleShape, SoftDepth.Panel) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * (if (neumorphic) 0.045f else 0.07f)
            val inset = stroke / 2f + if (neumorphic) this.size.minDimension * 0.05f else 0f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = theme.track,
                startAngle = ARC_START,
                sweepAngle = ARC_SWEEP,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (fraction > 0.001f) {
                theme.glow?.let { glow ->
                    drawArc(
                        color = glow.copy(alpha = 0.25f),
                        startAngle = ARC_START,
                        sweepAngle = ARC_SWEEP * fraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke * 2.2f, cap = StrokeCap.Round),
                    )
                }
                drawArc(
                    brush = Brush.linearGradient(theme.gauge),
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
                speedText(speedKmh),
                style = theme.numberStyle(if (over) theme.limitRing else theme.paneText),
                fontSize = dims.t(13f * (size / (dims.u * 40f)).coerceIn(0.6f, 1.2f), min = 36f),
                maxLines = 1,
            )
            Text(
                theme.label(stringResource(R.string.speed_unit)),
                style = theme.labelStyle(theme.paneMuted),
                fontSize = dims.t(2.6f, min = 16f),
            )
        }
    }
}

// ---------------------------------------------------------------- Number (03, 05, 07)

@Composable
private fun NumberSpeed(speed: Float?, limit: Int?, maxHeight: Dp) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val density = LocalDensity.current
    val over = isOver(speed, limit)
    val numberSize = with(density) { (maxHeight * 0.42f).toSp() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                theme.label(stringResource(R.string.speed_label)),
                style = theme.labelStyle(theme.paneMuted),
                fontSize = dims.t(2.2f, min = 16f),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    speedText(speed),
                    style = theme.numberStyle(if (over) theme.limitRing else theme.paneText),
                    fontSize = numberSize,
                    maxLines = 1,
                )
                Spacer(Modifier.width(dims.d(1f)))
                Text(
                    theme.label(stringResource(R.string.speed_unit)),
                    style = theme.labelStyle(theme.paneMuted).copy(shadow = null),
                    fontSize = dims.t(3f, min = 16f),
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = dims.d(2f)),
                )
            }
            if (over) {
                Text(
                    stringResource(R.string.speed_over_limit, (speed!! - limit!!).roundToInt()),
                    color = Color(0xFFB91C1C),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = dims.t(2.6f, min = 16f),
                    modifier = Modifier
                        .clip(theme.buttonShape(dims))
                        .background(Color(0xFFFDE8E8))
                        .padding(horizontal = dims.d(2f), vertical = dims.d(1f)),
                )
            } else {
                Text(
                    if (limit == null) stringResource(R.string.speed_limit_unknown) else stringResource(R.string.speed_zone, limit),
                    style = theme.labelStyle(theme.paneMuted),
                    fontSize = dims.t(2.3f, min = 16f),
                )
            }
        }
        LimitBadge(limitKmh = limit, size = maxOf(dims.d(13f), 56.dp), modifier = Modifier.align(Alignment.TopEnd))
    }
}

// ---------------------------------------------------------------- Giant (06 Minimal Mono)

@Composable
private fun GiantSpeed(speed: Float?, limit: Int?, maxHeight: Dp) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val density = LocalDensity.current
    val over = isOver(speed, limit)
    val numberSize = with(density) { (maxHeight * 0.62f).toSp() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text(
                theme.label(stringResource(R.string.speed_label)),
                style = theme.labelStyle(),
                fontSize = dims.t(2.2f, min = 16f),
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    speedText(speed),
                    style = theme.numberStyle(if (over) theme.limitRing else theme.text).copy(letterSpacing = (-0.06f).em),
                    fontSize = numberSize,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(Modifier.weight(0.2f))
            LimitCaption(speed, limit, theme)
        }
        LimitBadge(limitKmh = limit, size = maxOf(dims.d(16f), 64.dp), modifier = Modifier.align(Alignment.TopEnd))
    }
}

// ---------------------------------------------------------------- Tach (04 Sport Red)

@Composable
private fun TachSpeed(speed: Float?, limit: Int?, maxWidth: Dp, maxHeight: Dp) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val fraction = animatedFraction(speed)
    val badge = maxOf(dims.d(10f), 52.dp)
    val size = minOf(maxWidth, maxHeight - badge * 0.5f).coerceAtLeast(80.dp)
    val over = isOver(speed, limit)
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2f
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                drawCircle(Color(0xFF0B0B0D), radius = r, center = c)
                drawCircle(Color(0xFF2C2C31), radius = r - 1.5f, center = c, style = Stroke(width = 3f))
                // Red zone from 120 to 160 km/h
                val ring = r * 0.80f
                val tl = Offset(c.x - ring, c.y - ring)
                val sz = Size(ring * 2, ring * 2)
                drawArc(theme.accent2.copy(alpha = 0.85f), ARC_START + ARC_SWEEP * 0.75f, ARC_SWEEP * 0.25f, false, tl, sz, style = Stroke(width = r * 0.05f))
                if (fraction > 0.001f) {
                    drawArc(
                        Brush.sweepGradient(listOf(Color(0x00E10600), theme.accent, theme.accent2), center = c),
                        ARC_START, ARC_SWEEP * fraction, false, tl, sz, style = Stroke(width = r * 0.05f),
                    )
                }
                // Ticks every 10 km/h, long every 20
                for (i in 0..16) {
                    val a = Math.toRadians((ARC_START + ARC_SWEEP * i / 16f).toDouble())
                    val major = i % 2 == 0
                    val outer = r * 0.94f
                    val inner = if (major) r * 0.84f else r * 0.89f
                    val col = if (i >= 12) theme.accent2 else if (major) Color.White else Color(0xFFD4D4D8)
                    drawLine(
                        col,
                        Offset(c.x + outer * cos(a).toFloat(), c.y + outer * sin(a).toFloat()),
                        Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()),
                        strokeWidth = if (major) r * 0.022f else r * 0.011f,
                    )
                }
                // Needle
                rotate(ARC_START + ARC_SWEEP * fraction, pivot = c) {
                    val needle = Path().apply {
                        moveTo(c.x + r * 0.9f, c.y)
                        lineTo(c.x + r * 0.55f, c.y - r * 0.025f)
                        lineTo(c.x + r * 0.55f, c.y + r * 0.025f)
                        close()
                    }
                    drawPath(needle, theme.accent2)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    speedText(speed),
                    style = theme.numberStyle(if (over) theme.warning else theme.text, glowColor = null),
                    fontSize = dims.t(14f * (size / (dims.u * 40f)).coerceIn(0.6f, 1.2f), min = 40f),
                    maxLines = 1,
                )
                Text(
                    theme.label(stringResource(R.string.speed_unit)),
                    style = theme.labelStyle().copy(letterSpacing = 0.3f.em),
                    fontSize = dims.t(2.6f, min = 16f),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            modifier = Modifier.padding(top = dims.d(0.5f)),
        ) {
            LimitBadge(limitKmh = limit, size = badge)
            if (!dims.compact) LimitCaption(speed, limit, theme)
        }
    }
}

// ---------------------------------------------------------------- Tape (09 HUD Green)

private val PointerShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width * 0.86f, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width * 0.86f, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
private fun TapeSpeed(speed: Float?, limit: Int?, maxHeight: Dp) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shown by animateFloatAsState(targetValue = speed ?: 0f, animationSpec = tween(600), label = "tape")
    val over = isOver(speed, limit)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            // The moving tape: one tick per 10 km/h, ±40 km/h visible.
            Canvas(
                Modifier
                    .fillMaxSize()
                    .border(2.dp, theme.border),
            ) {
                val pxPerKmh = size.height / 80f
                val centre = size.height / 2f
                val lineCol = theme.muted
                var v = ((shown - 40f) / 5f).toInt() * 5
                while (v <= shown + 40f) {
                    if (v >= 0) {
                        val y = centre - (v - shown) * pxPerKmh
                        val major = v % 10 == 0
                        val col = if (limit != null && v == limit) theme.warning else lineCol
                        drawLine(
                            col,
                            Offset(size.width - (if (major) size.width * 0.28f else size.width * 0.14f), y),
                            Offset(size.width, y),
                            strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                        )
                    }
                    v += 5
                }
                if (limit != null && shown > limit) {
                    // Amber bar over the part of the tape above the limit.
                    val yLimit = centre - (limit - shown) * pxPerKmh
                    drawRect(theme.warning.copy(alpha = 0.7f), topLeft = Offset(size.width - 6.dp.toPx(), 0f), size = Size(6.dp.toPx(), yLimit.coerceIn(0f, size.height)))
                }
            }
            Box(
                Modifier
                    .fillMaxWidth(0.82f)
                    .height(maxOf(maxHeight * 0.22f, 56.dp))
                    .clip(PointerShape)
                    .background(theme.background)
                    .border(3.dp, if (over) theme.warning else theme.accent, PointerShape)
                    .padding(start = dims.d(1.6f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    speedText(speed),
                    style = theme.numberStyle(if (over) theme.warning else theme.accent),
                    fontSize = dims.t(9f, min = 32f),
                    maxLines = 1,
                )
            }
        }
        Column(
            Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dims.d(1.2f), Alignment.CenterVertically),
        ) {
            LimitBadge(limitKmh = limit, size = maxOf(dims.d(10.5f), 52.dp))
            Text(
                theme.label(stringResource(R.string.speed_unit)),
                style = theme.labelStyle(theme.muted),
                fontSize = dims.t(2.2f, min = 16f),
            )
        }
    }
}

// ---------------------------------------------------------------- Shared

/** Round road-sign badge: the limit at an approaching speed camera, otherwise "--". */
@Composable
internal fun LimitBadge(limitKmh: Int?, size: Dp, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val hud = theme.font == FontChoice.Mono
    Box(
        modifier
            .size(size)
            .then(
                if (theme.cardStyle == CardStyle.Neumorphic) {
                    Modifier.softSurface(theme, CircleShape, SoftDepth.Key, fill = Color.White)
                } else {
                    Modifier
                },
            )
            .clip(CircleShape)
            .background(if (hud) theme.background else Color.White)
            .border(size * 0.12f, theme.limitRing, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            limitKmh?.toString() ?: "--",
            color = if (hud) theme.limitRing else Color(0xFF111111),
            fontFamily = theme.font.family,
            fontWeight = if (hud) FontWeight.Normal else FontWeight.ExtraBold,
            fontSize = dims.t(3.6f * size.value / maxOf(dims.d(10f), 52.dp).value, min = 16f),
            maxLines = 1,
        )
    }
}

@Composable
private fun CameraBanner(alert: CameraAlert, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val title = buildString {
        append(
            when (alert.kind) {
                CameraAlert.Kind.FixedCamera -> stringResource(R.string.camera_fixed)
                CameraAlert.Kind.AverageSpeedStart -> stringResource(R.string.camera_average_start)
                CameraAlert.Kind.AverageSpeedZone -> stringResource(R.string.camera_average_in)
                else -> cameraKindLabel(alert.kind)
            },
        )
        append(" · ")
        append(stringResource(R.string.camera_distance, alert.distanceMeters))
        alert.limitKmh?.let {
            append(" · ")
            append(stringResource(R.string.camera_limit, it))
        }
    }
    // Amber while approaching; red when the car is over the camera's limit.
    val ink = if (alert.overLimit) theme.onDanger else Color(0xFF111111)
    val detail = if (alert.userReported) stringResource(R.string.camera_reported_by_you) else alert.description
    Row(
        modifier = modifier
            .clip(theme.buttonShape(dims))
            .background(if (alert.overLimit) theme.danger else theme.warning)
            .padding(dims.d(1.6f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = ink, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontWeight = FontWeight.Bold, fontSize = dims.t(2.6f, min = 16f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) {
                Text(detail, color = ink.copy(alpha = 0.85f), fontSize = dims.t(2.1f, min = 16f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
