package com.vikash.ampli.drive.calls

import com.vikash.ampli.drive.calls.VoiceReplies.Call
import com.vikash.ampli.drive.calls.VoiceReplies.Message
import com.vikash.ampli.drive.calls.VoiceReplies.YesNo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceRepliesTest {

    @Test
    fun answerWords() {
        for (s in listOf("uthaa", "Utha le", "haan", "हाँ", "हां", "हाँ, ठा ले", "उठा ले", "yes", "answer", "theek hai", "haan ji utha lo")) {
            assertEquals(s, Call.Answer, VoiceReplies.call(s))
        }
    }

    @Test
    fun rejectWords() {
        for (s in listOf("kaat", "kaat de", "naa", "na", "nahi", "baad mein", "बाद मैं", "काट दे", "ना", "reject", "abhi nahi", "mat utha", "naa, mat utha")) {
            assertEquals(s, Call.Reject, VoiceReplies.call(s))
        }
    }

    @Test
    fun anythingElseDoesNothing() {
        for (s in listOf("", "  ", "volume badha", "gaana chala", "hello", "thanks")) {
            assertEquals(s, Call.Unknown, VoiceReplies.call(s))
        }
        // Words are matched whole: "hamara" is not "ha", "nana" is not "na".
        assertEquals(Call.Unknown, VoiceReplies.call("hamara nana"))
    }

    @Test
    fun messageWords() {
        assertEquals(Message.Read, VoiceReplies.message("suna de"))
        assertEquals(Message.Read, VoiceReplies.message("सुणा दे"))
        assertEquals(Message.Read, VoiceReplies.message("haan"))
        assertEquals(Message.Read, VoiceReplies.message("पढ़ दे"))
        assertEquals(Message.Skip, VoiceReplies.message("rehan de"))
        assertEquals(Message.Skip, VoiceReplies.message("रहण दे"))
        assertEquals(Message.Skip, VoiceReplies.message("baad mein"))
        assertEquals(Message.Skip, VoiceReplies.message("mat suna"))
        assertEquals(Message.Reply, VoiceReplies.message("jawab de"))
        assertEquals(Message.Reply, VoiceReplies.message("जवाब बोलो"))
        assertEquals(Message.Unknown, VoiceReplies.message("kya time hua"))
    }

    @Test
    fun sendConfirmation() {
        assertEquals(YesNo.Yes, VoiceReplies.yesNo("haan bhej de"))
        assertEquals(YesNo.Yes, VoiceReplies.yesNo("भेज दे"))
        assertEquals(YesNo.Yes, VoiceReplies.yesNo("ok"))
        // "mat bhej" has "bhej" in it: nothing is ever sent on a doubt.
        assertEquals(YesNo.No, VoiceReplies.yesNo("mat bhej"))
        assertEquals(YesNo.No, VoiceReplies.yesNo("नहीं"))
        assertEquals(YesNo.No, VoiceReplies.yesNo("cancel"))
        assertEquals(YesNo.Unknown, VoiceReplies.yesNo("kal milte hain"))
    }

    @Test
    fun callerNames() {
        assertEquals("मम्मी", CallerLabel.spoken("मम्मी", "+919812345210"))
        assertEquals("9 8 1 2 3, 4 5 2 1 0", CallerLabel.spoken(null, "+91 98123 45210"))
        assertEquals("9 8 1 2 3, 4 5 2 1 0", CallerLabel.spoken("  ", "09812345210".drop(1)))
        assertNull(CallerLabel.spoken(null, null))
        assertNull(CallerLabel.spoken(null, "Private"))
        assertEquals("+91 98xxx xx210", CallerLabel.maskedNumber("+91 98123 45210"))
    }

    @Test
    fun notificationButtons() {
        assertEquals(CallActions.Kind.Answer, CallActions.classify("Answer"))
        assertEquals(CallActions.Kind.Answer, CallActions.classify("ACCEPT"))
        assertEquals(CallActions.Kind.Answer, CallActions.classify("उत्तर दें"))
        assertEquals(CallActions.Kind.Decline, CallActions.classify("Decline"))
        assertEquals(CallActions.Kind.Decline, CallActions.classify("Hang up"))
        assertEquals(CallActions.Kind.Decline, CallActions.classify("अस्वीकार करें"))
        assertEquals(CallActions.Kind.Other, CallActions.classify("Reply with message"))
        assertEquals(CallActions.Kind.Other, CallActions.classify(null))
    }
}
