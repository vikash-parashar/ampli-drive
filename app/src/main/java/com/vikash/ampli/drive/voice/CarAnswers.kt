package com.vikash.ampli.drive.voice

import android.content.Context
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.cameras.CameraGeometry
import com.vikash.ampli.drive.car.CarMonitor
import com.vikash.ampli.drive.car.Reminders
import com.vikash.ampli.drive.obd.DtcCatalog
import com.vikash.ampli.drive.obd.ObdState
import com.vikash.ampli.drive.tpms.TpmsMonitor
import com.vikash.ampli.drive.ui.clockLabel
import com.vikash.ampli.drive.ui.distanceLabel
import java.time.LocalDate
import kotlin.math.roundToInt

/** Spoken answers to the "Hey Ampli" car questions, in the app language ([res]). */
object CarAnswers {

    fun service(app: DriveApp, res: Context): String {
        val store = app.car.store
        val next = Reminders.nextService(store.services.value, LocalDate.now(), store.odometerKm.value)
            ?: return res.getString(R.string.voice_service_unknown)
        return res.getString(R.string.voice_service_next, next.first.name, CarMonitor.dueText(res, next.second))
    }

    fun tyres(app: DriveApp, res: Context): String {
        if (!app.prefs.pro.value.isPro) return res.getString(R.string.voice_car_pro)
        if (app.car.store.wheels.value.isEmpty()) return res.getString(R.string.voice_tyres_none)
        val now = System.currentTimeMillis()
        val readings = app.tpms.wheelReadings().filterValues { now - it.time < TpmsMonitor.STALE_MS }
        if (readings.isEmpty()) return res.getString(R.string.voice_tyres_no_signal)
        val alerts = app.tpms.alerts.value
        val parts = readings.entries.sortedBy { it.key.ordinal }.map { (w, s) ->
            res.getString(R.string.voice_tyre_value, res.getString(CarMonitor.wheelName(w)), s.reading.psi.roundToInt())
        }
        val warn = readings.entries.flatMap { (w, s) -> alerts[w].orEmpty().map { CarMonitor.tyreAlertLine(res, w, it, s.reading.psi) } }
        return (parts.joinToString(", ") + ". " + warn.joinToString(" ")).trim()
    }

    fun engine(app: DriveApp, res: Context, hindi: Boolean): String {
        if (!app.prefs.pro.value.isPro) return res.getString(R.string.voice_car_pro)
        val st = app.obd.state.value
        if (st.phase != ObdState.Phase.Connected) return res.getString(R.string.voice_engine_not_connected)
        val sb = StringBuilder()
        st.coolantC?.let { sb.append(res.getString(R.string.voice_engine_coolant, it)).append(' ') }
        st.volts?.let { sb.append(res.getString(R.string.voice_engine_battery, "%.1f".format(res.resources.configuration.locales[0], it))).append(' ') }
        st.warnings.forEach { sb.append(CarMonitor.engineWarningLine(res, it)).append(' ') }
        when {
            !st.dtcsRead -> Unit
            st.dtcs.isEmpty() -> sb.append(res.getString(R.string.voice_engine_no_codes))
            else -> sb.append(res.getString(R.string.obd_voice_codes, st.dtcs.size)).append(' ')
                .append(st.dtcs.take(3).joinToString(". ") { DtcCatalog.describe(it, hindi) })
        }
        return sb.toString().trim().ifEmpty { res.getString(R.string.voice_engine_no_data) }
    }

    fun parked(app: DriveApp, res: Context): String {
        val spot = app.car.store.parked.value ?: return res.getString(R.string.voice_parked_none)
        val here = app.speedTracker.state.value.location
        val time = clockLabel(res, spot.time)
        if (here == null) return res.getString(R.string.voice_parked_at, time)
        val d = CameraGeometry.distanceM(here.latitude, here.longitude, spot.lat, spot.lon)
        if (d < 40) return res.getString(R.string.voice_parked_here, time)
        return res.getString(R.string.voice_parked_away, distanceLabel(res, d), time)
    }
}
