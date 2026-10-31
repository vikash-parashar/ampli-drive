package com.vikash.ampli.drive.calls

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.voice.HaryanviLines.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings › Announce calls. Off by default; its own small file. */
class CallPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(sp.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _listen = MutableStateFlow(sp.getBoolean(KEY_LISTEN, true))

    /** Listen for "हाँ / ना" after the announcement (on by default once calls are announced). */
    val listen: StateFlow<Boolean> = _listen.asStateFlow()

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        sp.edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun setListen(on: Boolean) {
        _listen.value = on
        sp.edit().putBoolean(KEY_LISTEN, on).apply()
    }

    private companion object {
        const val FILE = "ampli_calls"
        const val KEY_ENABLED = "announce"
        const val KEY_LISTEN = "listen"
    }
}

/**
 * Free: "भाई, मम्मी का फोन आ रया सै, ठा ल्यूं?" when a call rings, then one "हाँ / ना" by voice.
 *
 * Where the ring comes from:
 * - the phone's own line (phones running Car Hub): TelephonyManager's call state, with
 *   READ_PHONE_STATE. The number comes with it on Android 8 / 9 only.
 * - a ringing call notification (the dialer, a head unit's Bluetooth phone app, a VoIP app),
 *   through the notification listener: its title is usually the caller's name.
 *
 * The name: the contact (READ_CONTACTS, looked up on the unit), else the notification's title,
 * else the number read digit by digit. Nothing about the call is stored or sent anywhere.
 *
 * Picking up: TelecomManager.acceptRingingCall with ANSWER_PHONE_CALLS (Android 8+); declining:
 * TelecomManager.endCall (Android 9+). Without those, the ringing notification's own Answer /
 * Decline button is pressed. When neither is possible (a head unit whose phone app hides its
 * buttons) the card asks for a tap and "ना" only stops the announcement.
 */
class CallAnnouncer(private val app: DriveApp) {
    val prefs: CallPrefs by lazy { CallPrefs(app) }
    private val scope = MainScope()
    private var job: Job? = null
    private var listening = false

    /** What the call card on screen shows; null when no call is ringing. */
    data class Card(
        val id: Long,
        /** Contact name, notification title, or the masked number. */
        val title: String,
        /** "+91 98xxx xx210" under the name, when known. */
        val number: String?,
        val phase: Phase,
    ) {
        enum class Phase { Asking, Listening, Answered, Declined, TapToAnswer }
    }

    private val _card = MutableStateFlow<Card?>(null)
    val card: StateFlow<Card?> = _card.asStateFlow()

    // The current ring: from the phone line, a notification, or both.
    private var ringId = 0L
    private var number: String? = null
    private var notifTitle: String? = null
    private var notifKey: String? = null
    private var answerAction: PendingIntent? = null
    private var declineAction: PendingIntent? = null

    private var started = false
    private var registered = false

    @Suppress("DEPRECATION")
    private val stateListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> onRinging(phoneNumber?.takeIf { it.isNotBlank() }, fromNotification = false)
                else -> onRingEnded()
            }
        }
    }

    /** Idempotent: MainActivity and the background monitor call it with the other listeners. */
    fun start() {
        started = true
        refresh()
    }

    /** Settings changed or a permission was granted: listen to the phone line when allowed. */
    @Suppress("DEPRECATION")
    fun refresh() {
        if (!started) return
        val want = prefs.enabled.value && granted(Manifest.permission.READ_PHONE_STATE)
        val tm = app.getSystemService(TelephonyManager::class.java) ?: return
        try {
            if (want && !registered) {
                tm.listen(stateListener, PhoneStateListener.LISTEN_CALL_STATE)
                registered = true
            } else if (!want && registered) {
                tm.listen(stateListener, PhoneStateListener.LISTEN_NONE)
                registered = false
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "call state: ${e.message}")
        }
    }

    /** Notification listener: every posted notification (cheap exit for anything not a ringing call). */
    fun onNotification(sbn: StatusBarNotification) {
        if (!prefs.enabled.value) return
        val n = sbn.notification ?: return
        if (n.category != Notification.CATEGORY_CALL) return
        if (!isRinging(n)) return
        notifKey = sbn.key
        notifTitle = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        answerAction = null
        declineAction = null
        for (a in n.actions.orEmpty()) {
            when (CallActions.classify(a.title)) {
                CallActions.Kind.Answer -> answerAction = a.actionIntent
                CallActions.Kind.Decline -> declineAction = a.actionIntent
                CallActions.Kind.Other -> Unit
            }
        }
        onRinging(null, fromNotification = true)
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == notifKey) {
            notifKey = null
            answerAction = null
            declineAction = null
            // The phone line, when it is watched, says when ringing really stops.
            if (!registered) onRingEnded()
        }
    }

    /** An incoming-call notification: a full-screen intent, or Android 12's CallStyle "incoming". */
    private fun isRinging(n: Notification): Boolean {
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0 && n.fullScreenIntent == null) return false
        return n.fullScreenIntent != null || n.extras?.getInt(EXTRA_CALL_TYPE, 0) == CALL_TYPE_INCOMING
    }

    private fun onRinging(phoneNumber: String?, fromNotification: Boolean) {
        if (!prefs.enabled.value) return
        if (phoneNumber != null) number = phoneNumber
        val current = _card.value
        if (current != null && job?.isActive == true) {
            // The second source of the same ring: a better name for the card, nothing said twice.
            _card.value = current.copy(title = displayName(), number = number?.let { CallerLabel.maskedNumber(it) })
            return
        }
        if (current != null) return
        // Never over the crash countdown or an SOS just opened.
        if (app.car.crashAt.value != null || app.car.sosOpenedRecently()) return
        val id = ++ringId
        _card.value = Card(id, displayName(), number?.let { CallerLabel.maskedNumber(it) }, Card.Phase.Asking)
        job?.cancel()
        job = scope.launch {
            // The notification (with the name) usually follows the ring by a moment.
            if (!fromNotification) delay(NAME_WAIT_MS)
            val contact = number?.let { lookupContact(it) }
            val spokenName = CallerLabel.spoken(contact ?: notifTitle?.takeIf { !looksLikeNumber(it) }, number ?: notifTitle)
            _card.value = _card.value?.takeIf { it.id == id }?.copy(title = contact ?: displayName())
            announce(spokenName)
            if (_card.value?.id != id) return@launch
            if (!prefs.listen.value || !app.voice.canListen()) return@launch
            repeat(2) {
                if (_card.value?.id != id) return@launch
                _card.value = _card.value?.copy(phase = Card.Phase.Listening)
                listening = true
                val heard = try {
                    app.voice.listenOnce()
                } finally {
                    listening = false
                }
                if (_card.value?.id != id) return@launch
                _card.value = _card.value?.copy(phase = Card.Phase.Asking)
                when (VoiceReplies.call(heard.orEmpty())) {
                    VoiceReplies.Call.Answer -> {
                        answer(spoken = true)
                        return@launch
                    }
                    VoiceReplies.Call.Reject -> {
                        later(spoken = true)
                        return@launch
                    }
                    VoiceReplies.Call.Unknown -> if (heard == null) return@launch // Silence: leave the card for a tap.
                }
            }
        }
    }

    private fun onRingEnded() {
        job?.cancel()
        job = null
        if (listening) app.voice.cancelListen()
        number = null
        notifTitle = null
        val c = _card.value ?: return
        // A picked-up call shows "picked up" a moment; anything else closes now.
        if (c.phase == Card.Phase.Answered) {
            scope.launch {
                delay(1_500L)
                if (_card.value?.id == c.id) _card.value = null
            }
        } else {
            _card.value = null
        }
    }

    /** The card's "हाँ, ठा ले" (or the spoken "हाँ"). */
    fun answer(spoken: Boolean = false) {
        val c = _card.value ?: return
        if (!spoken) job?.cancel()
        val ok = tryAnswer()
        _card.value = c.copy(phase = if (ok) Card.Phase.Answered else Card.Phase.TapToAnswer)
        scope.launch {
            if (ok) {
                // Silent while the call starts; a short line only after a spoken "हाँ".
                if (spoken) say(Event.CallAnswered, R.string.call_voice_answered)
            } else {
                // The phone's (or the unit's) own ringing screen has the green button.
                say(Event.CallManual, R.string.call_voice_manual)
            }
        }
    }

    /** The card's "बाद मैं" (or the spoken "ना"): decline when allowed, else just stop announcing. */
    fun later(spoken: Boolean = false) {
        val c = _card.value ?: return
        if (!spoken) job?.cancel()
        val declined = tryDecline()
        _card.value = null
        if (declined && spoken) scope.launch { say(Event.CallRejected, R.string.call_voice_declined) }
        Log.d(TAG, "later: declined=$declined (card ${c.id})")
    }

    /** Closes the card without touching the call. */
    fun dismiss() {
        job?.cancel()
        _card.value = null
    }

    @Suppress("DEPRECATION")
    private fun tryAnswer(): Boolean {
        if (granted(Manifest.permission.ANSWER_PHONE_CALLS)) {
            try {
                app.getSystemService(TelecomManager::class.java)?.acceptRingingCall()
                // The phone line says if it worked; a head unit's Bluetooth call isn't Telecom's.
                if (registered || answerAction == null) return true
            } catch (e: Exception) {
                Log.w(TAG, "acceptRingingCall: ${e.message}")
            }
        }
        return send(answerAction)
    }

    private fun tryDecline(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && granted(Manifest.permission.ANSWER_PHONE_CALLS)) {
            try {
                @Suppress("DEPRECATION")
                val ended = app.getSystemService(TelecomManager::class.java)?.endCall() == true
                if (ended) return true
            } catch (e: Exception) {
                Log.w(TAG, "endCall: ${e.message}")
            }
        }
        return send(declineAction)
    }

    private fun send(intent: PendingIntent?): Boolean {
        intent ?: return false
        return try {
            intent.send()
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        }
    }

    private suspend fun announce(name: String?) {
        if (name != null) {
            val line = app.desi.line(Event.CallAnnounce, name)
            if (line != null) {
                app.voicePlayer.say(line)
                return
            }
            app.speaker.say(plain().getString(R.string.call_voice_ask, name), app.car.language())
        } else {
            say(Event.CallUnknown, R.string.call_voice_ask_unknown)
        }
    }

    /** The desi line, or the plain app string (Standard style, or a recorded voice without the clip). */
    private suspend fun say(event: Event, plainRes: Int) {
        val line = app.desi.line(event)
        if (line != null) app.voicePlayer.say(line) else app.speaker.say(plain().getString(plainRes), app.car.language())
    }

    private fun plain(): Context = app.car.localized()

    private fun displayName(): String =
        notifTitle ?: number?.let { CallerLabel.maskedNumber(it) } ?: app.getString(R.string.call_unknown_caller)

    private fun looksLikeNumber(s: String): Boolean = s.count { it.isDigit() } >= 6

    private suspend fun lookupContact(number: String): String? {
        if (!granted(Manifest.permission.READ_CONTACTS)) return null
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                app.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.trim()?.takeIf { it.isNotEmpty() } else null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun granted(permission: String): Boolean = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "CallAnnouncer"
        private const val NAME_WAIT_MS = 700L

        /** Notification.EXTRA_CALL_TYPE / CallStyle.CALL_TYPE_INCOMING (API 31), by value for older units. */
        private const val EXTRA_CALL_TYPE = "android.callType"
        private const val CALL_TYPE_INCOMING = 1

        /** The runtime permissions Settings › Announce calls asks for (all optional but the first). */
        val PERMISSIONS = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ANSWER_PHONE_CALLS,
        )
    }
}
