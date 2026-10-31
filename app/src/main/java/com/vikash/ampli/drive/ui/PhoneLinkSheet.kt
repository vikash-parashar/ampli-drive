package com.vikash.ampli.drive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.bluetooth.BtDevice
import com.vikash.ampli.drive.bluetooth.PhoneBluetooth
import com.vikash.ampli.drive.system.AppResolver
import com.vikash.ampli.drive.system.FavouriteCallsStore
import com.vikash.ampli.drive.system.FavouriteContact
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.delay

/**
 * Car Hub's own Phone Link (the dock button and "connect my phone"), instead of the unit's
 * phone-mirroring app: the connected phone and what it is connected for, phone music, favourite
 * calls, the paired devices with Switch, Bluetooth settings, and a screen-mirroring app only when
 * the driver picked one (Settings, or long-press in Apps).
 */
@Composable
fun PhoneLinkSheet(app: DriveApp, onDismiss: () -> Unit, onRequestPermissions: () -> Unit) {
    val context = LocalContext.current
    val (bg, fg) = dialogColors()
    val bt = app.phoneBt
    val devices by bt.devices.collectAsStateWithLifecycle()
    val status by app.bluetooth.state.collectAsStateWithLifecycle()
    val mirror by app.prefs.autoPackage.collectAsStateWithLifecycle()
    val contacts by app.favouriteCalls.contacts.collectAsStateWithLifecycle()
    var switching by remember { mutableStateOf<BtDevice?>(null) }
    var phoneCard by remember { mutableStateOf(false) }
    val hasPermission = remember(status.hasPermission) { bt.hasPermission() }
    LaunchedEffect(Unit) { bt.refresh() }

    // Switch device: wait up to ~10 s for the unit to really connect, else open its Bluetooth app.
    LaunchedEffect(switching) {
        val target = switching ?: return@LaunchedEffect
        repeat(10) {
            delay(1_000L)
            bt.refresh()
            if (bt.devices.value.any { it.address == target.address && it.connected }) {
                switching = null
                return@LaunchedEffect
            }
        }
        switching = null
        bt.openPairingScreen(context, target.name)
    }

    val connected = bt.connectedPhone(devices)
    val phoneName = connected?.name ?: status.connectedName
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bg, titleContentColor = fg, textContentColor = fg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.PhonelinkRing, contentDescription = null, tint = LocalDriveTheme.current.accentSoft, modifier = Modifier.size(30.dp))
                Text(stringResource(R.string.plink_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            PhoneLinkContent(
                phoneName = phoneName,
                connected = connected,
                devices = devices,
                btSupported = bt.supported,
                hasPermission = hasPermission,
                switchingName = switching?.name,
                contacts = contacts,
                mirrorLabel = mirror?.let { AppResolver.label(context, it) },
                onPlay = { bt.playPhoneMusic(context) },
                onCall = { c ->
                    FavouriteCallsStore.call(context, c)
                    onDismiss()
                },
                onAddFavourite = { phoneCard = true },
                onSwitch = { d ->
                    if (bt.switchTo(d.address) == PhoneBluetooth.SwitchStart.Asked) {
                        switching = d
                    } else {
                        bt.openPairingScreen(context, d.name)
                    }
                },
                onBtSettings = { bt.openPairingScreen(context, null) },
                onAllow = onRequestPermissions,
                onMirror = {
                    mirror?.let { Launchers.openPackage(context, it) }
                    onDismiss()
                },
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
    if (phoneCard) PhoneCardSheet(app = app, onDismiss = { phoneCard = false })
}

/** The sheet's body; also used by the screen previews. */
@Composable
internal fun PhoneLinkContent(
    phoneName: String?,
    connected: BtDevice?,
    devices: List<BtDevice>,
    btSupported: Boolean,
    hasPermission: Boolean,
    switchingName: String?,
    contacts: List<FavouriteContact>,
    mirrorLabel: String?,
    onPlay: () -> Unit,
    onCall: (FavouriteContact) -> Unit,
    onAddFavourite: () -> Unit,
    onSwitch: (BtDevice) -> Unit,
    onBtSettings: () -> Unit,
    onAllow: () -> Unit,
    onMirror: () -> Unit,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Column(
        Modifier.widthIn(min = 460.dp).heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
    ) {
        // The connected phone.
        Row(
            Modifier
                .fillMaxWidth()
                .themedSurface(theme, RoundedCornerShape(16.dp), theme.surfaceHigh, if (phoneName != null) theme.ok.copy(alpha = 0.6f) else theme.border, sunk = true)
                .padding(horizontal = dims.d(2f), vertical = dims.d(1.4f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
        ) {
            Icon(
                if (phoneName != null) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = if (phoneName != null) theme.ok else theme.muted,
                modifier = Modifier.size(36.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    if (phoneName != null) stringResource(R.string.plink_connected, phoneName) else stringResource(R.string.plink_not_connected),
                    color = theme.text,
                    fontSize = dims.t(2.8f, min = 17f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val parts = listOfNotNull(
                    connected?.calls?.let { (if (it) "✓ " else "✗ ") + stringResource(R.string.plink_calls) },
                    connected?.music?.let { (if (it) "✓ " else "✗ ") + stringResource(R.string.plink_music) },
                    connected?.battery?.let { stringResource(R.string.plink_battery, it) },
                )
                if (parts.isNotEmpty()) Text(parts.joinToString("   "), color = theme.muted, fontSize = dims.t(2.3f, min = 14f))
            }
        }
        if (switchingName != null) {
            Text(stringResource(R.string.plink_switching, switchingName), color = theme.accentSoft, fontSize = dims.t(2.4f, min = 15f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
            if (phoneName != null) {
                BigButton(stringResource(R.string.plink_play), onPlay, Modifier.weight(1f), icon = Icons.Filled.PlayArrow)
            }
            BigButton(stringResource(R.string.plink_bt_settings), onBtSettings, Modifier.weight(1f), icon = Icons.Filled.Settings, primary = phoneName == null)
        }

        // Favourite calls (round 6): one tap calls.
        PlinkLabel(stringResource(R.string.fav_calls_title))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
            for (c in contacts) {
                SmallChip(c.name, onClick = { onCall(c) }, modifier = Modifier.weight(1f, fill = false))
            }
            if (contacts.size < com.vikash.ampli.drive.system.FavouriteCallsLogic.MAX) {
                SmallChip("+ " + stringResource(R.string.fav_calls_add), onClick = onAddFavourite)
            }
        }

        // Paired devices with Switch.
        PlinkLabel(stringResource(R.string.plink_devices))
        when {
            !btSupported -> Text(stringResource(R.string.plink_no_devices), color = theme.muted, fontSize = dims.t(2.3f, min = 14f))
            !hasPermission -> BigButton(stringResource(R.string.action_allow), onAllow, Modifier.fillMaxWidth(), icon = Icons.Filled.Bluetooth, primary = false)
            devices.isEmpty() -> Text(stringResource(R.string.plink_no_devices), color = theme.muted, fontSize = dims.t(2.3f, min = 14f))
            else -> for (d in devices) DeviceRow(d, onSwitch = { onSwitch(d) })
        }

        if (mirrorLabel != null) {
            BigButton(
                stringResource(R.string.plink_mirror) + " · " + mirrorLabel,
                onMirror,
                Modifier.fillMaxWidth(),
                icon = Icons.Filled.Cast,
                primary = false,
            )
        }
    }
}

@Composable
private fun PlinkLabel(text: String) {
    val dims = LocalDims.current
    Text(text, color = LocalDriveTheme.current.accentSoft, fontSize = dims.t(2.4f, min = 15f), fontWeight = FontWeight.Bold)
}

@Composable
private fun SmallChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Box(
        modifier
            .heightIn(min = MinTouch)
            .themedTap(CircleShape, theme.surfaceHigh, theme.border, borderWidth = 1.dp, onClick = onClick)
            .padding(horizontal = dims.d(2f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = theme.text, fontSize = dims.t(2.4f, min = 15f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DeviceRow(d: BtDevice, onSwitch: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .clip(RoundedCornerShape(14.dp))
            .background(if (d.connected) theme.accent.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, if (d.connected) theme.accentSoft.copy(alpha = 0.5f) else theme.border, RoundedCornerShape(14.dp))
            .padding(horizontal = dims.d(1.6f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
    ) {
        Icon(
            if (d.phone) Icons.Filled.PhoneAndroid else Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = if (d.connected) theme.ok else theme.muted,
            modifier = Modifier.size(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(d.name, color = theme.text, fontSize = dims.t(2.5f, min = 15f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(if (d.connected) R.string.plink_now else R.string.plink_paired),
                color = if (d.connected) theme.ok else theme.muted,
                fontSize = dims.t(2.1f, min = 13f),
            )
        }
        if (!d.connected) BigButton(stringResource(R.string.plink_switch), onSwitch, primary = false)
    }
}
