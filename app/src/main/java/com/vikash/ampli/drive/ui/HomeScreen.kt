package com.vikash.ampli.drive.ui

import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.bluetooth.BluetoothUiState
import com.vikash.ampli.drive.location.GpsState
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.ui.theme.Ampli
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Variant A from the mockup: status row, map · speed · music, then the dock. */
@Composable
fun HomeScreen(
    app: DriveApp,
    night: Boolean,
    resumeTick: Int,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val dims = LocalDims.current
    val context = LocalContext.current
    val gps by app.speedTracker.state.collectAsStateWithLifecycle()
    val bt by app.bluetooth.state.collectAsStateWithLifecycle()
    val autoPackage by app.prefs.autoPackage.collectAsStateWithLifecycle()

    // Pane widths from the mockup; 1024x600 gives the music card a little more room.
    val weights = if (dims.compact) Triple(1.4f, 0.95f, 1.0f) else Triple(1.55f, 1.0f, 1.05f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dims.d(2.4f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        StatusRow(
            gps = gps,
            bt = bt,
            onGpsClick = {
                when {
                    !gps.hasPermission -> onRequestPermissions()
                    !gps.providerEnabled -> Launchers.openLocationSettings(context)
                }
            },
            onBtClick = {
                if (!bt.hasPermission) {
                    onRequestPermissions()
                } else {
                    try {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(maxOf(dims.d(7.5f), 44.dp)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
        ) {
            MapPane(
                gps = gps,
                night = night,
                onOpenNavigation = { Launchers.openNavigation(context, gps.location) },
                onRequestPermissions = onRequestPermissions,
                modifier = Modifier.weight(weights.first).fillMaxHeight(),
            )
            SpeedPane(
                gps = gps,
                cameraAlerts = app.cameraAlerts,
                modifier = Modifier.weight(weights.second).fillMaxHeight(),
            )
            MusicCard(
                hub = app.mediaHub,
                resumeTick = resumeTick,
                modifier = Modifier.weight(weights.third).fillMaxHeight(),
            )
        }
        Dock(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxOf(dims.d(15f), MinTouch)),
            onNavigate = { Launchers.openNavigation(context, gps.location) },
            onMusic = { Launchers.openMusic(context, app.mediaHub.currentPackage()) },
            onPhone = { Launchers.openDialer(context) },
            onAuto = {
                if (!Launchers.openPhoneLink(context, autoPackage)) {
                    Toast.makeText(context, "Long-press your phone-link app to put it on this button", Toast.LENGTH_LONG).show()
                    onOpenApps()
                }
            },
            onApps = onOpenApps,
            onSettings = onOpenSettings,
        )
    }
}

@Composable
private fun StatusRow(
    gps: GpsState,
    bt: BluetoothUiState,
    onGpsClick: () -> Unit,
    onBtClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dims = LocalDims.current
    val context = LocalContext.current
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            // Wake just after the next second boundary so the minute flips on time.
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val is24h = remember { DateFormat.is24HourFormat(context) }
    val timeFmt = remember(is24h) { DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm") }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE, d MMM") }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2.4f)),
    ) {
        Text(
            now.format(timeFmt),
            fontSize = dims.t(4.6f, min = 22f),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            now.format(dateFmt),
            fontSize = dims.t(2.6f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (bt.supported) {
            val text: String
            val icon: ImageVector?
            val dot: Color?
            when {
                !bt.hasPermission -> {
                    text = "Bluetooth"; icon = Icons.Filled.Bluetooth; dot = null
                }
                !bt.enabled -> {
                    text = "BT off"; icon = Icons.Filled.BluetoothDisabled; dot = null
                }
                bt.connectedName != null -> {
                    text = "${bt.connectedName} · BT"; icon = null; dot = Ampli.Online
                }
                else -> {
                    text = "BT on"; icon = Icons.Filled.Bluetooth; dot = null
                }
            }
            Pill(text = text, icon = icon, dotColor = dot, onClick = onBtClick)
        }
        GpsPill(gps, onGpsClick)
    }
}

@Composable
private fun GpsPill(gps: GpsState, onClick: () -> Unit) {
    val text: String
    val icon: ImageVector
    val tint: Color
    when {
        !gps.hasPermission -> {
            text = "Location off"; icon = Icons.Filled.GpsOff; tint = Ampli.Warning
        }
        !gps.hasGps -> {
            text = "No GPS"; icon = Icons.Filled.GpsOff; tint = Ampli.Warning
        }
        !gps.providerEnabled -> {
            text = "GPS off"; icon = Icons.Filled.GpsOff; tint = Ampli.Warning
        }
        gps.speedKmh != null -> {
            text = if (gps.satellitesUsed > 0) "GPS ${gps.satellitesUsed} sats" else "GPS"
            icon = Icons.Filled.GpsFixed
            tint = MaterialTheme.colorScheme.primary
        }
        else -> {
            text = if (gps.satellitesInView > 0) "Searching ${gps.satellitesUsed}/${gps.satellitesInView}" else "Searching"
            icon = Icons.Filled.GpsNotFixed
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Pill(text = text, icon = icon, iconTint = tint, onClick = onClick)
}

@Composable
private fun Dock(
    modifier: Modifier,
    onNavigate: () -> Unit,
    onMusic: () -> Unit,
    onPhone: () -> Unit,
    onAuto: () -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit,
) {
    val dims = LocalDims.current
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        val item = Modifier.weight(1f).fillMaxHeight()
        DockButton("Navigate", Icons.Filled.Navigation, onNavigate, item, highlight = true)
        DockButton("Music", Icons.Filled.MusicNote, onMusic, item)
        DockButton("Phone", Icons.Filled.Phone, onPhone, item)
        DockButton(if (dims.compact) "Auto" else "Android Auto", Icons.Filled.DirectionsCar, onAuto, item)
        DockButton("Apps", Icons.Filled.Apps, onApps, item)
        DockButton("Settings", Icons.Filled.Settings, onSettings, item)
    }
}

@Composable
private fun DockButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier,
    highlight: Boolean = false,
) {
    val dims = LocalDims.current
    val shape = RoundedCornerShape(dims.d(2.4f))
    val bg = if (highlight) Ampli.Rose else MaterialTheme.colorScheme.surfaceContainer
    val fg = if (highlight) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (highlight) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (highlight) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(maxOf(dims.d(5f), 28.dp)),
        )
        Spacer(Modifier.height(dims.d(0.8f)))
        Text(
            label,
            fontSize = dims.t(2.2f),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
