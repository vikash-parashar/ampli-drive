package com.vikash.ampli.drive.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import kotlin.math.roundToInt
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.voice.VoiceActions
import com.vikash.ampli.drive.voice.VoiceService
import com.vikash.ampli.drive.voice.WakeModel

/** Settings › Voice: the wake-word switch, its model download, and the permissions voice needs. */
@Composable
internal fun VoiceSettingsCard(app: DriveApp, resumeTick: Int) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val always by app.prefs.voiceAlways.collectAsStateWithLifecycle()
    val model by app.wakeModel.state.collectAsStateWithLifecycle()
    var recheck by remember { mutableIntStateOf(0) }

    val hasMic = remember(resumeTick, recheck) {
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val hasMusic = remember(resumeTick, recheck) { VoiceActions.hasAudioPermission(context) }

    val turnOnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        recheck++
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            app.prefs.setVoiceAlways(true)
            app.wakeModel.ensure()
        }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        recheck++
    }

    // Start listening as soon as the model is in place (the switch may have been flipped mid-download).
    LaunchedEffect(always, model) {
        if (always && model == WakeModel.State.Missing) app.wakeModel.ensure()
        if (always && model == WakeModel.State.Ready) VoiceService.start(context)
    }

    /** Disclosure pending: 0 none, 1 before switching the wake word on, 2 before the mic permission only. */
    var askMic by remember { mutableIntStateOf(0) }
    val sensitivity by app.prefs.wakeSensitivity.collectAsStateWithLifecycle()

    fun requestMic() {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        turnOnLauncher.launch(perms.toTypedArray())
    }

    // Play's prominent disclosure before the microphone permission and before listening starts.
    fun turnOn() {
        if (app.prefs.micDisclosureAccepted) requestMic() else askMic = 1
    }

    if (askMic != 0) {
        DisclosureDialog(
            kind = Disclosure.Microphone,
            onAgree = {
                val forAlways = askMic == 1
                askMic = 0
                app.prefs.micDisclosureAccepted = true
                if (forAlways) requestMic() else permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            },
            onDecline = { askMic = 0 },
        )
    }

    fun toggle(on: Boolean) {
        if (on) {
            turnOn()
        } else {
            app.prefs.setVoiceAlways(false)
            VoiceService.stop(context)
        }
    }

    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                Box(Modifier.weight(1f)) {
                    CardTitle(stringResource(R.string.voice_title), stringResource(R.string.voice_examples))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouch)
                    .clickable { toggle(!always) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Box(Modifier.weight(1f)) {
                    CardTitle(stringResource(R.string.voice_always_title), stringResource(R.string.voice_always_hint))
                }
                ThemedSwitch(always) { toggle(it) }
            }
            if (always) {
                // Wake-word sensitivity: 1 strict … 5 eager (WakeSensitivity).
                Column {
                    Text(
                        stringResource(R.string.voice_sensitivity_title) + " · " + stringResource(sensitivityLabel(sensitivity)),
                        color = theme.text, fontSize = dims.t(2.6f, min = 15f),
                    )
                    Slider(
                        value = sensitivity.toFloat(),
                        onValueChange = { app.prefs.setWakeSensitivity(it.roundToInt()) },
                        valueRange = 1f..5f,
                        steps = 3,
                        colors = SliderDefaults.colors(thumbColor = theme.accent, activeTrackColor = theme.accent),
                    )
                    Text(stringResource(R.string.voice_sensitivity_hint), color = theme.muted, fontSize = dims.t(2.2f, min = 13f))
                }
            }
            Text(stringResource(R.string.voice_key_hint), color = theme.muted, fontSize = dims.t(2.2f, min = 13f))
            // Model download state, with a retry when it failed.
            val modelText = when (val m = model) {
                WakeModel.State.Missing -> stringResource(R.string.voice_model_missing)
                is WakeModel.State.Downloading -> stringResource(R.string.voice_model_downloading, m.percent)
                WakeModel.State.Unpacking -> stringResource(R.string.voice_model_unpacking)
                WakeModel.State.Ready -> stringResource(R.string.voice_model_ready)
                is WakeModel.State.Failed -> stringResource(R.string.voice_model_failed)
            }
            StatusLine(
                text = modelText,
                ok = model == WakeModel.State.Ready,
                action = if (model is WakeModel.State.Failed && always) stringResource(R.string.action_retry) else null,
                onAction = { app.wakeModel.ensure() },
            )
            StatusLine(
                text = stringResource(R.string.voice_mic_title) + " · " +
                    stringResource(if (hasMic) R.string.voice_mic_ok else R.string.voice_mic_needed),
                ok = hasMic,
                action = if (hasMic) null else stringResource(R.string.action_allow),
                onAction = {
                    if (app.prefs.micDisclosureAccepted) permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) else askMic = 2
                },
                secondary = if (hasMic) null else stringResource(R.string.action_app_settings),
                onSecondary = { Launchers.openAppDetails(context) },
            )
            StatusLine(
                text = stringResource(R.string.voice_media_title) + " · " +
                    stringResource(if (hasMusic) R.string.voice_media_ok else R.string.voice_media_needed),
                ok = hasMusic,
                action = if (hasMusic) null else stringResource(R.string.action_allow),
                onAction = { permLauncher.launch(arrayOf(VoiceActions.audioPermission())) },
            )
        }
    }
}

private fun sensitivityLabel(level: Int): Int = when (level) {
    1, 2 -> R.string.voice_sensitivity_strict
    3 -> R.string.voice_sensitivity_normal
    else -> R.string.voice_sensitivity_eager
}

@Composable
private fun StatusLine(
    text: String,
    ok: Boolean,
    action: String?,
    onAction: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
    ) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = stringResource(if (ok) R.string.status_ok else R.string.status_attention),
            tint = if (ok) theme.ok else theme.warning,
            modifier = Modifier.size(24.dp),
        )
        Text(text, color = theme.text, fontSize = dims.t(2.5f, min = 15f), modifier = Modifier.weight(1f))
        if (secondary != null) BigButton(text = secondary, onClick = onSecondary, primary = false)
        if (action != null) BigButton(text = action, onClick = onAction, primary = !ok)
    }
}
