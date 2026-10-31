package com.vikash.ampli.drive.feedback

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.vikash.ampli.drive.BuildConfig
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.i18n.AppLanguages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Help & feedback: sends reports to the Ampli owner server when [BuildConfig.OWNER_API_URL] is
 * set, keeps them in an offline queue (prefs) until the unit is online, and falls back to an
 * email to [SUPPORT_EMAIL] when no server is configured.
 */
class FeedbackRepository(private val app: DriveApp) {
    private val sp = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watching = false

    val hasBackend: Boolean get() = BuildConfig.OWNER_API_URL.isNotBlank()
    private val client: FeedbackClient? get() = if (hasBackend) FeedbackClient(BuildConfig.OWNER_API_URL) else null

    sealed interface Outcome {
        data class Sent(val ref: String) : Outcome
        /** Offline or the server failed: kept and sent later. */
        data object Queued : Outcome
        /** HTTP 429: the daily limit; kept and sent later. */
        data object RateLimited : Outcome
        /** The server refused it (4xx): nothing more to do. */
        data object Rejected : Outcome
    }

    /** A picked screenshot or the app log, copied into the app's own files. */
    data class Attachment(val file: File, val mime: String)

    fun meta(): ReportMeta = ReportMeta(
        installId = app.prefs.installId,
        appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        device = (Build.MANUFACTURER + " " + Build.MODEL).trim(),
        os = FeedbackJson.osLabel(Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
        locale = AppLanguages.current(app, app.prefs.language.value).ifEmpty { Locale.getDefault().toLanguageTag() },
        pro = app.prefs.pro.value.isPro,
    )

    fun queued(): List<QueuedReport> = FeedbackQueue.decode(sp.getString(KEY_QUEUE, null))

    private fun save(list: List<QueuedReport>) {
        sp.edit().putString(KEY_QUEUE, FeedbackQueue.encode(list)).apply()
    }

    /** Queues the report and tries to send everything waiting. */
    suspend fun submit(draft: ReportDraft, attachments: List<Attachment>): Outcome = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val dir = File(app.filesDir, "feedback/$id").apply { mkdirs() }
        val kept = attachments.take(MAX_ATTACHMENTS).mapNotNull { a ->
            val dst = File(dir, a.file.name)
            try {
                a.file.copyTo(dst, overwrite = true)
                dst.absolutePath + "#" + a.mime
            } catch (_: Exception) {
                null
            }
        }
        val entry = QueuedReport(id, System.currentTimeMillis(), FeedbackJson.report(draft, meta()), kept)
        mutex.withLock { save(FeedbackQueue.add(queued(), entry)) }
        val results = flush()
        results[id] ?: Outcome.Queued
    }

    /** Sends what is waiting. Returns the outcome per local id. */
    suspend fun flush(): Map<String, Outcome> = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext emptyMap()
        mutex.withLock {
            val out = HashMap<String, Outcome>()
            var list = queued()
            for (r in list.toList()) {
                val res = c.createReport(r.json)
                val next = FeedbackQueue.next(res.code, r.attempts)
                if (next == FeedbackQueue.Next.Done) {
                    val created = FeedbackClient.created(res.body)
                    if (created != null) {
                        for (a in r.attachments) {
                            val path = a.substringBefore('#')
                            val mime = a.substringAfter('#', "application/octet-stream")
                            val f = File(path)
                            if (f.exists() && f.length() <= MAX_ATTACHMENT_BYTES) c.uploadAttachment(created.first, f, mime)
                        }
                    }
                    out[r.localId] = Outcome.Sent(created?.second ?: "")
                } else {
                    out[r.localId] = when (next) {
                        FeedbackQueue.Next.RateLimited -> Outcome.RateLimited
                        FeedbackQueue.Next.Drop -> Outcome.Rejected
                        else -> Outcome.Queued
                    }
                }
                if (next == FeedbackQueue.Next.Done || next == FeedbackQueue.Next.Drop) {
                    File(app.filesDir, "feedback/${r.localId}").deleteRecursively()
                }
                list = FeedbackQueue.apply(list, r, next)
                // Offline: the rest would fail the same way.
                if (res.code == null || next == FeedbackQueue.Next.RateLimited) break
            }
            save(list)
            out
        }
    }

    /** Sends the queue whenever the unit gets a network (started once, from MainActivity). */
    fun watchNetwork() {
        if (watching || !hasBackend) return
        watching = true
        try {
            app.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (queued().isNotEmpty()) scope.launch { flush() }
                }
            })
        } catch (_: Exception) {
        }
        if (queued().isNotEmpty()) scope.launch { flush() }
    }

    suspend fun myRequests(): List<MyRequest>? = withContext(Dispatchers.IO) { client?.myRequests(app.prefs.installId) }

    suspend fun features(): List<FeatureItem>? = withContext(Dispatchers.IO) { client?.features(app.prefs.installId) }

    /** Returns true when the vote counted (one per install; the server ignores repeats). */
    suspend fun vote(featureId: Long): Boolean = withContext(Dispatchers.IO) { client?.vote(featureId, app.prefs.installId)?.ok == true }

    /** Copies a picked screenshot. Null when it is unreadable or larger than 2 MB. */
    fun importImage(uri: Uri): Attachment? {
        return try { importImageOrThrow(uri) } catch (_: Exception) { null }
    }

    private fun importImageOrThrow(uri: Uri): Attachment? {
        val mime = app.contentResolver.getType(uri) ?: "image/jpeg"
        val ext = if (mime.endsWith("png")) "png" else if (mime.endsWith("webp")) "webp" else "jpg"
        val dir = File(app.cacheDir, "feedback").apply { mkdirs() }
        val f = File(dir, "screenshot-" + System.nanoTime() + "." + ext)
        var tooBig = false
        app.contentResolver.openInputStream(uri)?.use { input ->
            f.outputStream().use { out ->
                val buf = ByteArray(32 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_ATTACHMENT_BYTES) {
                        tooBig = true
                        break
                    }
                    out.write(buf, 0, n)
                }
            }
        } ?: return null
        return if (tooBig) {
            f.delete()
            null
        } else {
            Attachment(f, mime)
        }
    }

    /** This app's own recent log lines (only when the driver ticks "Attach app logs"). */
    fun captureLogs(): Attachment? = try {
        val dir = File(app.cacheDir, "feedback").apply { mkdirs() }
        val f = File(dir, "app-log.txt")
        val p = ProcessBuilder("logcat", "-d", "-t", "600", "--pid=" + android.os.Process.myPid()).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        val bytes = text.toByteArray(Charsets.UTF_8)
        f.writeBytes(if (bytes.size > MAX_ATTACHMENT_BYTES) bytes.copyOfRange(bytes.size - MAX_ATTACHMENT_BYTES.toInt(), bytes.size) else bytes)
        Attachment(f, "text/plain")
    } catch (_: Exception) {
        null
    }

    /** No server configured: the same report as an email to support, attachments included. */
    fun emailIntent(draft: ReportDraft, attachments: List<Attachment>): Intent {
        val subject = "[Car Hub " + draft.kind.wire + "] " + draft.title.trim()
        val body = FeedbackJson.emailBody(draft, meta())
        val uris = ArrayList<Uri>()
        for (a in attachments) {
            try {
                uris += FileProvider.getUriForFile(app, app.packageName + ".files", a.file)
            } catch (_: Exception) {
            }
        }
        val intent = if (uris.isEmpty()) {
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("message/rfc822")
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return intent.putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body)
    }

    companion object {
        /** Placeholder until Ampli Studio has a real support mailbox. Must be replaced before launch. */
        const val SUPPORT_EMAIL = "support@ampliapps.in"
        const val MAX_ATTACHMENTS = 3
        const val MAX_ATTACHMENT_BYTES = 2L * 1024 * 1024
        private const val FILE = "feedback"
        private const val KEY_QUEUE = "queue"
    }
}
