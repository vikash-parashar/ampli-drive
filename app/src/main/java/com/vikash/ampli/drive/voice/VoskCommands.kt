package com.vikash.ampli.drive.voice

/** The phrases the offline recogniser listens for. Each must parse in [VoiceIntents] (unit-tested). */
object VoskCommands {
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
        // Power keys by voice (Restart and Power off always ask first).
        "screen off", "turn off the screen", "restart", "reboot", "power off", "shut down",
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
        "screen band karo", "screen off karo", "display band karo", "restart karo", "power off karo",
        "gaadi ki screen band karo", "radd karo",
    )

    /** Only right after a volume change ("aur" / "more"); see VoiceIntents.volumeFollowUp. */
    val FOLLOW_UPS = listOf("more", "a bit more", "again", "aur", "thoda aur", "aur tez", "aur kam")
    val HINDI_FOLLOW_UPS = listOf("और", "थोड़ा और", "और तेज", "और कम")

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
        // Power keys.
        "स्क्रीन बंद करो", "स्क्रीन ऑफ करो", "रीस्टार्ट करो", "पावर ऑफ करो", "गाड़ी की स्क्रीन बंद करो",
    )

    val HINDI_CHOICES = listOf("पहला", "दूसरा", "तीसरा", "पहली", "दूसरी", "तीसरी", "एक", "दो", "तीन")

    /**
     * Short answers to Ampli's questions (incoming call "uthaun?", "suna doon?", "bhej doon?",
     * restart "pakka?"), for the English model. A small grammar of its own, so a "haan" said over
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
        // Stop / cancel (the restart question, the screen-off countdown).
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

    private fun json(list: List<String>): String =
        list.joinToString(",", "[", "]") { phrase ->
            "\"" + phrase.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }

    /** Drops Vosk's "[unk]" tokens and extra spaces. */
    fun clean(text: String): String = text.replace("[unk]", " ").replace(Regex("\\s+"), " ").trim()
}
