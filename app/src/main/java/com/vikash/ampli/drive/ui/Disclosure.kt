package com.vikash.ampli.drive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

/** Which Google Play "prominent disclosure" to show. */
enum class Disclosure { BackgroundLocation, Microphone }

/**
 * Google Play's prominent disclosure, shown in the app before the data access starts (and
 * before the Android permission prompt): what is collected, what for, that it runs while the
 * app is closed or not in use, and that it stays on this unit. It cannot be dismissed by
 * tapping outside; the driver picks "Agree" or "No thanks".
 */
@Composable
fun DisclosureDialog(kind: Disclosure, onAgree: () -> Unit, onDecline: () -> Unit) {
    val (bg, fg) = dialogColors()
    val theme = LocalDriveTheme.current
    val (icon: ImageVector, title, lines) = when (kind) {
        Disclosure.BackgroundLocation -> Triple(
            Icons.Filled.MyLocation,
            stringResource(R.string.disclosure_location_title),
            listOf(
                stringResource(R.string.disclosure_location_body),
                stringResource(R.string.disclosure_location_uses),
                stringResource(R.string.disclosure_location_stays),
            ),
        )
        Disclosure.Microphone -> Triple(
            Icons.Filled.Mic,
            stringResource(R.string.disclosure_mic_title),
            listOf(
                stringResource(R.string.disclosure_mic_body),
                stringResource(R.string.disclosure_mic_uses),
                stringResource(R.string.disclosure_mic_stays),
            ),
        )
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = bg, titleContentColor = fg, textContentColor = fg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (line in lines) Text(line)
                Text(stringResource(R.string.disclosure_change_later), color = theme.muted)
            }
        },
        confirmButton = { TextButton(onClick = onAgree) { Text(stringResource(R.string.disclosure_agree)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.disclosure_decline)) } },
    )
}
