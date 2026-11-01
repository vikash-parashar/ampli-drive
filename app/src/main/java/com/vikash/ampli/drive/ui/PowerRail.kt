package com.vikash.ampli.drive.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.net.wifi.WifiManager
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.system.PowerControl
import com.vikash.ampli.drive.system.PowerHelperService
import com.vikash.ampli.drive.system.QuickActions
import com.vikash.ampli.drive.system.RailVolume
import com.vikash.ampli.drive.system.VolumeHold
import com.vikash.ampli.drive.ui.theme.DriveTheme
import com.vikash.ampli.drive.ui.theme.HomeStyle
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/*
 * Home's left rail (Back, Home, Apps, Vol +, Vol −, Mute, Controls) for head units without
 * buttons, the volume popup and the top strip that pulls down the quick panel. Flat fills only,
 * no animation: one redraw per tap on an Android 8.1 unit.
 */

/** The rail's width: 96 px at 1280x800. */
internal const val RAIL_UNITS = 12f

/** Amber of a muted Mute key (the same in every theme, like the SOS red). */
private val MutedAmber = Color(0xFFF59E0B)
private val MutedInk = Color(0xFF111111)

/** The rail's colours for one theme. */
private class RailColors(val bg: Color, val fg: Color, val muted: Color, val on: Color, val onFg: Color, val edge: Color?)

private fun railColors(theme: DriveTheme): RailColors = when (theme.style) {
    HomeStyle.MapFirst -> RailColors(Color(0xF017222A), Color(0xFFF1F5F9), Color(0xFFF1F5F9).copy(alpha = 0.66f), theme.accent, theme.onAccent, null)
    HomeStyle.BigGlance -> RailColors(Color(0xFF0B0B0E), Color(0xFFF5F5F5), Color(0xFFF5F5F5).copy(alpha = 0.62f), theme.accent, theme.onAccent, null)
    HomeStyle.Bento -> RailColors(theme.background, theme.text, theme.muted, theme.ink, theme.onInk, theme.border)
    HomeStyle.RoadSign -> RailColors(theme.ink, theme.onInk, theme.onInk.copy(alpha = 0.7f), theme.onInk, theme.ink, null)
}

/**
 * Home with the rail on its left. [content] (the Home layout) and everything in [overlays] sit
 * right of the rail, so the rail stays usable while the quick panel or the power cards are up.
 */
@Composable
fun HomeShell(
    app: DriveApp,
    resumeTick: Int,
    quickOpen: Boolean,
    onQuick: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dims = LocalDims.current
    val volume = remember { RailVolumeState() }
    Row(Modifier.fillMaxSize()) {
        PowerRail(
            app = app,
            resumeTick = resumeTick,
            volume = volume,
            controlsOpen = quickOpen,
            onBack = onBack,
            onHome = {
                onQuick(false)
                if (PowerControl.state.value == com.vikash.ampli.drive.system.PowerUi.Sheet) PowerControl.close()
            },
            onOpenApps = onOpenApps,
            onControls = { onQuick(!quickOpen) },
            modifier = Modifier.width(dims.d(RAIL_UNITS)).fillMaxHeight(),
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            content()
            TopStrip(app = app, resumeTick = resumeTick, quickOpen = quickOpen, onOpen = { onQuick(true) }, modifier = Modifier.align(Alignment.TopCenter))
            ControlsPanel(app = app, open = quickOpen, onClose = { onQuick(false) }, onOpenSettings = onOpenSettings)
            VolumePopup(volume, Modifier.align(Alignment.TopStart).padding(start = dims.d(1.2f), top = dims.d(34f)))
        }
    }
}

/** The rail's own copy of the music volume, and when the popup was last asked for. */
internal class RailVolumeState {
    var level by mutableStateOf<RailVolume.Level?>(null)
    var shownAt by mutableLongStateOf(0L)

    fun show(next: RailVolume.Level?) {
        if (next != null) level = next
        shownAt = android.os.SystemClock.elapsedRealtime()
    }
}

@Composable
private fun PowerRail(
    app: DriveApp,
    resumeTick: Int,
    volume: RailVolumeState,
    controlsOpen: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenApps: () -> Unit,
    onControls: () -> Unit,
    modifier: Modifier,
) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val context = LocalContext.current
    val c = railColors(theme)
    // The level, refreshed on return to Car Hub and whenever Android changes it (voice, steering keys).
    LaunchedEffect(resumeTick) { volume.level = QuickActions.volume(context) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                volume.level = QuickActions.volume(context)
            }
        }
        val registered = try {
            androidx.core.content.ContextCompat.registerReceiver(context, receiver, IntentFilter(VOLUME_CHANGED), androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
            true
        } catch (_: Exception) {
            false
        }
        onDispose {
            if (registered) {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
            }
        }
    }
    val muted = volume.level?.muted == true
    var m = modifier.background(c.bg)
    if (c.edge != null) m = m.drawRightEdge(c.edge, 3.dp)
    Column(
        m.padding(horizontal = dims.d(0.9f), vertical = dims.d(1f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dims.d(0.4f)),
    ) {
        RailKey(Icons.AutoMirrored.Filled.ArrowBack, R.string.rail_back, c, onClick = onBack)
        RailKey(Icons.Filled.Home, R.string.rail_home, c, active = !controlsOpen, onClick = onHome)
        RailKey(
            Icons.Filled.Apps, R.string.rail_apps, c,
            onClick = onOpenApps,
            // Long press: Android's recent apps through the helper, else the app drawer.
            onLongClick = { if (!PowerHelperService.recents()) onOpenApps() },
        )
        Box(Modifier.padding(vertical = dims.d(0.5f)).width(dims.d(7f)).height(1.dp).background(c.muted.copy(alpha = 0.4f)))
        VolumeKey(Icons.AutoMirrored.Filled.VolumeUp, R.string.rail_vol_up, c) { volume.show(QuickActions.stepVolume(context, 1)) }
        VolumeKey(Icons.AutoMirrored.Filled.VolumeDown, R.string.rail_vol_down, c) { volume.show(QuickActions.stepVolume(context, -1)) }
        RailKey(
            Icons.AutoMirrored.Filled.VolumeOff, R.string.rail_mute, c,
            fill = if (muted) MutedAmber else null,
            fillFg = MutedInk,
            description = stringResource(if (muted) R.string.rail_unmute else R.string.rail_mute),
            onClick = { volume.show(QuickActions.toggleMute(context)) },
        )
        Spacer(Modifier.weight(1f))
        RailKey(Icons.Filled.Tune, R.string.rail_controls, c, active = controlsOpen, outlined = true, onClick = onControls)
    }
}

/** "android.media.VOLUME_CHANGED_ACTION": sent by every Android since 4.x, though not in the SDK. */
private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"

private fun Modifier.drawRightEdge(color: Color, width: Dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRect(color, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
}

@Composable
private fun RailKey(
    icon: ImageVector,
    @StringRes label: Int,
    c: RailColors,
    active: Boolean = false,
    outlined: Boolean = false,
    fill: Color? = null,
    fillFg: Color = c.onFg,
    description: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val shape = theme.buttonShape(dims)
    val bg = fill ?: if (active) c.on else Color.Transparent
    val fg = when {
        fill != null -> fillFg
        active -> c.onFg
        else -> c.fg
    }
    val edge = if (outlined && !active) c.muted.copy(alpha = 0.5f) else null
    var m = Modifier.fillMaxWidth().height(dims.d(10.6f)).clip(shape).background(bg, shape)
    if (edge != null) m = m.border(1.5.dp, edge, shape)
    val tapLong by rememberUpdatedState(onLongClick)
    val tap by rememberUpdatedState(onClick)
    val text = description ?: stringResource(label)
    m = m
        .semantics {
            role = Role.Button
            contentDescription = text
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onLongPress = if (onLongClick != null) { _ -> tapLong?.invoke() } else null,
                onTap = { tap() },
            )
        }
    KeyFace(icon, label, fg, if (fill != null || active) fg.copy(alpha = 0.8f) else c.muted, m)
}

/** Vol + / Vol −: a step on touch, then repeats while held (400 ms, then every 130 ms). */
@Composable
private fun VolumeKey(icon: ImageVector, @StringRes label: Int, c: RailColors, step: () -> Unit) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val shape = theme.buttonShape(dims)
    var pressed by remember { mutableStateOf(false) }
    val onStep by rememberUpdatedState(step)
    val text = stringResource(label)
    val m = Modifier
        .fillMaxWidth()
        .height(dims.d(10.6f))
        .clip(shape)
        .background(if (pressed) c.fg.copy(alpha = 0.16f) else Color.Transparent, shape)
        .semantics {
            role = Role.Button
            contentDescription = text
            onClick { onStep(); true }
        }
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                pressed = true
                onStep()
                coroutineScope {
                    val repeat = launch {
                        var n = 1
                        while (true) {
                            delay(VolumeHold.delayBefore(n++))
                            onStep()
                        }
                    }
                    tryAwaitRelease()
                    repeat.cancel()
                }
                pressed = false
            })
        }
    KeyFace(icon, label, c.fg, c.muted, m)
}

/** Icon, the label in the app's language and, when that is not English, the English word under it. */
@Composable
private fun KeyFace(icon: ImageVector, @StringRes label: Int, fg: Color, sub: Color, modifier: Modifier) {
    val dims = LocalDims.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(dims.d(4.4f)))
        Text(
            stringResource(label),
            color = fg,
            fontSize = dims.t(2.3f, min = 12f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        englishLine(label)?.let {
            Text(it, color = sub, fontSize = dims.t(1.5f, min = 9f), maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center)
        }
    }
}

// ------------------------------------------------------------------------ volume popup

/** "आवाज़ 9/15" with a segmented bar, beside the volume keys; hides 1.6 s after the last change. */
@Composable
private fun VolumePopup(state: RailVolumeState, modifier: Modifier) {
    val at = state.shownAt
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(at) {
        if (at == 0L) return@LaunchedEffect
        visible = true
        delay(VolumeHold.POPUP_MS)
        visible = false
    }
    val level = state.level
    if (!visible || level == null) return
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val shape = theme.cardShape(dims, 0.6f)
    val fg = theme.onInk
    Column(
        modifier
            .width(dims.d(41f))
            .themedSurface(theme, shape, theme.ink, border = fg.copy(alpha = 0.18f))
            .padding(horizontal = dims.d(2.4f), vertical = dims.d(1.8f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
            Icon(
                if (level.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = if (level.muted) MutedAmber else fg,
                modifier = Modifier.size(dims.d(3.6f)),
            )
            Text(stringResource(R.string.rail_volume_title), color = fg, fontSize = dims.t(2.8f, min = 16f), fontWeight = FontWeight.Bold)
            englishLine(R.string.rail_volume_title)?.let { Text(it, color = fg.copy(alpha = 0.7f), fontSize = dims.t(1.9f, min = 11f)) }
            Spacer(Modifier.weight(1f))
            Text(
                if (level.muted) stringResource(R.string.rail_muted) else "${level.level}/${level.max}",
                color = if (level.muted) MutedAmber else fg,
                style = theme.numberStyle(fg),
                fontSize = dims.t(3.6f, min = 19f),
                maxLines = 1,
            )
        }
        val segments = RailVolume.segments(level.max)
        val filled = RailVolume.filled(level.level, level.max, segments)
        val on = if (level.muted) fg.copy(alpha = 0.45f) else theme.accent
        val off = fg.copy(alpha = 0.22f)
        Row(Modifier.fillMaxWidth().height(dims.d(2.2f)), horizontalArrangement = Arrangement.spacedBy(dims.d(0.45f))) {
            val seg = RoundedCornerShape(dims.d(0.5f))
            repeat(segments) { i ->
                Box(Modifier.weight(1f).fillMaxHeight().background(if (i < filled) on else off, seg))
            }
        }
    }
}

// ------------------------------------------------------------------------ top strip

/**
 * The pill at the top centre (Wi-Fi, Bluetooth, a grab bar and ⌄): tap or pull down for the quick
 * panel. The round ⏻ beside it opens the Power sheet (Settings › Power can hide it).
 */
@Composable
private fun TopStrip(app: DriveApp, resumeTick: Int, quickOpen: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    if (quickOpen) return
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val context = LocalContext.current
    val showPower by app.shell.stripPower.collectAsStateWithLifecycle()
    val bt by app.bluetooth.state.collectAsStateWithLifecycle()
    val wifiOn = remember(resumeTick) {
        try {
            context.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled == true
        } catch (_: Exception) {
            false
        }
    }
    val dark = theme.style == HomeStyle.MapFirst || theme.style == HomeStyle.BigGlance
    val bg = if (dark) Color(0xF017222A) else theme.surface
    val fg = if (dark) Color(0xFFF1F5F9) else theme.text
    val edge = if (dark) null else theme.border
    val h = dims.d(6.5f)
    val open by rememberUpdatedState(onOpen)
    val label = stringResource(R.string.strip_open)
    Row(modifier.padding(top = dims.d(1f)), horizontalArrangement = Arrangement.spacedBy(dims.d(1f)), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .height(h)
                .themedSurface(theme, RoundedCornerShape(50), bg, border = edge, borderWidth = 2.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = label
                    onClick { open(); true }
                }
                .pointerInput(Unit) { detectTapGestures(onTap = { open() }) }
                .pointerInput(Unit) {
                    var pulled = 0f
                    detectVerticalDragGestures(
                        onDragStart = { pulled = 0f },
                        onVerticalDrag = { _, dy ->
                            pulled += dy
                            if (pulled > 24.dp.toPx()) {
                                pulled = Float.NEGATIVE_INFINITY
                                open()
                            }
                        },
                    )
                }
                .padding(horizontal = dims.d(2.4f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
        ) {
            Icon(if (wifiOn) Icons.Filled.Wifi else Icons.Filled.WifiOff, contentDescription = null, tint = fg, modifier = Modifier.size(dims.d(3f)))
            Icon(if (bt.enabled) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled, contentDescription = null, tint = fg, modifier = Modifier.size(dims.d(3f)))
            Box(Modifier.width(dims.d(4.2f)).height(5.dp).background(fg.copy(alpha = 0.55f), RoundedCornerShape(50)))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = fg, modifier = Modifier.size(dims.d(3f)))
        }
        if (showPower) {
            Box(
                Modifier
                    .size(h)
                    .themedTap(CircleShape, bg, border = edge, borderWidth = 2.dp, onClick = { PowerControl.openSheet() }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = stringResource(R.string.power_title), tint = fg, modifier = Modifier.size(dims.d(3.2f)))
            }
        }
    }
}

// ------------------------------------------------------------------------ English sub-lines

/**
 * The English word under a Hindi (or other) label, as in the design ("पीछे / Back"). Null when
 * the app is in English or the two are the same.
 */
@Composable
internal fun englishLine(@StringRes id: Int): String? {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    if (locale.language == "en") return null
    val local = stringResource(id)
    val en = remember(id, locale) { EnglishRes.get(context, locale).getString(id) }
    return en.takeIf { it != local }
}

/** One English Resources object per app language, built once (it costs a little on a slow unit). */
private object EnglishRes {
    private var cache: Pair<Locale, Resources>? = null

    fun get(context: Context, locale: Locale): Resources {
        cache?.let { if (it.first == locale) return it.second }
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.ENGLISH)
        val res = context.createConfigurationContext(config).resources
        cache = locale to res
        return res
    }
}
