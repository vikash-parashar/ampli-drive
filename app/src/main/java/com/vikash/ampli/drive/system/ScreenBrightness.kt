package com.vikash.ampli.drive.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlin.math.roundToInt

/** Brightness steps by voice, pure for tests. Android's setting is 0 to 255. */
object BrightnessMath {
    const val MAX = 255

    /** A fifth of the range per "brighter" / "dimmer", never fully black. */
    fun next(current: Int, up: Boolean): Int {
        val step = MAX / 5
        val v = if (up) current + step else current - step
        return v.coerceIn(MIN, MAX)
    }

    fun percent(value: Int): Int = (value * 100.0 / MAX).roundToInt().coerceIn(0, 100)

    /** The darkest a voice command goes, so the screen never looks switched off. */
    const val MIN = 10
}

/**
 * "Hey Ampli, brightness up / down": the system brightness. Android asks the driver once to let
 * Car Hub "modify system settings" (WRITE_SETTINGS); until then [change] returns null.
 */
object ScreenBrightness {

    fun allowed(context: Context): Boolean = try {
        Settings.System.canWrite(context)
    } catch (_: Exception) {
        false
    }

    /**
     * Steps the brightness ([up] null: no change) and returns the level in percent, or null when
     * Car Hub may not change it. Auto-brightness is switched off, else the step would be undone.
     */
    fun change(context: Context, up: Boolean?): Int? {
        val cr = context.contentResolver
        return try {
            val current = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, BrightnessMath.MAX / 2)
            if (up == null) return BrightnessMath.percent(current)
            if (!allowed(context)) return null
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            val next = BrightnessMath.next(current, up)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, next)
            BrightnessMath.percent(next)
        } catch (_: Exception) {
            null
        }
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
