package com.vikash.ampli.drive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
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
import com.vikash.ampli.drive.prefs.AutoStartMode
import com.vikash.ampli.drive.prefs.AutoStartSettings
import com.vikash.ampli.drive.system.AppChooser
import com.vikash.ampli.drive.system.AppResolver
import com.vikash.ampli.drive.system.AppRole
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

/**
 * Settings › When the car starts (boot, ignition / ACC on, or a chosen phone connecting).
 * Off by default. See car/CarStart.kt.
 */
@Composable
internal fun CarStartCard(app: DriveApp) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val s by app.prefs.autoStart.collectAsStateWithLifecycle()
    val bt by app.bluetooth.state.collectAsStateWithLifecycle()
    fun set(v: AutoStartSettings) = app.prefs.setAutoStart(v)

    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                Box(Modifier.weight(1f)) { CardTitle(stringResource(R.string.autostart_title), stringResource(R.string.autostart_hint)) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                Chip(stringResource(R.string.autostart_off), s.mode == AutoStartMode.Off, { set(s.copy(mode = AutoStartMode.Off)) })
                Chip(stringResource(R.string.autostart_mirror), s.mode == AutoStartMode.Mirror, { set(s.copy(mode = AutoStartMode.Mirror)) })
                Chip(stringResource(R.string.autostart_maps_music), s.mode == AutoStartMode.MapsMusic, { set(s.copy(mode = AutoStartMode.MapsMusic)) })
            }
            if (s.mode != AutoStartMode.Off) {
                val automatic = stringResource(R.string.app_choice_automatic)
                if (s.mode == AutoStartMode.Mirror) {
                    AppLine(
                        label = stringResource(R.string.autostart_mirror_app),
                        value = s.mirrorPackage?.let { AppResolver.label(context, it) }
                            ?: AppResolver.bestGuess(context, AppRole.Mirror)?.let { automatic + " · " + it.label }
                            ?: automatic,
                        onChange = { AppChooser.ask(AppRole.Mirror, settingOnly = true) },
                    )
                } else {
                    AppLine(
                        label = stringResource(R.string.autostart_nav_app),
                        value = s.navPackage?.let { AppResolver.label(context, it) } ?: "$automatic · Google Maps",
                        onChange = { AppChooser.ask(AppRole.Nav, settingOnly = true) },
                    )
                    AppLine(
                        label = stringResource(R.string.music_app_title),
                        value = app.prefs.musicPackage.value?.let { AppResolver.label(context, it) } ?: automatic,
                        onChange = { AppChooser.ask(AppRole.Music, settingOnly = true) },
                    )
                    Text(stringResource(R.string.autostart_return), color = theme.text, fontSize = dims.t(2.5f, min = 15f))
                    Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                        for (sec in AutoStartSettings.RETURN_CHOICES) {
                            Chip(
                                if (sec == 0) stringResource(R.string.autostart_return_never) else stringResource(R.string.autostart_seconds, sec),
                                s.returnAfterSec == sec,
                                { set(s.copy(returnAfterSec = sec)) },
                            )
                        }
                    }
                }
                Text(stringResource(R.string.autostart_delay), color = theme.text, fontSize = dims.t(2.5f, min = 15f))
                Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                    for (sec in AutoStartSettings.DELAY_CHOICES) {
                        Chip(stringResource(R.string.autostart_seconds, sec), s.delaySec == sec, { set(s.copy(delaySec = sec)) })
                    }
                }
                // Bluetooth phone trigger: the phone connected now becomes the trigger.
                val phone = bt.connectedName
                val btText = when {
                    s.btDeviceName != null -> stringResource(R.string.autostart_bt_on, s.btDeviceName!!)
                    phone != null -> stringResource(R.string.autostart_bt_offer, phone)
                    else -> stringResource(R.string.autostart_bt_none)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = MinTouch),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
                ) {
                    Text(btText, color = theme.text, fontSize = dims.t(2.4f, min = 14f), modifier = Modifier.weight(1f))
                    when {
                        s.btDeviceName != null -> BigButton(stringResource(R.string.action_remove), { set(s.copy(btDeviceName = null)) }, primary = false)
                        phone != null -> BigButton(stringResource(R.string.action_use), { set(s.copy(btDeviceName = phone)) }, primary = false)
                    }
                }
                Text(stringResource(R.string.autostart_limits), color = theme.muted, fontSize = dims.t(2.1f, min = 12f))
            }
        }
    }
}

@Composable
private fun AppLine(label: String, value: String, onChange: () -> Unit) {
    val dims = LocalDims.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
    ) {
        Box(Modifier.weight(1f)) { CardTitle(label, value) }
        BigButton(stringResource(R.string.action_change), onChange, primary = false)
    }
}
