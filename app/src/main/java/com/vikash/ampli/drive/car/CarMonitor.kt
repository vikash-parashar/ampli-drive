package com.vikash.ampli.drive.car

import com.vikash.ampli.drive.overlay.SpeedBubble
import com.vikash.ampli.drive.voice.HaryanviLines

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.media.AudioManager
import android.os.LocaleList
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.annotation.StringRes
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.MainActivity
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.emergency.Sos
import com.vikash.ampli.drive.emergency.SosNotifier
import com.vikash.ampli.drive.i18n.AppLanguages
import com.vikash.ampli.drive.obd.DtcCatalog
import com.vikash.ampli.drive.obd.EngineWarning
import com.vikash.ampli.drive.tpms.TyreAlert
import com.vikash.ampli.drive.tpms.Wheel
import com.vikash.ampli.drive.voice.VoiceLocales
import com.vikash.ampli.drive.weather.Hazard
import com.vikash.ampli.drive.weather.RouteWeather
import com.vikash.ampli.drive.weather.WeatherClient
import com.vikash.ampli.drive.weather.WeatherReport
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import com.vikash.ampli.drive.voice.Spoken
import com.vikash.ampli.drive.voice.VoiceClips

/**
 * The car features that watch the drive: break reminder, odometer, parking spot, crash
 * detection, route weather, papers/service reminders, and the spoken tyre and engine alerts.
 * Everything runs on the main thread, fed by SpeedTracker fixes and the accelerometer.
 */
class CarMonitor(private val app: DriveApp) {
    val store = CarStore(app)
    private val scope = MainScope()

    private val fatigue = FatigueTimer()
    private val seatBelt = SeatBeltReminder()
    private val nightDrive = NightDriveReminder()
    private val odometer = OdometerAccumulator()
    private val parking = ParkingDetector()
    private val crash = CrashDetector()

    /** Trip history (Car › Trips): every drive, logged from the same fixes as the odometer. */
    val trips = TripStore(app)
    private val tripRecorder = TripRecorder().also { rec ->
        rec.restore(trips.loadActive(), System.currentTimeMillis())?.let { trips.add(it) }
        if (rec.active == null) trips.saveActive(null)
    }
    private var tripSavedAtM = 0.0

    /** Settings › Volume by speed. */
    private val speedVolume = com.vikash.ampli.drive.media.SpeedVolume(app)

    private val _foreground = MutableStateFlow(false)
    /** Launcher in front (MainActivity started), as a flow for the speed bubble. */
    val foregroundState: StateFlow<Boolean> = _foreground.asStateFlow()

    /** Launcher in front (MainActivity started). */
    var foreground: Boolean
        get() = _foreground.value
        set(value) { _foreground.value = value }

    // ------------------------------------------------------------------ Public state

    private val _drivingMs = MutableStateFlow(0L)
    /** Continuous driving time (short stops included). */
    val drivingMs: StateFlow<Long> = _drivingMs.asStateFlow()

    private val _breakDue = MutableStateFlow(false)
    /** The "time for a break" card is showing. */
    val breakDue: StateFlow<Boolean> = _breakDue.asStateFlow()

    private val _crashAt = MutableStateFlow<Long?>(null)
    /** Wall-clock time of a detected crash while its countdown runs, else null. */
    val crashAt: StateFlow<Long?> = _crashAt.asStateFlow()

    private val _crashSos = MutableStateFlow<String?>(null)
    /** The SOS message opened for the last crash (by the countdown or "Send now"); null until then. */
    val crashSos: StateFlow<String?> = _crashSos.asStateFlow()

    private val _weather = MutableStateFlow<WeatherReport?>(null)
    val weather: StateFlow<WeatherReport?> = _weather.asStateFlow()

    private val _weatherFailed = MutableStateFlow(false)
    val weatherFailed: StateFlow<Boolean> = _weatherFailed.asStateFlow()

    // ------------------------------------------------------------------ Sensors

    private val sensorManager: SensorManager? = app.getSystemService(SensorManager::class.java)
    private val motionSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** False on head units without an accelerometer: only the GPS speed-drop check works then. */
    val hasMotionSensor: Boolean get() = motionSensor != null
    private var sensorsOn = false

    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!store.settings.value.crashDetection) return
            val v = event.values
            val gravity = event.sensor.type == Sensor.TYPE_ACCELEROMETER
            if (crash.onAccel(System.currentTimeMillis(), v[0], v[1], v[2], gravity)) triggerCrash()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    init {
        app.speedTracker.addFixListener { loc, kmh, raw -> onFix(loc, kmh, raw) }
        app.speedTracker.onStopped = { last -> onTrackerStopped(last) }
        fatigue.thresholdMs = store.settings.value.breakAfterMin * FatigueTimer.MINUTE
        app.tpms.onNewAlert = { wheel, alert, seen -> onTyreAlert(wheel, alert, seen.reading.psi) }
        scope.launch { store.wheels.collect { app.tpms.wheels = it } }
        // FASTag tolls into the expense log. The first run copies the stored history (backfill).
        scope.launch { app.fastag.events.collect { store.syncTolls(it) } }
        scope.launch { store.settings.collect { app.tpms.limits = it.tpmsLimits } }
        // Spoken engine warnings and new trouble codes while the OBD dongle is connected.
        scope.launch {
            var warned = emptySet<EngineWarning>()
            val announced = HashSet<String>()
            app.obd.state.collect { st ->
                if (st.phase != com.vikash.ampli.drive.obd.ObdState.Phase.Connected) {
                    warned = emptySet()
                    announced.clear()
                    return@collect
                }
                onEngineWarnings(st.warnings - warned)
                warned = st.warnings
                if (st.dtcsRead) onEngineCodes(st.dtcs, announced)
            }
        }
    }

    fun startSensors() {
        if (sensorsOn || motionSensor == null || !store.settings.value.crashDetection) return
        sensorsOn = sensorManager?.registerListener(accelListener, motionSensor, SensorManager.SENSOR_DELAY_GAME) == true
    }

    fun stopSensors() {
        if (!sensorsOn) return
        sensorManager?.unregisterListener(accelListener)
        sensorsOn = false
    }

    /** Launcher came to the front. */
    fun onForeground() {
        foreground = true
        startSensors()
        checkDue()
        updateTpmsScan()
    }

    fun onBackground() {
        foreground = false
        if (!CarMonitorService.running) {
            stopSensors()
            app.tpms.stop()
        }
    }

    /** Settings changed: apply the new threshold and switch sensors. */
    fun applySettings() {
        val s = store.settings.value
        fatigue.thresholdMs = s.breakAfterMin * FatigueTimer.MINUTE
        fatigue.reset()
        if (!s.breakReminder) _breakDue.value = false
        if (s.crashDetection && (foreground || CarMonitorService.running)) startSensors() else if (!s.crashDetection) stopSensors()
    }

    // ------------------------------------------------------------------ GPS

    private var lastLocation: Location? = null
    private var lastDueCheck = 0L

    private fun onFix(location: Location, kmh: Float, rawKmh: Float?) {
        val now = System.currentTimeMillis()
        val s = store.settings.value
        lastLocation = location

        // Break reminder.
        if (fatigue.onSample(now, kmh) && s.breakReminder) remindBreak()
        _drivingMs.value = fatigue.drivingMs(now)
        if (!fatigue.driving && _breakDue.value) _breakDue.value = false
        // Haryanvi desi only, once per trip on first movement: "भाई सीट बेल्ट पहन ले।"
        if (seatBelt.onSample(now, fatigue.driving)) app.desi.sayLater(HaryanviLines.Event.SeatBelt)
        // Haryanvi desi only, once a night: after 11 pm and more than 30 minutes into the drive.
        if (nightDrive.onSample(now, java.time.LocalTime.now().hour, _drivingMs.value)) app.desi.sayLater(HaryanviLines.Event.NightDrive)

        // Odometer.
        val stepM = odometer.onFix(location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy else null, kmh, location.elapsedRealtimeNanos / 1_000_000L)
        store.addDistance(stepM)

        // Trip history.
        tripRecorder.onSample(now, kmh, location.latitude, location.longitude, stepM)?.let { trips.add(it) }
        tripRecorder.active.let { a ->
            if (a == null) {
                if (tripSavedAtM > 0.0) { trips.saveActive(null); tripSavedAtM = 0.0 }
            } else if (a.distanceM - tripSavedAtM >= 250.0 || tripSavedAtM == 0.0) {
                trips.saveActive(a)
                tripSavedAtM = a.distanceM.coerceAtLeast(0.1)
            }
        }

        // Music a little louder at speed (Settings › Volume by speed; off by default).
        speedVolume.onSpeed(kmh)

        // Parking spot.
        parking.onSample(now, kmh, location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy else null)
            ?.let { park(it) }

        // Crash: sudden stop.
        if (s.crashDetection && rawKmh != null && crash.onSpeed(now, rawKmh)) triggerCrash()

        // Weather.
        if (s.weatherAlerts) maybeFetchWeather(location, kmh, now)

        // Papers/service once an hour while driving.
        if (now - lastDueCheck > 60L * 60_000L) checkDue()

        if (kmh > 10f && app.tpms.isScanning.not()) updateTpmsScan()
    }

    private fun onTrackerStopped(last: Location?) {
        store.flushOdometer()
        odometer.reset()
        val loc = last ?: lastLocation
        parking.onStopped(System.currentTimeMillis(), loc?.latitude, loc?.longitude, loc?.takeIf { it.hasAccuracy() }?.accuracy)?.let { park(it) }
        endTrip(loc?.latitude, loc?.longitude)
    }

    /** Screen off / shutdown (ignition off on most head units). */
    fun onIgnitionOff() {
        store.flushOdometer()
        val loc = lastLocation
        endTrip(loc?.latitude, loc?.longitude)
        if (loc == null) return
        parking.onStopped(System.currentTimeMillis(), loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null)?.let { park(it) }
    }

    /**
     * A vendor ACC broadcast (see IgnitionActions). Off ends the trip: the parking spot is saved
     * from the last fix, the odometer is written and the break timer starts afresh. On starts a
     * new trip with a fresh break timer.
     */
    fun onIgnition(event: IgnitionEvent) {
        when (event) {
            IgnitionEvent.Off -> {
                onIgnitionOff()
                odometer.reset()
                // Haryanvi desi: "चाल फेर मिलांगे।" / "गाड्डी ढंग तै लॉक कर दिए।" (Standard stays quiet).
                app.desi.sayLater(HaryanviLines.Event.Farewell)
            }
            IgnitionEvent.On -> {
                // A new trip for the average speed, then greeting / auto start (CarStart).
                app.speedTracker.resetTrip()
                seatBelt.newTrip()
                CarStart.signal(app, StartTrigger.Ignition)
            }
        }
        fatigue.reset()
        _drivingMs.value = 0L
        _breakDue.value = false
        CarNotifier.cancel(app, CarNotifier.ID_BREAK)
    }

    private fun park(spot: ParkedSpot) {
        store.setParked(spot)
        store.flushOdometer()
        endTrip(spot.lat, spot.lon)
    }

    /** The drive is over: log it (when long enough) and forget the saved in-progress copy. */
    private fun endTrip(lat: Double?, lon: Double?) {
        tripRecorder.finish(System.currentTimeMillis(), lat, lon)?.let { trips.add(it) }
        trips.saveActive(null)
        tripSavedAtM = 0.0
        speedVolume.onSpeed(0f)
    }

    /** "Save spot here" on the parked-car screen. */
    fun parkHere(): Boolean {
        val loc = app.speedTracker.state.value.location ?: lastLocation ?: return false
        park(ParkedSpot(loc.latitude, loc.longitude, System.currentTimeMillis(), if (loc.hasAccuracy()) loc.accuracy else null))
        app.desi.sayLater(HaryanviLines.Event.ParkingSaved)
        return true
    }

    // ------------------------------------------------------------------ Break reminder

    private fun remindBreak() {
        _breakDue.value = true
        val hours = (_drivingMs.value / 3_600_000.0)
        val old = speech
        speech = scope.launch {
            old?.join()
            if (app.voice.busy.value) return@launch
            // Haryanvi desi style: "Thak gaya hoga bhai, ek chai ho jaye?"
            if (app.desi.say(HaryanviLines.Event.BreakReminder, formatHours(app, hours))) return@launch
            val lang = language()
            val res = localized(lang)
            app.voicePlayer.sayPlain(res.getString(R.string.break_voice, formatHours(res, hours)), lang, HaryanviLines.Event.BreakReminder, formatHours(app, hours))
        }
        if (!foreground) {
            CarNotifier.post(app, CarNotifier.ID_BREAK, app.getString(R.string.break_title), app.getString(R.string.break_body, formatHours(app, hours)))
        }
    }

    fun snoozeBreak() {
        fatigue.snooze(System.currentTimeMillis())
        _breakDue.value = false
        CarNotifier.cancel(app, CarNotifier.ID_BREAK)
    }

    private fun formatHours(res: Context, hours: Double): String {
        val locale = res.resources.configuration.locales[0]
        return if (hours % 1.0 < 0.08) String.format(locale, "%d", hours.toInt()) else String.format(locale, "%.1f", hours)
    }

    // ------------------------------------------------------------------ Crash

    private var crashJob: Job? = null
    private var crashTimer: Job? = null
    private var crashFixSignal: android.os.CancellationSignal? = null
    @Volatile private var crashFix: Location? = null
    private var savedVolume: Int? = null

    fun triggerCrash() {
        if (_crashAt.value != null) return
        _crashAt.value = System.currentTimeMillis()
        lastCrashElapsed = SystemClock.elapsedRealtime()
        _crashSos.value = null
        SosNotifier.cancel(app)
        // No WhatsApp message may talk over the countdown or the SOS screen.
        app.messages.cancelAll()
        // A fresh fix for the SOS message, whether or not the countdown screen is showing.
        crashFix = null
        crashFixSignal?.cancel()
        crashFixSignal = try {
            Sos.requestFreshLocation(app) { crashFix = it }
        } catch (_: Exception) {
            null
        }
        try {
            app.startActivity(
                Intent(app, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra(MainActivity.EXTRA_VOICE_ACTION, MainActivity.VOICE_CRASH),
            )
        } catch (_: Exception) {
        }
        louder()
        crashJob?.cancel()
        crashJob = scope.launch {
            val lang = language()
            val res = localized(lang)
            val end = SystemClock.elapsedRealtime() + CRASH_COUNTDOWN_S * 1000L
            while (_crashAt.value != null && SystemClock.elapsedRealtime() < end - 3000) {
                app.speaker.alertBeep()
                // Seconds left until the SOS message opens (the string has a %1$d placeholder).
                val secondsLeft = ((end - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(1)
                // Recorded Ampli voice clips when there are any (units with no TTS engine stay
                // audible); otherwise the phone voice says the localized line.
                app.voicePlayer.say(
                    Spoken(res.getString(R.string.crash_voice, secondsLeft), lang, VoiceClips.crashClips(secondsLeft)),
                )
                delay(4000)
            }
        }
        // The countdown ends here, not in the UI: the SOS message opens even when the countdown
        // screen was left (HOME key, another app in front). Its own timer, so a slow or missing
        // TTS engine in the voice loop above can never hold it up.
        crashTimer?.cancel()
        crashTimer = scope.launch {
            delay(CRASH_COUNTDOWN_S * 1000L)
            if (_crashAt.value != null) sendCrashSos(null)
        }
    }

    /**
     * Opens the SMS composer with the SOS message for the running crash countdown and ends it.
     * [location] is the countdown screen's best fix, if it has one. Returns the message (also
     * when the countdown had already opened it), or null when no crash is running.
     */
    fun sendCrashSos(location: Location?): String? {
        if (_crashAt.value == null) return _crashSos.value
        val fix = listOfNotNull(location, crashFix, Sos.lastKnownLocation(app, app.speedTracker.state.value.location))
            .maxByOrNull { it.time }
        val profile = app.emergency.profile.value
        val message = Sos.buildMessage(profile, fix)
        _crashSos.value = message
        sosOpenedAt = SystemClock.elapsedRealtime()
        // When the countdown timer is the caller, cancelling it here only stops it at its next
        // suspension point; the composer below still opens.
        clearCrash()
        // Android 10+ silently blocks activity starts from the background (no exception), so
        // the composer opens directly only while Car Hub is in front (or may draw over other
        // apps). Otherwise, or when it fails, a full-screen alert brings the SOS screen forward
        // and that opens the composer. Either way, nothing is sent without a tap.
        // Android 15+: "Display over other apps" allows a background start only while an overlay
        // is visible, and a blocked start throws nothing. So whenever Car Hub is not in front the
        // alert is posted as well; it is cancelled once the SOS screen is in front.
        val inFront = MainActivity.inFront
        val canStart = inFront || (Settings.canDrawOverlays(app) && SpeedBubble.visible)
        val opened = canStart && Sos.openSmsComposer(app, profile.contacts.map { it.phone }, message)
        if (!inFront || !opened) {
            Log.i("CarMonitor", "Crash SOS alert posted (inFront=$inFront, opened=$opened)")
            SosNotifier.postCrashSos(app)
        }
        return message
    }

    private var sosOpenedAt = 0L

    /** elapsedRealtime of the last detected crash, or null if none since the app started. */
    @Volatile var lastCrashElapsed: Long? = null
        private set

    /** The last crash was less than [SosNotifier.ALERT_TTL_MS] ago, so its SOS message may still open. */
    fun crashSosFresh(now: Long = SystemClock.elapsedRealtime()): Boolean =
        _crashSos.value != null && SosNotifier.isFresh(lastCrashElapsed, now)

    /** The crash SOS opened in the last two minutes: other voices (WhatsApp) stay quiet. */
    fun sosOpenedRecently(): Boolean =
        sosOpenedAt != 0L && SystemClock.elapsedRealtime() - sosOpenedAt < SOS_QUIET_MS

    /** Countdown cancelled or finished. */
    fun clearCrash() {
        _crashAt.value = null
        crashFixSignal?.cancel()
        crashFixSignal = null
        crashJob?.cancel()
        crashJob = null
        crashTimer?.cancel()
        crashTimer = null
        app.speaker.stop()
        restoreVolume()
    }

    /** Crash prompts must be heard over road noise: raise media volume to 80 % for the countdown. */
    private fun louder() {
        try {
            val am = app.getSystemService(AudioManager::class.java) ?: return
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val target = (max * 0.8f).toInt()
            if (cur < target) {
                savedVolume = cur
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun restoreVolume() {
        val v = savedVolume ?: return
        savedVolume = null
        try {
            app.getSystemService(AudioManager::class.java)?.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------ Weather

    private var lastWeatherAttempt = 0L
    private var weatherJob: Job? = null
    private val spokenHazards = HashMap<Hazard, Long>()

    private fun maybeFetchWeather(location: Location, kmh: Float, now: Long) {
        if (weatherJob?.isActive == true) return
        if (now - lastWeatherAttempt < RouteWeather.REFRESH_MS) return
        lastWeatherAttempt = now
        val bearing = if (location.hasBearing() && kmh >= 20f) location.bearing.toDouble() else null
        val points = RouteWeather.points(location.latitude, location.longitude, bearing)
        weatherJob = scope.launch {
            try {
                val report = WeatherClient.fetch(points)
                _weather.value = report
                _weatherFailed.value = false
                announceWeather(report)
            } catch (_: Exception) {
                _weatherFailed.value = true
                // Try again sooner when offline.
                lastWeatherAttempt = System.currentTimeMillis() - RouteWeather.REFRESH_MS + 5L * 60_000L
            }
        }
    }

    /** Forces a refresh (Car screen), still at most once a minute. */
    fun refreshWeather() {
        val loc = app.speedTracker.state.value.location ?: lastLocation ?: return
        if (System.currentTimeMillis() - lastWeatherAttempt < 60_000L) return
        lastWeatherAttempt = 0L
        maybeFetchWeather(loc, app.speedTracker.state.value.speedKmh ?: 0f, System.currentTimeMillis())
    }

    private fun announceWeather(report: WeatherReport) {
        val now = System.currentTimeMillis()
        val fresh = report.hazards.filter { (hz, _) -> now - (spokenHazards[hz] ?: 0L) > 60L * 60_000L }
        if (fresh.isEmpty()) return
        fresh.forEach { spokenHazards[it.first] = now }
        if (app.desi.enabled) {
            // Haryanvi desi: "आगे धुंध सै भाई, फॉग लाइट जला ले अर धीरे चला।"
            fresh.forEach { (hz, _) -> app.desi.sayLater(desiHazard(hz)) }
        } else {
            // No phone voice on this unit: the recorded desi weather lines stand in.
            speak(fresh.map { (hz, _) -> desiHazard(hz) }) { res -> fresh.joinToString(" ") { (hz, km) -> hazardLine(res, hz, km) } }
        }
        if (!foreground) {
            CarNotifier.post(app, CarNotifier.ID_WEATHER, app.getString(R.string.weather_title), fresh.joinToString(" ") { (hz, km) -> hazardLine(app, hz, km) })
        }
    }

    private fun desiHazard(hz: Hazard): HaryanviLines.Event = when (hz) {
        Hazard.Thunderstorm -> HaryanviLines.Event.WeatherThunder
        Hazard.HeavyRain -> HaryanviLines.Event.WeatherRain
        Hazard.Fog -> HaryanviLines.Event.WeatherFog
        Hazard.Heat -> HaryanviLines.Event.WeatherHeat
    }

    fun hazardLine(res: Context, hz: Hazard, km: Int): String {
        val what = res.getString(
            when (hz) {
                Hazard.Thunderstorm -> R.string.weather_thunder
                Hazard.HeavyRain -> R.string.weather_heavy_rain
                Hazard.Fog -> R.string.weather_fog
                Hazard.Heat -> R.string.weather_heat
            },
        )
        return if (km == 0) res.getString(R.string.weather_here, what) else res.getString(R.string.weather_ahead, what, km)
    }

    // ------------------------------------------------------------------ Papers and service

    fun dueItems(res: Context = app): List<DueItem> = Reminders.dueItems(
        store.papers.value, store.services.value, LocalDate.now(), store.odometerKm.value,
    ) { paperName(res, it) }

    fun checkDue() {
        lastDueCheck = System.currentTimeMillis()
        val due = dueItems()
        val today = LocalDate.now().toEpochDay()
        if (due.isEmpty() || store.lastDueNotice == today) return
        store.lastDueNotice = today
        CarNotifier.post(
            app, CarNotifier.ID_DUE,
            app.getString(R.string.due_notification_title),
            due.joinToString(" · ") { dueLine(app, it) },
        )
    }

    // ------------------------------------------------------------------ Tyres and engine

    fun updateTpmsScan() {
        val pro = app.prefs.pro.value.isPro
        if (pro && store.wheels.value.isNotEmpty() && (foreground || CarMonitorService.running)) {
            app.tpms.start(fast = false)
        }
    }

    private fun onTyreAlert(wheel: Wheel, alert: TyreAlert, psi: Double) {
        if (!app.prefs.pro.value.isPro) return
        speak { res -> tyreAlertLine(res, wheel, alert, psi) }
        if (!foreground) CarNotifier.post(app, CarNotifier.ID_TYRES, app.getString(R.string.tyres_title), tyreAlertLine(app, wheel, alert, psi))
    }

    fun onEngineWarnings(new: Set<EngineWarning>) {
        if (new.isEmpty()) return
        if (app.desi.enabled) {
            // Haryanvi desi for the common ones; overcharging keeps the plain line.
            val plain = new.filter { desiWarning(it) == null }
            new.mapNotNull { desiWarning(it) }.forEach { app.desi.sayLater(it) }
            if (plain.isNotEmpty()) speak { res -> plain.joinToString(" ") { engineWarningLine(res, it) } }
            return
        }
        // No phone voice: the recorded desi lines stand in when every warning has one.
        val standIns = new.map { desiWarning(it) }
        speak(if (null in standIns) emptyList() else standIns.filterNotNull()) { res -> new.joinToString(" ") { engineWarningLine(res, it) } }
    }

    private fun desiWarning(w: EngineWarning): HaryanviLines.Event? = when (w) {
        EngineWarning.CoolantHot -> HaryanviLines.Event.ObdEngineHot
        EngineWarning.BatteryLow -> HaryanviLines.Event.ObdBatteryLow
        EngineWarning.NotCharging -> HaryanviLines.Event.ObdNotCharging
        EngineWarning.Overcharging -> null
    }

    fun onEngineCodes(codes: List<String>, announced: MutableSet<String>) {
        val fresh = codes.filter { announced.add(it) }
        if (fresh.isEmpty()) return
        val hindi = VoiceLocales.parserLanguage(language()) == "hi"
        speak { res -> res.getString(R.string.obd_voice_codes, fresh.size) + " " + fresh.take(3).joinToString(". ") { DtcCatalog.describe(it, hindi) } }
    }

    // ------------------------------------------------------------------ Speech

    fun language(): String = AppLanguages.current(app, app.prefs.language.value)

    fun localized(tag: String = language()): Context {
        if (tag.isEmpty()) return app
        val config = Configuration(app.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return app.createConfigurationContext(config)
    }

    private var speech: Job? = null

    /**
     * Speaks after any line already being spoken, unless the voice assistant is busy. On a unit
     * without a TTS engine the recorded lines of [standIns] play instead (when all are recorded);
     * a line with no recording (tyres, fault codes) stays with the phone voice.
     */
    private fun speak(standIns: List<HaryanviLines.Event> = emptyList(), text: (Context) -> String) {
        val old = speech
        speech = scope.launch {
            old?.join()
            if (app.voice.busy.value) return@launch
            val lang = language()
            val clips = app.voicePlayer.standInClips(standIns.map { it to emptyArray<Any>() })
            app.voicePlayer.say(Spoken(text(localized(lang)), lang, clips))
        }
    }

    companion object {
        const val CRASH_COUNTDOWN_S = 30
        private const val SOS_QUIET_MS = 2 * 60_000L

        fun paperName(res: Context, kind: PaperKind): String = res.getString(
            when (kind) {
                PaperKind.Puc -> R.string.paper_puc
                PaperKind.Insurance -> R.string.paper_insurance
                PaperKind.Rc -> R.string.paper_rc
                PaperKind.Licence -> R.string.paper_licence
                PaperKind.FastagKyc -> R.string.paper_fastag_kyc
            },
        )

        /** "PUC: due in 5 days", "Engine oil: overdue by 300 km". */
        fun dueLine(res: Context, item: DueItem): String = item.title + ": " + dueText(res, item.status)

        fun dueText(res: Context, s: DueStatus): String {
            val days = s.daysLeft
            val km = s.kmLeft
            // Use whichever limit is nearer.
            val byKm = km != null && (days == null || km / 40.0 < days)
            return when {
                s.level == DueLevel.Unknown -> res.getString(R.string.due_not_set)
                byKm && km!! < 0 -> res.getString(R.string.due_overdue_km, Math.round(-km).toInt())
                byKm -> res.getString(R.string.due_in_km, Math.round(km!!).toInt())
                days != null && days < 0 -> res.getString(R.string.due_overdue_days, (-days).toInt())
                days == 0L -> res.getString(R.string.due_today)
                days != null -> res.getString(R.string.due_in_days, days.toInt())
                else -> res.getString(R.string.due_not_set)
            }
        }

        @StringRes
        fun wheelName(w: Wheel): Int = when (w) {
            Wheel.FL -> R.string.wheel_fl
            Wheel.FR -> R.string.wheel_fr
            Wheel.RL -> R.string.wheel_rl
            Wheel.RR -> R.string.wheel_rr
        }

        fun tyreAlertLine(res: Context, wheel: Wheel, alert: TyreAlert, psi: Double): String {
            val w = res.getString(wheelName(wheel))
            val p = Math.round(psi).toInt()
            return when (alert) {
                TyreAlert.Low -> res.getString(R.string.tyre_alert_low, w, p)
                TyreAlert.High -> res.getString(R.string.tyre_alert_high, w, p)
                TyreAlert.FastLeak -> res.getString(R.string.tyre_alert_leak, w)
                TyreAlert.Hot -> res.getString(R.string.tyre_alert_hot, w)
                TyreAlert.SensorAlarm -> res.getString(R.string.tyre_alert_sensor, w)
            }
        }

        fun engineWarningLine(res: Context, w: EngineWarning): String = res.getString(
            when (w) {
                EngineWarning.CoolantHot -> R.string.obd_warn_hot
                EngineWarning.BatteryLow -> R.string.obd_warn_battery_low
                EngineWarning.NotCharging -> R.string.obd_warn_not_charging
                EngineWarning.Overcharging -> R.string.obd_warn_overcharging
            },
        )
    }
}
