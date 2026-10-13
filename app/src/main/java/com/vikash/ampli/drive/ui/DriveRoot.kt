package com.vikash.ampli.drive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.location.SunTimes
import com.vikash.ampli.drive.prefs.ThemeMode
import com.vikash.ampli.drive.ui.theme.AmpliDriveTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.roundToInt

enum class Screen { Home, Apps, Settings }

@Composable
fun DriveRoot(
    app: DriveApp,
    screen: Screen,
    resumeTick: Int,
    onNavigate: (Screen) -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val themeMode by app.prefs.themeMode.collectAsStateWithLifecycle()
    val gpsState = app.speedTracker.state.collectAsStateWithLifecycle()

    // Only a coarse position matters for sunrise/sunset; avoids recomposing on every fix.
    val coarse by remember {
        derivedStateOf {
            gpsState.value.location?.let {
                Pair((it.latitude * 10).roundToInt() / 10.0, (it.longitude * 10).roundToInt() / 10.0)
            }
        }
    }
    val minute by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000L)
            value = System.currentTimeMillis()
        }
    }
    val night = when (themeMode) {
        ThemeMode.Night -> true
        ThemeMode.Day -> false
        ThemeMode.Auto -> coarse?.let { SunTimes.isNight(minute, it.first, it.second) }
            ?: SunTimes.isNightByClock(LocalTime.now())
    }

    AmpliDriveTheme(night = night) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val unit = maxHeight / 100f
                val dims = Dims(
                    u = unit,
                    uSp = with(density) { unit.toSp().value },
                    compact = maxWidth / maxHeight < 1.72f,
                )
                CompositionLocalProvider(LocalDims provides dims) {
                    when (screen) {
                        Screen.Home -> {
                            // A home screen has nowhere to go back to.
                            BackHandler(enabled = true) {}
                            HomeScreen(
                                app = app,
                                night = night,
                                resumeTick = resumeTick,
                                onOpenApps = { onNavigate(Screen.Apps) },
                                onOpenSettings = { onNavigate(Screen.Settings) },
                                onRequestPermissions = onRequestPermissions,
                            )
                        }
                        Screen.Apps -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Home) }
                            AppsScreen(app = app, onBack = { onNavigate(Screen.Home) })
                        }
                        Screen.Settings -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Home) }
                            SettingsScreen(
                                app = app,
                                resumeTick = resumeTick,
                                onBack = { onNavigate(Screen.Home) },
                                onRequestPermissions = onRequestPermissions,
                            )
                        }
                    }
                }
            }
        }
    }
}
