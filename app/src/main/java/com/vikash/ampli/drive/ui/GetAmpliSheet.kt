package com.vikash.ampli.drive.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.system.AmpliMusic
import com.vikash.ampli.drive.system.AppChooser
import com.vikash.ampli.drive.system.AppRole
import com.vikash.ampli.drive.system.Mp3Install
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.launch
import java.io.File

/** Where the test-build download is. */
sealed interface Mp3Download {
    data object Idle : Mp3Download
    data class Running(val percent: Int) : Mp3Download
    data class Ready(val file: File?, val needsAllow: Boolean = false) : Mp3Download
    data object Failed : Mp3Download
}

/** Shows the sheet while [AmpliMusic.askInstall] is set (DriveRoot). */
@Composable
fun GetAmpliHost() {
    val ask by AmpliMusic.askInstall.collectAsStateWithLifecycle()
    if (ask) GetAmpliSheet(onDismiss = AmpliMusic::hideInstallSheet)
}

/**
 * "Get Ampli MP3 Player": the Music button when the player is not on the unit. Play Store, a
 * "Download and install" from its GitHub test releases in test builds only, or another app.
 */
@Composable
fun GetAmpliSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (bg, fg) = dialogColors()
    var download by remember { mutableStateOf<Mp3Download>(Mp3Download.Idle) }

    fun install(file: File) {
        download = when (Mp3Install.install(context, file)) {
            Mp3Install.InstallStep.InstallerOpened -> Mp3Download.Ready(file)
            Mp3Install.InstallStep.AllowFirst -> Mp3Download.Ready(file, needsAllow = true)
            Mp3Install.InstallStep.Failed -> Mp3Download.Failed
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bg, titleContentColor = fg, textContentColor = fg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(
                    painterResource(R.drawable.ampli_mp3_logo),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                )
                Text(stringResource(R.string.getmp3_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            GetAmpliContent(
                showDownload = Mp3Install.enabled,
                download = download,
                onPlay = {
                    if (Mp3Install.openPlay(context)) {
                        onDismiss()
                    } else {
                        Toast.makeText(context, context.getString(R.string.error_cannot_open_app), Toast.LENGTH_SHORT).show()
                    }
                },
                onDownload = {
                    val d = download
                    if (d is Mp3Download.Ready && d.file != null) {
                        install(d.file)
                    } else {
                        download = Mp3Download.Running(0)
                        scope.launch {
                            val apk = Mp3Install.latest()
                            val file = apk?.let { Mp3Install.download(context, it) { pct -> download = Mp3Download.Running(pct) } }
                            if (file == null) download = Mp3Download.Failed else install(file)
                        }
                    }
                },
                onOther = {
                    onDismiss()
                    AppChooser.ask(AppRole.Music)
                },
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** The sheet's body; also used by the screen previews. */
@Composable
internal fun GetAmpliContent(
    showDownload: Boolean,
    download: Mp3Download,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onOther: () -> Unit,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Column(
        Modifier.widthIn(min = 420.dp).heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
    ) {
        Text(stringResource(R.string.getmp3_text), color = theme.muted, fontSize = dims.t(2.5f, min = 15f))
        BigButton(stringResource(R.string.getmp3_play), onPlay, Modifier.fillMaxWidth(), icon = Icons.Filled.Shop)
        if (showDownload) {
            when (download) {
                is Mp3Download.Running -> {
                    Text(stringResource(R.string.getmp3_downloading, download.percent), color = theme.text, fontSize = dims.t(2.4f, min = 15f))
                    LinearProgressIndicator(
                        progress = { download.percent / 100f },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 8.dp),
                        color = theme.accent,
                        trackColor = theme.surfaceHigh,
                    )
                }
                is Mp3Download.Ready -> {
                    if (download.needsAllow) Text(stringResource(R.string.getmp3_allow), color = theme.warning, fontSize = dims.t(2.4f, min = 15f))
                    BigButton(stringResource(R.string.getmp3_install), onDownload, Modifier.fillMaxWidth(), icon = Icons.Filled.Download, primary = false)
                }
                Mp3Download.Failed -> {
                    Text(stringResource(R.string.getmp3_failed), color = theme.warning, fontSize = dims.t(2.4f, min = 15f))
                    BigButton(stringResource(R.string.getmp3_download), onDownload, Modifier.fillMaxWidth(), icon = Icons.Filled.Download, primary = false)
                }
                Mp3Download.Idle -> {
                    BigButton(stringResource(R.string.getmp3_download), onDownload, Modifier.fillMaxWidth(), icon = Icons.Filled.Download, primary = false)
                }
            }
            Text(stringResource(R.string.getmp3_test_note), color = theme.muted, fontSize = dims.t(2.1f, min = 13f))
        }
        BigButton(stringResource(R.string.getmp3_other), onOther, Modifier.fillMaxWidth(), icon = Icons.Filled.LibraryMusic, primary = false)
    }
}
