package com.vikash.ampli.drive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.media.SpeedVolumeLevel
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

/** Settings › Volume by speed: Off / Low / Medium / High (off by default). */
@Composable
internal fun SpeedVolumeCard(app: DriveApp) {
    val level by app.prefs.speedVolume.collectAsStateWithLifecycle()
    SpeedVolumeCardContent(level, app.prefs::setSpeedVolume)
}

@Composable
internal fun SpeedVolumeCardContent(level: SpeedVolumeLevel, onChange: (SpeedVolumeLevel) -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                Box(Modifier.weight(1f)) {
                    CardTitle(stringResource(R.string.speed_volume_title), stringResource(R.string.speed_volume_hint))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dims.d(1f))) {
                for (l in SpeedVolumeLevel.entries) {
                    val label = when (l) {
                        SpeedVolumeLevel.Off -> R.string.car_off
                        SpeedVolumeLevel.Low -> R.string.speed_volume_low
                        SpeedVolumeLevel.Medium -> R.string.speed_volume_medium
                        SpeedVolumeLevel.High -> R.string.speed_volume_high
                    }
                    Chip(stringResource(label), l == level, { onChange(l) }, Modifier.weight(1f))
                }
            }
        }
    }
}
