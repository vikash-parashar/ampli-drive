package com.vikash.ampli.drive.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

/**
 * Crash detection and Android's "Display over other apps": without it, Android 10+ blocks the
 * SOS message from opening by itself after a crash while another app is in front (CarMonitor
 * then posts a full-screen alert instead). Shown in Settings › Car while crash detection is on
 * and the permission is missing.
 */
internal fun openOverlaySettings(context: Context): Boolean =
    startSafely(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName)))

/** The short explanation shown right after crash detection is switched on. */
@Composable
fun CrashOverlayCard(onAllow: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = theme.cardShape(dims, scale = 0.7f)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.surfaceHigh)
            .border(1.dp, theme.warning.copy(alpha = 0.6f), shape)
            .padding(horizontal = dims.d(2.4f), vertical = dims.d(1.8f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            Icon(Icons.Filled.Layers, contentDescription = null, tint = theme.warning, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dims.d(0.6f))) {
                Text(
                    stringResource(R.string.crash_overlay_title),
                    fontSize = dims.t(3f, min = 18f),
                    fontWeight = FontWeight.SemiBold,
                    color = theme.text,
                )
                Text(stringResource(R.string.crash_overlay_body), fontSize = dims.t(2.4f, min = 16f), color = theme.muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
            BigButton(stringResource(R.string.crash_overlay_allow), onAllow, Modifier.weight(1.4f), icon = Icons.AutoMirrored.Filled.OpenInNew)
            BigButton(stringResource(R.string.crash_overlay_later), onLater, Modifier.weight(1f), primary = false)
        }
    }
}

/** Settings › Car status row, kept until "Display over other apps" is allowed. */
@Composable
fun CrashOverlayStatusRow(onAllow: () -> Unit, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Row(
        modifier.fillMaxWidth().heightIn(min = MinTouch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Icon(Icons.Filled.Layers, contentDescription = null, tint = theme.warning, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.crash_overlay_status_title),
                fontSize = dims.t(2.8f, min = 17f),
                fontWeight = FontWeight.SemiBold,
                color = theme.text,
            )
            Text(stringResource(R.string.crash_overlay_status_off), fontSize = dims.t(2.4f, min = 16f), color = theme.warning)
        }
        BigButton(stringResource(R.string.crash_overlay_allow), onAllow, primary = false)
    }
}
