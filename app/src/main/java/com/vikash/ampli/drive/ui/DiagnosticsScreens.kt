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
                    WakeModel.State.Ready -> "ready (${WakeModel.MODEL_NAME})"
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
    val now by androidx.compose.runtime.produceState(SystemClock.elapsedRealtime()) {
        while (true) {
            delay(1000)
            value = SystemClock.elapsedRealtime()
        }
    }
    var net by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var netRun by remember { mutableIntStateOf(0) }
    LaunchedEffect(netRun) {
        net = NET_HOSTS.map { it to "testing…" }
        net = NET_HOSTS.map { url -> url to withContext(Dispatchers.IO) { probe(url) } }
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
                DiagRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${if (BuildConfig.DEBUG) " test build" else ""}", null)
                DiagRow("Pro", "active=${yes(pro.isPro)} · purchased=${yes(pro.purchased)} · test unlock=${yes(pro.testUnlock)}", pro.isPro)
                DiagRow("Voice style", style.key, null)
                DiagRow("When the car starts", autoStart.mode.key + " · delay ${autoStart.delaySec}s" + (autoStart.btDeviceName?.let { " · BT: $it" } ?: ""), null)
            }
            DiagSection("Map") {
                DiagRow("Style", map.styleUrl ?: "(not started)", null)
                DiagRow("State", map.phase.name + if (map.basic) " · BASIC raster fallback" else "", map.phase == com.vikash.ampli.drive.map.MapStatus.Phase.MapLoaded)
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
            DiagSection("Network") {
                for ((url, result) in net) DiagRow(url.removePrefix("https://").substringBefore('/'), result, result.startsWith("HTTP 2") || result.startsWith("HTTP 3"))
                BigButton(text = stringResource(R.string.action_retry), onClick = { netRun++ }, primary = false)
            }
        }
    }
}

private val NET_HOSTS = listOf(
    "https://tiles.openfreemap.org/styles/liberty",
    "https://tile.openstreetmap.org/0/0/0.png",
    "https://alphacephei.com/vosk/models/",
)

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
