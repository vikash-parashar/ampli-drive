package com.vikash.ampli.drive.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.car.CarMonitor
import com.vikash.ampli.drive.obd.DtcCatalog
import com.vikash.ampli.drive.obd.ObdState
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.voice.VoiceLocales
import com.vikash.ampli.drive.system.BtPermissions
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** OBD-II engine dashboard (Pro). */
@Composable
fun ObdScreen(app: DriveApp, onBack: () -> Unit, onUnlockPro: () -> Unit) {
    val dims = LocalDims.current
    val pro by app.prefs.pro.collectAsStateWithLifecycle()
    val state by app.obd.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        ScreenHeader(
            stringResource(R.string.obd_title),
            if (state.phase == ObdState.Phase.Connected) stringResource(R.string.obd_connected_to, state.deviceName ?: "OBD") else stringResource(R.string.obd_hint),
            onBack,
        )
        val body = Modifier.fillMaxWidth().weight(1f)
        when {
            !pro.isPro -> ProLocked(
                stringResource(R.string.obd_title),
                listOf(stringResource(R.string.obd_pro_1), stringResource(R.string.obd_pro_2), stringResource(R.string.obd_pro_3)),
                onUnlockPro, body,
            )
            state.phase == ObdState.Phase.Connected -> ObdDashboard(app, state, body)
            else -> ObdConnect(app, state, body)
        }
    }
}

@Composable
private fun ObdConnect(app: DriveApp, state: ObdState, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val hasPermission = remember(tick) { app.obd.hasPermission() }
    val devices = remember(tick, hasPermission) { app.obd.bondedDevices() }
    val last = app.car.store.obdAddress

    ParkToEdit(app, modifier) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
            ) {
                Text(stringResource(R.string.obd_steps), fontSize = dims.t(2.6f, min = 15f), color = theme.text)
                when (state.phase) {
                    ObdState.Phase.Connecting -> Text(stringResource(R.string.obd_connecting, state.deviceName ?: ""), fontSize = dims.t(3f, min = 17f), color = theme.accentSoft, fontWeight = FontWeight.Bold)
                    ObdState.Phase.Failed -> Text(stringResource(R.string.obd_failed), fontSize = dims.t(2.6f, min = 15f), color = theme.danger, fontWeight = FontWeight.Bold)
                    else -> Unit
                }
                if (!app.obd.bluetoothAvailable) {
                    Text(stringResource(R.string.obd_no_bluetooth), fontSize = dims.t(2.6f, min = 15f), color = theme.warning)
                }
                BigButton(
                    stringResource(R.string.obd_pair_new),
                    { startSafely(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    Modifier.fillMaxWidth(), icon = Icons.Filled.BluetoothSearching, primary = false,
                )
                Text(stringResource(R.string.obd_dongle_note), fontSize = dims.t(2.2f), color = theme.muted)
            }
            Pane(Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(dims.d(2f)),
                    verticalArrangement = Arrangement.spacedBy(dims.d(1f)),
                ) {
                    SectionLabel(stringResource(R.string.obd_paired_devices))
                    if (!hasPermission) {
                        Text(stringResource(R.string.obd_need_permission), fontSize = dims.t(2.6f, min = 15f), color = theme.text)
                        BigButton(stringResource(R.string.action_allow), { permission.launch(BtPermissions.connect) }, icon = Icons.Filled.Bluetooth)
                    } else if (devices.isEmpty()) {
                        Text(stringResource(R.string.obd_no_devices), fontSize = dims.t(2.6f, min = 15f), color = theme.muted)
                        BigButton(stringResource(R.string.action_refresh), { tick++ }, icon = Icons.Filled.Refresh, primary = false)
                    } else {
                        for (d in devices) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = MinTouch)
                                    .themedCard(theme, theme.cardShape(dims, scale = 0.6f), border = if (d.address == last) theme.accent else theme.border)
                                    .clickable {
                                        app.car.store.obdAddress = d.address
                                        app.obd.connect(d.address)
                                    }
                                    .padding(horizontal = dims.d(2f)),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
                            ) {
                                Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = theme.accentSoft)
                                Column(Modifier.weight(1f)) {
                                    Text(d.name, fontSize = dims.t(2.8f, min = 16f), color = theme.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(d.address, fontSize = dims.t(2f, min = 12f), color = theme.muted)
                                }
                                Text(stringResource(R.string.obd_connect), color = theme.accentSoft, fontWeight = FontWeight.Bold, fontSize = dims.t(2.6f, min = 15f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObdDashboard(app: DriveApp, state: ObdState, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hindi = VoiceLocales.parserLanguage(app.car.language()) == "hi"
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
            if (state.warnings.isNotEmpty()) {
                Pane(Modifier.fillMaxWidth(), fill = theme.danger) {
                    Row(Modifier.padding(dims.d(1.8f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = theme.onDanger, modifier = Modifier.size(32.dp))
                        Text(
                            state.warnings.joinToString(" ") { CarMonitor.engineWarningLine(context, it) },
                            color = theme.onDanger, fontSize = dims.t(2.8f, min = 16f), fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                Gauge(stringResource(R.string.obd_rpm), state.rpm?.roundToInt()?.toString(), "rpm", Modifier.weight(1f).fillMaxHeight())
                Gauge(stringResource(R.string.obd_speed), state.speedKmh?.toString(), "km/h", Modifier.weight(1f).fillMaxHeight())
                Gauge(
                    stringResource(R.string.obd_coolant), state.coolantC?.toString(), "°C", Modifier.weight(1f).fillMaxHeight(),
                    alert = state.coolantC != null && state.coolantC >= com.vikash.ampli.drive.obd.EngineHealth.HOT_C,
                )
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                Gauge(stringResource(R.string.obd_battery), state.volts?.let { "%.1f".format(it) }, "V", Modifier.weight(1f).fillMaxHeight(), alert = state.warnings.isNotEmpty() && state.volts != null)
                Gauge(
                    stringResource(R.string.obd_economy),
                    state.kmPerLitre?.let { "%.1f".format(it) } ?: state.litresPerHour?.let { "%.1f".format(it) },
                    if (state.kmPerLitre != null) stringResource(R.string.unit_kmpl) else "L/h",
                    Modifier.weight(1f).fillMaxHeight(),
                )
                BigButton(stringResource(R.string.obd_disconnect), { app.obd.disconnect() }, Modifier.weight(1f), icon = Icons.Filled.Close, primary = false)
            }
        }
        Pane(Modifier.weight(1f).fillMaxHeight()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(dims.d(2f)),
                verticalArrangement = Arrangement.spacedBy(dims.d(1f)),
            ) {
                SectionLabel(stringResource(R.string.obd_codes))
                when {
                    !state.dtcsRead -> Text(stringResource(R.string.obd_reading_codes), fontSize = dims.t(2.6f, min = 15f), color = theme.muted)
                    state.dtcs.isEmpty() -> Text(stringResource(R.string.obd_no_codes), fontSize = dims.t(3f, min = 17f), color = theme.ok, fontWeight = FontWeight.Bold)
                    else -> for (code in state.dtcs) {
                        Column(Modifier.fillMaxWidth().padding(vertical = dims.d(0.4f))) {
                            Text(code, style = theme.numberStyle(theme.warning, glowColor = null), fontSize = dims.t(3.2f, min = 18f))
                            Text(DtcCatalog.describe(code, hindi), fontSize = dims.t(2.6f, min = 15f), color = theme.text)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                    BigButton(stringResource(R.string.obd_read_again), { app.obd.refreshCodes() }, icon = Icons.Filled.Refresh, primary = false)
                    if (state.dtcs.isNotEmpty()) {
                        BigButton(stringResource(R.string.obd_speak), {
                            scope.launch {
                                val lang = app.car.language()
                                val res = app.car.localized(lang)
                                app.speaker.say(
                                    res.getString(R.string.obd_voice_codes, state.dtcs.size) + " " + state.dtcs.joinToString(". ") { DtcCatalog.describe(it, hindi) },
                                    lang,
                                )
                            }
                        }, icon = Icons.Filled.VolumeUp)
                    }
                }
                Text(stringResource(R.string.obd_codes_note), fontSize = dims.t(2.1f), color = theme.muted)
            }
        }
    }
}

@Composable
private fun Gauge(label: String, value: String?, unit: String, modifier: Modifier, alert: Boolean = false) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Pane(modifier, fill = if (alert) theme.danger else null) {
        Column(Modifier.fillMaxSize().padding(dims.d(1.6f)), verticalArrangement = Arrangement.Center) {
            Text(theme.label(label), style = theme.labelStyle(if (alert) theme.onDanger else theme.muted), fontSize = dims.t(2.2f), maxLines = 1)
            Text(value ?: "—", style = theme.numberStyle(if (alert) theme.onDanger else theme.text), fontSize = dims.t(7f, min = 30f), maxLines = 1)
            Text(unit, fontSize = dims.t(2.2f), color = if (alert) theme.onDanger else theme.muted)
        }
    }
}
