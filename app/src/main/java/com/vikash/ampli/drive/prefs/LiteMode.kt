package com.vikash.ampli.drive.prefs

/**
 * Lite mode for weak head units (1-2 GB, quad Cortex-A7, small GPU). It only takes away motion and
 * effects; the layout, colours and text stay exactly the same:
 * - decorative animations stand still (the boot splash shows its last frame, mic rings don't pulse);
 * - the map draws at most [MAP_MAX_FPS] frames a second, the car marker animates at that rate too,
 *   3D buildings and hill shading are left out, and the navigation camera stays flat (no tilt).
 *
 * On by itself on low-memory units ([DeviceClass.isLowEnd]); Settings › About › Diagnostics can
 * force it on or off. Read once at start ([enabled]); a change applies from the next start.
 */
object LiteMode {
    enum class Choice(val key: String) {
        Auto("auto"),
        On("on"),
        Off("off"),
        ;

        companion object {
            fun from(key: String?): Choice = entries.firstOrNull { it.key == key } ?: Auto
        }
    }

    /** Map frame cap in lite mode (the normal cap is 30). */
    const val MAP_MAX_FPS = 15

    /** Whether lite mode is on for [choice] on a unit that is [lowEnd] or not. */
    fun resolve(choice: Choice, lowEnd: Boolean): Boolean = when (choice) {
        Choice.On -> true
        Choice.Off -> false
        Choice.Auto -> lowEnd
    }

    /** The Diagnostics switch: Auto, then On, then Off, then Auto again. */
    fun next(choice: Choice): Choice = when (choice) {
        Choice.Auto -> Choice.On
        Choice.On -> Choice.Off
        Choice.Off -> Choice.Auto
    }

    /** Set once in DriveApp.onCreate. False in screen previews and tests, so they look as designed. */
    @Volatile
    var enabled: Boolean = false
}
