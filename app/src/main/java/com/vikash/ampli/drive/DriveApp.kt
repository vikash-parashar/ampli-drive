package com.vikash.ampli.drive

import android.app.Application
import com.vikash.ampli.drive.bluetooth.BluetoothStatus
import com.vikash.ampli.drive.cameras.CameraAlerts
import com.vikash.ampli.drive.cameras.NoCameraAlerts
import com.vikash.ampli.drive.location.SpeedTracker
import com.vikash.ampli.drive.media.MediaHub
import com.vikash.ampli.drive.prefs.DrivePrefs

/** Holds the few long-lived objects. Nothing starts here; MainActivity starts them when visible. */
class DriveApp : Application() {
    val prefs: DrivePrefs by lazy { DrivePrefs(this) }

    /** Swap for the real engine when the camera database ships. */
    val cameraAlerts: CameraAlerts = NoCameraAlerts

    val speedTracker: SpeedTracker by lazy { SpeedTracker(this, cameraAlerts) }
    val mediaHub: MediaHub by lazy { MediaHub(this) }
    val bluetooth: BluetoothStatus by lazy { BluetoothStatus(this) }
}
