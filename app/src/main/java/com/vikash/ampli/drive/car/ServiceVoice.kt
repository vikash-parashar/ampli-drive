package com.vikash.ampli.drive.car

import android.content.Context
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.voice.HaryanviLines
import java.time.LocalDate
import kotlin.math.abs

/**
 * Car start, after the papers: "इंजन ऑयल मैं 420 किलोमीटर बाकी सै, टाइम पै करवा लिए।" for the
 * service job due soonest (Papers and service › service jobs, [ServiceVoicePolicy]). Shares the
 * papers' "Remind me out loud" switch. Haryanvi style speaks the desi line, Standard a plain one.
 * Everything stays on the unit.
 */
class ServiceVoice(private val app: DriveApp) {
    private val sp = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun lastSaid(): Map<String, Long> =
        sp.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    fun pending(today: LocalDate = LocalDate.now()): List<ServiceReminder> = ServiceVoicePolicy.pick(
        items = app.car.store.services.value,
        today = today,
        odometerKm = app.car.store.odometerKm.value,
        lastSaid = lastSaid(),
    )

    /** CarStart, right after [PaperVoice.speakAtStart]. Main thread. */
    suspend fun speakAtStart() {
        if (!app.paperVoice.enabled.value) return
        if (app.speaker.inCall()) return
        val today = LocalDate.now()
        for (r in pending(today)) {
            say(r)
            sp.edit().putLong(r.id, today.toEpochDay()).apply()
        }
    }

    private suspend fun say(r: ServiceReminder) {
        val (event, args) = when {
            r.overdue -> HaryanviLines.Event.ServiceOverdue to arrayOf<Any>(r.name)
            r.kmLeft != null -> HaryanviLines.Event.ServiceDueKm to arrayOf<Any>(r.name, r.kmLeft)
            else -> HaryanviLines.Event.ServiceDueDays to arrayOf<Any>(r.name, (r.daysLeft ?: 0L).toInt())
        }
        val line = app.desi.line(event, *args)
        if (line != null) {
            app.voicePlayer.say(line)
            return
        }
        val lang = app.car.language()
        app.speaker.say(plainLine(app.car.localized(lang), r), lang)
    }

    companion object {
        private const val FILE = "service_voice"

        /** "Engine oil: due in 420 km" in the app language (Standard style, and recorded voices). */
        fun plainLine(res: Context, r: ServiceReminder): String = when {
            r.overdue && r.kmLeft != null -> res.getString(R.string.service_voice_overdue_km, r.name, abs(r.kmLeft))
            r.overdue -> res.getString(R.string.service_voice_overdue, r.name)
            r.kmLeft != null -> res.getString(R.string.service_voice_due_km, r.name, r.kmLeft)
            else -> res.getString(R.string.service_voice_due_days, r.name, (r.daysLeft ?: 0L).toInt())
        }
    }
}
