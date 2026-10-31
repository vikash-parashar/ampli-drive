package com.vikash.ampli.drive.ui

import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.dashcam.Clip
import com.vikash.ampli.drive.dashcam.Dashcam
import com.vikash.ampli.drive.dashcam.LoopStorage
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.delay
import java.io.File

/**
 * Car › Dashcam (Pro). REC timer, "saved" banner, the clips (saved ones first), the storage cap
 * and the switch, which asks for the camera permission. Units without a camera see only
 * "No camera found".
 */
@Composable
fun DashcamScreen(app: DriveApp, onBack: () -> Unit, onUnlockPro: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val pro by app.prefs.pro.collectAsStateWithLifecycle()
    val cam = app.dashcam
    val enabled by cam.enabled.collectAsStateWithLifecycle()
    val state by cam.state.collectAsStateWithLifecycle()
    val clips by cam.clips.collectAsStateWithLifecycle()
    val saved by cam.saved.collectAsStateWithLifecycle()
    val capMb by cam.capMb.collectAsStateWithLifecycle()
    // A clip plays in the unit's video player: parked only.
    val moving = isMoving(app)
    LaunchedEffect(Unit) { cam.refreshClips() }
    LaunchedEffect(saved) {
        if (saved != null) {
            delay(6_000L)
            cam.clearSaved()
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cam.setEnabled(true)
    }

    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        ScreenHeader(stringResource(R.string.dashcam_title), stringResource(R.string.dashcam_hint), onBack)
        when {
            !pro.isPro -> ProLocked(
                stringResource(R.string.dashcam_title),
                listOf(
                    stringResource(R.string.dashcam_point_loop),
                    stringResource(R.string.dashcam_point_jolt),
                    stringResource(R.string.dashcam_point_local),
                ),
                onUnlockPro,
                Modifier.fillMaxWidth().weight(1f),
            )
            !cam.hasCamera -> Pane(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    Modifier.fillMaxSize().padding(dims.d(4f)),
                    verticalArrangement = Arrangement.spacedBy(dims.d(1.4f), Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.VideocamOff, contentDescription = null, tint = theme.muted, modifier = Modifier.size(56.dp))
                    Text(stringResource(R.string.dashcam_no_camera), fontSize = dims.t(3.4f, min = 20f), fontWeight = FontWeight.Bold, color = theme.text)
                    Text(stringResource(R.string.dashcam_no_camera_hint), fontSize = dims.t(2.4f, min = 15f), color = theme.muted)
                }
            }
            else -> Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
                ) {
                    RecPanel(state, capMb)
                    if (saved != null) {
                        Text(
                            stringResource(R.string.dashcam_saved_banner),
                            color = theme.onAccent, fontSize = dims.t(2.6f, min = 16f), fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(theme.ok).padding(dims.d(1.6f)),
                        )
                    }
                    state.error?.let { e ->
                        Text(stringResource(errorText(e)), color = theme.warning, fontSize = dims.t(2.4f, min = 15f))
                    }
                    SettingCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable {
                                if (enabled) cam.setEnabled(false) else if (cam.hasPermission()) cam.setEnabled(true) else launcher.launch(Manifest.permission.CAMERA)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
                        ) {
                            Box(Modifier.weight(1f)) { CardTitle(stringResource(R.string.dashcam_switch), stringResource(R.string.dashcam_switch_hint)) }
                            ThemedSwitch(enabled) { on ->
                                if (!on) cam.setEnabled(false) else if (cam.hasPermission()) cam.setEnabled(true) else launcher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    }
                    if (state.recording) {
                        BigButton(stringResource(R.string.dashcam_save_now), { cam.saveNow() }, Modifier.fillMaxWidth(), icon = Icons.Filled.Save)
                    }
                    Text(theme.label(stringResource(R.string.dashcam_cap)), style = theme.labelStyle(), fontSize = dims.t(2.1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1f))) {
                        for (mb in LoopStorage.CAP_CHOICES_MB) Chip("${mb / 1024} GB", capMb == mb, { cam.setCapMb(mb) })
                    }
                    Text(stringResource(R.string.dashcam_rules), fontSize = dims.t(2.2f), color = theme.muted)
                }
                Pane(Modifier.weight(1f).fillMaxHeight()) {
                    if (clips.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(dims.d(3f)), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.dashcam_no_clips), fontSize = dims.t(2.6f, min = 16f), color = theme.muted)
                        }
                    } else {
                        // Saved clips first, then the loop, newest first.
                        val sorted = clips.sortedWith(compareByDescending<Clip> { it.locked }.thenByDescending { it.startMs })
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = dims.d(2f)), verticalArrangement = Arrangement.spacedBy(dims.d(0.4f))) {
                            items(sorted, key = { it.name }) { c ->
                                ClipRow(
                                    clip = c,
                                    active = state.recording && c.startMs == state.segmentStartMs?.let { it / 1000 * 1000 },
                                    onOpen = {
                                        if (moving) {
                                            Toast.makeText(context, context.getString(R.string.park_to_set), Toast.LENGTH_SHORT).show()
                                        } else {
                                            openClip(context, File(cam.folder, c.name))
                                        }
                                    },
                                    onDelete = { cam.delete(c) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecPanel(state: Dashcam.State, capMb: Long) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val now by produceState(System.currentTimeMillis(), state.sinceMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    Pane(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(dims.d(2f)), verticalArrangement = Arrangement.spacedBy(dims.d(0.6f))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(if (state.recording) theme.warning else theme.muted))
                val since = state.sinceMs
                Text(
                    if (state.recording && since != null) "REC " + hms((now - since).coerceAtLeast(0L)) else stringResource(R.string.dashcam_stopped),
                    style = theme.numberStyle(glowColor = null), fontSize = dims.t(4.4f, min = 22f),
                )
            }
            val hours = LoopStorage.hoursFor(capMb * 1024L * 1024L, Dashcam.BIT_RATE)
            Text(
                stringResource(R.string.dashcam_loop_line, "${capMb / 1024} GB", "%.0f".format(hours.coerceAtLeast(1.0))),
                fontSize = dims.t(2.4f, min = 15f), color = theme.muted,
            )
        }
    }
}

@Composable
private fun ClipRow(clip: Clip, active: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val seconds = (clip.bytes * 8L / Dashcam.BIT_RATE).coerceAtLeast(1L)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(enabled = !active, onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
    ) {
        Icon(
            if (clip.locked) Icons.Filled.Lock else Icons.Filled.Videocam, contentDescription = null,
            tint = if (clip.locked) theme.warning else theme.accentSoft, modifier = Modifier.size(26.dp),
        )
        Text(
            clockLabel(context, clip.startMs) + if (clip.locked) " · " + stringResource(R.string.dashcam_saved_tag) else "",
            fontSize = dims.t(2.6f, min = 15f), color = theme.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(
            if (active) stringResource(R.string.dashcam_recording) else "%d:%02d".format(seconds / 60, seconds % 60),
            style = theme.numberStyle(glowColor = null), fontSize = dims.t(2.6f, min = 15f),
        )
        if (!active) {
            IconButton(onClick = onDelete, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = theme.muted)
            }
        }
    }
}

private fun hms(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
}

private fun errorText(e: Dashcam.Error): Int = when (e) {
    Dashcam.Error.NoCamera -> R.string.dashcam_no_camera
    Dashcam.Error.CameraBusy -> R.string.dashcam_error_busy
    Dashcam.Error.NoPermission -> R.string.dashcam_error_permission
    Dashcam.Error.NoStorage -> R.string.dashcam_error_storage
}

/** The unit's own video player (the caller checks the car is parked). */
private fun openClip(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!startSafely(context, view)) Toast.makeText(context, context.getString(R.string.error_cannot_open_app), Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.error_cannot_open_app), Toast.LENGTH_SHORT).show()
    }
}
