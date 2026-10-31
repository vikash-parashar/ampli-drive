package com.vikash.ampli.drive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.voice.VoiceCommandCatalog

/**
 * Settings › Voice › What can I say? Every "Hey Ampli" command in groups, two columns on a
 * landscape head unit. Read while parked; "Hey Ampli, what can I say?" opens it too.
 */
@Composable
fun VoiceCommandsScreen(onBack: () -> Unit) {
    val dims = LocalDims.current
    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
        ScreenHeader(title = stringResource(R.string.voice_cmds_title), hint = stringResource(R.string.voice_cmds_hint), onBack = onBack)
        val groups = VoiceCommandCatalog.GROUPS
        // Balance the two columns by the number of lines, not of groups.
        val left = ArrayList<VoiceCommandCatalog.Group>()
        val right = ArrayList<VoiceCommandCatalog.Group>()
        var l = 0
        var r = 0
        for (g in groups) {
            val weight = g.items.size + 1
            if (l <= r) {
                left += g
                l += weight
            } else {
                right += g
                r += weight
            }
        }
        Row(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dims.d(1.4f))) { left.forEach { CommandGroupCard(it) } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dims.d(1.4f))) { right.forEach { CommandGroupCard(it) } }
        }
    }
}

@Composable
private fun CommandGroupCard(group: VoiceCommandCatalog.Group) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val after = stringResource(R.string.voice_cmds_after_volume)
    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(0.9f))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(24.dp))
                Text(stringResource(group.title), color = theme.text, fontSize = dims.t(3f, min = 18f), fontWeight = FontWeight.SemiBold)
            }
            for (item in group.items) {
                val line = buildAnnotatedString {
                    item.examples.forEachIndexed { i, e ->
                        if (i > 0) withStyle(SpanStyle(color = theme.muted)) { append("  ·  ") }
                        withStyle(SpanStyle(color = if (i == 0) theme.text else theme.accentSoft, fontWeight = if (i == 0) FontWeight.Medium else FontWeight.Normal)) {
                            append("“$e”")
                        }
                    }
                    if (item.followUp) {
                        withStyle(SpanStyle(color = theme.muted)) { append("  $after") }
                    }
                }
                Text(line, fontSize = dims.t(2.5f, min = 15f))
            }
        }
    }
}
