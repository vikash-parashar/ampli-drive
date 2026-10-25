package com.vikash.ampli.drive.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.vikash.ampli.drive.BuildConfig
import com.vikash.ampli.drive.nav.route.MiniJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Newest test APK of Ampli MP3 Player in its public GitHub releases. Plain Kotlin, unit-tested. */
object Mp3Releases {
    const val API_URL = "https://api.github.com/repos/vikash-parashar/music-player/releases?per_page=5"

    data class Apk(val tag: String, val name: String, val url: String, val size: Long, val build: Int)

    /** Exactly "Ampli-<N>.apk": not the "-protest" build and not the .aab. */
    private val APK_NAME = Regex("^Ampli-(\\d+)\\.apk$")

    /**
     * The newest non-draft release (by published date, then build number) that carries an
     * "Ampli-<N>.apk" asset, or null. [json] is the GitHub "list releases" response.
     */
    fun pick(json: String): Apk? {
        val releases = try {
            MiniJson.parse(json) as? List<*>
        } catch (_: Exception) {
            null
        } ?: return null
        val found = ArrayList<Pair<String, Apk>>()
        for (r in releases) {
            val rel = r as? Map<*, *> ?: continue
            if (rel["draft"] == true) continue
            val tag = rel["tag_name"] as? String ?: ""
            val published = rel["published_at"] as? String ?: ""
            val assets = rel["assets"] as? List<*> ?: continue
            val apk = assets.asSequence()
                .mapNotNull { it as? Map<*, *> }
                .mapNotNull { a ->
                    val name = a["name"] as? String ?: return@mapNotNull null
                    val build = APK_NAME.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
                    val url = a["browser_download_url"] as? String ?: return@mapNotNull null
                    if (!url.startsWith("https://")) return@mapNotNull null
                    Apk(tag, name, url, (a["size"] as? Number)?.toLong() ?: -1L, build)
                }
                .maxByOrNull { it.build } ?: continue
            found += published to apk
        }
        // ISO-8601 UTC dates sort as text.
        return found.sortedWith(compareByDescending<Pair<String, Apk>> { it.first }.thenByDescending { it.second.build })
            .firstOrNull()?.second
    }
}

/**
 * Test builds only ([BuildConfig.SIDELOAD_INSTALL]): downloads Ampli MP3 Player's newest test APK
 * into the cache and hands it to the system installer. The Play bundle never calls this and does
 * not declare REQUEST_INSTALL_PACKAGES (src/debug/AndroidManifest.xml).
 */
object Mp3Install {
    val enabled: Boolean get() = BuildConfig.SIDELOAD_INSTALL

    /** Finds the newest APK. Null when offline or nothing matches. Background thread. */
    suspend fun latest(): Mp3Releases.Apk? = withContext(Dispatchers.IO) {
        try {
            val c = open(Mp3Releases.API_URL)
            c.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (c.responseCode != 200) return@withContext null
                Mp3Releases.pick(c.inputStream.bufferedReader().use { it.readText() })
            } finally {
                c.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Downloads [apk] to cache/apk with [onProgress] 0..100. Returns the file, or null on failure. */
    suspend fun download(context: Context, apk: Mp3Releases.Apk, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "apk").apply { mkdirs() }
        dir.listFiles()?.forEach { if (it.name != apk.name) it.delete() }
        val out = File(dir, apk.name)
        if (apk.size > 0 && out.length() == apk.size) return@withContext out
        val part = File(dir, apk.name + ".part")
        try {
            val c = open(apk.url)
            try {
                if (c.responseCode !in 200..299) return@withContext null
                val total = c.contentLengthLong.takeIf { it > 0 } ?: apk.size
                var done = 0L
                var lastPct = -1
                c.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt().coerceIn(0, 100)
                                if (pct != lastPct) {
                                    lastPct = pct
                                    withContext(Dispatchers.Main) { onProgress(pct) }
                                }
                            }
                        }
                    }
                }
                if (apk.size > 0 && done != apk.size) return@withContext null
            } finally {
                c.disconnect()
            }
            out.delete()
            if (!part.renameTo(out)) return@withContext null
            out
        } catch (_: Exception) {
            part.delete()
            null
        }
    }

    enum class InstallStep { InstallerOpened, AllowFirst, Failed }

    /**
     * Opens the system installer for [file]. Android 8+ asks once to allow installs from Car Hub:
     * then that settings page opens instead, and the driver taps Install again on return.
     */
    fun install(context: Context, file: File): InstallStep {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            val allow = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(allow)
                InstallStep.AllowFirst
            } catch (_: Exception) {
                InstallStep.Failed
            }
        }
        return try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(view)
            InstallStep.InstallerOpened
        } catch (_: Exception) {
            InstallStep.Failed
        }
    }

    /** The Play listing: the Play app, else the web page. False when neither opens. */
    fun openPlay(context: Context): Boolean {
        for (link in listOf("market://details?id=${AmpliMusic.PACKAGE}", "https://play.google.com/store/apps/details?id=${AmpliMusic.PACKAGE}")) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "AmpliCarHub/${BuildConfig.VERSION_NAME}")
    }
}
