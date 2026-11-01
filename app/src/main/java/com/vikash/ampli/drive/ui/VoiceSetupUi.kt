package com.vikash.ampli.drive.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.voice.VoiceModelPlan
import com.vikash.ampli.drive.voice.VoiceService
import com.vikash.ampli.drive.voice.WakeModel

/** [WakeModel.State] as the plain phase [VoiceModelPlan] works with. */
private fun WakeModel.State.phase(): VoiceModelPlan.Phase = when (this) {
    WakeModel.State.Missing -> VoiceModelPlan.Phase.Missing
    is WakeModel.State.Downloading -> VoiceModelPlan.Phase.Downloading
    WakeModel.State.Unpacking -> VoiceModelPlan.Phase.Unpacking
    WakeModel.State.Ready -> VoiceModelPlan.Phase.Ready
    is WakeModel.State.Failed -> VoiceModelPlan.Phase.Failed
}

@Composable
private fun modelPart(model: WakeModel): VoiceModelPlan.Part {
    val state by model.state.collectAsStateWithLifecycle()
    val unpack by model.unpackPercent.collectAsStateWithLifecycle()
    val download = (state as? WakeModel.State.Downloading)?.percent ?: 0
    val phase = state.phase()
    return VoiceModelPlan.Part(model.spec.approxBytes, phase, VoiceModelPlan.modelPercent(phase, download, unpack, model.fromAssets))
}

/**
 * Home's thin "Getting Ampli ready… 40%" strip while the offline speech models download or
 * unpack (first start). Gone once they are ready. A tap opens Settings › Voice.
 */
@Composable
fun VoiceSetupStrip(app: DriveApp, onOpen: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val language by app.prefs.language.collectAsStateWithLifecycle()
    val want = remember(language) { app.voiceModels.want() }
    val english = modelPart(app.wakeModel)
    val hindi = modelPart(app.hindiModel)
    val parts = buildList {
        if (want.english || english.phase != VoiceModelPlan.Phase.Missing) add(english)
        if (want.hindi || hindi.phase != VoiceModelPlan.Phase.Missing) add(hindi)
    }
    val percent = VoiceModelPlan.stripPercent(parts)
    // Keeps the last value while the strip animates away.
    val last = remember { intArrayOf(0) }
    if (percent != null) last[0] = percent
    val shown = percent ?: last[0]
    AnimatedVisibility(
        visible = percent != null,
        enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(240)) + fadeOut(tween(180)),
    ) {
        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dims.d(1.6f), vertical = dims.d(0.6f))
                .themedCard(
                    theme,
                    shape,
                    fill = Brush.horizontalGradient(listOf(theme.surfaceHigh, theme.surface)),
                    border = theme.accent.copy(alpha = 0.4f),
                    accentEdge = false,
                    blur = false,
                )
                .clickable(onClick = onOpen)
                .padding(horizontal = dims.d(2f), vertical = dims.d(0.9f)),
            verticalArrangement = Arrangement.spacedBy(dims.d(0.6f)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(22.dp))
                Text(
                    stringResource(R.string.voice_getting_ready, shown),
                    color = theme.text,
                    fontSize = dims.t(2.4f, min = 14f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(theme.muted.copy(alpha = 0.25f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(shown.coerceIn(0, 100) / 100f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(theme.accent),
                )
            }
        }
    }
}

/**
 * First start: "Hey Ampli" is on by default, so the microphone is asked for once, after Play's
 * prominent disclosure, as soon as nothing else is on screen. "No thanks" or a denied permission
 * switches the wake word off (the driver can turn it on in Settings › Voice); an explicit off is
 * never switched back on.
 */
@Composable
fun FirstRunMicHost(app: DriveApp, resumeTick: Int) {
    val context = LocalContext.current
    val always by app.prefs.voiceAlways.collectAsStateWithLifecycle()
    var asked by remember { mutableStateOf(app.prefs.micFirstRunAsked) }
    val hasMic = remember(resumeTick, asked) {
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            VoiceService.start(context)
        } else {
            app.prefs.setVoiceAlways(false)
        }
    }
    fun request() {
        app.prefs.micFirstRunAsked = true
        asked = true
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        launcher.launch(perms.toTypedArray())
    }
    // Only after MainActivity's first-launch location prompt (never in previews or tests).
    if (asked || !always || hasMic || !app.prefs.askedPermissions) return
    if (app.prefs.micDisclosureAccepted) {
        LaunchedEffect(Unit) { request() }
        return
    }
    DisclosureDialog(
        kind = Disclosure.Microphone,
        onAgree = {
            app.prefs.micDisclosureAccepted = true
            request()
        },
        onDecline = {
            app.prefs.micFirstRunAsked = true
            asked = true
            app.prefs.setVoiceAlways(false)
        },
    )
}
