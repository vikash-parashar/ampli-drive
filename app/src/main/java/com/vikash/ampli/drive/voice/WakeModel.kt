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
     * ~42 MB zipped. Play installs carry it in the install-time asset pack; other installs fetch
     * it by themselves for Hindi-family (or unset) app languages (VoiceModelPlan).
     */
    Hindi(
        "vosk-model-small-hi-0.22",
        "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
        42L * 1024 * 1024,
    ),
    ;

    /** Where the Play install-time asset pack (module :voicemodels) puts this model's zip. */
    val assetPath: String get() = "vosk/$dirName.zip"
}

/**
 * A small offline Vosk model in filesDir/vosk/<model>/. Play installs unpack it from the
 * install-time asset pack (no network); sideloaded APKs, which carry no models, download it once.
 * [VoskModelSpec.EnglishIndia] spots "Hey Ampli"; [VoskModelSpec.Hindi] hears Hindi/Haryanvi
 * commands. [VoiceModels] decides which to fetch on first start. Progress shows in Settings › Voice
 * and on Home ("Getting Ampli ready… 40%").
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

    /** 0..100 while [State.Unpacking] (bytes of the zip read so far). */
    private val _unpackPercent = MutableStateFlow(0)
    val unpackPercent: StateFlow<Int> = _unpackPercent.asStateFlow()

    /** True while the current job unpacks the copy shipped in the Play asset pack. */
    @Volatile var fromAssets: Boolean = false
        private set

    /** A download or unpack is running now. */
    val busy: Boolean get() = job?.isActive == true

    private val prefs = appContext.getSharedPreferences("wake_model", Context.MODE_PRIVATE)
    private val failedKey = "${spec.dirName}.failedAt"
    private val failCountKey = "${spec.dirName}.failures"
    private val removedKey = "${spec.dirName}.removedByUser"

    /** The driver removed this model in Settings: automatic downloads leave it alone. */
    fun removedByUser(): Boolean = prefs.getBoolean(removedKey, false)

    private fun markFailed() {
        prefs.edit()
            .putLong(failedKey, System.currentTimeMillis())
            .putInt(failCountKey, failures() + 1)
            .apply()
    }

    /** Failed downloads or loads in a row (0 after a success). */
    fun failures(): Int = prefs.getInt(failCountKey, 0)

    /**
     * True while an [automatic] retry should wait after a failed download or load: 30 s after
     * the first failure, growing to [RETRY_BACKOFF_MS] (VoiceModelPlan.retryDelayMs).
     */
    private fun backingOff(now: Long = System.currentTimeMillis()): Boolean =
        VoiceModelPlan.backingOff(failures(), prefs.getLong(failedKey, 0L), now)

    /**
     * Starts the download unless it is installed or already running. [automatic] (app start,
     * every return home, a voice attempt): skipped during the back-off after a failure, so a
     * model that will not download or load is not fetched again on every onResume, and never
     * after the driver removed it. [ignoreBackoff]: a network just came back (still automatic, so
     * a removed model stays removed). A tap in Settings passes automatic = false and always retries.
     *
     * The copy in the Play asset pack is unpacked first when there is one; the download is the
     * fallback (sideloaded APKs, or a damaged pack).
     */
    @Synchronized
    fun ensure(automatic: Boolean = false, ignoreBackoff: Boolean = false) {
        if (isInstalled()) {
            _state.value = State.Ready
            return
        }
        if (job?.isActive == true) return
        if (automatic && (removedByUser() || (!ignoreBackoff && backingOff()))) return
        if (!automatic) prefs.edit().remove(removedKey).apply()
        job = scope.launch {
            try {
                if (!installFromAssets()) download()
                prefs.edit().remove(failedKey).remove(failCountKey).apply()
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

        fromAssets = false
        zip.inputStream().use { unpack(it, zip.length(), wantSha = null) }
        zip.delete()
    }

    /**
     * Unpacks the zip shipped in the Play install-time asset pack (assets/vosk/<model>.zip), if
     * this install has one. False when there is none (sideloaded APK, or a local debug build) or
     * when the copy is damaged: the download takes over then.
     */
    private suspend fun installFromAssets(): Boolean {
        val assets = appContext.assets
        val length = try {
            assets.openFd(spec.assetPath).use { it.length }
        } catch (_: Exception) {
            // Compressed or absent: open() below tells which.
            -1L
        }
        val input = try {
            assets.open(spec.assetPath)
        } catch (_: IOException) {
            return false
        }
        root.mkdirs()
        fromAssets = true
        return try {
            input.use { unpack(it, length.takeIf { l -> l > 0 } ?: spec.approxBytes, wantSha = spec.sha256) }
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            File(root, "${spec.dirName}.tmp").deleteRecursively()
            false
        } finally {
            fromAssets = false
        }
    }

    /**
     * Unpacks a model zip into [modelDir] (through a .tmp folder, moved in place at the end).
     * [totalBytes]: the zip's size, for [unpackPercent]. [wantSha]: when set, the zip's SHA-256
     * must match or nothing is installed.
     */
    private suspend fun unpack(source: java.io.InputStream, totalBytes: Long, wantSha: String?) {
        _unpackPercent.value = 0
        _state.value = State.Unpacking
        val tmp = File(root, "${spec.dirName}.tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        val base = tmp.canonicalPath + File.separator
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val counted = ProgressInputStream(java.security.DigestInputStream(source, md)) { read ->
            _unpackPercent.value = (read * 100 / totalBytes.coerceAtLeast(1L)).toInt().coerceIn(0, 99)
        }
        val buffered = counted.buffered(64 * 1024)
        ZipInputStream(buffered).use { zin ->
            while (true) {
                currentCoroutineContext().ensureActive()
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
            // The zip's central directory follows the last entry: read it too, so the checksum
            // covers the whole file.
            if (wantSha != null) {
                val rest = ByteArray(64 * 1024)
                while (buffered.read(rest) >= 0) Unit
            }
        }
        if (wantSha != null) {
            val got = md.digest().joinToString("") { "%02x".format(it) }
            if (!got.equals(wantSha, ignoreCase = true)) {
                tmp.deleteRecursively()
                throw IOException("Checksum mismatch")
            }
        }
        if (!File(tmp, "am").exists() && !File(tmp, "conf").exists()) throw IOException("Model incomplete")
        modelDir.deleteRecursively()
        if (!tmp.renameTo(modelDir)) throw IOException("Cannot move model")
        File(modelDir, READY_MARKER).writeText("ok")
        _unpackPercent.value = 100
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

        /** Longest wait before an automatic retry after failed downloads or loads. */
        const val RETRY_BACKOFF_MS = VoiceModelPlan.MAX_RETRY_MS

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

/** Counts the bytes read through it, for unpack progress. */
private class ProgressInputStream(input: java.io.InputStream, private val onRead: (Long) -> Unit) : java.io.FilterInputStream(input) {
    private var total = 0L
    private var lastReported = 0L

    private fun add(n: Long) {
        if (n <= 0) return
        total += n
        if (total - lastReported >= 256 * 1024) {
            lastReported = total
            onRead(total)
        }
    }

    override fun read(): Int = super.read().also { if (it >= 0) add(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { add(it.toLong()) }

    override fun skip(n: Long): Long = super.skip(n).also { add(it) }
}
