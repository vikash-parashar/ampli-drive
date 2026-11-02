package com.vikash.ampli.drive.system

/**
 * What the Power sheet, the quick panel and "screen band karo" can do. Restart and Power off were
 * removed on purpose: the unit's own MCU owns power, and Car Hub no longer offers either.
 */
enum class PowerAction { ScreenOff }

/** How Screen off works. */
enum class ScreenOffRoute {
    /** Android's screen lock (device admin, force-lock only). Only when the driver chose it in Settings. */
    DeviceAdmin,

    /** A black cover over Ampli; a touch wakes it. Music keeps playing. */
    Overlay,
}

/** Pure rules for the power keys, unit-tested. */
object PowerPlan {
    /** "Screen band karo" by voice: a countdown first (nothing to tap), and "radd karo" stops it. */
    const val VOICE_SCREEN_OFF_S = 5

    /**
     * Where su usually lives on rooted head units and phones. Only the volume keys use root now
     * (`input keyevent`, when the unit ignores Android's volume calls); nothing restarts the unit.
     */
    val SU_PATHS = listOf(
        "/system/xbin/su", "/system/bin/su", "/sbin/su", "/su/bin/su", "/system/sbin/su",
        "/vendor/bin/su", "/system/su", "/data/local/xbin/su", "/data/local/bin/su",
    )

    /** True when any [SU_PATHS] entry exists (checked by [exists]). */
    fun hasSu(exists: (String) -> Boolean): Boolean = SU_PATHS.any { exists(it) }

    /**
     * Screen off locks the screen only when the driver switched that on and Android has the
     * device-admin grant. A head unit without keys may only wake from a lock with the ignition,
     * so the touch-to-wake cover is the default.
     */
    fun screenOffRoute(adminActive: Boolean, adminWanted: Boolean): ScreenOffRoute =
        if (adminActive && adminWanted) ScreenOffRoute.DeviceAdmin else ScreenOffRoute.Overlay

    /** Whole seconds left on a countdown of [totalS] after [elapsedMs]. */
    fun secondsLeft(totalS: Int, elapsedMs: Long): Int =
        (totalS - (elapsedMs.coerceAtLeast(0L) / 1000L).toInt()).coerceIn(0, totalS)
}
