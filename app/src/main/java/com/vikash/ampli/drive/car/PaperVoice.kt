package com.vikash.ampli.drive.car

import android.content.Context
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.fastag.AnnualPassMath
import com.vikash.ampli.drive.voice.HaryanviLines
import com.vikash.ampli.drive.voice.Spoken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.Locale

/** One spoken reminder: a paper by [kind], or the FASTag pass ([kind] null). */
data class SpokenReminder(
    val key: String,
    val kind: PaperKind?,
    val daysLeft: Long,
    val expired: Boolean,
    /** FASTag pass trips left, when that is the reason. */
    val tripsLeft: Int? = null,
)

/** Which reminders to say at car start. Pure, unit-tested. */
object PaperVoicePolicy {
    val DAYS_CHOICES = listOf(3, 7, 15)
    const val DEFAULT_DAYS = 7

    /** At most this many per car start, so the welcome stays short. */
    const val MAX_PER_START = 2

    /** An expired paper is repeated at most every few days, not every start. */
    const val EXPIRED_REPEAT_DAYS = 3L

    /**
     * Papers expiring within [daysBefore] (or expired), not already said today ([lastSaid]:
     * key → epoch day), most urgent first, then the pass when its trips run low or it ends soon.
     */
    fun pick(
        papers: List<Paper>,
        today: LocalDate,
        daysBefore: Int,
        lastSaid: Map<String, Long>,
        passTripsLeft: Int? = null,
        passDaysLeft: Long? = null,
    ): List<SpokenReminder> {
        val day = today.toEpochDay()
        fun due(key: String, expired: Boolean): Boolean {
            val last = lastSaid[key] ?: return true
            return day - last >= if (expired) EXPIRED_REPEAT_DAYS else 1L
        }
        val out = ArrayList<SpokenReminder>()
        for (p in papers) {
            val expiry = p.expiry ?: continue
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, expiry)
            if (days > daysBefore) continue
            val key = p.kind.name.lowercase(Locale.US)
            val expired = days < 0
            if (due(key, expired)) out += SpokenReminder(key, p.kind, days, expired)
        }
        out.sortBy { it.daysLeft }
        if (passDaysLeft != null && passTripsLeft != null && passTripsLeft > 0 && passDaysLeft >= 0 && due(PASS_KEY, false)) {
            if (passTripsLeft <= com.vikash.ampli.drive.fastag.AnnualPass.LOW_TRIPS) {
                out += SpokenReminder(PASS_KEY, null, passDaysLeft, expired = false, tripsLeft = passTripsLeft)
            } else if (passDaysLeft <= daysBefore) {
                out += SpokenReminder(PASS_KEY, null, passDaysLeft, expired = false)
            }
        }
        return out.take(MAX_PER_START)
    }

    const val PASS_KEY = "fastag_pass"
}

/**
 * Settings › Car papers › "Remind me out loud": at car start, a few days before insurance, PUC,
 * RC, licence or FASTag KYC expire (dates from the Papers screen), and when the FASTag Annual
 * Pass runs low. Haryanvi style speaks the desi line (recorded voice: paper name + tail clips);
 * Standard style a plain line in the app language. Nothing leaves the unit.
 */
class PaperVoice(private val app: DriveApp) {
    private val sp = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(sp.getBoolean(KEY_ON, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _daysBefore = MutableStateFlow(sp.getInt(KEY_DAYS, PaperVoicePolicy.DEFAULT_DAYS))
    val daysBefore: StateFlow<Int> = _daysBefore.asStateFlow()

    fun setEnabled(on: Boolean) {
        sp.edit().putBoolean(KEY_ON, on).apply()
        _enabled.value = on
    }

    fun setDaysBefore(days: Int) {
        sp.edit().putInt(KEY_DAYS, days).apply()
        _daysBefore.value = days
    }

    private fun lastSaid(): Map<String, Long> =
        sp.all.filterKeys { it.startsWith(PREFIX) }.mapNotNull { (k, v) -> (v as? Long)?.let { k.removePrefix(PREFIX) to it } }.toMap()

    /** What would be said now (for the Settings preview and the start). */
    fun pending(today: LocalDate = LocalDate.now(), ignoreHistory: Boolean = false): List<SpokenReminder> {
        val pass = AnnualPassMath.status(app.fastag.pass.value, app.fastag.events.value, today)
        return PaperVoicePolicy.pick(
            papers = app.car.store.papers.value,
            today = today,
            daysBefore = _daysBefore.value,
            lastSaid = if (ignoreHistory) emptyMap() else lastSaid(),
            passTripsLeft = pass?.takeIf { !it.expired }?.remaining,
            passDaysLeft = pass?.takeIf { !it.expired }?.daysLeft,
        )
    }

    /** CarStart, right after the welcome. Main thread. */
    suspend fun speakAtStart() {
        if (!_enabled.value) return
        if (app.speaker.inCall()) return
        val today = LocalDate.now()
        val list = pending(today)
        for (r in list) {
            say(r)
            sp.edit().putLong(PREFIX + r.key, today.toEpochDay()).apply()
        }
    }

    /** Settings › Hear it now: the most urgent reminder, or a sample when nothing is due. */
    suspend fun sample() {
        val r = pending(ignoreHistory = true).firstOrNull()
            ?: SpokenReminder("insurance", PaperKind.Insurance, 5, expired = false)
        say(r)
    }

    private suspend fun say(r: SpokenReminder) {
        val desi = app.desi
        if (desi.enabled) {
            val spoken: Spoken? = when {
                r.tripsLeft != null -> desi.line(HaryanviLines.Event.PassTripsLow, r.tripsLeft)
                else -> {
                    val hindi = app.speaker.hasVoice(HINDI)
                    val name = if (hindi) devaName(r.key) else romanName(r.key)
                    desi.line(if (r.expired) HaryanviLines.Event.PaperExpired else HaryanviLines.Event.PaperDue, name, r.key)
                }
            }
            if (spoken != null) {
                app.voicePlayer.say(spoken)
                return
            }
        }
        val lang = app.prefs.language.value
        val text = standardLine(app, r)
        app.speaker.say(text, lang)
    }

    companion object {
        private const val FILE = "paper_voice"
        private const val KEY_ON = "on"
        private const val KEY_DAYS = "days"
        private const val PREFIX = "said_"
        private val HINDI: Locale = Locale.forLanguageTag("hi-IN")

        fun devaName(key: String): String = com.vikash.ampli.drive.voice.VoiceClips.PAPER_NAMES[key] ?: key

        fun romanName(key: String): String = when (key) {
            "insurance" -> "beema"
            "puc" -> "PUC"
            "rc" -> "RC"
            "licence" -> "driving licence"
            "fastagkyc" -> "FASTag KYC"
            else -> "FASTag pass"
        }

        /** Plain line in the app language (Standard voice style and the screen). */
        fun standardLine(res: Context, r: SpokenReminder): String {
            val name = r.kind?.let { CarMonitor.paperName(res, it) } ?: res.getString(R.string.pass_title)
            return when {
                r.tripsLeft != null -> res.getString(R.string.pass_voice_trips_low, r.tripsLeft)
                r.expired -> res.getString(R.string.papers_voice_expired, name)
                r.daysLeft == 0L -> res.getString(R.string.papers_voice_today, name)
                else -> res.getString(R.string.papers_voice_due, name, r.daysLeft.toInt())
            }
        }
    }
}
