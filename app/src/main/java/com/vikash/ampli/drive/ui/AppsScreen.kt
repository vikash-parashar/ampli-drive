package com.vikash.ampli.drive.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.vikash.ampli.drive.system.Shell
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.system.AppEntry
import com.vikash.ampli.drive.system.InstalledApps
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

/**
 * Apps, Ampli Shell style: only the driver's Ampli list (by default the map, music, phone link and
 * Ampli apps), then "More apps" with everything else, optionally behind a 4-digit PIN. In More
 * apps, "Edit list" turns taps into add / remove. Long-press puts an app on the Phone link button.
 */
@Composable
fun AppsScreen(app: DriveApp, onBack: () -> Unit, onOpenStore: () -> Unit = {}) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val autoPackage by app.prefs.autoPackage.collectAsStateWithLifecycle()
    val musicPackage by app.prefs.musicPackage.collectAsStateWithLifecycle()
    val chosenSaved by app.shell.drawerApps.collectAsStateWithLifecycle()
    val pinSet by app.shell.pinSet.collectAsStateWithLifecycle()
    val apps by produceState<List<AppEntry>?>(null) { value = InstalledApps.load(context) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var askPin by remember { mutableStateOf<PinAsk?>(null) }
    var pinError by remember { mutableStateOf(false) }
    val hidden by app.shell.hiddenApps.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }

    val list = apps
    val chosen = remember(list, chosenSaved, musicPackage) {
        chosenSaved ?: list?.let { Shell.defaultDrawer(context, it, musicPackage) }.orEmpty()
    }
    if (showAll) BackHandler(enabled = true) { showAll = false; editing = false }

    Column(
        modifier = Modifier.fillMaxSize().padding(dims.d(2.4f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
            Box(Modifier.weight(1f)) {
                ScreenHeader(
                    title = stringResource(if (showAll) R.string.shell_more_apps else R.string.apps_title),
                    hint = stringResource(if (editing) R.string.shell_edit_hint else R.string.apps_hint),
                    onBack = { if (showAll) { showAll = false; editing = false } else onBack() },
                )
            }
            if (showAll && hidden.isNotEmpty()) {
                BigButton(
                    text = stringResource(R.string.apps_unhide_all, hidden.size),
                    onClick = app.shell::unhideAll,
                    icon = Icons.Filled.Visibility,
                    primary = false,
                )
            }
            if (showAll) {
                BigButton(
                    text = stringResource(if (pinSet) R.string.shell_pin_remove else R.string.shell_pin_set),
                    onClick = { if (pinSet) app.shell.setPin(null) else askPin = PinAsk.Set },
                    icon = Icons.Filled.Lock,
                    primary = false,
                )
                BigButton(
                    text = stringResource(if (editing) R.string.shell_edit_done else R.string.shell_edit_list),
                    onClick = { editing = !editing },
                    icon = Icons.Filled.Edit,
                    primary = editing,
                )
            }
        }
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.accent)
            }
        } else {
            val visible = list.filter { it.packageName !in hidden }
            val shown = if (showAll) visible else visible.filter { it.packageName in chosen }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 132.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = dims.d(2f)),
                horizontalArrangement = Arrangement.spacedBy(dims.d(1.5f)),
                verticalArrangement = Arrangement.spacedBy(dims.d(1.5f)),
            ) {
                items(shown, key = { it.packageName + "/" + it.activityName }) { entry ->
                    AppTile(
                        entry = entry,
                        isAuto = entry.packageName == autoPackage,
                        onList = if (showAll) entry.packageName in chosen else null,
                        onOpen = {
                            if (editing) {
                                val next = if (entry.packageName in chosen) chosen - entry.packageName else chosen + entry.packageName
                                app.shell.setDrawerApps(next)
                            } else {
                                Launchers.openComponent(context, entry.packageName, entry.activityName)
                            }
                        },
                        // Long-press: hide it, or make it the Phone Link sheet's screen-mirroring app.
                        onLongPress = { menuFor = entry },
                    )
                }
                if (!showAll) {
                    // Ampli Store: Pro, the MP3 Player and voice packs.
                    item(key = "ampli-store") { StoreTile(onClick = onOpenStore) }
                    item(key = "more-apps") {
                        MoreAppsTile(locked = pinSet, onClick = {
                            if (pinSet) {
                                pinError = false
                                askPin = PinAsk.Unlock
                            } else {
                                showAll = true
                            }
                        })
                    }
                }
            }
        }
    }

    menuFor?.let { entry ->
        val (bg, fg) = dialogColors()
        val isMirror = entry.packageName == autoPackage
        AlertDialog(
            onDismissRequest = { menuFor = null },
            containerColor = bg, titleContentColor = fg, textContentColor = fg,
            title = { Text(entry.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                    BigButton(
                        text = stringResource(R.string.apps_hide),
                        onClick = {
                            app.shell.setHidden(entry.packageName, true)
                            Toast.makeText(context, context.getString(R.string.apps_hidden_toast, entry.label), Toast.LENGTH_SHORT).show()
                            menuFor = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.VisibilityOff,
                        primary = false,
                    )
                    BigButton(
                        text = stringResource(if (isMirror) R.string.apps_mirror_stop else R.string.apps_mirror_use),
                        onClick = {
                            if (isMirror) {
                                app.prefs.setAutoPackage(null)
                                Toast.makeText(context, context.getString(R.string.apps_removed_from_auto), Toast.LENGTH_SHORT).show()
                            } else {
                                app.prefs.setAutoPackage(entry.packageName)
                                Toast.makeText(context, context.getString(R.string.apps_added_to_auto, entry.label), Toast.LENGTH_SHORT).show()
                            }
                            menuFor = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.PhonelinkRing,
                        primary = false,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    askPin?.let { ask ->
        PinDialog(
            title = stringResource(if (ask == PinAsk.Set) R.string.shell_pin_set else R.string.shell_pin_enter),
            error = pinError,
            onPin = { pin ->
                when (ask) {
                    PinAsk.Set -> {
                        app.shell.setPin(pin)
                        askPin = null
                    }
                    PinAsk.Unlock -> if (app.shell.checkPin(pin)) {
                        askPin = null
                        showAll = true
                    } else {
                        pinError = true
                    }
                }
            },
            onDismiss = { askPin = null },
            moving = isMoving(app),
        )
    }
}

private enum class PinAsk { Set, Unlock }

/** The last tile of the Ampli list: everything else, with a lock when a PIN is set. */
@Composable
private fun MoreAppsTile(locked: Boolean, onClick: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = RoundedCornerShape(dims.d(2.2f))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .themedTap(shape, theme.accent.copy(alpha = 0.14f), theme.accent.copy(alpha = 0.5f), borderWidth = 1.dp, onClick = onClick)
            .padding(dims.d(1.4f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(if (locked) Icons.Filled.Lock else Icons.Filled.Apps, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(44.dp))
        Text(stringResource(R.string.shell_more_apps), fontSize = dims.t(2.2f), color = theme.text, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
    }
}

/** The Ampli Store tile in the Ampli list. */
@Composable
private fun StoreTile(onClick: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = RoundedCornerShape(dims.d(2.2f))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .themedTap(shape, Saffron.copy(alpha = 0.16f), Saffron.copy(alpha = 0.6f), borderWidth = 1.dp, onClick = onClick)
            .padding(dims.d(1.4f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        // Soft themes: the saffron icon is too faint on the light base (under 3:1), so it takes the accent.
        Icon(Icons.Filled.Storefront, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(44.dp))
        Text(stringResource(R.string.store_title), fontSize = dims.t(2.2f), color = theme.text, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ScreenHeader(title: String, hint: String?, onBack: () -> Unit) {
    val dims = LocalDims.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Box(
            modifier = Modifier
                .size(MinTouch)
                .themedTap(CircleShape, MaterialTheme.colorScheme.surfaceContainer, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = dims.t(4.2f, min = 22f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            if (hint != null) {
                Text(hint, fontSize = dims.t(2.3f), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AppTile(entry: AppEntry, isAuto: Boolean, onOpen: () -> Unit, onLongPress: () -> Unit, onList: Boolean? = null) {
    val dims = LocalDims.current
    val context = LocalContext.current
    val iconPx = with(LocalDensity.current) { 56.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, entry) { value = InstalledApps.icon(context, entry, iconPx) }
    val shape = RoundedCornerShape(dims.d(2.2f))
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, if (isAuto) LocalDriveTheme.current.accent else MaterialTheme.colorScheme.outlineVariant, shape)
            .pointerInput(entry) {
                detectTapGestures(onTap = { currentOnOpen() }, onLongPress = { currentOnLongPress() })
            }
            .padding(dims.d(1.4f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            val bmp = icon
            if (bmp != null) Image(bmp, contentDescription = null, modifier = Modifier.size(56.dp))
            if (onList != null) {
                Icon(
                    if (onList) Icons.Filled.CheckCircle else Icons.Filled.AddCircleOutline,
                    contentDescription = stringResource(if (onList) R.string.shell_on_list else R.string.shell_not_on_list),
                    tint = if (onList) LocalDriveTheme.current.ok else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                )
            }
            if (isAuto) {
                Icon(
                    Icons.Filled.PhonelinkRing,
                    contentDescription = stringResource(R.string.apps_on_auto),
                    tint = LocalDriveTheme.current.accent,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        }
        Text(
            entry.label,
            fontSize = dims.t(2.2f),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
