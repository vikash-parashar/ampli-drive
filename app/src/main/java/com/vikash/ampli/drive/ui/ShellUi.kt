package com.vikash.ampli.drive.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.prefs.ShellPrefs
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.system.Shell
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// ------------------------------------------------------------------------ default home

/** "Make Ampli your home screen": the system role dialog on Android 10+, else home settings. */
@Composable
internal fun rememberMakeHome(onDone: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onDone() }
    return remember(launcher) {
        {
            val intent = Shell.homeRoleIntent(context)
            val asked = intent != null && try {
                launcher.launch(intent)
                true
            } catch (_: Exception) {
                false
            }
            if (!asked) Launchers.openHomeSettings(context)
        }
    }
}

@Composable
internal fun DefaultHomeCard(onMake: () -> Unit, onLater: (() -> Unit)?, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Column(
        modifier
            .widthIn(max = 820.dp)
            .themedCard(
                theme,
                RoundedCornerShape(24.dp),
                fill = Brush.horizontalGradient(listOf(theme.surfaceHigh, theme.surface)),
                border = theme.accent.copy(alpha = 0.5f),
                accentEdge = false,
                blur = false,
            )
            .padding(horizontal = dims.d(3f), vertical = dims.d(2.2f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2.2f))) {
            GlowBadge { Icon(Icons.Filled.Home, contentDescription = null, tint = theme.text, modifier = Modifier.size(28.dp)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.shell_home_title), color = theme.text, fontSize = dims.t(3.6f, min = 20f), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.shell_home_hint), color = theme.muted, fontSize = dims.t(2.4f, min = 14f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f), Alignment.End)) {
            if (onLater != null) BigButton(text = stringResource(R.string.shell_home_later), onClick = onLater, primary = false)
            BigButton(text = stringResource(R.string.shell_home_action), onClick = onMake, icon = Icons.Filled.Home)
        }
    }
}

/** Home: the card springs in from the top while Ampli is not the default home app, until "Not now". */
@Composable
fun DefaultHomeCardHost(app: DriveApp, resumeTick: Int) {
    val context = LocalContext.current
    var dismissed by remember { mutableStateOf(app.shell.homeCardDismissed) }
    var tick by remember { mutableStateOf(0) }
    val isHome = remember(resumeTick, tick) { Launchers.isDefaultHome(context) }
    val makeHome = rememberMakeHome { tick++ }
    val dims = LocalDims.current
    Box(Modifier.fillMaxSize().padding(top = dims.d(12f)), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = !isHome && !dismissed,
            enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { -it } +
                scaleIn(spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.92f) + fadeIn(tween(220)),
            exit = slideOutVertically(tween(260)) { -it / 2 } + fadeOut(tween(200)),
        ) {
            DefaultHomeCard(
                onMake = makeHome,
                onLater = {
                    app.shell.homeCardDismissed = true
                    dismissed = true
                },
                modifier = Modifier.padding(horizontal = dims.d(3f)),
            )
        }
    }
}

// ------------------------------------------------------------------------ quick controls

data class QuickTile(val control: Shell.Control, val icon: ImageVector, val label: String, val state: String?, val on: Boolean)

/** Pull-down panel from Home: a grid of system controls, each opening the right settings page. */
@Composable
fun QuickControlsHost(app: DriveApp, open: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val bt by app.bluetooth.state.collectAsStateWithLifecycle()
    val status by produceState(Shell.status(context), open) {
        while (open) {
            value = Shell.status(context)
            delay(2_000L)
        }
    }
    QuickControlsPanel(
        visible = open,
        tiles = quickTiles(status, btSupported = bt.supported, btOn = bt.enabled, btDevice = bt.connectedName),
        onTile = { Shell.open(context, it) },
        onClose = onClose,
    )
}

@Composable
internal fun quickTiles(status: Shell.Status, btSupported: Boolean, btOn: Boolean, btDevice: String?): List<QuickTile> {
    val on = stringResource(R.string.shell_on)
    val off = stringResource(R.string.shell_off)
    val wifiState = when {
        status.ssid != null -> status.ssid
        status.wifiConnected -> stringResource(R.string.shell_connected)
        status.wifiOn == true -> on
        status.wifiOn == false -> off
        else -> null
    }
    return listOf(
        QuickTile(Shell.Control.Bluetooth, Icons.Filled.Bluetooth, stringResource(R.string.bt_label), if (!btSupported) null else btDevice ?: if (btOn) on else off, btSupported && btOn),
        QuickTile(Shell.Control.Wifi, Icons.Filled.Wifi, stringResource(R.string.shell_wifi), wifiState, status.wifiOn == true || status.wifiConnected),
        QuickTile(Shell.Control.Hotspot, Icons.Filled.WifiTethering, stringResource(R.string.shell_hotspot), null, false),
        QuickTile(Shell.Control.Equalizer, Icons.Filled.Equalizer, stringResource(R.string.shell_equalizer), null, false),
        QuickTile(Shell.Control.Brightness, Icons.Filled.BrightnessMedium, stringResource(R.string.shell_brightness), status.brightnessPct?.let { "$it%" }, false),
        QuickTile(Shell.Control.Volume, Icons.Filled.VolumeUp, stringResource(R.string.shell_volume), status.volumePct?.let { "$it%" }, false),
        QuickTile(Shell.Control.Display, Icons.Filled.Tv, stringResource(R.string.shell_display), null, false),
        QuickTile(Shell.Control.Sound, Icons.Filled.Settings, stringResource(R.string.shell_sound), null, false),
    )
}

@Composable
internal fun QuickControlsPanel(visible: Boolean, tiles: List<QuickTile>, onTile: (Shell.Control) -> Unit, onClose: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = visible
    if (!shown.currentState && !shown.targetState && shown.isIdle) return
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visibleState = shown, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            )
        }
        AnimatedVisibility(
            visibleState = shown,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = slideInVertically(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) { -it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(180)),
        ) {
            Column(
                Modifier
                    .padding(horizontal = dims.d(3f), vertical = dims.d(2f))
                    .widthIn(max = 980.dp)
                    .themedCard(
                        theme,
                        RoundedCornerShape(28.dp),
                        fill = Brush.verticalGradient(listOf(theme.surfaceHigh, theme.surface)),
                        border = theme.accent.copy(alpha = 0.4f),
                        accentEdge = false,
                        blur = false,
                    )
                    .padding(dims.d(2.6f)),
                verticalArrangement = Arrangement.spacedBy(dims.d(1.8f)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.shell_quick_title),
                        color = theme.text,
                        fontSize = dims.t(3.4f, min = 19f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier.size(MinTouch).clip(CircleShape).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = theme.muted, modifier = Modifier.size(28.dp))
                    }
                }
                tiles.chunked(4).forEachIndexed { row, chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                        chunk.forEachIndexed { col, tile ->
                            QuickTileView(tile, index = row * 4 + col, onClick = { onTile(tile.control) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTileView(tile: QuickTile, index: Int, onClick: () -> Unit, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    // Tiles pop in one after another as the panel lands.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(60L + index * 35L)
        enter.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
    }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .graphicsLayer {
                alpha = enter.value.coerceIn(0f, 1f)
                scaleX = 0.85f + 0.15f * enter.value
                scaleY = 0.85f + 0.15f * enter.value
            }
            .heightIn(min = 112.dp)
            .clip(shape)
            .background(if (tile.on) Brush.linearGradient(listOf(theme.accent, theme.accent2)) else Brush.linearGradient(listOf(theme.surfaceHigh, theme.surfaceHigh)))
            .border(1.dp, if (tile.on) theme.accent.copy(alpha = 0.6f) else theme.border, shape)
            .clickable(onClick = onClick)
            .padding(dims.d(1.8f)),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val fg = if (tile.on) theme.onAccent else theme.text
        Icon(tile.icon, contentDescription = null, tint = if (tile.on) theme.onAccent else theme.accentSoft, modifier = Modifier.size(32.dp))
        Text(tile.label, color = fg, fontSize = dims.t(2.5f, min = 15f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (tile.state != null) {
            Text(tile.state, color = if (tile.on) theme.onAccent.copy(alpha = 0.85f) else theme.muted, fontSize = dims.t(2.1f, min = 12f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ------------------------------------------------------------------------ phone link

/** No CarPlay / Android Auto app on this screen: say what to install. */
@Composable
internal fun PhoneLinkMissingDialog(onOpenApps: () -> Unit, onDismiss: () -> Unit) {
    val (bg, fg) = dialogColors()
    val theme = LocalDriveTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bg, titleContentColor = fg, textContentColor = fg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.PhonelinkRing, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(30.dp))
                Text(stringResource(R.string.shell_link_missing_title), fontWeight = FontWeight.Bold)
            }
        },
        text = { Text(stringResource(R.string.shell_link_missing_body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        dismissButton = { TextButton(onClick = onOpenApps) { Text(stringResource(R.string.shell_link_open_apps)) } },
    )
}

// ------------------------------------------------------------------------ PIN

/** Four-digit PIN for "More apps": [onPin] gets it once four digits are typed and OK pressed. */
@Composable
internal fun PinDialog(title: String, error: Boolean, onPin: (String) -> Unit, onDismiss: () -> Unit) {
    val (bg, fg) = dialogColors()
    val theme = LocalDriveTheme.current
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bg, titleContentColor = fg, textContentColor = fg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(28.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter { it.isDigit() }.take(4) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.shell_pin_field)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (error) Text(stringResource(R.string.shell_pin_wrong), color = theme.warning)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (ShellPrefs.validPin(pin)) onPin(pin) }, enabled = ShellPrefs.validPin(pin)) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

// ------------------------------------------------------------------------ boot splash

/**
 * After boot / ignition: the Car Hub knob turns, a cyan glow sweeps its ticks, then "Ampli" and
 * the CAR HUB tag fade in ([Shell.SPLASH_MS]). It fades into Home, where the "Ram Ram" welcome
 * card and voice follow (CarStart waits for it). Normal returns to Home never show it.
 */
@Composable
fun BootSplashHost() {
    val requested by Shell.splash.collectAsStateWithLifecycle()
    val shown = remember { MutableTransitionState(false) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(requested) {
        if (requested == 0L) return@LaunchedEffect
        progress.snapTo(0f)
        shown.targetState = true
        progress.animateTo(1f, tween((Shell.SPLASH_MS - 400L).toInt(), easing = LinearEasing))
        delay(400L)
        shown.targetState = false
        Shell.splashDone()
    }
    AnimatedVisibility(visibleState = shown, enter = fadeIn(tween(150)), exit = fadeOut(tween(450))) {
        BootSplash(progress.value)
    }
}

private val SplashDeep = Color(0xFF061218)
private val SplashTeal = Color(0xFF0B3A48)
private val TileTop = Color(0xFF0B2733)
private val TileBottom = Color(0xFF0891B2)
private val Cyan = Color(0xFF67E8F9)

/** One frame of the startup animation; [t] runs 0 to 1. Pure, so previews can draw any frame. */
@Composable
fun BootSplash(t: Float) {
    fun phase(from: Float, to: Float) = FastOutSlowInEasing.transform(((t - from) / (to - from)).coerceIn(0f, 1f))
    val tileIn = phase(0f, 0.25f)
    val turn = phase(0.08f, 0.62f)
    val sweep = phase(0.15f, 0.7f)
    val nameIn = phase(0.5f, 0.8f)
    val tagIn = phase(0.62f, 0.92f)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(SplashTeal, SplashDeep)))
            // Takes every touch while it plays.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(196.dp)
                    .graphicsLayer {
                        alpha = tileIn
                        scaleX = 0.82f + 0.18f * tileIn
                        scaleY = 0.82f + 0.18f * tileIn
                    }
                    .clip(RoundedCornerShape(48.dp))
                    .background(Brush.linearGradient(listOf(TileTop, TileBottom))),
                contentAlignment = Alignment.Center,
            ) {
                Knob(turn = turn, sweep = sweep, modifier = Modifier.size(132.dp))
            }
            Text(
                "Ampli",
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer {
                    alpha = nameIn
                    translationY = (1f - nameIn) * 24.dp.toPx()
                },
            )
            Text(
                "CAR HUB",
                color = TileTop,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = tagIn
                        scaleX = 0.9f + 0.1f * tagIn
                        scaleY = 0.9f + 0.1f * tagIn
                    }
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }
    }
}

/** The Ampli knob: seven ticks over a white dial whose pointer turns up to 12 o'clock. */
@Composable
private fun Knob(turn: Float, sweep: Float, modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height * 0.56f)
        val r = size.minDimension * 0.27f
        val tickIn = r * 1.32f
        val tickOut = r * 1.62f
        // Ticks fan over the top, from -150° to -30° (0° = 3 o'clock).
        val angles = (0 until 7).map { -150f + it * 20f }
        val sweepTo = -150f + 120f * sweep
        // Cyan halo that follows the sweep.
        if (sweep > 0f) {
            drawArc(
                brush = Brush.sweepGradient(listOf(Cyan.copy(alpha = 0f), Cyan.copy(alpha = 0.55f), Cyan.copy(alpha = 0f)), center = c),
                startAngle = -160f,
                sweepAngle = 140f * sweep,
                useCenter = false,
                topLeft = Offset(c.x - tickOut * 1.08f, c.y - tickOut * 1.08f),
                size = Size(tickOut * 2.16f, tickOut * 2.16f),
                style = Stroke(width = r * 0.5f, cap = StrokeCap.Round),
                alpha = 0.6f,
            )
        }
        for (a in angles) {
            val lit = a <= sweepTo + 0.5f
            val rad = Math.toRadians(a.toDouble())
            val dir = Offset(cos(rad).toFloat(), sin(rad).toFloat())
            drawLine(
                color = if (lit) Cyan else Color.White.copy(alpha = 0.35f),
                start = c + dir * tickIn,
                end = c + dir * tickOut,
                strokeWidth = r * 0.16f,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(Color.White, radius = r, center = c)
        // Pointer: starts at 8 o'clock and turns to 12.
        rotate(degrees = -120f * (1f - turn), pivot = c) {
            drawLine(TileTop, start = c, end = Offset(c.x, c.y - r * 0.78f), strokeWidth = r * 0.2f, cap = StrokeCap.Round)
        }
    }
}
