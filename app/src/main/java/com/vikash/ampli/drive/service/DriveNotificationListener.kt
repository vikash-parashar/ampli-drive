package com.vikash.ampli.drive.service

import android.app.Notification
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.fastag.FastagParser
import com.vikash.ampli.drive.nav.NavTurnRepository
import com.vikash.ampli.drive.nav.NextTurn

/**
 * Notification access does three jobs:
 * 1. It is the key that lets MediaSessionManager.getActiveSessions() list every player
 *    (the head unit's Bluetooth music app, Ampli, USB players). That part needs no code here.
 * 2. It reads Google Maps' ongoing navigation notification to fill the next-turn card.
 * 3. It reads SMS notifications (Google Messages, Samsung, AOSP, the default SMS app, phone-link
 *    apps) for FASTag toll debits. No READ_SMS permission: only what the notification shows.
 */
class DriveNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        val active = try {
            activeNotifications
        } catch (_: Exception) {
            null
        }
        active?.firstOrNull { isNavNotification(it) }?.let { handle(it) }
        // SMS that arrived while the listener was unbound: stored; only recent ones get the pop-up card.
        val now = System.currentTimeMillis()
        active?.filter { isSmsNotification(it) }?.forEach { handleSms(it, announce = now - it.postTime < ANNOUNCE_MS) }
    }

    override fun onListenerDisconnected() {
        connected = false
        NavTurnRepository.clear()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (isNavNotification(sbn)) {
            handle(sbn)
        } else if (isSmsNotification(sbn)) {
            handleSms(sbn, announce = true)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in NAV_PACKAGES && sbn.isOngoing) {
            NavTurnRepository.clear(sbn.packageName)
        }
    }

    private fun isNavNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName !in NAV_PACKAGES) return false
        val n = sbn.notification ?: return false
        // Google Maps posts its turn-by-turn card as an ongoing notification in the
        // navigation category; other Maps notifications (traffic, reviews) are not ongoing.
        return n.category == Notification.CATEGORY_NAVIGATION || sbn.isOngoing
    }

    private fun handle(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        if (title.isNullOrEmpty()) return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
        NavTurnRepository.post(
            NextTurn(
                title = title,
                text = text?.takeIf { it.isNotEmpty() },
                subText = sub?.takeIf { it.isNotEmpty() },
                arrow = loadArrow(n),
                sourcePackage = sbn.packageName,
            ),
        )
    }

    private fun isSmsNotification(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName
        if (pkg in SMS_PACKAGES) return true
        if (defaultSmsPackage == null) {
            defaultSmsPackage = try {
                Telephony.Sms.getDefaultSmsPackage(this) ?: ""
            } catch (_: Exception) {
                ""
            }
        }
        return pkg == defaultSmsPackage
    }

    /** Parses every message text the SMS notification carries (single, inbox or messaging style). */
    private fun handleSms(sbn: StatusBarNotification, announce: Boolean) {
        val store = (application as? DriveApp)?.fastag ?: return
        // Only after the FASTag SMS disclosure was accepted (Play: prominent disclosure); nothing
        // but FASTag-keyword messages is parsed, and only the parsed toll fields are stored.
        if (!store.readsSms) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        // text -> (sender, time). The same text often appears in several extras; keep the first.
        val texts = LinkedHashMap<String, Pair<String?, Long>>()
        // MessagingStyle (Google Messages, Samsung): one bundle per message with its own time.
        @Suppress("DEPRECATION")
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { p ->
            val b = p as? Bundle ?: return@forEach
            val text = b.getCharSequence("text")?.toString() ?: return@forEach
            val time = b.getLong("time").takeIf { it > 0 } ?: sbn.postTime
            texts.putIfAbsent(text, (b.getCharSequence("sender")?.toString() ?: title) to time)
        }
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let { texts.putIfAbsent(it, title to sbn.postTime) }
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let { texts.putIfAbsent(it, title to sbn.postTime) }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { texts.putIfAbsent(it.toString(), title to sbn.postTime) }

        for ((text, meta) in texts) {
            if (!FastagParser.mentionsFastag(text)) continue
            val parsed = FastagParser.parse(text, meta.first) ?: continue
            store.add(parsed, meta.second, announce)
        }
    }

    private fun loadArrow(n: Notification): Bitmap? = try {
        n.getLargeIcon()?.loadDrawable(this)?.toBitmap(ARROW_PX, ARROW_PX)
    } catch (_: Exception) {
        null
    }

    companion object {
        const val GOOGLE_MAPS = "com.google.android.apps.maps"
        private val NAV_PACKAGES = setOf(GOOGLE_MAPS)

        /**
         * SMS apps whose notifications may carry FASTag messages. The system default SMS app is
         * added at runtime. com.syu.bt and com.android.bluetooth post SMS that head units pull
         * from the paired phone over Bluetooth MAP; Truecaller is a common SMS app in India.
         */
        private val SMS_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.android.messaging",
            "com.oneplus.mms",
            "com.truecaller",
            "com.syu.bt",
            "com.android.bluetooth",
        )

        /** Newer SMS found at startup still get the home-screen card. */
        private const val ANNOUNCE_MS = 2L * 60 * 1000
        private const val ARROW_PX = 128

        private var defaultSmsPackage: String? = null

        /** True while the system has the listener bound (notification access granted). */
        @Volatile
        var connected: Boolean = false
            private set
    }
}
