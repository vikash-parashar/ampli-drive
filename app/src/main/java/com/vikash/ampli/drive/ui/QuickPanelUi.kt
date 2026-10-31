package com.vikash.ampli.drive.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.MainActivity
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.bluetooth.BtDevice
import com.vikash.ampli.drive.location.SunTimes
import com.vikash.ampli.drive.system.AmpliMusic
import com.vikash.ampli.drive.system.Shell
import com.vikash.ampli.drive.ui.theme.AmpliDriveTheme
import com.vikash.ampli.drive.ui.theme.DriveThemes
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalTime

/** The tiles the over-other-apps panel shows, in one row. */
internal val OVERLAY_CONTROLS = listOf(
    Shell.Control.Bluetooth,
    Shell.Control.Wifi,
    Shell.Control.Hotspot,
    Shell.Control.Brightness,
    Shell.Control.Volume,
    Shell.Control.Equalizer,
)

/**
 * The quick panel in its own overlay window (QuickStrip), over Google Maps or any other app:
 * Car Hub's theme and layout units, the system tiles, the connected phone with phone music and
 * media keys, Ampli Music and a way back Home. Every action closes the panel.
 */
@Composable
fun QuickPanelOverlay(app: DriveApp, open: Boolean, onClose: () -> Unit) {
    val themeChoice by app.prefs.theme.collectAsStateWithLifecycle()
    val pro by app.prefs.pro.collectAsStateWithLifecycle()
    val theme = remember(themeChoice, pro) {
        val loc = app.speedTracker.state.value.location
        val night = loc?.let { SunTimes.isNight(System.currentTimeMillis(), it.latitude, it.longitude) }
            ?: SunTimes.isNightByClock(LocalTime.now())
        // No blur in an overlay window: glass themes use their stronger tint.
        DriveThemes.resolve(themeChoice, night, pro.isPro)
    }
    AmpliDriveTheme(theme) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val unit = maxHeight / 100f
            val dims = Dims(u = unit, uSp = with(LocalDensity.current) { unit.toSp().value }, compact = maxWidth / maxHeight < 1.72f)
            CompositionLocalProvider(LocalDims provides dims) {
                QuickPanelHost(app, open, onClose)
            }
        }
    }
}

@Composable
private fun QuickPanelHost(app: DriveApp, open: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val bt by app.bluetooth.state.collectAsStateWithLifecycle()
    val devices by app.phoneBt.devices.collectAsStateWithLifecycle()
    LaunchedEffect(open) { if (open) app.phoneBt.refresh() }
    // Read off the main thread, and only while the panel is open.
    val status by produceState<Shell.Status?>(null, open) {
        while (open) {
            value = withContext(Dispatchers.IO) { Shell.status(context) }
            delay(2_000L)
        }
    }
    val playing by produceState(false, open) {
        val am = context.getSystemService(AudioManager::class.java)
        while (open) {
            value = am?.isMusicActive == true
            delay(1_000L)
        }
    }
    fun then(action: () -> Unit) {
        action()
        onClose()
    }
    QuickControlsPanel(
        visible = open,
        tiles = quickTiles(status ?: Shell.Status(null, false, null, null, null), btSupported = bt.supported, btOn = bt.enabled, btDevice = bt.connectedName)
            .filter { it.control in OVERLAY_CONTROLS },
        onTile = { then { Shell.open(context, it) } },
        onClose = onClose,
        perRow = OVERLAY_CONTROLS.size,
        header = {
            QuickPanelExtras(
                phone = app.phoneBt.connectedPhone(devices),
                playing = playing,
                onPlayPhone = { then { app.phoneBt.playPhoneMusic(context) } },
                onBtSettings = { then { app.phoneBt.openBluetoothSettings(context) } },
                // Media keys keep the panel open: the driver may skip a few songs.
                onKey = { app.phoneBt.key(it) },
                onAmpliMusic = { then { openAmpliMusic(context) } },
                onHome = { then { openHome(context) } },
            )
        },
    )
}

/** Ampli MP3 Player with autoplay; without it, Car Hub's "Get Ampli MP3 Player" sheet. */
private fun openAmpliMusic(context: Context) {
    if (AmpliMusic.installed(context) && AmpliMusic.open(context, autoplay = true)) return
    AmpliMusic.showInstallSheet()
    openHome(context)
}

/** Back to Car Hub's Home, like the Home key (MainActivity treats CATEGORY_HOME as "go Home"). */
private fun openHome(context: Context) {
    try {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
    } catch (_: Exception) {
    }
}

/**
 * The panel's phone section: connected phone ("Calls ✓  Music ✓"), "Play phone music" (or
 * Bluetooth settings without a phone), previous / play-pause / next, then Ampli Music and Home.
 */
@Composable
internal fun QuickPanelExtras(
    phone: BtDevice?,
    playing: Boolean,
    onPlayPhone: () -> Unit,
    onBtSettings: () -> Unit,
    onKey: (Int) -> Unit,
    onAmpliMusic: () -> Unit,
    onHome: () -> Unit,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = RoundedCornerShape(20.dp)
    Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
        Row(
            Modifier
                .fillMaxWidth()
                .themedSurface(theme, shape, theme.surfaceHigh, if (phone != null) theme.ok.copy(alpha = 0.6f) else theme.border, sunk = true)
                .padding(horizontal = dims.d(2f), vertical = dims.d(1.2f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
        ) {
            Icon(
                if (phone != null) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = if (phone != null) theme.ok else theme.muted,
                modifier = Modifier.size(32.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    phone?.name ?: stringResource(R.string.plink_not_connected),
                    color = theme.text,
                    fontSize = dims.t(2.7f, min = 16f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val parts = listOfNotNull(
                    phone?.calls?.let { stringResource(R.string.plink_calls) + if (it) " ✓" else " ✗" },
                    phone?.music?.let { stringResource(R.string.plink_music) + if (it) " ✓" else " ✗" },
                )
                if (parts.isNotEmpty()) {
                    Text(parts.joinToString("   "), color = theme.muted, fontSize = dims.t(2.2f, min = 13f), maxLines = 1)
                }
            }
            if (phone != null) {
                BigButton(stringResource(R.string.plink_play), onPlayPhone, icon = Icons.Filled.PlayArrow)
            } else {
                BigButton(stringResource(R.string.plink_bt_settings), onBtSettings, icon = Icons.Filled.Settings, primary = false)
            }
            KeyButton(Icons.Filled.SkipPrevious, stringResource(R.string.music_previous)) { onKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
            KeyButton(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                stringResource(if (playing) R.string.music_pause else R.string.music_play),
                active = playing,
            ) { onKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
            KeyButton(Icons.Filled.SkipNext, stringResource(R.string.music_next)) { onKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
            BigButton(stringResource(R.string.quick_ampli_music), onAmpliMusic, Modifier.weight(1f), icon = Icons.Filled.LibraryMusic)
            BigButton(stringResource(R.string.quick_home), onHome, Modifier.weight(1f), icon = Icons.Filled.Home, primary = false)
        }
    }
}

@Composable
private fun KeyButton(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val theme = LocalDriveTheme.current
    Box(
        Modifier
            .size(MinTouch)
            .themedTap(CircleShape, theme.surface, theme.border, borderWidth = 1.dp, active = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = theme.text, modifier = Modifier.size(32.dp))
    }
}
