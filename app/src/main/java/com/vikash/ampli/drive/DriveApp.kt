package com.vikash.ampli.drive

import android.app.Application
import android.content.Context
import com.vikash.ampli.drive.billing.ProBilling
import com.vikash.ampli.drive.bluetooth.BluetoothStatus
import com.vikash.ampli.drive.cameras.CameraAlerts
import com.vikash.ampli.drive.car.CarMonitor
import com.vikash.ampli.drive.cameras.CameraRepository
import com.vikash.ampli.drive.cameras.LiveCameraAlerts
import com.vikash.ampli.drive.emergency.EmergencyStore
import com.vikash.ampli.drive.fastag.FastagStore
import com.vikash.ampli.drive.i18n.AppLanguages
import com.vikash.ampli.drive.location.SpeedTracker
import com.vikash.ampli.drive.media.MediaHub
import com.vikash.ampli.drive.obd.ObdClient
import com.vikash.ampli.drive.prefs.DrivePrefs
import com.vikash.ampli.drive.tpms.TpmsMonitor
import com.vikash.ampli.drive.voice.Speaker
import com.vikash.ampli.drive.voice.VoiceAssistant
import com.vikash.ampli.drive.voice.WakeModel

/** Holds the few long-lived objects. Nothing starts here; MainActivity starts them when visible. */
class DriveApp : Application() {
    val prefs: DrivePrefs by lazy { DrivePrefs(this) }

    /** Offline speed cameras (OpenStreetMap, ODbL, plus the driver's reports); loads on first use. */
    val cameras: CameraRepository by lazy { CameraRepository(this) }
    val cameraAlerts: CameraAlerts by lazy { LiveCameraAlerts(this) }

    /** One text-to-speech engine for the assistant and the camera alerts. */
    val speaker: Speaker by lazy { Speaker(this) }

    val speedTracker: SpeedTracker by lazy { SpeedTracker(this, cameraAlerts) }
    val mediaHub: MediaHub by lazy { MediaHub(this) }
    val bluetooth: BluetoothStatus by lazy { BluetoothStatus(this) }
    val billing: ProBilling by lazy { ProBilling(this, prefs) }
    val fastag: FastagStore by lazy { FastagStore(this) }
    val emergency: EmergencyStore by lazy { EmergencyStore(this) }

    /** Car owner features: break reminder, papers, parking, crash detection, weather, expenses. */
    val car: CarMonitor by lazy { CarMonitor(this) }

    /** OBD-II dongle (Pro) and BLE tyre sensors (Pro). */
    val obd: ObdClient by lazy { ObdClient(this) }
    val tpms: TpmsMonitor by lazy { TpmsMonitor(this) }

    /** "Hey Ampli": the conversation, and the offline wake-word model it downloads once. */
    val voice: VoiceAssistant by lazy { VoiceAssistant(this) }
    val wakeModel: WakeModel by lazy { WakeModel(this) }

    /** Android 12: strings fetched through the app context follow the in-app language too. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguages.wrap(base))
    }
}
