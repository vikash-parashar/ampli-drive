package com.vikash.ampli.drive.voice

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * The small offline Vosk model used only to spot "Hey Ampli". It is about 36 MB, so it is not
 * in the APK: it downloads once, the first time the driver switches the wake word on, into
 * filesDir/vosk/. Progress shows in Settings › Voice.
 */
class WakeModel(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    sealed interface State {
        data object Missing : State
        data class Downloading(val percent: Int) : State
        data object Unpacking : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val root = File(appContext.filesDir, "vosk")

    /** Folder handed to org.vosk.Model. */
    val modelDir = File(root, MODEL_NAME)

    private val _state = MutableStateFlow(if (isInstalled()) State.Ready else State.Missing)
    val state: StateFlow<State> = _state.asStateFlow()

    fun isInstalled(): Boolean = File(modelDir, READY_MARKER).exists()

    /** Starts the download unless it is installed or already running. */
    fun ensure() {
        if (isInstalled()) {
            _state.value = State.Ready
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                download()
                _state.value = State.Ready
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun download() {
        root.mkdirs()
        val zip = File(appContext.cacheDir, "$MODEL_NAME.zip")
        _state.value = State.Downloading(0)
        val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: APPROX_BYTES
            conn.inputStream.use { input ->
                zip.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = (done * 100 / total).toInt().coerceIn(0, 99)
                        if (pct != lastPct) {
                            lastPct = pct
                            _state.value = State.Downloading(pct)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        _state.value = State.Unpacking
        val tmp = File(root, "$MODEL_NAME.tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        val base = tmp.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                // The zip has one top folder (vosk-model-small-en-in-0.4/); drop it.
                val rel = entry.name.substringAfter('/', "")
                if (rel.isEmpty()) continue
                val out = File(tmp, rel)
                // Zip-slip guard.
                if (!out.canonicalPath.startsWith(base)) throw IOException("Bad zip entry")
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zin.copyTo(it) }
                }
            }
        }
        zip.delete()
        if (!File(tmp, "am").exists() && !File(tmp, "conf").exists()) throw IOException("Model incomplete")
        modelDir.deleteRecursively()
        if (!tmp.renameTo(modelDir)) throw IOException("Cannot move model")
        File(modelDir, READY_MARKER).writeText("ok")
    }

    /** Frees the 36 MB, for example when the driver switches the wake word off for good. */
    fun delete() {
        job?.cancel()
        root.deleteRecursively()
        _state.value = State.Missing
    }

    companion object {
        const val MODEL_NAME = "vosk-model-small-en-in-0.4"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip"
        private const val APPROX_BYTES = 36L * 1024 * 1024
        private const val READY_MARKER = ".ampli-ready"
    }
}
