package com.vikash.ampli.drive.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.system.RestrictedSettings
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.delay

/**
 * Android 13+, sideloaded APK ("restricted settings"):
 * - before: the driver tapped "Allow access"; the steps show first, since Android refuses the
 *   switch once and only then offers App info › ⋮ › "Allow restricted settings".
 * - after: the driver went to notification access (from Music, Settings or Maps prompts) and came
 *   back with it still off. This also works when the app was restarted meanwhile (the flag is saved).
 * Closes by itself once access is on.
 */
@Composable
fun RestrictedSettingsGuideHost(resumeTick: Int) {
    val context = LocalContext.current
    val armed by RestrictedSettings.armed.collectAsStateWithLifecycle()
    val before by RestrictedSettings.before.collectAsStateWithLifecycle()
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(resumeTick) {
        RestrictedSettings.restore(context)
        if (RestrictedSettings.hasNotificationAccess(context)) {
            RestrictedSettings.hideBefore()
            if (RestrictedSettings.armed.value) RestrictedSettings.disarm(context)
            show = false
        } else if (RestrictedSettings.armed.value) {
            show = true
        }
    }
    val after = show && armed
    RestrictedSettingsGuide(
        visible = after || before,
        before = before && !after,
        onOpenAppInfo = { Launchers.openAppDetails(context) },
        onOpenAccess = { Launchers.openNotificationAccess(context) },
        onClose = {
            show = false
            RestrictedSettings.disarm(context)
        },
    )
}

/** The guide card over a scrim: springs in like the first-run name card, steps follow in turn. */
@Composable
internal fun RestrictedSettingsGuide(
    visible: Boolean,
    before: Boolean = false,
    onOpenAppInfo: () -> Unit,
    onOpenAccess: () -> Unit,
    onClose: () -> Unit,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = visible
    if (!shown.currentState && !shown.targetState && shown.isIdle) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visibleState = shown, enter = fadeIn(tween(260)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            )
        }
        AnimatedVisibility(
            visibleState = shown,
            enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } +
                scaleIn(spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.92f) +
                fadeIn(tween(240)),
            exit = slideOutVertically(tween(220)) { it / 4 } + fadeOut(tween(180)),
        ) {
            Column(
                modifier = Modifier
                    .padding(dims.d(3f))
                    .widthIn(max = 820.dp)
                    .themedCard(
                        theme,
                        RoundedCornerShape(28.dp),
                        fill = Brush.verticalGradient(listOf(theme.surfaceHigh, theme.surface)),
                        border = theme.accent.copy(alpha = 0.45f),
                        accentEdge = false,
                        blur = false,
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dims.d(4f), vertical = dims.d(3f)),
                verticalArrangement = Arrangement.spacedBy(dims.d(1.8f)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                    GlowBadge { Icon(Icons.Filled.Shield, contentDescription = null, tint = theme.text, modifier = Modifier.size(26.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(if (before) R.string.restricted_before_title else R.string.restricted_title),
                            color = theme.text,
                            fontSize = dims.t(4f, min = 21f),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(if (before) R.string.restricted_before_hint else R.string.restricted_hint),
                            color = theme.muted,
                            fontSize = dims.t(2.4f, min = 14f),
                        )
                    }
                    Box(
                        Modifier
                            .size(MinTouch)
                            .clip(CircleShape)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = theme.muted, modifier = Modifier.size(28.dp))
                    }
                }
                // Android shows ⋮ › "Allow restricted settings" only after one refused try, so the
                // try comes first. Coming back with access still off, that step is done.
                val steps = restrictedSteps().map { stringResource(it) }
                steps.forEachIndexed { i, step ->
                    GuideStep(number = i + 1, text = step, index = i, done = !before && i == 0)
                }
                if (!before) GuideStep(number = null, text = stringResource(R.string.restricted_menu_missing), index = steps.size)
                Text(
                    stringResource(R.string.restricted_play_note),
                    color = theme.accentSoft,
                    fontSize = dims.t(2.3f, min = 13f),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                    // Before: the first step is the switch. After: App info is next.
                    val appInfo: @Composable (Modifier) -> Unit = { m ->
                        BigButton(
                            text = stringResource(R.string.restricted_open_app_info),
                            onClick = onOpenAppInfo,
                            icon = Icons.Filled.Info,
                            primary = !before,
                            modifier = m,
                        )
                    }
                    val access: @Composable (Modifier) -> Unit = { m ->
                        BigButton(
                            text = stringResource(R.string.restricted_open_access),
                            onClick = onOpenAccess,
                            icon = Icons.Filled.NotificationsActive,
                            primary = before,
                            modifier = m,
                        )
                    }
                    if (before) {
                        access(Modifier.weight(1f))
                        appInfo(Modifier.weight(1f))
                    } else {
                        appInfo(Modifier.weight(1f))
                        access(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** The steps in order: try the switch (refused once), App info, ⋮ › Allow restricted settings, back and on. */
internal fun restrictedSteps(): List<Int> = listOf(
    R.string.restricted_step_try,
    R.string.restricted_step_1,
    R.string.restricted_step_2,
    R.string.restricted_step_3,
)

/** One numbered step (or a tip when [number] is null), fading up a moment after the one before. */
@Composable
private fun GuideStep(number: Int?, text: String, index: Int, done: Boolean = false) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val still = com.vikash.ampli.drive.prefs.LiteMode.enabled
    val enter = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (still) return@LaunchedEffect
        delay(120L + index * 70L)
        enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    Row(
        Modifier.graphicsLayer {
            alpha = enter.value
            translationY = (1f - enter.value) * 16.dp.toPx()
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.8f)),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (number != null) theme.accent.copy(alpha = 0.22f) else theme.surfaceHigh)
                .border(1.dp, theme.accent.copy(alpha = if (number != null) 0.6f else 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = theme.text, modifier = Modifier.size(20.dp))
            } else {
                Text(number?.toString() ?: "?", color = theme.text, fontSize = dims.t(2.4f, min = 15f), fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text,
            color = if (number != null && !done) theme.text else theme.muted,
            fontSize = dims.t(2.6f, min = 15f),
            modifier = Modifier.weight(1f),
        )
    }
}
