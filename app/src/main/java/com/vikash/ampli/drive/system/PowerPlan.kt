package com.vikash.ampli.drive.system

/** Screen off, Restart and Power off: the Power sheet, the quick panel and "screen band karo". */
enum class PowerAction { ScreenOff, Restart, PowerOff }

/**
 * Ways Ampli can restart or power off the head unit, best first.
 * [Root]: `su -c reboot` (many FYT / TS-type units ship with su).
 * [Helper]: the "Ampli power helper" accessibility service opens Android's own power menu, where
 * the driver taps Restart or Power off. Android does not let a normal app restart the unit itself.
 *
 * No vendor broadcast is tried: none of the FYT / TS7 power intents is documented anywhere Ampli
 * can verify, and a guessed one could do something else on another unit.
 */
enum class PowerRoute { Root, Helper }

/** What a tap on Restart or Power off shows first. */
enum class PowerStep {
    /** Screen off: acts at once. */
    Now,

    /** Restart / Power off: the 5-second countdown with Cancel. */
    Countdown,

    /** Rooted unit, first time: the "restarts directly" note, then the countdown. */
    RootNotice,

    /** No root and no helper: the one-time "turn on the Ampli power helper" card. */
    SetupHelper,
}

/** How Screen off works. */
enum class ScreenOffRoute {
    /** Android's screen lock (device admin, force-lock only). Only when the driver chose it in Settings. */
    DeviceAdmin,

    /** A black cover over Ampli with the backlight at its lowest; a touch wakes it. Music keeps playing. */
    Overlay,
}

/** Pure rules for the power keys, unit-tested. */
object PowerPlan {
    /** Restart and Power off never act on the first tap: a countdown with a big Cancel first. */
    const val COUNTDOWN_S = 5

    /** "Screen band karo" by voice: a countdown first (nothing to tap), and "radd karo" stops it. */
    const val VOICE_SCREEN_OFF_S = 5

    /** How long the "Tap Restart here" pointer stays next to Android's power menu. */
    const val POINTER_HINT_MS = 8_000L

    /** How long su may take (its "allow root" prompt included) before the helper is tried. */
    const val ROOT_WAIT_S = 20L

    /** Where su usually lives on rooted head units and phones. */
    val SU_PATHS = listOf(
        "/system/xbin/su", "/system/bin/su", "/sbin/su", "/su/bin/su", "/system/sbin/su",
        "/vendor/bin/su", "/system/su", "/data/local/xbin/su", "/data/local/bin/su",
    )

    /** True when any [SU_PATHS] entry exists (checked by [exists]). */
    fun hasSu(exists: (String) -> Boolean): Boolean = SU_PATHS.any { exists(it) }

    /** The routes to try at the end of the countdown, in order. Empty: nothing can do it. */
    fun routes(rootAvailable: Boolean, helperOn: Boolean): List<PowerRoute> = buildList {
        if (rootAvailable) add(PowerRoute.Root)
        if (helperOn) add(PowerRoute.Helper)
    }

    /** What a tap on [action] shows first. */
    fun firstStep(action: PowerAction, rootAvailable: Boolean, helperOn: Boolean, rootNoticeSeen: Boolean): PowerStep = when {
        action == PowerAction.ScreenOff -> PowerStep.Now
        rootAvailable && !rootNoticeSeen -> PowerStep.RootNotice
        routes(rootAvailable, helperOn).isNotEmpty() -> PowerStep.Countdown
        else -> PowerStep.SetupHelper
    }

    /** The shell command run as root, or null for Screen off (never needs root). */
    fun rootCommand(action: PowerAction): String? = when (action) {
        PowerAction.Restart -> "reboot"
        PowerAction.PowerOff -> "reboot -p"
        PowerAction.ScreenOff -> null
    }

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
