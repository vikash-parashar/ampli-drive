package com.vikash.ampli.drive.media

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** What the music card says about where the sound comes from. */
object MediaSources {
    /**
     * Head units' Bluetooth music (A2DP sink / AVRCP) sessions: AOSP's own Bluetooth app and the
     * vendor apps on FYT / TS-style units (com.syu.bt, com.tw.bt...). Matched by package. Pure.
     */
    private val BT_PACKAGES = setOf(
        "com.android.bluetooth",
        "com.syu.bt",
        "com.syu.btmusic",
        "com.tw.bt",
        "com.tw.btmusic",
        "com.microntek.bluetooth",
        "com.microntek.btmusic",
        "com.ts.bt",
        "com.ts.btmusic",
        "com.fyt.bt",
        "com.nwd.bt.music",
        "com.autochips.bluetooth",
        "com.mediatek.bluetooth",
    )

    fun isBluetooth(pkg: String?): Boolean {
        val p = pkg?.lowercase() ?: return false
        if (p in BT_PACKAGES) return true
        // Vendor apps vary: "com.xyz.btmusic", "com.xyz.bluetooth.music", "com.xyz.a2dp".
        val parts = p.split('.')
        return parts.any { it == "bt" || it == "btmusic" || it == "bluetooth" || it == "a2dp" || it == "a2dpsink" || it == "btaudio" }
    }

    /** The app's launcher icon at 96 px, or null (uninstalled, or the system won't say). */
    fun icon(context: Context, pkg: String): ImageBitmap? = try {
        context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
