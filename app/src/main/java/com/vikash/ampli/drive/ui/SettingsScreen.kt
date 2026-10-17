package com.vikash.ampli.drive.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.content.Intent
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Language
import com.vikash.ampli.drive.feedback.FeedbackRepository
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.BuildConfig
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.i18n.AppLanguages
import com.vikash.ampli.drive.map.GLANCE_ATTRIBUTION
import com.vikash.ampli.drive.prefs.ThemeChoice
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.system.AppChooser
import com.vikash.ampli.drive.system.AppResolver
import com.vikash.ampli.drive.system.AppRole
import com.vikash.ampli.drive.ui.theme.CardStyle
import com.vikash.ampli.drive.ui.theme.Decoration
import com.vikash.ampli.drive.ui.theme.DriveTheme
import com.vikash.ampli.drive.ui.theme.DriveThemes
import com.vikash.ampli.drive.ui.theme.HomeLayout
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.ui.theme.ThemeFamily
import com.vikash.ampli.drive.ui.theme.ThemeAccess
import com.vikash.ampli.drive.system.BtPermissions

@Composable
fun SettingsScreen(
    app: DriveApp,
    night: Boolean,
    resumeTick: Int,
    onBack: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenFastag: () -> Unit,
    onOpenEmergency: () -> Unit,
    onOpenCar: () -> Unit = {},
    onOpenFeedback: () -> Unit = {},
    onOpenVoiceCheck: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
) {
    val dims = LocalDims.current
    val context = LocalContext.current
    val emergency by app.emergency.profile.collectAsStateWithLifecycle()
    val keepScreenOn by app.prefs.keepScreenOn.collectAsStateWithLifecycle()
    val themeChoice by app.prefs.theme.collectAsStateWithLifecycle()
    val pro by app.prefs.pro.collectAsStateWithLifecycle()
    val reduceGlass by app.prefs.reduceGlass.collectAsStateWithLifecycle()
    val savedLanguage by app.prefs.language.collectAsStateWithLifecycle()
    val price by app.billing.price.collectAsStateWithLifecycle()
    val gps by app.speedTracker.state.collectAsStateWithLifecycle()
    val musicPkg by app.prefs.musicPackage.collectAsStateWithLifecycle()
    val phonePkg by app.prefs.phonePackage.collectAsStateWithLifecycle()
    var showProSheet by rememberSaveable { mutableStateOf(false) }
    val fullScreen by app.shell.fullScreen.collectAsStateWithLifecycle()
    val startupAnimation by app.shell.startupAnimation.collectAsStateWithLifecycle()
    var homeAsked by remember { mutableStateOf(0) }
    val makeHome = rememberMakeHome { homeAsked++ }

    // Re-read every time the launcher comes back from a system settings screen.
    val isHome = remember(resumeTick, homeAsked) { Launchers.isDefaultHome(context) }
    val hasFine = remember(resumeTick) { granted(context, Manifest.permission.ACCESS_FINE_LOCATION) }
    val hasCoarse = remember(resumeTick) { granted(context, Manifest.permission.ACCESS_COARSE_LOCATION) }
    val hasBt = remember(resumeTick) { BtPermissions.hasConnect(context) }
    val hasNotif = remember(resumeTick) { app.mediaHub.hasAccess() }
    val language = remember(resumeTick, savedLanguage) { AppLanguages.current(context, savedLanguage) }

    Column(
        modifier = Modifier.fillMaxSize().padding(dims.d(2.4f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_title), hint = null, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
        ) {
            // A Pro or tip payment still waiting for UPI approval.
            PaymentPendingNote(app = app)
            // Ampli Shell: shown until Ampli is the head unit's home app.
            if (!isHome) DefaultHomeCard(onMake = makeHome, onLater = null, modifier = Modifier.fillMaxWidth())
            ThemePicker(
                choice = themeChoice,
                night = night,
                isPro = pro.isPro,
                onChange = app.prefs::setTheme,
                onLocked = { showProSheet = true },
            )
            ToggleRow(
                title = stringResource(R.string.glass_reduce_title),
                status = stringResource(
                    if (app.prefs.reduceGlassByDevice) R.string.glass_reduce_hint_auto else R.string.glass_reduce_hint,
                ),
                checked = reduceGlass,
                onChange = app.prefs::setReduceGlass,
            )
            if (BuildConfig.DEBUG) {
                ToggleRow(
                    title = stringResource(R.string.pro_test_title),
                    status = stringResource(R.string.pro_test_hint),
                    checked = pro.testUnlock,
                    onChange = app.prefs::setProTestUnlock,
                )
            }
            LanguagePicker(
                selected = language,
                onSelect = { tag ->
                    app.prefs.setLanguage(tag)
                    if (AppLanguages.apply(context, tag)) context.findActivity()?.recreate()
                },
            )
            SettingRow(
                title = stringResource(R.string.home_title),
                status = stringResource(if (isHome) R.string.home_ok else R.string.home_not_default),
                ok = isHome,
                action = stringResource(if (isHome) R.string.action_change else R.string.home_set_default),
                onAction = { if (isHome) Launchers.openHomeSettings(context) else makeHome() },
            )
            // Head units name their apps "Multimedia", "BT phone"…: the driver can pick them here.
            SettingRow(
                title = stringResource(R.string.music_app_title),
                status = musicPkg?.let { AppResolver.label(context, it) } ?: stringResource(R.string.app_choice_automatic),
                ok = true,
                action = stringResource(R.string.action_change),
                onAction = { AppChooser.ask(AppRole.Music, settingOnly = true) },
            )
            SettingRow(
                title = stringResource(R.string.phone_app_title),
                status = phonePkg?.let { AppResolver.label(context, it) } ?: stringResource(R.string.app_choice_automatic),
                ok = true,
                action = stringResource(R.string.action_change),
                onAction = { AppChooser.ask(AppRole.Phone, settingOnly = true) },
            )
            SettingRow(
                title = stringResource(R.string.location_title),
                status = stringResource(
                    when {
                        !hasFine && hasCoarse -> R.string.location_approximate
                        !hasFine -> R.string.location_off
                        !gps.hasGps -> R.string.location_no_gps
                        !gps.providerEnabled -> R.string.location_switched_off
                        else -> R.string.location_ok
                    },
                ),
                ok = hasFine && gps.hasGps && gps.providerEnabled,
                action = when {
                    !hasFine -> stringResource(R.string.action_allow)
                    !gps.providerEnabled -> stringResource(R.string.action_turn_on)
                    else -> null
                },
                onAction = {
                    if (!hasFine) onRequestPermissions() else Launchers.openLocationSettings(context)
                },
                secondaryAction = if (!hasFine) stringResource(R.string.action_app_settings) else null,
                onSecondary = { Launchers.openAppDetails(context) },
            )
            SettingRow(
                title = stringResource(R.string.notif_title),
                status = stringResource(if (hasNotif) R.string.notif_ok else R.string.notif_needed),
                ok = hasNotif,
                action = stringResource(if (hasNotif) R.string.action_manage else R.string.allow_access),
                onAction = { Launchers.openNotificationAccess(context) },
            )
            SettingRow(
                title = stringResource(R.string.bt_label),
                status = stringResource(if (hasBt) R.string.bt_ok else R.string.bt_needed),
                ok = hasBt,
                action = if (hasBt) null else stringResource(R.string.action_allow),
                onAction = onRequestPermissions,
            )
            SettingRow(
                title = stringResource(R.string.em_title),
                status = if (emergency.contacts.isEmpty()) {
                    stringResource(R.string.em_status_empty)
                } else {
                    listOf(
                        stringResource(R.string.em_status, emergency.contacts.size),
                        emergency.bloodGroup,
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                },
                ok = emergency.contacts.isNotEmpty(),
                action = stringResource(R.string.action_edit),
                onAction = onOpenEmergency,
            )
            CarSettingsCard(app = app, onOpenCar = onOpenCar, resumeTick = resumeTick)
            FastagSettingsCard(app = app, onOpenFastag = onOpenFastag)
            PapersReminderCard(app = app)
            VoiceSettingsCard(app = app, resumeTick = resumeTick, onOpenVoiceCheck = onOpenVoiceCheck)
            VoiceStyleCard(app = app)
            VoicePackCard(app = app)
            DesiSamplesCard(app = app)
            WelcomeSettingsCard(app = app)
            NavigateWithCard(app = app)
            NavPromptsCard(app = app)
            CarStartCard(app = app)
            CameraSettingsCard(app = app, isPro = pro.isPro, onLocked = { showProSheet = true })
            // Settings › Display: Ampli as the whole screen.
            ToggleRow(
                title = stringResource(R.string.shell_fullscreen_title),
                status = stringResource(R.string.shell_fullscreen_hint),
                checked = fullScreen,
                onChange = app.shell::setFullScreen,
            )
            ToggleRow(
                title = stringResource(R.string.shell_startup_title),
                status = stringResource(R.string.shell_startup_hint),
                checked = startupAnimation,
                onChange = app.shell::setStartupAnimation,
            )
            ToggleRow(
                title = stringResource(R.string.screen_on_title),
                status = stringResource(R.string.screen_on_hint),
                checked = keepScreenOn,
                onChange = app.prefs::setKeepScreenOn,
            )
            // Settings only: never on the driving screens.
            MoreFromAmpliCard()
            SupportCard(app = app)
            LinkRow(
                icon = Icons.Filled.Feedback,
                title = stringResource(R.string.feedback_title),
                hint = stringResource(R.string.feedback_settings_hint),
                action = stringResource(R.string.action_open),
                onClick = onOpenFeedback,
            )
            InfoCard(
                logo = true,
                title = stringResource(R.string.about_title),
                lines = listOfNotNull(
                    "Ampli Car Hub ${BuildConfig.VERSION_NAME}",
                    if (pro.isPro) stringResource(R.string.pro_active) else null,
                    stringResource(R.string.about_map, GLANCE_ATTRIBUTION),
                    stringResource(R.string.cameras_attribution),
                    stringResource(R.string.weather_attribution),
                    stringResource(R.string.about_not_affiliated),
                ),
            )
            // Settings › About › Diagnostics: real Android version, map, GPS, network, for support photos.
            LinkRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.diag_title),
                hint = stringResource(R.string.diag_hint),
                action = stringResource(R.string.action_open),
                onClick = onOpenDiagnostics,
            )
            // Settings › About › Website.
            LinkRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.website_title),
                hint = WEBSITE_URL.removePrefix("https://") + " · " + FeedbackRepository.SUPPORT_EMAIL,
                action = stringResource(R.string.action_open),
                onClick = { startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE_URL))) },
            )
            // Settings › About › Privacy policy (also required by Play).
            LinkRow(
                icon = Icons.Filled.Policy,
                title = stringResource(R.string.privacy_title),
                hint = stringResource(R.string.privacy_hint),
                action = stringResource(R.string.action_open),
                onClick = { startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) },
            )
        }
    }

    if (showProSheet) {
        ProSheet(
            price = price,
            onBuy = {
                val activity = context.findActivity()
                if (activity == null || !app.billing.purchase(activity)) {
                    Toast.makeText(context, context.getString(R.string.pro_unavailable), Toast.LENGTH_LONG).show()
                } else {
                    showProSheet = false
                }
            },
            onRestore = {
                if (!app.billing.restore()) {
                    Toast.makeText(context, context.getString(R.string.pro_unavailable), Toast.LENGTH_LONG).show()
                }
                showProSheet = false
            },
            onDismiss = { showProSheet = false },
        )
    }
}

private fun granted(context: Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun SettingCard(content: @Composable () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = theme.cardShape(dims, scale = 0.9f)
    Box(
        Modifier
            .fillMaxWidth()
            .themedCard(theme, shape, accentEdge = false)
            .padding(horizontal = dims.d(2.6f), vertical = dims.d(1.8f)),
    ) { content() }
}

@Composable
internal fun CardTitle(title: String, status: String?) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Column {
        Text(title, fontSize = dims.t(3f, min = 18f), fontWeight = FontWeight.SemiBold, color = theme.text)
        if (status != null) Text(status, fontSize = dims.t(2.4f), color = theme.muted)
    }
}

@Composable
internal fun SettingRow(
    title: String,
    status: String,
    ok: Boolean,
    action: String?,
    onAction: () -> Unit,
    secondaryAction: String? = null,
    onSecondary: () -> Unit = {},
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    SettingCard {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
        ) {
            Icon(
                if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = stringResource(if (ok) R.string.status_ok else R.string.status_attention),
                tint = if (ok) theme.ok else theme.warning,
                modifier = Modifier.size(32.dp),
            )
            Box(Modifier.weight(1f)) { CardTitle(title, status) }
            if (secondaryAction != null) {
                BigButton(text = secondaryAction, onClick = onSecondary, primary = false)
            }
            if (action != null) {
                BigButton(text = action, onClick = onAction, primary = !ok)
            }
        }
    }
}

/** Ampli's website (Settings › About, Help & feedback, privacy policy). */
const val WEBSITE_URL = "https://ampliapps.in"

/** Car Hub privacy policy on the Ampli website (source: docs/privacy-policy.html). */
const val PRIVACY_POLICY_URL = "https://ampliapps.in/privacy/carhub/"

@Composable
private fun LinkRow(icon: ImageVector, title: String, hint: String, action: String, onClick: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    SettingCard {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
        ) {
            Icon(icon, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
            Box(Modifier.weight(1f)) { CardTitle(title, hint) }
            BigButton(text = action, onClick = onClick, primary = false)
        }
    }
}

@Composable
internal fun ThemedSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val theme = LocalDriveTheme.current
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = theme.onAccent,
            checkedTrackColor = theme.accent,
            uncheckedThumbColor = theme.muted,
            uncheckedTrackColor = theme.surfaceHigh,
            uncheckedBorderColor = theme.border,
        ),
    )
}

@Composable
internal fun ToggleRow(title: String, status: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val dims = LocalDims.current
    SettingCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouch)
                .clickable { onChange(!checked) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
        ) {
            Box(Modifier.weight(1f)) { CardTitle(title, status) }
            ThemedSwitch(checked, onChange)
        }
    }
}

/** A selectable chip, at least 64 dp tall. */
@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = if (theme.radius <= 0f) androidx.compose.ui.graphics.RectangleShape else CircleShape
    Box(
        modifier = modifier
            .heightIn(min = MinTouch)
            .clip(shape)
            .background(if (selected) theme.accent else theme.surfaceHigh)
            .border(1.dp, if (selected) theme.accent else theme.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = dims.d(2.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) theme.onAccent else theme.text,
            fontSize = dims.t(2.6f, min = 16f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ------------------------------------------------------------------------------------ Themes

private enum class Slot { Day, Night }

@Composable
private fun ThemePicker(
    choice: ThemeChoice,
    night: Boolean,
    isPro: Boolean,
    onChange: (ThemeChoice) -> Unit,
    onLocked: () -> Unit,
) {
    val dims = LocalDims.current
    val active = LocalDriveTheme.current
    val context = LocalContext.current
    // The chip starts on the slot showing now (and follows sunrise/sunset), so a tap changes the
    // screen at once. It used to be saveable and could sit on "Day" after dark: the tap then only
    // changed the day theme and nothing visible happened.
    var slot by remember(night) { mutableStateOf(if (night) Slot.Night else Slot.Day) }

    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Icon(Icons.Filled.Palette, contentDescription = null, tint = active.accentSoft, modifier = Modifier.size(32.dp))
                Box(Modifier.weight(1f)) { CardTitle(stringResource(R.string.theme_title), null) }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouch)
                    .clickable { onChange(toggledAuto(choice, active)) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Box(Modifier.weight(1f)) {
                    CardTitle(stringResource(R.string.theme_auto), stringResource(R.string.theme_auto_hint))
                }
                ThemedSwitch(choice.auto) { onChange(toggledAuto(choice, active)) }
            }
            if (choice.auto) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
                ) {
                    Text(stringResource(R.string.theme_choose_for), fontSize = dims.t(2.6f), color = active.muted)
                    Chip(stringResource(R.string.theme_day), slot == Slot.Day, { slot = Slot.Day })
                    Chip(stringResource(R.string.theme_night), slot == Slot.Night, { slot = Slot.Night })
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = (maxWidth / 190.dp).toInt().coerceIn(2, 5)
                Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                  for (family in ThemeFamily.entries) {
                    Text(
                        stringResource(
                            when (family) {
                                ThemeFamily.Classic -> R.string.theme_family_classic
                                ThemeFamily.Glass -> R.string.theme_family_glass
                                ThemeFamily.Saffron -> R.string.theme_family_saffron
                                ThemeFamily.Soft -> R.string.theme_family_soft
                            },
                        ),
                        fontSize = dims.t(2.6f, min = 15f),
                        fontWeight = FontWeight.Bold,
                        color = active.muted,
                        modifier = Modifier.padding(top = dims.d(0.8f)),
                    )
                    for (row in DriveThemes.all.filter { it.family == family }.chunked(columns)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                            for (t in row) {
                                val tag = when {
                                    !choice.auto -> null
                                    t.id == choice.dayId && t.id == choice.nightId ->
                                        stringResource(R.string.theme_day) + " · " + stringResource(R.string.theme_night)
                                    t.id == choice.dayId -> stringResource(R.string.theme_day)
                                    t.id == choice.nightId -> stringResource(R.string.theme_night)
                                    else -> null
                                }
                                ThemeCard(
                                    theme = t,
                                    inUse = t.id == active.id,
                                    locked = t.isPro && !isPro,
                                    slotTag = tag,
                                    onClick = {
                                        if (t.isPro && !isPro) {
                                            onLocked()
                                        } else {
                                            val forNight = slot == Slot.Night
                                            onChange(ThemeAccess.select(choice, t.id, forNight))
                                            // Chosen for the other half of the day: say when it will show.
                                            if (choice.auto && forNight != night) {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(if (forNight) R.string.theme_saved_for_night else R.string.theme_saved_for_day),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // Keep the last row's cards the same width as the others.
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                  }
                }
            }
        }
    }
}

/** Turning Auto off keeps whatever is showing now; turning it on restores the day/night pair. */
private fun toggledAuto(choice: ThemeChoice, showing: DriveTheme): ThemeChoice =
    if (choice.auto) choice.copy(auto = false, fixedId = showing.id) else choice.copy(auto = true)

@Composable
private fun ThemeCard(
    theme: DriveTheme,
    inUse: Boolean,
    locked: Boolean,
    slotTag: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dims = LocalDims.current
    val ui = LocalDriveTheme.current
    val shape = RoundedCornerShape(dims.d(1.8f))
    Column(
        modifier = modifier
            .clip(shape)
            .background(ui.surfaceHigh)
            .border(if (inUse) 3.dp else 1.dp, if (inUse) ui.accent else ui.border, shape)
            .clickable(onClick = onClick)
            .padding(dims.d(1f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(0.8f)),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1024f / 600f).clip(RoundedCornerShape(dims.d(1.2f)))) {
            ThemeSwatch(theme, Modifier.fillMaxSize())
            if (locked) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.theme_locked),
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (inUse) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(ui.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.theme_in_use),
                        tint = ui.onAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                theme.code,
                fontSize = dims.t(2f, min = 12f),
                color = ui.muted,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
            Text(
                theme.nameRes?.let { stringResource(it) } ?: theme.name,
                fontSize = dims.t(2.4f, min = 14f),
                fontWeight = FontWeight.SemiBold,
                color = ui.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Badge(
                text = stringResource(if (theme.isPro) R.string.theme_pro else R.string.theme_free),
                background = if (theme.isPro) Color(0xFFFBBF24) else ui.ok,
                content = Color(0xFF111111),
            )
            if (slotTag != null) Badge(text = slotTag, background = ui.accent, content = ui.onAccent)
        }
    }
}

@Composable
private fun Badge(text: String, background: Color, content: Color) {
    val dims = LocalDims.current
    Text(
        text,
        color = content,
        fontSize = dims.t(1.8f, min = 11f),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A miniature of the home screen in [t]'s colours and layout, drawn with a few rectangles. */
@Composable
private fun ThemeSwatch(t: DriveTheme, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (t.decoration == Decoration.Synthwave) {
            drawRect(
                Brush.verticalGradient(
                    0f to Color(0xFF0D0221), 0.5f to Color(0xFF4A1366), 0.53f to Color(0xFFB8236F),
                    0.532f to Color(0xFF12002A), 1f to Color(0xFF12002A),
                ),
            )
            drawCircle(Color(0xFFFF901F), radius = h * 0.16f, center = Offset(w / 2f, h * 0.42f))
        } else if (t.decoration == Decoration.Glass && t.glass != null) {
            drawGlassBackdrop(t.glass)
        } else {
            drawRect(t.background)
        }
        if (t.decoration == Decoration.Holi) {
            drawCircle(Color(0x8CFF3D8B), radius = h * 0.3f, center = Offset(0f, 0f))
            drawCircle(Color(0x8CFFD23F), radius = h * 0.3f, center = Offset(w, 0f))
        }
        val pad = h * 0.05f
        val gap = h * 0.035f
        val dockH = h * 0.15f
        val top = pad + h * 0.08f
        val bottom = h - pad - dockH - gap
        val r = CornerRadius(h * t.radius / 100f * 1.2f)
        val mapColor = if (t.map.name == "Liberty" || t.map.name == "Positron" || t.map.name == "Bright") Color(0xFFE7EBF0) else Color(0xFF1A1D26)

        fun pane(x: Float, y: Float, pw: Float, ph: Float, fill: Color) {
            if (t.cardStyle == CardStyle.Pop) drawRoundRect(t.accent2, Offset(x + 3f, y + 3f), Size(pw, ph), r)
            if (t.cardStyle == CardStyle.Neumorphic) {
                drawRoundRect(t.shadowDark, Offset(x + 2f, y + 2f), Size(pw, ph), r)
                drawRoundRect(t.shadowLight, Offset(x - 2f, y - 2f), Size(pw, ph), r)
            }
            drawRoundRect(fill, Offset(x, y), Size(pw, ph), r)
            if (t.borderWidth.value > 0f && t.border.alpha > 0f) {
                drawRoundRect(t.border, Offset(x, y), Size(pw, ph), r, style = Stroke(width = maxOf(1f, t.borderWidth.value)))
            }
        }

        fun mapPane(x: Float, y: Float, pw: Float, ph: Float) {
            pane(x, y, pw, ph, mapColor)
            drawLine(t.accent2, Offset(x + pw * 0.2f, y + ph * 0.9f), Offset(x + pw * 0.55f, y + ph * 0.45f), strokeWidth = h * 0.02f, cap = StrokeCap.Round)
            drawLine(t.accent2, Offset(x + pw * 0.55f, y + ph * 0.45f), Offset(x + pw * 0.8f, y + ph * 0.2f), strokeWidth = h * 0.02f, cap = StrokeCap.Round)
            if (t.mapTint.alpha > 0f) drawRoundRect(t.mapTint, Offset(x, y), Size(pw, ph), r)
            // Turn card
            drawRoundRect(t.accent, Offset(x + pw * 0.08f, y + ph * 0.08f), Size(pw * 0.25f, ph * 0.18f), r)
        }

        fun speedPane(x: Float, y: Float, pw: Float, ph: Float) {
            pane(x, y, pw, ph, t.speedSurface ?: t.surface)
            val d = minOf(pw, ph) * 0.62f
            val c = Offset(x + pw / 2f, y + ph / 2f)
            drawArc(t.track, 135f, 270f, false, Offset(c.x - d / 2, c.y - d / 2), Size(d, d), style = Stroke(width = d * 0.1f, cap = StrokeCap.Round))
            drawArc(t.speedText ?: t.accent, 135f, 170f, false, Offset(c.x - d / 2, c.y - d / 2), Size(d, d), style = Stroke(width = d * 0.1f, cap = StrokeCap.Round))
        }

        fun musicPane(x: Float, y: Float, pw: Float, ph: Float) {
            pane(x, y, pw, ph, t.musicSurface ?: t.surface)
            drawRoundRect(t.accent, Offset(x + pw * 0.12f, y + ph * 0.1f), Size(pw * 0.76f, ph * 0.42f), r)
            drawRoundRect(t.musicText.copy(alpha = 0.8f), Offset(x + pw * 0.12f, y + ph * 0.6f), Size(pw * 0.6f, ph * 0.06f))
            drawRoundRect(t.muted.copy(alpha = 0.7f), Offset(x + pw * 0.12f, y + ph * 0.7f), Size(pw * 0.4f, ph * 0.05f))
            drawCircle(t.accent, radius = ph * 0.08f, center = Offset(x + pw / 2f, y + ph * 0.86f))
        }

        // Clock stub
        drawRoundRect(t.text.copy(alpha = 0.85f), Offset(pad, pad), Size(w * 0.09f, h * 0.05f))
        val areaW = w - pad * 2
        val areaH = bottom - top
        when (t.layout) {
            HomeLayout.MapSpeedMusic, HomeLayout.SpeedMapMusic, HomeLayout.MusicSpeedMap -> {
                val ws = when (t.layout) {
                    HomeLayout.MapSpeedMusic -> listOf(1.55f, 1f, 1.05f)
                    HomeLayout.SpeedMapMusic -> listOf(0.95f, 1.5f, 1.05f)
                    else -> listOf(1f, 0.95f, 1.6f)
                }
                val unit = (areaW - gap * 2) / ws.sum()
                var x = pad
                ws.forEachIndexed { i, wt ->
                    val pw = unit * wt
                    val kind = when (t.layout) {
                        HomeLayout.MapSpeedMusic -> i
                        HomeLayout.SpeedMapMusic -> listOf(1, 0, 2)[i]
                        else -> listOf(2, 1, 0)[i]
                    }
                    when (kind) {
                        0 -> mapPane(x, top, pw, areaH)
                        1 -> speedPane(x, top, pw, areaH)
                        else -> musicPane(x, top, pw, areaH)
                    }
                    x += pw + gap
                }
            }
            HomeLayout.MapTopRow -> {
                val mh = (areaH - gap) * 0.55f
                mapPane(pad, top, areaW, mh)
                val unit = (areaW - gap) / 2.2f
                speedPane(pad, top + mh + gap, unit * 0.8f, areaH - mh - gap)
                musicPane(pad + unit * 0.8f + gap, top + mh + gap, unit * 1.4f, areaH - mh - gap)
            }
            HomeLayout.MapStacked -> {
                val unit = (areaW - gap) / 2.75f
                mapPane(pad, top, unit * 1.75f, areaH)
                val x = pad + unit * 1.75f + gap
                val sh = (areaH - gap) * 0.45f
                speedPane(x, top, unit, sh)
                musicPane(x, top + sh + gap, unit, areaH - sh - gap)
            }
            HomeLayout.GiantSpeed -> {
                val unit = (areaW - gap) / 2.45f
                // Giant number blocks
                drawRect(t.text, Offset(pad, top + areaH * 0.15f), Size(unit * 0.55f, areaH * 0.55f))
                drawRect(t.text, Offset(pad + unit * 0.65f, top + areaH * 0.15f), Size(unit * 0.55f, areaH * 0.55f))
                drawRect(t.accent2, Offset(pad, top + areaH * 0.82f), Size(unit * 0.9f, areaH * 0.08f))
                val x = pad + unit * 1.45f + gap
                mapPane(x, top, unit, (areaH - gap) * 0.45f)
                musicPane(x, top + (areaH - gap) * 0.45f + gap, unit, (areaH - gap) * 0.55f)
            }
        }
        // Dock
        val dw = (areaW - gap * 5) / 6f
        for (i in 0 until 6) {
            val x = pad + i * (dw + gap)
            pane(x, h - pad - dockH, dw, dockH, if (i == 0) t.dockActive else t.surface)
        }
        if (t.decoration == Decoration.Scanlines) {
            var y = 0f
            while (y < h) {
                drawLine(t.accent.copy(alpha = 0.08f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                y += 3f
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProSheet(price: String?, onBuy: () -> Unit, onRestore: () -> Unit, onDismiss: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (theme.surface.alpha < 1f) theme.background else theme.surface,
        contentColor = theme.text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dims.d(4f), vertical = dims.d(1f)),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
        ) {
            Text(
                stringResource(R.string.pro_title),
                fontSize = dims.t(4.4f, min = 22f),
                fontWeight = FontWeight.ExtraBold,
                color = theme.text,
            )
            Text(stringResource(R.string.pro_body), fontSize = dims.t(2.8f, min = 16f), color = theme.muted)
            // Little strip of the Pro themes.
            Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1f))) {
                for (t in listOf(
                    DriveThemes.MidnightGlass, DriveThemes.SportRed, DriveThemes.Synthwave,
                    DriveThemes.RoseGlass, DriveThemes.HoliPop, DriveThemes.SoftNight,
                )) {
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1024f / 600f)
                            .clip(RoundedCornerShape(dims.d(1f))),
                    ) { ThemeSwatch(t, Modifier.fillMaxSize()) }
                }
            }
            for (point in listOf(R.string.pro_point_themes, R.string.pro_point_car, R.string.pro_point_once)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = theme.ok, modifier = Modifier.size(24.dp))
                    Text(stringResource(point), fontSize = dims.t(2.6f, min = 15f), color = theme.text)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = dims.d(1f), bottom = dims.d(3f)),
                horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
            ) {
                BigButton(
                    text = if (price != null) stringResource(R.string.pro_buy_price, price) else stringResource(R.string.pro_buy),
                    onClick = onBuy,
                    modifier = Modifier.weight(1.4f),
                )
                BigButton(text = stringResource(R.string.pro_restore), onClick = onRestore, primary = false, modifier = Modifier.weight(1f))
                BigButton(text = stringResource(R.string.pro_not_now), onClick = onDismiss, primary = false, modifier = Modifier.weight(0.8f))
            }
        }
    }
}

// ---------------------------------------------------------------------------------- Language

@Composable
private fun LanguagePicker(selected: String, onSelect: (String) -> Unit) {
    val dims = LocalDims.current
    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
            CardTitle(stringResource(R.string.language_title), stringResource(R.string.language_hint))
            val options = listOf("" to stringResource(R.string.language_system)) +
                AppLanguages.all.map { it.tag to it.nativeName }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = (maxWidth / 170.dp).toInt().coerceIn(2, 6)
                Column(verticalArrangement = Arrangement.spacedBy(dims.d(1f))) {
                    for (row in options.chunked(columns)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1f))) {
                            for ((tag, name) in row) {
                                Chip(name, selected == tag, { onSelect(tag) }, Modifier.weight(1f))
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, lines: List<String>, logo: Boolean = false) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (logo) {
                // "dark" lockup is the one made for dark backgrounds.
                Image(
                    painterResource(if (theme.isLight) R.drawable.logo_carhub_horizontal_light else R.drawable.logo_carhub_horizontal_dark),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.padding(bottom = dims.d(1f)).height(maxOf(dims.d(8f), 48.dp)),
                )
            }
            Text(title, fontSize = dims.t(3f, min = 18f), fontWeight = FontWeight.SemiBold, color = theme.text)
            for (line in lines) {
                Text(line, fontSize = dims.t(2.3f), color = theme.muted)
            }
        }
    }
}
