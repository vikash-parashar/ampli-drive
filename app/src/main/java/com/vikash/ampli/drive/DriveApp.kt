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
import com.vikash.ampli.drive.feedback.FeedbackRepository
import com.vikash.ampli.drive.i18n.AppLanguages
import com.vikash.ampli.drive.location.SpeedTracker
import com.vikash.ampli.drive.media.MediaHub
import com.vikash.ampli.drive.nav.InAppNavigator
import com.vikash.ampli.drive.nav.NavPrompter
import com.vikash.ampli.drive.obd.ObdClient
import com.vikash.ampli.drive.prefs.DrivePrefs
import com.vikash.ampli.drive.tpms.TpmsMonitor
import com.vikash.ampli.drive.voice.DesiVoice
import com.vikash.ampli.drive.voice.Speaker
import com.vikash.ampli.drive.voice.VoiceAssistant
import com.vikash.ampli.drive.voice.VoicePlayer
import com.vikash.ampli.drive.voice.WelcomeGreeter
import com.vikash.ampli.drive.voice.WakeModel

/** Holds the few long-lived objects. Nothing starts here; MainActivity starts them when visible. */
class DriveApp : Application() {
    val prefs: DrivePrefs by lazy { DrivePrefs(this) }

    /** Offline speed cameras (OpenStreetMap, ODbL, plus the driver's reports); loads on first use. */
    val cameras: CameraRepository by lazy { CameraRepository(this) }
    val cameraAlerts: CameraAlerts by lazy { LiveCameraAlerts(this) }

    /** One text-to-speech engine for the assistant and the camera alerts. */
    val speaker: Speaker by lazy { Speaker(this) }

    /** Recorded voice pack (Settings › Voice: Male / Female), falling back to [speaker]. */
    val voicePlayer: VoicePlayer by lazy { VoicePlayer(this) }

    /** Settings › Voice style "Haryanvi desi": playful lines for greetings, alerts and replies. */
    val desi: DesiVoice by lazy { DesiVoice(this) }

    /** "Ram Ram {name} Ji, aaj kit ka plan hai?" once per car start (CarStart). */
    val welcome: WelcomeGreeter by lazy { WelcomeGreeter(this) }

    /** Speaks navigation turns: Car Hub's own, and Google Maps' (Settings › Haryanvi navigation prompts). */
    val navPrompter: NavPrompter by lazy { NavPrompter(this) }

    /** Turn-by-turn on Car Hub's own map (OSRM routes). Settings › Navigate with. */
    val navigator: InAppNavigator by lazy { InAppNavigator(this) }

    val speedTracker: SpeedTracker by lazy { SpeedTracker(this, cameraAlerts) }
    val mediaHub: MediaHub by lazy { MediaHub(this) }
    val bluetooth: BluetoothStatus by lazy { BluetoothStatus(this) }
    val billing: ProBilling by lazy { ProBilling(this, prefs) }

    /** Ampli Shell: full screen, startup animation, the Ampli app list and its PIN. */
    val shell: com.vikash.ampli.drive.prefs.ShellPrefs by lazy { com.vikash.ampli.drive.prefs.ShellPrefs(this) }
    val fastag: FastagStore by lazy { FastagStore(this) }

    /** Pro: WhatsApp messages read aloud (Settings, off by default). */
    val messages: com.vikash.ampli.drive.messages.WhatsAppReader by lazy { com.vikash.ampli.drive.messages.WhatsAppReader(this) }
    val emergency: EmergencyStore by lazy { EmergencyStore(this) }

    /** Car owner features: break reminder, papers, parking, crash detection, weather, expenses. */
    val car: CarMonitor by lazy { CarMonitor(this) }

    /** Papers (and FASTag pass) reminders spoken at car start. */
    val paperVoice: com.vikash.ampli.drive.car.PaperVoice by lazy { com.vikash.ampli.drive.car.PaperVoice(this) }

    /** OBD-II dongle (Pro) and BLE tyre sensors (Pro). */
    val obd: ObdClient by lazy { ObdClient(this) }
    val tpms: TpmsMonitor by lazy { TpmsMonitor(this) }

    /** "Hey Ampli": the conversation, and the offline wake-word model it downloads once. */
    val voice: VoiceAssistant by lazy { VoiceAssistant(this) }
    val wakeModel: WakeModel by lazy { WakeModel(this) }

    /** Optional offline Hindi command model (~42 MB), downloaded only when the driver asks. */
    val hindiModel: WakeModel by lazy { WakeModel(this, com.vikash.ampli.drive.voice.VoskModelSpec.Hindi) }

    /** Home, Office and favourite places (Where to? and the Home chips; "ghar chalo"). */
    val places: com.vikash.ampli.drive.nav.SavedPlacesStore by lazy { com.vikash.ampli.drive.nav.SavedPlacesStore(this) }

    /** Up to four favourite contacts on the Phone card. */
    val favouriteCalls: com.vikash.ampli.drive.system.FavouriteCallsStore by lazy { com.vikash.ampli.drive.system.FavouriteCallsStore(this) }

    /** 2 GB or less: Home draws first and the map and music card follow (DeviceClass). */
    val lowEnd: Boolean by lazy { com.vikash.ampli.drive.prefs.DeviceClass.isLowEnd(this) }

    /** Settings › Help & feedback: reports to the Ampli owner server, or email without one. */
    val feedback: FeedbackRepository by lazy { FeedbackRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // Server endpoints: cached remote config over BuildConfig (one small GET at most every 12 h).
        com.vikash.ampli.drive.config.RemoteConfig.init(this)
        // A fresh install starts in Hindi (FirstRunDefaults). Runs before the first activity, so
        // MainActivity already opens in it. A per-app language set in system Settings wins.
        prefs.takeFirstRunLanguage()?.let { tag ->
            if (AppLanguages.current(this, "").isEmpty()) AppLanguages.apply(this, tag)
        }
    }

    /** Hidden or short of memory: let go of icon, QR and map tile caches (they reload on demand). */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        com.vikash.ampli.drive.system.MemoryTrim.onTrimMemory(level)
    }

    /** Android 12: strings fetched through the app context follow the in-app language too. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguages.wrap(base))
    }
}
