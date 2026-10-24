package com.vikash.ampli.drive.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.system.AppEntry
import com.vikash.ampli.drive.system.AppMatch
import com.vikash.ampli.drive.system.AppResolver
import com.vikash.ampli.drive.system.AppRole
import com.vikash.ampli.drive.system.InstalledApps
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Which app is your music / phone app?" Every launchable app, the likely ones on top.
 * [onPick] with null means "Automatic" (forget the choice).
 */
@Composable
fun AppChooserDialog(role: AppRole, onPick: (AppEntry?) -> Unit, onDismiss: () -> Unit, automaticLabel: String? = null) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val apps by produceState<List<Pair<AppEntry, Int>>?>(null, role) {
        value = withContext(Dispatchers.IO) { AppResolver.candidates(context, role) }
    }
    val shape = RoundedCornerShape(dims.d(2.4f))
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(if (theme.surface.alpha < 1f) theme.background else theme.surface, shape)
                .border(1.dp, theme.border, shape)
                .padding(dims.d(2.4f)),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.2f)),
        ) {
            Text(
                stringResource(
                    when (role) {
                        AppRole.Music -> R.string.chooser_music_title
                        AppRole.Phone -> R.string.chooser_phone_title
                        AppRole.Mirror -> R.string.chooser_mirror_title
                        AppRole.Nav -> R.string.chooser_nav_title
                    },
                ),
                color = theme.text,
                fontSize = dims.t(3.4f, min = 20f),
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.chooser_hint), color = theme.muted, fontSize = dims.t(2.3f, min = 13f))
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = theme.accent)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        ChooserRow(label = automaticLabel ?: stringResource(R.string.chooser_automatic), icon = null, suggested = false) { onPick(null) }
                    }
                    items(list, key = { it.first.packageName + "/" + it.first.activityName }) { (entry, score) ->
                        val iconPx = with(LocalDensity.current) { 40.dp.roundToPx() }
                        val icon by produceState<ImageBitmap?>(null, entry) { value = InstalledApps.icon(context, entry, iconPx) }
                        ChooserRow(label = entry.label, icon = icon, suggested = score >= AppMatch.GOOD_ENOUGH) { onPick(entry) }
                    }
                }
            }
            BigButton(text = stringResource(R.string.action_close), onClick = onDismiss, primary = false)
        }
    }
}

@Composable
private fun ChooserRow(label: String, icon: ImageBitmap?, suggested: Boolean, onClick: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = RoundedCornerShape(dims.d(1.6f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .background(theme.surfaceHigh, shape)
            .border(if (suggested) 2.dp else 1.dp, if (suggested) theme.accent else theme.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = dims.d(2f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Box(Modifier.size(40.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
        }
        Text(
            label,
            color = theme.text,
            fontSize = dims.t(2.6f, min = 15f),
            fontWeight = if (suggested) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (suggested) Text(stringResource(R.string.chooser_suggested), color = theme.accentSoft, fontSize = dims.t(2f, min = 12f))
    }
}
