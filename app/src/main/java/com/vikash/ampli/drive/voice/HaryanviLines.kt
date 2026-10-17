package com.vikash.ampli.drive.voice

import kotlin.random.Random

/**
 * The "Haryanvi desi" personality (Settings › Voice style, the default): every playful line the
 * car speaks lives in this one file, so a native speaker can review and change them. The same
 * lines are listed in docs/haryanvi-lines.md for review (a unit test keeps the two in step).
 *
 * Each [Line] has:
 * - [Line.deva]: what the Hindi TTS voice reads (Devanagari reads far better than Latin),
 * - [Line.roman]: the same line in Latin letters, spoken by the English voice when the unit has
 *   no Hindi voice,
 * - [Line.en]: what it means in English, for reviewers only (never spoken).
 *
 * Rules for editors:
 * - Playful and family-safe only. No abuse, no swearing, nothing crude or sexual, no caste or
 *   community jokes, nothing that mocks anyone: the app is rated for everyone and Play's content
 *   policy applies to speech too. HaryanviLinesTest checks a banned-word list over the whole bank.
 * - 4 to 8 variants per event so it doesn't get repetitive ([LinePicker] never says the same
 *   line twice in a row).
 * - Left is "उल्टे हाथ" (ulte haath), right is "सुल्टे हाथ" (sulte haath).
 * - Safety prompts (SOS, crash countdown, emergency) are NOT in this file on purpose: they always
 *   use the clear standard Hindi / app-language strings. [forEvent] returns null for them.
 * - "%1$s" / "%1$d" placeholders keep their number in both scripts ([sampleArgs] shows what goes in).
 *
 * Android has no Haryanvi TTS locale, so these are spoken with hi-IN, else en-IN (roman).
 */
object HaryanviLines {

    data class Line(val deva: String, val roman: String, val en: String = "")

    /** Sections of Settings › Voice › Hear samples and of the review file. */
    enum class Group(val title: String) {
        Welcome("Welcome and goodbye"),
        Voice("Hey Ampli"),
        Navigation("Navigation"),
        Speed("Speed and cameras"),
        Road("Toll, breaks, parking, weather"),
        Car("Car health (OBD, Pro)"),
        Music("Music"),
        Talk("Small talk"),

        /** Recorded human voice only: stand-ins for lines with a name or number in them. */
        HumanVoice("Human voice only (no names or numbers)"),
        Safety("Safety (always plain Hindi)"),
    }

    enum class Event(val group: Group, val title: String, val safety: Boolean = false) {
        /** Car start (boot / ACC on). Never with the owner's name: that shows on the card only. */
        Welcome(Group.Welcome, "Welcome on car start"),

        // Time-of-day line said after the welcome (and after the Hey Ampli greeting).
        WelcomeMorning(Group.Welcome, "Morning (4 to 11)"),
        WelcomeAfternoon(Group.Welcome, "Afternoon (12 to 4 pm)"),
        WelcomeEvening(Group.Welcome, "Evening (5 to 8 pm)"),
        WelcomeNight(Group.Welcome, "Night (9 pm to 3 am)"),

        /** ACC off, or parked after arriving. */
        Farewell(Group.Welcome, "Goodbye (ACC off / parked)"),

        /** "Hey Ampli" session opening, at most every 3 hours: asking where to go. */
        Greeting(Group.Voice, "Hey Ampli greeting"),

        /** "Hey Ampli" between greetings: a short "yes, tell me". */
        WakeReply(Group.Voice, "Hey Ampli reply"),
        AskDestination(Group.Voice, "Asking where to go"),

        /** %1$s is the place. */
        Searching(Group.Voice, "Searching a place"),

        /** %1$s is the place. */
        StartingNav(Group.Voice, "Starting navigation"),
        CommandDone(Group.Voice, "Command done"),
        DidntCatch(Group.Voice, "Didn't hear"),
        NotUnderstood(Group.Voice, "Didn't understand"),
        Cancelled(Group.Voice, "Cancelled"),

        // Turn-by-turn. A far prompt is NavSoon + the manoeuvre ("400 मीटर आगे, उल्टे हाथ ले ले"),
        // a near one NavNow + the manoeuvre ("इब, उल्टे हाथ ले ले").

        /** Route ready: %1$s is kilometres, %2$d minutes. */
        RouteFound(Group.Navigation, "Route found"),

        /** Off the route, fetching a new one. */
        Rerouting(Group.Navigation, "Rerouting"),

        /** Prefix for a turn ahead: %1$s is the distance ("400 मीटर"). */
        NavSoon(Group.Navigation, "Turn ahead (prefix)"),

        /** Prefix for a turn right now. */
        NavNow(Group.Navigation, "Turn now (prefix)"),
        NavLeft(Group.Navigation, "Left"),
        NavRight(Group.Navigation, "Right"),
        NavSlightLeft(Group.Navigation, "Slight left"),
        NavSlightRight(Group.Navigation, "Slight right"),
        NavSharpLeft(Group.Navigation, "Sharp left"),
        NavSharpRight(Group.Navigation, "Sharp right"),
        NavUTurn(Group.Navigation, "U-turn"),
        NavKeepLeft(Group.Navigation, "Keep left"),
        NavKeepRight(Group.Navigation, "Keep right"),
        NavFlyover(Group.Navigation, "Flyover"),
        NavRamp(Group.Navigation, "Ramp"),
        NavExit(Group.Navigation, "Exit"),
        NavRoundabout(Group.Navigation, "Roundabout"),

        /** Roundabout with a known exit: %1$d is the exit number. */
        NavRoundaboutExit(Group.Navigation, "Roundabout exit number"),
        NavContinue(Group.Navigation, "Continue straight"),
        NavArrive(Group.Navigation, "Arrived"),

        /** Faster than the known limit while navigating: %1$d is the limit. */
        SpeedLimit(Group.Speed, "Over the limit"),

        /** Still over the limit after the first warning. */
        SpeedEaseOff(Group.Speed, "Still over the limit"),

        /** Back under the limit after a warning. */
        SpeedBackUnder(Group.Speed, "Back under the limit"),

        /** Speed camera ahead: %1$d metres, %2$d limit. */
        CameraAhead(Group.Speed, "Camera ahead"),

        /** Speed camera ahead without a known limit: %1$d metres. */
        CameraAheadNoLimit(Group.Speed, "Camera ahead (no limit)"),

        /** Over the limit near a camera. */
        OverSpeed(Group.Speed, "Over the limit at a camera"),

        /** FASTag toll SMS: %1$s rupees deducted, %2$s rupees balance. */
        TollPaid(Group.Road, "Toll deducted"),

        /** FASTag toll SMS without a balance: %1$s rupees deducted. */
        TollPaidNoBalance(Group.Road, "Toll deducted (no balance)"),

        /** Driving too long: %1$s is the hours. */
        BreakReminder(Group.Road, "Break reminder"),

        /** "Save spot here" on the parked-car screen. */
        ParkingSaved(Group.Road, "Parking spot saved"),

        /** Once per trip when the car first moves (no belt sensor on head units). */
        SeatBelt(Group.Road, "Seat belt reminder"),

        /** Said before the answer to "where is my car". */
        WhereParked(Group.Road, "Where's my car"),
        WeatherRain(Group.Road, "Heavy rain ahead"),
        WeatherThunder(Group.Road, "Thunderstorm ahead"),
        WeatherFog(Group.Road, "Fog ahead"),
        WeatherHeat(Group.Road, "Heat"),

        ObdEngineHot(Group.Car, "Engine too hot"),
        ObdBatteryLow(Group.Car, "Battery low"),
        ObdNotCharging(Group.Car, "Battery not charging"),

        MusicPlay(Group.Music, "Play music"),

        /** %1$s is the song or search. */
        MusicPlayNamed(Group.Music, "Play a named song"),
        MusicNext(Group.Music, "Next song"),
        MusicPrevious(Group.Music, "Previous song"),
        MusicPause(Group.Music, "Pause music"),

        TalkHowAreYou(Group.Talk, "\"के हाल सै?\""),
        TalkWhoAreYou(Group.Talk, "\"कौन सै तू?\""),
        TalkJoke(Group.Talk, "\"चुटकला सुणा\""),

        // Recorded human voice only (Settings › Voice: Male / Female). A recorded clip can't say
        // the place, a song or a rupee amount, so the voice pack says these instead and
        // the screen shows the detail. VoiceClips.recordedEvent maps each event to its stand-in.

        /** Instead of Searching (no place name). */
        SearchingPlain(Group.HumanVoice, "Searching (human voice)"),

        /** Instead of StartingNav (no place name). */
        StartingNavPlain(Group.HumanVoice, "Starting navigation (human voice)"),

        /** Instead of RouteFound (kilometres and minutes are on screen). */
        RouteFoundPlain(Group.HumanVoice, "Route found (human voice)"),

        /** Said right after a recorded distance clip ("चार सौ मीटर" + "आगे,"), before the turn. */
        NavSoonTail(Group.HumanVoice, "After a distance (human voice)"),

        /** Instead of SpeedLimit (the limit is on the speed pane). */
        SpeedLimitPlain(Group.HumanVoice, "Over the limit (human voice)"),

        /** Said right after a recorded distance clip: "तीन सौ मीटर" + "पै कैमरा सै…". */
        CameraAheadTail(Group.HumanVoice, "Camera after a distance (human voice)"),

        /** Instead of TollPaid / TollPaidNoBalance (the amount shows on the toll card). */
        TollPaidPlain(Group.HumanVoice, "Toll deducted (human voice)"),

        /** Instead of BreakReminder (no hours). */
        BreakReminderPlain(Group.HumanVoice, "Break reminder (human voice)"),

        /** Safety prompts: never playful, never in this bank. */
        Sos(Group.Safety, "SOS", safety = true),
        Crash(Group.Safety, "Crash countdown", safety = true),
    }

    private val LINES: Map<Event, List<Line>> = mapOf(
        // ------------------------------------------------------------------ Welcome and goodbye
        Event.Welcome to listOf(
            Line("राम राम जी, आज कित जाणा सै?", "Ram Ram ji, aaj kit jaana sai?", "Ram Ram! Where are we going today?"),
            Line("राम राम भाई, आज कित का प्लान सै?", "Ram Ram bhai, aaj kit ka plan sai?", "Ram Ram brother, what's the plan today?"),
            Line("और तैयारे? सुणा दे, के हाल सै?", "Aur taiyaare? Suna de, ke haal sai?", "So, all set? Tell me, how are things?"),
            Line("राम राम ताई आले, आज कित की तैयारी सै?", "Ram Ram Tai Aale, aaj kit ki taiyaari hai?", "Ram Ram, dear friend! What are we ready for today?"),
            Line("राम राम भाई! गाड्डी त्यार सै, बोल कित चालां?", "Ram Ram bhai! Gaadi tyaar sai, bol kit chaalaan?", "Ram Ram! The car is ready, say where we go."),
            Line("भाई कित जाणा सै आज?", "Bhai kit jaana sai aaj?", "Brother, where are we going today?"),
            Line("आज कित की तैयारी सै?", "Aaj kit ki taiyaari sai?", "What are we ready for today?"),
            Line("आज कित का प्लान बणैगा?", "Aaj kit ka plan banega?", "What's the plan going to be today?"),
        ),
        Event.WelcomeMorning to listOf(
            Line("सवेरे सवेरे कित चाल पड़े ताऊ?", "Savere savere kit chaal pade tau?", "Off somewhere so early in the morning, Tau?"),
            Line("सवेरे की राम राम! चाय पी ली के?", "Savere ki Ram Ram! Chai pi li ke?", "Morning Ram Ram! Had your tea?"),
            Line("सवेरे सवेरे सड़क खाली सै, फेर भी धीरे चलाइए।", "Savere savere sadak khaali sai, pher bhi dheere chalaiye.", "The road is empty this early, still drive slowly."),
            Line("दिन की शुरुआत बढ़िया हो, चाल पड़ां।", "Din ki shuruaat badhiya ho, chaal padaan.", "May the day start well, let's go."),
        ),
        Event.WelcomeAfternoon to listOf(
            Line("दोपहर हो री सै, धूप तै बच कै चलाइए।", "Dopahar ho ri sai, dhoop te bach ke chalaiye.", "It's afternoon, mind the sun."),
            Line("रोटी खा ली के भाई? फेर चालां।", "Roti kha li ke bhai? Pher chaalaan.", "Had your lunch, brother? Then let's go."),
            Line("दोपहर मैं नींद आवै तो बता दिए, रुक ल्यांगे।", "Dopahar main neend aave to bata diye, ruk lyaange.", "If you feel sleepy this afternoon, tell me and we'll stop."),
            Line("घाम घणा सै, AC चला ले अर पाणी पी ले।", "Ghaam ghana sai, AC chala le ar paani pi le.", "It's very hot, turn on the AC and drink some water."),
        ),
        Event.WelcomeEvening to listOf(
            Line("साँझ हो ली, बत्ती जला ले भाई।", "Saanjh ho li, batti jala le bhai.", "Evening is here, switch on the lights."),
            Line("साँझ का टैम सै, सड़क पै भीड़ होगी, आराम तै चलाइए।", "Saanjh ka time sai, sadak pe bheed hogi, aaram te chalaiye.", "It's evening rush time, drive easy."),
            Line("काम निपट ग्या के? चाल घरां चालां।", "Kaam nipat gaya ke? Chaal ghara chaalaan.", "Work done? Let's head home."),
            Line("साँझ की राम राम जी!", "Saanjh ki Ram Ram ji!", "Good evening, Ram Ram!"),
        ),
        Event.WelcomeNight to listOf(
            Line("रात हो री सै, आराम तै चलाइए।", "Raat ho ri sai, aaram te chalaiye.", "It's night, drive easy."),
            Line("रात नै हेडलाइट ठीक राखिए, अर नींद आवै तो रुक जाइए।", "Raat nai headlight theek raakhiye, ar neend aave to ruk jaaiye.", "Keep the headlights right at night, and stop if you get sleepy."),
            Line("इतणी रात नै कित चाल पड़े भाई? ध्यान तै।", "Itni raat nai kit chaal pade bhai? Dhyaan te.", "Where to this late, brother? Carefully."),
            Line("रात का सफर सै, मैं जाग्गूं सूं, तू भी जाग्या रहिए।", "Raat ka safar sai, main jaaggun sun, tu bhi jaagya rahiye.", "It's a night trip: I'm awake, you stay awake too."),
        ),
        Event.Farewell to listOf(
            Line("चाल फेर मिलांगे।", "Chaal pher milaange.", "Alright, see you again."),
            Line("गाड्डी ढंग तै लॉक कर दिए।", "Gaadi dhang te lock kar diye.", "Lock the car properly."),
            Line("राम राम भाई, आराम कर ले, फेर मिलांगे।", "Ram Ram bhai, aaram kar le, pher milaange.", "Ram Ram brother, get some rest, see you again."),
            Line("पहुँच गे, शीशे चढ़ा ले अर गाड्डी लॉक कर दिए।", "Pahunch ge, sheeshe chadha le ar gaadi lock kar diye.", "We're here: roll up the windows and lock the car."),
            Line("आज का सफर बढ़िया रया, राम राम!", "Aaj ka safar badhiya raya, Ram Ram!", "Today's trip went well, Ram Ram!"),
        ),

        // ------------------------------------------------------------------ Hey Ampli
        Event.Greeting to listOf(
            Line("राम राम भाई! कित चालणा सै आज?", "Ram Ram bhai! Kit chalna sai aaj?", "Ram Ram brother! Where are we going today?"),
            Line("सुणा ताई आळे, गाड्डी त्यार सै!", "Suna tai aale, gaadi tyar sai!", "Tell me, friend, the car is ready!"),
            Line("राम राम! बेल्ट ला ले, फेर चालांगे।", "Ram Ram! Belt laga le, phir chalange.", "Ram Ram! Put on your belt, then we go."),
            Line("आ ग्या भाई? चाल, आज कित की सैर करणी सै?", "Aa gaya bhai? Chal, aaj kit ki sair karni sai?", "You're here? Come on, where shall we ride today?"),
            Line("राम राम जी! गाड्डी भी रेडी, मैं भी रेडी।", "Ram Ram ji! Gaadi bhi ready, main bhi ready.", "Ram Ram! The car is ready, and so am I."),
            Line("भाई कैसा सै, सब राजी खुशी? परिवार में सब ठीक?", "Bhai kaisa sai, sab raazi khushi? Parivaar mein sab theek?", "How are you, brother, all well? Everyone fine at home?"),
        ),
        Event.WakeReply to listOf(
            Line("हाँ भाई, बोल।", "Haan bhai, bol.", "Yes brother, go ahead."),
            Line("हाँ जी, सुण रया सूं।", "Haan ji, sun reya sun.", "Yes, I'm listening."),
            Line("बोल ताऊ, के सेवा करूं?", "Bol tau, ke seva karun?", "Tell me, Tau, how can I help?"),
            Line("हाँ, बता के करणा सै?", "Haan, bata ke karna sai?", "Yes, tell me what to do."),
        ),
        Event.AskDestination to listOf(
            Line("कित चालणा सै?", "Kit chalna sai?", "Where do we go?"),
            Line("बता भाई, कित ले चालूं?", "Bata bhai, kit le chalun?", "Tell me, brother, where shall I take you?"),
            Line("हां जी, कड़े जाणा सै?", "Haan ji, kade jana sai?", "Yes, where do you want to go?"),
            Line("बोल, कित का रास्ता दिखाऊं?", "Bol, kit ka rasta dikhaun?", "Say it, which way shall I show?"),
            Line("बोल भाई, आज कित का प्लान सै?", "Bol bhai, aaj kit ka plan sai?", "Tell me, brother, what's today's plan?"),
        ),
        Event.Searching to listOf(
            Line("%1\$s ढूंढ रया सूं, रुक जा एक मिनट।", "%1\$s dhoond reya sun, ruk ja ek minute.", "Looking for {place}, wait a minute."),
            Line("ठहर जा भाई, %1\$s देख रया सूं।", "Thahar ja bhai, %1\$s dekh reya sun.", "Hold on, brother, I'm looking up {place}."),
            Line("%1\$s? इबे तलाश करूं।", "%1\$s? Ibe talash karun.", "{place}? Searching right away."),
            Line("चाल, %1\$s का पता करूं सूं।", "Chaal, %1\$s ka pata karun sun.", "OK, finding out where {place} is."),
        ),
        Event.StartingNav to listOf(
            Line("चाल भाई, %1\$s चालांगे। रास्ता मैं बताऊंगा।", "Chal bhai, %1\$s chalange. Rasta main bataunga.", "Come on, we're going to {place}. I'll show the way."),
            Line("%1\$s का रास्ता लाग ग्या, आराम तै चालियो।", "%1\$s ka rasta laag gaya, aaram te chaliyo.", "Route to {place} is set, drive easy."),
            Line("ठीक सै, %1\$s। ध्यान तै चलाणा भाई।", "Theek sai, %1\$s. Dhyan te chalana bhai.", "OK, {place}. Drive carefully, brother."),
            Line("चाल पड़े %1\$s कान्ही, बेल्ट ला ले।", "Chaal pade %1\$s kaanhi, belt la le.", "Off towards {place}, buckle up."),
            Line("%1\$s चालां सां, रास्ता मेरे पै छोड़ दे।", "%1\$s chaalaan saan, rasta mere pe chhod de.", "We're off to {place}, leave the route to me."),
        ),
        Event.CommandDone to listOf(
            Line("ठीक सै, कर दिया।", "Theek sai, kar diya.", "OK, done."),
            Line("हो ग्या भाई।", "Ho gaya bhai.", "Done, brother."),
            Line("लै, कर दिया काम।", "Lai, kar diya kaam.", "There, job done."),
            Line("बस, हो ग्या।", "Bas, ho gaya.", "That's it, done."),
        ),
        Event.DidntCatch to listOf(
            Line("के कह्या? फेर तै बोल।", "Ke kahya? Pher te bol.", "What did you say? Say it again."),
            Line("समझ ना आया भाई, एक बार फेर बता।", "Samajh na aaya bhai, ek baar pher bata.", "Didn't get it, brother, tell me once more."),
            Line("थोड़ा जोर तै बोल, सुण ना पाया।", "Thoda zor te bol, sun na paaya.", "Speak a bit louder, I couldn't hear."),
            Line("समझ न्ही आया, फेर बोल।", "Samajh nahi aaya, pher bol.", "Didn't understand, say it again."),
        ),
        Event.NotUnderstood to listOf(
            Line("या बात मेरे पल्ले ना पड़ी।", "Ya baat mere palle na padi.", "That went over my head."),
            Line("भाई, यो काम मेरे बस का ना सै इबे।", "Bhai, yo kaam mere bas ka na sai ibe.", "Brother, I can't do that one yet."),
            Line("के कह रया सै? समझ्या ना।", "Ke kah reya sai? Samjhya na.", "What are you saying? I didn't get it."),
            Line("भाई, न्यू बोल: रोहतक चाल, या गाणा बजा।", "Bhai, nyu bol: Rohtak chaal, ya gaana baja.", "Brother, say it like: go to Rohtak, or play a song."),
        ),
        Event.Cancelled to listOf(
            Line("ठीक सै भाई, कोए बात ना।", "Theek sai bhai, koi baat na.", "OK brother, no problem."),
            Line("चाल छोड़, जद चाहिए तद बुला लियो।", "Chal chhod, jad chahiye tad bula liyo.", "Never mind, call me when you need me."),
            Line("राम राम, मैं उरे ए सूं।", "Ram Ram, main ure e sun.", "Ram Ram, I'm right here."),
            Line("ठीक सै, जद मर्जी हो, हे एम्पली बोल दिए।", "Theek sai, jad marzi ho, Hey Ampli bol diye.", "OK, whenever you like, just say Hey Ampli."),
        ),

        // ------------------------------------------------------------------ Navigation
        Event.RouteFound to listOf(
            Line("चाल, रास्ता मिल गया, %1\$s किलोमीटर सै।", "Chaal, rasta mil gaya, %1\$s kilometre sai.", "Come on, got the route, it's {km} km."),
            Line("रास्ता मिल ग्या भाई, %1\$s किलोमीटर अर %2\$d मिनट का सफर सै।", "Rasta mil gaya bhai, %1\$s kilometre ar %2\$d minute ka safar sai.", "Got the route, brother: {km} km, a {min}-minute trip."),
            Line("लै, %1\$s किलोमीटर का रास्ता त्यार सै, चाल पड़।", "Lai, %1\$s kilometre ka rasta tyaar sai, chaal pad.", "Here, a {km} km route is ready, let's go."),
            Line("बढ़िया, रास्ता लाग ग्या। %1\$s किलोमीटर, कोए %2\$d मिनट।", "Badhiya, rasta laag gaya. %1\$s kilometre, koi %2\$d minute.", "Great, the route is set: {km} km, about {min} minutes."),
        ),
        Event.Rerouting to listOf(
            Line("रास्ता भटक गया भाई, नया रास्ता देख रया सूं।", "Rasta bhatak gaya bhai, naya rasta dekh reya sun.", "We went off the route, brother, finding a new one."),
            Line("कोए बात ना, दूसरा रास्ता काढूं सूं।", "Koi baat na, doosra rasta kaadhun sun.", "No problem, I'm finding another route."),
            Line("ओ हो, मोड़ छूट ग्या! रुक, नया रास्ता बणाऊं सूं।", "O ho, mod chhoot gaya! Ruk, naya rasta banaaun sun.", "Oops, missed the turn! Wait, making a new route."),
            Line("घबरा मत, घूम कै भी पहुँचांगे, नया रास्ता देख रया सूं।", "Ghabra mat, ghoom ke bhi pahunchaange, naya rasta dekh reya sun.", "Don't worry, we'll get there anyway, finding a new route."),
        ),
        Event.NavSoon to listOf(
            Line("आगे %1\$s पे", "Aage %1\$s pe", "In {distance}"),
            Line("%1\$s आगे,", "%1\$s aage,", "{distance} ahead,"),
            Line("सुण भाई, %1\$s आगे,", "Sun bhai, %1\$s aage,", "Listen brother, {distance} ahead,"),
            Line("%1\$s बाद,", "%1\$s baad,", "After {distance},"),
        ),
        Event.NavNow to listOf(
            Line("इब", "Ib", "Now"),
            Line("अभी,", "Abhi,", "Right now,"),
            Line("बस इब्बे,", "Bas ibbe,", "Right this moment,"),
            Line("चाल इब,", "Chaal ib,", "Now then,"),
        ),
        Event.NavLeft to listOf(
            Line("उल्टे हाथ ले ले।", "ulte haath le le.", "take the left."),
            Line("बाएं हाथ नै मुड़ ले भाई।", "baayen haath nai mud le bhai.", "turn left, brother."),
            Line("उल्टे हाथ नै काट ले।", "ulte haath nai kaat le.", "cut to the left."),
            Line("बाएं मुड़ ज्या, आराम तै।", "baayen mud jya, aaram te.", "turn left, easy."),
            Line("लेफ्ट ले ले भाई।", "left le le bhai.", "take a left, brother."),
            Line("उल्टे हाथ कान्ही मुड़ ले भाई।", "ulte haath kaanhi mud le bhai.", "turn towards the left, brother."),
            Line("भाई उल्टे हाथ न ले ले।", "bhai ulte haath na le le.", "brother, take the left."),
        ),
        Event.NavRight to listOf(
            Line("सुल्टे हाथ ले ले।", "sulte haath le le.", "take the right."),
            Line("सीधे हाथ मुड़ ले।", "seedhe haath mud le.", "turn right."),
            Line("दाएं हाथ नै मुड़ ले भाई।", "daayen haath nai mud le bhai.", "turn right, brother."),
            Line("दाएं मुड़ ज्या, आराम तै।", "daayen mud jya, aaram te.", "turn right, easy."),
            Line("राइट ले ले भाई।", "right le le bhai.", "take a right, brother."),
            Line("सुल्टे हाथ कान्ही मुड़ ले भाई।", "sulte haath kaanhi mud le bhai.", "turn towards the right, brother."),
            Line("सुल्टे हाथ न ले ले।", "sulte haath na le le.", "take the right."),
        ),
        Event.NavSlightLeft to listOf(
            Line("हल्का सा बाएं नै हो ले।", "halka sa baayen nai ho le.", "bear slightly left."),
            Line("थोड़ा सा उल्टे हाथ नै दबा ले।", "thoda sa ulte haath nai daba le.", "ease a little to the left."),
            Line("बाएं नै हल्का सरक ले।", "baayen nai halka sarak le.", "slide slightly left."),
            Line("उल्टे हाथ नै हल्का सा मोड़ ले।", "ulte haath nai halka sa mod le.", "turn slightly left."),
        ),
        Event.NavSlightRight to listOf(
            Line("हल्का सा दाएं नै हो ले।", "halka sa daayen nai ho le.", "bear slightly right."),
            Line("थोड़ा सा सुल्टे हाथ नै दबा ले।", "thoda sa sulte haath nai daba le.", "ease a little to the right."),
            Line("दाएं नै हल्का सरक ले।", "daayen nai halka sarak le.", "slide slightly right."),
            Line("सुल्टे हाथ नै हल्का सा मोड़ ले।", "sulte haath nai halka sa mod le.", "turn slightly right."),
        ),
        Event.NavSharpLeft to listOf(
            Line("पूरा बाएं नै घुमा ले, तीखा मोड़ सै।", "poora baayen nai ghuma le, teekha mod sai.", "turn hard left, it's a sharp bend."),
            Line("तीखा मोड़ सै, बाएं नै पूरा काट ले।", "teekha mod sai, baayen nai poora kaat le.", "sharp bend, cut fully left."),
            Line("संभल कै, तेज बाएं मोड़ सै।", "sambhal ke, tez baayen mod sai.", "careful, sharp left turn."),
            Line("उल्टे हाथ पूरा घुमा ले, मोड़ तीखा सै।", "ulte haath poora ghuma le, mod teekha sai.", "steer fully left, the turn is sharp."),
        ),
        Event.NavSharpRight to listOf(
            Line("पूरा दाएं नै घुमा ले, तीखा मोड़ सै।", "poora daayen nai ghuma le, teekha mod sai.", "turn hard right, it's a sharp bend."),
            Line("तीखा मोड़ सै, दाएं नै पूरा काट ले।", "teekha mod sai, daayen nai poora kaat le.", "sharp bend, cut fully right."),
            Line("संभल कै, तेज दाएं मोड़ सै।", "sambhal ke, tez daayen mod sai.", "careful, sharp right turn."),
            Line("सुल्टे हाथ पूरा घुमा ले, मोड़ तीखा सै।", "sulte haath poora ghuma le, mod teekha sai.", "steer fully right, the turn is sharp."),
        ),
        Event.NavUTurn to listOf(
            Line("गाड्डी घुमा ले, यू-टर्न ले ले।", "gaddi ghuma le, U-turn le le.", "turn the car around, take a U-turn."),
            Line("यू-टर्न मार ले भाई, उल्टा घूम ले।", "U-turn maar le bhai, ulta ghoom le.", "do a U-turn, brother, turn back."),
            Line("गाड्डी पाच्छै नै घुमा ले, यू-टर्न सै।", "gaadi paachhe nai ghuma le, U-turn sai.", "turn the car back, it's a U-turn."),
            Line("यू-टर्न ले ले, धीरे तै।", "U-turn le le, dheere te.", "take the U-turn, slowly."),
            Line("उल्टा घुमा ले भाई, रास्ता पाच्छै सै।", "ulta ghuma le bhai, rasta paachhe sai.", "turn around, brother, the way is behind us."),
        ),
        Event.NavKeepLeft to listOf(
            Line("बाएं आळी लेन मैं रहियो।", "baayen aali lane mein rahiyo.", "stay in the left lane."),
            Line("बाएं नै चिपक कै चालिए।", "baayen nai chipak ke chaliye.", "keep close to the left."),
            Line("बाएं पास्सै रह भाई।", "baayen paase reh bhai.", "keep to the left side, brother."),
            Line("उल्टे हाथ आळी लेन पकड़ ले।", "ulte haath aali lane pakad le.", "take the left lane."),
        ),
        Event.NavKeepRight to listOf(
            Line("दाएं आळी लेन मैं रहियो।", "daayen aali lane mein rahiyo.", "stay in the right lane."),
            Line("दाएं नै चिपक कै चालिए।", "daayen nai chipak ke chaliye.", "keep close to the right."),
            Line("दाएं पास्सै रह भाई।", "daayen paase reh bhai.", "keep to the right side, brother."),
            Line("सुल्टे हाथ आळी लेन पकड़ ले।", "sulte haath aali lane pakad le.", "take the right lane."),
        ),
        Event.NavFlyover to listOf(
            Line("फ्लाईओवर पे ले ले।", "flyover pe le le.", "take the flyover."),
            Line("फ्लाईओवर पे चढ़ ले भाई।", "flyover pe chadh le bhai.", "go up the flyover, brother."),
            Line("ऊपर आळा पुल पकड़ ले।", "upar aala pul pakad le.", "take the bridge up top."),
            Line("फ्लाईओवर ले ले, नीचै मत उतरियो।", "flyover le le, neeche mat utariyo.", "take the flyover, don't go under."),
        ),
        Event.NavRamp to listOf(
            Line("रैंप पे ले ले।", "ramp pe le le.", "take the ramp."),
            Line("रैंप पकड़ ले भाई।", "ramp pakad le bhai.", "take the ramp, brother."),
            Line("रैंप पै चढ़ ले, आराम तै।", "ramp pe chadh le, aaram te.", "go up the ramp, easy."),
            Line("साइड आळा रैंप ले ले।", "side aala ramp le le.", "take the side ramp."),
        ),
        Event.NavExit to listOf(
            Line("एग्जिट ले ले भाई।", "exit le le bhai.", "take the exit, brother."),
            Line("बाहर लिकड़ ले, एग्जिट आ ग्या।", "bahar likad le, exit aa gaya.", "get off, the exit is here."),
            Line("हाईवे तै उतर ले, एग्जिट सै।", "highway te utar le, exit sai.", "leave the highway, it's the exit."),
            Line("आगे आळे एग्जिट तै लिकड़ ले भाई।", "aage aale exit te likad le bhai.", "take the exit ahead, brother."),
        ),
        Event.NavRoundabout to listOf(
            Line("गोल चक्कर आ रया सै, संभल कै।", "gol chakkar aa reya sai, sambhal ke.", "roundabout coming, careful."),
            Line("चौक पै घूम ले भाई, गोल चक्कर सै।", "chowk pe ghoom le bhai, gol chakkar sai.", "go round at the circle, brother."),
            Line("गोल चक्कर सै, धीरे कर ले।", "gol chakkar sai, dheere kar le.", "roundabout, slow down."),
            Line("गोल चक्कर पै धीरे हो कै घूम ले।", "gol chakkar pe dheere ho ke ghoom le.", "slow down and go round the roundabout."),
        ),
        Event.NavRoundaboutExit to listOf(
            Line("गोल चक्कर पै %1\$d नंबर आळा रास्ता पकड़ ले।", "gol chakkar pe %1\$d number aala rasta pakad le.", "at the roundabout take exit {n}."),
            Line("चक्कर मैं घूम कै %1\$d नंबर तै लिकड़ ले।", "chakkar mein ghoom ke %1\$d number te likad le.", "go round and leave by exit {n}."),
            Line("गोल चक्कर सै, %1\$d नंबर का रास्ता लेणा सै।", "gol chakkar sai, %1\$d number ka rasta lena sai.", "roundabout: we take exit {n}."),
            Line("चक्कर पै घूम ले, %1\$d नंबर आळे रास्ते तै बाहर लिकड़ ले।", "chakkar pe ghoom le, %1\$d number aale raste te bahar likad le.", "go round and come out on exit {n}."),
        ),
        Event.NavContinue to listOf(
            Line("सीधा चाल।", "Sidha chal.", "Go straight."),
            Line("सीधा चालता रह भाई।", "Seedha chalta reh bhai.", "Keep going straight, brother."),
            Line("सीधे सीधे चाल, कित मुड़णा कोनी।", "Seedhe seedhe chal, kit mudna koni.", "Straight on, no turns for now."),
            Line("सीधा रास्ता सै, मौज तै चला।", "Seedha rasta sai, mauj te chala.", "Straight road, enjoy the drive."),
            Line("भाई सीधा चाल।", "Bhai seedha chal.", "Go straight, brother."),
        ),
        Event.NavArrive to listOf(
            Line("पहुँच गए ताऊ!", "Pahunch gaye tau!", "We've arrived, Tau!"),
            Line("पहुंच गे भाई, मंजिल आ गी!", "Pahunch ge bhai, manzil aa gi!", "We're here, brother, destination reached!"),
            Line("ल्यो जी, आ ग्या ठिकाणा!", "Lyo ji, aa gaya thikana!", "There you go, we've reached the place!"),
            Line("बस भाई, याड़े ए सै मंजिल।", "Bas bhai, yaade e sai manzil.", "That's it, brother, the destination is right here."),
            Line("आ गे भाई, गाड्डी साइड ला ले आराम तै।", "Aa ge bhai, gaadi side la le aaram te.", "We're here, brother, pull over gently."),
        ),

        // ------------------------------------------------------------------ Speed and cameras
        Event.SpeedLimit to listOf(
            Line("मंदी कर ले भाई, इस रोड पे स्पीड कम सै।", "Mandi kar le bhai, is road pe speed kam sai.", "Slow down, brother, this road has a lower limit."),
            Line("ताऊ, गाड्डी मंदी कर ले।", "Tau, gaddi mandi kar le.", "Tau, slow the car down."),
            Line("स्पीड घटा ले भाई, लिमिट %1\$d सै।", "Speed ghata le bhai, limit %1\$d sai.", "Reduce speed, brother, the limit is {limit}."),
            Line("धीरे कर ले, आड़े %1\$d की लिमिट सै।", "Dheere kar le, aade %1\$d ki limit sai.", "Slow down, the limit here is {limit}."),
            Line("%1\$d तै ऊपर ना, चालान कट ज्यागा।", "%1\$d te upar na, chalaan kat jyaga.", "Not above {limit}, or you'll get a fine."),
        ),
        Event.SpeedEaseOff to listOf(
            Line("छोड़ दे भाई, रेस छोड़ दे।", "Chhod de bhai, race chhod de.", "Let it go, brother, this isn't a race."),
            Line("रेस ना सै भाई, आराम तै चला।", "Race na sai bhai, aaram te chala.", "It's not a race, brother, drive easy."),
            Line("पैर हटा ले एक्सीलेटर तै, भाई।", "Pair hata le accelerator te, bhai.", "Ease off the accelerator, brother."),
            Line("इतनी जल्दी के सै भाई, घरां सब बाट देखैं सैं।", "Itni jaldi ke sai bhai, ghara sab baat dekhe sai.", "Why the hurry, brother? Everyone at home is waiting for you."),
            Line("भाई मंदा कर ले।", "Bhai manda kar le.", "Brother, slow down."),
            Line("भाई आराम तै चाल।", "Bhai aaram te chal.", "Brother, go easy."),
        ),
        Event.SpeedBackUnder to listOf(
            Line("शाबाश, न्यू ए चला।", "Shaabaash, nyu e chala.", "Well done, drive just like this."),
            Line("ये हुई ना बात! इब ठीक सै स्पीड।", "Ye hui na baat! Ib theek sai speed.", "That's it! The speed is fine now."),
            Line("बढ़िया भाई, आराम तै चलाण मैं ए मजा सै।", "Badhiya bhai, aaram te chalaan main e maza sai.", "Nice, brother, the fun is in driving easy."),
            Line("शाबाश! घरां आळे भी राजी होंगे।", "Shaabaash! Ghara aale bhi raazi honge.", "Well done! The family will be happy too."),
        ),
        Event.CameraAhead to listOf(
            Line("ध्यान तै भाई, आगे कैमरा सै, स्पीड कम कर ले! %1\$d मीटर पै, लिमिट %2\$d।", "Dhyan te bhai, aage camera sai, speed kam kar le! %1\$d metre pe, limit %2\$d.", "Careful, brother, camera ahead, slow down! In {m} metres, limit {limit}."),
            Line("%1\$d मीटर पै कैमरा आ रया सै, %2\$d तै ऊपर मत जाइयो।", "%1\$d metre pe camera aa reya sai, %2\$d te upar mat jaiyo.", "Camera in {m} metres, don't go above {limit}."),
            Line("संभल कै भाई! %1\$d मीटर पै कैमरा, लिमिट %2\$d सै।", "Sambhal ke bhai! %1\$d metre pe camera, limit %2\$d sai.", "Careful, brother! Camera in {m} metres, the limit is {limit}."),
            Line("आगे कैमरा खड़्या सै %1\$d मीटर पै, %2\$d तै ऊपर गया तो चालान कट ज्यागा।", "Aage camera khadya sai %1\$d metre pe, %2\$d te upar gaya to chalaan kat jyaga.", "A camera is waiting {m} metres ahead; above {limit} you'll get a fine."),
        ),
        Event.CameraAheadNoLimit to listOf(
            Line("धीरे कर ले, आगे कैमरा सै! %1\$d मीटर।", "Dheere kar le, aage camera sai! %1\$d metre.", "Slow down, camera ahead! {m} metres."),
            Line("ध्यान तै भाई, आगे कैमरा सै! %1\$d मीटर।", "Dhyan te bhai, aage camera sai! %1\$d metre.", "Careful, brother, camera ahead! {m} metres."),
            Line("%1\$d मीटर पै कैमरा सै, आराम तै।", "%1\$d metre pe camera sai, aaram te.", "Camera in {m} metres, take it easy."),
            Line("संभल कै, %1\$d मीटर पै कैमरा।", "Sambhal ke, %1\$d metre pe camera.", "Careful, camera in {m} metres."),
        ),
        Event.OverSpeed to listOf(
            Line("मंदी कर ले भाई, इस रोड पे स्पीड कम सै।", "Mandi kar le bhai, is road pe speed kam sai.", "Slow down, brother, this road has a lower limit."),
            Line("धीरे कर ले, आगे कैमरा सै।", "Dheere kar le, aage camera sai.", "Slow down, there's a camera ahead."),
            Line("ताऊ, गाड्डी मंदी कर ले।", "Tau, gaddi mandi kar le.", "Tau, slow the car down."),
            Line("अरे धीरे भाई, चालान कट ज्यागा!", "Arre dheere bhai, chalaan kat jyaga!", "Hey, slowly, brother, you'll get a fine!"),
            Line("घरां कोए बाट देख रया सै, धीरे चला।", "Ghara koi baat dekh reya sai, dheere chala.", "Someone at home is waiting for you, drive slowly."),
            Line("भाई आगे कैमरे लाग रे सैं, मंदा कर ले।", "Bhai aage camere laag re sain, manda kar le.", "Brother, cameras ahead, slow down."),
            Line("भाई आगे ब्रेक कर ले।", "Bhai aage brake kar le.", "Brother, brake ahead."),
        ),

        // ------------------------------------------------------------------ Toll, breaks, parking, weather
        Event.TollPaid to listOf(
            Line("टोल कट गया, %1\$s रुपये, बैलेंस %2\$s रुपये बच्या सै।", "Toll kat gaya, %1\$s rupaye, balance %2\$s rupaye bachya sai.", "Toll deducted, ₹{amount}; ₹{balance} balance left."),
            Line("%1\$s रुपये का टोल कट ग्या भाई, खाते मैं %2\$s बचे सैं।", "%1\$s rupaye ka toll kat gaya bhai, khaate main %2\$s bache sain.", "₹{amount} toll deducted, brother; {balance} left in the account."),
            Line("फास्टैग तै %1\$s रुपये गए, इब %2\$s रुपये बचे सैं।", "FASTag te %1\$s rupaye gaye, ib %2\$s rupaye bache sain.", "₹{amount} went from FASTag; ₹{balance} left now."),
            Line("टोल पार! %1\$s कटे, %2\$s बाकी सैं।", "Toll paar! %1\$s kate, %2\$s baaki sain.", "Past the toll! {amount} deducted, {balance} remaining."),
        ),
        Event.TollPaidNoBalance to listOf(
            Line("टोल कट गया, %1\$s रुपये।", "Toll kat gaya, %1\$s rupaye.", "Toll deducted, ₹{amount}."),
            Line("%1\$s रुपये का टोल कट ग्या भाई।", "%1\$s rupaye ka toll kat gaya bhai.", "₹{amount} toll deducted, brother."),
            Line("फास्टैग तै %1\$s रुपये गए, चाल आगे।", "FASTag te %1\$s rupaye gaye, chaal aage.", "₹{amount} went from FASTag, let's move on."),
            Line("टोल पार! %1\$s रुपये कटे।", "Toll paar! %1\$s rupaye kate.", "Past the toll! ₹{amount} deducted."),
        ),
        Event.BreakReminder to listOf(
            Line("%1\$s घंटे हो लिए, थक ग्या होगा भाई, एक चाय हो जाए?", "%1\$s ghante ho liye, thak gaya hoga bhai, ek chai ho jaye?", "{hours} hours already, you must be tired, brother; how about a tea?"),
            Line("%1\$s घंटे तै चला रया सै, थोड़ा रुक कै कमर सीधी कर ले।", "%1\$s ghante te chala reya sai, thoda ruk ke kamar seedhi kar le.", "Driving for {hours} hours; stop a bit and stretch your back."),
            Line("भाई %1\$s घंटे हो गए, ढाबा आवै तै रुक लियो।", "Bhai %1\$s ghante ho gaye, dhaba aave te ruk liyo.", "Brother, {hours} hours now; stop at the next dhaba."),
            Line("%1\$s घंटे हो लिए, चाय पी ले भाई।", "%1\$s ghante ho liye, chai pi le bhai.", "{hours} hours already, have some tea, brother."),
            Line("%1\$s घंटे तै स्टीयरिंग पकड़ राख्या सै, पाँच मिनट टांग सीधी कर ले।", "%1\$s ghante te steering pakad raakhya sai, paanch minute taang seedhi kar le.", "You've held the wheel for {hours} hours; stretch your legs for five minutes."),
        ),
        Event.ParkingSaved to listOf(
            Line("गाड्डी की जगह याद कर ली, बेफिकर रह।", "Gaadi ki jagah yaad kar li, befikar reh.", "I've saved where the car is, relax."),
            Line("ठीक सै, गाड्डी याड़े खड़ी सै, मन्नै बेरा सै।", "Theek sai, gaadi yaade khadi sai, manne bera sai.", "OK, the car is parked here, I know it."),
            Line("पार्किंग सेव कर दी, भूल ज्यावै तो मन्नै पूछ लिए।", "Parking save kar di, bhool jyaave to manne poochh liye.", "Parking saved; if you forget, ask me."),
            Line("जगह नोट कर ली भाई, गाड्डी ना गुमैगी।", "Jagah note kar li bhai, gaadi na gumegi.", "Spot noted, brother, the car won't get lost."),
        ),
        Event.SeatBelt to listOf(
            Line("भाई सीट बेल्ट पहन ले।", "Bhai seat belt pehan le.", "Brother, put on your seat belt."),
            Line("बेल्ट ला ले भाई, फेर चालांगे।", "Belt la le bhai, pher chaalaange.", "Buckle up, brother, then we go."),
            Line("सीट बेल्ट पहन ले, सफर बढ़िया रहवैगा।", "Seat belt pehan le, safar badhiya rahvega.", "Put on the seat belt, the trip will go well."),
            Line("पहलां बेल्ट, फेर गाड्डी। पहन ले भाई।", "Pehlaan belt, pher gaadi. Pehan le bhai.", "Belt first, then the car. Put it on, brother."),
        ),
        Event.WhereParked to listOf(
            Line("रुक, देखूं गाड्डी कित खड़ी सै।", "Ruk, dekhun gaadi kit khadi sai.", "Wait, let me see where the car is."),
            Line("थारी गाड्डी का पता मेरे धोरै सै।", "Thaari gaadi ka pata mere dhore sai.", "I have your car's location."),
            Line("चिंता मत कर, गाड्डी गुमी कोनी।", "Chinta mat kar, gaadi gumi koni.", "Don't worry, the car isn't lost."),
            Line("सुण, गाड्डी न्यू खड़ी सै।", "Sun, gaadi nyu khadi sai.", "Listen, here's where the car is."),
        ),
        Event.WeatherRain to listOf(
            Line("आगे बारिश सै भाई, धीरे चलाइए अर वाइपर चला ले।", "Aage baarish sai bhai, dheere chalaiye ar wiper chala le.", "Rain ahead, brother: drive slowly and turn on the wipers."),
            Line("मींह आण आळा सै, सड़क चिकणी होगी, संभल कै।", "Meenh aan aala sai, sadak chikni hogi, sambhal ke.", "Rain is coming, the road will be slippery, careful."),
            Line("बादळ बरसण लाग रे सैं, ब्रेक आराम तै मारिए।", "Baadal barsan laag re sain, brake aaram te maariye.", "The clouds are pouring, brake gently."),
            Line("आगे जोर की बारिश सै, दूरी बणा कै चलाइए।", "Aage jor ki baarish sai, doori bana ke chalaiye.", "Heavy rain ahead, keep your distance."),
        ),
        Event.WeatherThunder to listOf(
            Line("आगे आंधी-तूफान सै भाई, कोए सुरक्षित जगह देख कै रुक लिए।", "Aage aandhi-toofan sai bhai, koi surakshit jagah dekh ke ruk liye.", "Storm ahead, brother; find a safe place and stop."),
            Line("आगे बिजळी कड़क री सै, धीरे चलाइए अर पेड़ां तै दूर रहिए।", "Aage bijli kadak ri sai, dheere chalaiye ar pedaan te door rahiye.", "Lightning ahead: drive slowly and keep away from trees."),
            Line("तूफान आण आळा सै, जल्दबाजी मत करिए।", "Toofan aan aala sai, jaldbaazi mat kariye.", "A storm is coming, don't rush."),
            Line("आगे मौसम बिगड़ रया सै, आराम तै अर ध्यान तै।", "Aage mausam bigad reya sai, aaram te ar dhyaan te.", "The weather is turning bad ahead; easy and careful."),
        ),
        Event.WeatherFog to listOf(
            Line("आगे धुंध सै भाई, फॉग लाइट जला ले अर धीरे चला।", "Aage dhundh sai bhai, fog light jala le ar dheere chala.", "Fog ahead, brother: fog lights on and drive slowly."),
            Line("धुंध घणी सै, आगे आळी गाड्डी तै दूरी राखिए।", "Dhundh ghani sai, aage aali gaadi te doori raakhiye.", "Thick fog, keep a distance from the car ahead."),
            Line("आगे कुछ ना दीखै, मंदी कर ले अर पीली बत्ती जला ले।", "Aage kuchh na deekhe, mandi kar le ar peeli batti jala le.", "You can't see ahead: slow down and switch on the hazard lights."),
            Line("धुंध मैं ओवरटेक मत करिए भाई।", "Dhundh main overtake mat kariye bhai.", "Don't overtake in the fog, brother."),
        ),
        Event.WeatherHeat to listOf(
            Line("घणी गर्मी सै भाई, पाणी पी ले अर AC चला ले।", "Ghani garmi sai bhai, paani pi le ar AC chala le.", "It's very hot, brother: drink water and turn on the AC."),
            Line("बाहर लू चाल री सै, टायर अर इंजन का ध्यान राखिए।", "Bahar loo chaal ri sai, tyre ar engine ka dhyaan raakhiye.", "A heatwave outside; keep an eye on the tyres and engine."),
            Line("आज घाम घणा सै, गाड्डी छाँ मैं खड़ी करिए।", "Aaj ghaam ghana sai, gaadi chhaan main khadi kariye.", "The sun is strong today, park in the shade."),
            Line("गर्मी मैं सिर दुखै तो रुक कै पाणी पी लिए।", "Garmi main sir dukhe to ruk ke paani pi liye.", "If the heat gives you a headache, stop and drink water."),
        ),

        // ------------------------------------------------------------------ Car health (OBD)
        Event.ObdEngineHot to listOf(
            Line("भाई, इंजन घणा गरम हो रया सै, गाड्डी साइड लगा कै रोक ले।", "Bhai, engine ghana garam ho reya sai, gaadi side laga ke rok le.", "Brother, the engine is running very hot; pull over and stop."),
            Line("इंजन का तापमान ऊपर सै, AC बंद कर अर कित छाँ मैं रुक ले।", "Engine ka taapmaan upar sai, AC band kar ar kit chhaan main ruk le.", "Engine temperature is high; switch off the AC and stop in some shade."),
            Line("इंजन तप रया सै, कूलेंट चेक करवा लिए।", "Engine tap reya sai, coolant check karva liye.", "The engine is heating up; get the coolant checked."),
            Line("रुक जा भाई, इंजन नै ठंडा होण दे।", "Ruk ja bhai, engine nai thanda hon de.", "Stop, brother, let the engine cool down."),
        ),
        Event.ObdBatteryLow to listOf(
            Line("बैटरी कमजोर सै भाई, जल्दी चेक करवा ले।", "Battery kamzor sai bhai, jaldi check karva le.", "The battery is weak, brother, get it checked soon."),
            Line("बैटरी का वोल्टेज कम सै, गाड्डी बंद करी तो स्टार्ट होण मैं दिक्कत आ सकै सै।", "Battery ka voltage kam sai, gaadi band kari to start hon main dikkat aa sake sai.", "Battery voltage is low; once switched off, the car may not start easily."),
            Line("बैटरी थकी होई सै, मिस्त्री नै दिखा दिए।", "Battery thaki hoi sai, mistri nai dikha diye.", "The battery is worn out, show it to a mechanic."),
            Line("बैटरी डाउन होण लाग री सै, लाइट अर AC कम कर ले।", "Battery down hon laag ri sai, light ar AC kam kar le.", "The battery is running down, reduce lights and AC."),
        ),
        Event.ObdNotCharging to listOf(
            Line("बैटरी चार्ज ना हो री भाई, अल्टरनेटर चेक करवा ले।", "Battery charge na ho ri bhai, alternator check karva le.", "The battery isn't charging, brother; get the alternator checked."),
            Line("चार्जिंग ठीक कोनी, जल्दी मिस्त्री धोरै चालां।", "Charging theek koni, jaldi mistri dhore chaalaan.", "Charging isn't right; let's go to a mechanic soon."),
            Line("बैटरी मैं करंट ना जा रया, लंबा सफर मत करिए।", "Battery main current na ja reya, lamba safar mat kariye.", "No charge is going into the battery; avoid a long trip."),
            Line("चार्जिंग मैं गड़बड़ सै, गाड्डी बंद होण तै पहलां दिखा दिए।", "Charging main gadbad sai, gaadi band hon te pehlaan dikha diye.", "Something is wrong with charging; get it seen before the car dies."),
        ),

        // ------------------------------------------------------------------ Music
        Event.MusicPlay to listOf(
            Line("ले, गाणा बजा दिया।", "Le, gaana baja diya.", "There, the song is playing."),
            Line("चाल, गाणे चला दिए, मौज कर।", "Chaal, gaane chala diye, mauj kar.", "Songs are on, enjoy."),
            Line("ले भाई, म्यूजिक शुरू।", "Le bhai, music shuru.", "Here, brother, music on."),
            Line("बजा दिया, आवाज ठीक सै के?", "Baja diya, aawaaz theek sai ke?", "Playing; is the volume OK?"),
        ),
        Event.MusicPlayNamed to listOf(
            Line("ले, %1\$s बजा दिया।", "Le, %1\$s baja diya.", "There, playing {song}."),
            Line("चाल, %1\$s सुण।", "Chaal, %1\$s sun.", "Come on, listen to {song}."),
            Line("%1\$s लगा दिया भाई, मौज कर।", "%1\$s laga diya bhai, mauj kar.", "Put on {song}, brother, enjoy."),
            Line("%1\$s बाज रया सै, आवाज बढ़ा ले।", "%1\$s baaj reya sai, aawaaz badha le.", "{song} is playing, turn it up."),
        ),
        Event.MusicNext to listOf(
            Line("अगला गाणा लगा दिया।", "Agla gaana laga diya.", "Put on the next song."),
            Line("ले, दूसरा गाणा।", "Le, doosra gaana.", "Here, another song."),
            Line("यो ना जंच्या? चाल अगला।", "Yo na jachya? Chaal agla.", "Didn't like this one? Next."),
            Line("बदल दिया भाई।", "Badal diya bhai.", "Changed it, brother."),
        ),
        Event.MusicPrevious to listOf(
            Line("पाछला गाणा फेर लगा दिया।", "Paachhla gaana pher laga diya.", "Put the previous song back on."),
            Line("ले, पहलड़ा गाणा।", "Le, pehlada gaana.", "Here, the earlier song."),
            Line("वो ए गाणा फेर तै।", "Vo e gaana pher te.", "That same song again."),
            Line("पाच्छै कर दिया भाई।", "Paachhe kar diya bhai.", "Went back, brother."),
        ),
        Event.MusicPause to listOf(
            Line("गाणा रोक दिया।", "Gaana rok diya.", "Song paused."),
            Line("ले, म्यूजिक चुप कर दिया।", "Le, music chup kar diya.", "There, music is quiet."),
            Line("ठीक सै, थोड़ी शांति।", "Theek sai, thodi shaanti.", "OK, a little peace."),
            Line("रोक दिया भाई, जद मन करै फेर बजा दयूंगा।", "Rok diya bhai, jad man kare pher baja dyunga.", "Paused, brother; I'll play again whenever you like."),
        ),

        // ------------------------------------------------------------------ Small talk
        Event.TalkHowAreYou to listOf(
            Line("बढ़िया सूं, तू सुणा।", "Badhiya sun, tu suna.", "I'm great, you tell me."),
            Line("मौज मैं सूं भाई, तू बता के हाल सै?", "Mauj main sun bhai, tu bata ke haal sai?", "Having fun, brother; how are you?"),
            Line("एकदम चकाचक! थारे साथ सफर मैं मजा आवै सै।", "Ekdam chakachak! Thaare saath safar main maza aave sai.", "Absolutely great! Travelling with you is fun."),
            Line("ठाठ सैं भाई, बस तू धीरे चलाइए।", "Thaath sain bhai, bas tu dheere chalaiye.", "Living it up, brother; you just drive slowly."),
        ),
        Event.TalkWhoAreYou to listOf(
            Line("मैं एम्पली सूं, थारी हरियाणवी गाड्डी।", "Main Ampli sun, thaari Haryanvi gaadi.", "I'm Ampli, your Haryanvi car."),
            Line("मैं एम्पली सूं, दुनिया की पहली गाड्डी जो हरियाणवी बोल्लै सै!", "Main Ampli sun, duniya ki pehli gaadi jo Haryanvi bolle sai!", "I'm Ampli, the world's first car that talks in Haryanvi!"),
            Line("थारा साथी एम्पली, रास्ता भी बताऊं, गाणे भी बजाऊं।", "Thaara saathi Ampli, rasta bhi bataaun, gaane bhi bajaaun.", "Your buddy Ampli: I show the way and play the songs."),
            Line("मैं एम्पली सूं, देसी बोली आळी थारी गाड्डी।", "Main Ampli sun, desi boli aali thaari gaadi.", "I'm Ampli, your car that speaks desi."),
        ),
        Event.TalkJoke to listOf(
            Line(
                "ताई बोली, गाड्डी धीरे चला। ताऊ बोल्या, धीरे ए चला रया सूं, सड़क ए तावळी भाज री सै!",
                "Tai boli, gaadi dheere chala. Tau bolya, dheere e chala reya sun, sadak e taavli bhaaj ri sai!",
                "Tai said: drive slowly. Tau said: I am; it's the road that's running fast!",
            ),
            Line(
                "मन्नै ताऊ तै पूछ्या, हॉर्न क्यूं बजाया? बोल्या, भैंस नै राम राम करी थी!",
                "Manne tau te poochhya, horn kyun bajaya? Bolya, bhains nai Ram Ram kari thi!",
                "I asked Tau why he honked. He said: I was saying Ram Ram to the buffalo!",
            ),
            Line(
                "जाम मैं फंस कै ताऊ बोल्या, इतणी देर मैं तो मेरी भैंस भी पहुँच ज्यांदी!",
                "Jaam main phans ke tau bolya, itni der main to meri bhains bhi pahunch jyaandi!",
                "Stuck in a jam, Tau said: my buffalo would have got there by now!",
            ),
            Line(
                "ताऊ बोल्या, जीपीएस की के जरूरत, मैं तो हर चौक पै रास्ता पूछ ल्यूं सूं!",
                "Tau bolya, GPS ki ke zaroorat, main to har chowk pe rasta poochh lyun sun!",
                "Tau said: who needs GPS? I ask the way at every crossing!",
            ),
            Line(
                "स्पीड ब्रेकर देख कै ताऊ बोल्या, यो सड़क भी थक कै बैठ गी!",
                "Speed breaker dekh ke tau bolya, yo sadak bhi thak ke baith gi!",
                "Seeing a speed breaker, Tau said: even the road got tired and sat down!",
            ),
        ),

        // ------------------------------------------------------------------ Human voice only
        Event.SearchingPlain to listOf(
            Line("ढूंढ रया सूं, रुक जा एक मिनट।", "Dhoond reya sun, ruk ja ek minute.", "Looking it up, wait a minute."),
            Line("ठहर जा भाई, देख रया सूं।", "Thahar ja bhai, dekh reya sun.", "Hold on, brother, I'm looking."),
            Line("इबे तलाश करूं।", "Ibe talash karun.", "Searching right away."),
            Line("चाल, पता करूं सूं कित सै।", "Chaal, pata karun sun kit sai.", "OK, finding out where it is."),
        ),
        Event.StartingNavPlain to listOf(
            Line("चाल भाई, चालांगे। रास्ता मैं बताऊंगा।", "Chal bhai, chalange. Rasta main bataunga.", "Come on, let's go. I'll show the way."),
            Line("रास्ता लाग ग्या, आराम तै चालियो।", "Rasta laag gaya, aaram te chaliyo.", "Route is set, drive easy."),
            Line("ठीक सै, चाल पड़ां। ध्यान तै चलाणा भाई।", "Theek sai, chaal padaan. Dhyan te chalana bhai.", "OK, let's go. Drive carefully, brother."),
            Line("चाल पड़े, बेल्ट ला ले।", "Chaal pade, belt la le.", "We're off, buckle up."),
        ),
        Event.RouteFoundPlain to listOf(
            Line("चाल, रास्ता मिल गया, चाल पड़।", "Chaal, rasta mil gaya, chaal pad.", "Come on, got the route, let's go."),
            Line("रास्ता मिल ग्या भाई, दूरी स्क्रीन पै देख ले।", "Rasta mil gaya bhai, doori screen pe dekh le.", "Got the route, brother, the distance is on the screen."),
            Line("लै, रास्ता त्यार सै, चाल पड़।", "Lai, rasta tyaar sai, chaal pad.", "Here, the route is ready, let's go."),
            Line("बढ़िया, रास्ता लाग ग्या।", "Badhiya, rasta laag gaya.", "Great, the route is set."),
        ),
        Event.NavSoonTail to listOf(
            Line("आगे,", "aage,", "ahead,"),
            Line("आगे जा कै,", "aage ja ke,", "further on,"),
            Line("बाद,", "baad,", "after that,"),
            Line("पै,", "pe,", "at that point,"),
        ),
        Event.SpeedLimitPlain to listOf(
            Line("स्पीड घटा ले भाई, लिमिट तै ऊपर सै।", "Speed ghata le bhai, limit te upar sai.", "Reduce speed, brother, you're over the limit."),
            Line("धीरे कर ले, आड़े लिमिट कम सै।", "Dheere kar le, aade limit kam sai.", "Slow down, the limit here is lower."),
            Line("लिमिट तै ऊपर ना, चालान कट ज्यागा।", "Limit te upar na, chalaan kat jyaga.", "Not above the limit, or you'll get a fine."),
            Line("भाई, स्पीड घणी सै, लिमिट स्क्रीन पै देख ले।", "Bhai, speed ghani sai, limit screen pe dekh le.", "Brother, too fast; the limit is on the screen."),
            Line("भाई मंदा कर ले, लिमिट तै ऊपर सै।", "Bhai manda kar le, limit te upar sai.", "Brother, slow down, you're over the limit."),
        ),
        Event.CameraAheadTail to listOf(
            Line("पै कैमरा सै, स्पीड कम कर ले!", "pe camera sai, speed kam kar le!", "{distance}: camera there, slow down!"),
            Line("पै कैमरा आ रया सै, ध्यान तै।", "pe camera aa reya sai, dhyan te.", "{distance}: a camera is coming, careful."),
            Line("पै कैमरा खड़्या सै, लिमिट तै ऊपर मत जाइयो।", "pe camera khadya sai, limit te upar mat jaiyo.", "{distance}: a camera stands there, don't go over the limit."),
            Line("पै कैमरा सै, आराम तै।", "pe camera sai, aaram te.", "{distance}: camera there, take it easy."),
        ),
        Event.TollPaidPlain to listOf(
            Line("टोल कट गया भाई, स्क्रीन पै देख ले कितणे कटे।", "Toll kat gaya bhai, screen pe dekh le kitne kate.", "Toll deducted, brother; see on screen how much."),
            Line("फास्टैग तै टोल कट ग्या, चाल आगे।", "FASTag te toll kat gaya, chaal aage.", "The toll went from FASTag, let's move on."),
            Line("टोल पार! पैसे कट गए।", "Toll paar! Paise kat gaye.", "Past the toll! Money deducted."),
            Line("टोल का पैसा कट ग्या, बैलेंस स्क्रीन पै सै।", "Toll ka paisa kat gaya, balance screen pe sai.", "Toll paid; the balance is on the screen."),
        ),
        Event.BreakReminderPlain to listOf(
            Line("घणी देर हो ली चलाते, थक ग्या होगा भाई, एक चाय हो जाए?", "Ghani der ho li chalaate, thak gaya hoga bhai, ek chai ho jaye?", "You've been driving a long time, tired? A cup of tea?"),
            Line("थोड़ा रुक कै कमर सीधी कर ले भाई।", "Thoda ruk ke kamar seedhi kar le bhai.", "Stop a bit and stretch your back, brother."),
            Line("भाई घणी देर हो गी, ढाबा आवै तै रुक लियो।", "Bhai ghani der ho gi, dhaba aave te ruk liyo.", "Brother, it's been a while; stop at the next dhaba."),
            Line("स्टीयरिंग घणी देर तै पकड़ राख्या सै, पाँच मिनट टांग सीधी कर ले।", "Steering ghani der te pakad raakhya sai, paanch minute taang seedhi kar le.", "You've held the wheel a long time; stretch your legs for five minutes."),
        ),
    )

    /** The lines for [event], or null for safety events and events with no desi lines. */
    fun forEvent(event: Event): List<Line>? = if (event.safety) null else LINES[event]

    /** Every playful line, for tests, the samples list and the review file. */
    internal val allLines: Map<Event, List<Line>> get() = LINES

    /** The time-of-day line for [hour] (0..23): morning 4–11, afternoon 12–16, evening 17–20, night. */
    fun timeOfDay(hour: Int): Event = when (hour) {
        in 4..11 -> Event.WelcomeMorning
        in 12..16 -> Event.WelcomeAfternoon
        in 17..20 -> Event.WelcomeEvening
        else -> Event.WelcomeNight
    }

    /** Example values for an event's placeholders: Settings › Voice › Hear samples and tests. */
    fun sampleArgs(event: Event): Array<Any> = when (event) {
        Event.Searching, Event.StartingNav -> arrayOf("Rohtak")
        Event.RouteFound -> arrayOf("42", 55)
        Event.NavSoon -> arrayOf("400 मीटर")
        Event.NavRoundaboutExit -> arrayOf(2)
        Event.SpeedLimit -> arrayOf(60)
        Event.CameraAhead -> arrayOf(300, 60)
        Event.CameraAheadNoLimit -> arrayOf(300)
        Event.TollPaid -> arrayOf("145", "855")
        Event.TollPaidNoBalance -> arrayOf("145")
        Event.BreakReminder -> arrayOf("2")
        Event.MusicPlayNamed -> arrayOf("52 गज का दामण")
        else -> emptyArray()
    }
}

/**
 * Picks a random variant per event, never the same one twice in a row. Keeps one memory per
 * event, so a camera alert doesn't reset the greeting's history.
 */
class LinePicker(private val random: Random = Random.Default) {
    private val last = HashMap<HaryanviLines.Event, Int>()

    fun pick(event: HaryanviLines.Event): HaryanviLines.Line? = pickIndexed(event)?.line

    /** Like [pick], with the variant's clip id ([Picked.id]) for the recorded voice. */
    fun pickIndexed(event: HaryanviLines.Event): Picked? {
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
        return Picked(event, index, lines[index])
    }
}
