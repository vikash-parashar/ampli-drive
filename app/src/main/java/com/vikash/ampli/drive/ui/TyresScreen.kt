package com.vikash.ampli.drive.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.car.CarMonitor
import com.vikash.ampli.drive.tpms.SeenSensor
import com.vikash.ampli.drive.tpms.TpmsDecoders
import com.vikash.ampli.drive.tpms.TpmsMonitor
import com.vikash.ampli.drive.tpms.TyreAlert
import com.vikash.ampli.drive.tpms.Wheel
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.system.BtPermissions
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Tyre pressure (Pro): car seen from above with each wheel's pressure, and sensor pairing. */
@Composable
fun TyresScreen(app: DriveApp, onBack: () -> Unit, onUnlockPro: () -> Unit) {
    val dims = LocalDims.current
    val pro by app.prefs.pro.collectAsStateWithLifecycle()
    var pairing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        ScreenHeader(stringResource(R.string.tyres_title), stringResource(R.string.tyres_hint), onBack)
        val body = Modifier.fillMaxWidth().weight(1f)
        if (!pro.isPro) {
            ProLocked(
                stringResource(R.string.tyres_title),
                listOf(stringResource(R.string.tyres_pro_1), stringResource(R.string.tyres_pro_2), stringResource(R.string.tyres_supported)),
                onUnlockPro, body,
            )
        } else {
            TyresBody(app, pairing, { pairing = it }, body)
        }
    }
}

@Composable
private fun TyresBody(app: DriveApp, pairing: Boolean, setPairing: (Boolean) -> Unit, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val hasPermission = remember(tick) { app.tpms.hasPermission() }
    val sensors by app.tpms.sensors.collectAsStateWithLifecycle()
    val alerts by app.tpms.alerts.collectAsStateWithLifecycle()
    val wheels by app.car.store.wheels.collectAsStateWithLifecycle()
    val settings by app.car.store.settings.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(5_000L)
            value = System.currentTimeMillis()
        }
    }

    // Ask for "Nearby devices" the first time; scan fast while this screen is open.
    LaunchedEffect(Unit) { if (!app.tpms.hasPermission()) permission.launch(BtPermissions.scan) }
    DisposableEffect(hasPermission) {
        if (hasPermission) app.tpms.start(fast = true)
        onDispose {
            app.tpms.stop()
            app.car.updateTpmsScan()
        }
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        Pane(Modifier.weight(1f).fillMaxHeight()) {
            CarTopView(
                readings = Wheel.entries.associateWith { w -> wheels[w]?.let { sensors[it] } },
                alerts = alerts,
                now = now,
                modifier = Modifier.fillMaxSize().padding(dims.d(2f)),
            )
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
            if (!hasPermission) {
                Text(stringResource(R.string.tyres_need_permission), fontSize = dims.t(2.6f, min = 15f), color = theme.text)
                BigButton(stringResource(R.string.action_allow), { permission.launch(BtPermissions.scan) }, icon = Icons.Filled.Bluetooth)
            } else if (!app.tpms.bluetoothLeAvailable) {
                Text(stringResource(R.string.tyres_no_ble), fontSize = dims.t(2.6f, min = 15f), color = theme.warning)
            }
            if (!pairing) {
                for (w in Wheel.entries) {
                    val a = alerts[w].orEmpty()
                    val r = wheels[w]?.let { sensors[it] }
                    Text(
                        stringResource(CarMonitor.wheelName(w)) + ": " + when {
                            wheels[w] == null -> stringResource(R.string.tyres_not_paired)
                            r == null || now - r.time > TpmsMonitor.STALE_MS -> stringResource(R.string.tyres_no_signal)
                            a.isNotEmpty() -> a.joinToString(" · ") { CarMonitor.tyreAlertLine(context, w, it, r.reading.psi).substringAfter(": ") }
                            else -> stringResource(R.string.tyres_ok)
                        },
                        fontSize = dims.t(2.6f, min = 15f), color = if (a.isNotEmpty()) theme.danger else theme.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(stringResource(R.string.tyres_limits, settings.tpmsLowPsi, settings.tpmsHighPsi), fontSize = dims.t(2.2f), color = theme.muted)
                BigButton(stringResource(R.string.tyres_pair), { setPairing(true) }, Modifier.fillMaxWidth(), primary = false)
                Text(stringResource(R.string.tyres_supported), fontSize = dims.t(2.1f), color = theme.muted)
            } else {
                ParkToEdit(app, Modifier.fillMaxWidth().weight(1f)) {
                    PairingList(app, sensors.values.sortedByDescending { it.rssi }, wheels, now, onDone = { setPairing(false) })
                }
            }
        }
    }
}

@Composable
private fun PairingList(app: DriveApp, seen: List<SeenSensor>, wheels: Map<Wheel, String>, now: Long, onDone: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(dims.d(1f))) {
        Text(stringResource(R.string.tyres_pair_hint), fontSize = dims.t(2.4f, min = 14f), color = theme.text)
        if (seen.isEmpty()) {
            Text(stringResource(R.string.tyres_searching), fontSize = dims.t(2.6f, min = 15f), color = theme.muted)
        }
        for (s in seen) {
            val r = s.reading
            Pane(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(dims.d(1.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(0.6f))) {
                    Text(
                        "${r.psi.roundToInt()} psi · " + (r.temperatureC?.let { "${it.roundToInt()}°C · " } ?: "") + r.family,
                        fontSize = dims.t(2.6f, min = 15f), color = theme.text, fontWeight = FontWeight.SemiBold,
                    )
                    Text(r.sensorId + " · " + ((now - s.time) / 1000).coerceAtLeast(0) + " s", fontSize = dims.t(2f, min = 12f), color = theme.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(dims.d(0.8f))) {
                        for (w in Wheel.entries) {
                            Chip(w.name, wheels[w] == r.sensorId, {
                                app.tpms.forget(w)
                                app.car.store.pairWheel(w, if (wheels[w] == r.sensorId) null else r.sensorId)
                            })
                        }
                    }
                }
            }
        }
        BigButton(stringResource(R.string.action_done), onDone, Modifier.fillMaxWidth())
        Text(stringResource(R.string.tyres_supported_detail, TpmsDecoders.ALL.joinToString { it.family }), fontSize = dims.t(2f), color = theme.muted)
    }
}

/** A car outline from above with a box per wheel: psi and °C, red when alerting. */
@Composable
private fun CarTopView(readings: Map<Wheel, SeenSensor?>, alerts: Map<Wheel, Set<TyreAlert>>, now: Long, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val bodyW = w * 0.34f
            val bodyH = h * 0.9f
            val left = (w - bodyW) / 2f
            val top = (h - bodyH) / 2f
            drawRoundRect(theme.surfaceHigh, Offset(left, top), Size(bodyW, bodyH), CornerRadius(bodyW * 0.3f))
            drawRoundRect(theme.border, Offset(left, top), Size(bodyW, bodyH), CornerRadius(bodyW * 0.3f), style = Stroke(width = 3f))
            // Windscreen and rear window.
            drawRoundRect(theme.muted.copy(alpha = 0.35f), Offset(left + bodyW * 0.12f, top + bodyH * 0.2f), Size(bodyW * 0.76f, bodyH * 0.13f), CornerRadius(12f))
            drawRoundRect(theme.muted.copy(alpha = 0.25f), Offset(left + bodyW * 0.15f, top + bodyH * 0.72f), Size(bodyW * 0.7f, bodyH * 0.09f), CornerRadius(12f))
            for (wheel in Wheel.entries) {
                val bad = alerts[wheel].orEmpty().isNotEmpty()
                val x = if (wheel == Wheel.FL || wheel == Wheel.RL) left - bodyW * 0.12f else left + bodyW * 0.92f
                val y = if (wheel == Wheel.FL || wheel == Wheel.FR) top + bodyH * 0.12f else top + bodyH * 0.68f
                drawRoundRect(if (bad) theme.danger else theme.text.copy(alpha = 0.75f), Offset(x, y), Size(bodyW * 0.2f, bodyH * 0.18f), CornerRadius(8f))
            }
        }
        // Labels sit outside the wheels, left and right.
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceAround) {
            for (pair in listOf(Wheel.FL to Wheel.FR, Wheel.RL to Wheel.RR)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    WheelLabel(pair.first, readings[pair.first], alerts[pair.first].orEmpty(), now, TextAlign.Start)
                    WheelLabel(pair.second, readings[pair.second], alerts[pair.second].orEmpty(), now, TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun WheelLabel(wheel: Wheel, seen: SeenSensor?, alerts: Set<TyreAlert>, now: Long, align: TextAlign) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val stale = seen == null || now - seen.time > TpmsMonitor.STALE_MS
    val color = when {
        alerts.isNotEmpty() -> theme.danger
        stale -> theme.muted
        else -> theme.text
    }
    Column(horizontalAlignment = if (align == TextAlign.End) Alignment.End else Alignment.Start) {
        Text(wheel.name, style = theme.labelStyle(), fontSize = dims.t(2.2f))
        Text(
            if (stale) "—" else "${seen!!.reading.psi.roundToInt()}",
            style = theme.numberStyle(color, glowColor = null),
            fontSize = dims.t(7f, min = 30f),
        )
        Text(
            if (stale) "psi" else "psi · " + (seen!!.reading.temperatureC?.let { "${it.roundToInt()}°C" } ?: ""),
            fontSize = dims.t(2.2f), color = theme.muted, textAlign = align,
        )
    }
}
