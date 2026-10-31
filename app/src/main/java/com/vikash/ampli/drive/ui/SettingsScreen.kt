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
import androidx.compose.ui.semantics.Role
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
import com.vikash.ampli.drive.billing.ProPricing
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.i18n.AppLanguages
import com.vikash.ampli.drive.map.GLANCE_ATTRIBUTION
import com.vikash.ampli.drive.prefs.ThemeChoice
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.system.RestrictedSettings
import com.vikash.ampli.drive.system.AppChooser
import com.vikash.ampli.drive.system.AppResolver
import com.vikash.ampli.drive.system.AppRole
import com.vikash.ampli.drive.ui.theme.CardStyle
import com.vikash.ampli.drive.ui.theme.DriveTheme
import com.vikash.ampli.drive.ui.theme.DriveThemes
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.ui.theme.ThemeAccess
import com.vikash.ampli.drive.ui.theme.HomeStyle
import com.vikash.ampli.drive.system.BtPermissions
import com.vikash.ampli.drive.tripcost.TripCostFlags

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
    onOpenVoiceCommands: () -> Unit = {},
) {
    val dims = LocalDims.current
    val context = LocalContext.current
    val emergency by app.emergency.profile.collectAsStateWithLifecycle()
    val keepScreenOn by app.prefs.keepScreenOn.collectAsStateWithLifecycle()
    val themeChoice by app.prefs.theme.collectAsStateWithLifecycle()
    val pro by app.prefs.pro.collectAsStateWithLifecycle()
    val savedLanguage by app.prefs.language.collectAsStateWithLifecycle()
    val price by app.billing.price.collectAsStateWithLifecycle()
    val gps by app.speedTracker.state.collectAsStateWithLifecycle()
    val musicPkg by app.prefs.musicPackage.collectAsStateWithLifecycle()
    val phonePkg by app.prefs.phonePackage.collectAsStateWithLifecycle()
    val phoneAutoPlay by app.prefs.phoneAutoPlay.collectAsStateWithLifecycle()
    val mirrorPkg by app.prefs.autoPackage.collectAsStateWithLifecycle()
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
    val notifMayBlock = remember(resumeTick, hasNotif) { !hasNotif && RestrictedSettings.mayBlock(context) }
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
                isPro = pro.isPro,
                onChange = app.prefs::setTheme,
                onLocked = { showProSheet = true },
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
            // Phone music over Bluetooth: press play on the phone when it connects.
            ToggleRow(
                title = stringResource(R.string.phone_autoplay_title),
                status = stringResource(R.string.phone_autoplay_hint),
                checked = phoneAutoPlay,
                onChange = app.prefs::setPhoneAutoPlay,
            )
            // Phone Link sheet: an optional screen-mirroring app at its bottom (none by default).
            SettingRow(
                title = stringResource(R.string.mirror_app_title),
                status = mirrorPkg?.let { AppResolver.label(context, it) } ?: stringResource(R.string.mirror_app_none),
                ok = true,
                action = stringResource(R.string.action_change),
                onAction = { AppChooser.ask(AppRole.Mirror, settingOnly = true, phoneLink = true) },
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
                onAction = { if (hasNotif) Launchers.openNotificationAccess(context) else Launchers.allowNotificationAccess(context) },
                // Android 13+ sideloaded: the switch is refused once; App info › ⋮ › Allow restricted settings.
                secondaryAction = if (notifMayBlock) stringResource(R.string.restricted_open_app_info) else null,
                onSecondary = { Launchers.openAppDetails(context) },
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
            SpeedVolumeCard(app = app)
            FastagSettingsCard(app = app, onOpenFastag = onOpenFastag)
            PapersReminderCard(app = app)
            VoiceSettingsCard(app = app, resumeTick = resumeTick, onOpenVoiceCheck = onOpenVoiceCheck, onOpenCommands = onOpenVoiceCommands)
            VoiceStyleCard(app = app)
            VoicePackCard(app = app)
            DesiSamplesCard(app = app)
            WelcomeSettingsCard(app = app)
            NavigateWithCard(app = app)
            NavPromptsCard(app = app)
            CallSettingsCard(app = app, resumeTick = resumeTick)
            WhatsAppReadCard(app = app, isPro = pro.isPro, resumeTick = resumeTick, onLocked = { showProSheet = true })
            CarStartCard(app = app)
            BhajanSettingsCard(app = app, isPro = pro.isPro, onLocked = { showProSheet = true })
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
                    stringResource(R.string.fuel_attribution),
                    stringResource(R.string.fuel_attribution_ocm),
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
        val isLaunchPrice by app.billing.isLaunchPrice.collectAsStateWithLifecycle()
        ProSheet(
            price = price,
            isLaunchPrice = isLaunchPrice,
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
internal fun ThemedSwitch(checked: Boolean, onChange: (Boolean) -> Unit) = ThemedSwitch(checked, enabled = true, onChange = onChange)

/** [ThemedSwitch] that can be greyed out (for example "park to switch on"). */
@Composable
internal fun ThemedSwitch(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val theme = LocalDriveTheme.current
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
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
            .border(if (theme.borderWidth > 1.dp) theme.borderWidth else 1.dp, if (selected && theme.borderWidth <= 1.dp) theme.accent else theme.border, shape)
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

/**
 * Settings › Home screen theme: the four themes as cards with a small picture of each Home, a
 * Free / Pro badge, and the "Auto night" switch (Pro: Big Glance after sunset).
 */
@Composable
internal fun ThemePicker(
    choice: ThemeChoice,
    isPro: Boolean,
    onChange: (ThemeChoice) -> Unit,
    onLocked: () -> Unit,
) {
    val dims = LocalDims.current
    val active = LocalDriveTheme.current
    val autoNightOn = choice.autoNight && ThemeAccess.autoNightAvailable(isPro)

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
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth < 760.dp) 2 else 4
                Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                    for (row in DriveThemes.all.chunked(columns)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                            for (t in row) {
                                val locked = ThemeAccess.locked(t.id, isPro)
                                ThemeCard(
                                    theme = t,
                                    inUse = t.id == active.id,
                                    locked = locked,
                                    onClick = { if (locked) onLocked() else onChange(ThemeAccess.select(choice, t.id)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            // Auto night: Big Glance between sunset and sunrise. Pro only; free drivers stay on Map First.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouch)
                    .clickable {
                        if (ThemeAccess.autoNightAvailable(isPro)) onChange(choice.copy(autoNight = !choice.autoNight)) else onLocked()
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Box(Modifier.weight(1f)) {
                    CardTitle(
                        stringResource(R.string.theme_auto_night),
                        stringResource(if (isPro) R.string.theme_auto_night_hint else R.string.theme_auto_night_pro),
                    )
                }
                if (!isPro) {
                    Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.theme_locked), tint = active.muted, modifier = Modifier.size(24.dp))
                }
                ThemedSwitch(autoNightOn, enabled = isPro) { onChange(choice.copy(autoNight = it)) }
            }
        }
    }
}

@Composable
private fun ThemeCard(
    theme: DriveTheme,
    inUse: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dims = LocalDims.current
    val ui = LocalDriveTheme.current
    val shape = ui.cardShape(dims, scale = 0.5f)
    Column(
        modifier = modifier
            .themedTap(shape, ui.surfaceHigh, border = ui.border, active = inUse, role = Role.RadioButton, onClick = onClick)
            .padding(dims.d(1f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(0.8f)),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1280f / 800f).clip(RoundedCornerShape(dims.d(1.2f)))) {
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
        Text(
            stringResource(theme.nameRes),
            fontSize = dims.t(2.6f, min = 16f),
            fontWeight = FontWeight.SemiBold,
            color = ui.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Badge(
            text = stringResource(if (theme.isPro) R.string.theme_pro else R.string.theme_free),
            background = if (theme.isPro) Color(0xFFFBBF24) else ui.ok,
            content = Color(0xFF111111),
        )
    }
}

@Composable
private fun Badge(text: String, background: Color, content: Color) {
    val dims = LocalDims.current
    Text(
        text,
        color = content,
        fontSize = dims.t(1.8f, min = 12f),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A miniature of [t]'s Home, drawn with a few rectangles in its own colours and layout. */
@Composable
private fun ThemeSwatch(t: DriveTheme, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sos = t.danger
        fun box(x: Float, y: Float, bw: Float, bh: Float, color: Color, r: Float = 0.03f, edge: Color? = null) {
            val cr = CornerRadius(w * r, w * r)
            drawRoundRect(color, Offset(x * w, y * h), Size(bw * w, bh * h), cr)
            if (edge != null) drawRoundRect(edge, Offset(x * w, y * h), Size(bw * w, bh * h), cr, style = Stroke(width = maxOf(1.5f, w * 0.008f)))
        }
        drawRect(t.background)
        when (t.style) {
            HomeStyle.BigGlance -> {
                box(0.04f, 0.15f, 0.29f, 0.6f, t.surface)
                box(0.355f, 0.15f, 0.29f, 0.6f, t.surface)
                box(0.67f, 0.15f, 0.29f, 0.6f, t.surface)
                box(0.08f, 0.32f, 0.14f, 0.3f, t.text, 0.01f)
                box(0.04f, 0.8f, 0.48f, 0.15f, t.accent)
                box(0.55f, 0.8f, 0.2f, 0.15f, t.surface)
                box(0.77f, 0.8f, 0.19f, 0.15f, sos)
            }
            HomeStyle.MapFirst -> {
                drawRect(Color(0xFFE8E2D4))
                drawRect(Color(0xFFF7F3EA), Offset(0f, h * 0.4f), Size(w, h * 0.06f))
                drawRect(Color(0xFFF7F3EA), Offset(w * 0.48f, 0f), Size(w * 0.05f, h))
                drawRect(t.accent, Offset(0f, h * 0.415f), Size(w * 0.5f, h * 0.03f))
                drawRect(t.accent, Offset(w * 0.49f, h * 0.43f), Size(w * 0.03f, h * 0.57f))
                box(0.03f, 0.05f, 0.34f, 0.2f, t.surface)
                box(0.82f, 0.15f, 0.15f, 0.24f, t.surface, 0.08f)
                box(0.03f, 0.74f, 0.37f, 0.21f, t.surface)
                box(0.63f, 0.8f, 0.09f, 0.15f, t.surface)
                box(0.74f, 0.75f, 0.13f, 0.21f, t.accent, 0.07f)
                box(0.885f, 0.8f, 0.09f, 0.15f, sos)
            }
            HomeStyle.Bento -> {
                box(0.03f, 0.04f, 0.3f, 0.6f, t.ink, edge = t.border)
                box(0.35f, 0.04f, 0.31f, 0.29f, t.accent)
                box(0.68f, 0.04f, 0.29f, 0.29f, t.background, edge = t.border)
                box(0.35f, 0.36f, 0.31f, 0.28f, t.surface)
                box(0.68f, 0.36f, 0.29f, 0.28f, t.surfaceHigh)
                box(0.03f, 0.68f, 0.46f, 0.28f, t.accent)
                box(0.51f, 0.68f, 0.14f, 0.28f, t.background, edge = t.border)
                box(0.67f, 0.68f, 0.14f, 0.28f, t.ink)
                box(0.83f, 0.68f, 0.14f, 0.28f, sos)
            }
            HomeStyle.RoadSign -> {
                drawRect(t.ink, Offset.Zero, Size(w, h * 0.09f))
                box(0.03f, 0.15f, 0.31f, 0.6f, t.surface, 0f, t.border)
                box(0.37f, 0.15f, 0.31f, 0.6f, t.accent, 0f, t.border)
                box(0.71f, 0.15f, 0.26f, 0.28f, t.ink, 0f)
                box(0.71f, 0.47f, 0.26f, 0.28f, t.surface, 0f, t.border)
                box(0.03f, 0.81f, 0.15f, 0.14f, t.surface, 0f, t.border)
                box(0.2f, 0.81f, 0.15f, 0.14f, t.surface, 0f, t.border)
                box(0.37f, 0.81f, 0.27f, 0.14f, t.accent, 0f, t.border)
                box(0.66f, 0.81f, 0.15f, 0.14f, t.surface, 0f, t.border)
                box(0.83f, 0.81f, 0.14f, 0.14f, sos, 0f, t.border)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProSheet(
    price: String?,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
    isLaunchPrice: Boolean = false,
    launchOffer: Boolean = BuildConfig.LAUNCH_OFFER,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.surface.copy(alpha = 1f),
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
                for (t in DriveThemes.all.filter { it.isPro }) {
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1280f / 800f)
                            .clip(RoundedCornerShape(dims.d(1f))),
                    ) { ThemeSwatch(t, Modifier.fillMaxSize()) }
                }
            }
            // Trip cost stays hidden until TripCostFlags.ENABLED is switched on.
            val points = listOfNotNull(
                R.string.pro_point_themes,
                R.string.pro_point_car,
                R.string.pro_point_whatsapp,
                R.string.pro_point_trip_cost.takeIf { TripCostFlags.ENABLED },
                R.string.pro_point_once,
            )
            for (point in points) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = theme.ok, modifier = Modifier.size(24.dp))
                    Text(stringResource(point), fontSize = dims.t(2.6f, min = 15f), color = theme.text)
                }
            }
            // Only while the offer runs and Play really charges the launch price: never a fake discount.
            if (launchOffer && isLaunchPrice) {
                Text(
                    stringResource(
                        R.string.pro_launch_line,
                        ProPricing.rupees(ProPricing.LAUNCH_PRICE_INR),
                        ProPricing.rupees(ProPricing.REGULAR_PRICE_INR),
                    ),
                    fontSize = dims.t(2.6f, min = 16f),
                    fontWeight = FontWeight.SemiBold,
                    color = theme.accentSoft,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = dims.d(1f), bottom = dims.d(3f)),
                horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
            ) {
                BigButton(
                    // Play's price whenever it has answered; the Console price as a fallback before that.
                    text = stringResource(R.string.pro_buy_price, price ?: ProPricing.fallbackPrice(launchOffer)),
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
