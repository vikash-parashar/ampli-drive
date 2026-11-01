package com.vikash.ampli.drive.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.system.PowerAction
import com.vikash.ampli.drive.system.PowerControl
import com.vikash.ampli.drive.system.PowerHelperService
import com.vikash.ampli.drive.system.PowerUi
import com.vikash.ampli.drive.system.QuickActions
import com.vikash.ampli.drive.system.RestrictedSettings
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * Everything [PowerControl] shows: the Power sheet, the one-time helper card (or the root note),
 * the voice question, the countdown, "Tap Restart here", and the black Screen off cover and the
 * clock screensaver. Cards sit right of Home's rail ([inset]); the cover takes the whole screen.
 */
@Composable
fun PowerHost(resumeTick: Int, inset: Dp) {
    val ui by PowerControl.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Android 13+ sideload: the helper's switch is "restricted"; show how to allow it.
    var guide by remember { mutableStateOf(false) }
    LaunchedEffect(resumeTick) {
        guide = PowerControl.waitingForHelper && !PowerHelperService.enabled(context) && RestrictedSettings.mayBlock(context)
    }
    if (ui != PowerUi.None) BackHandler(enabled = true) { if (ui == PowerUi.ScreenOff || ui == PowerUi.Screensaver) PowerControl.wake() else PowerControl.close() }
    when (val s = ui) {
        PowerUi.None -> Unit
        PowerUi.ScreenOff -> BlackCover(clock = false)
        PowerUi.Screensaver -> BlackCover(clock = true)
        else -> Box(Modifier.fillMaxSize().padding(start = inset)) {
            Scrim(dim = s !is PowerUi.Pointer) { if (s is PowerUi.Sheet || s is PowerUi.Failed) PowerControl.close() }
            when (s) {
                PowerUi.Sheet -> PowerSheet()
                is PowerUi.Setup -> if (s.rooted) RootNotice() else HelperSetup()
                is PowerUi.Confirm -> AskCard(
                    title = if (s.action == PowerAction.Restart) R.string.power_ask_restart else R.string.power_ask_off,
                    hint = R.string.power_ask_hint,
                    yes = R.string.power_yes,
                    no = R.string.power_no,
                    onYes = { PowerControl.request(context, s.action) },
                    onNo = { PowerControl.close() },
                )
                is PowerUi.Countdown -> if (s.voice) {
                    AskCard(
                        titleText = stringResource(countingRes(s.action), s.left),
                        sub = if (s.action == PowerAction.ScreenOff) R.string.power_screen_music else null,
                        hint = R.string.power_counting_hint_voice,
                        yes = R.string.power_cancel,
                        no = R.string.power_now,
                        onYes = { PowerControl.close() },
                        onNo = { PowerControl.now(context) },
                    )
                } else {
                    CountdownCard(s)
                }
                is PowerUi.Working -> MessageCard(
                    icon = actionIcon(s.action),
                    title = if (s.action == PowerAction.Restart) R.string.power_working_restart else R.string.power_working_off,
                    body = null,
                    onClose = null,
                )
                is PowerUi.Pointer -> PointerHint(s.action)
                is PowerUi.Failed -> MessageCard(
                    icon = actionIcon(s.action),
                    title = R.string.power_failed_title,
                    body = R.string.power_failed_body,
                    onClose = { PowerControl.close() },
                )
                else -> Unit
            }
        }
    }
    RestrictedSettingsGuide(
        visible = guide && ui is PowerUi.Setup,
        before = false,
        onOpenAppInfo = { Launchers.openAppDetails(context) },
        onOpenAccess = { PowerHelperService.openSettings(context) },
        onClose = { guide = false },
        copy = RestrictedCopy.PowerHelper,
    )
}

@Composable
private fun Scrim(dim: Boolean, onTap: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (dim) 0.5f else 0f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
    )
}

private fun countingRes(action: PowerAction): Int = when (action) {
    PowerAction.ScreenOff -> R.string.power_counting_screen
    PowerAction.Restart -> R.string.power_counting_restart
    PowerAction.PowerOff -> R.string.power_counting_off
}

private fun actionIcon(action: PowerAction): ImageVector = when (action) {
    PowerAction.ScreenOff -> Icons.Filled.DarkMode
    PowerAction.Restart -> Icons.Filled.RestartAlt
    PowerAction.PowerOff -> Icons.Filled.PowerSettingsNew
}

private fun actionLabel(action: PowerAction): Int = when (action) {
    PowerAction.ScreenOff -> R.string.power_screen_off
    PowerAction.Restart -> R.string.power_restart
    PowerAction.PowerOff -> R.string.power_off
}

/** The card every power screen sits in: opaque, theme border, no blur. */
@Composable
private fun PowerCard(modifier: Modifier, content: @Composable () -> Unit) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val fill = if (theme.surface.alpha < 1f) theme.background else theme.surface
    Column(
        modifier
            .themedCard(theme, theme.cardShape(dims), fill = SolidColor(fill), border = theme.border, accentEdge = false, blur = false)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = dims.d(3.4f), vertical = dims.d(3f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(1.8f)),
    ) { content() }
}

/** "पावर Power" style: the localized words, then the English in a lighter size beside them. */
@Composable
private fun BiTitle(@StringRes id: Int, size: Float, min: Float, modifier: Modifier = Modifier, color: Color? = null) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(dims.d(1f))) {
        Text(stringResource(id), color = color ?: theme.text, fontSize = dims.t(size, min = min), fontWeight = FontWeight.Bold)
        englishLine(id)?.let { Text(it, color = (color ?: theme.muted).copy(alpha = 0.8f), fontSize = dims.t(size * 0.62f, min = min * 0.7f), modifier = Modifier.padding(bottom = 2.dp)) }
    }
}

@Composable
private fun CloseKey(onClose: () -> Unit) {
    val theme = LocalDriveTheme.current
    Box(Modifier.size(MinTouch).themedTap(CircleShape, theme.surfaceHigh, onClick = onClose), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = theme.text, modifier = Modifier.size(28.dp))
    }
}

/** A wide key with the localized word and the English beside it. */
@Composable
private fun BiKey(@StringRes id: Int, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val bg = if (primary) theme.accent else theme.surfaceHigh
    val fg = if (primary) theme.onAccent else theme.text
    Row(
        modifier.heightIn(min = maxOf(dims.d(9f), MinTouch)).themedTap(theme.buttonShape(dims), bg, border = theme.border, onClick = onClick).padding(horizontal = dims.d(2f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(id), color = fg, fontSize = dims.t(3f, min = 17f), fontWeight = FontWeight.Bold)
        englishLine(id)?.let {
            Spacer(Modifier.width(dims.d(1.4f)))
            Text(it, color = fg.copy(alpha = 0.85f), fontSize = dims.t(2f, min = 12f))
        }
    }
}

// ------------------------------------------------------------------------ sheet

@Composable
private fun androidx.compose.foundation.layout.BoxScope.PowerSheet() {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val context = LocalContext.current
    PowerCard(Modifier.align(Alignment.Center).padding(horizontal = dims.d(7f)).widthIn(max = 1000.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BiTitle(R.string.power_title, 3.2f, 18f, Modifier.weight(1f))
            CloseKey { PowerControl.close() }
        }
        Row(Modifier.fillMaxWidth().height(dims.d(18f)), horizontalArrangement = Arrangement.spacedBy(dims.d(1.8f))) {
            for (action in PowerAction.values()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .themedTap(theme.cardShape(dims, 0.6f), theme.surfaceHigh, border = theme.border) { PowerControl.request(context, action) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(actionIcon(action), contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(dims.d(5.4f)))
                    Text(stringResource(actionLabel(action)), color = theme.text, fontSize = dims.t(3.2f, min = 18f), fontWeight = FontWeight.Bold)
                    englishLine(actionLabel(action))?.let { Text(it, color = theme.muted, fontSize = dims.t(2.1f, min = 12f)) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.power_wait_note), color = theme.text, fontSize = dims.t(2f, min = 12f))
                englishLine(R.string.power_wait_note)?.let { Text(it, color = theme.muted, fontSize = dims.t(1.5f, min = 10f)) }
            }
            BiKey(R.string.power_cancel, primary = false, modifier = Modifier.width(dims.d(31f))) { PowerControl.close() }
        }
    }
}

// ------------------------------------------------------------------------ one-time cards

@Composable
private fun androidx.compose.foundation.layout.BoxScope.HelperSetup() {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val context = LocalContext.current
    PowerCard(Modifier.align(Alignment.Center).padding(horizontal = dims.d(11f)).widthIn(max = 1000.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            Box(Modifier.size(dims.d(10.5f)).background(theme.accent, theme.buttonShape(dims)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = theme.onAccent, modifier = Modifier.size(dims.d(5.4f)))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.power_setup_title), color = theme.text, fontSize = dims.t(3.6f, min = 19f), fontWeight = FontWeight.Bold)
                englishLine(R.string.power_setup_title)?.let { Text(it, color = theme.muted, fontSize = dims.t(2.2f, min = 12f)) }
            }
        }
        Column {
            Text(stringResource(R.string.disclosure_power_body), color = theme.text, fontSize = dims.t(2.6f, min = 15f))
            englishLine(R.string.disclosure_power_body)?.let { Text(it, color = theme.muted, fontSize = dims.t(1.9f, min = 11f)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
            listOf(R.string.power_setup_step1, R.string.power_setup_step2, R.string.power_setup_step3).forEachIndexed { i, step ->
                Row(
                    Modifier.weight(1f).themedSurface(theme, theme.cardShape(dims, 0.4f), theme.surfaceHigh, border = theme.border).padding(dims.d(1.4f)),
                    horizontalArrangement = Arrangement.spacedBy(dims.d(1f)),
                ) {
                    Box(Modifier.size(dims.d(3.6f)).background(theme.accent, CircleShape), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = theme.onAccent, fontSize = dims.t(2f, min = 12f), fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(stringResource(step), color = theme.text, fontSize = dims.t(2.2f, min = 13f), fontWeight = FontWeight.SemiBold)
                        englishLine(step)?.let { Text(it, color = theme.muted, fontSize = dims.t(1.7f, min = 10f)) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(dims.d(3.2f)))
            Column {
                Text(stringResource(R.string.disclosure_power_private), color = theme.text, fontSize = dims.t(2f, min = 12f))
                englishLine(R.string.disclosure_power_private)?.let { Text(it, color = theme.muted, fontSize = dims.t(1.5f, min = 10f)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            BiKey(R.string.power_setup_turn_on, primary = true, modifier = Modifier.weight(1.4f)) { PowerControl.setupContinue(context) }
            BiKey(R.string.power_setup_later, primary = false, modifier = Modifier.weight(1f)) { PowerControl.close() }
        }
    }
}

/** Rooted unit, first time: Restart and Power off will act directly (after the countdown). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.RootNotice() {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    PowerCard(Modifier.align(Alignment.Center).padding(horizontal = dims.d(14f)).widthIn(max = 900.dp)) {
        BiTitle(R.string.power_root_title, 3.6f, 19f)
        Column {
            Text(stringResource(R.string.power_root_body), color = theme.text, fontSize = dims.t(2.5f, min = 14f))
            englishLine(R.string.power_root_body)?.let { Text(it, color = theme.muted, fontSize = dims.t(1.9f, min = 11f)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            BiKey(R.string.power_root_ok, primary = true, modifier = Modifier.weight(1.4f)) { PowerControl.setupContinue(context) }
            BiKey(R.string.power_cancel, primary = false, modifier = Modifier.weight(1f)) { PowerControl.close() }
        }
    }
}

// ------------------------------------------------------------------------ voice card and countdown

/** The "AMPLI" card: a question (or the voice countdown), what to say, and two keys. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.AskCard(
    @StringRes hint: Int,
    @StringRes yes: Int,
    @StringRes no: Int,
    onYes: () -> Unit,
    onNo: () -> Unit,
    @StringRes title: Int? = null,
    titleText: String? = null,
    @StringRes sub: Int? = null,
) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    PowerCard(Modifier.align(Alignment.TopCenter).padding(top = dims.d(11f), start = dims.d(18f), end = dims.d(18f)).widthIn(max = 900.dp)) {
        Text("AMPLI", color = theme.accentSoft, fontSize = dims.t(1.9f, min = 12f), fontWeight = FontWeight.Bold, letterSpacing = androidx.compose.ui.unit.TextUnit(0.12f, androidx.compose.ui.unit.TextUnitType.Em))
        Column {
            Text(titleText ?: stringResource(title!!), color = theme.text, fontSize = dims.t(4.2f, min = 22f), fontWeight = FontWeight.Bold)
            title?.let { englishLine(it) }?.let { Text(it, color = theme.muted, fontSize = dims.t(2.4f, min = 14f)) }
            sub?.let { Text(stringResource(it), color = theme.muted, fontSize = dims.t(2.4f, min = 14f)) }
        }
        Text(stringResource(hint), color = theme.muted, fontSize = dims.t(2f, min = 12f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            BiKey(yes, primary = true, modifier = Modifier.weight(1f), onClick = onYes)
            BiKey(no, primary = false, modifier = Modifier.weight(1f), onClick = onNo)
        }
    }
}

/** Restart / Power off in N s: a ring, a big Cancel and "Now". */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.CountdownCard(s: PowerUi.Countdown) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val context = LocalContext.current
    PowerCard(Modifier.align(Alignment.Center).padding(horizontal = dims.d(18f)).widthIn(max = 900.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(3f))) {
            Box(Modifier.size(dims.d(25f)), contentAlignment = Alignment.Center) {
                val fraction = if (s.total > 0) s.left.toFloat() / s.total else 0f
                val track = theme.surfaceHigh
                val accent = theme.accent
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.minDimension * 0.08f
                    drawArc(track, 0f, 360f, false, style = Stroke(w))
                    drawArc(accent, -90f, 360f * fraction, false, style = Stroke(w, cap = StrokeCap.Round))
                }
                Text("${s.left}", style = theme.numberStyle(theme.text), fontSize = dims.t(10f, min = 48f))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dims.d(1f))) {
                Text(stringResource(countingRes(s.action), s.left), color = theme.text, fontSize = dims.t(4f, min = 21f), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(if (s.rooted) R.string.power_countdown_root else R.string.power_countdown_menu),
                    color = theme.muted,
                    fontSize = dims.t(2.2f, min = 13f),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            BiKey(R.string.power_cancel, primary = true, modifier = Modifier.weight(1.6f).heightIn(min = dims.d(14.5f))) { PowerControl.close() }
            BiKey(R.string.power_now, primary = false, modifier = Modifier.weight(1f).heightIn(min = dims.d(14.5f))) { PowerControl.now(context) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.MessageCard(icon: ImageVector, @StringRes title: Int, @StringRes body: Int?, onClose: (() -> Unit)?) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    PowerCard(Modifier.align(Alignment.Center).padding(horizontal = dims.d(18f)).widthIn(max = 860.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            Icon(icon, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(dims.d(6f)))
            BiTitle(title, 3.6f, 19f, Modifier.weight(1f))
            if (onClose != null) CloseKey(onClose)
        }
        body?.let { Text(stringResource(it), color = theme.text, fontSize = dims.t(2.5f, min = 14f)) }
    }
}

/** Android's power menu is open in the middle: "Tap Restart here" points at it from the right. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PointerHint(action: PowerAction) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    Row(
        Modifier
            .align(Alignment.CenterEnd)
            .padding(end = dims.d(3f))
            .widthIn(max = dims.d(40f))
            .themedSurface(theme, theme.cardShape(dims, 0.6f), theme.accent, border = theme.border)
            .clickable { PowerControl.close() }
            .padding(dims.d(2.2f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = theme.onAccent, modifier = Modifier.size(dims.d(5f)))
        Column {
            Text(
                stringResource(if (action == PowerAction.Restart) R.string.power_pointer_restart else R.string.power_pointer_off),
                color = theme.onAccent,
                fontSize = dims.t(3f, min = 17f),
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.power_pointer_body), color = theme.onAccent.copy(alpha = 0.85f), fontSize = dims.t(2f, min = 12f))
        }
        Icon(Icons.Filled.TouchApp, contentDescription = null, tint = theme.onAccent, modifier = Modifier.size(dims.d(4f)))
    }
}

// ------------------------------------------------------------------------ Screen off and screensaver

/**
 * Black over everything, the backlight at its lowest for this window; music, navigation voice
 * and "Hey Ampli" keep running. Any touch wakes it. [clock]: the screensaver's dim clock.
 */
@Composable
private fun BlackCover(clock: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    if (!clock) {
        DisposableEffect(activity) {
            val before = activity?.window?.attributes?.screenBrightness
            QuickActions.setWindowBrightness(activity, 0f)
            onDispose { QuickActions.setWindowBrightness(activity, before?.takeIf { it >= 0f }) }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onPress = { PowerControl.wake() }) },
        contentAlignment = Alignment.Center,
    ) {
        if (clock) ScreensaverClock()
    }
}

@Composable
private fun ScreensaverClock() {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = System.currentTimeMillis()
        }
    }
    // The clock moves a little each minute so nothing burns into the panel.
    val minute = (now / 60_000L).toInt()
    val dx = ((minute * 37) % 21 - 10).toFloat()
    val dy = ((minute * 53) % 15 - 7).toFloat()
    val time = remember(now) { android.text.format.DateFormat.getTimeFormat(context).format(Date(now)) }
    val date = remember(now) { DateFormat.getDateInstance(DateFormat.FULL).format(Date(now)) }
    Column(Modifier.padding(start = dims.d(10f + dx), top = dims.d(10f + dy)), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, style = theme.numberStyle(Color(0xFFB8BCC4)), fontSize = dims.t(22f, min = 96f), textAlign = TextAlign.Center)
        Text(date, color = Color(0xFF7C818A), fontSize = dims.t(3.4f, min = 18f))
        Text(stringResource(R.string.power_touch_wake), color = Color(0xFF5A5F68), fontSize = dims.t(2.2f, min = 13f), modifier = Modifier.padding(top = dims.d(3f)))
    }
}
