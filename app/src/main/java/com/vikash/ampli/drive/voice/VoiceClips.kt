package com.vikash.ampli.drive.voice

import java.security.MessageDigest
import java.util.Locale

/**
 * Settings › Voice: who speaks the Haryanvi lines. [Male] and [Female] play the recorded voice
 * pack (app/src/main/assets/voice/<folder>/<id>.ogg); [Tts] is the phone's text-to-speech.
 */
enum class VoicePack(val key: String, val folder: String?) {
    Male("male", "male"),
    Female("female", "female"),
    Tts("tts", null);

    val human: Boolean get() = folder != null

    companion object {
        /** The stored choice, or null when the driver never picked one. */
        fun fromKey(key: String?): VoicePack? = entries.firstOrNull { it.key == key }

        /**
         * The voice in use: the driver's pick, else Male once the male clips are in this build,
         * else the phone voice.
         */
        fun resolve(chosen: VoicePack?, maleClips: Int): VoicePack = chosen ?: if (maleClips > 0) Male else Tts
    }
}

/** One thing to say: [text] for the screen and the TTS fallback, [clips] for the recorded voice. */
data class Spoken(
    val text: String,
    val language: String,
    val clips: List<String> = emptyList(),
    /** An Ampli line from the reviewed bank: Home shows it as the caption while it is said. */
    val caption: Boolean = false,
) {
    /** Said one after the other. The clips only survive when both halves have them. */
    operator fun plus(other: Spoken): Spoken = Spoken(
        text = "$text ${other.text}",
        language = language,
        clips = if (clips.isNotEmpty() && other.clips.isNotEmpty()) clips + other.clips else emptyList(),
        caption = caption && other.caption,
    )
}

/** A line picked from [HaryanviLines], with its stable clip id ("welcome_01"). */
data class Picked(val event: HaryanviLines.Event, val index: Int, val line: HaryanviLines.Line) {
    val id: String get() = VoiceClips.id(event, index)
}

/**
 * The recorded voice pack: clip ids, the stand-ins for lines with a name or number, distance
 * buckets, and the manifest (voice/lines.json) the voice-pack workflow records from. Plain Kotlin,
 * unit-tested.
 *
 * A clip id is "<event in snake case>_<variant, from 01>", so editing a line keeps its id; the
 * workflow stores a hash of the text it recorded (assets/voice/<voice>/clips.json) and the app
 * only plays a clip whose hash still matches the line in [HaryanviLines]. A changed line falls
 * back to TTS until the workflow records it again.
 */
object VoiceClips {

    /** Roundabout exits recorded as whole sentences ("गोल चक्कर पै तीन नंबर…"). */
    val ROUNDABOUT_EXITS = 1..5
    private val EXIT_WORDS = mapOf(1 to "एक", 2 to "दो", 3 to "तीन", 4 to "चार", 5 to "पाँच")

    /** A recorded distance: "चार सौ मीटर". Words, not digits, so the voice reads them in Hindi. */
    data class Distance(val meters: Int, val deva: String) {
        val id: String get() = "dist_${meters}m"
    }

    val DISTANCES: List<Distance> = listOf(
        Distance(50, "पचास मीटर"),
        Distance(100, "सौ मीटर"),
        Distance(150, "डेढ़ सौ मीटर"),
        Distance(200, "दो सौ मीटर"),
        Distance(250, "ढाई सौ मीटर"),
        Distance(300, "तीन सौ मीटर"),
        Distance(350, "साढ़े तीन सौ मीटर"),
        Distance(400, "चार सौ मीटर"),
        Distance(450, "साढ़े चार सौ मीटर"),
        Distance(500, "पाँच सौ मीटर"),
        Distance(600, "छह सौ मीटर"),
        Distance(700, "सात सौ मीटर"),
        Distance(800, "आठ सौ मीटर"),
        Distance(900, "नौ सौ मीटर"),
        Distance(1000, "एक किलोमीटर"),
        Distance(1500, "डेढ़ किलोमीटर"),
        Distance(2000, "दो किलोमीटर"),
        Distance(2500, "ढाई किलोमीटर"),
        Distance(3000, "तीन किलोमीटर"),
        Distance(4000, "चार किलोमीटर"),
        Distance(5000, "पाँच किलोमीटर"),
        Distance(10000, "दस किलोमीटर"),
    )

    /**
     * Recorded numbers one to ten: the FASTag pass "N trips left" and a countdown
     * ([countdownClip]). Words, not digits, so the voice reads them in Hindi.
     */
    val NUMBERS: List<String> = listOf("एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ", "दस")

    fun numberId(n: Int): String = "num_$n"

    /** The clip for [n] (1..10), or null outside that range (the phone voice speaks then). */
    fun countdownClip(n: Int): String? = if (n in 1..NUMBERS.size) numberId(n) else null

    /**
     * Recorded paper names, stitched in front of a [HaryanviLines.Event.PaperDueTail] /
     * [HaryanviLines.Event.PaperExpiredTail] clip. Keys match car.PaperKind names in lower case,
     * plus "fastag_pass".
     */
    val PAPER_NAMES: Map<String, String> = linkedMapOf(
        "insurance" to "बीमा",
        "puc" to "पीयूसी",
        "rc" to "आरसी",
        "licence" to "ड्राइविंग लाइसेंस",
        "fastagkyc" to "फास्टैग केवाईसी",
        "fastag_pass" to "फास्टैग पास",
    )

    fun paperId(key: String): String = "paper_$key"

    /**
     * Safety prompts in the recorded voices, for units without a TTS engine (else they only beep).
     * Plain, clear Hindi, never Haryanvi slang and never from the playful bank; the texts follow
     * the app's Hindi strings (crash_voice, sos_ready_*, voice_sos) with the number taken out.
     */
    val SAFETY: Map<String, String> = linkedMapOf(
        SAFETY_CRASH to "दुर्घटना का पता चला। कुछ सेकंड में आपके इमरजेंसी संपर्क के लिए SOS मैसेज भेजने को तैयार होगा। रद्द करने के लिए ‘मैं ठीक हूँ’ बोलें या दबाएँ।",
        SAFETY_SECONDS_LEFT to "सेकंड बाकी।",
        SAFETY_SOS_READY to "SOS संदेश तैयार है। मैसेज ऐप में भेजें दबाएँ, फिर यह स्क्रीन बचाव दल को दिखाएँ।",
        SAFETY_HELP_PANEL to "मदद पैनल खुल रहा है। आपकी पुष्टि के बिना कुछ नहीं भेजा जाएगा।",
    )

    const val SAFETY_CRASH = "safety_crash"
    const val SAFETY_SECONDS_LEFT = "safety_seconds_left"
    const val SAFETY_SOS_READY = "safety_sos_ready"
    const val SAFETY_HELP_PANEL = "safety_help_panel"

    /**
     * Pro "read WhatsApp messages aloud": the recorded opener said before the phone voice reads
     * the sender and the text (messages.WhatsAppReader). Without the clip, TTS says it all.
     */
    val MESSAGES: Map<String, String> = linkedMapOf(
        "msg_whatsapp" to "व्हाट्सऐप पै मैसेज आया सै।",
    )

    const val MESSAGE_WHATSAPP = "msg_whatsapp"

    /**
     * The crash countdown without TTS: the full warning while more than ten seconds are left,
     * then "<number> सेकंड बाकी।" for the last ten.
     */
    fun crashClips(secondsLeft: Int): List<String> =
        countdownClip(secondsLeft)?.let { listOf(it, SAFETY_SECONDS_LEFT) } ?: listOf(SAFETY_CRASH)

    /** The recorded distance nearest to [meters]; on a tie the shorter one (better early than late). */
    fun bucket(meters: Int): Distance = DISTANCES.minWith(compareBy<Distance> { kotlin.math.abs(it.meters - meters) }.thenBy { it.meters })

    fun id(event: HaryanviLines.Event, index: Int): String =
        snake(event.name) + "_" + (index + 1).toString().padStart(2, '0')

    fun exitId(lineId: String, exit: Int): String = "${lineId}_x$exit"

    private fun snake(name: String): String = name.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase(Locale.US)

    /** A line can be recorded as is when nothing is filled in at run time. */
    fun recordable(line: HaryanviLines.Line): Boolean = !line.deva.contains('%')

    /**
     * What the recorded voice says instead of a line with a name or number in it. The screen
     * still shows the full line (the toll card shows the rupees, the welcome card the name).
     */
    fun recordedEvent(event: HaryanviLines.Event): HaryanviLines.Event = when (event) {
        HaryanviLines.Event.Searching -> HaryanviLines.Event.SearchingPlain
        HaryanviLines.Event.StartingNav -> HaryanviLines.Event.StartingNavPlain
        HaryanviLines.Event.RouteFound -> HaryanviLines.Event.RouteFoundPlain
        HaryanviLines.Event.SpeedLimit -> HaryanviLines.Event.SpeedLimitPlain
        HaryanviLines.Event.TollPaid, HaryanviLines.Event.TollPaidNoBalance -> HaryanviLines.Event.TollPaidPlain
        HaryanviLines.Event.BreakReminder -> HaryanviLines.Event.BreakReminderPlain
        HaryanviLines.Event.FastagLowBalance -> HaryanviLines.Event.FastagLowBalancePlain
        HaryanviLines.Event.MusicPlayNamed -> HaryanviLines.Event.MusicPlay
        else -> event
    }

    /**
     * The clips for [picked] filled with [args]: the line itself when it has no blanks, a stitched
     * distance + tail for camera alerts, the exit-number sentence for roundabouts, else a stand-in
     * line from [pick]. Empty when nothing recorded fits (the phone voice speaks then).
     */
    fun clipsFor(picked: Picked, args: Array<out Any>, pick: (HaryanviLines.Event) -> Picked?): List<String> {
        val event = picked.event
        return when (event) {
            HaryanviLines.Event.NavRoundaboutExit -> {
                val exit = args.firstOrNull() as? Int
                if (exit != null && exit in ROUNDABOUT_EXITS) {
                    listOf(exitId(picked.id, exit))
                } else {
                    listOfNotNull(pick(HaryanviLines.Event.NavRoundabout)?.id)
                }
            }
            HaryanviLines.Event.CameraAhead, HaryanviLines.Event.CameraAheadNoLimit -> {
                val meters = (args.firstOrNull() as? Number)?.toInt() ?: return emptyList()
                val tail = pick(HaryanviLines.Event.CameraAheadTail) ?: return emptyList()
                listOf(bucket(meters).id, tail.id)
            }
            HaryanviLines.Event.NavSoon -> emptyList() // NavPhrases stitches the distance itself.
            // "<paper name>" + tail; args = (shown name, PAPER_NAMES key).
            HaryanviLines.Event.PaperDue, HaryanviLines.Event.PaperExpired -> {
                val key = args.getOrNull(1) as? String ?: return emptyList()
                if (key !in PAPER_NAMES) return emptyList()
                val tailEvent = if (event == HaryanviLines.Event.PaperDue) HaryanviLines.Event.PaperDueTail else HaryanviLines.Event.PaperExpiredTail
                val tail = pick(tailEvent) ?: return emptyList()
                listOf(paperId(key), tail.id)
            }
            // "<number>" + tail, only up to ten.
            HaryanviLines.Event.PassTripsLow -> {
                val n = (args.firstOrNull() as? Number)?.toInt() ?: return emptyList()
                val num = countdownClip(n) ?: return emptyList()
                val tail = pick(HaryanviLines.Event.PassTripsTail) ?: return emptyList()
                listOf(num, tail.id)
            }
            else -> {
                val stand = recordedEvent(event)
                when {
                    stand != event -> listOfNotNull(pick(stand)?.id)
                    recordable(picked.line) -> listOf(picked.id)
                    else -> emptyList()
                }
            }
        }
    }

    /** "<distance> <tail>" for a turn ahead: "चार सौ मीटर" + "आगे,". */
    fun distanceClips(meters: Int, tail: Picked?): List<String> =
        if (tail == null) emptyList() else listOf(bucket(meters).id, tail.id)

    /**
     * One clip the workflow records. [group] lets it pick a delivery tag. [voice] is "male" or
     * "female" for a line worded per voice (only that voice records it), else null (both do).
     */
    data class Entry(val id: String, val event: String, val group: String, val text: String, val voice: String? = null) {
        val hash: String get() = hash(text)
    }

    /** Every clip of the voice pack, in a stable order. */
    val manifest: List<Entry> by lazy { buildManifest() }

    /** Clip id → hash of the text it must have been recorded from. */
    val expectedHashes: Map<String, String> by lazy { manifest.associate { it.id to it.hash } }

    private fun buildManifest(): List<Entry> {
        val out = ArrayList<Entry>()
        for (event in HaryanviLines.Event.entries) {
            val lines = HaryanviLines.forEvent(event) ?: continue
            if (recordedEvent(event) != event) continue // The stand-in is recorded instead.
            lines.forEachIndexed { i, line ->
                val lineId = id(event, i)
                if (event == HaryanviLines.Event.NavRoundaboutExit) {
                    for (exit in ROUNDABOUT_EXITS) {
                        val text = line.deva.replace("%1\$d", EXIT_WORDS.getValue(exit))
                        out += Entry(exitId(lineId, exit), event.name, event.group.name, text)
                    }
                } else if (recordable(line)) {
                    if (line.devaF != null) {
                        out += Entry(lineId + MALE_SUFFIX, event.name, event.group.name, line.deva, VoicePack.Male.key)
                        out += Entry(lineId + FEMALE_SUFFIX, event.name, event.group.name, line.devaF, VoicePack.Female.key)
                    } else {
                        out += Entry(lineId, event.name, event.group.name, line.deva)
                    }
                }
            }
        }
        for (d in DISTANCES) out += Entry(d.id, "Distance", "Distance", d.deva)
        NUMBERS.forEachIndexed { i, w -> out += Entry(numberId(i + 1), "Number", "Number", w) }
        for ((key, name) in PAPER_NAMES) out += Entry(paperId(key), "PaperName", "Papers", name)
        for ((id, text) in SAFETY) out += Entry(id, "Safety", "Safety", text)
        for ((id, text) in MESSAGES) out += Entry(id, "Message", "Messages", text)
        // Round 7: time, date, weather, volume and speed answers (AnswerClips), appended so the
        // ids above keep their order in voice/lines.json.
        for ((id, text) in AnswerClips.ALL) {
            val number = id.startsWith("num_")
            out += Entry(id, if (number) "Number" else "Answer", if (number) "Number" else "Answers", text)
        }
        return out
    }

    /** Clip-id endings of a line worded per voice ([HaryanviLines.Line.devaF]). */
    const val MALE_SUFFIX = "_m"
    const val FEMALE_SUFFIX = "_f"

    /** The id [voice] has [id] under: "<id>_m" / "<id>_f" for a line worded per voice, else [id]. */
    fun voiceId(voice: VoicePack, id: String, known: Set<String> = expectedHashes.keys): String {
        if (id in known) return id
        val suffix = when (voice) {
            VoicePack.Male -> MALE_SUFFIX
            VoicePack.Female -> FEMALE_SUFFIX
            VoicePack.Tts -> return id
        }
        return if (id + suffix in known) id + suffix else id
    }

    /** First 12 hex digits of the SHA-1 of the UTF-8 text. */
    fun hash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }

    /** voice/lines.json: what the voice-pack workflow records. */
    fun manifestJson(): String {
        val sb = StringBuilder()
        sb.append("{\n  \"version\": 1,\n")
        sb.append("  \"note\": \"Generated from HaryanviLines.kt by VoiceManifestTest. Do not edit by hand.\",\n")
        sb.append("  \"characters\": ").append(manifest.sumOf { it.text.length }).append(",\n")
        sb.append("  \"lines\": [\n")
        manifest.forEachIndexed { i, e ->
            sb.append("    {\"id\": ").append(q(e.id))
                .append(", \"event\": ").append(q(e.event))
                .append(", \"group\": ").append(q(e.group))
                .append(", \"text\": ").append(q(e.text))
            if (e.voice != null) sb.append(", \"voice\": ").append(q(e.voice))
            sb
                .append(", \"hash\": ").append(q(e.hash)).append('}')
            sb.append(if (i < manifest.size - 1) ",\n" else "\n")
        }
        sb.append("  ]\n}\n")
        return sb.toString()
    }

    private fun q(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}

/**
 * The clips bundled in this build, per voice: clip id → hash of the text it was recorded from
 * (assets/voice/<voice>/clips.json). Plain Kotlin, unit-tested.
 */
class ClipLibrary(
    private val recorded: Map<VoicePack, Map<String, String>>,
    private val expected: Map<String, String> = VoiceClips.expectedHashes,
) {
    /** True when [id] is recorded for [voice] from the line's current text. */
    fun has(voice: VoicePack, id: String): Boolean {
        val key = VoiceClips.voiceId(voice, id, expected.keys)
        val want = expected[key] ?: return false
        return recorded[voice]?.get(key) == want
    }

    /** True when every clip in [ids] can be played (and there is at least one). */
    fun hasAll(voice: VoicePack, ids: List<String>): Boolean = ids.isNotEmpty() && ids.all { has(voice, it) }

    /** Up-to-date clips for [voice]. */
    fun count(voice: VoicePack): Int = recorded[voice]?.count { (id, h) -> expected[id] == h } ?: 0

    /**
     * How to say [spoken] with [voice]: the clips to play in order, or null for the phone voice
     * (Phone voice picked, a line without clips, or any clip missing or out of date).
     */
    fun plan(voice: VoicePack, spoken: Spoken): List<String>? =
        if (voice.human && hasAll(voice, spoken.clips)) spoken.clips.map { VoiceClips.voiceId(voice, it, expected.keys) } else null

    companion object {
        val EMPTY = ClipLibrary(emptyMap())
    }
}
