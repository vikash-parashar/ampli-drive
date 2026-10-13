package com.vikash.ampli.drive.location

import java.time.LocalTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sunrise/sunset from the standard sunrise equation (accurate to a minute or two).
 * Works offline from the GPS position, so the launcher can switch day/night on its own.
 */
object SunTimes {

    /** True when the sun is below the horizon at [epochMillis] at this position. */
    fun isNight(epochMillis: Long, latitude: Double, longitude: Double): Boolean {
        val jd = epochMillis / 86_400_000.0 + 2_440_587.5
        val n = ceil(jd - 2_451_545.0 + 0.0008)
        val jStar = n - longitude / 360.0
        val m = norm360(357.5291 + 0.98560028 * jStar)
        val mRad = Math.toRadians(m)
        val c = 1.9148 * sin(mRad) + 0.0200 * sin(2 * mRad) + 0.0003 * sin(3 * mRad)
        val lambda = Math.toRadians(norm360(m + c + 180.0 + 102.9372))
        val jTransit = 2_451_545.0 + jStar + 0.0053 * sin(mRad) - 0.0069 * sin(2 * lambda)
        val decl = asin(sin(lambda) * sin(Math.toRadians(23.4397)))
        val phi = Math.toRadians(latitude)
        val cosW = (sin(Math.toRadians(-0.833)) - sin(phi) * sin(decl)) / (cos(phi) * cos(decl))
        if (cosW > 1.0) return true // polar night
        if (cosW < -1.0) return false // midnight sun
        val w = Math.toDegrees(acos(cosW))
        val rise = toEpochMillis(jTransit - w / 360.0)
        val set = toEpochMillis(jTransit + w / 360.0)
        return epochMillis < rise || epochMillis > set
    }

    /** Used before the first GPS fix: night from 18:30 to 06:00 local time. */
    fun isNightByClock(time: LocalTime): Boolean =
        time.isBefore(LocalTime.of(6, 0)) || time.isAfter(LocalTime.of(18, 30))

    private fun toEpochMillis(julianDay: Double): Long =
        ((julianDay - 2_440_587.5) * 86_400_000.0).toLong()

    private fun norm360(x: Double): Double {
        val r = x % 360.0
        return if (r < 0) r + 360.0 else r
    }
}
