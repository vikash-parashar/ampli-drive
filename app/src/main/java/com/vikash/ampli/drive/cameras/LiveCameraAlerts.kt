package com.vikash.ampli.drive.cameras

import android.content.Context
import android.content.res.Configuration
import android.location.Location
import android.os.LocaleList
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.i18n.AppLanguages
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Offline speed-camera alerts over [CameraRepository] (OpenStreetMap data, ODbL, plus the
 * driver's reports). SpeedTracker feeds every GPS fix on the main thread.
 *
 * Everyone gets the on-screen chip. Spoken alerts and the over-limit beep are Pro and follow the
 * "Voice alerts" switch; they go through the shared [com.vikash.ampli.drive.voice.Speaker], which
 * ducks music while it talks.
 */
class LiveCameraAlerts(private val app: DriveApp) : CameraAlerts {
    private val engine = CameraAlertEngine()
    private val scope = MainScope()
    private var speech: Job? = null

    private val _current = MutableStateFlow<CameraAlert?>(null)
    override val current: StateFlow<CameraAlert?> = _current.asStateFlow()

    private val _limit = MutableStateFlow<Int?>(null)
    override val speedLimitKmh: StateFlow<Int?> = _limit.asStateFlow()

    override fun onLocation(location: Location, speedKmh: Float) {
        val prefs = app.prefs
        if (!prefs.cameraAlerts.value) {
            if (_current.value != null) reset()
            return
        }
        app.cameras.ensureLoaded()
        engine.config = CameraAlertEngine.Config(
            alertDistanceM = prefs.cameraDistance.value,
            onlyOverLimit = prefs.cameraOverOnly.value,
        )
        val heading = if (location.hasBearing() && speedKmh >= CameraGeometry.SLOW_KMH) location.bearing.toDouble() else null
        val fix = CameraAlertEngine.Fix(location.latitude, location.longitude, speedKmh.toDouble(), heading)
        val candidates = app.cameras.near(fix.lat, fix.lon, CameraGeometry.LOOKAHEAD_M)
        val result = engine.update(fix, candidates)

        val target = result.target
        _current.value = target?.let {
            CameraAlert(
                id = it.camera.id,
                kind = if (it.camera.type == "average_speed") CameraAlert.Kind.AverageSpeedStart else CameraAlert.Kind.FixedCamera,
                distanceMeters = it.distanceM.toInt(),
                limitKmh = it.camera.maxspeed,
                description = "",
                overLimit = it.overLimit,
                userReported = it.camera.userReported,
            )
        }
        // The speed pane turns red when the speed is above this limit.
        _limit.value = target?.camera?.maxspeed
        if (result.events.isNotEmpty()) speak(result.events)
    }

    override fun reset() {
        engine.reset()
        _current.value = null
        _limit.value = null
    }

    private fun speak(events: List<CameraAlertEngine.Event>) {
        val prefs = app.prefs
        if (!prefs.pro.value.isPro || !prefs.cameraVoice.value) return
        val lang = AppLanguages.current(app, prefs.language.value)
        val res = localized(lang)
        val lines = ArrayList<String>(2)
        var beep = false
        for (e in events) {
            when (e) {
                is CameraAlertEngine.Event.Announce -> lines += if (e.camera.maxspeed != null) {
                    res.getString(R.string.camera_voice_ahead, e.roundedMeters, e.camera.maxspeed)
                } else {
                    res.getString(R.string.camera_voice_ahead_nolimit, e.roundedMeters)
                }
                is CameraAlertEngine.Event.SlowDown -> lines += res.getString(R.string.camera_voice_slow_down)
                is CameraAlertEngine.Event.Beep -> beep = true
                is CameraAlertEngine.Event.Cleared -> Unit
            }
        }
        if (lines.isEmpty() && !beep) return
        val old = speech
        speech = scope.launch {
            old?.join()
            if (beep) app.speaker.alertBeep()
            if (lines.isNotEmpty()) app.speaker.say(lines.joinToString(" "), lang)
        }
    }

    private fun localized(tag: String): Context {
        if (tag.isEmpty()) return app
        val config = Configuration(app.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return app.createConfigurationContext(config)
    }
}
