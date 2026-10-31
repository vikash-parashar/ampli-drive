package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.voice.HaryanviLines.Event
import com.vikash.ampli.drive.voice.HaryanviLines.Line

/*
 * Haryanvi lines, round 3: the owner's approved navigation list, word for word (only "%d" became
 * "%1$d"). Spoken during Car Hub's own turn-by-turn, Haryanvi style only; the triggers are in
 * nav/NavCues.kt. Appended after rounds 1 and 2, so earlier clip ids keep their numbers. Like
 * round 2, a recorded voice only picks a line once its clip is in the build ([VoiceGate]); lines
 * with a number are never recorded, so a recorded voice skips them (or says the usual line).
 *
 * Not here, because nothing in the app can tell when to say them: flyover choice and jam under
 * the flyover, speed breakers, traffic jam / jam cleared, the dhaba landmark and "toll ahead".
 * See docs/haryanvi-lines.md, "Not in this list".
 */
internal val LINES_V3: Map<Event, List<Line>> = mapOf(
    Event.NavArrive to listOf(
        Line("पहुँच गे भाई! गाड्डी ढंग तै लगा दिए।", "Pahunch ge bhai! Gaaddi dhang te laga diye.", "We're here! Park properly."),
        Line("मंजिल आ गी, जय बाबा की! आज का गेड़ा बढ़िया रया।", "Manzil aa gi, Jai Baba Ki! Aaj ka geda badhiya raya.", "Arrived, Jai Baba Ki! Good trip today."),
    ),
    Event.NavDistanceLeft to listOf(
        Line("बस %1\$d किलोमीटर बचे, घणा ना सै इब।", "Bas %1\$d kilometre bache, ghana na sai ib.", "Just {km} km left, not much now."),
        Line("%1\$d किलोमीटर और, फेर पहुँच गे समझ।", "%1\$d kilometre aur, pher pahunch ge samajh.", "{km} km more and we're there."),
    ),
    Event.NavHalfway to listOf(
        Line("आधा रास्ता हो ग्या भाई, बाकी आधा मौज मैं।", "Aadha rasta ho gya bhai, baaki aadha mauj main.", "Half way done, the rest is a breeze."),
    ),
    Event.NavTenMinutesLeft to listOf(
        Line("बस दस मिनट का खेल सै, पहुँचण आळे सां।", "Bas das minat ka khel sai, pahunchan aale saan.", "Ten more minutes, almost there."),
    ),
    Event.NavKeepStraight to listOf(
        Line("सीधा चालता रह, आगले %1\$d किलोमीटर कोए मोड़ कोनी।", "Seedha chaalta reh, aagle %1\$d kilometre koi mod koni.", "Keep straight, no turn for {km} km."),
        Line("नाक की सीध मैं चाल, मैं बता द्यूंगा कद मुड़णा सै।", "Naak ki seedh main chaal, main bata dyunga kad mudna sai.", "Straight ahead, I'll tell you when to turn."),
    ),
    Event.NavPumpLeft to listOf(
        Line("पेट्रोल पंप तै उल्टे हाथ ले लिए।", "Petrol pump te ulte haath le liye.", "Take the left at the petrol pump."),
    ),
    Event.NavCngPumpRight to listOf(
        Line("सीएनजी पंप कै बाद सुल्टे हाथ मुड़ ले।", "CNG pump ke baad sulte haath mud le.", "Turn right after the CNG pump."),
    ),
    Event.NavRoundaboutSecondExit to listOf(
        Line("गोल चक्कर आ रया सै, दूसरा रास्ता पकड़ लिए।", "Gol chakkar aa raya sai, doosra rasta pakad liye.", "Roundabout ahead, take the second exit."),
    ),
    Event.NavRoundaboutThirdExit to listOf(
        Line("चक्कर पै तीसरा निकास, घूम कै सीधा।", "Chakkar pai teesra nikaas, ghoom ke seedha.", "Third exit at the roundabout."),
    ),
    Event.NavHighwayEnter to listOf(
        Line("हाईवे पै चढ़ ले भाई, इब खुली सड़क सै।", "Highway pai chadh le bhai, ib khuli sadak sai.", "Get on the highway, open road now."),
    ),
    Event.NavHighwayExitRight to listOf(
        Line("आग्गै हाईवे तै उतरणा सै, सुल्टे हाथ आळी लेन मैं आ ज्या।", "Aaggai highway te utarna sai, sulte haath aali lane main aa jya.", "Exit the highway ahead, move to the right lane."),
    ),
    Event.NavLaneLeft to listOf(
        Line("उल्टे हाथ आळी लेन पकड़ ले, आग्गै मुड़णा सै।", "Ulte haath aali lane pakad le, aaggai mudna sai.", "Get in the left lane, turn coming."),
    ),
    Event.NavLaneMiddle to listOf(
        Line("बीच आळी लेन मैं रह, दोनूं कान्ही रास्ता बंटैगा।", "Beech aali lane main reh, donun kaanhi rasta bantaiga.", "Stay in the middle lane, the road splits."),
    ),
    Event.NavArrivingRight to listOf(
        Line("बस आ गे, %1\$d मीटर पै सुल्टे हाथ थारी मंजिल सै।", "Bas aa ge, %1\$d meetar pai sulte haath thaari manzil sai.", "Almost there, destination on the right in {m} m."),
    ),
)
