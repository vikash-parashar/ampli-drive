package com.vikash.ampli.drive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.car.CarMonitorService
import com.vikash.ampli.drive.car.CarSettings
import com.vikash.ampli.drive.car.FatigueTimer
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

/** Settings › Car: break reminder, crash detection, weather alerts, background monitoring, tyre limits. */
@Composable
internal fun CarSettingsCard(app: DriveApp, onOpenCar: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val s by app.car.store.settings.collectAsStateWithLifecycle()
    fun update(change: (CarSettings) -> CarSettings) {
        app.car.store.updateSettings(change)
        app.car.applySettings()
    }

    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = MinTouch),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                Box(Modifier.weight(1f)) { CardTitle(stringResource(R.string.car_settings_title), stringResource(R.string.car_settings_hint)) }
                BigButton(stringResource(R.string.car_open), onOpenCar, primary = false)
            }
            CarSwitch(stringResource(R.string.break_setting), stringResource(R.string.break_setting_hint), s.breakReminder) { v -> update { it.copy(breakReminder = v) } }
            if (s.breakReminder) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                    Text(stringResource(R.string.break_after), fontSize = dims.t(2.6f), color = theme.muted)
                    for (m in FatigueTimer.THRESHOLD_CHOICES_MIN) {
                        Chip(formatDuration(context, m * 60_000L), s.breakAfterMin == m, { update { it.copy(breakAfterMin = m) } })
                    }
                }
            }
            CarSwitch(
                stringResource(R.string.crash_setting),
                stringResource(if (app.car.hasMotionSensor) R.string.crash_setting_hint else R.string.crash_no_sensor),
                s.crashDetection,
            ) { v -> update { it.copy(crashDetection = v) } }
            if (s.crashDetection) {
                Text(stringResource(R.string.crash_tap_note), fontSize = dims.t(2.2f), color = theme.warning)
            }
            CarSwitch(stringResource(R.string.weather_setting), stringResource(R.string.weather_setting_hint), s.weatherAlerts) { v -> update { it.copy(weatherAlerts = v) } }
            CarSwitch(stringResource(R.string.monitor_setting), stringResource(R.string.monitor_setting_hint), s.backgroundMonitor) { v ->
                update { it.copy(backgroundMonitor = v) }
                if (v) CarMonitorService.start(context) else CarMonitorService.stop(context)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                Text(stringResource(R.string.tyres_low_setting), fontSize = dims.t(2.6f), color = theme.muted)
                for (p in listOf(24, 26, 28, 30)) Chip("$p psi", s.tpmsLowPsi == p, { update { it.copy(tpmsLowPsi = p) } })
            }
        }
    }
}

@Composable
private fun CarSwitch(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val dims = LocalDims.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Box(Modifier.weight(1f)) { CardTitle(title, hint) }
        ThemedSwitch(checked, onChange)
    }
}
