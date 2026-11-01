package com.vikash.ampli.drive.ui

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.BuildConfig
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.map.MapDiagnostics
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.voice.Listener
import com.vikash.ampli.drive.voice.VoiceService
import com.vikash.ampli.drive.voice.VoskCommandListener
import com.vikash.ampli.drive.voice.WakeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// Both screens are plain lists so the owner can photograph them for support. Values are
// technical on purpose (package names, API levels) and are not translated.

@Composable
private fun DiagSection(title: String, content: @Composable () -> Unit) {
    val dims = LocalDims.current
    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(0.6f))) {
            Text(title, color = LocalDriveTheme.current.accentSoft, fontSize = dims.t(2.8f, min = 16f), fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** One fact. [ok] null = neutral (no icon colour). */
@Composable
private fun DiagRow(label: String, value: String, ok: Boolean? = null, action: String? = null, onAction: () -> Unit = {}) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = if (action != null) 48.dp else 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f)),
    ) {
        Icon(
            when (ok) {
                true -> Icons.Filled.CheckCircle
                false -> Icons.Filled.Error
                null -> Icons.Filled.Info
            },
            contentDescription = null,
            tint = when (ok) {
                true -> theme.ok
                false -> theme.warning
                null -> theme.muted
            },
            modifier = Modifier.size(20.dp),
        )
        Text(label, color = theme.muted, fontSize = dims.t(2.3f, min = 13f), modifier = Modifier.width(190.dp))
        Text(value, color = theme.text, fontSize = dims.t(2.3f, min = 13f), fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        if (action != null) BigButton(text = action, onClick = onAction, primary = false)
    }
}

private fun yes(b: Boolean) = if (b) "yes" else "NO"

/** TTS facts, read once from a short-lived engine. */
private data class TtsInfo(
    val ready: Boolean,
    val engines: List<String>,
    val defaultEngine: String?,
    val hindi: Int,
    val english: Int,
)

private fun langText(code: Int): String = when {
    code >= TextToSpeech.LANG_COUNTRY_AVAILABLE -> "yes"
    code == TextToSpeech.LANG_AVAILABLE -> "yes (language only)"
    code == TextToSpeech.LANG_MISSING_DATA -> "voice data missing"
    else -> "NO"
}

@Composable
private fun rememberTtsInfo(): TtsInfo? {
    val context = LocalContext.current
    var info by remember { mutableStateOf<TtsInfo?>(null) }
    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        val engine = try {
            TextToSpeech(context.applicationContext) { status ->
                val t = tts
                info = if (status == TextToSpeech.SUCCESS && t != null) {
                    TtsInfo(
                        ready = true,
                        engines = t.engines.map { it.name },
                        defaultEngine = t.defaultEngine,
                        hindi = t.isLanguageAvailable(Locale.forLanguageTag("hi-IN")),
                        english = t.isLanguageAvailable(Locale.forLanguageTag("en-IN")),
                    )
                } else {
                    TtsInfo(false, t?.engines?.map { it.name }.orEmpty(), null, TextToSpeech.LANG_NOT_SUPPORTED, TextToSpeech.LANG_NOT_SUPPORTED)
                }
            }
        } catch (_: Exception) {
            info = TtsInfo(false, emptyList(), null, TextToSpeech.LANG_NOT_SUPPORTED, TextToSpeech.LANG_NOT_SUPPORTED)
            null
        }
        tts = engine
        onDispose { engine?.shutdown() }
    }
    return info
}

@Suppress("DEPRECATION")
private fun recognitionServices(context: Context): List<String> = try {
    context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        .map { it.serviceInfo.packageName + "/" + it.serviceInfo.name.substringAfterLast('.') }
} catch (_: Exception) {
    emptyList()
}

private fun openPlayStore(context: Context, pkg: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }
}

/** Settings › Voice › Check voice. */
@Composable
fun VoiceCheckScreen(app: DriveApp, onBack: () -> Unit) {
    val dims = LocalDims.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val model by app.wakeModel.state.collectAsStateWithLifecycle()
    val wake by VoiceService.status.collectAsStateWithLifecycle()
    val always by app.prefs.voiceAlways.collectAsStateWithLifecycle()
    val tts = rememberTtsInfo()
    val hasMic = remember(tick) { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
    val sysRecognizer = remember(tick) { SpeechRecognizer.isRecognitionAvailable(context) }
    val recognizerSetting = remember(tick) {
        try {
            Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
        } catch (_: Exception) {
            null
        }
    }
    val services = remember(tick) { recognitionServices(context) }
    var heard by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
        ScreenHeader(title = stringResource(R.string.vcheck_title), hint = stringResource(R.string.vcheck_hint), onBack = onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
            DiagSection("Microphone and wake word") {
                DiagRow("Mic permission", yes(hasMic), hasMic)
                DiagRow("Always listen", if (always) "on" else "off", null)
                DiagRow("Wake word service", wake.toString(), wake is VoiceService.Status.Listening)
                val modelText = when (val m = model) {
                    WakeModel.State.Missing -> "not downloaded"
                    is WakeModel.State.Downloading -> "downloading ${m.percent}%"
                    WakeModel.State.Unpacking -> "unpacking"
                    WakeModel.State.Ready -> "ready (${app.wakeModel.spec.dirName})"
                    is WakeModel.State.Failed -> "FAILED: ${m.message}"
                }
                DiagRow(
                    "Offline model (Vosk)",
                    modelText + (app.wakeModel.loadError?.let { " · load error: $it" } ?: ""),
                    model == WakeModel.State.Ready,
                    action = if (model == WakeModel.State.Ready || model is WakeModel.State.Downloading || model == WakeModel.State.Unpacking) {
                        null
                    } else {
                        stringResource(R.string.vcheck_download)
                    },
                    onAction = { app.wakeModel.ensure() },
                )
                val hindi by app.hindiModel.state.collectAsStateWithLifecycle()
                DiagRow(
                    "Offline Hindi commands (Vosk)",
                    when (val m = hindi) {
                        WakeModel.State.Missing -> "not downloaded (optional, Settings › Voice)"
                        is WakeModel.State.Downloading -> "downloading ${m.percent}%"
                        WakeModel.State.Unpacking -> "unpacking"
                        WakeModel.State.Ready -> "ready (${app.hindiModel.spec.dirName})"
                        is WakeModel.State.Failed -> "FAILED: ${m.message}"
                    } + (app.hindiModel.loadError?.let { " · load error: $it" } ?: ""),
                    if (hindi == WakeModel.State.Ready) true else null,
                )
            }
            DiagSection("Speech recognition") {
                DiagRow("SpeechRecognizer available", yes(sysRecognizer), sysRecognizer)
                DiagRow("Recognizer setting", recognizerSetting ?: "(none)", null)
                DiagRow("Recognition services", services.joinToString(", ").ifEmpty { "(none installed)" }, services.isNotEmpty())
                DiagRow(
                    "Used for commands",
                    when {
                        sysRecognizer -> "system recognizer"
                        model == WakeModel.State.Ready -> "offline model (English commands)"
                        else -> "NONE: download the offline model"
                    },
                    sysRecognizer || model == WakeModel.State.Ready,
                )
            }
            DiagSection("Text to speech") {
                if (tts == null) {
                    DiagRow("Engine", "starting…", null)
                } else {
                    DiagRow(
                        "Engines installed",
                        tts.engines.joinToString(", ").ifEmpty { "(none)" },
                        tts.engines.isNotEmpty(),
                        action = if (tts.engines.isEmpty() || !tts.ready) stringResource(R.string.vcheck_get_tts) else null,
                        onAction = { openPlayStore(context, "com.google.android.tts") },
                    )
                    DiagRow("Default engine", tts.defaultEngine ?: "(none)", tts.ready)
                    DiagRow(
                        "Hindi voice (hi-IN)",
                        langText(tts.hindi),
                        tts.hindi >= TextToSpeech.LANG_AVAILABLE,
                        action = if (tts.ready && tts.hindi < TextToSpeech.LANG_AVAILABLE) stringResource(R.string.vcheck_tts_settings) else null,
                        onAction = {
                            try {
                                context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) {
                                openPlayStore(context, "com.google.android.tts")
                            }
                        },
                    )
                    DiagRow("English voice (en-IN)", langText(tts.english), tts.english >= TextToSpeech.LANG_AVAILABLE)
                }
            }
            DiagSection("Tests") {
                Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                    BigButton(text = stringResource(R.string.vcheck_say_test), onClick = {
                        scope.launch {
                            app.speaker.say("Hello, this is Ampli Car Hub.", "en-IN")
                            app.speaker.say("नमस्ते, यह एम्पली कार हब की आवाज़ है।", "hi")
                        }
                    })
                    BigButton(text = if (listening) stringResource(R.string.vcheck_listening) else stringResource(R.string.vcheck_listen_test), onClick = {
                        if (listening) return@BigButton
                        listening = true
                        heard = null
                        scope.launch {
                            val result = app.voice.withMic {
                                Listener(app, VoskCommandListener(app)).listen(app.prefs.language.value, onPartial = { heard = "… $it" })
                            }
                            heard = when (result) {
                                is Listener.Result.Heard -> "Heard: \"${result.text}\"" + if (result.offline) " (offline model)" else " (system recognizer)"
                                Listener.Result.Silence -> "Nothing heard"
                                Listener.Result.Unavailable -> "No recognizer available"
                            }
                            listening = false
                            tick++
                        }
                    }, primary = false)
                }
                heard?.let { DiagRow("Listen test", it, it.startsWith("Heard")) }
                Text(stringResource(R.string.vcheck_listen_hint), color = LocalDriveTheme.current.muted, fontSize = dims.t(2.1f, min = 12f))
            }
        }
    }
}

/** Settings › About › Diagnostics. */
@Composable
fun DiagnosticsScreen(app: DriveApp, onBack: () -> Unit) {
    val dims = LocalDims.current
    val context = LocalContext.current
    val pro by app.prefs.pro.collectAsStateWithLifecycle()
    val map by MapDiagnostics.status.collectAsStateWithLifecycle()
    val textureMode by app.prefs.mapTextureMode.collectAsStateWithLifecycle()
    val gps by app.speedTracker.state.collectAsStateWithLifecycle()
    val obd by app.obd.state.collectAsStateWithLifecycle()
    val autoStart by app.prefs.autoStart.collectAsStateWithLifecycle()
    val style by app.prefs.voiceStyle.collectAsStateWithLifecycle()
    val navPrompts by app.prefs.navPrompts.collectAsStateWithLifecycle()
    val navDebug by app.navPrompter.debug.collectAsStateWithLifecycle()
    val navTurn by com.vikash.ampli.drive.nav.NavTurnRepository.turn.collectAsStateWithLifecycle()
    val navApp by app.prefs.navApp.collectAsStateWithLifecycle()
    val inApp by app.navigator.state.collectAsStateWithLifecycle()
    val navDiag by app.navigator.diagnostics.collectAsStateWithLifecycle()
    val now by androidx.compose.runtime.produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            delay(1000)
            value = SystemClock.elapsedRealtime()
        }
    }
    var net by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var netRun by remember { mutableIntStateOf(0) }
    LaunchedEffect(netRun) {
        net = netHosts().map { it to "testing…" }
        net = netHosts().map { url -> url to withContext(Dispatchers.IO) { probe(url) } }
    }
    val ramGb = remember {
        val mi = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(mi)
        mi.totalMem / 1_073_741_824.0
    }
    val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
        ScreenHeader(title = stringResource(R.string.diag_title), hint = stringResource(R.string.diag_screen_hint), onBack = onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
            DiagSection("Unit") {
                // The About page of these units often shows a made-up version; SDK_INT is the real one.
                DiagRow("Android (real)", "API ${Build.VERSION.SDK_INT} · release ${Build.VERSION.RELEASE}", null)
                DiagRow("Build", "${Build.DISPLAY} · patch ${Build.VERSION.SECURITY_PATCH}", null)
                DiagRow("Maker / model", "${Build.MANUFACTURER} / ${Build.MODEL} (${Build.DEVICE})", null)
                DiagRow("Hardware / board", "${Build.HARDWARE} / ${Build.BOARD}", null)
                DiagRow("ABIs", Build.SUPPORTED_ABIS.joinToString(", "), null)
                DiagRow("RAM", String.format(Locale.US, "%.1f GB", ramGb), null)
                DiagRow("Screen", "${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels} @ ${context.resources.displayMetrics.densityDpi} dpi", null)
            }
            DiagSection("App") {
                DiagRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${if (BuildConfig.TEST_BUILD) " test build" else ""}", null)
                DiagRow("Pro", "active=${yes(pro.isPro)} · purchased=${yes(pro.purchased)} · test unlock=${yes(pro.testUnlock)}", pro.isPro)
                DiagRow("Voice style", style.key, null)
                DiagRow("When the car starts", autoStart.mode.key + " · delay ${autoStart.delaySec}s" + (autoStart.btDeviceName?.let { " · BT: $it" } ?: ""), null)
            }
            DiagSection("Start-up") {
                // Process start to the first Home frame (cold), or activity start to it (warm).
                val (coldMs, warmMs) = remember { com.vikash.ampli.drive.system.StartupTiming.saved(context) }
                val now = com.vikash.ampli.drive.system.StartupTiming.last
                DiagRow(
                    "This start",
                    now?.let { "${it.ms} ms (${if (it.kind == com.vikash.ampli.drive.system.StartupTiming.Kind.Cold) "cold: process start to first Home frame" else "warm: activity start to first Home frame"})" } ?: "(Home not shown yet)",
                    now?.let { it.ms < 3000 },
                )
                DiagRow("Last cold start", if (coldMs > 0) "$coldMs ms" else "(not measured yet)", if (coldMs > 0) coldMs < 3000 else null)
                if (warmMs > 0) DiagRow("Last warm start", "$warmMs ms", warmMs < 1500)
                DiagRow("Low-memory unit", if (app.lowEnd) "yes: map and music card load after the first frame" else "no", null)
                // Lite mode: no decorative motion, map at 15 fps without 3D. Auto = on for low-memory units.
                var liteChoice by remember { mutableStateOf(app.prefs.liteMode) }
                val liteNext = com.vikash.ampli.drive.prefs.LiteMode.resolve(liteChoice, app.lowEnd)
                DiagRow(
                    "Lite mode",
                    "${liteChoice.key} · now ${if (com.vikash.ampli.drive.prefs.LiteMode.enabled) "on" else "off"}" +
                        if (liteNext != com.vikash.ampli.drive.prefs.LiteMode.enabled) " · ${if (liteNext) "on" else "off"} after a restart" else "",
                    null,
                    action = stringResource(R.string.diag_switch),
                    onAction = {
                        liteChoice = com.vikash.ampli.drive.prefs.LiteMode.next(liteChoice)
                        app.prefs.liteMode = liteChoice
                    },
                )
                DiagRow("Memory released", "${com.vikash.ampli.drive.system.MemoryTrim.trims} times this run", null)
            }
            // Why the welcome did or did not play after a start (power cut, ACC on, boot).
            DiagSection("Start log") {
                val log by app.startLog.entries.collectAsStateWithLifecycle()
                val isHome = remember { com.vikash.ampli.drive.system.Launchers.isDefaultHome(context) }
                DiagRow("Default home app", if (isHome) "Car Hub" else "NO: another launcher (Car Hub may not show after a power cut)", isHome)
                DiagRow("Boot count / uptime", "${remember { bootCountText(context) }} · up ${SystemClock.elapsedRealtime() / 60_000} min · slept ${(SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()) / 60_000} min", null)
                if (log.isEmpty()) DiagRow("Signals", "(none yet)", null)
                val fmt = remember { java.text.SimpleDateFormat("dd MMM HH:mm:ss", Locale.US) }
                for (e in log) {
                    DiagRow(
                        fmt.format(java.util.Date(e.atMs)),
                        e.trigger + " · greeted " + yes(e.greeted) + (if (e.reason.isNotBlank()) " · " + e.reason else ""),
                        e.greeted,
                    )
                }
            }
            DiagSection("Phone over Bluetooth") {
                val devices by app.phoneBt.devices.collectAsStateWithLifecycle()
                val autoPlay by app.phoneBt.lastAutoPlay.collectAsStateWithLifecycle()
                val vendor = remember { com.vikash.ampli.drive.bluetooth.VendorBt.find(context) }
                DiagRow("Paired devices", if (devices.isEmpty()) "(none visible to apps)" else devices.joinToString { it.name + if (it.connected) " (connected)" else "" }, devices.any { it.connected })
                DiagRow("Unit's Bluetooth app", vendor?.let { it.label + " · " + it.packageName + "/" + it.activityName.substringAfterLast('.') } ?: "(not found)", vendor != null)
                vendor?.let { v ->
                    val acts = remember(v) { com.vikash.ampli.drive.bluetooth.VendorBt.exportedActivities(context, v.packageName) }
                    DiagRow("Its music screen", com.vikash.ampli.drive.bluetooth.PhoneBtRules.musicActivity(acts)?.substringAfterLast('.') ?: "(none exported)", null)
                }
                DiagRow("Auto-play last time", autoPlay ?: "(no phone connected since start)", null)
            }
            // Vendor package names, for a support photo.
            DiagSection("Unit apps") {
                val rows by androidx.compose.runtime.produceState<List<com.vikash.ampli.drive.system.UnitApps.Row>?>(null) {
                    value = withContext(Dispatchers.IO) { com.vikash.ampli.drive.system.UnitApps.list(context) }
                }
                val list = rows
                when {
                    list == null -> DiagRow("Apps", "reading…", null)
                    list.isEmpty() -> DiagRow("Apps", "(none matched)", null)
                    else -> for (r in list) DiagRow(r.kind, r.label + " · " + r.packageName, null)
                }
            }
            DiagSection("Map") {
                DiagRow("Style", map.styleUrl ?: "(not started)", null)
                DiagRow("State", map.phase.name + (if (map.basic) " · own fallback style" else "") + (if (map.unavailable) " · TILES UNAVAILABLE" else ""), map.phase == com.vikash.ampli.drive.map.MapStatus.Phase.MapLoaded)
                DiagRow("Frame drawn", yes(map.drewFrame), map.drewFrame)
                DiagRow(
                    "Drawing surface",
                    if (textureMode) "TextureView" else "GLSurfaceView",
                    null,
                    action = stringResource(R.string.diag_switch),
                    onAction = { app.prefs.setMapTextureMode(!textureMode) },
                )
                DiagRow("Map errors", "${map.errorCount} (network ${map.netErrorCount})", map.errorCount == 0)
                map.lastError?.let { DiagRow("Last error", it, false) }
            }
            DiagSection("Location") {
                DiagRow("Permission", "fine=${yes(fine)} · coarse=${yes(coarse)}", fine)
                DiagRow("Providers", app.speedTracker.providers().joinToString(", ").ifEmpty { "(none)" }, gps.hasGps)
                DiagRow("GPS switched on", yes(gps.providerEnabled), gps.providerEnabled)
                DiagRow("Satellites used / seen", "${gps.satellitesUsed} / ${gps.satellitesInView}", gps.satellitesUsed > 0)
                DiagRow("Position from", gps.source.name, gps.source == com.vikash.ampli.drive.location.LocationSource.Gps)
                val age = if (gps.lastFixElapsedMs > 0) "${(now - gps.lastFixElapsedMs) / 1000} s ago" else "never"
                DiagRow("Last speed fix", "$age · ${gps.lastFixProvider ?: "-"} · hasSpeed=${yes(gps.lastFixHasSpeed)}", gps.lastFixElapsedMs > 0 && now - gps.lastFixElapsedMs < 5000)
                DiagRow(
                    "Speed / trip avg",
                    "${gps.speedKmh?.let { String.format(Locale.US, "%.0f", it) } ?: "--"} / ${gps.avgKmh?.let { String.format(Locale.US, "%.0f", it) } ?: "--"} km/h",
                    null,
                )
                DiagRow("OBD (RPM)", obd.phase.name + (obd.rpm?.let { " · ${it.toInt()} rpm" } ?: ""), obd.phase == com.vikash.ampli.drive.obd.ObdState.Phase.Connected)
            }
            DiagSection("Navigation") {
                DiagRow("Navigate with", navApp.key, null)
                DiagRow(
                    "Routing server",
                    navDiag.server + if (navDiag.demoServer) " (OSRM public demo: testing only)" else "",
                    !navDiag.demoServer,
                )
                val wall = System.currentTimeMillis()
                DiagRow(
                    "Last route",
                    navDiag.lastStatus + if (navDiag.lastAtMs > 0) " · ${(wall - navDiag.lastAtMs) / 1000} s ago · took ${navDiag.lastTookMs} ms" else "",
                    if (navDiag.lastAtMs == 0L) null else !navDiag.lastStatus.startsWith("FAILED"),
                )
                DiagRow(
                    "Route",
                    "${navDiag.steps} steps · " + String.format(Locale.US, "%.1f km", navDiag.routeKm) +
                        " · speed limits ${if (navDiag.hasSpeedLimits) "yes" else "none"}",
                    null,
                )
                DiagRow("Off route / reroutes", "${navDiag.offRouteCount} / ${navDiag.rerouteCount}", null)
                val p = inApp.progress
                DiagRow(
                    "Now",
                    inApp.phase.name + (inApp.failure?.let { " · $it" } ?: "") +
                        (p?.let { " · step ${it.stepIndex + 1} in ${it.distanceToStepM.toInt()} m · ${it.distanceFromRouteM.toInt()} m from line" } ?: ""),
                    null,
                )
            }
            DiagSection("Navigation prompts") {
                val listener = com.vikash.ampli.drive.service.DriveNotificationListener.connected
                DiagRow("Prompts", "on=${yes(navPrompts)} · style ${style.key}", null)
                DiagRow("Notification access", yes(listener), listener)
                DiagRow("Maps navigating", yes(navTurn != null), null)
                val d = navDebug
                if (d == null) {
                    DiagRow("Last Maps notification", "(none yet)", null)
                } else {
                    val wall = System.currentTimeMillis()
                    DiagRow("Last Maps notification", "\"${d.title}\" / \"${d.text ?: "-"}\" · ${(wall - d.atMs) / 1000} s ago", null)
                    DiagRow(
                        "Parsed",
                        d.parsed?.let { p -> p.maneuver.name + " · " + (p.distanceM?.let { "$it m" } ?: "no distance") + (p.exitNumber?.let { " · exit $it" } ?: "") }
                            ?: "not recognised",
                        d.parsed != null,
                    )
                    DiagRow("Last spoken", d.lastSpoken ?: "-", null)
                }
            }
            DiagSection("Network") {
                for ((url, result) in net) DiagRow(url.removePrefix("https://").substringBefore('/'), result, result.startsWith("HTTP 2") || result.startsWith("HTTP 3"))
                BigButton(text = stringResource(R.string.action_retry), onClick = { netRun++ }, primary = false)
            }
        }
    }
}

private fun bootCountText(context: Context): String = try {
    "boot #" + Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
} catch (_: Exception) {
    "boot # unknown"
}

/** The servers this build talks to (RemoteConfig over BuildConfig); unconfigured ones are skipped. */
private fun netHosts(): List<String> {
    val cfg = com.vikash.ampli.drive.config.RemoteConfig.current()
    return listOfNotNull(
        "https://tiles.openfreemap.org/styles/liberty",
        cfg.mapFallbackStyleUrl.takeIf { it.isNotBlank() },
        "https://alphacephei.com/vosk/models/",
        // In-app navigation routing (a tiny route in Delhi).
        cfg.routingBaseUrl.takeIf { it.isNotBlank() }?.let { it.trimEnd('/') + "/route/v1/driving/77.2090,28.6139;77.2167,28.6200?overview=false" },
        cfg.geocoderUrl.takeIf { it.isNotBlank() }?.let { it + (if (it.contains('?')) "&" else "?") + "q=Rohtak&limit=1" },
    )
}

/** One HTTPS request, so TLS (old CA store) and DNS problems show by name. */
private fun probe(url: String): String {
    val started = SystemClock.elapsedRealtime()
    return try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.requestMethod = "GET"
        c.setRequestProperty("User-Agent", "AmpliCarHub/${BuildConfig.VERSION_NAME} (diagnostics)")
        try {
            val code = c.responseCode
            "HTTP $code in ${SystemClock.elapsedRealtime() - started} ms"
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        "${e.javaClass.simpleName}: ${e.message?.take(120)}"
    }
}
