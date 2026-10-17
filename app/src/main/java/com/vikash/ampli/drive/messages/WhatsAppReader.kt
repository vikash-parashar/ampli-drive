package com.vikash.ampli.drive.messages

import android.app.Notification
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.voice.Spoken
import com.vikash.ampli.drive.voice.VoiceClips
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Pro: reads WhatsApp and WhatsApp Business messages aloud ("Rahul का मैसेज: कहाँ पहुँचे?").
 * Fed by [com.vikash.ampli.drive.service.DriveNotificationListener]; the rules are in [MessageGate].
 *
 * - Voice only. The text is never drawn on any screen and never stored: it lives in memory only
 *   until it has been spoken.
 * - Nothing is sent: no SMS, no auto-reply, no notification action is ever triggered.
 * - Quiet during a call, a Hey Ampli session and a navigation instruction (it waits a little,
 *   then drops the message rather than talk over the road).
 * - Group chats are skipped unless Settings › Read group messages is on.
 */
class WhatsAppReader(private val app: DriveApp) {
    val prefs: MessagePrefs by lazy { MessagePrefs(app) }
    private val gate = MessageGate()
    private val scope = MainScope()
    private var queue: Job? = null

    /** Called on the main thread by the notification listener for every posted notification. */
    fun onNotification(sbn: StatusBarNotification) {
        if (sbn.packageName !in PACKAGES) return
        if (!prefs.enabled.value) return // Cheap exit before anything is read.
        val msg = extract(sbn) ?: return
        val settings = GateSettings(
            enabled = prefs.enabled.value,
            disclosureAccepted = prefs.disclosureAccepted.value,
            isPro = app.prefs.pro.value.isPro,
            allowGroups = prefs.allowGroups.value,
        )
        when (val r = gate.decide(msg, settings, System.currentTimeMillis())) {
            is GateResult.Speak -> enqueue(r.sender, r.text)
            is GateResult.Skip -> Log.d(TAG, "skipped: ${r.reason}")
        }
    }

    private fun enqueue(sender: String, text: String) {
        val old = queue
        queue = scope.launch {
            old?.join()
            if (!waitForQuiet()) return@launch
            speak(sender, text)
        }
    }

    /** Waits up to [QUIET_WAIT_MS] for a call, Hey Ampli or a turn instruction to finish. */
    private suspend fun waitForQuiet(): Boolean {
        var waited = 0L
        while (busy()) {
            if (waited >= QUIET_WAIT_MS) return false
            delay(POLL_MS)
            waited += POLL_MS
        }
        return true
    }

    /** A call, Hey Ampli, a turn instruction, or a crash countdown / SOS just opened. */
    private fun busy(): Boolean =
        app.speaker.inCall() || app.voice.busy.value || app.navPrompter.speaking.value ||
            app.car.crashAt.value != null || app.car.sosOpenedRecently()

    /** A crash started: drop every waiting message and stop one being read. */
    fun cancelAll() {
        val q = queue ?: return
        queue = null
        q.cancel()
        app.speaker.stop()
    }

    private suspend fun speak(sender: String, text: String) {
        val hindi = app.speaker.hasVoice(HINDI)
        val language = if (hindi) "hi" else "en-IN"
        val voice = app.voicePlayer.voice
        val clip = VoiceClips.MESSAGE_WHATSAPP
        if (voice.human && app.voicePlayer.library.has(voice, clip)) {
            // Recorded "व्हाट्सऐप पै मैसेज आया सै" in the Ampli voice, then the name and text by TTS.
            app.voicePlayer.say(Spoken(VoiceClips.MESSAGES.getValue(clip), "hi", listOf(clip)), needFocus = true)
            if (busy()) return
            app.speaker.say(MessagePhrases.afterClip(sender, text), language, needFocus = true)
        } else {
            app.speaker.say(MessagePhrases.full(sender, text, hindi), language, needFocus = true)
        }
    }

    private fun extract(sbn: StatusBarNotification): IncomingMessage? {
        val n = sbn.notification ?: return null
        // Summaries ("5 messages from 2 chats"), calls and "WhatsApp Web is active" are not messages.
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return null
        if (n.category == Notification.CATEGORY_CALL || n.category == CATEGORY_MISSED_CALL) return null
        val style = try {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        } catch (_: Exception) {
            null
        }
        if (style != null) {
            val last = style.messages.lastOrNull() ?: return null
            // No person = a message the driver sent from the phone.
            val sender = last.person?.name?.toString() ?: return null
            val text = last.text?.toString() ?: return null
            return IncomingMessage(
                packageName = sbn.packageName,
                sender = sender,
                text = text,
                group = style.isGroupConversation,
                timeMs = last.timestamp.takeIf { it > 0 } ?: sbn.postTime,
            )
        }
        // Old WhatsApp builds without MessagingStyle: title = sender ("Group: Sender" for groups).
        val extras = n.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return null
        val group = extras.getBoolean(EXTRA_IS_GROUP, false) || title.contains(": ")
        return IncomingMessage(sbn.packageName, title.substringAfterLast(": "), text, group, sbn.postTime)
    }

    companion object {
        private const val TAG = "WhatsAppReader"
        val PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val HINDI: Locale = Locale.forLanguageTag("hi-IN")
        private const val QUIET_WAIT_MS = 30_000L
        private const val POLL_MS = 500L

        /** Notification.EXTRA_IS_GROUP_CONVERSATION (API 28), by name for Android 8. */
        private const val EXTRA_IS_GROUP = "android.isGroupConversation"

        /** Notification.CATEGORY_MISSED_CALL (API 31), by value for older units. */
        private const val CATEGORY_MISSED_CALL = "missed_call"
    }
}
