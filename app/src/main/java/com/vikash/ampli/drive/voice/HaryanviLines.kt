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

        // Turn-by-turn (Settings › Haryanvi navigation prompts), read from Google Maps' notification.
        // A far prompt is NavSoon + the manoeuvre ("400 मीटर आगे, बाएं हाथ नै मुड़ ले"),
        // a near one NavNow + the manoeuvre ("इब्बे, बाएं हाथ नै मुड़ ले").

        /** Prefix for a turn ahead: %1$s is the distance ("400 मीटर"). */
        NavSoon,

        /** Prefix for a turn right now. */
        NavNow,
        NavLeft,
        NavRight,
        NavSlightLeft,
        NavSlightRight,
        NavSharpLeft,
        NavSharpRight,
        NavUTurn,
        NavKeepLeft,
        NavKeepRight,
        NavFlyover,
        NavRamp,
        NavExit,
        NavRoundabout,

        /** Roundabout with a known exit: %1$d is the exit number. */
        NavRoundaboutExit,
        NavArrive,
        NavContinue,

        /** Faster than the known limit while navigating: %1$d is the limit. */
        SpeedLimit,

        /** Still over the limit after the first warning. */
        SpeedEaseOff,

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
            // Dheere kar le, aage camera sai! %1\$d metre.
            Line("धीरे कर ले, आगे कैमरा सै! %1\$d मीटर।", "Dheere kar le, aage camera sai! %1\$d metre."),
            // Dhyan te bhai, aage camera sai! %1\$d metre.
            Line("ध्यान तै भाई, आगे कैमरा सै! %1\$d मीटर।", "Dhyan te bhai, aage camera sai! %1\$d metre."),
            // %1\$d metre pe camera sai, aaram te.
            Line("%1\$d मीटर पै कैमरा सै, आराम तै।", "%1\$d metre pe camera sai, aaram te."),
            // Sambhal ke, %1\$d metre pe camera.
            Line("संभल कै, %1\$d मीटर पै कैमरा।", "Sambhal ke, %1\$d metre pe camera."),
        ),
        Event.OverSpeed to listOf(
            // Mandi kar le bhai, is road pe speed kam sai.
            Line("मंदी कर ले भाई, इस रोड पे स्पीड कम सै।", "Mandi kar le bhai, is road pe speed kam sai."),
            // Dheere kar le, aage camera sai.
            Line("धीरे कर ले, आगे कैमरा सै।", "Dheere kar le, aage camera sai."),
            // Tau, gaddi mandi kar le.
            Line("ताऊ, गाड्डी मंदी कर ले।", "Tau, gaddi mandi kar le."),
            // Arre dheere bhai, chalaan kat jyaga!
            Line("अरे धीरे भाई, चालान कट ज्यागा!", "Arre dheere bhai, chalaan kat jyaga!"),
            // Ghara koi baat dekh reya sai, dheere chala.
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
        Event.NavSoon to listOf(
            // Aage %1\$s pe
            Line("आगे %1\$s पे", "Aage %1\$s pe"),
            // %1\$s aage,
            Line("%1\$s आगे,", "%1\$s aage,"),
            // Sun bhai, %1\$s aage,
            Line("सुण भाई, %1\$s आगे,", "Sun bhai, %1\$s aage,"),
        ),
        Event.NavNow to listOf(
            // Ib
            Line("इब", "Ib"),
            // Abhi,
            Line("अभी,", "Abhi,"),
            // Bas ibbe,
            Line("बस इब्बे,", "Bas ibbe,"),
        ),
        Event.NavLeft to listOf(
            // ulte haath le le.
            Line("उल्टे हाथ ले ले।", "ulte haath le le."),
            // baayen haath nai mud le bhai.
            Line("बाएं हाथ नै मुड़ ले भाई।", "baayen haath nai mud le bhai."),
            // ulte haath nai kaat le.
            Line("उल्टे हाथ नै काट ले।", "ulte haath nai kaat le."),
            // baayen mud jya, aaram te.
            Line("बाएं मुड़ ज्या, आराम तै।", "baayen mud jya, aaram te."),
            // left le le bhai.
            Line("लेफ्ट ले ले भाई।", "left le le bhai."),
        ),
        Event.NavRight to listOf(
            // sulte haath le le.
            Line("सुल्टे हाथ ले ले।", "sulte haath le le."),
            // seedhe haath mud le.
            Line("सीधे हाथ मुड़ ले।", "seedhe haath mud le."),
            // daayen haath nai mud le bhai.
            Line("दाएं हाथ नै मुड़ ले भाई।", "daayen haath nai mud le bhai."),
            // daayen mud jya, aaram te.
            Line("दाएं मुड़ ज्या, आराम तै।", "daayen mud jya, aaram te."),
            // right le le bhai.
            Line("राइट ले ले भाई।", "right le le bhai."),
        ),
        Event.NavSlightLeft to listOf(
            // halka sa baayen nai ho le.
            Line("हल्का सा बाएं नै हो ले।", "halka sa baayen nai ho le."),
            // thoda sa ulte haath nai daba le.
            Line("थोड़ा सा उल्टे हाथ नै दबा ले।", "thoda sa ulte haath nai daba le."),
            // baayen nai halka sarak le.
            Line("बाएं नै हल्का सरक ले।", "baayen nai halka sarak le."),
        ),
        Event.NavSlightRight to listOf(
            // halka sa daayen nai ho le.
            Line("हल्का सा दाएं नै हो ले।", "halka sa daayen nai ho le."),
            // thoda sa sulte haath nai daba le.
            Line("थोड़ा सा सुलटे हाथ नै दबा ले।", "thoda sa sulte haath nai daba le."),
            // daayen nai halka sarak le.
            Line("दाएं नै हल्का सरक ले।", "daayen nai halka sarak le."),
        ),
        Event.NavSharpLeft to listOf(
            // poora baayen nai ghuma le, teekha mod sai.
            Line("पूरा बाएं नै घुमा ले, तीखा मोड़ सै।", "poora baayen nai ghuma le, teekha mod sai."),
            // teekha mod sai, baayen nai poora kaat le.
            Line("तीखा मोड़ सै, बाएं नै पूरा काट ले।", "teekha mod sai, baayen nai poora kaat le."),
            // sambhal ke, tez baayen mod sai.
            Line("संभल कै, तेज बाएं मोड़ सै।", "sambhal ke, tez baayen mod sai."),
        ),
        Event.NavSharpRight to listOf(
            // poora daayen nai ghuma le, teekha mod sai.
            Line("पूरा दाएं नै घुमा ले, तीखा मोड़ सै।", "poora daayen nai ghuma le, teekha mod sai."),
            // teekha mod sai, daayen nai poora kaat le.
            Line("तीखा मोड़ सै, दाएं नै पूरा काट ले।", "teekha mod sai, daayen nai poora kaat le."),
            // sambhal ke, tez daayen mod sai.
            Line("संभल कै, तेज दाएं मोड़ सै।", "sambhal ke, tez daayen mod sai."),
        ),
        Event.NavUTurn to listOf(
            // gaddi ghuma le, U-turn le le.
            Line("गाड्डी घुमा ले, यू-टर्न ले ले।", "gaddi ghuma le, U-turn le le."),
            // U-turn maar le bhai, ulta ghoom le.
            Line("यू-टर्न मार ले भाई, उल्टा घूम ले।", "U-turn maar le bhai, ulta ghoom le."),
            // gaadi paachhe nai ghuma le, U-turn sai.
            Line("गाड्डी पाच्छै नै घुमा ले, यू-टर्न सै।", "gaadi paachhe nai ghuma le, U-turn sai."),
            // U-turn le le, dheere te.
            Line("यू-टर्न ले ले, धीरे तै।", "U-turn le le, dheere te."),
            // ulta ghuma le bhai, rasta paachhe sai.
            Line("उल्टा घुमा ले भाई, रास्ता पाच्छै सै।", "ulta ghuma le bhai, rasta paachhe sai."),
        ),
        Event.NavKeepLeft to listOf(
            // baayen wali lane mein rahiyo.
            Line("बाएं आळी लेन मैं रहियो।", "baayen aali lane mein rahiyo."),
            // baayen nai chipak ke chaliye.
            Line("बाएं नै चिपक कै चालिए।", "baayen nai chipak ke chaliye."),
            // baayen paase reh bhai.
            Line("बाएं पास्सै रह भाई।", "baayen paase reh bhai."),
        ),
        Event.NavKeepRight to listOf(
            // daayen wali lane mein rahiyo.
            Line("दाएं आळी लेन मैं रहियो।", "daayen aali lane mein rahiyo."),
            // daayen nai chipak ke chaliye.
            Line("दाएं नै चिपक कै चालिए।", "daayen nai chipak ke chaliye."),
            // daayen paase reh bhai.
            Line("दाएं पास्सै रह भाई।", "daayen paase reh bhai."),
        ),
        Event.NavFlyover to listOf(
            // flyover pe le le.
            Line("फ्लाईओवर पे ले ले।", "flyover pe le le."),
            // flyover pe chadh le bhai.
            Line("फ्लाईओवर पे चढ़ ले भाई।", "flyover pe chadh le bhai."),
            // upar aala pul pakad le.
            Line("ऊपर आळा पुल पकड़ ले।", "upar aala pul pakad le."),
            // flyover le le, neeche mat utariyo.
            Line("फ्लाईओवर ले ले, नीचै मत उतरियो।", "flyover le le, neeche mat utariyo."),
        ),
        Event.NavRamp to listOf(
            // ramp pe le le.
            Line("रैंप पे ले ले।", "ramp pe le le."),
            // ramp pakad le bhai.
            Line("रैंप पकड़ ले भाई।", "ramp pakad le bhai."),
            // ramp pe chadh le, aaram te.
            Line("रैंप पै चढ़ ले, आराम तै।", "ramp pe chadh le, aaram te."),
            // side aala ramp le le.
            Line("साइड आळा रैंप ले ले।", "side aala ramp le le."),
        ),
        Event.NavExit to listOf(
            // exit le le bhai.
            Line("एग्जिट ले ले भाई।", "exit le le bhai."),
            // bahar nikal le, exit aa gaya.
            Line("बाहर लिकड़ ले, एग्जिट आ ग्या।", "bahar likad le, exit aa gaya."),
            // highway te utar le, exit sai.
            Line("हाईवे तै उतर ले, एग्जिट सै।", "highway te utar le, exit sai."),
        ),
        Event.NavRoundabout to listOf(
            // gol chakkar aa reya sai, sambhal ke.
            Line("गोल चक्कर आ रया सै, संभल कै।", "gol chakkar aa reya sai, sambhal ke."),
            // chowk pe ghoom le bhai, gol chakkar sai.
            Line("चौक पै घूम ले भाई, गोल चक्कर सै।", "chowk pe ghoom le bhai, gol chakkar sai."),
            // gol chakkar sai, dheere kar le.
            Line("गोल चक्कर सै, धीरे कर ले।", "gol chakkar sai, dheere kar le."),
        ),
        Event.NavRoundaboutExit to listOf(
            // gol chakkar pe %d number wala rasta pakad le.
            Line("गोल चक्कर पै %1\$d नंबर आळा रास्ता पकड़ ले।", "gol chakkar pe %1\$d number aala rasta pakad le."),
            // chakkar mein ghoom ke %d number se nikal le.
            Line("चक्कर मैं घूम कै %1\$d नंबर तै लिकड़ ले।", "chakkar mein ghoom ke %1\$d number te likad le."),
            // gol chakkar sai, %d number ka rasta lena sai.
            Line("गोल चक्कर सै, %1\$d नंबर का रास्ता लेणा सै।", "gol chakkar sai, %1\$d number ka rasta lena sai."),
        ),
        Event.NavArrive to listOf(
            // Pahunch gaye tau!
            Line("पहुँच गए ताऊ!", "Pahunch gaye tau!"),
            // Pahunch ge bhai, manzil aa gi!
            Line("पहुंच गे भाई, मंजिल आ गी!", "Pahunch ge bhai, manzil aa gi!"),
            // Lyo ji, aa gaya thikana!
            Line("ल्यो जी, आ ग्या ठिकाणा!", "Lyo ji, aa gaya thikana!"),
            // Bas bhai, yaade e sai manzil.
            Line("बस भाई, याड़े ए सै मंजिल।", "Bas bhai, yaade e sai manzil."),
            // Aa ge bhai, gaadi side la le aaram te.
            Line("आ गे भाई, गाड्डी साइड ला ले आराम तै।", "Aa ge bhai, gaadi side la le aaram te."),
        ),
        Event.NavContinue to listOf(
            // Sidha chal.
            Line("सीधा चाल।", "Sidha chal."),
            // Seedha chalta reh bhai.
            Line("सीधा चालता रह भाई।", "Seedha chalta reh bhai."),
            // Seedhe seedhe chal, kit mudna koni.
            Line("सीधे सीधे चाल, कित मुड़णा कोनी।", "Seedhe seedhe chal, kit mudna koni."),
            // Seedha rasta sai, mauj te chala.
            Line("सीधा रास्ता सै, मौज तै चला।", "Seedha rasta sai, mauj te chala."),
        ),
        Event.SpeedLimit to listOf(
            // Mandi kar le bhai, is road pe speed kam sai.
            Line("मंदी कर ले भाई, इस रोड पे स्पीड कम सै।", "Mandi kar le bhai, is road pe speed kam sai."),
            // Tau, gaddi mandi kar le.
            Line("ताऊ, गाड्डी मंदी कर ले।", "Tau, gaddi mandi kar le."),
            // Speed ghata le bhai, limit %1\$d sai.
            Line("स्पीड घटा ले भाई, लिमिट %1\$d सै।", "Speed ghata le bhai, limit %1\$d sai."),
            // Dheere kar le, aade %1\$d ki limit sai.
            Line("धीरे कर ले, आड़े %1\$d की लिमिट सै।", "Dheere kar le, aade %1\$d ki limit sai."),
            // %1\$d te upar na, chalaan kat jyaga.
            Line("%1\$d तै ऊपर ना, चालान कट ज्यागा।", "%1\$d te upar na, chalaan kat jyaga."),
        ),
        Event.SpeedEaseOff to listOf(
            // Chhod de bhai, race chhod de.
            Line("छोड़ दे भाई, रेस छोड़ दे।", "Chhod de bhai, race chhod de."),
            // Race na sai bhai, aaram te chala.
            Line("रेस ना सै भाई, आराम तै चला।", "Race na sai bhai, aaram te chala."),
            // Pair hata le accelerator te, bhai.
            Line("पैर हटा ले एक्सीलेटर तै, भाई।", "Pair hata le accelerator te, bhai."),
            // Itni jaldi ke sai bhai, ghara sab baat dekhe sai.
            Line("इतनी जल्दी के सै भाई, घरां सब बाट देखैं सैं।", "Itni jaldi ke sai bhai, ghara sab baat dekhe sai."),
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
