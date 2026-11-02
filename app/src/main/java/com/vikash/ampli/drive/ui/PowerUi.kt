package com.vikash.ampli.drive.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.vikash.ampli.drive.system.PowerAction
import com.vikash.ampli.drive.system.PowerControl
import com.vikash.ampli.drive.system.PowerUi
import com.vikash.ampli.drive.system.QuickActions
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * Everything [PowerControl] shows: the Power sheet (Screen off, Screensaver), the voice
 * screen-off countdown, and the black Screen off cover and the clock screensaver. Cards sit right
 * of Home's rail ([inset]); the cover takes the whole screen.
 */
@Composable
fun PowerHost(@Suppress("UNUSED_PARAMETER") resumeTick: Int, inset: Dp) {
    val ui by PowerControl.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (ui != PowerUi.None) BackHandler(enabled = true) { if (ui == PowerUi.ScreenOff || ui == PowerUi.Screensaver) PowerControl.wake() else PowerControl.close() }
    when (val s = ui) {
        PowerUi.None -> Unit
        PowerUi.ScreenOff -> BlackCover(clock = false)
        PowerUi.Screensaver -> BlackCover(clock = true)
        else -> Box(Modifier.fillMaxSize().padding(start = inset)) {
            Scrim(dim = true) { if (s is PowerUi.Sheet) PowerControl.close() }
            when (s) {
                PowerUi.Sheet -> PowerSheet()
                is PowerUi.Countdown -> AskCard(
                    titleText = stringResource(R.string.power_counting_screen, s.left),
                    sub = R.string.power_screen_music,
                    hint = R.string.power_counting_hint_voice,
                    yes = R.string.power_cancel,
                    no = R.string.power_now,
                    onYes = { PowerControl.close() },
                    onNo = { PowerControl.now(context) },
                )
                else -> Unit
            }
        }
    }
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

/** The Power sheet's keys: Screen off and the clock screensaver. */
private enum class SheetKey(val icon: ImageVector, @StringRes val label: Int) {
    ScreenOff(Icons.Filled.DarkMode, R.string.power_screen_off),
    Screensaver(Icons.Filled.Tv, R.string.qp_screensaver),
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
            for (key in SheetKey.values()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .themedTap(theme.cardShape(dims, 0.6f), theme.surfaceHigh, border = theme.border) {
                            when (key) {
                                SheetKey.ScreenOff -> PowerControl.request(context, PowerAction.ScreenOff)
                                SheetKey.Screensaver -> PowerControl.screensaver()
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(key.icon, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(dims.d(5.4f)))
                    Text(stringResource(key.label), color = theme.text, fontSize = dims.t(3.2f, min = 18f), fontWeight = FontWeight.Bold)
                    englishLine(key.label)?.let { Text(it, color = theme.muted, fontSize = dims.t(2.1f, min = 12f)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            BiKey(R.string.power_cancel, primary = false, modifier = Modifier.width(dims.d(31f))) { PowerControl.close() }
        }
    }
}

// ------------------------------------------------------------------------ voice card and countdown

/** The "AMPLI" card: the voice screen-off countdown, what to say, and two keys. */
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
