package com.vikash.ampli.drive.prefs

import android.app.ActivityManager
import android.content.Context

/** Rough device class, used to pick cheap defaults on small head units. */
object DeviceClass {

    /**
     * 2.5 GiB. A "2 GB" unit reports about 1.8–1.9 GiB of total memory (the kernel and GPU keep
     * the rest), and a "3 GB" unit about 2.7 GiB, so this splits them cleanly.
     */
    const val LOW_RAM_BYTES: Long = 2_684_354_560L

    /** True on Android's low-RAM devices and on units with 2 GB of RAM or less. */
    fun isLowEnd(isLowRamDevice: Boolean, totalMemBytes: Long): Boolean =
        isLowRamDevice || (totalMemBytes in 1..LOW_RAM_BYTES)

    fun isLowEnd(context: Context): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return isLowEnd(am.isLowRamDevice, info.totalMem)
    }
}
