package com.vikash.ampli.drive.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.system.PowerAction
import com.vikash.ampli.drive.system.PowerControl
import com.vikash.ampli.drive.system.PowerHelperService
import com.vikash.ampli.drive.system.QuickActions
import com.vikash.ampli.drive.system.ScreenBrightness
import com.vikash.ampli.drive.system.ScreenShot
import com.vikash.ampli.drive.system.Shell
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * The quick panel from the rail's Controls key or the top strip: the time, seven tiles (Wi-Fi,
 * Bluetooth, EQ, Screensaver, Settings, Clear memory, Screenshot), Volume and Brightness sliders
 * and Screen off / Restart / Power off. Long press on a tile opens its Android page.
 */
@Composable
internal fun ControlsPanel(app: DriveApp, open: Boolean, onClose: () -> Unit, onOpenSettings: () -> Unit) {
    if (!open) return
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val bt by app.bluetooth.state.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    // Wi-Fi, EQ app and levels, read off the main thread while the panel is open.
    val status by produceState<Shell.Status?>(null, refresh) {
        while (true) {
            value = withContext(Dispatchers.IO) { Shell.status(context) }
            delay(3_000L)
        }
    }
    var pickEq by remember { mutableStateOf(false) }
    var memory by remember { mutableStateOf<String?>(null) }
    var clearing by remember { mutableStateOf(false) }

    // Screenshot: storage asked once (Pictures/Ampli), then Android's capture prompt.
    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) {
            scope.launch {
                val file = ScreenShot.capture(context, r.resultCode, data)
                toast(context, if (file != null) R.string.qp_shot_saved else R.string.qp_shot_failed)
            }
        }
    }
    val startCapture = {
        val intent = ScreenShot.captureIntent(context)
        if (intent == null) {
            toast(context, R.string.qp_shot_failed)
        } else {
            try {
                captureLauncher.launch(intent)
            } catch (_: Exception) {
                toast(context, R.string.qp_shot_failed)
            }
        }
    }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startCapture() }
    val screenshot = {
        onClose()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && PowerHelperService.connected -> scope.launch {
                // The panel closes first so it is not in the picture.
                delay(350L)
                if (!ScreenShot.viaHelper()) toast(context, R.string.qp_shot_failed) else toast(context, R.string.qp_shot_android)
            }
            ScreenShot.projectionWorks -> {
                if (!QuickActions.hasStorageWrite(context) && !app.shell.askedStorage) {
                    app.shell.askedStorage = true
                    storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    startCapture()
                }
            }
            else -> toast(context, R.string.qp_shot_needs_helper)
        }
        Unit
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )
        val shape = if (theme.radius <= 0f) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(bottomStart = dims.d(theme.radius), bottomEnd = dims.d(theme.radius))
        Column(
            Modifier
                .fillMaxWidth()
                .themedCard(theme, shape, fill = androidx.compose.ui.graphics.SolidColor(panelColor()), border = theme.border, accentEdge = false, blur = false)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = dims.d(4f), vertical = dims.d(2.75f)),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
        ) {
            PanelHead(onClose)
            // Eight tiles in two rows (design board "Controls"): Wi-Fi, Bluetooth, Android settings,
            // Car settings (Ampli's settings when the unit has no car-settings app), EQ,
            // Screensaver, Memory and Screenshot.
            val car by produceState<com.vikash.ampli.drive.system.CarSettingsApp?>(null) {
                value = withContext(Dispatchers.IO) { com.vikash.ampli.drive.system.SystemSettings.carSettings(context) }
            }
            val s = status
            val wifiOn = s?.wifiOn == true || s?.wifiConnected == true
            Row(Modifier.fillMaxWidth().height(dims.d(13f)), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                val tile = Modifier.weight(1f).fillMaxHeight()
                PanelTile(
                    Icons.Filled.Wifi, R.string.qp_wifi,
                    sub = s?.ssid ?: stringResource(if (wifiOn) R.string.qp_on else R.string.qp_off),
                    on = wifiOn, modifier = tile,
                    onLongClick = { QuickActions.openWifiSettings(context) },
                ) {
                    QuickActions.toggleWifi(context)
                    refresh++
                }
                PanelTile(
                    Icons.Filled.Bluetooth, R.string.qp_bt,
                    sub = bt.connectedName ?: stringResource(if (bt.enabled) R.string.qp_on else R.string.qp_off),
                    on = bt.enabled, modifier = tile,
                    onLongClick = { QuickActions.openBluetoothSettings(context) },
                ) { QuickActions.toggleBluetooth(context) }
                PanelTile(Icons.Filled.Settings, R.string.android_settings, sub = stringResource(R.string.android_settings_hint), modifier = tile, onLongClick = {
                    onClose()
                    onOpenSettings()
                }) {
                    onClose()
                    com.vikash.ampli.drive.system.SystemSettings.openAndroid(context)
                }
                if (car != null) {
                    PanelTile(Icons.Filled.DirectionsCar, R.string.car_settings, sub = stringResource(R.string.vendor_car_settings_hint), modifier = tile) {
                        onClose()
                        if (!com.vikash.ampli.drive.system.SystemSettings.openCar(context)) toast(context, R.string.error_cannot_open_app)
                    }
                } else {
                    PanelTile(Icons.Filled.Tune, R.string.qp_settings, modifier = tile, onLongClick = { com.vikash.ampli.drive.system.SystemSettings.openAndroid(context) }) {
                        onClose()
                        onOpenSettings()
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(dims.d(13f)), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                val tile = Modifier.weight(1f).fillMaxHeight()
                PanelTile(Icons.Filled.Equalizer, R.string.qp_eq, sub = s?.eqApp, modifier = tile, onLongClick = { pickEq = true }) {
                    Shell.open(context, Shell.Control.Equalizer)
                }
                PanelTile(Icons.Filled.Tv, R.string.qp_screensaver, modifier = tile, onLongClick = { QuickActions.openDreamSettings(context) }) {
                    onClose()
                    PowerControl.screensaver()
                }
                PanelTile(
                    Icons.Filled.CleaningServices, R.string.qp_clear,
                    sub = if (clearing) stringResource(R.string.qp_clearing) else memory,
                    modifier = tile,
                ) {
                    if (!clearing) {
                        clearing = true
                        scope.launch {
                            val freed = QuickActions.clearMemory(context)
                            memory = if (freed >= MIN_FREED) context.getString(R.string.qp_cleared, QuickActions.sizeLabel(freed)) else context.getString(R.string.qp_cleared_none)
                            clearing = false
                        }
                    }
                }
                PanelTile(Icons.Filled.Screenshot, R.string.qp_screenshot, modifier = tile, onClick = screenshot)
            }
            // Volume and brightness.
            Row(Modifier.fillMaxWidth().height(dims.d(11f)), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                var vol by remember { mutableStateOf(QuickActions.volume(context)) }
                PillSlider(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    label = R.string.qp_volume,
                    fraction = vol?.let { if (it.max > 0) it.level.toFloat() / it.max else 0f } ?: 0f,
                    value = vol?.let { "${it.level}/${it.max}" } ?: "–",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) { f ->
                    val max = vol?.max ?: return@PillSlider
                    vol = QuickActions.setVolume(context, Math.round(f * max)) ?: vol
                }
                var bright by remember {
                    mutableStateOf(QuickActions.brightnessPercent(context, windowPercent(activity)) ?: 50)
                }
                PillSlider(
                    icon = Icons.Filled.BrightnessMedium,
                    label = R.string.qp_brightness,
                    fraction = bright / 100f,
                    value = "$bright%",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onStart = {
                        // Once: ask for "Modify system settings" so the whole screen dims, not just Car Hub.
                        if (!ScreenBrightness.allowed(context) && !app.shell.askedWriteSettings) {
                            app.shell.askedWriteSettings = true
                            ScreenBrightness.openPermission(context)
                            false
                        } else {
                            true
                        }
                    },
                ) { f ->
                    bright = Math.round(f * 100).coerceIn(1, 100)
                    QuickActions.setBrightness(activity, context, bright)
                }
            }
            // Screen off, Restart, Power off.
            Row(Modifier.fillMaxWidth().height(dims.d(11f)), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                val m = Modifier.weight(1f).fillMaxHeight()
                PowerRowKey(Icons.Filled.DarkMode, R.string.power_screen_off, m) {
                    onClose()
                    PowerControl.request(context, PowerAction.ScreenOff)
                }
                // Restart in amber and Power off in SOS red, as designed.
                PowerRowKey(Icons.Filled.RestartAlt, R.string.power_restart, m, tone = PowerTone.Warn) {
                    onClose()
                    PowerControl.request(context, PowerAction.Restart)
                }
                PowerRowKey(Icons.Filled.PowerSettingsNew, R.string.power_off, m, tone = PowerTone.Danger) {
                    onClose()
                    PowerControl.request(context, PowerAction.PowerOff)
                }
            }
        }
    }
    if (pickEq) {
        EqChooserDialog(
            onPick = { component ->
                Shell.chooseEqualizer(context, component)
                pickEq = false
                refresh++
            },
            onDismiss = { pickEq = false },
        )
    }
}

/** Less than this is noise in Android's free-memory figure. */
private const val MIN_FREED = 8L * 1024 * 1024

private fun toast(context: android.content.Context, @StringRes id: Int) {
    try {
        Toast.makeText(context, id, Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
    }
}

private fun windowPercent(activity: Activity?): Int? =
    activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f }?.let { Math.round(it * 100) }

/** The panel's fill: opaque on every theme (nothing behind it is blurred). */
@Composable
private fun panelColor(): Color {
    val theme = LocalDriveTheme.current
    return if (theme.surface.alpha < 1f) theme.background else theme.surface
}

@Composable
private fun PanelHead(onClose: () -> Unit) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = System.currentTimeMillis()
        }
    }
    val context = LocalContext.current
    val time = remember(now) { android.text.format.DateFormat.getTimeFormat(context).format(Date(now)) }
    val date = remember(now) { DateFormat.getDateInstance(DateFormat.FULL).format(Date(now)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        Text(time, style = theme.numberStyle(theme.text), fontSize = dims.t(5.5f, min = 26f), maxLines = 1)
        Text(date, color = theme.muted, fontSize = dims.t(2.3f, min = 13f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.qp_title), color = theme.text, fontSize = dims.t(2.6f, min = 15f), fontWeight = FontWeight.Bold)
        englishLine(R.string.qp_title)?.let { Text(it, color = theme.muted, fontSize = dims.t(1.9f, min = 11f)) }
        Box(
            Modifier.size(MinTouch).themedTap(CircleShape, theme.surfaceHigh, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = theme.text, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun PanelTile(
    icon: ImageVector,
    @StringRes label: Int,
    modifier: Modifier,
    sub: String? = null,
    on: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val shape = theme.buttonShape(dims)
    val fg = if (on) theme.onAccent else theme.text
    Column(
        modifier
            .themedTap(shape, if (on) theme.accent else theme.surface, border = if (on) null else theme.border, borderWidth = 2.dp, onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = dims.d(3f), vertical = dims.d(1.75f)),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, contentDescription = null, tint = if (on) theme.onAccent else theme.accentSoft, modifier = Modifier.size(dims.d(3.5f)))
        Column {
            Text(stringResource(label), color = fg, fontSize = dims.t(3f, min = 18f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val second = sub ?: englishLine(label)
            if (second != null) {
                Text(second, color = if (on) fg.copy(alpha = 0.85f) else theme.muted, fontSize = dims.t(2.25f, min = 14f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A pill slider: the accent fill is the level; drag or tap anywhere on it to set. [onStart]
 * returning false ignores that touch (Brightness asking for permission first).
 */
@Composable
private fun PillSlider(
    icon: ImageVector,
    @StringRes label: Int,
    fraction: Float,
    value: String,
    modifier: Modifier,
    onStart: () -> Boolean = { true },
    onChange: (Float) -> Unit,
) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val shape: Shape = if (theme.radius <= 0f) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(50)
    val change by rememberUpdatedState(onChange)
    val start by rememberUpdatedState(onStart)
    BoxWithConstraints(
        modifier
            .themedSurface(theme, shape, theme.surfaceHigh, border = theme.border)
            .pointerInput(Unit) {
                detectTapGestures { o -> if (start()) change((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                var allowed = true
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        allowed = start()
                        if (allowed) change((o.x / size.width).coerceIn(0f, 1f))
                    },
                ) { pointer, _ ->
                    if (allowed) change((pointer.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        Box(Modifier.fillMaxHeight().width(maxWidth * fraction.coerceIn(0f, 1f)).clip(shape).background(theme.accent))
        Row(
            Modifier.fillMaxSize().padding(horizontal = dims.d(2.6f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f)),
        ) {
            val onFill = fraction > 0.3f
            val fg = if (onFill) theme.onAccent else theme.text
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(dims.d(3.6f)))
            Text(stringResource(label), color = fg, fontSize = dims.t(2.4f, min = 14f), fontWeight = FontWeight.Bold, maxLines = 1)
            englishLine(label)?.let { Text(it, color = fg.copy(alpha = 0.8f), fontSize = dims.t(1.7f, min = 10f), maxLines = 1) }
            Spacer(Modifier.weight(1f))
            Text(value, color = if (fraction > 0.85f) theme.onAccent else theme.text, style = theme.numberStyle(theme.text), fontSize = dims.t(3.2f, min = 18f), maxLines = 1)
        }
    }
}

private enum class PowerTone { Plain, Warn, Danger }

@Composable
private fun PowerRowKey(icon: ImageVector, @StringRes label: Int, modifier: Modifier, tone: PowerTone = PowerTone.Plain, onClick: () -> Unit) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val fill = when (tone) {
        PowerTone.Plain -> theme.surfaceHigh
        PowerTone.Warn -> theme.warning.copy(alpha = 0.16f)
        PowerTone.Danger -> theme.danger
    }
    val ink = when (tone) {
        PowerTone.Plain -> theme.text
        PowerTone.Warn -> if (theme.isLight) theme.text else Color(0xFFFFE3B0)
        PowerTone.Danger -> theme.onDanger
    }
    Row(
        modifier
            .themedTap(theme.buttonShape(dims), fill, border = if (tone == PowerTone.Warn) theme.warning else theme.border, borderWidth = 2.dp, onClick = onClick)
            .padding(horizontal = dims.d(2.5f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f), Alignment.CenterHorizontally),
    ) {
        Icon(icon, contentDescription = null, tint = if (tone == PowerTone.Plain) theme.accentSoft else ink, modifier = Modifier.size(dims.d(4f)))
        Column {
            Text(stringResource(label), color = ink, fontSize = dims.t(3.25f, min = 18f), fontWeight = FontWeight.Bold, maxLines = 1)
            englishLine(label)?.let { Text(it, color = ink.copy(alpha = 0.75f), fontSize = dims.t(2f, min = 13f), maxLines = 1) }
        }
    }
}
