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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.em
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
 * Car Hub's control rail (Back, Home, Apps, Play/Pause, Vol +, Vol −, Mute, Controls) for head
 * units without buttons, the volume popup and the top strip that pulls down the quick panel.
 * India drives on the left, so the driver sits on the right: the rail is on the screen's right
 * edge, on every Car Hub screen (DriveRoot puts it beside whatever screen is showing). Flat fills
 * only, no animation on the keys: one redraw per tap on an Android 8.1 unit.
 */

/** The rail's width: 104 px at 1280x800 (design board "Rail"). */
internal const val RAIL_UNITS = 13f

/** The rail's keys from top to bottom, and how they fit a screen's height. Pure, so it is unit-tested. */
internal object RailSpec {
    enum class Key { Back, Home, Apps, PlayPause, VolUp, VolDown, Mute, Controls }

    val ORDER: List<Key> = Key.values().toList()

    /** Key height, gap and top/bottom padding, in Dims units (1 unit = 1% of the screen height). */
    const val KEY_UNITS = 10.5f
    const val GAP_UNITS = 0.8f
    const val PAD_UNITS = 1.5f
    const val SIDE_UNITS = 1.5f

    /** Height the keys, gaps and padding take, in units; the rest is the gap above Controls. */
    fun usedUnits(keys: Int = ORDER.size): Float = 2 * PAD_UNITS + keys * KEY_UNITS + (keys - 1) * GAP_UNITS

    /** A key's height and width in px on a screen [heightPx] tall. */
    fun keyHeightPx(heightPx: Int): Float = heightPx * KEY_UNITS / 100f
    fun keyWidthPx(heightPx: Int): Float = heightPx * (RAIL_UNITS - 2 * SIDE_UNITS) / 100f
}

/** Ink on the amber Mute key (the same in every theme, like the SOS red). */
private val MutedInk = Color(0xFF1A1206)

/** The rail's colours for one theme: strip, key fill, ink, active key and the muted amber. */
private class RailColors(
    val bg: Color,
    val key: Color,
    val fg: Color,
    val muted: Color,
    val on: Color,
    val onFg: Color,
    val edge: Color?,
    val amber: Color,
)

private fun railColors(theme: DriveTheme): RailColors = RailColors(
    bg = theme.rail,
    key = theme.railKey,
    fg = theme.railInk,
    muted = theme.railInk.copy(alpha = 0.72f),
    on = theme.banner,
    onFg = theme.onBanner,
    edge = if (theme.cardStyle == com.vikash.ampli.drive.ui.theme.CardStyle.Brutal) null else theme.border,
    amber = theme.warning,
)

/** The rail's own copy of the music volume, and when the popup was last asked for. */
internal class RailVolumeState {
    var level by mutableStateOf<RailVolume.Level?>(null)
    var shownAt by mutableLongStateOf(0L)

    fun show(next: RailVolume.Level?) {
        if (next != null) level = next
        shownAt = android.os.SystemClock.elapsedRealtime()
    }
}

/**
 * The quick panel's pull-down, on every Car Hub screen: a thin glowing line at the top centre
 * (it pulses three times when a screen opens, then rests) and a drag zone along the top edge.
 * Pull down anywhere along the top, or tap the line, for the quick panel. Hidden while it is open.
 */
@Composable
internal fun TopHandle(open: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (open) return
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val glow = theme.accent
    val pulse = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) {
        repeat(3) {
            pulse.animateTo(0.35f, androidx.compose.animation.core.tween(700))
            pulse.animateTo(1f, androidx.compose.animation.core.tween(700))
        }
    }
    val tap by rememberUpdatedState(onOpen)
    val label = stringResource(R.string.strip_open)
    val pullDown = Modifier.pointerInput(Unit) {
        var pulled = 0f
        detectVerticalDragGestures(
            onDragStart = { pulled = 0f },
            onVerticalDrag = { _, dy ->
                pulled += dy
                if (pulled > 24.dp.toPx()) {
                    pulled = Float.NEGATIVE_INFINITY
                    tap()
                }
            },
        )
    }
    Box(modifier.fillMaxWidth()) {
        // The top edge: drags only (taps go through to the screen's own header).
        Box(Modifier.fillMaxWidth().height(dims.d(2.4f)).then(pullDown))
        // The handle: a bigger target for a tap or a pull.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .width(dims.d(22f))
                .height(maxOf(dims.d(5.5f), 40.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = label
                    onClick { tap(); true }
                }
                .pointerInput(Unit) { detectTapGestures(onTap = { tap() }) }
                .then(pullDown),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .padding(top = dims.d(0.8f))
                    .width(dims.d(15f))
                    .height(dims.d(1.6f))
                    .graphicsLayer { alpha = pulse.value },
                contentAlignment = Alignment.Center,
            ) {
                // A soft halo (no blur on Android 8.1: a wider, faint pill) and the line.
                Box(Modifier.fillMaxSize().background(glow.copy(alpha = 0.22f), RoundedCornerShape(50)))
                Box(Modifier.width(dims.d(12f)).height(dims.d(0.7f)).background(glow, RoundedCornerShape(50)))
            }
        }
    }
}

/**
 * The rail itself, on the right edge. [homeActive]: Home is the screen showing (its key is lit
 * unless the quick panel is up).
 */
@Composable
internal fun PowerRail(
    app: DriveApp,
    resumeTick: Int,
    volume: RailVolumeState,
    homeActive: Boolean,
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
    // Play / Pause follows the session the music card shows (Ampli MP3, BT music, a USB player...).
    val media by app.mediaHub.state.collectAsStateWithLifecycle()
    val playing = media.nowPlaying?.isPlaying == true
    var m = modifier.background(c.bg)
    if (c.edge != null) m = m.drawLeftEdge(c.edge, 2.dp)
    // Eight 80 x 84 keys, 6 px apart, 12 px from the top and bottom at 1280x800 (RailSpec).
    Column(
        m.padding(horizontal = dims.d(RailSpec.SIDE_UNITS), vertical = dims.d(RailSpec.PAD_UNITS)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dims.d(RailSpec.GAP_UNITS)),
    ) {
        RailKey(Icons.AutoMirrored.Filled.ArrowBack, R.string.rail_back, c, onClick = onBack)
        RailKey(Icons.Filled.Home, R.string.rail_home, c, active = homeActive && !controlsOpen, onClick = onHome)
        RailKey(
            Icons.Filled.Apps, R.string.rail_apps, c,
            onClick = onOpenApps,
            // Long press: Android's recent apps through the helper, else the app drawer.
            onLongClick = { if (!PowerHelperService.recents()) onOpenApps() },
        )
        RailKey(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (playing) R.string.rail_pause else R.string.rail_play,
            c,
            onClick = { if (!app.mediaHub.togglePlayback()) QuickActions.mediaPlayPause(context) },
        )
        VolumeKey(Icons.AutoMirrored.Filled.VolumeUp, R.string.rail_vol_up, c) { volume.show(QuickActions.stepVolume(context, 1)) }
        VolumeKey(Icons.AutoMirrored.Filled.VolumeDown, R.string.rail_vol_down, c) { volume.show(QuickActions.stepVolume(context, -1)) }
        RailKey(
            Icons.AutoMirrored.Filled.VolumeOff, R.string.rail_mute, c,
            fill = if (muted) c.amber else null,
            fillFg = MutedInk,
            description = stringResource(if (muted) R.string.rail_unmute else R.string.rail_mute),
            onClick = { volume.show(QuickActions.toggleMute(context)) },
        )
        Spacer(Modifier.weight(1f))
        RailKey(Icons.Filled.Tune, R.string.rail_controls, c, active = controlsOpen, onClick = onControls)
    }
}

/** "android.media.VOLUME_CHANGED_ACTION": sent by every Android since 4.x, though not in the SDK. */
private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"

/** The hairline between the screen and the rail (the rail sits on the right). */
private fun Modifier.drawLeftEdge(color: Color, width: Dp): Modifier = drawBehind {
    drawRect(color, topLeft = Offset.Zero, size = Size(width.toPx(), size.height))
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
    val bg = fill ?: if (active) c.on else c.key
    val fg = when {
        fill != null -> fillFg
        active -> c.onFg
        else -> c.fg
    }
    val edge = if (outlined && !active) c.muted.copy(alpha = 0.5f) else null
    var m = Modifier.fillMaxWidth().height(dims.d(RailSpec.KEY_UNITS)).clip(shape).background(bg, shape)
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
        .height(dims.d(RailSpec.KEY_UNITS))
        .clip(shape)
        .background(if (pressed) c.on else c.key, shape)
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
    KeyFace(icon, label, if (pressed) c.onFg else c.fg, if (pressed) c.onFg else c.muted, m)
}

/** Icon, the label in the app's language and, when that is not English, the English word under it. */
@Composable
private fun KeyFace(icon: ImageVector, @StringRes label: Int, fg: Color, sub: Color, modifier: Modifier) {
    val dims = LocalDims.current
    Column(modifier.padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(dims.d(0.3f), Alignment.CenterVertically)) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(dims.d(3.6f)))
        // The whole word, always ("CONTROLS" shrinks to fit the 80 px key instead of "CONTRO…").
        FitText(stringResource(label).uppercase(), color = fg, fontSize = dims.t(2f, min = 13f), letterSpacing = 0.04.em)
        englishLine(label)?.let {
            Text(it, color = sub, fontSize = dims.t(1.5f, min = 9f), maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center)
        }
    }
}

// ------------------------------------------------------------------------ volume popup

/** "आवाज़ 9/15" with a segmented bar, beside the volume keys; hides 1.6 s after the last change. */
@Composable
internal fun VolumePopup(state: RailVolumeState, modifier: Modifier) {
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
                tint = if (level.muted) theme.warning else fg,
                modifier = Modifier.size(dims.d(3.6f)),
            )
            Text(stringResource(R.string.rail_volume_title), color = fg, fontSize = dims.t(2.8f, min = 16f), fontWeight = FontWeight.Bold)
            englishLine(R.string.rail_volume_title)?.let { Text(it, color = fg.copy(alpha = 0.7f), fontSize = dims.t(1.9f, min = 11f)) }
            Spacer(Modifier.weight(1f))
            Text(
                if (level.muted) stringResource(R.string.rail_muted) else "${level.level}/${level.max}",
                color = if (level.muted) theme.warning else fg,
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
    // Map First's own top bar (clock, Hey Ampli, FASTag) takes the pull-down there.
    if (theme.style == HomeStyle.MapFirst) return
    val bg = theme.surface
    val fg = theme.text
    val edge = theme.border
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
