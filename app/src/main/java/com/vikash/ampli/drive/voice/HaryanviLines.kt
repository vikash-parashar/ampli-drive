package com.vikash.ampli.drive.voice

import kotlin.random.Random

/**
 * The "Haryanvi desi" voice pack (Settings › Voice style). Every line lives in this one file so
 * a native speaker can edit them: [Line.deva] is what the Hindi TTS voice reads (Devanagari
 * reads far better than Latin), [Line.roman] is the same line in Latin letters, spoken by the
 * English voice when the unit has no Hindi voice.
 *
 * Rules for editors:
 * - Playful and family-safe only. No abuse, no swearing, nothing sexual, nothing that mocks
 *   anyone: the app is rated for everyone and Play's content policy applies to speech too.
 * - 3 to 5 variants per event so it doesn't get repetitive ([LinePicker] never says the same
 *   line twice in a row).
 * - Safety prompts (SOS, crash countdown) are NOT in this file on purpose: they always use the
 *   clear standard Hindi / app-language strings. [forEvent] returns null for them.
 * - "%1$s" / "%1$d" placeholders keep their order (place name, metres, speed limit).
 *
 * Android has no Haryanvi TTS locale, so these are spoken with hi-IN, else en-IN (roman).
 */
object HaryanviLines {

    data class Line(val deva: String, val roman: String)

    enum class Event(val safety: Boolean = false) {
        /** Car Hub starting after boot or ignition on. */
        Greeting,

        /** "Hey Ampli" session opening: asking where to go. */
        AskDestination,
        Searching,
        StartingNav,
        DidntCatch,
        NotUnderstood,
        Cancelled,

        /** Speed camera ahead: %1$d metres, %2$d limit. */
        CameraAhead,

        /** Speed camera ahead without a known limit: %1$d metres. */
        CameraAheadNoLimit,

        /** Over the limit near a camera. */
        OverSpeed,

        /** Driving too long: break reminder. */
        BreakReminder,

        /** Safety prompts: never playful. */
        Sos(safety = true),
        Crash(safety = true),
    }

    private val LINES: Map<Event, List<Line>> = mapOf(
        Event.Greeting to listOf(
            // Ram Ram bhai! Kit chalna sai aaj?
            Line("राम राम भाई! कित चालणा सै आज?", "Ram Ram bhai! Kit chalna sai aaj?"),
            // Suna tai aale, gaadi tyar sai!
            Line("सुणा ताई आळे, गाड्डी त्यार सै!", "Suna tai aale, gaadi tyar sai!"),
            // Ram Ram! Belt laga le, phir chalange.
            Line("राम राम! बेल्ट ला ले, फेर चालांगे।", "Ram Ram! Belt laga le, phir chalange."),
            // Aa gaya bhai? Chal, aaj kit ki sair karni sai?
            Line("आ ग्या भाई? चाल, आज कित की सैर करणी सै?", "Aa gaya bhai? Chal, aaj kit ki sair karni sai?"),
            // Ram Ram ji! Gaadi bhi ready, main bhi ready.
            Line("राम राम जी! गाड्डी भी रेडी, मैं भी रेडी।", "Ram Ram ji! Gaadi bhi ready, main bhi ready."),
        ),
        Event.AskDestination to listOf(
            // Kit chalna sai?
            Line("कित चालणा सै?", "Kit chalna sai?"),
            // Bata bhai, kit le chalun?
            Line("बता भाई, कित ले चालूं?", "Bata bhai, kit le chalun?"),
            // Haan ji, kade jana sai?
            Line("हां जी, कड़े जाणा सै?", "Haan ji, kade jana sai?"),
            // Bol, kit ka rasta dikhaun?
            Line("बोल, कित का रास्ता दिखाऊं?", "Bol, kit ka rasta dikhaun?"),
        ),
        Event.Searching to listOf(
            // %s dhoond reya sun, ruk ja ek minute.
            Line("%1\$s ढूंढ रया सूं, रुक जा एक मिनट।", "%1\$s dhoond reya sun, ruk ja ek minute."),
            // Thahar ja bhai, %s dekh reya sun.
            Line("ठहर जा भाई, %1\$s देख रया सूं।", "Thahar ja bhai, %1\$s dekh reya sun."),
            // %s? Abhi talash karun.
            Line("%1\$s? इबे तलाश करूं।", "%1\$s? Ibe talash karun."),
        ),
        Event.StartingNav to listOf(
            // Chal bhai, %s chalange. Rasta main bataunga.
            Line("चाल भाई, %1\$s चालांगे। रास्ता मैं बताऊंगा।", "Chal bhai, %1\$s chalange. Rasta main bataunga."),
            // %s ka rasta lag gaya, aaram te chaliyo.
            Line("%1\$s का रास्ता लाग ग्या, आराम तै चालियो।", "%1\$s ka rasta laag gaya, aaram te chaliyo."),
            // Theek sai, %s. Dhyan te chalana bhai.
            Line("ठीक सै, %1\$s। ध्यान तै चलाणा भाई।", "Theek sai, %1\$s. Dhyan te chalana bhai."),
        ),
        Event.DidntCatch to listOf(
            // Ke kahya? Fer te bol.
            Line("के कह्या? फेर तै बोल।", "Ke kahya? Pher te bol."),
            // Samajh na aaya bhai, ek baar fer bata.
            Line("समझ ना आया भाई, एक बार फेर बता।", "Samajh na aaya bhai, ek baar pher bata."),
            // Thoda zor te bol, sun na paaya.
            Line("थोड़ा जोर तै बोल, सुण ना पाया।", "Thoda zor te bol, sun na paaya."),
        ),
        Event.NotUnderstood to listOf(
            // Ya baat mere palle na padi.
            Line("या बात मेरे पल्ले ना पड़ी।", "Ya baat mere palle na padi."),
            // Bhai, yo kaam mere bas ka na sai abhi.
            Line("भाई, यो काम मेरे बस का ना सै इबे।", "Bhai, yo kaam mere bas ka na sai ibe."),
            // Ke kah reya sai? Samjha na.
            Line("के कह रया सै? समझ्या ना।", "Ke kah reya sai? Samjhya na."),
        ),
        Event.Cancelled to listOf(
            // Theek sai bhai, koi baat na.
            Line("ठीक सै भाई, कोए बात ना।", "Theek sai bhai, koi baat na."),
            // Chal chhod, jab chahiye tab bula liyo.
            Line("चाल छोड़, जद चाहिए तद बुला लियो।", "Chal chhod, jad chahiye tad bula liyo."),
            // Raam raam, main yahin sun.
            Line("राम राम, मैं उरे ए सूं।", "Ram Ram, main ure e sun."),
        ),
        Event.CameraAhead to listOf(
            // Dhyan te bhai, aage camera sai, speed kam kar le! %d metre, limit %d.
            Line("ध्यान तै भाई, आगे कैमरा सै, स्पीड कम कर ले! %1\$d मीटर पै, लिमिट %2\$d।", "Dhyan te bhai, aage camera sai, speed kam kar le! %1\$d metre pe, limit %2\$d."),
            // Camera aa reya sai %d metre pe, %d te upar mat jaiyo.
            Line("%1\$d मीटर पै कैमरा आ रया सै, %2\$d तै ऊपर मत जाइयो।", "%1\$d metre pe camera aa reya sai, %2\$d te upar mat jaiyo."),
            // Sambhal ke bhai! %d metre pe camera, limit %d sai.
            Line("संभल कै भाई! %1\$d मीटर पै कैमरा, लिमिट %2\$d सै।", "Sambhal ke bhai! %1\$d metre pe camera, limit %2\$d sai."),
        ),
        Event.CameraAheadNoLimit to listOf(
            // Dhyan te bhai, aage camera sai! %d metre.
            Line("ध्यान तै भाई, आगे कैमरा सै! %1\$d मीटर।", "Dhyan te bhai, aage camera sai! %1\$d metre."),
            // %d metre pe camera sai, aaram te.
            Line("%1\$d मीटर पै कैमरा सै, आराम तै।", "%1\$d metre pe camera sai, aaram te."),
            // Sambhal ke, %d metre pe camera.
            Line("संभल कै, %1\$d मीटर पै कैमरा।", "Sambhal ke, %1\$d metre pe camera."),
        ),
        Event.OverSpeed to listOf(
            // Bawlibooch hai ke? Speed ghata le!
            Line("बावळीबूच है के? स्पीड घटा ले!", "Bawlibooch hai ke? Speed ghata le!"),
            // Aare dheere bhai, chalaan kat jaga!
            Line("अरे धीरे भाई, चालान कट ज्यागा!", "Arre dheere bhai, chalaan kat jyaga!"),
            // Jahaz na sai yo, speed kam kar!
            Line("जहाज ना सै यो, स्पीड कम कर!", "Jahaaz na sai yo, speed kam kar!"),
            // Ghar pe koi wait kar reya sai, dheere chala.
            Line("घरां कोए बाट देख रया सै, धीरे चला।", "Ghara koi baat dekh reya sai, dheere chala."),
        ),
        Event.BreakReminder to listOf(
            // Thak gaya hoga bhai, ek chai ho jaye? (%s hours)
            Line("%1\$s घंटे हो लिए, थक ग्या होगा भाई, एक चाय हो जाए?", "%1\$s ghante ho liye, thak gaya hoga bhai, ek chai ho jaye?"),
            // %s ghante te chala reya sai, thoda ruk ke kamar seedhi kar le.
            Line("%1\$s घंटे तै चला रया सै, थोड़ा रुक कै कमर सीधी कर ले।", "%1\$s ghante te chala reya sai, thoda ruk ke kamar seedhi kar le."),
            // Bhai %s ghante ho gaye, dhaba aave te ruk liyo.
            Line("भाई %1\$s घंटे हो गए, ढाबा आवै तै रुक लियो।", "Bhai %1\$s ghante ho gaye, dhaba aave te ruk liyo."),
        ),
    )

    /** The lines for [event], or null for safety events and events with no desi lines. */
    fun forEvent(event: Event): List<Line>? = if (event.safety) null else LINES[event]

    /** Every playful line, for tests. */
    internal val allLines: Map<Event, List<Line>> get() = LINES
}

/**
 * Picks a random variant per event, never the same one twice in a row. Keeps one memory per
 * event, so a camera alert doesn't reset the greeting's history.
 */
class LinePicker(private val random: Random = Random.Default) {
    private val last = HashMap<HaryanviLines.Event, Int>()

    fun pick(event: HaryanviLines.Event): HaryanviLines.Line? {
        val lines = HaryanviLines.forEvent(event) ?: return null
        if (lines.isEmpty()) return null
        val prev = last[event]
        val index = if (lines.size == 1) {
            0
        } else if (prev == null) {
            random.nextInt(lines.size)
        } else {
            var i = random.nextInt(lines.size - 1)
            if (i >= prev) i++
            i
        }
        last[event] = index
        return lines[index]
    }
}
