package com.vikash.ampli.drive.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.cameras.ChallanSaver
import com.vikash.ampli.drive.car.FuelDiary
import com.vikash.ampli.drive.car.FuelFill
import com.vikash.ampli.drive.car.FuelType
import com.vikash.ampli.drive.car.Mileage
import com.vikash.ampli.drive.map.OfflineMapState
import com.vikash.ampli.drive.map.OfflineRegion
import com.vikash.ampli.drive.map.OfflineRegions
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import java.time.LocalDate
import java.time.ZoneId

/** Driver controls in these cards are at least this tall (owner's rule: 80 px targets). */
private val ExtraTouch: Dp = 80.dp

@Composable
private fun ExtraHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String?) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Icon(icon, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
        Box(Modifier.weight(1f)) { CardTitle(title, hint) }
    }
}

@Composable
private fun ExtraSwitch(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val dims = LocalDims.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = ExtraTouch).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Box(Modifier.weight(1f)) { CardTitle(title, hint) }
        ThemedSwitch(checked, onChange)
    }
}

@Composable
private fun Note(text: String, warn: Boolean = false) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Text(text, fontSize = dims.t(2.2f), color = if (warn) theme.warning else theme.muted)
}

// ------------------------------------------------------------------------------ Offline map

@Composable
internal fun regionLabel(key: String): String = stringResource(
    when (key) {
        OfflineRegions.DELHI_NCR.key -> R.string.x_region_delhi_ncr
        OfflineRegions.TRICITY.key -> R.string.x_region_tricity
        "around_me" -> R.string.x_region_around
        else -> R.string.x_region_haryana_ncr
    },
)

/** Settings › Offline map: region chips, size and free space, Download / Pause / Delete. */
@Composable
internal fun OfflineMapCard(app: DriveApp) {
    val dims = LocalDims.current
    val st by app.offlineMap.state.collectAsStateWithLifecycle()
    val chosenKey by app.extras.offlineRegion.collectAsStateWithLifecycle()
    val gps by app.speedTracker.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { app.offlineMap.refresh() }
    val key = chosenKey ?: OfflineRegions.HARYANA_NCR.key
    val here = gps.location
    val region: OfflineRegion = if (key == "around_me" && here != null) {
        OfflineRegions.aroundMe(here.latitude, here.longitude)
    } else {
        OfflineRegions.byKey(key)
    }
    val free = remember(st.phase) { app.offlineMap.freeBytes() }
    val need = OfflineRegions.megabytes(OfflineRegions.estimatedBytes(region))
    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
            ExtraHeader(Icons.Filled.Map, stringResource(R.string.x_offline_title), stringResource(R.string.x_offline_hint))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(dims.d(1f))) {
                for (k in OfflineRegions.ALL.map { it.key } + "around_me") {
                    Chip(regionLabel(k), k == key, { app.extras.setOfflineRegion(k) }, Modifier.heightIn(min = ExtraTouch))
                }
            }
            Note(stringResource(R.string.x_offline_size, need, (free / 1_000_000L).toInt()))
            val line = when (st.phase) {
                OfflineMapState.Phase.Ready -> stringResource(R.string.x_offline_ready, regionLabel(st.regionKey ?: key), OfflineRegions.megabytes(st.bytes))
                OfflineMapState.Phase.Downloading, OfflineMapState.Phase.Checking -> stringResource(R.string.x_offline_progress, st.percent)
                OfflineMapState.Phase.Paused -> stringResource(R.string.x_offline_paused, st.percent)
                OfflineMapState.Phase.NeedsWifi -> stringResource(R.string.x_offline_needs_wifi)
                OfflineMapState.Phase.NoSpace -> stringResource(R.string.x_offline_no_space, need)
                OfflineMapState.Phase.Failed -> stringResource(R.string.x_offline_failed, st.error.orEmpty())
                OfflineMapState.Phase.Idle -> null
            }
            if (line != null) {
                Note(line, warn = st.phase in setOf(OfflineMapState.Phase.NeedsWifi, OfflineMapState.Phase.NoSpace, OfflineMapState.Phase.Failed))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                val busy = st.phase == OfflineMapState.Phase.Downloading || st.phase == OfflineMapState.Phase.Checking
                if (busy) {
                    BigButton(stringResource(R.string.x_offline_pause), { app.offlineMap.pause() }, Modifier.weight(1f).heightIn(min = ExtraTouch), icon = Icons.Filled.Pause, primary = false)
                } else {
                    BigButton(
                        stringResource(if (st.phase == OfflineMapState.Phase.Paused) R.string.x_offline_resume else R.string.x_offline_download),
                        { app.offlineMap.download(region) },
                        Modifier.weight(1f).heightIn(min = ExtraTouch),
                        icon = Icons.Filled.Download,
                    )
                }
                if (st.regionKey != null && st.phase != OfflineMapState.Phase.Idle) {
                    BigButton(stringResource(R.string.x_offline_delete), { app.offlineMap.delete() }, Modifier.weight(1f).heightIn(min = ExtraTouch), icon = Icons.Filled.Delete, primary = false)
                }
            }
            Note(stringResource(R.string.x_offline_licence))
        }
    }
}

// --------------------------------------------------------------------------- Resume on start

@Composable
internal fun ResumeOnStartCard(app: DriveApp) {
    val dims = LocalDims.current
    val music by app.extras.resumeMusic.collectAsStateWithLifecycle()
    val route by app.extras.resumeRoute.collectAsStateWithLifecycle()
    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1f))) {
            ExtraHeader(Icons.Filled.PlayCircle, stringResource(R.string.x_resume_title), null)
            ExtraSwitch(stringResource(R.string.x_resume_music), stringResource(R.string.x_resume_music_hint), music) { app.extras.setResumeMusic(it) }
            ExtraSwitch(stringResource(R.string.x_resume_route), stringResource(R.string.x_resume_route_hint), route) { app.extras.setResumeRoute(it) }
        }
    }
}

// ----------------------------------------------------------------------------- Phone hotspot

@Composable
internal fun HotspotCard(app: DriveApp, resumeTick: Int = 0) {
    val dims = LocalDims.current
    val context = LocalContext.current
    val ssid by app.extras.hotspotSsid.collectAsStateWithLifecycle()
    val phone by app.extras.hotspotPhone.collectAsStateWithLifecycle()
    val auto by app.extras.hotspotAuto.collectAsStateWithLifecycle()
    val status by app.hotspot.status.collectAsStateWithLifecycle()
    val networks = remember(resumeTick) { app.hotspot.savedNetworks() }
    val phones = remember(resumeTick) { app.hotspot.pairedPhones() }
    val current = remember(resumeTick, status) { app.hotspot.currentSsid() }
    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
            ExtraHeader(Icons.Filled.Wifi, stringResource(R.string.x_hotspot_title), stringResource(R.string.x_hotspot_hint))
            if (!app.hotspot.supported) {
                Note(stringResource(R.string.x_hotspot_android10), warn = true)
            } else {
                ExtraSwitch(stringResource(R.string.x_hotspot_auto), stringResource(R.string.x_hotspot_auto_hint), auto) { app.extras.setHotspotAuto(it) }
                Text(stringResource(R.string.x_hotspot_pick), fontSize = dims.t(2.6f), color = LocalDriveTheme.current.muted)
                if (networks.isEmpty()) {
                    Note(stringResource(R.string.x_hotspot_no_saved), warn = true)
                } else {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(dims.d(1f))) {
                        for (n in networks) Chip(n, n == ssid, { app.extras.setHotspotSsid(n) }, Modifier.heightIn(min = ExtraTouch))
                    }
                }
                Text(stringResource(R.string.x_hotspot_phone), fontSize = dims.t(2.6f), color = LocalDriveTheme.current.muted)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(dims.d(1f))) {
                    Chip(stringResource(R.string.x_hotspot_any_phone), phone == null, { app.extras.setHotspotPhone(null) }, Modifier.heightIn(min = ExtraTouch))
                    for (p in phones) Chip(p, p == phone, { app.extras.setHotspotPhone(p) }, Modifier.heightIn(min = ExtraTouch))
                }
                if (current != null) Note(stringResource(R.string.x_hotspot_status, current))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                if (app.hotspot.supported && ssid != null) {
                    BigButton(stringResource(R.string.x_hotspot_connect), { app.hotspot.connectNow() }, Modifier.weight(1f).heightIn(min = ExtraTouch), icon = Icons.Filled.Wifi)
                }
                BigButton(
                    stringResource(R.string.x_wifi_settings),
                    {
                        try {
                            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {
                        }
                    },
                    Modifier.weight(1f).heightIn(min = ExtraTouch),
                    primary = false,
                )
            }
        }
    }
}

// ------------------------------------------------------------------------- Challan-bachat

/** "≈ ₹3,000 challan bachaya": warnings obeyed, the fine range, and what it means in km. */
@Composable
internal fun ChallanCard(app: DriveApp, costPerKm: Double?) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val count by app.extras.challanCount.collectAsStateWithLifecycle()
    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1f))) {
            ExtraHeader(Icons.Filled.Savings, stringResource(R.string.x_challan_title), stringResource(R.string.x_challan_count, count))
            Text(
                stringResource(R.string.x_challan_amount, rupees(ChallanSaver.savedLow(count).toDouble()), rupees(ChallanSaver.savedHigh(count).toDouble())),
                fontSize = dims.t(3.4f, min = 20f),
                fontWeight = FontWeight.ExtraBold,
                color = if (count > 0) theme.ok else theme.text,
            )
            ChallanSaver.kmOfDriving(count, costPerKm)?.let { km ->
                Note(stringResource(R.string.x_challan_km, km, rupees(Math.round((costPerKm ?: 0.0) * 100) / 100.0)))
            }
            Note(stringResource(R.string.x_challan_assume))
        }
    }
}

// ------------------------------------------------------------------------------ Fuel diary

/** The voice fuel diary: how to say it, this month's fuel spend and the assumptions. */
@Composable
internal fun FuelDiaryNote(fills: List<FuelFill>) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val monthStart = remember { LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val spend = remember(fills) { FuelDiary.monthSpend(fills, monthStart) }
    val fuel = remember(fills) { Mileage.mainFuel(fills) ?: FuelType.Petrol }
    val price = remember(fills, fuel) { FuelDiary.lastPrice(fills, fuel) ?: FuelDiary.defaultPrice(fuel) }
    val kmpl = remember(fills, fuel) { FuelDiary.lastKmPerLitre(fills, fuel) }
    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(0.8f))) {
            ExtraHeader(Icons.Filled.LocalGasStation, stringResource(R.string.x_diary_title), stringResource(R.string.x_diary_hint))
            Text(stringResource(R.string.x_diary_month, rupees(Math.round(spend).toDouble())), fontSize = dims.t(2.8f, min = 16f), fontWeight = FontWeight.Bold, color = theme.text)
            if (kmpl != null) Note(stringResource(R.string.x_diary_kmpl, "%.1f".format(kmpl)))
            Note(stringResource(R.string.x_diary_assume, rupees(Math.round(price * 10) / 10.0)))
        }
    }
}
