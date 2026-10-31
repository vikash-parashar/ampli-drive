package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.weather.Sky
import kotlin.math.roundToInt

/**
 * Round 7 spoken answers (time, date, weather, volume, speed) for units without a TTS engine:
 * short recorded pieces stitched together, like the distance + tail of a camera alert. "अभी" +
 * "शाम के" + "पाँच" + "बजकर" + "बीस" + "मिनट हुए हैं।". Plain Kotlin, unit-tested.
 *
 * The pieces are in the voice-pack manifest (voice/lines.json, group "Answers") so the voice-pack
 * workflow records them; voice/round7-lines.md is the review list. Until they are recorded the
 * answer card shows the answer and a short recorded acknowledgement plays instead.
 *
 * Plain, clear Hindi for the shared pieces (numbers, months, days); the sentence pieces have a
 * Haryanvi twin ("_hr") used when the Haryanvi desi style is on. Family-safe like the rest.
 */
object AnswerClips {

    /** Numbers 11 to 59 (1 to 10 are [VoiceClips.NUMBERS]), for minutes, dates and degrees. */
    val NUMBERS_MORE: List<String> = listOf(
        "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस", "बीस",
        "इक्कीस", "बाईस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस", "अट्ठाईस", "उनतीस", "तीस",
        "इकतीस", "बत्तीस", "तैंतीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अड़तीस", "उनतालीस", "चालीस",
        "इकतालीस", "बयालीस", "तैंतालीस", "चवालीस", "पैंतालीस", "छियालीस", "सैंतालीस", "अड़तालीस", "उनचास", "पचास",
        "इक्यावन", "बावन", "तिरेपन", "चौवन", "पचपन", "छप्पन", "सत्तावन", "अट्ठावन", "उनसठ",
    )

    /** The recorded number for [n] (1 to 59), or null. */
    fun number(n: Int): String? = if (n in 1..(10 + NUMBERS_MORE.size)) VoiceClips.numberId(n) else null

    private val WEEKDAYS = listOf("सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार", "रविवार")
    private val MONTHS = listOf(
        "जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर",
    )

    /** Plain pieces, said the same in both styles. */
    val SHARED: Map<String, String> = linkedMapOf<String, String>().apply {
        put("t_now", "अभी")
        put("t_morning", "सुबह के")
        put("t_afternoon", "दोपहर के")
        put("t_evening", "शाम के")
        put("t_night", "रात के")
        put("t_baj_kar", "बजकर")
        put("d_today", "आज")
        WEEKDAYS.forEachIndexed { i, w -> put("wd_${i + 1}", w) }
        MONTHS.forEachIndexed { i, m -> put("mon_${i + 1}", m) }
        put("wx_temp", "तापमान")
        put("sp_prefix", "अभी स्पीड")
    }

    /** Sentence pieces in plain Hindi (the Standard style). */
    val PLAIN: Map<String, String> = linkedMapOf(
        "t_minutes" to "मिनट हुए हैं।",
        "t_oclock" to "बजे हैं।",
        "d_is" to "है।",
        "wx_clear" to "अभी मौसम साफ़ है।",
        "wx_partly" to "अभी हल्के बादल हैं।",
        "wx_cloudy" to "अभी बादल छाए हैं।",
        "wx_rain" to "अभी बारिश हो रही है।",
        "wx_heavy_rain" to "अभी तेज़ बारिश हो रही है, ध्यान से चलाइए।",
        "wx_thunder" to "अभी आंधी-तूफ़ान का मौसम है, ध्यान से चलाइए।",
        "wx_fog" to "अभी धुंध है।",
        "wx_snow" to "अभी बर्फ़ पड़ रही है।",
        "wx_degrees" to "डिग्री है।",
        "wx_rain_yes" to "अगले कुछ घंटों में बारिश के आसार हैं।",
        "wx_rain_no" to "अगले कुछ घंटों में बारिश के आसार नहीं हैं।",
        "wx_offline" to "इंटरनेट नहीं है, अभी मौसम नहीं देख सकते।",
        "wx_failed" to "अभी मौसम की जानकारी नहीं मिल रही, थोड़ी देर बाद पूछिए।",
        "no_location" to "अभी लोकेशन नहीं मिल रही।",
        "sp_kmh" to "किलोमीटर प्रति घंटा है।",
        "vol_up" to "आवाज़ बढ़ा दी।",
        "vol_down" to "आवाज़ कम कर दी।",
        "vol_max" to "आवाज़ पूरी कर दी।",
        "vol_mute" to "आवाज़ बंद कर दी।",
        "vol_unmute" to "आवाज़ फिर से चालू।",
        "nav_stopped" to "नेविगेशन बंद कर दिया।",
    )

    /** The same pieces in Haryanvi (Haryanvi desi style). Same ids with "_hr". */
    val HARYANVI: Map<String, String> = linkedMapOf(
        "t_minutes" to "मिनट होगे।",
        "t_oclock" to "बज गे सैं।",
        "d_is" to "सै।",
        "wx_clear" to "मौसम एकदम साफ सै।",
        "wx_partly" to "थोड़े-थोड़े बादळ सैं।",
        "wx_cloudy" to "बादळ छाए होए सैं।",
        "wx_rain" to "मींह पड़ रया सै।",
        "wx_heavy_rain" to "जोर का मींह पड़ रया सै, संभल कै चलाइए।",
        "wx_thunder" to "आंधी-तूफान का मौसम सै, ध्यान राखिए।",
        "wx_fog" to "धुंध घणी सै।",
        "wx_snow" to "बर्फ पड़ री सै।",
        "wx_degrees" to "डिग्री सै।",
        "wx_rain_yes" to "अगले कुछ घंट्यां मैं मींह आण के आसार सैं।",
        "wx_rain_no" to "अगले कुछ घंट्यां मैं मींह का कोए आसार कोनी।",
        "wx_offline" to "इंटरनेट कोनी चाल रया, इब मौसम ना देख सकां।",
        "wx_failed" to "मौसम का हाल इब कोनी मिल रया, थोड़ी वार पाछै पूछ लिए।",
        "no_location" to "लोकेशन कोनी मिल री।",
        "sp_kmh" to "किलोमीटर प्रति घंटा सै।",
        "vol_up" to "आवाज बढ़ा दी भाई।",
        "vol_down" to "आवाज कम कर दी।",
        "vol_max" to "आवाज पूरी खोल दी।",
        "vol_mute" to "आवाज बंद कर दी।",
        "vol_unmute" to "आवाज फेर चालू।",
        "nav_stopped" to "रास्ता बंद कर दिया।",
    )

    /** Every piece the voice pack records: id → text. */
    val ALL: Map<String, String> by lazy {
        linkedMapOf<String, String>().apply {
            NUMBERS_MORE.forEachIndexed { i, w -> put(VoiceClips.numberId(11 + i), w) }
            putAll(SHARED)
            putAll(PLAIN)
            for ((id, text) in HARYANVI) put(id + "_hr", text)
        }
    }

    /** The sentence piece [id] in the right style. */
    fun styled(id: String, haryanvi: Boolean): String = if (haryanvi && id in HARYANVI) id + "_hr" else id

    /** "अभी शाम के पाँच बजकर बीस मिनट हुए हैं।" for [hour] 0 to 23 and [minute] 0 to 59. */
    fun time(hour: Int, minute: Int, haryanvi: Boolean): List<String> {
        val h12 = ((hour + 11) % 12) + 1
        val part = when (hour) {
            in 4..11 -> "t_morning"
            in 12..15 -> "t_afternoon"
            in 16..19 -> "t_evening"
            else -> "t_night"
        }
        val out = mutableListOf("t_now", part, number(h12)!!)
        if (minute == 0) {
            out += styled("t_oclock", haryanvi)
        } else {
            out += "t_baj_kar"
            out += number(minute) ?: return emptyList()
            out += styled("t_minutes", haryanvi)
        }
        return out
    }

    /** "आज बुधवार तीस अक्टूबर है।"; [dayOfWeek] 1 = Monday, [month] 1 = January. */
    fun date(dayOfWeek: Int, dayOfMonth: Int, month: Int, haryanvi: Boolean): List<String> {
        if (dayOfWeek !in 1..7 || month !in 1..12) return emptyList()
        val day = number(dayOfMonth) ?: return emptyList()
        return listOf("d_today", "wd_$dayOfWeek", day, "mon_$month", styled("d_is", haryanvi))
    }

    fun skyId(sky: Sky): String = when (sky) {
        Sky.Clear -> "wx_clear"
        Sky.PartlyCloudy -> "wx_partly"
        Sky.Cloudy -> "wx_cloudy"
        Sky.Rain -> "wx_rain"
        Sky.HeavyRain -> "wx_heavy_rain"
        Sky.Thunder -> "wx_thunder"
        Sky.Fog -> "wx_fog"
        Sky.Snow -> "wx_snow"
    }

    /**
     * "अभी हल्के बादल हैं। तापमान इकतीस डिग्री है। अगले कुछ घंटों में बारिश के आसार नहीं हैं।"
     * For "will it rain?" the rain part comes first. The temperature is left out outside 1 to 59 °C.
     */
    fun weather(sky: Sky, temperatureC: Double?, rainLikely: Boolean, rainQuestion: Boolean, haryanvi: Boolean): List<String> {
        val skyPart = listOf(styled(skyId(sky), haryanvi))
        val t = temperatureC?.roundToInt()?.let { number(it) }
        val tempPart = if (t != null) listOf("wx_temp", t, styled("wx_degrees", haryanvi)) else emptyList()
        val rainPart = listOf(styled(if (rainLikely) "wx_rain_yes" else "wx_rain_no", haryanvi))
        return if (rainQuestion) rainPart + skyPart + tempPart else skyPart + tempPart + rainPart
    }

    /** "अभी स्पीड चौवन किलोमीटर प्रति घंटा है।", only up to 59 (higher speeds: card + acknowledgement). */
    fun speed(kmh: Int, haryanvi: Boolean): List<String> {
        val n = number(kmh) ?: return emptyList()
        return listOf("sp_prefix", n, styled("sp_kmh", haryanvi))
    }

    /** One sentence piece ("wx_offline", "vol_up", …) in the right style. */
    fun line(id: String, haryanvi: Boolean): List<String> = listOf(styled(id, haryanvi))
}
