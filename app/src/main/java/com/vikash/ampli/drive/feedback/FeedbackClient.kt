package com.vikash.ampli.drive.feedback

import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One of the driver's own tickets ("My requests"). */
data class MyRequest(val ref: String, val kind: String, val title: String, val status: String, val reply: String?)

/** A feature others asked for, with its votes. */
data class FeatureItem(val id: Long, val title: String, val votes: Int, val voted: Boolean)

/** Outcome of one HTTP call: [code] is null when the server was not reached. */
data class HttpResult(val code: Int?, val body: String = "") {
    val ok: Boolean get() = code != null && code in 200..299
}

/**
 * The Ampli owner server's public endpoints (in-app-hook-spec.md). Blocking calls: use them
 * from Dispatchers.IO. The server's list replies are read leniently ("items" or a bare array)
 * because the server is still being built.
 */
class FeedbackClient(private val baseUrl: String) {
    private val base = baseUrl.trimEnd('/')

    fun createReport(json: String): HttpResult = send("POST", "$base/v1/public/reports", json)

    /** Multipart upload of one screenshot or log file (the caller enforces 2 MB and 3 files). */
    fun uploadAttachment(reportId: Long, file: File, mime: String): HttpResult {
        val boundary = "----ampli" + System.nanoTime()
        val conn = open("POST", "$base/v1/public/reports/$reportId/attachments") ?: return HttpResult(null)
        return try {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            DataOutputStream(conn.outputStream).use { out ->
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name.replace("\"", "")}\"\r\n")
                out.writeBytes("Content-Type: $mime\r\n\r\n")
                file.inputStream().use { it.copyTo(out) }
                out.writeBytes("\r\n--$boundary--\r\n")
            }
            read(conn)
        } catch (_: IOException) {
            HttpResult(null)
        } finally {
            conn.disconnect()
        }
    }

    fun myRequests(installId: String): List<MyRequest>? {
        val r = send("GET", "$base/v1/public/reports?install_id=" + enc(installId), null)
        if (!r.ok) return null
        return items(r.body).mapNotNull { o ->
            val replies = o.optJSONArray("replies")
            val lastReply = replies?.let { a -> (a.length() - 1 downTo 0).firstNotNullOfOrNull { i -> a.optJSONObject(i)?.optString("body")?.takeIf { it.isNotBlank() } ?: a.optString(i).takeIf { it.isNotBlank() } } }
            MyRequest(
                ref = o.optString("ref").ifBlank { "#" + o.optLong("id") },
                kind = o.optString("kind"),
                title = o.optString("title"),
                status = o.optString("status"),
                reply = o.optString("reply").takeIf { it.isNotBlank() && it != "null" } ?: lastReply,
            )
        }
    }

    fun features(installId: String): List<FeatureItem>? {
        val r = send("GET", "$base/v1/public/features?product=${FeedbackJson.PRODUCT}&install_id=" + enc(installId), null)
        if (!r.ok) return null
        return items(r.body).mapNotNull { o ->
            val id = o.optLong("id", -1L).takeIf { it >= 0 } ?: return@mapNotNull null
            FeatureItem(id, o.optString("title"), o.optInt("votes"), o.optBoolean("voted"))
        }.sortedByDescending { it.votes }
    }

    fun vote(featureId: Long, installId: String): HttpResult =
        send("POST", "$base/v1/public/features/$featureId/vote", FeedbackJson.vote(installId))

    private fun items(body: String): List<JSONObject> = try {
        val t = body.trim()
        val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).let { it.optJSONArray("items") ?: it.optJSONArray("reports") ?: it.optJSONArray("features") ?: JSONArray() }
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun send(method: String, url: String, json: String?): HttpResult {
        val conn = open(method, url) ?: return HttpResult(null)
        return try {
            if (json != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            read(conn)
        } catch (_: IOException) {
            HttpResult(null)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(method: String, url: String): HttpURLConnection? = try {
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }
    } catch (_: Exception) {
        null
    }

    private fun read(conn: HttpURLConnection): HttpResult {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return HttpResult(code, body)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        /** `201 {"id": 142, "ref": "T-0142"}` → (142, "T-0142"). */
        fun created(body: String): Pair<Long, String>? = try {
            val o = JSONObject(body)
            val id = o.optLong("id", -1L)
            if (id < 0) null else id to o.optString("ref").ifBlank { "#$id" }
        } catch (_: Exception) {
            null
        }
    }
}
