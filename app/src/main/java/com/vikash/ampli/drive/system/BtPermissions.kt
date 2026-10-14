package com.vikash.ampli.drive.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Bluetooth runtime permissions on every Android version the launcher supports.
 *
 * - Android 12+ (API 31): "Nearby devices", i.e. BLUETOOTH_CONNECT and BLUETOOTH_SCAN.
 * - Android 8 to 11: BLUETOOTH and BLUETOOTH_ADMIN are granted at install time, and a BLE scan
 *   only returns results with ACCESS_FINE_LOCATION.
 *
 * Many head units report a spoofed Android version, so this always goes by the real API level.
 */
object BtPermissions {
    private val modern get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Needed to list paired devices, read the connected phone and open an OBD socket. */
    val connect: String
        get() = if (modern) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH

    /** Needed to scan for BLE tyre sensors. */
    val scan: String
        get() = if (modern) Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION

    fun hasConnect(context: Context): Boolean = granted(context, connect)

    fun hasScan(context: Context): Boolean = granted(context, scan)

    private fun granted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
