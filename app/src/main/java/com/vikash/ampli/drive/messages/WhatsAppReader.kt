package com.vikash.ampli.drive.messages

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.calls.VoiceReplies
import com.vikash.ampli.drive.voice.HaryanviLines.Event
import com.vikash.ampli.drive.voice.Spoken
import com.vikash.ampli.drive.voice.VoiceClips
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.selects.select
import java.util.Locale

/**
 * Pro: reads WhatsApp and WhatsApp Business messages aloud (and Telegram, when switched on).
 * Fed by [com.vikash.ampli.drive.service.DriveNotificationListener]; the rules are in [MessageGate].
 *
 * - With a speech recogniser: "रमेश का WhatsApp आया सै, सुणाऊं?" first, and the text only after
 *   "सुणा दे / हाँ" (or a tap on the card). Without one, the message is read straight away as before.
 * - Voice only. The message text is never drawn on any screen and never stored: it lives in memory
 *   only until it has been spoken. The card shows the sender and the app, nothing more.
 * - Replies (Settings › Reply by voice, off by default, after its own disclosure): the dictated
 *   reply is read back and goes out only after "हाँ / भेज दे", through the notification's own
 *   reply button (the same as replying from the notification shade). No SMS, no auto-reply,
 *   nothing sent to any server.
 * - Quiet during a call, a Hey Ampli session and a navigation instruction (it waits a little,
 *   then drops the message rather than talk over the road).
 * - Group chats are skipped unless Settings › Read group messages is on.
 */
class WhatsAppReader(private val app: DriveApp) {
    val prefs: MessagePrefs by lazy { MessagePrefs(app) }
    private val gate = MessageGate()
    private val queue = MessageQueue(Dispatchers.Main)
    private val scope = MainScope()

    /** The message card on screen: who wrote, on which app, and what the car is doing. */
    data class Prompt(
        val id: Long,
        val sender: String,
        val appName: String,
        val phase: Phase,
        /** A reply can be dictated (the notification has a reply button and replies are on). */
        val canReply: Boolean,
        /** Which question the card's buttons answer. */
        val step: Step = Step.Offer,
    ) {
        enum class Phase { Asking, Listening, Reading, Dictating, Confirming, Sent }

        /** "सुणाऊं?", then "जवाब भेजणा सै?", then the dictation, then "भेज दूँ?". */
        enum class Step { Offer, OfferReply, Dictate, Confirm, Sent }
    }

    private val _prompt = MutableStateFlow<Prompt?>(null)
    val prompt: StateFlow<Prompt?> = _prompt.asStateFlow()
    private var promptId = 0L

    /** A tap on the card, answering the current question. */
    @Volatile private var tap: CompletableDeferred<VoiceReplies.Message>? = null

    /** Called on the main thread by the notification listener for every posted notification. */
    fun onNotification(sbn: StatusBarNotification) {
        if (sbn.packageName !in PACKAGES) return
        if (!prefs.enabled.value) return // Cheap exit before anything is read.
        if (sbn.packageName in TELEGRAM && !(prefs.telegram.value && prefs.chatDisclosureAccepted.value)) return
        // No speech engine: only the opener clip could play, never who wrote or what.
        if (app.voicePlayer.ttsMissing.value) return
        val (msg, reply) = extract(sbn) ?: return
        val settings = GateSettings(
            enabled = prefs.enabled.value,
            disclosureAccepted = prefs.disclosureAccepted.value,
            isPro = app.prefs.pro.value.isPro,
            allowGroups = prefs.allowGroups.value,
        )
        when (val r = gate.decide(msg, settings, System.currentTimeMillis())) {
            is GateResult.Speak -> enqueue(r.sender, r.text, sbn.packageName, reply)
            is GateResult.Skip -> Log.d(TAG, "skipped: ${r.reason}")
        }
    }

    private fun enqueue(sender: String, text: String, pkg: String, reply: NotificationCompat.Action?) {
        queue.enqueue {
            if (!waitForQuiet()) return@enqueue
            val canReply = reply != null && prefs.voiceReply.value && prefs.chatDisclosureAccepted.value && app.prefs.pro.value.isPro
            if (app.voice.canListen()) {
                converse(sender, text, pkg, if (canReply) reply else null)
            } else {
                speak(sender, text)
            }
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
            app.car.crashAt.value != null || app.car.sosOpenedRecently() || app.calls.card.value != null

    /** A crash started: drop every waiting message and stop one being read. */
    fun cancelAll() {
        tap?.complete(VoiceReplies.Message.Skip)
        _prompt.value = null
        // Every waiting message goes, not just the newest; TTS is cut only if one was queued.
        if (queue.cancelAll()) app.speaker.stop()
    }

    /** The card's buttons: "सुणा दे" ([VoiceReplies.Message.Read]), "रहण दे" (Skip), "जवाब बोलो" (Reply). */
    fun onTap(choice: VoiceReplies.Message) {
        val t = tap
        if (t != null && t.isActive) {
            app.voice.cancelListen()
            t.complete(choice)
        } else if (choice == VoiceReplies.Message.Skip) {
            // "रहण दे" while it is reading: stop and close.
            app.speaker.stop()
            app.voicePlayer.stop()
            _prompt.value = null
        }
    }

    /** Ask first, read on "सुणा दे", then offer a reply. Runs inside the message queue. */
    private suspend fun converse(sender: String, text: String, pkg: String, reply: NotificationCompat.Action?) {
        val id = ++promptId
        val appName = appName(pkg)
        _prompt.value = Prompt(id, sender, appName, Prompt.Phase.Asking, reply != null)
        try {
            say(Event.MessageAnnounce, app.car.localized().getString(R.string.chat_voice_ask, sender, appName), sender, appName)
            var choice = ask(id, yesNo = false)
            if (choice == VoiceReplies.Message.Read) {
                phase(id, Prompt.Phase.Reading)
                if (stopped(id)) return
                app.speaker.say(if (text.isEmpty()) sender else text, language(), needFocus = true)
                if (reply == null || stopped(id)) return
                step(id, Prompt.Step.OfferReply)
                say(Event.MessageAskReply, app.car.localized().getString(R.string.chat_voice_ask_reply))
                choice = ask(id, yesNo = true)
                // "हाँ" to "जवाब भेजणा सै?" means reply.
                if (choice == VoiceReplies.Message.Read) choice = VoiceReplies.Message.Reply
            }
            if (choice != VoiceReplies.Message.Reply || reply == null || stopped(id)) return
            dictate(id, reply)
        } finally {
            if (_prompt.value?.id == id && _prompt.value?.phase != Prompt.Phase.Sent) _prompt.value = null
        }
    }

    private suspend fun dictate(id: Long, reply: NotificationCompat.Action) {
        step(id, Prompt.Step.Dictate)
        phase(id, Prompt.Phase.Dictating)
        say(Event.MessageDictate, app.car.localized().getString(R.string.chat_voice_dictate))
        if (stopped(id)) return
        val said = app.voice.listenOnce()?.trim()?.take(MAX_REPLY_CHARS)
        if (said.isNullOrEmpty() || stopped(id)) {
            say(Event.MessageNotSent, app.car.localized().getString(R.string.chat_voice_not_sent))
            return
        }
        step(id, Prompt.Step.Confirm)
        phase(id, Prompt.Phase.Confirming)
        say(Event.MessageConfirm, app.car.localized().getString(R.string.chat_voice_confirm, said), said)
        if (stopped(id)) return
        val ok = ask(id, yesNo = true) == VoiceReplies.Message.Read && !stopped(id) && send(reply, said)
        if (ok) {
            step(id, Prompt.Step.Sent)
            phase(id, Prompt.Phase.Sent)
            say(Event.MessageSent, app.car.localized().getString(R.string.chat_voice_sent))
            delay(1_200L)
            if (_prompt.value?.id == id) _prompt.value = null
        } else {
            say(Event.MessageNotSent, app.car.localized().getString(R.string.chat_voice_not_sent))
        }
    }

    /**
     * One answer, by voice or by a tap, whichever comes first. With [yesNo] a spoken "हाँ / भेज दे"
     * is [VoiceReplies.Message.Read] and "ना" is Skip. Silence or anything unclear is Skip:
     * nothing is read or sent on a doubt.
     */
    private suspend fun ask(id: Long, yesNo: Boolean): VoiceReplies.Message {
        if (stopped(id)) return VoiceReplies.Message.Skip
        val t = CompletableDeferred<VoiceReplies.Message>()
        tap = t
        phase(id, Prompt.Phase.Listening)
        val heard = scope.async { app.voice.listenOnce() }
        val choice = try {
            select<VoiceReplies.Message> {
                t.onAwait { it }
                heard.onAwait { text ->
                    when {
                        text == null -> VoiceReplies.Message.Skip
                        yesNo -> when (VoiceReplies.yesNo(text)) {
                            VoiceReplies.YesNo.Yes -> VoiceReplies.Message.Read
                            else -> if (VoiceReplies.message(text) == VoiceReplies.Message.Reply) VoiceReplies.Message.Reply else VoiceReplies.Message.Skip
                        }
                        else -> VoiceReplies.message(text).let { if (it == VoiceReplies.Message.Unknown) VoiceReplies.Message.Skip else it }
                    }
                }
            }
        } finally {
            heard.cancel()
            tap = null
        }
        if (_prompt.value?.id == id && choice != VoiceReplies.Message.Skip) phase(id, Prompt.Phase.Asking)
        return choice
    }

    private fun step(id: Long, s: Prompt.Step) {
        _prompt.value = _prompt.value?.takeIf { it.id == id }?.copy(step = s)
    }

    private fun phase(id: Long, p: Prompt.Phase) {
        _prompt.value = _prompt.value?.takeIf { it.id == id }?.copy(phase = p)
    }

    /** The card was closed, a call came or a crash started. */
    private fun stopped(id: Long): Boolean = _prompt.value?.id != id || app.speaker.inCall() || app.car.crashAt.value != null

    /** The notification's own reply button, filled in as if typed there. */
    private fun send(action: NotificationCompat.Action, text: String): Boolean {
        val inputs = action.remoteInputs ?: return false
        val intent: PendingIntent = action.actionIntent ?: return false
        return try {
            val results = Bundle()
            for (ri in inputs) results.putCharSequence(ri.resultKey, text)
            val fill = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            RemoteInput.addResultsToIntent(inputs, fill, results)
            intent.send(app, 0, fill)
            true
        } catch (e: Exception) {
            Log.w(TAG, "reply failed: ${e.message}")
            false
        }
    }

    private fun language(): String = if (hindiVoice) "hi" else "en-IN"

    @Volatile private var hindiVoice = true

    /** The desi line, or the plain app string (Standard style, or a recorded voice without the clip). */
    private suspend fun say(event: Event, plain: String, vararg args: Any) {
        hindiVoice = app.speaker.hasVoice(HINDI)
        val line = app.desi.line(event, *args)
        if (line != null) app.voicePlayer.say(line, needFocus = true) else app.speaker.say(plain, app.car.language(), needFocus = true)
    }

    private fun appName(pkg: String): String = if (pkg in TELEGRAM) "Telegram" else "WhatsApp"

    /** Without a recogniser: who wrote and what, straight away (as before replies existed). */
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

    private fun extract(sbn: StatusBarNotification): Pair<IncomingMessage, NotificationCompat.Action?>? {
        val n = sbn.notification ?: return null
        // Summaries ("5 messages from 2 chats"), calls and "WhatsApp Web is active" are not messages.
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return null
        if (n.category == Notification.CATEGORY_CALL || n.category == CATEGORY_MISSED_CALL) return null
        val reply = replyAction(n)
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
            ) to reply
        }
        // Old builds without MessagingStyle: title = sender ("Group: Sender" for groups).
        val extras = n.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return null
        val group = extras.getBoolean(EXTRA_IS_GROUP, false) || title.contains(": ")
        return IncomingMessage(sbn.packageName, title.substringAfterLast(": "), text, group, sbn.postTime) to reply
    }

    /** The notification's reply button: an action that takes free text (RemoteInput). */
    private fun replyAction(n: Notification): NotificationCompat.Action? {
        val count = NotificationCompat.getActionCount(n)
        val actions = (0 until count).mapNotNull { NotificationCompat.getAction(n, it) }
        return actions.firstOrNull { it.semanticAction == NotificationCompat.Action.SEMANTIC_ACTION_REPLY && it.hasFreeText() }
            ?: actions.firstOrNull { it.hasFreeText() }
    }

    private fun NotificationCompat.Action.hasFreeText(): Boolean =
        actionIntent != null && remoteInputs?.any { it.allowFreeFormInput } == true

    companion object {
        private const val TAG = "WhatsAppReader"
        private val TELEGRAM = setOf("org.telegram.messenger", "org.telegram.messenger.web")

        /** WhatsApp, WhatsApp Business, and Telegram (only when Settings › Telegram messages is on). */
        val PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b") + TELEGRAM
        private val HINDI: Locale = Locale.forLanguageTag("hi-IN")
        private const val QUIET_WAIT_MS = 30_000L
        private const val POLL_MS = 500L

        /** A spoken reply longer than this is cut (it is read back in full before sending). */
        private const val MAX_REPLY_CHARS = 300

        /** Notification.EXTRA_IS_GROUP_CONVERSATION (API 28), by name for Android 8. */
        private const val EXTRA_IS_GROUP = "android.isGroupConversation"

        /** Notification.CATEGORY_MISSED_CALL (API 31), by value for older units. */
        private const val CATEGORY_MISSED_CALL = "missed_call"
    }
}
