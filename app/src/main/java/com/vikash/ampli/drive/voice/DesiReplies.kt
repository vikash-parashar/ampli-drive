package com.vikash.ampli.drive.voice

import java.util.Locale

/**
 * Hey Ampli's plain replies in everyday Haryanvi, for the Haryanvi voice style.
 *
 * Most of Ampli's moments have a Haryanvi line in [HaryanviLines], but the plain replies (no
 * GPS fix, song not found, permission needed, "kitne ka bharwaya?", the speed, the camera
 * ahead…) used to come straight from the app-language strings, so a Hindi-language unit
 * heard textbook Hindi ("कृपया सेटिंग्स में… अनुमति दें") in the middle of a Haryanvi chat.
 * When the Haryanvi style is on and the app is in Hindi or Haryanvi, the reply below is said
 * instead (and shown on the answer card); with the style off, the string resource is used as
 * before. Keyed by the string resource name, with exactly the same placeholders
 * (DesiRepliesTest checks them against values-hi).
 *
 * Gender-neutral first person ("खोल दिया", "बता द्यूं सूं"), since either recorded voice
 * may be the one talking. Safety replies (SOS, crash) are never here: they stay plain and clear.
 * Written in Devanagari for the Hindi voice (eSpeak NG's and Ampli's own both read ण, ळ and
 * the nasal सूं / सैं right; Latin Haryanvi through an English voice does not).
 * A reply without a blank is also in the voice pack ([BankClips], "desi_<name>"): the picked
 * recorded voice says it when its clip is up to date, else the phone voice.
 */
object DesiReplies {

    val LINES: Map<String, String> = mapOf(
        // Microphone, speech and permissions
        "voice_mic_denied" to "सेटिंग्स मैं जा कै माइक की इजाजत दे दे भाई, फेर बोलिए।",
        "voice_unavailable" to "बोली पिछाणण आळा इंजन इब ताईं त्यार कोनी। सेटिंग्स, आवाज़, आवाज़ जाँचें खोल कै देख ले।",
        "voice_music_permission" to "सेटिंग्स, वॉइस मैं गाण्यां की इजाजत दे दे, फेर बजा द्यूं।",
        "voice_brightness_permission" to "पहलां Car Hub नै सिस्टम सेटिंग बदलण की इजाजत दे दे, फेर बोलिए।",
        "voice_volume_failed" to "Android नै आवाज कोनी बदलण दी, कदे डू नॉट डिस्टर्ब तो चालू ना सै?",
        "power_say_screen_off" to "स्क्रीन बंद करूं सूं। रोकणी हो तो बोल दे, रद्द करो।",

        // Places and navigation
        "voice_say_number" to "नंबर बोल दे भाई, जिंयां “दूसरी आळी”।",
        "voice_search_failed" to "इब टोह कोनी होई, नेट चाल रया सै के, देख ले।",
        "voice_no_places" to "%1\$s कोनी मिल्या, कोए और नाम बोल कै देख।",
        "voice_found_one" to "एक जगहां मिली सै।",
        "voice_nav_failed" to "रास्ता कोनी खुल्या, एक बार फेर बोल दे।",
        "voice_nav_stopped" to "ठीक सै, रास्ता बंद कर दिया।",
        "voice_nav_stop_maps" to "Car Hub मैं तो कोए रास्ता कोनी चाल रया। Google Maps आळा रास्ता बंद करणा हो तो Maps मैं X दबा दे।",
        "voice_no_location" to "इब GPS तै गाड्डी की जगहां कोनी मिली।",
        "place_not_set" to "%1\$s इब ताईं सेव कोनी। पहलां जगहां टोह कै सेव कर ले।",

        // Phone, music, apps
        "voice_bt_playing" to "ले, थारे फोन तै बजा दिया।",
        "voice_bt_start_phone" to "फोन पै गाणा चला दे भाई, बाजण लाग ज्या तो फेर मैं संभाल ल्यूं।",
        "voice_auto_missing" to "इस यूनिट मैं Android Auto या फोन जोड़ण आळा ऐप कोनी मिल्या।",
        "voice_auto_opening" to "फोन मेरे तै खुद कोनी जुड़दा, ज्यां तै %1\$s खोल दिया। पहली बार जोड़णा फोन पै ए पड़ैगा।",
        "voice_call" to "फोन खोल दिया, %1\$s नै चुण ले।",
        "voice_call_any" to "ले, फोन खोल दिया।",
        "voice_song_not_found" to "%1\$s इस यूनिट पै कोनी मिल्या।",
        "voice_no_player" to "कोए गाणा कोनी चाल्या। एक बार Ampli MP3 या कोए गाण्यां का ऐप खोल ले, फेर बोलिए।",
        "voice_feedback" to "मदद अर राय आळा पेज खोल दिया। गाड्डी रुकैगी जद फॉर्म खुलैगा।",
        "dashcam_voice_not_recording" to "डैशकैम इब रिकॉर्ड कोनी कर रया।",
        "x_hotspot_unsupported" to "वाई-फाई सेटिंग खोल दी, ओड़ै फोन का हॉटस्पॉट चुण ले।",
        "x_hotspot_none" to "पहलां सेटिंग मैं फोन का हॉटस्पॉट चुण ले, वाई-फाई सेटिंग खोल दी सै।",
        "x_hotspot_joining" to "%1\$s तै जोड़ूं सूं।",
        "voice_report_pro" to "सड़क रिपोर्ट Pro मैं आवै सै।",

        // Answers: time, speed, weather, cameras, fuel
        "voice_time_is" to "इब %1\$s बजे सैं।",
        "voice_date_is" to "आज %1\$s सै।",
        "voice_speed_is" to "थारी स्पीड %1\$d किलोमीटर घंटे की सै।",
        "voice_weather_offline" to "नेट कोनी चाल रया, ज्यां तै इब मौसम कोनी देख सकदे।",
        "voice_weather_failed" to "मौसम का हाल इब कोनी मिल्या, थोड़ी वार पाछै पूछ लिए।",
        "voice_camera_none_ahead" to "आगै 20 किलोमीटर ताईं कोए स्पीड कैमरा कोनी।",
        "voice_camera_next_ahead" to "अगला स्पीड कैमरा %1\$s किलोमीटर पै सै, लिमिट %2\$d।",
        "voice_camera_next_ahead_nolimit" to "अगला स्पीड कैमरा %1\$s किलोमीटर पै सै।",
        "voice_fuel_none" to "50 किलोमीटर ताईं कोए स्टेशन कोनी मिल्या।",
        "voice_fuel_answer" to "%1\$s, %2\$s किलोमीटर दूर सै।",

        // Road reports and the drive diary
        "voice_report_saved" to "%1\$s नोट कर लिया। 2 घंटे ताईं आड़ै चेता द्यूं सूं।",
        "x_camera_saved" to "आड़ै कैमरा सेव कर दिया। जद ताईं तू “कैमरा नहीं है” ना बोलै, रहवैगा।",
        "x_gone_camera" to "ठीक सै, आड़ै तै कैमरा हटा दिया।",
        "x_gone_jam" to "ठीक सै, जाम खुल्या मान लिया।",
        "x_gone_police" to "ठीक सै, पुलिस का नाका हटा दिया।",
        "x_gone_nothing" to "आड़ै धोरै कुछ रिपोर्ट कोनी था।",
        "x_queue_no_pump" to "400 मीटर मैं कोए पंप कोनी। पंप पै पहोंच कै बोलिए।",
        "x_queue_saved_long" to "नोट कर लिया: %1\$s पै लांबी लाइन सै।",
        "x_queue_saved_short" to "नोट कर लिया: %1\$s पै लाइन कोनी।",
        "x_fuel_ask" to "कितणे का भरवाया? रुपये बोल दे या लीटर।",
        "x_fuel_not_saved" to "रकम समझ कोनी आई, खर्चे मैं हाथ तै जोड़ दिए।",
        "x_fuel_saved" to "सेव कर दिया: %1\$s, करीबन %2\$s लीटर।",
        "x_challan_none" to "इब ताईं कुछ कोनी गिण्या। कैमरा की चेतावणी पै धीरे होया कर, आड़ै जुड़दा जावैगा।",
        "x_challan_answer" to "चेतावणी पाछै तू %1\$d बार धीरे होया। करीबन %2\$s तै %3\$s का चालान बच ग्या।",
    )

    /** App languages whose users get these replies in the Haryanvi style (Hindi and Haryanvi). */
    fun appliesTo(appLanguage: String): Boolean =
        Locale.forLanguageTag(appLanguage.ifEmpty { Locale.getDefault().toLanguageTag() }).language in setOf("hi", "bgc")

    /** The Haryanvi reply for the string resource [name] with [args] filled in, or null. */
    fun format(name: String, vararg args: Any): String? {
        val template = LINES[name] ?: return null
        return if (args.isEmpty()) template else String.format(Locale.US, template, *args)
    }
}
