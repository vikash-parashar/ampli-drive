package com.vikash.ampli.drive.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.system.AppCleaner
import com.vikash.ampli.drive.system.AppEntry
import com.vikash.ampli.drive.system.CleanerApp
import com.vikash.ampli.drive.system.InstalledApps
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.system.QuickActions
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.launch

/**
 * "Gaadi slow? Apps saaf karo" (design board "Cleaner"): the installed apps, heaviest first, with
 * when each was last used, and for the chosen app: Open, put it on the Auto button, hide it from
 * Ampli's Apps, App info (Force stop) and Uninstall. Ampli itself is never listed; system apps get
 * App info instead of Uninstall. Sizes and last use need Usage access: the screen says how.
 */
@Composable
fun CleanerScreen(app: DriveApp, resumeTick: Int, onBack: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val autoPackage by app.prefs.autoPackage.collectAsStateWithLifecycle()
    val hidden by app.shell.hiddenApps.collectAsStateWithLifecycle()
    // Re-read on every return: after an uninstall, App info or the Usage access page.
    val access = remember(resumeTick) { AppCleaner.hasUsageAccess(context) }
    val apps by produceState<List<CleanerApp>?>(null, resumeTick) { value = AppCleaner.load(context) }
    val memory = remember(resumeTick) { AppCleaner.memory(context) }
    var selected by remember { mutableStateOf<String?>(null) }
    var clearing by remember { mutableStateOf(false) }
    val now = remember(resumeTick) { System.currentTimeMillis() }

    Column(
        modifier = Modifier.fillMaxSize().padding(dims.d(3f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        ScreenHeader(
            title = stringResource(R.string.cleaner_title),
            hint = stringResource(
                R.string.cleaner_memory,
                QuickActions.sizeLabel(memory.ramUsed),
                QuickActions.sizeLabel(memory.ramTotal),
                QuickActions.sizeLabel(memory.storageFree),
            ),
            onBack = onBack,
            action = stringResource(if (clearing) R.string.qp_clearing else R.string.cleaner_clear_memory),
            onAction = {
                if (!clearing) {
                    clearing = true
                    scope.launch {
                        val freed = QuickActions.clearMemory(context)
                        clearing = false
                        val msg = if (freed > 8L * 1024 * 1024) context.getString(R.string.qp_cleared, QuickActions.sizeLabel(freed)) else context.getString(R.string.qp_cleared_none)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
        val list = apps
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(dims.d(1.5f))) {
                if (!access) UsageAccessGuide(onAllow = { AppCleaner.openUsageAccess(context) })
                Text(
                    stringResource(R.string.cleaner_heaviest).uppercase(),
                    color = theme.muted,
                    fontWeight = FontWeight.Bold,
                    fontSize = dims.t(2.25f, min = 16f),
                    letterSpacing = 0.1.em,
                )
                if (list == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = theme.accent) }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(dims.d(1.5f))) {
                        items(list, key = { it.packageName }) { a ->
                            CleanerRow(
                                app = a,
                                now = now,
                                selected = a.packageName == selected,
                                onSelect = { selected = a.packageName },
                                onRemove = {
                                    if (a.system) AppCleaner.appInfo(context, a.packageName) else AppCleaner.uninstall(context, a)
                                },
                            )
                        }
                    }
                }
            }
            val chosen = list?.firstOrNull { it.packageName == selected } ?: list?.firstOrNull { !it.system }
            if (chosen != null) {
                CleanerSheet(
                    app = chosen,
                    onAuto = autoPackage == chosen.packageName,
                    hidden = chosen.packageName in hidden,
                    onOpen = { Launchers.openComponent(context, chosen.packageName, chosen.activityName) },
                    onAutoButton = {
                        if (autoPackage == chosen.packageName) {
                            app.prefs.setAutoPackage(null)
                            Toast.makeText(context, context.getString(R.string.apps_removed_from_auto), Toast.LENGTH_SHORT).show()
                        } else {
                            app.prefs.setAutoPackage(chosen.packageName)
                            Toast.makeText(context, context.getString(R.string.apps_added_to_auto, chosen.label), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onHide = {
                        val hide = chosen.packageName !in hidden
                        app.shell.setHidden(chosen.packageName, hide)
                        if (hide) Toast.makeText(context, context.getString(R.string.apps_hidden_toast, chosen.label), Toast.LENGTH_SHORT).show()
                    },
                    onAppInfo = { AppCleaner.appInfo(context, chosen.packageName) },
                    onUninstall = { AppCleaner.uninstall(context, chosen) },
                    modifier = Modifier.width(dims.d(49f)).fillMaxHeight(),
                )
            }
        }
    }
}

/** Without Usage access: what it is for and the three steps to allow it. */
@Composable
private fun UsageAccessGuide(onAllow: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = theme.cardShape(dims, 0.83f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.surface)
            .padding(horizontal = dims.d(2.5f), vertical = dims.d(2f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dims.d(0.5f))) {
            Text(stringResource(R.string.cleaner_access_title), color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(2.75f, min = 20f))
            Text(stringResource(R.string.cleaner_access_steps), color = theme.muted, fontWeight = FontWeight.SemiBold, fontSize = dims.t(2.25f, min = 16f))
        }
        BigButton(text = stringResource(R.string.cleaner_access_allow), onClick = onAllow)
    }
}

@Composable
private fun CleanerRow(app: CleanerApp, now: Long, selected: Boolean, onSelect: () -> Unit, onRemove: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val worth = AppCleaner.worthCleaning(app, now)
    val shape = theme.buttonShape(dims)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dims.d(11f))
            .themedTap(shape, theme.surface, border = if (selected) theme.accent else if (worth) theme.warning.copy(alpha = 0.5f) else theme.surfaceHigh, borderWidth = 2.dp, onClick = onSelect)
            .padding(horizontal = dims.d(2f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        AppIcon(app, dims.d(8f))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(3f, min = 20f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                cleanerNote(app, now),
                color = if (worth) theme.warning else theme.muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = dims.t(2.25f, min = 16f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            (if (app.sizeExact) "" else "~") + QuickActions.sizeLabel(app.sizeBytes),
            style = theme.numberStyle(theme.text),
            fontSize = dims.t(3.75f, min = 22f),
            maxLines = 1,
        )
        val danger = Color(0xFFFF8A98)
        Box(
            Modifier
                .heightIn(min = dims.d(8f))
                .themedTap(shape, Color.Transparent, border = if (app.system) theme.border else danger, borderWidth = 2.dp, onClick = onRemove)
                .padding(horizontal = dims.d(2.75f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (app.system) R.string.cleaner_stop else R.string.cleaner_remove),
                color = if (app.system) theme.text else if (theme.isLight) theme.danger else danger,
                fontWeight = FontWeight.Bold,
                fontSize = dims.t(2.75f, min = 18f),
                maxLines = 1,
            )
        }
    }
}

/** "9 din se nahi khola", "Kal khola tha", "System app". */
@Composable
private fun cleanerNote(app: CleanerApp, now: Long): String {
    val days = AppCleaner.daysSince(app.lastUsed, now)
    return when {
        app.system -> stringResource(R.string.cleaner_system)
        days == null -> stringResource(R.string.cleaner_last_unknown)
        days == 0L -> stringResource(R.string.cleaner_used_today)
        days == 1L -> stringResource(R.string.cleaner_used_yesterday)
        else -> stringResource(R.string.cleaner_not_opened, days.toInt())
    }
}

/** The chosen app's options: Open, Auto button, Hide, App info, Uninstall. */
@Composable
private fun CleanerSheet(
    app: CleanerApp,
    onAuto: Boolean,
    hidden: Boolean,
    onOpen: () -> Unit,
    onAutoButton: () -> Unit,
    onHide: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = theme.cardShape(dims)
    Column(
        modifier
            .clip(shape)
            .background(theme.surface)
            .then(Modifier.themedSurface(theme, shape, theme.surface, border = theme.accent, borderWidth = 2.dp))
            .verticalScroll(rememberScrollState())
            .padding(dims.d(3f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(1.5f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            AppIcon(app, dims.d(9f))
            Column(Modifier.weight(1f)) {
                Text(app.label, color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(3.25f, min = 22f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(QuickActions.sizeLabel(app.sizeBytes), color = theme.muted, fontWeight = FontWeight.SemiBold, fontSize = dims.t(2.25f, min = 16f), maxLines = 1)
            }
        }
        SheetKey(stringResource(R.string.cleaner_open), onOpen)
        SheetKey(stringResource(if (onAuto) R.string.apps_mirror_stop else R.string.cleaner_auto), onAutoButton)
        SheetKey(stringResource(if (hidden) R.string.cleaner_unhide else R.string.cleaner_hide), onHide)
        SheetKey(stringResource(R.string.cleaner_app_info), onAppInfo)
        if (!app.system) SheetKey(stringResource(R.string.cleaner_uninstall), onUninstall, danger = true)
        Text(stringResource(R.string.cleaner_sheet_hint), color = theme.muted, fontSize = dims.t(2.25f, min = 16f))
    }
}

@Composable
private fun SheetKey(label: String, onClick: () -> Unit, danger: Boolean = false) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = theme.buttonShape(dims)
    val pink = Color(0xFFFF8A98)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dims.d(9f))
            .themedTap(
                shape,
                if (danger) (if (theme.isLight) theme.danger.copy(alpha = 0.1f) else Color(0xFF2A0E13)) else theme.surfaceHigh,
                border = if (danger) pink else null,
                borderWidth = 2.dp,
                onClick = onClick,
            )
            .padding(horizontal = dims.d(2.75f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            color = if (danger) (if (theme.isLight) theme.danger else pink) else theme.text,
            fontWeight = FontWeight.Bold,
            fontSize = dims.t(3f, min = 20f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AppIcon(app: CleanerApp, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val theme = LocalDriveTheme.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = InstalledApps.icon(context, AppEntry(app.packageName, app.activityName, app.label), px)
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(theme.surfaceHigh), contentAlignment = Alignment.Center) {
        val bmp = icon
        if (bmp != null) {
            Image(bmp, contentDescription = null, modifier = Modifier.size(size * 0.8f))
        } else {
            Text(app.label.take(1).uppercase(), style = theme.numberStyle(theme.text), fontSize = LocalDims.current.t(4f, min = 22f))
        }
    }
}
