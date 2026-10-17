package com.vikash.ampli.drive.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.cameras.CameraGeometry
import com.vikash.ampli.drive.car.CarMonitor
import com.vikash.ampli.drive.car.DueLevel
import com.vikash.ampli.drive.car.ParkedSpot
import com.vikash.ampli.drive.obd.ObdState
import com.vikash.ampli.drive.ui.theme.CardStyle
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Data-entry screens lock above this speed. */
const val PARK_TO_EDIT_KMH = 10f

/**
 * Whether the text-entry lock is on. A missing speed (GPS lost in a tunnel or under a flyover)
 * counts as moving for [DriveLock.STALE_MS] after the car was last seen moving: the lock fails
 * closed. A unit that has never had a moving fix (garage, no GPS) stays editable.
 */
object DriveLock {
    const val STALE_MS = 2 * 60_000L

    /** elapsedRealtime of the last fix above [PARK_TO_EDIT_KMH]; 0 = never. */
    @Volatile var lastMovingAtMs = 0L

    fun moving(speedKmh: Float?, lastMovingAtMs: Long, nowMs: Long): Boolean = when {
        speedKmh != null -> speedKmh > PARK_TO_EDIT_KMH
        else -> lastMovingAtMs > 0L && nowMs - lastMovingAtMs in 0 until STALE_MS
    }
}

@Composable
fun isMoving(app: DriveApp): Boolean {
    val gps by app.speedTracker.state.collectAsStateWithLifecycle()
    val speed = gps.speedKmh
    if (speed != null && speed > PARK_TO_EDIT_KMH) DriveLock.lastMovingAtMs = android.os.SystemClock.elapsedRealtime()
    // Re-checks every few seconds while there is no speed, so the lock lifts after STALE_MS.
    val now by androidx.compose.runtime.produceState(android.os.SystemClock.elapsedRealtime(), speed == null) {
        while (speed == null) {
            kotlinx.coroutines.delay(5_000L)
            value = android.os.SystemClock.elapsedRealtime()
        }
    }
    return DriveLock.moving(speed, DriveLock.lastMovingAtMs, now)
}

/** Shows [content] only while parked; otherwise a big "Park to edit" message. Both fill [modifier]. */
@Composable
fun ParkToEdit(app: DriveApp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val moving = isMoving(app)
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Box(modifier) {
        if (!moving) {
            content()
        } else {
            Pane(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().padding(dims.d(3f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dims.d(1.6f), Alignment.CenterVertically),
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = theme.warning, modifier = Modifier.size(56.dp))
                    Text(stringResource(R.string.park_to_edit), fontSize = dims.t(5f, min = 26f), fontWeight = FontWeight.Bold, color = theme.text)
                    Text(stringResource(R.string.park_to_edit_hint), fontSize = dims.t(2.6f, min = 16f), color = theme.muted, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------ Intents

fun startSafely(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    Toast.makeText(context, context.getString(R.string.error_cannot_open_app), Toast.LENGTH_SHORT).show()
    false
} catch (_: SecurityException) {
    Toast.makeText(context, context.getString(R.string.error_cannot_open_app), Toast.LENGTH_SHORT).show()
    false
}

/** Text to the share sheet (WhatsApp, Messages, Bluetooth to the paired phone…). */
fun shareText(context: Context, text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startSafely(context, Intent.createChooser(send, title))
}

/** Google Maps search near the car, e.g. "dhaba" or "petrol pump". */
fun openMapsSearch(context: Context, query: String, near: Location?) {
    val at = near?.let { "%.5f,%.5f".format(java.util.Locale.US, it.latitude, it.longitude) } ?: "0,0"
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$at?q=" + Uri.encode(query)))
    try {
        context.startActivity(Intent(geo).setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        if (!startSafely(context, geo)) {
            startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query))))
        }
    }
}

/** "4:32 PM" or "Yesterday 4:32 PM"-style label for a past time. */
fun clockLabel(context: Context, time: Long): String {
    val zone = ZoneId.systemDefault()
    val t = Instant.ofEpochMilli(time).atZone(zone)
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    val clock = DateTimeFormatter.ofPattern(pattern, context.resources.configuration.locales[0]).format(t)
    return if (DateUtils.isToday(time)) clock else DateTimeFormatter.ofPattern("d MMM", context.resources.configuration.locales[0]).format(t) + " " + clock
}

fun distanceLabel(context: Context, meters: Double): String =
    if (meters < 1000) context.getString(R.string.car_distance_m, (meters / 10).roundToInt() * 10)
    else context.getString(R.string.car_distance_km, "%.1f".format(context.resources.configuration.locales[0], meters / 1000.0))

private fun parkedDistance(spot: ParkedSpot, here: Location?): Double? =
    here?.let { CameraGeometry.distanceM(it.latitude, it.longitude, spot.lat, spot.lon) }

// ------------------------------------------------------------------------------ Home pieces

/**
 * Status-row chip for the most urgent car item: weather hazard, tyre alert, engine warning,
 * papers or service due. Nothing shows when all is well. Tap opens the Car hub.
 */
@Composable
fun CarChip(app: DriveApp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val weather by app.car.weather.collectAsStateWithLifecycle()
    val tyres by app.tpms.alerts.collectAsStateWithLifecycle()
    val obd by app.obd.state.collectAsStateWithLifecycle()
    val papers by app.car.store.papers.collectAsStateWithLifecycle()
    val services by app.car.store.services.collectAsStateWithLifecycle()
    val odo by app.car.store.odometerKm.collectAsStateWithLifecycle()
    val pro by app.prefs.pro.collectAsStateWithLifecycle()
    val settings by app.car.store.settings.collectAsStateWithLifecycle()
    // Re-evaluated when the inputs change; the odometer is rounded so this isn't every metre.
    val odoKm = (odo / 10).toInt()
    val due = remember(papers, services, odoKm) { app.car.dueItems(context) }

    val tyre = if (pro.isPro) tyres.entries.firstOrNull { it.value.isNotEmpty() } else null
    val hazard = if (settings.weatherAlerts) weather?.hazards?.firstOrNull() else null
    val (text, danger) = when {
        tyre != null -> {
            val reading = app.tpms.wheelReadings()[tyre.key]?.reading?.psi ?: 0.0
            CarMonitor.tyreAlertLine(context, tyre.key, tyre.value.first(), reading) to true
        }
        obd.warnings.isNotEmpty() -> CarMonitor.engineWarningLine(context, obd.warnings.first()) to true
        hazard != null -> app.car.hazardLine(context, hazard.first, hazard.second) to false
        due.isNotEmpty() -> CarMonitor.dueLine(context, due.first()) to (due.first().status.level == DueLevel.Overdue)
        else -> return
    }
    val shape = if (theme.radius <= 0f || theme.cardStyle == CardStyle.Skewed) RectangleShape else CircleShape
    val bg = if (danger) theme.danger else theme.warning
    val fg = if (danger) theme.onDanger else Color(0xFF111111)
    Box(modifier.heightIn(min = 56.dp).widthIn(max = 280.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(shape).background(bg).padding(horizontal = dims.d(1.8f), vertical = dims.d(0.9f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(1f)),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = fg, modifier = Modifier.size(dims.d(2.8f)))
            Text(text, fontSize = dims.t(2.4f, min = 13f), color = fg, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** True when the dock's Car button should carry a red dot. */
@Composable
fun carNeedsAttention(app: DriveApp): Boolean {
    val context = LocalContext.current
    val papers by app.car.store.papers.collectAsStateWithLifecycle()
    val services by app.car.store.services.collectAsStateWithLifecycle()
    val odo by app.car.store.odometerKm.collectAsStateWithLifecycle()
    val tyres by app.tpms.alerts.collectAsStateWithLifecycle()
    val obd by app.obd.state.collectAsStateWithLifecycle()
    val odoKm = (odo / 10).toInt()
    val due = remember(papers, services, odoKm) { app.car.dueItems(context).isNotEmpty() }
    return due || tyres.values.any { it.isNotEmpty() } || obd.warnings.isNotEmpty() || obd.dtcs.isNotEmpty()
}

/**
 * Over the home screen: the "time for a chai break" card. It stays until the driver snoozes,
 * picks a stop, or parks for 10 minutes.
 */
@Composable
fun CarAlertHost(app: DriveApp) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val due by app.car.breakDue.collectAsStateWithLifecycle()
    val driving by app.car.drivingMs.collectAsStateWithLifecycle()
    if (!due) return
    Box(Modifier.fillMaxSize().padding(bottom = dims.d(18f)), contentAlignment = Alignment.BottomCenter) {
        Row(
            modifier = Modifier
                .widthIn(max = 900.dp)
                .themedCard(theme, theme.cardShape(dims, scale = 0.8f), fill = Brush.linearGradient(listOf(theme.surfaceHigh, theme.surfaceHigh)), border = theme.accent)
                .padding(horizontal = dims.d(2.6f), vertical = dims.d(1.8f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(theme.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Coffee, contentDescription = null, tint = theme.onAccent, modifier = Modifier.size(32.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.break_title), fontSize = dims.t(3.6f, min = 20f), fontWeight = FontWeight.Bold, color = theme.text, maxLines = 1)
                Text(
                    stringResource(R.string.break_driving_for, formatDuration(context, driving)),
                    fontSize = dims.t(2.4f, min = 14f), color = theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val here = app.speedTracker.state.value.location
            BigButton(stringResource(R.string.break_find_dhaba), { openMapsSearch(context, "dhaba", here); app.car.snoozeBreak() }, icon = Icons.Filled.Restaurant)
            BigButton(stringResource(R.string.break_find_fuel), { openMapsSearch(context, "petrol pump", here); app.car.snoozeBreak() }, icon = Icons.Filled.LocalGasStation, primary = false)
            BigButton(stringResource(R.string.break_snooze), { app.car.snoozeBreak() }, icon = Icons.Filled.Snooze, primary = false)
        }
    }
}

/** "1 h 20 min". */
fun formatDuration(context: Context, ms: Long): String {
    val totalMin = (ms / 60_000L).toInt()
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h == 0 -> context.getString(R.string.duration_min, m)
        m == 0 -> context.getString(R.string.duration_h, h)
        else -> context.getString(R.string.duration_h_min, h, m)
    }
}

/** On the home map: where the car is parked, while parked. Tap opens the parked-car screen. */
@Composable
fun ParkedPill(app: DriveApp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val theme = LocalDriveTheme.current
    val spot by app.car.store.parked.collectAsStateWithLifecycle()
    val gps by app.speedTracker.state.collectAsStateWithLifecycle()
    val s = spot ?: return
    if ((gps.speedKmh ?: 0f) > PARK_TO_EDIT_KMH) return
    val d = parkedDistance(s, gps.location)
    val text = if (d != null && d > 40) {
        stringResource(R.string.parked_pill_away, distanceLabel(context, d))
    } else {
        stringResource(R.string.parked_pill_since, clockLabel(context, s.time))
    }
    Pill(text = text, icon = Icons.Filled.LocalParking, iconTint = theme.accentSoft, background = theme.surface, onClick = onClick, modifier = modifier)
}

// ------------------------------------------------------------------------------ Car hub

enum class CarDest { Parked, Papers, Expenses, Obd, Tyres, Settings }

@Composable
fun CarHubScreen(app: DriveApp, onBack: () -> Unit, onOpen: (CarDest) -> Unit) {
    val dims = LocalDims.current
    val context = LocalContext.current
    val settings by app.car.store.settings.collectAsStateWithLifecycle()
    val parked by app.car.store.parked.collectAsStateWithLifecycle()
    val driving by app.car.drivingMs.collectAsStateWithLifecycle()
    val weather by app.car.weather.collectAsStateWithLifecycle()
    val weatherFailed by app.car.weatherFailed.collectAsStateWithLifecycle()
    val odo by app.car.store.odometerKm.collectAsStateWithLifecycle()
    val papers by app.car.store.papers.collectAsStateWithLifecycle()
    val services by app.car.store.services.collectAsStateWithLifecycle()
    val fills by app.car.store.fills.collectAsStateWithLifecycle()
    val obd by app.obd.state.collectAsStateWithLifecycle()
    val tyres by app.tpms.alerts.collectAsStateWithLifecycle()
    val pro by app.prefs.pro.collectAsStateWithLifecycle()
    val gps by app.speedTracker.state.collectAsStateWithLifecycle()
    val odoKm = (odo / 10).toInt()
    val due = remember(papers, services, odoKm) { app.car.dueItems(context) }
    val avg = remember(fills) { com.vikash.ampli.drive.car.Mileage.average(fills) }

    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        ScreenHeader(
            title = stringResource(R.string.car_title),
            hint = stringResource(R.string.car_odometer_value, "%,d".format(odo.toLong())),
            onBack = onBack,
        )
        val tiles: List<@Composable (Modifier) -> Unit> = listOf(
            { m ->
                val d = parked?.let { parkedDistance(it, gps.location) }
                CarTile(
                    stringResource(R.string.parked_title), Icons.Filled.LocalParking,
                    when {
                        parked == null -> stringResource(R.string.parked_none)
                        d != null && d > 40 -> stringResource(R.string.parked_pill_away, distanceLabel(context, d))
                        else -> stringResource(R.string.parked_pill_since, clockLabel(context, parked!!.time))
                    },
                    onClick = { onOpen(CarDest.Parked) }, modifier = m,
                )
            },
            { m ->
                CarTile(
                    stringResource(R.string.papers_title), Icons.Filled.Description,
                    if (due.isEmpty()) stringResource(R.string.papers_all_ok) else CarMonitor.dueLine(context, due.first()),
                    alert = due.isNotEmpty(), danger = due.firstOrNull()?.status?.level == DueLevel.Overdue,
                    onClick = { onOpen(CarDest.Papers) }, modifier = m,
                )
            },
            { m ->
                CarTile(
                    stringResource(R.string.expenses_title), Icons.Filled.CurrencyRupee,
                    avg?.let { stringResource(R.string.expenses_avg_kmpl, "%.1f".format(it)) } ?: stringResource(R.string.expenses_tile_hint),
                    onClick = { onOpen(CarDest.Expenses) }, modifier = m,
                )
            },
            { m ->
                CarTile(
                    stringResource(R.string.obd_title), Icons.Filled.Build,
                    when {
                        !pro.isPro -> stringResource(R.string.car_pro_feature)
                        obd.phase == ObdState.Phase.Connected && obd.dtcs.isNotEmpty() -> stringResource(R.string.obd_codes_count, obd.dtcs.size)
                        obd.phase == ObdState.Phase.Connected -> stringResource(R.string.obd_connected)
                        else -> stringResource(R.string.obd_not_connected)
                    },
                    alert = obd.warnings.isNotEmpty() || obd.dtcs.isNotEmpty(), danger = obd.warnings.isNotEmpty(),
                    locked = !pro.isPro, onClick = { onOpen(CarDest.Obd) }, modifier = m,
                )
            },
            { m ->
                val bad = tyres.values.any { it.isNotEmpty() }
                CarTile(
                    stringResource(R.string.tyres_title), Icons.Filled.TireRepair,
                    when {
                        !pro.isPro -> stringResource(R.string.car_pro_feature)
                        bad -> stringResource(R.string.tyres_check)
                        else -> stringResource(R.string.tyres_tile_hint)
                    },
                    alert = bad && pro.isPro, danger = bad && pro.isPro, locked = !pro.isPro,
                    onClick = { onOpen(CarDest.Tyres) }, modifier = m,
                )
            },
            { m ->
                CarTile(
                    stringResource(R.string.break_tile), Icons.Filled.Coffee,
                    when {
                        !settings.breakReminder -> stringResource(R.string.car_off)
                        driving > 60_000L -> stringResource(R.string.break_driving_for, formatDuration(context, driving))
                        else -> stringResource(R.string.break_every, formatDuration(context, settings.breakAfterMin * 60_000L))
                    },
                    onClick = { onOpen(CarDest.Settings) }, modifier = m,
                )
            },
            { m ->
                val hz = weather?.hazards?.firstOrNull()
                CarTile(
                    stringResource(R.string.weather_title), Icons.Filled.Cloud,
                    when {
                        !settings.weatherAlerts -> stringResource(R.string.car_off)
                        hz != null -> app.car.hazardLine(context, hz.first, hz.second)
                        weather != null -> stringResource(R.string.weather_clear)
                        weatherFailed -> stringResource(R.string.weather_offline)
                        else -> stringResource(R.string.weather_waiting)
                    },
                    alert = hz != null && settings.weatherAlerts,
                    onClick = { app.car.refreshWeather(); onOpen(CarDest.Settings) }, modifier = m,
                )
            },
            { m ->
                CarTile(
                    stringResource(R.string.crash_tile), Icons.Filled.Sensors,
                    when {
                        !settings.crashDetection -> stringResource(R.string.car_off)
                        app.car.hasMotionSensor -> stringResource(R.string.crash_on_sensor)
                        else -> stringResource(R.string.crash_on_gps_only)
                    },
                    onClick = { onOpen(CarDest.Settings) }, modifier = m,
                )
            },
        )
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
            for (row in tiles.chunked(4)) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                    for (tile in row) tile(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun CarTile(
    title: String,
    icon: ImageVector,
    status: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alert: Boolean = false,
    danger: Boolean = false,
    locked: Boolean = false,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val accent = when {
        danger -> theme.danger
        alert -> theme.warning
        else -> theme.accentSoft
    }
    Column(
        modifier = modifier
            .heightIn(min = 96.dp)
            .themedCard(theme, theme.cardShape(dims, scale = 0.8f), border = if (alert || danger) accent else theme.border)
            .clickable(onClick = onClick)
            .padding(dims.d(2f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(0.8f), Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1f))) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(maxOf(dims.d(5f), 28.dp)))
            Spacer(Modifier.weight(1f))
            if (locked) Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.theme_locked), tint = Color(0xFFFBBF24), modifier = Modifier.size(22.dp))
        }
        Text(title, fontSize = dims.t(3f, min = 17f), fontWeight = FontWeight.Bold, color = theme.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(status, fontSize = dims.t(2.3f, min = 14f), color = if (alert || danger) accent else theme.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ------------------------------------------------------------------------------ Parked car

@Composable
fun ParkedScreen(app: DriveApp, onBack: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val spot by app.car.store.parked.collectAsStateWithLifecycle()
    val gps by app.speedTracker.state.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000L)
            value = System.currentTimeMillis()
        }
    }
    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        ScreenHeader(stringResource(R.string.parked_title), stringResource(R.string.parked_hint), onBack)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            Pane(Modifier.weight(1.2f).fillMaxHeight()) {
                Column(
                    Modifier.fillMaxSize().padding(dims.d(3f)),
                    verticalArrangement = Arrangement.spacedBy(dims.d(1.4f), Alignment.CenterVertically),
                ) {
                    Icon(Icons.Filled.LocalParking, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(64.dp))
                    val s = spot
                    if (s == null) {
                        Text(stringResource(R.string.parked_none), fontSize = dims.t(4f, min = 22f), fontWeight = FontWeight.Bold, color = theme.text)
                        Text(stringResource(R.string.parked_none_hint), fontSize = dims.t(2.6f, min = 16f), color = theme.muted)
                    } else {
                        val d = parkedDistance(s, gps.location)
                        Text(
                            stringResource(R.string.parked_at, clockLabel(context, s.time)),
                            fontSize = dims.t(4.4f, min = 24f), fontWeight = FontWeight.Bold, color = theme.text,
                        )
                        Text(
                            stringResource(R.string.parked_ago, DateUtils.getRelativeTimeSpanString(s.time, now, DateUtils.MINUTE_IN_MILLIS).toString()),
                            fontSize = dims.t(2.8f, min = 16f), color = theme.muted,
                        )
                        if (d != null) {
                            Text(stringResource(R.string.parked_pill_away, distanceLabel(context, d)), fontSize = dims.t(3.4f, min = 20f), color = theme.accentSoft, fontWeight = FontWeight.SemiBold)
                        }
                        Text("%.5f, %.5f".format(s.lat, s.lon) + (s.accuracyM?.let { " · ±${it.roundToInt()} m" } ?: ""), fontSize = dims.t(2.2f), color = theme.muted)
                    }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
            ) {
                val s = spot
                if (s != null) {
                    BigButton(
                        stringResource(R.string.parked_share),
                        {
                            val text = context.getString(R.string.parked_share_text, clockLabel(context, s.time), s.mapsUrl)
                            shareText(context, text, context.getString(R.string.parked_share))
                        },
                        Modifier.fillMaxWidth(), icon = Icons.Filled.Share,
                    )
                    BigButton(
                        stringResource(R.string.parked_open_maps),
                        { startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(s.mapsUrl))) },
                        Modifier.fillMaxWidth(), icon = Icons.Filled.Map, primary = false,
                    )
                }
                BigButton(
                    stringResource(R.string.parked_save_here),
                    {
                        val ok = app.car.parkHere()
                        Toast.makeText(context, context.getString(if (ok) R.string.parked_saved else R.string.camera_report_no_fix), Toast.LENGTH_SHORT).show()
                    },
                    Modifier.fillMaxWidth(), icon = Icons.Filled.MyLocation, primary = false,
                )
                if (s != null) {
                    BigButton(stringResource(R.string.parked_clear), { app.car.store.setParked(null) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Delete, primary = false)
                }
                Text(stringResource(R.string.parked_how), fontSize = dims.t(2.2f), color = theme.muted)
            }
        }
    }
}

/** Shown in place of a Pro screen on the free tier: what it does, and the unlock button. */
@Composable
fun ProLocked(title: String, points: List<String>, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Pane(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(dims.d(3f)),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(36.dp))
                Text(title, fontSize = dims.t(4f, min = 22f), fontWeight = FontWeight.Bold, color = theme.text)
            }
            for (p in points) {
                Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                    Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(22.dp))
                    Text(p, fontSize = dims.t(2.6f, min = 16f), color = theme.text)
                }
            }
            BigButton(stringResource(R.string.car_unlock_pro), onUnlock, icon = Icons.Filled.Lock)
        }
    }
}

/** The Pro purchase sheet, opened from a locked car feature. */
@Composable
fun ProUnlockSheet(app: DriveApp, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val price by app.billing.price.collectAsStateWithLifecycle()
    ProSheet(
        price = price,
        onBuy = {
            val activity = context.findActivity()
            if (activity == null || !app.billing.purchase(activity)) {
                Toast.makeText(context, context.getString(R.string.pro_unavailable), Toast.LENGTH_LONG).show()
            } else {
                onDismiss()
            }
        },
        onRestore = {
            if (!app.billing.restore()) Toast.makeText(context, context.getString(R.string.pro_unavailable), Toast.LENGTH_LONG).show()
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}
