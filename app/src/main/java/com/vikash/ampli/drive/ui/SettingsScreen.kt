package com.vikash.ampli.drive.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.BuildConfig
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.map.GLANCE_ATTRIBUTION
import com.vikash.ampli.drive.prefs.ThemeMode
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.ui.theme.Ampli

@Composable
fun SettingsScreen(
    app: DriveApp,
    resumeTick: Int,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val dims = LocalDims.current
    val context = LocalContext.current
    val keepScreenOn by app.prefs.keepScreenOn.collectAsStateWithLifecycle()
    val themeMode by app.prefs.themeMode.collectAsStateWithLifecycle()
    val gps by app.speedTracker.state.collectAsStateWithLifecycle()

    // Re-read every time the launcher comes back from a system settings screen.
    val isHome = remember(resumeTick) { Launchers.isDefaultHome(context) }
    val hasFine = remember(resumeTick) { granted(context, Manifest.permission.ACCESS_FINE_LOCATION) }
    val hasCoarse = remember(resumeTick) { granted(context, Manifest.permission.ACCESS_COARSE_LOCATION) }
    val hasBt = remember(resumeTick) { granted(context, Manifest.permission.BLUETOOTH_CONNECT) }
    val hasNotif = remember(resumeTick) { app.mediaHub.hasAccess() }

    Column(
        modifier = Modifier.fillMaxSize().padding(dims.d(2.4f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        ScreenHeader(title = "Settings", hint = null, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
        ) {
            SettingRow(
                title = "Default home screen",
                status = if (isHome) "Ampli Drive opens when you press Home" else "Not the default yet",
                ok = isHome,
                action = if (isHome) "Change" else "Set as default home",
                onAction = { Launchers.openHomeSettings(context) },
            )
            SettingRow(
                title = "Location",
                status = when {
                    !hasFine && hasCoarse -> "Only approximate location: the speedometer needs precise location"
                    !hasFine -> "Off: no map, speed or automatic day/night"
                    !gps.hasGps -> "Allowed, but this unit reports no GPS receiver"
                    !gps.providerEnabled -> "Allowed, but location is switched off on the unit"
                    else -> "Allowed: GPS speed and map are on"
                },
                ok = hasFine && gps.hasGps && gps.providerEnabled,
                action = when {
                    !hasFine -> "Allow"
                    !gps.providerEnabled -> "Turn on"
                    else -> null
                },
                onAction = {
                    if (!hasFine) onRequestPermissions() else Launchers.openLocationSettings(context)
                },
                secondaryAction = if (!hasFine) "App settings" else null,
                onSecondary = { Launchers.openAppDetails(context) },
            )
            SettingRow(
                title = "Notification access",
                status = if (hasNotif) {
                    "Allowed: music controls and Google Maps turns work"
                } else {
                    "Needed to show music from any app and the next turn from Google Maps"
                },
                ok = hasNotif,
                action = if (hasNotif) "Manage" else "Allow access",
                onAction = { Launchers.openNotificationAccess(context) },
            )
            SettingRow(
                title = "Bluetooth",
                status = if (hasBt) "Allowed: shows the connected phone" else "Off: the phone name won't show in the status row",
                ok = hasBt,
                action = if (hasBt) null else "Allow",
                onAction = onRequestPermissions,
            )
            ToggleRow(
                title = "Keep screen on",
                status = "While the home screen is showing",
                checked = keepScreenOn,
                onChange = app.prefs::setKeepScreenOn,
            )
            ThemeRow(selected = themeMode, onSelect = app.prefs::setThemeMode)
            InfoCard(
                title = "About",
                lines = listOf(
                    "Ampli Drive ${BuildConfig.VERSION_NAME}",
                    "Map: $GLANCE_ATTRIBUTION",
                    "Speed-limit and speed-camera data are not available yet.",
                ),
            )
        }
    }
}

private fun granted(context: android.content.Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    val dims = LocalDims.current
    val shape = RoundedCornerShape(dims.d(2.4f))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = dims.d(2.6f), vertical = dims.d(1.8f)),
    ) { content() }
}

@Composable
private fun SettingRow(
    title: String,
    status: String,
    ok: Boolean,
    action: String?,
    onAction: () -> Unit,
    secondaryAction: String? = null,
    onSecondary: () -> Unit = {},
) {
    val dims = LocalDims.current
    SettingCard {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
        ) {
            Icon(
                if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = if (ok) "OK" else "Needs attention",
                tint = if (ok) Ampli.Online else Ampli.Warning,
                modifier = Modifier.size(32.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = dims.t(3f, min = 18f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(status, fontSize = dims.t(2.4f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (secondaryAction != null) {
                BigButton(text = secondaryAction, onClick = onSecondary, primary = false)
            }
            if (action != null) {
                BigButton(text = action, onClick = onAction, primary = !ok)
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, status: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val dims = LocalDims.current
    SettingCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouch)
                .clickable { onChange(!checked) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = dims.t(3f, min = 18f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(status, fontSize = dims.t(2.4f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Ampli.Rose,
                ),
            )
        }
    }
}

@Composable
private fun ThemeRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val dims = LocalDims.current
    SettingCard {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Day / night", fontSize = dims.t(3f, min = 18f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Auto switches at local sunrise and sunset, worked out from GPS",
                    fontSize = dims.t(2.4f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (mode in ThemeMode.entries) {
                val on = mode == selected
                Box(
                    modifier = Modifier
                        .heightIn(min = MinTouch)
                        .clip(CircleShape)
                        .background(if (on) Ampli.Rose else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onSelect(mode) }
                        .padding(horizontal = dims.d(3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        mode.name,
                        color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = dims.t(2.6f, min = 16f),
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, lines: List<String>) {
    val dims = LocalDims.current
    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = dims.t(3f, min = 18f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            for (line in lines) {
                Text(line, fontSize = dims.t(2.3f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
