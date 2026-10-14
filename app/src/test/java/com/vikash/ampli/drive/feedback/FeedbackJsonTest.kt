package com.vikash.ampli.drive.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackJsonTest {
    private val meta = ReportMeta(
        installId = "8a3b0c1e-0000-4000-8000-000000000001",
        appVersion = "0.1.40 (40)",
        device = "FYT UIS7862",
        os = "Android 13 (SDK 33)",
        locale = "hi-IN",
        pro = true,
    )

    @Test
    fun bugReportHasSpecFieldsInOrder() {
        val json = FeedbackJson.report(ReportDraft(ReportKind.Bug, "  Map freezes ", "It stops after a tunnel"), meta)
        assertEquals(
            """{"product":"carhub","kind":"bug","title":"Map freezes","body":"It stops after a tunnel",""" +
                """"install_id":"8a3b0c1e-0000-4000-8000-000000000001","app_version":"0.1.40 (40)",""" +
                """"device":"FYT UIS7862","os":"Android 13 (SDK 33)","locale":"hi-IN","pro":true}""",
            json,
        )
    }

    @Test
    fun orderIdOnlyForSupportAndContactOnlyWhenTyped() {
        val support = FeedbackJson.report(ReportDraft(ReportKind.Support, "Pro not active", "Paid twice", contact = "a@b.in", orderId = "GPA.1234-5678"), meta)
        assertTrue(support.contains(""""order_id":"GPA.1234-5678""""))
        assertTrue(support.contains(""""contact":"a@b.in""""))
        val feature = FeedbackJson.report(ReportDraft(ReportKind.Feature, "Dark map", "Please", orderId = "GPA.1"), meta)
        assertFalse(feature.contains("order_id"))
        assertFalse(feature.contains("contact"))
    }

    @Test
    fun escapesQuotesNewlinesAndControlCharacters() {
        val json = FeedbackJson.report(ReportDraft(ReportKind.Bug, "Say \"hi\"", "line1\nline2\\ end\t\u0001"), meta)
        assertTrue(json.contains(""""title":"Say \"hi\"""""))
        assertTrue(json.contains(""""body":"line1\nline2\\ end\t\u0001""""))
        assertFalse(json.contains("\n"))
    }

    @Test
    fun bodyAndTitleAreCappedAndValidated() {
        val long = "x".repeat(5000)
        val json = FeedbackJson.report(ReportDraft(ReportKind.Bug, "ok title", long), meta)
        assertTrue(json.contains("\"" + "x".repeat(FeedbackJson.BODY_MAX) + "\""))
        assertEquals(listOf(FeedbackJson.Problem.TitleTooShort), FeedbackJson.validate(ReportDraft(ReportKind.Bug, "ab", "")))
        assertEquals(listOf(FeedbackJson.Problem.BadEmail), FeedbackJson.validate(ReportDraft(ReportKind.Bug, "abc", "", contact = "not-an-email")))
        assertTrue(FeedbackJson.validate(ReportDraft(ReportKind.Bug, "abc", "", contact = "me@example.com")).isEmpty())
    }

    @Test
    fun voteBodyAndOsLabel() {
        assertEquals("""{"install_id":"x-1"}""", FeedbackJson.vote("x-1"))
        assertEquals("Android 14 (SDK 34)", FeedbackJson.osLabel("14", 34))
    }

    @Test
    fun hindiTextIsKeptAsIs() {
        val json = FeedbackJson.report(ReportDraft(ReportKind.Feedback, "नक्शा", "ठीक है"), meta)
        assertTrue(json.contains(""""title":"नक्शा""""))
    }
}
