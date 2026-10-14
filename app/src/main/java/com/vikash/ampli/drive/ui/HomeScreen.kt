package com.vikash.ampli.drive.ui

import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.bluetooth.BluetoothUiState
import com.vikash.ampli.drive.location.GpsState
import com.vikash.ampli.drive.nav.NavTurnRepository
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.ui.theme.CardStyle
import com.vikash.ampli.drive.ui.theme.HomeLayout
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.ui.theme.SpeedStyle
import com.vikash.ampli.drive.ui.theme.ThemeFamily
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Status row, the three panes (arranged per theme), then the dock. */
@Composable
fun HomeScreen(
    app: DriveApp,
    resumeTick: Int,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: (sos: Boolean) -> Unit,
    onOpenFastag: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val gps by app.speedTracker.state.collectAsStateWithLifecycle()
    val bt by app.bluetooth.state.collectAsStateWithLifecycle()
    val autoPackage by app.prefs.autoPackage.collectAsStateWithLifecycle()
    val turn by NavTurnRepository.turn.collectAsStateWithLifecycle()
    val autoHint = stringResource(R.string.auto_button_hint)
    val fastagOn by app.fastag.enabled.collectAsStateWithLifecycle()

    val fastagChip: @Composable () -> Unit = {
        FastagChip(app = app, onClick = onOpenFastag, modifier = Modifier.fillMaxHeight())
    }

    val openNav = { Launchers.openNavigation(context, gps.location); Unit }
    val gap = dims.d(2f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dims.d(2.4f)),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        StatusRow(
            gps = gps,
            bt = bt,
            fastag = if (fastagOn) fastagChip else null,
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
                // 56 dp so the FASTag chip and status pills are full-size touch targets.
                .height(maxOf(dims.d(7.5f), 56.dp)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            val map = @Composable { m: Modifier, showTurn: Boolean ->
                MapPane(
                    gps = gps,
                    onOpenNavigation = openNav,
                    onRequestPermissions = onRequestPermissions,
                    modifier = m,
                    showTurn = showTurn,
                )
            }
            val speed = @Composable { m: Modifier -> SpeedPane(gps = gps, cameraAlerts = app.cameraAlerts, modifier = m) }
            val music = @Composable { m: Modifier -> MusicCard(hub = app.mediaHub, resumeTick = resumeTick, modifier = m) }

            when (theme.layout) {
                HomeLayout.MapSpeedMusic -> {
                    val w = if (dims.compact) Triple(1.4f, 0.95f, 1.0f) else Triple(1.55f, 1.0f, 1.05f)
                    map(Modifier.weight(w.first).fillMaxHeight(), true)
                    speed(Modifier.weight(w.second).fillMaxHeight())
                    music(Modifier.weight(w.third).fillMaxHeight())
                }
                HomeLayout.SpeedMapMusic -> {
                    // HUD keeps its speed tape narrow; Rose and Neumorphic give the dial more room.
                    val speedW = if (theme.speedStyle == SpeedStyle.Tape) 0.8f else 0.95f
                    speed(Modifier.weight(speedW).fillMaxHeight())
                    map(Modifier.weight(if (dims.compact) 1.35f else 1.5f).fillMaxHeight(), true)
                    music(Modifier.weight(1.05f).fillMaxHeight())
                }
                HomeLayout.MusicSpeedMap -> {
                    music(Modifier.weight(1f).fillMaxHeight())
                    speed(Modifier.weight(0.95f).fillMaxHeight())
                    map(Modifier.weight(if (dims.compact) 1.4f else 1.6f).fillMaxHeight(), true)
                }
                HomeLayout.MapTopRow -> {
                    // Kerala Green / Soft Peach: wide map strip on top, the rest in a row below.
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                        map(Modifier.weight(1f).fillMaxWidth(), true)
                        Row(Modifier.weight(0.85f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            speed(Modifier.weight(0.8f).fillMaxHeight())
                            music(Modifier.weight(1.4f).fillMaxHeight())
                        }
                    }
                }
                HomeLayout.MapStacked -> {
                    map(Modifier.weight(1.75f).fillMaxHeight(), true)
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        speed(Modifier.weight(0.85f).fillMaxWidth())
                        music(Modifier.weight(1.15f).fillMaxWidth())
                    }
                }
                HomeLayout.GiantSpeed -> {
                    // Minimal Mono: a giant speed number and the next turn in big type on the left.
                    Column(
                        Modifier.weight(1.45f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        speed(Modifier.weight(1f).fillMaxWidth())
                        turn?.let {
                            NextTurnCard(turn = it, onClick = openNav, modifier = Modifier.fillMaxWidth(), large = !dims.compact)
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        map(Modifier.weight(1f).fillMaxWidth(), false)
                        music(Modifier.weight(1.1f).fillMaxWidth())
                    }
                }
            }
        }
        Dock(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxOf(dims.d(15f), MinTouch)),
            onNavigate = openNav,
            onVoice = { app.voice.start() },
            onMusic = { Launchers.openMusic(context, app.mediaHub.currentPackage()) },
            onPhone = { Launchers.openDialer(context) },
            onAuto = {
                if (!Launchers.openPhoneLink(context, autoPackage)) {
                    Toast.makeText(context, autoHint, Toast.LENGTH_LONG).show()
                    onOpenApps()
                }
            },
            onApps = onOpenApps,
            onSettings = onOpenSettings,
            onHelp = { onOpenHelp(false) },
            onSos = { onOpenHelp(true) },
        )
    }
}

@Composable
private fun StatusRow(
    gps: GpsState,
    bt: BluetoothUiState,
    fastag: (@Composable () -> Unit)?,
    onGpsClick: () -> Unit,
    onBtClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            // Wake just after the next second boundary so the minute flips on time.
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val is24h = remember { DateFormat.is24HourFormat(context) }
    val timeFmt = remember(is24h) { DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm") }
    val dateFmt = remember(locale) { DateTimeFormatter.ofPattern("EEE, d MMM", locale) }
    val lightClock = theme.id == "midnight_glass" || theme.id == "minimal_mono"

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2.4f)),
    ) {
        Text(
            now.format(timeFmt),
            style = theme.numberStyle(theme.text),
            fontSize = dims.t(4.6f, min = 22f),
            fontWeight = if (lightClock) FontWeight.Light else FontWeight.Bold,
        )
        Text(
            theme.label(now.format(dateFmt)),
            style = theme.labelStyle(),
            fontSize = dims.t(2.6f),
        )
        // Saffron family: a "have a safe trip" line in an Indian script, as in the designs.
        theme.greeting?.let {
            Text(
                it,
                color = theme.accentSoft,
                fontSize = dims.t(3.4f, min = 18f),
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        fastag?.invoke()
        if (bt.supported) {
            val text: String
            val icon: ImageVector?
            val dot: Color?
            when {
                !bt.hasPermission -> {
                    text = stringResource(R.string.bt_label); icon = Icons.Filled.Bluetooth; dot = null
                }
                !bt.enabled -> {
                    text = stringResource(R.string.bt_off); icon = Icons.Filled.BluetoothDisabled; dot = null
                }
                bt.connectedName != null -> {
                    text = stringResource(R.string.bt_connected, bt.connectedName); icon = null; dot = theme.ok
                }
                else -> {
                    text = stringResource(R.string.bt_on); icon = Icons.Filled.Bluetooth; dot = null
                }
            }
            Pill(text = text, icon = icon, dotColor = dot, onClick = onBtClick)
        }
        GpsPill(gps, onGpsClick)
    }
}

@Composable
private fun GpsPill(gps: GpsState, onClick: () -> Unit) {
    val theme = LocalDriveTheme.current
    val text: String
    val icon: ImageVector
    val tint: Color
    when {
        !gps.hasPermission -> {
            text = stringResource(R.string.gps_location_off); icon = Icons.Filled.GpsOff; tint = theme.warning
        }
        !gps.hasGps -> {
            text = stringResource(R.string.gps_none); icon = Icons.Filled.GpsOff; tint = theme.warning
        }
        !gps.providerEnabled -> {
            text = stringResource(R.string.gps_off); icon = Icons.Filled.GpsOff; tint = theme.warning
        }
        gps.speedKmh != null -> {
            text = if (gps.satellitesUsed > 0) stringResource(R.string.gps_sats, gps.satellitesUsed) else "GPS"
            icon = Icons.Filled.GpsFixed
            tint = theme.accentSoft
        }
        else -> {
            text = if (gps.satellitesInView > 0) {
                stringResource(R.string.gps_searching_count, gps.satellitesUsed, gps.satellitesInView)
            } else {
                stringResource(R.string.gps_searching)
            }
            icon = Icons.Filled.GpsNotFixed
            tint = theme.muted
        }
    }
    Pill(text = text, icon = icon, iconTint = tint, onClick = onClick)
}

@Composable
private fun Dock(
    modifier: Modifier,
    onNavigate: () -> Unit,
    onVoice: () -> Unit,
    onMusic: () -> Unit,
    onPhone: () -> Unit,
    onAuto: () -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onSos: () -> Unit,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val gap = if (theme.cardStyle == CardStyle.Outline && theme.radius <= 0f) dims.d(1f) else dims.d(2f)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        val item = Modifier.weight(1f).fillMaxHeight()
        DockButton(stringResource(R.string.dock_navigate), Icons.Filled.Navigation, onNavigate, item, highlight = true)
        // Same flow as the wake word; works without "Always listen" and without the model.
        DockButton(stringResource(R.string.voice_button), Icons.Filled.Mic, onVoice, item)
        DockButton(stringResource(R.string.dock_music), Icons.Filled.MusicNote, onMusic, item)
        DockButton(stringResource(R.string.dock_phone), Icons.Filled.Phone, onPhone, item)
        DockButton(
            if (dims.compact) stringResource(R.string.dock_auto) else stringResource(R.string.dock_android_auto),
            Icons.Filled.DirectionsCar,
            onAuto,
            item,
        )
        DockButton(stringResource(R.string.dock_apps), Icons.Filled.Apps, onApps, item)
        DockButton(stringResource(R.string.dock_settings), Icons.Filled.Settings, onSettings, item)
        // Red Help on the right end, nearest the driver in right-hand-drive cars.
        // Tap: helplines. Hold 3 s: SOS.
        HelpDockButton(onTap = onHelp, onHold = onSos, modifier = item)
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
    val theme = LocalDriveTheme.current
    val shape = if (theme.cardStyle == CardStyle.Skewed) skewShape(0.22f) else theme.cardShape(dims, scale = 0.9f)
    val fill = if (highlight) theme.dockActive else theme.surface
    val iconTint = when {
        highlight -> theme.onDockActive
        theme.cardStyle == CardStyle.NeonGlow -> theme.accent2
        theme.id == "ampli_rose" -> theme.accentSoft
        theme.family == ThemeFamily.Saffron -> theme.accent
        else -> theme.text
    }
    val labelColor = if (highlight) theme.onDockActive else theme.muted
    // Minimal Mono marks the active item with a bar on top instead of a fill.
    val topBar = highlight && theme.id == "minimal_mono"
    Column(
        modifier = modifier
            .themedCard(
                theme,
                shape,
                fill = Brush.linearGradient(listOf(fill, fill)),
                border = if (highlight && theme.cardStyle != CardStyle.Outline) Color.Transparent else theme.border,
            )
            .then(
                if (topBar) {
                    Modifier.drawWithContent {
                        drawContent()
                        drawRect(theme.accent, size = Size(size.width, 3.dp.toPx()))
                    }
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(maxOf(dims.d(5f), 28.dp)),
        )
        Spacer(Modifier.height(dims.d(0.8f)))
        Text(
            theme.label(label),
            style = theme.labelStyle(labelColor),
            fontSize = dims.t(2.2f),
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

