package com.vikash.ampli.drive.ui

import android.content.Context
import android.location.Location
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.map.GLANCE_ATTRIBUTION
import com.vikash.ampli.drive.map.GlanceMap
import com.vikash.ampli.drive.nav.Maneuver
import com.vikash.ampli.drive.nav.NavSession
import com.vikash.ampli.drive.nav.route.RouteProgress
import com.vikash.ampli.drive.nav.route.RouteStep
import com.vikash.ampli.drive.nav.route.RouteTracker
import com.vikash.ampli.drive.ui.theme.DriveTheme
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ----------------------------------------------------------------------------- Formatting

/** "350 m" (rounded to 10 m), "1.2 km", "14 km". */
internal fun navDistance(context: Context, m: Double): String {
    val metres = m.roundToInt().coerceAtLeast(0)
    return when {
        metres < 1000 -> context.getString(R.string.nav_m, if (metres < 100) (metres / 5) * 5 else (metres / 10) * 10)
        metres < 10_000 -> context.getString(R.string.nav_km, String.format(Locale.US, "%.1f", metres / 1000.0))
        else -> context.getString(R.string.nav_km, (metres / 1000.0).roundToInt().toString())
    }
}

/** "18 min", "1 h 5 min". */
internal fun navDuration(context: Context, seconds: Double): String {
    val min = (seconds / 60.0).roundToInt().coerceAtLeast(1)
    return if (min < 60) context.getString(R.string.nav_min, min) else context.getString(R.string.nav_h_min, min / 60, min % 60)
}

/** The plain action words for a step ("Turn left"), for when the road has no name. */
internal fun maneuverText(context: Context, step: RouteStep): String {
    val id = when (step.maneuver) {
        Maneuver.Left -> R.string.nav_turn_left
        Maneuver.Right -> R.string.nav_turn_right
        Maneuver.SlightLeft -> R.string.nav_slight_left
        Maneuver.SlightRight -> R.string.nav_slight_right
        Maneuver.SharpLeft -> R.string.nav_sharp_left
        Maneuver.SharpRight -> R.string.nav_sharp_right
        Maneuver.UTurn -> R.string.nav_u_turn
        Maneuver.KeepLeft -> R.string.nav_keep_left
        Maneuver.KeepRight -> R.string.nav_keep_right
        Maneuver.Flyover -> R.string.nav_flyover
        Maneuver.Ramp -> R.string.nav_ramp
        Maneuver.Exit -> R.string.nav_exit
        Maneuver.Roundabout -> if (step.exitNumber != null) R.string.nav_roundabout_exit else R.string.nav_roundabout
        Maneuver.Arrive -> R.string.nav_arrive
        Maneuver.Continue -> R.string.nav_continue
    }
    val text = if (step.maneuver == Maneuver.Roundabout && step.exitNumber != null) {
        context.getString(id, step.exitNumber)
    } else {
        context.getString(id)
    }
    return text.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

@Composable
private fun rememberClock(): LocalTime {
    val now by produceState(LocalTime.now()) {
        while (true) {
            delay(15_000L)
            value = LocalTime.now()
        }
    }
    return now
}

@Composable
private fun etaText(remainingS: Double): String {
    val context = LocalContext.current
    val now = rememberClock()
    val is24h = remember { DateFormat.is24HourFormat(context) }
    val fmt = remember(is24h) { DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", Locale.getDefault()) }
    return now.plusSeconds(remainingS.toLong()).format(fmt)
}

/** Panels over the map can't blur it: glass themes use their stronger, near-opaque tint. */
private fun mapPanelFill(theme: DriveTheme): androidx.compose.ui.graphics.Brush {
    val c = theme.glass?.let { Color(it.overMap) } ?: theme.surface
    return androidx.compose.ui.graphics.Brush.linearGradient(listOf(c, c))
}

// ------------------------------------------------------------------------- Manoeuvre arrow

/** Direction of the arm for a manoeuvre, degrees clockwise from straight ahead. */
private fun armAngle(step: RouteStep): Float {
    val mod = step.osrmModifier?.lowercase()
    val side = when {
        mod == null -> 0f
        "left" in mod -> -1f
        "right" in mod -> 1f
        else -> 0f
    }
    return when (step.maneuver) {
        Maneuver.Left -> -90f
        Maneuver.Right -> 90f
        Maneuver.SlightLeft -> -45f
        Maneuver.SlightRight -> 45f
        Maneuver.SharpLeft -> -135f
        Maneuver.SharpRight -> 135f
        Maneuver.KeepLeft -> -32f
        Maneuver.KeepRight -> 32f
        Maneuver.Ramp, Maneuver.Exit, Maneuver.Flyover -> 32f * side
        Maneuver.Roundabout -> when (mod) {
            "left" -> -90f
            "sharp left" -> -135f
            "slight left" -> -45f
            "right" -> 90f
            "sharp right" -> 135f
            "slight right" -> 45f
            "uturn" -> 180f
            else -> 0f
        }
        else -> 0f
    }
}

/** A big, simple manoeuvre arrow drawn in [color]; no icon font or bitmap needed. */
@Composable
fun ManeuverArrow(step: RouteStep, color: Color, modifier: Modifier = Modifier) {
    val angle = armAngle(step)
    Canvas(modifier) {
        val s = size.minDimension
        val w = s * 0.12f
        val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val cx = size.width / 2
        val top = (size.height - s) / 2
        fun p(x: Float, y: Float) = Offset(cx - s / 2 + x * s, top + y * s)
        when (step.maneuver) {
            Maneuver.Arrive -> {
                // A map pin.
                val c = p(0.5f, 0.40f)
                val r = s * 0.24f
                val pin = Path().apply {
                    moveTo(c.x - r * 0.9f, c.y + r * 0.45f)
                    lineTo(p(0.5f, 0.92f).x, p(0.5f, 0.92f).y)
                    lineTo(c.x + r * 0.9f, c.y + r * 0.45f)
                    close()
                }
                drawPath(pin, color)
                drawCircle(color, r, c)
                drawCircle(Color.Black.copy(alpha = 0.25f), r * 0.42f, c)
            }
            Maneuver.UTurn -> {
                // Up, over to the right, and back down (India drives on the left, U-turns go right).
                val path = Path().apply {
                    moveTo(p(0.32f, 0.92f).x, p(0.32f, 0.92f).y)
                    lineTo(p(0.32f, 0.42f).x, p(0.32f, 0.42f).y)
                    cubicTo(p(0.32f, 0.12f).x, p(0.32f, 0.12f).y, p(0.70f, 0.12f).x, p(0.70f, 0.12f).y, p(0.70f, 0.42f).x, p(0.70f, 0.42f).y)
                    lineTo(p(0.70f, 0.60f).x, p(0.70f, 0.60f).y)
                }
                drawPath(path, color, style = stroke)
                arrowHead(p(0.70f, 0.78f), 180f, s, color)
            }
            Maneuver.Roundabout -> {
                val c = p(0.5f, 0.46f)
                val r = s * 0.17f
                drawCircle(color, r, c, style = Stroke(width = w * 0.8f))
                drawLine(color, p(0.5f, 0.94f), Offset(c.x, c.y + r), strokeWidth = w, cap = StrokeCap.Round)
                val rad = Math.toRadians(angle.toDouble())
                val start = Offset(c.x + (r * sin(rad)).toFloat(), c.y - (r * cos(rad)).toFloat())
                val end = Offset(c.x + (s * 0.36f * sin(rad)).toFloat(), c.y - (s * 0.36f * cos(rad)).toFloat())
                drawLine(color, start, end, strokeWidth = w, cap = StrokeCap.Round)
                arrowHead(end, angle, s, color)
            }
            else -> {
                val keep = step.maneuver == Maneuver.KeepLeft || step.maneuver == Maneuver.KeepRight
                val pivot = if (angle == 0f) p(0.5f, 0.5f) else p(0.5f, if (keep) 0.58f else 0.52f)
                if (keep) {
                    // The lane not taken, faint.
                    drawLine(color.copy(alpha = 0.3f), pivot, p(0.5f, 0.18f), strokeWidth = w * 0.8f, cap = StrokeCap.Round)
                }
                if (step.maneuver == Maneuver.Flyover) {
                    // A little bridge under the arrow.
                    val arch = Path().apply {
                        moveTo(p(0.12f, 0.86f).x, p(0.12f, 0.86f).y)
                        quadraticBezierTo(p(0.5f, 0.56f).x, p(0.5f, 0.56f).y, p(0.88f, 0.86f).x, p(0.88f, 0.86f).y)
                    }
                    drawPath(arch, color.copy(alpha = 0.45f), style = Stroke(width = w * 0.6f, cap = StrokeCap.Round))
                }
                val bottom = p(0.5f, 0.94f)
                if (angle == 0f) {
                    drawLine(color, bottom, p(0.5f, 0.30f), strokeWidth = w, cap = StrokeCap.Round)
                    arrowHead(p(0.5f, 0.22f), 0f, s, color)
                } else {
                    val len = s * 0.30f
                    val rad = Math.toRadians(angle.toDouble())
                    val end = Offset(pivot.x + (len * sin(rad)).toFloat(), pivot.y - (len * cos(rad)).toFloat())
                    val path = Path().apply {
                        moveTo(bottom.x, bottom.y)
                        lineTo(pivot.x, pivot.y)
                        lineTo(end.x, end.y)
                    }
                    drawPath(path, color, style = stroke)
                    val tip = Offset(pivot.x + ((len + s * 0.08f) * sin(rad)).toFloat(), pivot.y - ((len + s * 0.08f) * cos(rad)).toFloat())
                    arrowHead(tip, angle, s, color)
                }
            }
        }
    }
}

/** A filled triangle with its tip at [tip], pointing [angle] degrees clockwise from up. */
private fun DrawScope.arrowHead(tip: Offset, angle: Float, s: Float, color: Color) {
    val h = s * 0.22f
    val half = s * 0.17f
    rotate(angle, pivot = tip) {
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - half, tip.y + h)
            lineTo(tip.x + half, tip.y + h)
            close()
        }
        drawPath(path, color)
    }
}

// --------------------------------------------------------------------------- Shared parts

/** The step after [progress]'s next one, for "Then …". */
private fun followingStep(session: NavSession, progress: RouteProgress): RouteStep? =
    session.route?.steps?.getOrNull(progress.stepIndex + 1)

/** The fix moved onto the route line and turned along it, so the car sits on the road. */
private fun shownLocation(location: Location, session: NavSession): Location {
    val p = session.progress ?: return location
    if (session.phase != NavSession.Phase.Active || p.distanceFromRouteM > RouteTracker.ON_ROUTE_M) return location
    return Location(location).apply {
        latitude = p.snapped.lat
        longitude = p.snapped.lon
        bearing = p.routeBearing.toFloat()
    }
}

/** Arrow on a theme-coloured tile, as on the Google Maps turn card. */
@Composable
private fun ArrowTile(step: RouteStep, sizeU: Float, theme: DriveTheme) {
    val dims = LocalDims.current
    Box(
        Modifier
            .size(maxOf(dims.d(sizeU), 56.dp))
            .clip(theme.cardShape(dims, scale = 0.45f))
            .background(theme.turnTile),
        contentAlignment = Alignment.Center,
    ) {
        ManeuverArrow(step, theme.turnTileText, Modifier.fillMaxSize().padding(dims.d(sizeU * 0.1f)))
    }
}

// ------------------------------------------------------------------------- Navigation screen

/**
 * Full-screen turn-by-turn on Car Hub's own map: route line, the car on the road, the next
 * manoeuvre at the top (arrow, distance, road, then the one after), arrival time, time and
 * distance left, End and "Open in Google Maps" at the bottom. Sized for 1024x600 and up.
 */
@Composable
fun NavigationScreen(app: DriveApp, onBack: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val session by app.navigator.state.collectAsStateWithLifecycle()
    val gps by app.speedTracker.state.collectAsStateWithLifecycle()
    val muted by app.navigator.muted.collectAsStateWithLifecycle()
    val textureMode by app.prefs.mapTextureMode.collectAsStateWithLifecycle()
    val location = gps.location

    // Nothing to show any more (ended, or the arrival card timed out): back to the home screen.
    if (session.phase == NavSession.Phase.Idle) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
    }

    Box(Modifier.fillMaxSize().background(theme.background)) {
        if (location != null) {
            GlanceMap(
                location = shownLocation(location, session),
                styleUrl = theme.map.url,
                puckColor = theme.mapPuck.toArgb(),
                textureMode = textureMode,
                route = session.route?.points,
                routeKey = session.routeVersion,
                routeColor = theme.mapPuck.toArgb(),
                navigating = session.phase == NavSession.Phase.Active,
                modifier = Modifier.fillMaxSize(),
            )
            if (theme.mapTint.alpha > 0f) Box(Modifier.fillMaxSize().background(theme.mapTint))
        }

        val pad = dims.d(2f)
        val progress = session.progress
        val route = session.route
        when (session.phase) {
            NavSession.Phase.Active, NavSession.Phase.Arrived -> if (route != null && progress != null) {
                val arrived = session.phase == NavSession.Phase.Arrived
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(pad)
                        .fillMaxWidth(if (dims.compact) 0.62f else 0.5f),
                    verticalArrangement = Arrangement.spacedBy(dims.d(1f)),
                ) {
                    if (arrived) {
                        ArrivedBanner(session.destination?.name.orEmpty())
                    } else {
                        TurnBanner(route.steps[progress.stepIndex], progress.distanceToStepM, followingStep(session, progress))
                    }
                    if (session.rerouting) {
                        Pill(text = stringResource(R.string.nav_rerouting), background = theme.surface)
                    }
                }
                NavBottomBar(
                    session = session,
                    progress = progress,
                    muted = muted,
                    onMute = { app.navigator.setMuted(!muted) },
                    onGoogle = { app.navigator.openInGoogleMaps(context) },
                    onEnd = {
                        app.navigator.stop()
                        onBack()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(pad),
                )
            }
            NavSession.Phase.Routing -> NavMessageCard(
                title = stringResource(R.string.nav_routing, session.destination?.name.orEmpty()),
                body = null,
                busy = true,
                onRetry = null,
                onGoogle = { app.navigator.openInGoogleMaps(context) },
                onClose = {
                    app.navigator.stop()
                    onBack()
                },
                modifier = Modifier.align(Alignment.Center),
            )
            NavSession.Phase.Failed -> NavMessageCard(
                title = stringResource(R.string.nav_fail_title),
                body = failureText(context, session),
                busy = false,
                onRetry = { app.navigator.retry() },
                onGoogle = { app.navigator.openInGoogleMaps(context) },
                onClose = {
                    app.navigator.stop()
                    onBack()
                },
                modifier = Modifier.align(Alignment.Center),
            )
            NavSession.Phase.Idle -> Unit
        }

        // OpenStreetMap / OSRM credit, required by their licences.
        Text(
            GLANCE_ATTRIBUTION + " · " + stringResource(R.string.nav_routing_attribution),
            fontSize = dims.t(1.5f, min = 10f),
            color = Color(0xFF3F3A44),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(dims.d(1f))
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.75f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun failureText(context: Context, session: NavSession): String {
    val name = session.destination?.name.orEmpty()
    return when (session.failure) {
        NavSession.Failure.Network, null -> context.getString(R.string.nav_fail_network, name)
        NavSession.Failure.NoRoute -> context.getString(R.string.nav_fail_no_route, name)
        NavSession.Failure.Busy -> context.getString(R.string.nav_fail_busy)
        NavSession.Failure.Server -> context.getString(R.string.nav_fail_server)
        NavSession.Failure.NoLocation -> context.getString(R.string.nav_fail_no_location)
        NavSession.Failure.PlaceNotFound -> context.getString(R.string.nav_fail_not_found, name)
    }
}

/** Next manoeuvre: big arrow, distance and road; the one after in a slim row underneath. */
@Composable
private fun TurnBanner(step: RouteStep, distanceM: Double, then: RouteStep?) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val shape = theme.cardShape(dims, scale = 0.8f)
    Column(
        Modifier.themedCard(theme, shape, fill = theme.turnBackground, border = theme.turnBorder, accentEdge = false, blur = false),
    ) {
        Row(
            Modifier.padding(horizontal = dims.d(2f), vertical = dims.d(1.8f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(2.2f)),
        ) {
            ArrowTile(step, 15f, theme)
            Column(Modifier.weight(1f)) {
                Text(
                    navDistance(context, distanceM),
                    style = theme.numberStyle(theme.turnText),
                    fontSize = dims.t(8f, min = 30f),
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
                Text(
                    step.label ?: maneuverText(context, step),
                    color = theme.turnText,
                    fontSize = dims.t(3.6f, min = 18f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (then != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.16f))
                    .padding(horizontal = dims.d(2f), vertical = dims.d(0.9f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
            ) {
                Text(
                    theme.label(stringResource(R.string.nav_then)),
                    color = theme.turnMuted,
                    fontSize = dims.t(2.4f, min = 14f),
                    fontWeight = FontWeight.SemiBold,
                )
                ManeuverArrow(then, theme.turnText, Modifier.size(maxOf(dims.d(4.4f), 26.dp)))
                Text(
                    then.label ?: maneuverText(context, then),
                    color = theme.turnMuted,
                    fontSize = dims.t(2.4f, min = 14f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ArrivedBanner(name: String) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = theme.cardShape(dims, scale = 0.8f)
    Row(
        Modifier
            .themedCard(theme, shape, fill = theme.turnBackground, border = theme.turnBorder, accentEdge = false, blur = false)
            .padding(horizontal = dims.d(2f), vertical = dims.d(1.8f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2.2f)),
    ) {
        ArrowTile(RouteStep(Maneuver.Arrive, null, com.vikash.ampli.drive.nav.route.LatLon(0.0, 0.0), null, null, 0.0, "arrive", null), 12f, theme)
        Text(
            stringResource(R.string.nav_arrived_at, name),
            color = theme.turnText,
            fontSize = dims.t(4f, min = 20f),
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Arrival time, time left, km left; mute, Google Maps and End. */
@Composable
private fun NavBottomBar(
    session: NavSession,
    progress: RouteProgress,
    muted: Boolean,
    onMute: () -> Unit,
    onGoogle: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val shape = theme.cardShape(dims, scale = 0.8f)
    Row(
        modifier
            .heightIn(min = maxOf(dims.d(13f), 72.dp))
            .themedCard(theme, shape, fill = mapPanelFill(theme), accentEdge = false, blur = false)
            .padding(horizontal = dims.d(2.4f), vertical = dims.d(1f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2.6f)),
    ) {
        val arrived = session.phase == NavSession.Phase.Arrived
        if (!arrived) {
            Stat(etaText(progress.remainingS), theme.label(stringResource(R.string.nav_arrival)), accent = true)
            Stat(navDuration(context, progress.remainingS), null)
            Stat(navDistance(context, progress.remainingM), null)
        } else {
            Text(
                session.destination?.name.orEmpty(),
                color = theme.text,
                fontSize = dims.t(3.4f, min = 18f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        RoundIconButton(
            icon = if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
            label = stringResource(if (muted) R.string.nav_unmute else R.string.nav_mute),
            onClick = onMute,
        )
        BigButton(
            text = stringResource(R.string.nav_google_short),
            onClick = onGoogle,
            icon = Icons.Filled.Map,
            primary = false,
        )
        EndButton(onEnd)
    }
}

@Composable
private fun Stat(value: String, label: String?, accent: Boolean = false) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            value,
            style = theme.numberStyle(if (accent) theme.accentSoft else theme.text),
            fontSize = dims.t(5f, min = 22f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        if (label != null) {
            Text(label, style = theme.labelStyle(), fontSize = dims.t(2f, min = 12f), maxLines = 1)
        }
    }
}

@Composable
private fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val theme = LocalDriveTheme.current
    Box(
        Modifier
            .size(MinTouch)
            .clip(CircleShape)
            .background(theme.surfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = theme.text, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun EndButton(onClick: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Row(
        Modifier
            .heightIn(min = MinTouch)
            .clip(theme.buttonShape(dims))
            .background(theme.danger)
            .clickable(onClick = onClick)
            .padding(horizontal = dims.d(3.4f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1f)),
    ) {
        Icon(Icons.Filled.Close, contentDescription = null, tint = theme.onDanger, modifier = Modifier.size(28.dp))
        Text(
            stringResource(R.string.nav_end),
            color = theme.onDanger,
            fontSize = dims.t(3.2f, min = 18f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** Routing in progress, or a failure with Try again / Open in Google Maps. */
@Composable
private fun NavMessageCard(
    title: String,
    body: String?,
    busy: Boolean,
    onRetry: (() -> Unit)?,
    onGoogle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Column(
        modifier
            .widthIn(max = 640.dp)
            .padding(dims.d(3f))
            .themedCard(theme, theme.cardShape(dims), fill = mapPanelFill(theme), blur = false)
            .padding(dims.d(3f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        if (busy) CircularProgressIndicator(color = theme.accent, modifier = Modifier.size(maxOf(dims.d(7f), 40.dp)))
        Text(title, color = theme.text, fontSize = dims.t(3.6f, min = 20f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (body != null) {
            Text(body, color = theme.muted, fontSize = dims.t(2.8f, min = 16f), textAlign = TextAlign.Center)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)), verticalAlignment = Alignment.CenterVertically) {
            if (onRetry != null) BigButton(text = stringResource(R.string.action_retry), onClick = onRetry, icon = Icons.Filled.Refresh)
            BigButton(text = stringResource(R.string.nav_open_google), onClick = onGoogle, icon = Icons.Filled.Map, primary = onRetry == null && !busy)
            BigButton(text = stringResource(R.string.action_close), onClick = onClose, primary = false)
        }
    }
}

// --------------------------------------------------------------------------- Home card

/**
 * Compact in-app navigation card for the home screen's map pane: next manoeuvre and arrival.
 * Tapping it opens the full navigation screen.
 */
@Composable
fun NavHomeCard(session: NavSession, onClick: () -> Unit, modifier: Modifier = Modifier, large: Boolean = false) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val shape = theme.cardShape(dims, scale = 0.8f)
    val scale = if (large) 1.4f else 1f
    val progress = session.progress
    val step = progress?.let { session.route?.steps?.getOrNull(it.stepIndex) }
    Row(
        modifier = modifier
            .themedCard(theme, shape, fill = theme.turnBackground, border = theme.turnBorder, accentEdge = false, blur = false)
            .clickable(onClick = onClick)
            .padding(horizontal = dims.d(2f), vertical = dims.d(1.6f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        when {
            session.phase == NavSession.Phase.Arrived -> {
                Text(
                    stringResource(R.string.nav_arrived_at, session.destination?.name.orEmpty()),
                    color = theme.turnText,
                    fontSize = dims.t(3.2f * scale, min = 18f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            step != null && progress != null -> {
                ArrowTile(step, 9f * scale, theme)
                Column(Modifier.weight(1f)) {
                    Text(
                        navDistance(context, progress.distanceToStepM),
                        style = theme.numberStyle(theme.turnText),
                        fontSize = dims.t(4.6f * scale, min = 20f),
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                    Text(
                        step.label ?: maneuverText(context, step),
                        color = theme.turnText,
                        fontSize = dims.t(2.6f * scale, min = 14f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        etaText(progress.remainingS) + " · " + navDuration(context, progress.remainingS) + " · " +
                            navDistance(context, progress.remainingM),
                        color = theme.turnMuted,
                        fontSize = dims.t(2.2f, min = 12f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            session.phase == NavSession.Phase.Failed -> {
                Text(
                    stringResource(R.string.nav_fail_title),
                    color = theme.turnText,
                    fontSize = dims.t(3f * scale, min = 16f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
            }
            else -> {
                CircularProgressIndicator(color = theme.turnText, modifier = Modifier.size(maxOf(dims.d(5f), 28.dp)))
                Text(
                    stringResource(R.string.nav_routing, session.destination?.name.orEmpty()),
                    color = theme.turnText,
                    fontSize = dims.t(2.8f * scale, min = 15f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Route on the small home map too: the map pane passes this to [GlanceMap]. */
internal fun homeShownLocation(location: Location, session: NavSession): Location = shownLocation(location, session)
