package com.vikash.ampli.drive.system

/**
 * When to join the driver's phone hotspot. Plain Kotlin, unit-tested; [HotspotLink] does the
 * Wi-Fi calls.
 */
object HotspotLogic {
    /** Android stores Wi-Fi names in quotes ("\"Vikash's Phone\""); hex names have none. */
    fun unquote(ssid: String?): String? {
        val s = ssid?.trim() ?: return null
        if (s.isEmpty() || s == "<unknown ssid>") return null
        return if (s.length >= 2 && s.startsWith('"') && s.endsWith('"')) s.substring(1, s.length - 1) else s
    }

    fun quote(ssid: String): String = "\"" + ssid + "\""

    fun same(a: String?, b: String?): Boolean {
        val x = unquote(a) ?: return false
        val y = unquote(b) ?: return false
        return x == y
    }

    /** Don't try again sooner than this after an attempt. */
    const val RETRY_MS = 60_000L

    /**
     * Join now? Only with a hotspot chosen, not already on it, not on some other Wi-Fi (a home
     * network stays), and not tried in the last [RETRY_MS]. [seen] is true when the hotspot is
     * in the scan results or the paired phone just connected over Bluetooth.
     */
    fun shouldConnect(
        chosenSsid: String?,
        currentSsid: String?,
        onOtherWifi: Boolean,
        seen: Boolean,
        lastTryAt: Long,
        now: Long,
    ): Boolean {
        if (unquote(chosenSsid) == null) return false
        if (same(chosenSsid, currentSsid)) return false
        if (onOtherWifi) return false
        if (!seen) return false
        return now - lastTryAt >= RETRY_MS || lastTryAt <= 0L || now < lastTryAt
    }

    /** The Bluetooth device that means "the phone is here": same name, case and spaces ignored. */
    fun isPairedPhone(chosenName: String?, deviceName: String?): Boolean {
        val a = chosenName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
        val b = deviceName?.trim()?.lowercase() ?: return false
        return a == b
    }
}
