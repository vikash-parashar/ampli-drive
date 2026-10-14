package com.vikash.ampli.drive.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.prefs.DrivePrefs
import com.vikash.ampli.drive.ui.theme.CardStyle
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

/**
 * Status-row chip: "Camera 300 m · 60" while a camera is ahead (amber, red when over the limit),
 * otherwise a quiet "No camera ahead". Tap opens a small menu, long-press reports a camera here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraChip(app: DriveApp, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val current by app.cameraAlerts.current.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val alert = current
    val shape = if (theme.radius <= 0f || theme.cardStyle == CardStyle.Skewed) RectangleShape else CircleShape
    val bg = when {
        alert == null -> theme.surface
        alert.overLimit -> theme.danger
        else -> theme.warning
    }
    val fg = when {
        alert == null -> theme.text
        alert.overLimit -> theme.onDanger
        else -> Color(0xFF111111)
    }
    val text = if (alert == null) {
        if (dims.compact) null else stringResource(R.string.camera_chip_idle)
    } else {
        buildString {
            append(stringResource(R.string.camera_chip_label))
            append(' ')
            append(stringResource(R.string.camera_distance, alert.distanceMeters / 10 * 10))
            alert.limitKmh?.let { append(" · ").append(it) }
        }
    }
    val reportLabel = stringResource(R.string.camera_report_here)

    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .combinedClickable(
                onClickLabel = reportLabel,
                onClick = { menu = true },
                onLongClickLabel = reportLabel,
                onLongClick = { reportHere(app, context) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(bg)
                .border(1.dp, if (alert == null) theme.border else bg, shape)
                .padding(horizontal = dims.d(1.8f), vertical = dims.d(0.9f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(1f)),
        ) {
            Icon(
                Icons.Filled.PhotoCamera,
                contentDescription = if (text == null) stringResource(R.string.camera_chip_idle) else null,
                tint = if (alert == null) theme.muted else fg,
                modifier = Modifier.size(dims.d(2.8f)),
            )
            if (text != null) {
                Text(
                    text,
                    fontSize = dims.t(2.4f, min = 13f),
                    color = if (alert == null) theme.muted else fg,
                    fontWeight = if (alert == null) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            modifier = Modifier.background(if (theme.surface.alpha < 1f) theme.background else theme.surface),
        ) {
            DropdownMenuItem(
                text = { Text(reportLabel, color = theme.text, fontSize = dims.t(2.6f, min = 16f)) },
                leadingIcon = { Icon(Icons.Filled.AddLocationAlt, contentDescription = null, tint = theme.accentSoft) },
                onClick = {
                    menu = false
                    reportHere(app, context)
                },
                modifier = Modifier.heightIn(min = MinTouch),
            )
        }
    }
}

/** Saves a camera at the current GPS fix (with its heading) to the driver's own list. */
private fun reportHere(app: DriveApp, context: Context) {
    val gps = app.speedTracker.state.value
    val here = gps.location
    if (here == null || gps.speedKmh == null) {
        Toast.makeText(context, context.getString(R.string.camera_report_no_fix), Toast.LENGTH_LONG).show()
        return
    }
    app.cameras.report(here, gps.speedKmh)
    Toast.makeText(context, context.getString(R.string.camera_report_saved), Toast.LENGTH_LONG).show()
}

/** Settings › Speed cameras. Voice alerts are Pro; everyone gets the on-screen chip. */
@Composable
internal fun CameraSettingsCard(app: DriveApp, isPro: Boolean, onLocked: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val on by app.prefs.cameraAlerts.collectAsStateWithLifecycle()
    val voice by app.prefs.cameraVoice.collectAsStateWithLifecycle()
    val distance by app.prefs.cameraDistance.collectAsStateWithLifecycle()
    val overOnly by app.prefs.cameraOverOnly.collectAsStateWithLifecycle()
    val stats by app.cameras.stats.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { app.cameras.ensureLoaded() }

    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                Box(Modifier.weight(1f)) {
                    CardTitle(stringResource(R.string.cameras_title), stringResource(R.string.cameras_hint))
                }
            }
            SwitchLine(
                title = stringResource(R.string.cameras_alerts_title),
                hint = null,
                checked = on,
                onChange = app.prefs::setCameraAlerts,
            )
            SwitchLine(
                title = stringResource(R.string.cameras_voice_title),
                hint = stringResource(if (isPro) R.string.cameras_voice_hint else R.string.cameras_voice_pro),
                checked = voice && isPro,
                locked = !isPro,
                onChange = { if (isPro) app.prefs.setCameraVoice(it) else onLocked() },
            )
            SwitchLine(
                title = stringResource(R.string.cameras_over_only_title),
                hint = stringResource(R.string.cameras_over_only_hint),
                checked = overOnly,
                onChange = app.prefs::setCameraOverOnly,
            )
            Text(stringResource(R.string.cameras_distance_title), color = theme.text, fontSize = dims.t(2.6f, min = 16f))
            Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1f))) {
                for (m in DrivePrefs.CAMERA_DISTANCES) {
                    Chip(
                        text = stringResource(R.string.camera_distance, m),
                        selected = distance == m,
                        onClick = { app.prefs.setCameraDistance(m) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val small = dims.t(2.3f)
            Text(stringResource(R.string.cameras_stats, stats.loaded, stats.reported), color = theme.text, fontSize = small)
            Text(
                stats.dataDate?.let { stringResource(R.string.cameras_data_date, it) } ?: stringResource(R.string.cameras_data_unknown),
                color = theme.muted,
                fontSize = small,
            )
            Text(stringResource(R.string.cameras_report_hint), color = theme.muted, fontSize = small)
            Text(stringResource(R.string.cameras_disclaimer), color = theme.muted, fontSize = small)
            Text(stringResource(R.string.cameras_attribution), color = theme.muted, fontSize = small)
        }
    }
}

@Composable
private fun SwitchLine(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit, locked: Boolean = false) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Box(Modifier.weight(1f)) { CardTitle(title, hint) }
        if (locked) Icon(Icons.Filled.Lock, contentDescription = null, tint = theme.muted, modifier = Modifier.size(24.dp))
        ThemedSwitch(checked, onChange)
    }
}
