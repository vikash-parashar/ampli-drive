package com.vikash.ampli.drive.bluetooth

import java.util.Locale

/** Plain rules for phone music over Bluetooth. Unit-tested. */
object PhoneBtRules {

    /** BluetoothClass.Device.Major values (copied so the rules stay plain Kotlin). */
    const val MAJOR_MISC = 0x0000
    const val MAJOR_PHONE = 0x0200
    const val MAJOR_UNCATEGORIZED = 0x1F00

    /** A phone, or a device that does not say what it is (many phones on head units). */
    fun isPhoneClass(major: Int?): Boolean = major == null || major == MAJOR_PHONE || major == MAJOR_UNCATEGORIZED || major == MAJOR_MISC

    enum class AutoPlay { Play, Off, NotPhone, InCall, UserPaused, AlreadyPlaying }

    /** Don't press play again within this long after the driver paused. */
    const val PAUSE_RESPECT_MS = 60_000L

    /** Wait for A2DP after the phone's ACL link comes up, then for sound after pressing play. */
    const val A2DP_WAIT_MS = 4_000L
    const val SOUND_WAIT_MS = 3_000L

    /**
     * Whether to press play when a phone connects. [lastUserPauseAtMs] is when the driver last
     * paused (0 = never), on the same clock as [nowMs].
     */
    fun autoPlay(
        enabled: Boolean,
        phoneClass: Boolean,
        inCall: Boolean,
        musicActive: Boolean,
        lastUserPauseAtMs: Long,
        nowMs: Long,
    ): AutoPlay = when {
        !enabled -> AutoPlay.Off
        !phoneClass -> AutoPlay.NotPhone
        inCall -> AutoPlay.InCall
        lastUserPauseAtMs > 0 && nowMs - lastUserPauseAtMs in 0 until PAUSE_RESPECT_MS -> AutoPlay.UserPaused
        musicActive -> AutoPlay.AlreadyPlaying
        else -> AutoPlay.Play
    }

    /**
     * How much an installed app looks like the head unit's own Bluetooth app ("Bluetooth", "BT",
     * "蓝牙", com.syu.bt…). 0 = not it. Android's own Bluetooth service and Settings are excluded.
     */
    fun vendorBtScore(label: String, packageName: String, ownPackage: String = ""): Int {
        val p = packageName.lowercase(Locale.ROOT)
        if (p == "com.android.bluetooth" || p == "com.android.settings" || p.startsWith("com.vikash.ampli")) return 0
        if (ownPackage.isNotEmpty() && p == ownPackage) return 0
        val l = label.lowercase(Locale.ROOT).trim()
        // Phone-mirroring apps and BLE tyre or OBD tools are not the unit's Bluetooth app.
        if (l.contains("link") || l.contains("obd") || l.contains("tpms") || l.contains("mirror")) return 0
        var s = when {
            l in EXACT -> 100
            l.contains("bluetooth") || l.contains("蓝牙") || l.contains("ब्लूटूथ") -> 80
            l.startsWith("bt ") || l.endsWith(" bt") -> 75
            else -> 0
        }
        val segments = p.split('.')
        if (p.contains("bluetooth")) s = maxOf(s, 60)
        if (segments.any { it == "bt" || it.startsWith("bt") || it.endsWith("bt") } && segments.size > 1) s = maxOf(s, 50)
        return s
    }

    const val VENDOR_GOOD_ENOUGH = 50

    private val EXACT = setOf("bluetooth", "bt", "蓝牙", "bt music", "bt phone", "bluetooth music", "bluetooth phone", "ब्लूटूथ")

    /** The vendor app's music screen among its exported activities ("…MusicActivity", "…A2dp…"). */
    fun musicActivity(activities: List<String>): String? =
        activities.firstOrNull { it.lowercase(Locale.ROOT).substringAfterLast('.').contains("music") }
            ?: activities.firstOrNull { it.lowercase(Locale.ROOT).contains("music") }
            ?: activities.firstOrNull { it.lowercase(Locale.ROOT).contains("a2dp") }
}
