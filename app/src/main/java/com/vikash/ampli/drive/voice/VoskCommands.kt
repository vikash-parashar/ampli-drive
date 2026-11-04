package com.vikash.ampli.drive.voice

/** The phrases the offline recogniser listens for. Each must parse in [VoiceIntents] (unit-tested). */
object VoskCommands {
    /**
     * Every way of asking for music the field reports showed ("gana baja", "gana chala de",
     * "gana suna de"): each music noun with each play verb, plus the verbs alone (said right
     * after the follow-up chime). In grammar mode Vosk only ever outputs grammar phrases, so a
     * form missing here comes out as some other phrase or "[unk]". Words the model lacks get a
     * known spelling through [HinglishSpelling].
     */
    val MUSIC_NOUNS_ROMAN = listOf("gana", "gaana", "gaane", "gane", "song", "songs", "music", "gaana koi", "koi gana", "bhajan")
    val MUSIC_VERBS_ROMAN = listOf(
        "baja", "bajao", "baja de", "baja do", "bajaa de", "chala", "chalao", "chala de", "chala do", "chalaa de",
        "suna", "sunao", "suna de", "suna do", "sunaa de", "laga", "lagao", "laga de", "laga do",
        "play", "play karo", "chalu karo", "shuru karo",
    )
    val MUSIC_PLAY_ROMAN: List<String> = musicMatrix(
        MUSIC_NOUNS_ROMAN, MUSIC_VERBS_ROMAN,
        verbOnly = listOf("baja de", "chala de", "suna de", "laga de", "baja do", "bajao", "chalao", "play karo"),
    )

    val MUSIC_NOUNS_HINDI = listOf("गाना", "गाने", "सॉन्ग", "म्यूजिक", "कोई गाना", "भजन")
    val MUSIC_VERBS_HINDI = listOf(
        "बजा", "बजाओ", "बजा दे", "बजा दो", "चला", "चलाओ", "चला दे", "चला दो", "सुना", "सुनाओ", "सुना दे", "सुना दो",
        "लगा", "लगाओ", "लगा दे", "लगा दो", "प्ले करो", "चालू करो", "शुरू करो",
    )
    val MUSIC_PLAY_HINDI: List<String> = musicMatrix(
        MUSIC_NOUNS_HINDI, MUSIC_VERBS_HINDI,
        verbOnly = listOf("बजा दे", "चला दे", "सुना दे", "लगा दे", "बजा दो", "चला दो"),
    )

    private fun musicMatrix(nouns: List<String>, verbs: List<String>, verbOnly: List<String>): List<String> =
        nouns.flatMap { n -> verbs.map { v -> "$n $v" } } + verbOnly

    val COMMANDS = listOf(
        "navigate", "navigate to", "take me home", "take me to",
        "go home", "go to office", "take me to office", "ghar chalo", "office chalo",
        "next camera", "speed camera",
        "play music", "play", "pause", "stop music", "next song", "previous song", "resume",
        "call", "android auto", "connect my phone",
        "where is my car", "tyre pressure", "next service", "engine status",
        "sos", "help me", "emergency", "report a problem",
        "cancel", "stop", "hello",
        "how are you", "who are you", "tell me a joke",
        // Round 7: volume, stop, time, weather, maps, brightness, Bluetooth, help.
        "volume", "volume up", "volume down", "volume full", "volume five", "volume ten", "mute", "unmute",
        "louder", "quieter", "play next song", "next", "previous", "preview", "stop navigation",
        "what time is it", "what is the time", "what is the date", "how is the weather", "weather",
        "will it rain", "what is my speed", "open maps", "brightness up", "brightness down",
        "bluetooth settings", "what can i say",
        // Fuel finder and road reports.
        "nearest cng pump", "cng pump", "petrol pump", "diesel pump", "charging station", "fuel station",
        "police ahead", "accident ahead", "traffic jam ahead", "mobile camera ahead",
        // Screen off by voice.
        "screen off", "turn off the screen",
        // Camera and police reports, pump queues, fuel diary, challan counter, break, hotspot.
        "camera here", "no camera here", "police gone", "long queue at cng", "no queue at the pump",
        "petrol filled", "how much did i save", "remind me later", "connect hotspot",
        // Talking with Ampli: thanks, greetings, the day, the news, another joke.
        "thank you", "thanks", "good morning", "good evening", "how is today", "tell me the news", "news",
        "headlines", "tell me another", "one more", "i am bored", "talk to me",
    )

    /**
     * Roman Hindi / Hinglish phrases for the English model, so Hindi-speaking drivers are heard
     * on head units without Google speech before the Hindi model is downloaded. Vosk drops words
     * its English vocabulary lacks (with a log warning) and keeps the rest of the phrase, so each
     * phrase is chosen so that what is left still means the same or nothing ("gaana chalao" →
     * "chalao" still plays). Every phrase must parse in [VoiceIntents] (unit-tested).
     */
    val HINGLISH_COMMANDS = listOf(
        "le chalo", "le chal", "rasta dikhao", "ghar le chalo", "office le chalo",
        "gaana chalao", "gaana bajao", "gana chalao", "music chalao", "gaana chalu karo",
        "gaana band karo", "gaana roko", "music band karo", "agla gaana", "pichla gaana",
        "awaaz badhao", "awaz badhao", "awaaz kam karo", "awaaz tez karo", "awaaz full karo", "awaaz band",
        "volume badhao", "volume kam karo", "volume full karo",
        "cng pump kahan hai", "petrol pump kahan hai", "diesel pump kahan hai", "pump kahan hai",
        "charging station kahan hai",
        "kitne baje hain", "time kya hua", "aaj kya date hai", "mausam kaisa hai", "baarish hogi",
        "speed kitni hai", "map kholo", "naksha kholo", "bluetooth kholo",
        "brightness badhao", "brightness kam karo", "navigation band karo", "rasta band karo",
        "ruko", "band karo", "bas", "cancel karo", "namaste",
        "madad karo", "bachao", "gaadi kahan hai", "phone karo", "call karo", "phone jodo",
        "tyre pressure kitna hai", "service kab hai", "engine kaisa hai",
        "police aage hai", "aage jam hai", "accident aage hai", "mobile camera aage hai",
        "kya haal hai", "ke haal sai", "tum kaun ho", "chutkula sunao", "kya bol sakta hoon",
        "screen band karo", "screen off karo", "display band karo",
        "radd karo",
        // Police / camera reports by voice (direction-aware, they expire), and taking them back.
        "police hai", "aage police hai", "naka hai", "aage naka hai", "naka laga hai",
        "camera hai", "aage camera hai", "camera laga hai", "camera nahi hai", "police hat gayi", "naka hat gaya",
        "jam khul gaya",
        // Pump queues ("CNG pe line lambi hai") and the petrol diary ("1000 ka petrol").
        "cng pe line lambi hai", "cng par lambi line hai", "line lambi hai", "pump khali hai", "cng khali hai",
        "line nahi hai", "petrol bharwaya", "petrol dalwaya", "cng bharwayi", "diesel bharwaya",
        "five hundred ka petrol", "one thousand ka petrol", "two thousand ka petrol", "five hundred ki cng",
        "twenty litre", "thirty litre", "forty litre", "fifty litre",
        "kitna challan bachaya", "break baad mein", "hotspot jodo", "phone ka internet chalao",
        "gaadi kahan khadi hai",
        // How drivers really ask for music, the music keys and the volume when answering Ampli.
        "gana sunao", "gaana sunao", "gaane sunao", "gane sunao", "gaana suna do", "gana suna do",
        "gaana lagao", "gana lagao", "gaane lagao", "gaana chala do", "gana chala do", "gaane chalao",
        "gaana baja do", "gana bajao", "gaane bajao", "koi gaana chalao", "koi gaana sunao", "koi gana lagao",
        "song chalao", "song lagao", "song sunao", "song bajao", "song chala do",
        "music sunao", "music lagao", "music bajao", "music chala do", "music suna do",
        "bhajan sunao", "bhajan chalao", "bhajan lagao", "bhajan bajao",
        "gaana band kar", "gaana band kar do", "gana band karo", "gaane band karo", "gaana rok do",
        "music band kar do", "music roko", "song band karo", "song roko",
        "agla chalao", "agla gaana chalao", "agla song", "agla laga do", "next gaana", "agla gana lagao",
        "pichla chalao", "pichla song", "pichla gaana chalao",
        "awaaz badha do", "awaz badha do", "awaaz tez kar do", "awaaz kam kar do", "awaaz dheemi karo",
        "volume badha do", "volume kam kar do",
        // Talking with Ampli (Haryanvi / Hindi): how are you, greetings, who are you, thanks, bored.
        "suna de kya haal hai", "kya haal hai bhai", "ke haal se", "kaisa hai", "kaise ho", "sab badhiya ke",
        "kya chal raha hai", "ram ram", "ram ram ji", "jai ram ji ki", "kaun hai tu", "tera naam kya hai",
        "tera naam ke se", "dhanyawad", "shukriya", "bore ho raha hoon", "baat kar", "baat karo", "mujhse baat karo",
        // The day, the news, jokes.
        "aaj ka din kaisa hai", "aaj ka din kaisa rahega", "aaj kya hai", "aaj ki tareekh",
        "aaj ki taaza khabar", "taaza khabar sunao", "taaza khabar suna de", "khabar suna", "khabar sunao", "khabar suna de",
        "news batao", "news sunao", "samachar sunao", "samachar", "aur khabar", "agli khabar",
        "chutkula suna", "chutkula suna de", "joke suna", "joke sunao", "hasa de", "koi mazedaar baat suna",
        "ek aur chutkula", "ek aur", "aur suna", "aur sunao", "ek aur suna",
        // Music in Haryanvi: stop, next, previous, louder / softer.
        "gaana rok de", "gana rok de", "gaana band kar de", "gana band kar de", "rok de", "roko", "tham ja", "thamja",
        "chup kar ja", "chup", "chup kar", "agla laga de", "agla gaana laga de", "aage ka", "pichla laga de",
        "awaaz badha de", "tez kar de", "awaaz kam kar de", "dheere kar de",
    ) + MUSIC_PLAY_ROMAN


    /** Only right after a volume change ("aur" / "more"); see VoiceIntents.volumeFollowUp. */
    val FOLLOW_UPS = listOf(
        "more", "a bit more", "again", "aur", "thoda aur", "aur tez", "aur kam",
        // After a joke or the news: one more.
        "ek aur", "aur suna", "aur sunao", "ek aur suna", "aur khabar",
    )
    val HINDI_FOLLOW_UPS = listOf("और", "थोड़ा और", "और तेज", "और कम", "एक और", "और सुना", "और सुनाओ", "एक और सुना", "और खबर")

    /** Only while a list of places is showing. */
    val CHOICES = listOf(
        "first", "second", "third", "first one", "second one", "third one", "one", "two", "three",
        "pehla", "doosra", "teesra", "pehla wala", "doosra wala", "teesra wala",
    )

    /**
     * Hindi / Haryanvi phrases for the Hindi model (Devanagari, as that model writes). Words the
     * model doesn't know are skipped by Vosk with a log warning, so plain Hindi words are used.
     */
    val HINDI_COMMANDS = listOf(
        "रास्ता दिखाओ", "ले चलो", "घर ले चलो", "चलो", "ले चल", "नेविगेट करो",
        "घर चलो", "ऑफिस चलो", "ऑफिस ले चलो", "दफ्तर चलो",
        "कैमरा", "अगला कैमरा",
        "गाना बजाओ", "गाना चलाओ", "बजाओ", "गाना बंद करो", "गाना रोको", "अगला गाना", "पिछला गाना", "गाना चालू करो",
        "फोन करो", "कॉल करो", "फोन जोड़ो",
        "गाड़ी कहाँ है", "अगली सर्विस कब", "टायर प्रेशर", "इंजन कैसा है",
        "मदद", "बचाओ", "इमरजेंसी", "शिकायत",
        "रद्द करो", "रहने दो", "बस", "नमस्ते",
        "के हाल सै", "क्या हाल है", "तू कौन सै", "तुम कौन हो", "चुटकला सुणा", "चुटकुला सुनाओ",
        // Round 7.
        "आवाज बढ़ाओ", "आवाज कम करो", "आवाज पूरी करो", "आवाज बंद", "वॉल्यूम", "म्यूट", "अनम्यूट",
        "रुको", "बंद करो", "नेविगेशन बंद करो", "रास्ता बंद करो", "कितने बजे हैं", "टाइम क्या हुआ",
        "आज क्या तारीख है", "मौसम कैसा है", "बारिश होगी", "स्पीड कितनी है", "मैप खोलो", "नक्शा खोलो",
        "ब्लूटूथ खोलो", "ब्राइटनेस बढ़ाओ", "ब्राइटनेस कम करो", "क्या बोल सकता हूँ",
        // Fuel finder and road reports.
        "सीएनजी पंप कहाँ है", "सीएनजी पंप", "पेट्रोल पंप", "डीजल पंप", "चार्जिंग स्टेशन",
        "पुलिस आगे है", "एक्सीडेंट आगे है", "आगे जाम है", "मोबाइल कैमरा आगे है",
        // Screen off.
        "स्क्रीन बंद करो", "स्क्रीन ऑफ करो",
        // Police / camera reports and taking them back.
        "पुलिस है", "आगे पुलिस है", "नाका है", "आगे नाका है", "नाका लगा है",
        "कैमरा है", "आगे कैमरा है", "कैमरा लगा है", "कैमरा नहीं है", "पुलिस हट गई", "नाका हट गया", "जाम खुल गया",
        // Pump queues and the petrol diary.
        "सीएनजी पे लंबी लाइन है", "लाइन लंबी है", "पंप खाली है", "सीएनजी खाली है", "लाइन नहीं है",
        "पेट्रोल भरवाया", "पेट्रोल डलवाया", "सीएनजी भरवाई", "डीजल भरवाया",
        "हजार का पेट्रोल", "पांच सौ का पेट्रोल", "दो हजार का पेट्रोल", "पांच सौ की सीएनजी",
        "बीस लीटर", "तीस लीटर", "चालीस लीटर", "पचास लीटर",
        "कितना चालान बचाया", "ब्रेक बाद में", "हॉटस्पॉट जोड़ो", "गाड़ी कहाँ खड़ी है",
        // How drivers really ask for music, the music keys and the volume when answering Ampli.
        "गाना सुनाओ", "गाने सुनाओ", "गाना सुना दो", "गाने सुना दो", "गाना लगाओ", "गाने लगाओ",
        "गाना चला दो", "गाने चलाओ", "गाना बजा दो", "गाने बजाओ", "कोई गाना चलाओ", "कोई गाना सुनाओ", "कोई गाना लगाओ",
        "म्यूजिक चलाओ", "म्यूजिक बजाओ", "म्यूजिक लगाओ", "म्यूजिक सुनाओ", "सॉन्ग चलाओ", "सॉन्ग लगाओ",
        "भजन सुनाओ", "भजन चलाओ", "भजन लगाओ", "भजन बजाओ",
        "गाना बंद कर", "गाना बंद कर दो", "गाने बंद करो", "गाना रोक दो", "म्यूजिक बंद करो", "म्यूजिक बंद कर दो",
        "अगला चलाओ", "अगला गाना चलाओ", "अगला लगाओ", "अगला गाना लगाओ", "पिछला चलाओ", "पिछला गाना चलाओ",
        "आवाज बढ़ा दो", "आवाज कम कर दो", "आवाज तेज करो", "आवाज तेज कर दो", "आवाज धीमी करो",
        "वॉल्यूम बढ़ाओ", "वॉल्यूम कम करो", "वॉल्यूम बढ़ा दो",
        // English said by drivers whose app is in English but whose unit hears them through the
        // Hindi model (offline-only units answer Ampli with it): as the Hindi model writes them.
        "प्ले म्यूजिक", "म्यूजिक प्ले करो", "गाना प्ले करो", "प्ले करो", "नेक्स्ट सॉन्ग", "नेक्स्ट", "स्टॉप",
        "पॉज़ करो", "वॉल्यूम अप", "वॉल्यूम डाउन", "कैंसल", "हेलो",
        // Talking with Ampli (Haryanvi / Hindi).
        "सुना दे क्या हाल है", "के हाल से", "कैसा है", "कैसे हो", "सब बढ़िया के", "क्या चल रहा है",
        "राम राम", "राम राम जी", "जय राम जी की", "गुड मॉर्निंग", "कौन है तू", "तेरा नाम क्या है", "तेरा नाम के से",
        "थैंक यू", "धन्यवाद", "शुक्रिया", "बोर हो रहा हूँ", "बात कर", "बात करो", "मुझसे बात करो",
        // The day, the news, jokes.
        "आज का दिन कैसा है", "आज का दिन कैसा रहेगा", "आज क्या है",
        "आज की ताजा खबर", "ताजा खबर सुनाओ", "ताजा खबर सुना दे", "खबर सुना", "खबर सुनाओ", "खबर सुना दे",
        "समाचार सुनाओ", "समाचार", "न्यूज सुनाओ", "और खबर", "अगली खबर",
        "चुटकुला सुना", "चुटकुला सुना दे", "चुटकला सुना दे", "जोक सुना", "हंसा दे", "कोई मजेदार बात सुना", "एक और चुटकुला",
        "एक और", "और सुना", "और सुनाओ", "एक और सुना",
        // Music in Haryanvi.
        "गाना रोक दे", "गाना बंद कर दे", "रोक दे", "रोको", "थम जा", "चुप कर जा", "चुप", "चुप कर",
        "अगला लगा दे", "अगला गाना लगा दे", "आगे का", "पिछला लगा दे", "आवाज बढ़ा दे", "तेज कर दे", "आवाज कम कर दे", "धीरे कर दे",
    ) + MUSIC_PLAY_HINDI

    val HINDI_CHOICES = listOf("पहला", "दूसरा", "तीसरा", "पहली", "दूसरी", "तीसरी", "एक", "दो", "तीन")

    /**
     * Short answers to Ampli's questions (incoming call "uthaun?", "suna doon?", "bhej doon?",
     * the screen-off countdown), for the English model. A small grammar of its own, so a "haan" said over
     * a ringtone or music is picked out instead of being transcribed as some English word. Each
     * phrase must mean something to [com.vikash.ampli.drive.calls.VoiceReplies] (unit-tested);
     * words the model lacks are dropped by Vosk, so English twins are listed too.
     */
    val REPLIES = listOf(
        // Yes / pick up.
        "haan", "han", "haan ji", "ji haan", "yes", "yeah", "ok", "okay", "theek hai",
        "utha lo", "uthao", "utha le", "answer", "pick up", "accept",
        // No / later.
        "na", "naa", "nahi", "nahin", "no", "nope", "baad mein", "abhi nahi", "rehne de", "reject", "decline", "later",
        "kaat de", "mat utha",
        // Messages.
        "suna de", "sunao", "suna do", "padh de", "read", "read it", "skip", "chhod de",
        "jawab de", "jawab bhej", "reply", "bhej de", "bhej do", "send", "send it", "mat bhej", "galat",
        // Stop / cancel (the screen-off countdown).
        "cancel", "cancel karo", "radd karo", "ruko", "band karo", "stop",
    )

    /** [REPLIES] for the Hindi model, in Devanagari. */
    val HINDI_REPLIES = listOf(
        "हाँ", "हां", "हाँ जी", "जी हाँ", "ठीक है", "उठा लो", "उठाओ", "उठा ले", "ओके",
        "ना", "नहीं", "बाद में", "अभी नहीं", "रहने दे", "काट दे", "मत उठा",
        "सुना दे", "सुनाओ", "पढ़ दे", "छोड़ दे", "जवाब दे", "जवाब भेज", "भेज दे", "भेज दो", "मत भेज", "गलत",
        "रद्द करो", "रुको", "बंद करो", "कैंसल",
    )

    fun replyPhrases(hindi: Boolean = false): List<String> = (if (hindi) HINDI_REPLIES else REPLIES).distinct()

    fun replyGrammarJson(hindi: Boolean = false): String = json(replyPhrases(hindi) + "[unk]")

    /** Every phrase the grammar for that model holds, without "[unk]" (no duplicates). */
    fun phrases(hindi: Boolean = false): List<String> =
        (if (hindi) HINDI_COMMANDS + HINDI_CHOICES + HINDI_FOLLOW_UPS else COMMANDS + HINGLISH_COMMANDS + CHOICES + FOLLOW_UPS).distinct()

    fun grammarJson(hindi: Boolean = false): String = json(phrases(hindi) + "[unk]")

    /**
     * The grammar after Ampli has said something ([ListenMode.FollowUp]): every command phrase
     * plus the short answers ([REPLIES]) and small-talk replies ([FollowUp.CHAT]). The answer
     * grammar alone would turn "gaana chalao" or "ghar chalo" into "[unk]"; the command grammar
     * alone would do the same to "haan", "nahi" or "badhiya".
     */
    fun followUpPhrases(hindi: Boolean = false): List<String> =
        (phrases(hindi) + replyPhrases(hindi) + (if (hindi) FollowUp.HINDI_CHAT else FollowUp.CHAT)).distinct()

    fun followUpGrammarJson(hindi: Boolean = false): String = json(followUpPhrases(hindi) + "[unk]")

    /** A grammar of [phrases] plus "[unk]", for phrases adapted to a model ([HinglishSpelling]). */
    fun grammarOf(phrases: List<String>): String = json(phrases + "[unk]")

    private fun json(list: List<String>): String =
        list.joinToString(",", "[", "]") { phrase ->
            "\"" + phrase.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }

    /** Drops Vosk's "[unk]" tokens and extra spaces. */
    fun clean(text: String): String = text.replace("[unk]", " ").replace(Regex("\\s+"), " ").trim()
}

/**
 * Roman Hindi spellings the English model may know (unit-tested). In grammar mode Vosk silently
 * drops every word its vocabulary lacks: with vosk-model-small-en-in-0.4 alone (no Hindi model),
 * a phrase like "gaana chalao" whose words are all missing vanishes from the grammar, and the
 * driver's "gaana chalao" can only come out as "[unk]". [adapt] swaps each missing word for the
 * first spelling of it the model does know ("gaana" → "gana"), and [restore] turns what was heard
 * back into the phrase [VoiceIntents] parses.
 */
object HinglishSpelling {
    val VARIANTS: Map<String, List<String>> = mapOf(
        "gaana" to listOf("gana", "ganna", "gaane", "gane", "ghana"),
        "gana" to listOf("gaana", "ganna", "ghana"),
        "chalao" to listOf("chalaao", "chalaw", "chala", "challa"),
        "chalo" to listOf("challo", "chalow", "chaalo"),
        "chal" to listOf("chaal", "chull"),
        "bajao" to listOf("bajaao", "bajaw", "baja"),
        "haan" to listOf("han", "haa", "hann"),
        "han" to listOf("haan", "hann"),
        "nahi" to listOf("nahin", "nahee", "nai"),
        "nahin" to listOf("nahi", "nahee"),
        "badhiya" to listOf("badiya", "badhia", "badia", "bariya"),
        "theek" to listOf("thik", "theak", "teek", "tik"),
        "karo" to listOf("kro", "caro", "karoo"),
        "kholo" to listOf("kolo", "khollo", "cholo"),
        "awaaz" to listOf("awaz", "awaj", "avaaz"),
        "awaz" to listOf("awaaz", "awaj"),
        "badhao" to listOf("badhaao", "badao", "barhao"),
        "ghar" to listOf("gar", "ghur"),
        "agla" to listOf("agala", "aagla"),
        "pichla" to listOf("pichhla", "pichala"),
        "roko" to listOf("rokko", "roco"),
        "ruko" to listOf("rukko", "ruco"),
        "band" to listOf("bund", "bandh"),
        "bas" to listOf("bus", "buss"),
        "sunao" to listOf("sunaao", "sunaw"),
        "batao" to listOf("bataao", "bataw"),
        "kahan" to listOf("kahaan", "kaha", "kahaa"),
        "hai" to listOf("hay", "hey", "hae"),
        "mast" to listOf("must", "musth"),
        "radd" to listOf("rad", "rudd"),
        "madad" to listOf("madat", "maddad"),
        "rasta" to listOf("raasta", "rastaa"),
        "dikhao" to listOf("dikhaao", "dikao"),
        "le" to listOf("lay", "lae"),
        "suna" to listOf("soona", "sunna", "sunaa"),
        "lagao" to listOf("lagaao", "lagau", "lagaw"),
        "laga" to listOf("lagaa", "lagga"),
        "gaane" to listOf("gane", "gaaney", "ganey"),
        "gane" to listOf("gaane", "ganey"),
        "bhajan" to listOf("bhajjan", "bajan", "bhajans"),
        "koi" to listOf("koee", "koy", "koyi"),
        "badha" to listOf("badhaa", "barha", "bada"),
        "kar" to listOf("karr", "kur"),
        "dheemi" to listOf("dheemee", "dhimi", "deemi"),
        "rok" to listOf("roak", "roke"),
        "tez" to listOf("tej", "taze"),
        "lambi" to listOf("lumbi", "lambhi", "lamby"),
        "khali" to listOf("khaali", "kali", "khalee"),
        "bharwaya" to listOf("bharvaya", "barwaya", "bharwaaya"),
        "dalwaya" to listOf("dalvaya", "dalwaaya"),
        "bharwayi" to listOf("bharvayi", "bharwai"),
        "naka" to listOf("nakka", "naaka"),
        "bachaya" to listOf("bachaaya", "bachya"),
        "challan" to listOf("chalan", "chaalan"),
        "jodo" to listOf("jodho", "jordo"),
        "khadi" to listOf("khari", "khaadi"),
        "gaadi" to listOf("gadi", "gaari", "gari"),
        // Music play verbs ("gana baja de", "gana chalu karo").
        "baja" to listOf("bajaa", "bajja", "bhaja"),
        "bajaa" to listOf("baja", "bajja"),
        "chala" to listOf("chalaa", "challa", "chalah"),
        "chalaa" to listOf("chala", "challa"),
        "sunaa" to listOf("suna", "sunna"),
        "lagaa" to listOf("laga", "lagga"),
        "de" to listOf("dey", "day", "dee"),
        "chalu" to listOf("chaalu", "chaloo", "chaaloo"),
        "shuru" to listOf("shuroo", "shoeroo", "suru"),
        "songs" to listOf("song"),
        // Talking with Ampli: the news, jokes, the day, thanks.
        "khabar" to listOf("khabbar", "kabar", "khaber"),
        "taaza" to listOf("taza", "taja", "tazaa"),
        "haal" to listOf("hal", "hall"),
        "samachar" to listOf("samaachar", "samachaar"),
        "shukriya" to listOf("shukriyaa", "shukria"),
        "dhanyawad" to listOf("dhanyavad", "dhanyawaad", "dhanyavaad"),
        "tareekh" to listOf("tarikh", "tareek"),
        "kaisa" to listOf("kaisaa", "kesa"),
        "chutkula" to listOf("chutkulla", "chutkala"),
        "mazedaar" to listOf("mazedar", "majedar"),
        "hasa" to listOf("hansa", "hassa"),
        "chup" to listOf("chupp", "choop"),
        "tham" to listOf("thaam", "thum"),
        "thamja" to listOf("thamjaa"),
        "aage" to listOf("aagey", "aghe"),
        "dheere" to listOf("dhere", "dheerey"),
        "baat" to listOf("baath", "bat"),
        "kaun" to listOf("kon", "kaon"),
        "naam" to listOf("nam", "naem"),
        "agli" to listOf("agali", "aagli"),
    )

    /** The grammar to give the model, and the spellings to turn back ([restore]). */
    data class Adapted(val phrases: List<String>, val back: Map<String, String>)

    /**
     * [phrases] with every word [known] lacks replaced by its first known variant. A variant that
     * is itself a word of another phrase is never used (turning it back would change that
     * phrase). Words with no known variant stay as they are (Vosk drops them, as before).
     */
    fun adapt(phrases: List<String>, known: (String) -> Boolean): Adapted {
        val own = phrases.flatMap { it.split(' ') }.filter { it.isNotBlank() }.toSet()
        val back = HashMap<String, String>()
        val out = phrases.map { phrase ->
            phrase.split(' ').joinToString(" ") { word ->
                if (word.isBlank() || known(word)) {
                    word
                } else {
                    val v = VARIANTS[word]?.firstOrNull { it !in own && (back[it] == null || back[it] == word) && known(it) }
                    if (v == null) {
                        word
                    } else {
                        back[v] = word
                        v
                    }
                }
            }
        }.distinct()
        return Adapted(out, back)
    }

    /** What was heard, with the swapped spellings turned back into the grammar's own words. */
    fun restore(text: String, back: Map<String, String>): String {
        if (back.isEmpty() || text.isEmpty()) return text
        return text.split(' ').joinToString(" ") { back[it] ?: it }
    }
}
