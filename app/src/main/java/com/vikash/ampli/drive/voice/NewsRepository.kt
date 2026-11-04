package com.vikash.ampli.drive.voice

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.vikash.ampli.drive.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * The headlines for "aaj ki taaza khabar": one public RSS feed ([BuildConfig.NEWS_FEED_URL]),
 * fetched only when asked, at most every [NewsFeed.CACHE_MS], within [NewsFeed.TIMEOUT_MS].
 */
class NewsRepository(context: Context, private val url: String = BuildConfig.NEWS_FEED_URL) {
    private val appContext = context.applicationContext

    @Volatile private var titles: List<String> = emptyList()
    @Volatile private var fetchedAt = 0L

    sealed interface Result {
        /** [fetchedAt] tells a new fetch apart from the one already being read out. */
        data class Ok(val titles: List<String>, val fetchedAt: Long, val source: NewsFeed.Source) : Result
        data object Offline : Result
        data object Failed : Result
        data object NotSetUp : Result
    }

    val source: NewsFeed.Source get() = NewsFeed.source(url)

    suspend fun get(now: Long = System.currentTimeMillis()): Result {
        if (url.isBlank()) return Result.NotSetUp
        if (titles.isNotEmpty() && NewsFeed.fresh(fetchedAt, now)) return Result.Ok(titles, fetchedAt, source)
        if (!online()) return Result.Offline
        val xml = withTimeoutOrNull(NewsFeed.TIMEOUT_MS.toLong()) {
            try {
                runInterruptible(Dispatchers.IO) { download() }
            } catch (_: Exception) {
                null
            }
        }
        val fresh = xml?.let { NewsFeed.parseTitles(it) }.orEmpty()
        if (fresh.isEmpty()) return if (online()) Result.Failed else Result.Offline
        titles = fresh
        fetchedAt = now
        return Result.Ok(fresh, now, source)
    }

    private fun download(): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = NewsFeed.TIMEOUT_MS
        conn.readTimeout = NewsFeed.TIMEOUT_MS
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "AmpliCarHub/${BuildConfig.VERSION_NAME} (Android)")
        conn.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
        return try {
            if (conn.responseCode != 200) return null
            conn.inputStream.use { input ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                while (buf.size() < NewsFeed.MAX_BYTES) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                }
                // UTF-8, without the byte-order mark some feeds start with.
                buf.toByteArray().toString(Charsets.UTF_8).removePrefix("﻿")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun online(): Boolean = try {
        val cm = appContext.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: Exception) {
        true
    }
}
