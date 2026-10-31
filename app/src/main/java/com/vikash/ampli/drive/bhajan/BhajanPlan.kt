package com.vikash.ampli.drive.bhajan

import com.vikash.ampli.drive.nav.route.MiniJson
import java.time.DayOfWeek

/**
 * One short bhajan bundled with the app (assets/bhajan/manifest.json). Only Ampli's own
 * recordings go in that folder: [original] must be true or the entry is ignored.
 */
data class Bhajan(
    val id: String,
    /** English name for the settings sheet. */
    val title: String,
    /** Hindi name ("राम नाम धुन"). */
    val titleHi: String,
    /** The card's big line while it plays ("जय बजरंगबली!"). */
    val greeting: String,
    /** Asset path, "bhajan/<id>.mp3". */
    val file: String,
    val seconds: Int,
    /** Free users get the free one; the rest need Pro. */
    val free: Boolean,
    /** Days this bhajan suits by default (Tuesday: Hanuman, Saturday: Shani Dev). */
    val days: Set<DayOfWeek>,
    val original: Boolean,
)

/** Settings › When the car starts › Bhajan. Off by default. */
data class BhajanSettings(
    val enabled: Boolean = false,
    /** Days it plays on the first start of the day. */
    val days: Set<DayOfWeek> = DEFAULT_DAYS,
    /** Also every morning (4 to 11) on the first start, whatever the day. */
    val everyMorning: Boolean = false,
    /** The driver's pick per day; a day without one uses the manifest's default for that day. */
    val choice: Map<DayOfWeek, String> = emptyMap(),
    /** Player volume, 0.1 to 1 (low by default: it plays under the road, not over it). */
    val volume: Float = DEFAULT_VOLUME,
) {
    companion object {
        val DEFAULT_DAYS: Set<DayOfWeek> = setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY)
        const val DEFAULT_VOLUME = 0.55f
    }
}

/** What [BhajanPlan.decide] says for this car start. */
sealed interface BhajanDecision {
    data class Play(val bhajan: Bhajan, val volume: Float) : BhajanDecision
    data class Skip(val reason: Reason) : BhajanDecision

    enum class Reason { Off, NotToday, AlreadyPlayed, NoBhajans, FileMissing }
}

/** Which bhajan (if any) plays after the welcome. Pure, unit-tested. */
object BhajanPlan {
    const val MANIFEST = "bhajan/manifest.json"

    /** Never longer than this, whatever the file. */
    const val MAX_MS = 60_000L

    /**
     * @param lastPlayedDay epoch day of the last time a bhajan played (-1 = never): one a day.
     * @param exists whether the bhajan's audio file is in this build (files can lag the manifest).
     */
    fun decide(
        settings: BhajanSettings,
        catalog: List<Bhajan>,
        today: DayOfWeek,
        hour: Int,
        epochDay: Long,
        lastPlayedDay: Long,
        isPro: Boolean,
        exists: (Bhajan) -> Boolean,
    ): BhajanDecision {
        if (!settings.enabled) return BhajanDecision.Skip(BhajanDecision.Reason.Off)
        if (lastPlayedDay == epochDay) return BhajanDecision.Skip(BhajanDecision.Reason.AlreadyPlayed)
        val morning = settings.everyMorning && hour in 4..11
        if (today !in settings.days && !morning) return BhajanDecision.Skip(BhajanDecision.Reason.NotToday)
        val b = pickFor(settings, catalog, today, isPro) ?: return BhajanDecision.Skip(BhajanDecision.Reason.NoBhajans)
        if (!exists(b)) return BhajanDecision.Skip(BhajanDecision.Reason.FileMissing)
        return BhajanDecision.Play(b, settings.volume.coerceIn(0.1f, 1f))
    }

    /**
     * The bhajan for [day]: the driver's pick, else the manifest's default for that day, else the
     * first one. Without Pro only a free bhajan plays (a Pro pick falls back to the free one).
     */
    fun pickFor(settings: BhajanSettings, catalog: List<Bhajan>, day: DayOfWeek, isPro: Boolean): Bhajan? {
        val usable = catalog.filter { it.original }
        if (usable.isEmpty()) return null
        val chosen = settings.choice[day]?.let { id -> usable.firstOrNull { it.id == id } }
            ?: usable.firstOrNull { day in it.days }
            ?: usable.first()
        if (isPro || chosen.free) return chosen
        return usable.firstOrNull { it.free }
    }

    /** True when [b] needs Pro (the settings sheet shows a lock). */
    fun locked(b: Bhajan, isPro: Boolean): Boolean = !isPro && !b.free

    /** Reads assets/bhajan/manifest.json. Bad entries are skipped; a bad file gives an empty list. */
    fun parse(json: String): List<Bhajan> {
        val root = try {
            MiniJson.parse(json) as? Map<*, *>
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        val list = root["bhajans"] as? List<*> ?: return emptyList()
        return list.mapNotNull { e ->
            val o = e as? Map<*, *> ?: return@mapNotNull null
            val id = (o["id"] as? String)?.takeIf { it.matches(Regex("[a-z0-9_]+")) } ?: return@mapNotNull null
            val days = (o["days"] as? List<*>).orEmpty().mapNotNull { d -> dayOf(d as? String) }.toSet()
            Bhajan(
                id = id,
                title = o["title"] as? String ?: id,
                titleHi = o["titleHi"] as? String ?: (o["title"] as? String ?: id),
                greeting = o["greeting"] as? String ?: "",
                file = (o["file"] as? String)?.takeIf { it.startsWith("bhajan/") && !it.contains("..") } ?: "bhajan/$id.mp3",
                seconds = (o["seconds"] as? Double)?.toInt() ?: 45,
                free = o["free"] as? Boolean ?: false,
                days = days,
                original = o["original"] as? Boolean ?: false,
            )
        }
    }

    /** "TUE" / "tuesday" → TUESDAY. */
    fun dayOf(s: String?): DayOfWeek? {
        val t = s?.trim()?.uppercase() ?: return null
        if (t.length < 3) return null
        return DayOfWeek.entries.firstOrNull { it.name.startsWith(t.take(3)) }
    }

    /** "TUE,SAT" for storage. */
    fun encodeDays(days: Set<DayOfWeek>): String = days.sortedBy { it.value }.joinToString(",") { it.name.take(3) }

    fun decodeDays(s: String?): Set<DayOfWeek>? = s?.split(',')?.mapNotNull { dayOf(it) }?.toSet()

    /** "TUE=hanuman,SAT=shani" for storage. */
    fun encodeChoice(choice: Map<DayOfWeek, String>): String =
        choice.entries.sortedBy { it.key.value }.joinToString(",") { it.key.name.take(3) + "=" + it.value }

    fun decodeChoice(s: String?): Map<DayOfWeek, String> = s.orEmpty().split(',').mapNotNull { part ->
        val (d, id) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        (dayOf(d) ?: return@mapNotNull null) to id.trim()
    }.toMap()
}
