package com.vikash.ampli.drive.feedback

/** What the driver is sending from Settings › Help & feedback. */
enum class ReportKind(val wire: String) { Bug("bug"), Feature("feature"), Support("support"), Feedback("feedback") }

/** The form, as typed. Empty optional fields are left out of the request. */
data class ReportDraft(
    val kind: ReportKind,
    val title: String,
    val body: String,
    val contact: String = "",
    val orderId: String = "",
)

/** Facts about this install sent with every report (no personal data). */
data class ReportMeta(
    val installId: String,
    val appVersion: String,
    val device: String,
    val os: String,
    val locale: String,
    val pro: Boolean,
)

/**
 * Request bodies for the Ampli owner server (spec: owner-app/in-app-hook-spec.md, v0).
 * Hand-written JSON so it is plain Kotlin and unit-tested on the JVM.
 */
object FeedbackJson {
    const val PRODUCT = "carhub"
    const val TITLE_MIN = 3
    const val TITLE_MAX = 120
    const val BODY_MAX = 4000

    enum class Problem { TitleTooShort, TitleTooLong, BodyTooLong, BadEmail }

    /** Problems that block sending, empty when the form is fine. */
    fun validate(d: ReportDraft): List<Problem> = buildList {
        val t = d.title.trim()
        if (t.length < TITLE_MIN) add(Problem.TitleTooShort)
        if (t.length > TITLE_MAX) add(Problem.TitleTooLong)
        if (d.body.trim().length > BODY_MAX) add(Problem.BodyTooLong)
        val c = d.contact.trim()
        if (c.isNotEmpty() && !EMAIL.matches(c)) add(Problem.BadEmail)
    }

    private val EMAIL = Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""")

    /** `POST /v1/public/reports` body. order_id is sent for support requests only. */
    fun report(d: ReportDraft, m: ReportMeta): String {
        val fields = linkedMapOf<String, Any>(
            "product" to PRODUCT,
            "kind" to d.kind.wire,
            "title" to d.title.trim().take(TITLE_MAX),
            "body" to d.body.trim().take(BODY_MAX),
        )
        d.contact.trim().takeIf { it.isNotEmpty() }?.let { fields["contact"] = it }
        fields["install_id"] = m.installId
        fields["app_version"] = m.appVersion
        fields["device"] = m.device
        fields["os"] = m.os
        fields["locale"] = m.locale
        fields["pro"] = m.pro
        if (d.kind == ReportKind.Support) d.orderId.trim().takeIf { it.isNotEmpty() }?.let { fields["order_id"] = it }
        return obj(fields)
    }

    /** `POST /v1/public/features/{id}/vote` body. */
    fun vote(installId: String): String = obj(linkedMapOf("install_id" to installId))

    /** "Android 14 (SDK 34)". */
    fun osLabel(release: String, sdk: Int): String = "Android $release (SDK $sdk)"

    /** Plain-text version for the email fallback (no server configured). */
    fun emailBody(d: ReportDraft, m: ReportMeta): String = buildString {
        append(d.body.trim()).append("\n\n---\n")
        append("Kind: ").append(d.kind.wire).append('\n')
        if (d.kind == ReportKind.Support && d.orderId.isNotBlank()) append("Play order id: ").append(d.orderId.trim()).append('\n')
        append("App: Ampli Car Hub ").append(m.appVersion).append('\n')
        append("Device: ").append(m.device).append(" · ").append(m.os).append('\n')
        append("Locale: ").append(m.locale).append(" · Pro: ").append(if (m.pro) "yes" else "no").append('\n')
        append("Install id: ").append(m.installId).append('\n')
    }

    fun obj(fields: Map<String, Any?>): String = fields.entries.joinToString(",", "{", "}") { (k, v) ->
        quote(k) + ":" + value(v)
    }

    private fun value(v: Any?): String = when (v) {
        null -> "null"
        is Boolean -> v.toString()
        is Int, is Long -> v.toString()
        is Number -> v.toDouble().let { if (it.isFinite()) it.toString() else "null" }
        else -> quote(v.toString())
    }

    /** JSON string with RFC 8259 escaping; output never contains a raw line break. */
    fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2).append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                ' ' -> sb.append("\\u2028")
                ' ' -> sb.append("\\u2029")
                else -> if (c < ' ') sb.append(String.format(java.util.Locale.US, "\\u%04x", c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
