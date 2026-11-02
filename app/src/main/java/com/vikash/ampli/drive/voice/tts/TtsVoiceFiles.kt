package com.vikash.ampli.drive.voice.tts

import android.content.Context
import com.vikash.ampli.drive.voice.VoiceModelPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * One voice of Ampli's bundled engine: a CMU Flite clustergen voice (.flitevox, used unmodified;
 * CMU's free BSD-style licence is embedded in the file, see assets/licenses/flite.txt). [sha512]
 * is the published hash of the festvox.org file (the one Linux distributions check); a download
 * that does not match is thrown away. [languages]: what it can read ([BundledTts.HINDI],
 * [BundledTts.ENGLISH]).
 */
enum class TtsVoiceSpec(val fileName: String, val languages: Set<String>, val bytes: Long, val sha512: String) {
    /**
     * Hindi, female (CMU Indic, speaker "ab", 2017). Built with English recordings too (its
     * secondary language is English), so it also reads English words and sentences, with an
     * Indian accent: one voice for both languages, and Hinglish lines don't switch speakers.
     */
    Hindi(
        "cmu_indic_hin_ab.flitevox",
        setOf(BundledTts.HINDI, BundledTts.ENGLISH),
        30_897_320L,
        "c84a8a2e76ff237a9ab3678acddc8874e20be6693d3f00d08354537dc0ce7938059227024e89d9cd06aa1b16817a8d4c0b3872eae67c8980970226899bf0ed17",
    ),
    ;

    /** Where the Play install-time asset pack (module :voicemodels) puts this voice. */
    val assetPath: String get() = "tts/$fileName"

    /**
     * Download for installs without the asset pack (sideloaded test APKs): the unmodified
     * festvox.org file, mirrored over HTTPS in this app's GitHub release "tts-voices-1" by CI
     * (festvox.org itself serves plain HTTP only).
     */
    val url: String get() = "https://github.com/vikash-parashar/ampli-drive/releases/download/tts-voices-1/$fileName"
}

/**
 * The bundled engine's voice files in filesDir/tts/. Play installs copy them from the
 * install-time asset pack on first start (no network); sideloaded APKs download them once, in the
 * background, with the same back-off as the offline speech models ([VoiceModelPlan]). Nothing
 * for the driver to do.
 */
class TtsVoiceFiles(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val dir = File(appContext.filesDir, "tts")
    private val prefs = appContext.getSharedPreferences("tts_voices", Context.MODE_PRIVATE)

    sealed interface State {
        data object Missing : State
        data class Working(val percent: Int) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow(if (allInstalled()) State.Ready else State.Missing)

    /** All voices together: Ready once every voice is in place. */
    val state: StateFlow<State> = _state.asStateFlow()

    fun file(spec: TtsVoiceSpec): File = File(dir, spec.fileName)

    fun isInstalled(spec: TtsVoiceSpec): Boolean = file(spec).let { it.isFile && it.length() == spec.bytes }

    fun allInstalled(): Boolean = TtsVoiceSpec.entries.all { isInstalled(it) }

    /** Languages ([BundledTts.HINDI], [BundledTts.ENGLISH]) whose voice is in place. */
    fun installedLanguages(): Set<String> = TtsVoiceSpec.entries.filter { isInstalled(it) }.flatMap { it.languages }.toSet()

    /** A copy or download is running now. */
    val busy: Boolean get() = job?.isActive == true

    fun failures(): Int = prefs.getInt(KEY_FAILURES, 0)

    /**
     * Starts installing what is missing. [automatic] (app start, return home, network back) waits
     * out the back-off after a failure unless [ignoreBackoff]; a tap in Settings always retries.
     */
    @Synchronized
    fun ensure(automatic: Boolean = false, ignoreBackoff: Boolean = false) {
        if (allInstalled()) {
            _state.value = State.Ready
            return
        }
        if (job?.isActive == true) return
        if (automatic && !ignoreBackoff &&
            VoiceModelPlan.backingOff(failures(), prefs.getLong(KEY_FAILED_AT, 0L), System.currentTimeMillis())
        ) {
            return
        }
        job = scope.launch {
            try {
                val missing = TtsVoiceSpec.entries.filter { !isInstalled(it) }
                val total = missing.sumOf { it.bytes }.coerceAtLeast(1L)
                var before = 0L
                for (spec in missing) {
                    install(spec) { done -> _state.value = State.Working(((before + done) * 100 / total).toInt().coerceIn(0, 99)) }
                    before += spec.bytes
                }
                prefs.edit().remove(KEY_FAILED_AT).remove(KEY_FAILURES).apply()
                _state.value = State.Ready
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                prefs.edit().putLong(KEY_FAILED_AT, System.currentTimeMillis()).putInt(KEY_FAILURES, failures() + 1).apply()
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun install(spec: TtsVoiceSpec, progress: (Long) -> Unit) {
        dir.mkdirs()
        val part = File(dir, spec.fileName + ".part")
        part.delete()
        _state.value = State.Working(0)
        // The Play asset pack's copy first (Play has already checked it), else the download.
        val fromAssets = try {
            appContext.assets.open(spec.assetPath).use { copy(it, part, spec.bytes, progress) }
            part.length() == spec.bytes
        } catch (_: IOException) {
            false
        }
        if (!fromAssets) {
            part.delete()
            download(spec, part, progress)
            if (part.length() != spec.bytes || !sha512Hex(part).equals(spec.sha512, ignoreCase = true)) {
                part.delete()
                throw IOException("Checksum mismatch: ${spec.fileName}")
            }
        }
        if (!part.renameTo(file(spec))) {
            part.delete()
            throw IOException("Could not save ${spec.fileName}")
        }
    }

    private suspend fun download(spec: TtsVoiceSpec, to: File, progress: (Long) -> Unit) {
        val conn = URL(spec.url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.use { copy(it, to, spec.bytes, progress) }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun copy(input: InputStream, to: File, limit: Long, progress: (Long) -> Unit) {
        to.outputStream().use { out ->
            val buf = ByteArray(64 * 1024)
            var done = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                done += n
                if (done > limit) throw IOException("Unexpected size")
                progress(done)
            }
        }
    }

    /** Deletes the voices (a later [ensure] installs them again). */
    fun delete() {
        job?.cancel()
        dir.listFiles()?.forEach { it.delete() }
        _state.value = State.Missing
    }

    companion object {
        private const val KEY_FAILED_AT = "failedAt"
        private const val KEY_FAILURES = "failures"

        fun sha512Hex(file: File): String {
            val md = MessageDigest.getInstance("SHA-512")
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
