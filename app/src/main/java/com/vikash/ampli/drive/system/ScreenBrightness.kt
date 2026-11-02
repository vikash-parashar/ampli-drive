package com.vikash.ampli.drive.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Screen brightness in six steps, like the head unit's own quick app. Pure, unit-tested.
 *
 * The one table of levels Car Hub ever writes, on Android's 0..255 scale. On the TS7 (FYT-style,
 * the MCU drives the backlight) the panel is nearly black below about a quarter of the range and
 * barely changes in the top fifth, so a straight 0..100% slider was either black or full. Step 1
 * is still clearly visible at night; the other steps are spread over the range where the panel
 * actually changes. Tune the panel's feel here and nowhere else.
 */
object BrightnessSteps {
    /** Step 1 ..= 6 → Settings.System.SCREEN_BRIGHTNESS. Step 1 is the floor: never darker. */
    val LEVELS: IntArray = intArrayOf(45, 75, 110, 150, 200, 255)

    const val COUNT = 6
    const val MAX = 255

    /** Nothing Car Hub writes is below this ([LEVELS] step 1). */
    val FLOOR: Int get() = LEVELS[0]

    /** The step the sunset dim uses (Settings › Theme › dim at night). */
    const val NIGHT_STEP = 2

    /** The 0..255 value of [step] (clamped to 1..[COUNT]). */
    fun value(step: Int): Int = LEVELS[step.coerceIn(1, COUNT) - 1]

    /** The step nearest to a raw 0..255 value (set by the unit's own app, or unknown). */
    fun stepFor(raw: Int): Int {
        var best = 1
        var bestDist = Int.MAX_VALUE
        for (i in LEVELS.indices) {
            val d = kotlin.math.abs(LEVELS[i] - raw)
            if (d < bestDist) {
                bestDist = d
                best = i + 1
            }
        }
        return best
    }

    /** One step brighter or dimmer, never past 1 or [COUNT]. */
    fun next(step: Int, up: Boolean): Int = (step.coerceIn(1, COUNT) + if (up) 1 else -1).coerceIn(1, COUNT)

    /** A tap at [fraction] (0..1) across the six-segment bar: the segment under the finger. */
    fun fromFraction(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * COUNT).toInt().plus(1).coerceIn(1, COUNT)

    /** [value] as this window's override (0..1), for units where Car Hub may not change the setting. */
    fun windowValue(step: Int): Float = value(step) / MAX.toFloat()
}

/**
 * The system brightness. Android asks the driver once to let Car Hub "modify system settings"
 * (WRITE_SETTINGS); until then only Car Hub's own window can follow the step.
 *
 * No vendor (FYT / com.syu.*) brightness call is used: none is documented anywhere Car Hub can
 * verify, and the unit's backlight follows Settings.System.SCREEN_BRIGHTNESS in manual mode.
 */
object ScreenBrightness {

    fun allowed(context: Context): Boolean = try {
        Settings.System.canWrite(context)
    } catch (_: Exception) {
        false
    }

    /** The current step from the system setting, or null when Android gives none. */
    fun step(context: Context): Int? = try {
        BrightnessSteps.stepFor(Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
    } catch (_: Exception) {
        null
    }

    /**
     * Writes [step]'s level to the system: manual mode first (auto-brightness would undo it),
     * then the value, never below [BrightnessSteps.FLOOR]. False when Car Hub may not.
     */
    fun setStep(context: Context, step: Int): Boolean {
        if (!allowed(context)) return false
        return try {
            val cr = context.contentResolver
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, BrightnessSteps.value(step).coerceAtLeast(BrightnessSteps.FLOOR))
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * "Brightness up / down" by voice ([up] null: no change). Returns the step afterwards, or null
     * when Car Hub may not change it.
     */
    fun change(context: Context, up: Boolean?): Int? {
        val current = step(context) ?: BrightnessSteps.COUNT / 2
        if (up == null) return current
        if (!allowed(context)) return null
        val next = BrightnessSteps.next(current, up)
        return if (setStep(context, next)) next else null
    }

    /** Android's "Modify system settings" switch for Car Hub. */
    fun openPermission(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }
}
