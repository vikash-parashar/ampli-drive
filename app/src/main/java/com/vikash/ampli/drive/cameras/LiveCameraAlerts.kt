package com.vikash.ampli.drive.cameras

import android.content.Context
import android.content.res.Configuration
import android.location.Location
import android.os.LocaleList
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.i18n.AppLanguages
import com.vikash.ampli.drive.voice.HaryanviLines
import com.vikash.ampli.drive.voice.Spoken
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
 * Everyone gets the on-screen chip for fixed cameras. Red-light cameras, average-speed zones and
 * road reports follow [RadarTier] (Pro). Spoken alerts and the over-limit beep are Pro and follow
 * the "Voice alerts" switch; they go through the shared [com.vikash.ampli.drive.voice.Speaker],
 * which ducks music while it talks.
 */
class LiveCameraAlerts(private val app: DriveApp) : CameraAlerts {
    private val engine = CameraAlertEngine()
    private val zoneTracker = ZoneTracker()
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
        val isPro = prefs.pro.value.isPro
        val now = System.currentTimeMillis()
        engine.config = CameraAlertEngine.Config(
            alertDistanceM = prefs.cameraDistance.value,
            onlyOverLimit = prefs.cameraOverOnly.value,
        )
        val heading = if (location.hasBearing() && speedKmh >= CameraGeometry.SLOW_KMH) location.bearing.toDouble() else null
        val fix = CameraAlertEngine.Fix(location.latitude, location.longitude, speedKmh.toDouble(), heading)

        val candidates = ArrayList<SpeedCamera>()
        app.cameras.near(fix.lat, fix.lon, CameraGeometry.LOOKAHEAD_M).filterTo(candidates) { RadarTier.alerts(it.type, isPro) }
        var reportTimes: Map<String, Long> = emptyMap()
        if (RadarTier.allowed(RadarFeature.Reports, isPro)) {
            app.roadReports.prune(now)
            val live = app.roadReports.live(now)
            if (live.isNotEmpty()) {
                reportTimes = live.associate { it.id to it.time }
                for (r in live) {
                    if (CameraGeometry.distanceM(fix.lat, fix.lon, r.lat, r.lon) <= CameraGeometry.LOOKAHEAD_M) candidates += r.asCamera()
                }
            }
        }
        val result = engine.update(fix, candidates)

        var zone: ZoneTracker.State? = null
        var zoneEvents: List<ZoneTracker.Event> = emptyList()
        if (RadarTier.allowed(RadarFeature.AverageSpeedZones, isPro)) {
            zoneTracker.alertM = prefs.cameraDistance.value.toDouble()
            val zr = zoneTracker.update(fix.lat, fix.lon, heading, fix.speedKmh, now, app.cameras.zonesNear(fix.lat, fix.lon, ZONE_SEARCH_M))
            zone = zr.state
            zoneEvents = zr.events
        } else {
            zoneTracker.reset()
        }

        val target = result.target
        _current.value = when {
            target != null -> CameraAlert(
                id = target.camera.id,
                kind = CameraAlert.kindFor(target.camera.type),
                distanceMeters = target.distanceM.toInt(),
                limitKmh = target.camera.maxspeed,
                description = "",
                overLimit = target.overLimit,
                userReported = target.camera.userReported && target.camera.type == SpeedCamera.TYPE_USER,
                reportedAt = reportTimes[target.camera.id],
            )
            zone != null -> CameraAlert(
                id = zone.zone.id,
                kind = if (zone.phase == ZoneTracker.Phase.Inside) CameraAlert.Kind.AverageSpeedZone else CameraAlert.Kind.AverageSpeedStart,
                distanceMeters = zone.distanceM.toInt(),
                limitKmh = zone.zone.maxspeed,
                description = zone.zone.name,
                overLimit = if (zone.phase == ZoneTracker.Phase.Inside) {
                    zone.overLimit
                } else {
                    zone.zone.maxspeed != null && fix.speedKmh > zone.zone.maxspeed
                },
                averageKmh = zone.averageKmh,
            )
            else -> null
        }
        // The speed pane turns red when the speed is above this limit.
        _limit.value = target?.camera?.maxspeed ?: zone?.zone?.maxspeed
        if (result.events.isNotEmpty() || zoneEvents.isNotEmpty()) speak(result.events, zoneEvents)
    }

    override fun reset() {
        engine.reset()
        zoneTracker.reset()
        _current.value = null
        _limit.value = null
    }

    /** The spoken line for a road report or red-light camera: its event and the standard text. */
    private fun specialLine(type: String): Pair<HaryanviLines.Event, Int>? = when (type) {
        SpeedCamera.TYPE_RED_LIGHT -> HaryanviLines.Event.RedLightAhead to R.string.radar_voice_red_light
        ReportKind.MobileCamera.type -> HaryanviLines.Event.MobileCameraAhead to R.string.radar_voice_mobile
        ReportKind.Police.type -> HaryanviLines.Event.PoliceAhead to R.string.radar_voice_police
        ReportKind.Accident.type -> HaryanviLines.Event.AccidentAhead to R.string.radar_voice_accident
        ReportKind.Jam.type -> HaryanviLines.Event.JamAhead to R.string.radar_voice_jam
        else -> null
    }

    private fun speak(events: List<CameraAlertEngine.Event>, zoneEvents: List<ZoneTracker.Event>) {
        val prefs = app.prefs
        if (!prefs.pro.value.isPro || !prefs.cameraVoice.value) return
        val lang = AppLanguages.current(app, prefs.language.value)
        val res = localized(lang)
        val old = speech
        speech = scope.launch {
            old?.join()
            val lines = ArrayList<Spoken>(2)
            var beep = false

            /** The desi line when that style is on, else the plain text (recorded stand-in without TTS). */
            suspend fun plain(event: HaryanviLines.Event, text: String) {
                lines += app.desi.line(event) ?: Spoken(text, lang, app.voicePlayer.standInClips(event))
            }

            for (z in zoneEvents) {
                when (z) {
                    is ZoneTracker.Event.Started -> plain(HaryanviLines.Event.AvgZoneStart, res.getString(R.string.radar_voice_zone_start))
                    is ZoneTracker.Event.Ended -> plain(HaryanviLines.Event.AvgZoneEnd, res.getString(R.string.radar_voice_zone_end))
                }
            }
            for (e in events) {
                // Red-light cameras and road reports have their own lines, without a distance.
                if (e is CameraAlertEngine.Event.Announce) {
                    val special = specialLine(e.camera.type)
                    if (special != null) {
                        plain(special.first, res.getString(special.second))
                        continue
                    }
                }
                // Haryanvi desi style (Settings › Voice style): the same alert in desi words.
                val desi = when (e) {
                    is CameraAlertEngine.Event.Announce -> if (e.camera.maxspeed != null) {
                        app.desi.line(HaryanviLines.Event.CameraAhead, e.roundedMeters, e.camera.maxspeed ?: 0)
                    } else {
                        app.desi.line(HaryanviLines.Event.CameraAheadNoLimit, e.roundedMeters)
                    }
                    is CameraAlertEngine.Event.SlowDown -> app.desi.line(HaryanviLines.Event.OverSpeed)
                    else -> null
                }
                if (desi != null) {
                    lines += desi
                    continue
                }
                // Standard style. Without a TTS engine the recorded desi line stands in
                // (distance clip + "पै कैमरा सै…", or the slow-down line), so the alert is heard.
                when (e) {
                    is CameraAlertEngine.Event.Announce -> lines += Spoken(
                        if (e.camera.maxspeed != null) {
                            res.getString(R.string.camera_voice_ahead, e.roundedMeters, e.camera.maxspeed)
                        } else {
                            res.getString(R.string.camera_voice_ahead_nolimit, e.roundedMeters)
                        },
                        lang,
                        if (e.camera.maxspeed != null) {
                            app.voicePlayer.standInClips(HaryanviLines.Event.CameraAhead, e.roundedMeters, e.camera.maxspeed ?: 0)
                        } else {
                            app.voicePlayer.standInClips(HaryanviLines.Event.CameraAheadNoLimit, e.roundedMeters)
                        },
                    )
                    is CameraAlertEngine.Event.SlowDown -> lines += Spoken(
                        res.getString(R.string.camera_voice_slow_down),
                        lang,
                        app.voicePlayer.standInClips(HaryanviLines.Event.OverSpeed),
                    )
                    is CameraAlertEngine.Event.Beep -> beep = true
                    is CameraAlertEngine.Event.Cleared -> Unit
                }
            }
            if (lines.isEmpty() && !beep) return@launch
            if (beep) app.speaker.alertBeep()
            // Recorded voice (Settings › Voice) when every part has clips, else one TTS sentence.
            if (lines.isNotEmpty()) app.voicePlayer.say(lines.reduce { a, b -> a + b })
        }
    }

    private fun localized(tag: String): Context {
        if (tag.isEmpty()) return app
        val config = Configuration(app.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return app.createConfigurationContext(config)
    }

    private companion object {
        /** Zones whose start or end is this close are checked for an approach. */
        const val ZONE_SEARCH_M = 3_000.0
    }
}
