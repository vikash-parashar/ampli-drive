package com.vikash.ampli.drive.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.cameras.AheadItem
import com.vikash.ampli.drive.cameras.AheadKind
import com.vikash.ampli.drive.cameras.CameraAlert
import com.vikash.ampli.drive.cameras.RadarFeature
import com.vikash.ampli.drive.cameras.RadarTier
import com.vikash.ampli.drive.cameras.ReportKind
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Speed cameras and police checks (like Radarbot): the camera ahead with its limit roundel, your
 * speed (red when over) and a distance countdown; on the right everything coming up on this road
 * (Pro); and the report sheet. Fixed cameras are free; see [RadarTier] for the rest.
 */
@Composable
fun RadarScreen(app: DriveApp, onBack: () -> Unit, onUnlockPro: () -> Unit) {
    val context = LocalContext.current
    val alert by app.cameraAlerts.current.collectAsStateWithLifecycle()
    val limit by app.cameraAlerts.speedLimitKmh.collectAsStateWithLifecycle()
    val gps by app.speedTracker.state.collectAsStateWithLifecycle()
    val pro by app.prefs.pro.collectAsStateWithLifecycle()
    val distance by app.prefs.cameraDistance.collectAsStateWithLifecycle()
    val ahead by app.radarAhead.items.collectAsStateWithLifecycle()
    val located by app.radarAhead.located.collectAsStateWithLifecycle()
    val navigating by app.navigator.state.collectAsStateWithLifecycle()
    var reporting by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf<ReportKind?>(null) }
    val aheadAllowed = RadarTier.allowed(RadarFeature.AheadList, pro.isPro)
    DisposableEffect(aheadAllowed) {
        if (aheadAllowed) app.radarAhead.start()
        onDispose { if (aheadAllowed) app.radarAhead.stop() }
    }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000L)
            value = System.currentTimeMillis()
        }
    }
    val moving = gps.location?.hasBearing() == true && (gps.speedKmh ?: 0f) >= 10f
    RadarContent(
        alert = alert,
        speedKmh = gps.speedKmh?.roundToInt(),
        limitKmh = limit ?: alert?.limitKmh,
        alertDistanceM = distance,
        ahead = if (located) ahead else null,
        aheadMode = when {
            navigating.visible && navigating.route != null -> AheadMode.Route
            moving -> AheadMode.Heading
            else -> AheadMode.Parked
        },
        aheadLocked = !aheadAllowed,
        reportLocked = !RadarTier.allowed(RadarFeature.Reports, pro.isPro),
        now = now,
        onReport = { if (RadarTier.allowed(RadarFeature.Reports, pro.isPro)) reporting = true else onUnlockPro() },
        onUnlockPro = onUnlockPro,
        onBack = onBack,
    )
    if (reporting) {
        ReportSheet(
            reported = reported,
            onReport = { kind ->
                val here = app.speedTracker.state.value
                val loc = here.location
                if (loc == null) {
                    Toast.makeText(context, context.getString(R.string.camera_report_no_fix), Toast.LENGTH_LONG).show()
                } else {
                    app.roadReports.add(kind, loc, here.speedKmh)
                    reported = kind
                }
            },
            onDismiss = {
                reporting = false
                reported = null
            },
        )
    }
}

/** How "Coming up on this road" was worked out, for its heading. */
enum class AheadMode { Route, Heading, Parked }

@Composable
internal fun cameraKindLabel(kind: CameraAlert.Kind): String = stringResource(
    when (kind) {
        CameraAlert.Kind.FixedCamera -> R.string.radar_item_fixed
        CameraAlert.Kind.AverageSpeedStart -> R.string.camera_average_start
        CameraAlert.Kind.AverageSpeedZone -> R.string.camera_average_in
        CameraAlert.Kind.RedLight -> R.string.radar_item_red
        CameraAlert.Kind.MobileCamera -> R.string.radar_item_mobile
        CameraAlert.Kind.Police -> R.string.radar_item_police
        CameraAlert.Kind.Accident -> R.string.radar_item_accident
        CameraAlert.Kind.Jam -> R.string.radar_item_jam
    },
)

private fun kindIcon(kind: CameraAlert.Kind): ImageVector = when (kind) {
    CameraAlert.Kind.FixedCamera -> Icons.Filled.PhotoCamera
    CameraAlert.Kind.AverageSpeedStart, CameraAlert.Kind.AverageSpeedZone -> Icons.Filled.Timer
    CameraAlert.Kind.RedLight -> Icons.Filled.Traffic
    CameraAlert.Kind.MobileCamera -> Icons.Filled.Videocam
    CameraAlert.Kind.Police -> Icons.Filled.LocalPolice
    CameraAlert.Kind.Accident -> Icons.Filled.Warning
    CameraAlert.Kind.Jam -> Icons.Filled.DirectionsCar
}

private fun aheadIcon(kind: AheadKind): ImageVector = when (kind) {
    AheadKind.FixedCamera -> Icons.Filled.PhotoCamera
    AheadKind.RedLight -> Icons.Filled.Traffic
    AheadKind.AverageZone -> Icons.Filled.Timer
    AheadKind.MobileCamera -> Icons.Filled.Videocam
    AheadKind.Police -> Icons.Filled.LocalPolice
    AheadKind.Accident -> Icons.Filled.Warning
    AheadKind.Jam -> Icons.Filled.DirectionsCar
}

@Composable
private fun aheadLabel(kind: AheadKind): String = stringResource(
    when (kind) {
        AheadKind.FixedCamera -> R.string.radar_item_fixed
        AheadKind.RedLight -> R.string.radar_item_red
        AheadKind.AverageZone -> R.string.radar_item_zone
        AheadKind.MobileCamera -> R.string.radar_item_mobile
        AheadKind.Police -> R.string.radar_item_police
        AheadKind.Accident -> R.string.radar_item_accident
        AheadKind.Jam -> R.string.radar_item_jam
    },
)

@Composable
private fun distanceLabel(m: Double): String {
    if (m < 1_000) return stringResource(R.string.camera_distance, ((m / 10).roundToInt() * 10))
    val locale = LocalContext.current.resources.configuration.locales[0]
    return stringResource(R.string.fuel_km, String.format(locale, if (m < 10_000) "%.1f" else "%.0f", m / 1000.0))
}

/** The radar screen without the app: what the screenshot tests render. [ahead] null = no GPS yet. */
@Composable
fun RadarContent(
    alert: CameraAlert?,
    speedKmh: Int?,
    limitKmh: Int?,
    alertDistanceM: Int,
    ahead: List<AheadItem>?,
    aheadMode: AheadMode,
    aheadLocked: Boolean,
    reportLocked: Boolean,
    now: Long,
    onReport: () -> Unit,
    onUnlockPro: () -> Unit,
    onBack: () -> Unit,
) {
    val dims = LocalDims.current
    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        ScreenHeader(stringResource(R.string.radar_title), if (dims.compact) null else stringResource(R.string.radar_hint), onBack)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            AlertPane(alert, speedKmh, limitKmh, alertDistanceM, reportLocked, onReport, Modifier.weight(1.3f).fillMaxHeight())
            if (aheadLocked) {
                ProLocked(
                    stringResource(R.string.radar_ahead_title),
                    listOf(stringResource(R.string.radar_pro_1), stringResource(R.string.radar_pro_2)),
                    onUnlockPro,
                    Modifier.weight(1f).fillMaxHeight(),
                )
            } else {
                AheadPane(ahead, aheadMode, now, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun AlertPane(
    alert: CameraAlert?,
    speedKmh: Int?,
    limitKmh: Int?,
    alertDistanceM: Int,
    reportLocked: Boolean,
    onReport: () -> Unit,
    modifier: Modifier,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val over = speedKmh != null && limitKmh != null && speedKmh > limitKmh
    val hot = over || alert?.overLimit == true
    val shape = theme.cardShape(dims)
    Pane(modifier.then(if (hot) Modifier.border(3.dp, theme.danger, shape) else Modifier)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(dims.d(2.6f)),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.8f), Alignment.CenterVertically),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2.6f))) {
                LimitBadge(limitKmh, maxOf(dims.d(15f), 72.dp))
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            speedKmh?.toString() ?: "--",
                            style = theme.numberStyle(if (hot) theme.danger else theme.text, glowColor = null),
                            fontSize = dims.t(11f, min = 44f),
                            maxLines = 1,
                        )
                        Text(
                            " " + stringResource(R.string.speed_unit),
                            color = theme.muted,
                            fontSize = dims.t(2.6f, min = 15f),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = dims.d(1.4f)),
                        )
                    }
                    val note = when {
                        over -> stringResource(R.string.radar_over, speedKmh!! - limitKmh!!)
                        limitKmh != null -> stringResource(R.string.radar_under)
                        else -> null
                    }
                    if (note != null) {
                        Text(note, color = if (over) theme.danger else theme.muted, fontSize = dims.t(2.4f, min = 15f), fontWeight = if (over) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            if (alert != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                    Icon(kindIcon(alert.kind), contentDescription = null, tint = if (hot) theme.danger else theme.accentSoft, modifier = Modifier.size(30.dp))
                    Text(cameraKindLabel(alert.kind), color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(3.2f, min = 18f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val detail = buildList {
                    if (alert.description.isNotBlank()) add(alert.description)
                    if (alert.userReported) add(stringResource(R.string.camera_reported_by_you))
                    alert.reportedAt?.let { add(reportedAgo(it)) }
                    alert.averageKmh?.let { add(stringResource(R.string.radar_avg, it)) }
                }.joinToString(" · ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (detail.isNotBlank()) {
                        Text("$detail · ", color = theme.muted, fontSize = dims.t(2.3f, min = 14f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                    Text(distanceLabel(alert.distanceMeters.toDouble()), color = theme.text, fontWeight = FontWeight.ExtraBold, fontSize = dims.t(2.6f, min = 16f), maxLines = 1)
                }
                // Distance countdown: fills up as the camera gets closer.
                val full = if (alert.kind == CameraAlert.Kind.AverageSpeedZone) maxOf(alert.distanceMeters, 1) else alertDistanceM.coerceAtLeast(1)
                val fraction = (1f - alert.distanceMeters.toFloat() / full).coerceIn(0.04f, 1f)
                Box(Modifier.fillMaxWidth().height(maxOf(dims.d(1.6f), 10.dp)).clip(RoundedCornerShape(50)).background(theme.track)) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(50)).background(if (hot) theme.danger else theme.accent))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = theme.muted, modifier = Modifier.size(30.dp))
                    Text(stringResource(R.string.radar_clear), color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(3.2f, min = 18f))
                }
                Text(stringResource(R.string.radar_clear_hint, alertDistanceM), color = theme.muted, fontSize = dims.t(2.3f, min = 14f))
            }
            BigButton(
                stringResource(R.string.radar_report),
                onReport,
                Modifier.fillMaxWidth(),
                icon = if (reportLocked) Icons.Filled.Lock else Icons.Filled.Campaign,
            )
        }
    }
}

@Composable
private fun reportedAgo(time: Long, now: Long = System.currentTimeMillis()): String =
    stringResource(R.string.radar_reported_ago, ((now - time).coerceAtLeast(0L) / 60_000L).toInt())

@Composable
private fun AheadPane(ahead: List<AheadItem>?, mode: AheadMode, now: Long, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Pane(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(dims.d(1.8f)),
            verticalArrangement = Arrangement.spacedBy(dims.d(1f)),
        ) {
            Text(
                stringResource(if (mode == AheadMode.Parked) R.string.radar_ahead_nearby else R.string.radar_ahead_title),
                color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(2.7f, min = 16f),
            )
            when {
                ahead == null -> Text(stringResource(R.string.radar_waiting_gps), color = theme.muted, fontSize = dims.t(2.3f, min = 14f))
                ahead.isEmpty() -> Text(stringResource(R.string.radar_ahead_empty), color = theme.muted, fontSize = dims.t(2.3f, min = 14f))
                else -> for (item in ahead) AheadRow(item, now)
            }
        }
    }
}

@Composable
private fun AheadRow(item: AheadItem, now: Long) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = RoundedCornerShape(dims.d(1.6f))
    val report = item.reportedAt != null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.surfaceHigh.copy(alpha = 0.55f))
            .border(1.dp, theme.border, shape)
            .padding(horizontal = dims.d(1.2f), vertical = dims.d(0.9f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f)),
    ) {
        Box(
            Modifier.size(maxOf(dims.d(5f), 30.dp)).clip(RoundedCornerShape(dims.d(1.2f))).background(theme.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(aheadIcon(item.kind), contentDescription = null, tint = if (report) theme.warning else theme.accentSoft, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            val title = buildString {
                append(aheadLabel(item.kind))
                item.limitKmh?.let { append(" · ").append(stringResource(R.string.radar_limit, it)) }
            }
            Text(title, color = theme.text, fontSize = dims.t(2.2f, min = 14f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val locale = LocalContext.current.resources.configuration.locales[0]
            val sub = when {
                item.reportedAt != null -> stringResource(R.string.radar_reported_ago, ((now - item.reportedAt).coerceAtLeast(0L) / 60_000L).toInt())
                item.zoneLengthM != null -> listOf(item.name, stringResource(R.string.radar_zone_len, String.format(locale, "%.0f", item.zoneLengthM / 1000.0)))
                    .filter { it.isNotBlank() }.joinToString(" · ")
                item.userReported -> stringResource(R.string.camera_reported_by_you)
                else -> item.name
            }
            if (sub.isNotBlank()) Text(sub, color = theme.muted, fontSize = dims.t(1.9f, min = 12f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(distanceLabel(item.distanceM), color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(2.2f, min = 14f), maxLines = 1)
    }
}

/**
 * "What's ahead?": four big buttons that are safe to tap while driving. Saves a report at the car's
 * spot for two hours, on this unit only. [reported] shows the thank-you line.
 */
@Composable
fun ReportSheet(reported: ReportKind?, onReport: (ReportKind) -> Unit, onDismiss: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Pane(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(0.88f)
                // Taps on the sheet stay on the sheet.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            blur = false,
        ) {
            Column(Modifier.padding(dims.d(3f)), verticalArrangement = Arrangement.spacedBy(dims.d(1.8f))) {
                Text(stringResource(R.string.report_title), color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(3.4f, min = 20f))
                Text(stringResource(R.string.report_hint), color = theme.muted, fontSize = dims.t(2.3f, min = 14f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                    for (k in ReportKind.entries) {
                        val label = reportLabel(k)
                        Column(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1.15f)
                                .heightIn(min = MinTouch)
                                .themedTap(theme.buttonShape(dims), theme.surfaceHigh, border = if (reported == k) theme.ok else theme.border, active = reported == k, onClick = { onReport(k) }),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(dims.d(1f), Alignment.CenterVertically),
                        ) {
                            Icon(reportIcon(k), contentDescription = null, tint = if (reported == k) theme.ok else theme.accentSoft, modifier = Modifier.size(maxOf(dims.d(6f), 34.dp)))
                            Text(label, color = theme.text, fontWeight = FontWeight.ExtraBold, fontSize = dims.t(2.4f, min = 15f), maxLines = 2)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                    Text(
                        reported?.let { stringResource(R.string.report_saved, reportLabel(it)) } ?: "",
                        color = theme.ok,
                        fontWeight = FontWeight.Bold,
                        fontSize = dims.t(2.4f, min = 15f),
                        modifier = Modifier.weight(1f),
                    )
                    BigButton(stringResource(R.string.action_done), onDismiss, primary = false)
                }
            }
        }
    }
}

@Composable
private fun reportLabel(k: ReportKind): String = stringResource(
    when (k) {
        ReportKind.MobileCamera -> R.string.radar_item_mobile
        ReportKind.Police -> R.string.radar_item_police
        ReportKind.Accident -> R.string.radar_item_accident
        ReportKind.Jam -> R.string.radar_item_jam
    },
)

private fun reportIcon(k: ReportKind): ImageVector = when (k) {
    ReportKind.MobileCamera -> Icons.Filled.Videocam
    ReportKind.Police -> Icons.Filled.LocalPolice
    ReportKind.Accident -> Icons.Filled.Warning
    ReportKind.Jam -> Icons.Filled.DirectionsCar
}
