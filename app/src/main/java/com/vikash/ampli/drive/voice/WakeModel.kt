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
 * One downloadable Vosk model (Apache-2.0, alphacephei.com). [sha256]: lowercase hex of the zip;
 * when set, a download that does not match is thrown away. Null skips the check (until the
 * owner supplies the hashes).
 */
enum class VoskModelSpec(val dirName: String, val url: String, val approxBytes: Long, val sha256: String? = null) {
    /** Wake word + English command fallback. */
    EnglishIndia(
        "vosk-model-small-en-in-0.4",
        "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
        36L * 1024 * 1024,
    ),

    /**
     * Hindi commands (and Haryanvi said with Hindi words) on head units without Google speech.
     * ~42 MB zipped, so it is never bundled: fetched by itself for Hindi-family app languages when
     * there is no working system recogniser, or from Settings › Voice when the driver asks.
     */
    Hindi(
        "vosk-model-small-hi-0.22",
        "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
        42L * 1024 * 1024,
    ),
}

/**
 * A small offline Vosk model, downloaded once into filesDir/vosk/<model>/ (never in the APK).
 * [VoskModelSpec.EnglishIndia] spots "Hey Ampli" and is fetched the first time the driver switches
 * the wake word on; [VoskModelSpec.Hindi] hears Hindi/Haryanvi commands and is fetched when a
 * Hindi-family app language has no working system recogniser (or on demand).
 * Progress shows in Settings › Voice.
 */
class WakeModel(context: Context, val spec: VoskModelSpec = VoskModelSpec.EnglishIndia) {
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
    val modelDir = File(root, spec.dirName)

    private val _state = MutableStateFlow(if (isInstalled()) State.Ready else State.Missing)
    val state: StateFlow<State> = _state.asStateFlow()

    fun isInstalled(): Boolean = File(modelDir, READY_MARKER).exists()

    private val prefs = appContext.getSharedPreferences("wake_model", Context.MODE_PRIVATE)
    private val failedKey = "${spec.dirName}.failedAt"
    private val removedKey = "${spec.dirName}.removedByUser"

    /** The driver removed this model in Settings: automatic downloads leave it alone. */
    fun removedByUser(): Boolean = prefs.getBoolean(removedKey, false)

    private fun markFailed() {
        prefs.edit().putLong(failedKey, System.currentTimeMillis()).apply()
    }

    /** True while an [automatic] retry should wait after a failed download or load. */
    private fun backingOff(now: Long = System.currentTimeMillis()): Boolean {
        val at = prefs.getLong(failedKey, 0L)
        return at > 0L && now - at in 0 until RETRY_BACKOFF_MS
    }

    /**
     * Starts the download unless it is installed or already running. [automatic] (app start,
     * every return home, a voice attempt): skipped for [RETRY_BACKOFF_MS] after a failure, so a
     * model that will not download or load is not fetched again on every onResume. A tap in
     * Settings passes false and always retries.
     */
    fun ensure(automatic: Boolean = false) {
        if (isInstalled()) {
            _state.value = State.Ready
            return
        }
        if (job?.isActive == true) return
        if (automatic && (backingOff() || removedByUser())) return
        if (!automatic) prefs.edit().remove(removedKey).apply()
        job = scope.launch {
            try {
                download()
                prefs.edit().remove(failedKey).apply()
                _state.value = State.Ready
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                markFailed()
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun download() {
        root.mkdirs()
        val zip = File(appContext.cacheDir, "${spec.dirName}.zip")
        _state.value = State.Downloading(0)
        val conn = URL(spec.url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: spec.approxBytes
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
        spec.sha256?.let { want ->
            val got = sha256Hex(zip)
            if (!got.equals(want, ignoreCase = true)) {
                zip.delete()
                throw IOException("Checksum mismatch")
            }
        }

        _state.value = State.Unpacking
        val tmp = File(root, "${spec.dirName}.tmp")
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

    /**
     * Frees this model's space (the other model stays). [byUser]: the driver tapped Remove, so
     * automatic downloads do not bring it back (a tap on Download does).
     */
    fun delete(byUser: Boolean = false) {
        if (byUser) prefs.edit().putBoolean(removedKey, true).apply()
        job?.cancel()
        synchronized(this) {
            loaded?.close()
            loaded = null
        }
        modelDir.deleteRecursively()
        File(root, "${spec.dirName}.tmp").deleteRecursively()
        _state.value = State.Missing
    }

    @Volatile private var loaded: org.vosk.Model? = null
    private val loadLock = kotlinx.coroutines.sync.Mutex()

    /** Why the last [loadModel] failed (shown on Settings › Voice › Check voice), or null. */
    @Volatile var loadError: String? = null
        private set

    /**
     * The loaded model, shared by the wake word and the offline command fallback (one copy in
     * memory). Loaded once on first use; never closed while the app runs, except by [delete].
     * A model that fails to load is deleted so the next [ensure] downloads it again.
     */
    suspend fun loadModel(): org.vosk.Model? {
        loaded?.let { return it }
        if (!isInstalled()) return null
        return loadLock.withLockCompat {
            loaded ?: kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    org.vosk.LibVosk.setLogLevel(org.vosk.LogLevel.WARNINGS)
                    org.vosk.Model(modelDir.absolutePath).also {
                        loaded = it
                        loadError = null
                    }
                } catch (t: Throwable) {
                    loadError = t.message ?: t.javaClass.simpleName
                    // A broken unpack: remove it so the next ensure() fetches a fresh copy, but
                    // only after the back-off for automatic retries (no download loop).
                    markFailed()
                    File(modelDir, READY_MARKER).delete()
                    _state.value = State.Failed(loadError ?: "load failed")
                    null
                }
            }
        }
    }

    private suspend fun <T> kotlinx.coroutines.sync.Mutex.withLockCompat(block: suspend () -> T): T {
        lock()
        try {
            return block()
        } finally {
            unlock()
        }
    }

    companion object {
        private const val READY_MARKER = ".ampli-ready"

        /** Wait before an automatic retry after a failed download or load. */
        const val RETRY_BACKOFF_MS = 6 * 60 * 60_000L

        fun sha256Hex(file: File): String {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
