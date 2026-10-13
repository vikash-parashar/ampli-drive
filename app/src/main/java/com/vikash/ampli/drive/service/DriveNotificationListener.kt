package com.vikash.ampli.drive.service

import android.app.Notification
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import com.vikash.ampli.drive.nav.NavTurnRepository
import com.vikash.ampli.drive.nav.NextTurn

/**
 * Notification access does two jobs:
 * 1. It is the key that lets MediaSessionManager.getActiveSessions() list every player
 *    (the head unit's Bluetooth music app, Ampli, USB players). That part needs no code here.
 * 2. It reads Google Maps' ongoing navigation notification to fill the next-turn card.
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
    }

    override fun onListenerDisconnected() {
        connected = false
        NavTurnRepository.clear()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (isNavNotification(sbn)) handle(sbn)
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

    private fun loadArrow(n: Notification): Bitmap? = try {
        n.getLargeIcon()?.loadDrawable(this)?.toBitmap(ARROW_PX, ARROW_PX)
    } catch (_: Exception) {
        null
    }

    companion object {
        const val GOOGLE_MAPS = "com.google.android.apps.maps"
        private val NAV_PACKAGES = setOf(GOOGLE_MAPS)
        private const val ARROW_PX = 128

        /** True while the system has the listener bound (notification access granted). */
        @Volatile
        var connected: Boolean = false
            private set
    }
}
