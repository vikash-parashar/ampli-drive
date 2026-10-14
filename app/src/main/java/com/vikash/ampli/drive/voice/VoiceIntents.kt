package com.vikash.ampli.drive.voice

import java.util.Locale

/** What the driver asked for, after the wake word. */
sealed interface VoiceIntent {
    /** [destination] may be empty ("navigate"), then the assistant asks where to. */
    data class Navigate(val destination: String) : VoiceIntent

    /** 0-based pick from the spoken list of places ("the second one" = 1). */
    data class ChooseOption(val index: Int) : VoiceIntent
    data object NextCamera : VoiceIntent

    /** Song from USB / SD / internal storage. Also plain "play <song>". */
    data class PlayLocal(val query: String) : VoiceIntent

    /** Music from the phone over Bluetooth; [query] is often empty. */
    data class PlayBluetooth(val query: String) : VoiceIntent

    /** "Play" / "play music" with nothing named: resume whatever was playing. */
    data object Resume : VoiceIntent
    data object Pause : VoiceIntent
    data object Next : VoiceIntent
    data object Previous : VoiceIntent
    data object OpenAndroidAuto : VoiceIntent
    data class CallContact(val name: String) : VoiceIntent

    /** "SOS", "help me", "emergency": opens the Help panel. Never sends anything by itself. */
    data object Sos : VoiceIntent
    data object Greeting : VoiceIntent

    /** Car questions: "how many km to next service", "tyre pressure", "engine status", "where did I park". */
    data object ServiceDue : VoiceIntent
    data object TyrePressure : VoiceIntent
    data object EngineStatus : VoiceIntent
    data object WhereParked : VoiceIntent

    /** "Report a problem", "send feedback": opens Help & feedback (the form waits until parked). */
    data object ReportProblem : VoiceIntent
    data object Cancel : VoiceIntent
    data class Unknown(val text: String) : VoiceIntent
}

/**
 * Rule-based parser, no network and no model. English and Hindi (Devanagari and Latin
 * "Hinglish") are always active, because drivers mix them whatever the app language is.
 * Other app languages add their own keyword set below; those sets are best-effort and
 * need native review like the strings files.
 */
object VoiceIntents {

    /**
     * @param language parser language from [VoiceLocales.parserLanguage] ("hi", "ta", "de"…).
     * @param expectingChoice true while a list of places is on screen; only then do bare
     *   numbers and ordinals count as a choice (otherwise "go to sector 2" would be one).
     */
    fun parse(text: String, language: String = "en", expectingChoice: Boolean = false): VoiceIntent {
        val kw = keywordsFor(language)
        val t = stripWakeWord(normalize(text))
        if (t.isEmpty()) return VoiceIntent.Unknown("")
        val words = t.split(' ').size

        if (expectingChoice && words <= 6) {
            ordinal(t, kw)?.let { return VoiceIntent.ChooseOption(it) }
        }
        if (kw.pause.any { has(t, it) }) return VoiceIntent.Pause
        if (words <= 4 && kw.cancel.any { has(t, it) }) return VoiceIntent.Cancel
        if (kw.sos.any { has(t, it) }) return VoiceIntent.Sos
        if (REPORT.any { has(t, it) }) return VoiceIntent.ReportProblem
        carQuestion(t)?.let { return it }
        if (kw.camera.any { has(t, it) }) return VoiceIntent.NextCamera
        if (kw.auto.any { has(t, it) }) return VoiceIntent.OpenAndroidAuto
        if (kw.next.any { has(t, it) } && (words <= 4 || kw.music.any { has(t, it) })) return VoiceIntent.Next
        if (kw.previous.any { has(t, it) } && (words <= 4 || kw.music.any { has(t, it) })) return VoiceIntent.Previous
        if (kw.resume.any { has(t, it) } && words <= 4) return VoiceIntent.Resume

        if (kw.play.any { has(t, it) }) {
            val local = kw.local.any { has(t, it) }
            val bt = !local && kw.bluetooth.any { has(t, it) }
            val rest = remove(t, kw.play + kw.local + kw.bluetooth)
            val query = trimFillers(remove(rest, kw.music), kw.fillers)
            return when {
                bt -> VoiceIntent.PlayBluetooth(query)
                query.isEmpty() && !local -> VoiceIntent.Resume
                else -> VoiceIntent.PlayLocal(query)
            }
        }
        if (kw.call.any { has(t, it) }) {
            return VoiceIntent.CallContact(trimFillers(remove(t, kw.call), kw.fillers))
        }
        if (kw.navigate.any { has(t, it) }) {
            return VoiceIntent.Navigate(trimFillers(remove(t, kw.navigate), kw.fillers + kw.placeFillers))
        }
        if (words <= 6 && kw.greeting.any { has(t, it) }) return VoiceIntent.Greeting
        return VoiceIntent.Unknown(text.trim())
    }

    /**
     * Car questions, English and Hindi (any app language). Checked before "navigate", because
     * "find my car" and "where is my car" would otherwise start a place search.
     */
    private fun carQuestion(t: String): VoiceIntent? = when {
        CAR_PARKED.any { has(t, it) } -> VoiceIntent.WhereParked
        CAR_SERVICE.any { has(t, it) } -> VoiceIntent.ServiceDue
        CAR_TYRES.any { has(t, it) } -> VoiceIntent.TyrePressure
        CAR_ENGINE.any { has(t, it) } -> VoiceIntent.EngineStatus
        else -> null
    }

    private val REPORT = listOf(
        "report a problem", "report problem", "report an issue", "report a bug", "report bug",
        "send feedback", "give feedback", "feedback", "complaint", "contact support",
        "problem report", "शिकायत", "समस्या बताओ", "समस्या बताना", "फीडबैक", "shikayat", "samasya batao",
    ).map { normalize(it) }

    private val CAR_PARKED = listOf(
        "where did i park", "where have i parked", "where is my car", "where s my car", "find my car",
        "my parked car", "parking spot", "where i parked",
        "गाड़ी कहाँ", "गाड़ी कहां", "गाडी कहाँ", "गाडी कहां", "कार कहाँ", "कार कहां", "कहाँ पार्क", "कहां पार्क",
        "gaadi kahan", "gadi kahan", "gaadi kaha", "gadi kaha", "car kahan", "car kaha", "kahan park", "kaha park",
    ).map { normalize(it) }

    private val CAR_SERVICE = listOf(
        "next service", "service due", "km to service", "kilometres to service", "kilometers to service",
        "when is the service", "when is service", "service kab", "agli service", "next servicing",
        "अगली सर्विस", "सर्विस कब", "सर्विसिंग कब",
    ).map { normalize(it) }

    private val CAR_TYRES = listOf(
        "tyre pressure", "tire pressure", "tyre pressures", "tire pressures", "check the tyres", "check tyres",
        "how are my tyres", "how are the tyres", "air in the tyres",
        "टायर प्रेशर", "टायर में हवा", "टायरों में हवा", "टायर कैसे", "tyre me hawa", "tyre mein hawa", "tayar me hawa",
    ).map { normalize(it) }

    private val CAR_ENGINE = listOf(
        "engine status", "engine health", "check engine", "engine light", "engine problem", "engine temperature",
        "how is the engine", "fault code", "fault codes", "obd",
        "इंजन कैसा", "इंजन की हालत", "इंजन स्टेटस", "इंजन ठीक", "engine kaisa", "engine ki halat", "engine theek",
    ).map { normalize(it) }

    /** The place name in a free answer such as "Phoenix Mall please" after "Where to?". */
    fun destinationFrom(text: String, language: String = "en"): String {
        val kw = keywordsFor(language)
        return trimFillers(remove(stripWakeWord(normalize(text)), kw.navigate), kw.fillers + kw.placeFillers)
    }

    // ------------------------------------------------------------------------------ Matching

    internal fun normalize(text: String): String = text
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\p{P}\\p{S}&&[^%]]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val wakeWords = listOf("hey", "hello", "hi", "ok", "okay", "हे", "हेलो", "हैलो")
    private val wakeNames = listOf("ampli", "amply", "ample", "ampl", "एम्पली", "एंप्ली", "ऐम्पली")

    /** Drops a leading "hey ampli" that the recogniser sometimes hears again. */
    private fun stripWakeWord(t: String): String {
        val words = t.split(' ').toMutableList()
        if (words.size >= 2 && words[0] in wakeWords && words[1] in wakeNames) {
            return words.drop(2).joinToString(" ")
        }
        if (words.isNotEmpty() && words[0] in wakeNames) return words.drop(1).joinToString(" ")
        return t
    }

    private fun isCjk(s: String) = s.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }

    private val regexCache = HashMap<String, Regex>()

    private fun phraseRegex(phrase: String): Regex = regexCache.getOrPut(phrase) {
        // Letters, combining marks (Indic vowel signs) and digits all count as "inside a word".
        Regex("(?<![\\p{L}\\p{M}\\p{N}])" + Regex.escape(phrase) + "(?![\\p{L}\\p{M}\\p{N}])")
    }

    private fun has(t: String, phrase: String): Boolean =
        if (isCjk(phrase)) t.contains(phrase) else phraseRegex(phrase).containsMatchIn(t)

    private fun remove(t: String, phrases: List<String>): String {
        var s = t
        for (p in phrases.sortedByDescending { it.length }) {
            s = if (isCjk(p)) s.replace(p, " ") else phraseRegex(p).replace(s, " ")
        }
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /** Removes filler words ("the", "मुझे", "से"…) from both ends only, so names keep them. */
    private fun trimFillers(t: String, fillers: List<String>): String {
        val set = fillers.toSet()
        val cjk = fillers.filter { isCjk(it) }
        var words = t.split(' ').filter { it.isNotEmpty() }
        while (words.isNotEmpty() && words.first() in set) words = words.drop(1)
        while (words.isNotEmpty() && words.last() in set) words = words.dropLast(1)
        var s = words.joinToString(" ")
        var changed = true
        while (changed && s.isNotEmpty()) {
            changed = false
            for (f in cjk) {
                if (s.startsWith(f) && s.length > f.length) { s = s.removePrefix(f).trim(); changed = true }
                if (s.endsWith(f) && s.length > f.length) { s = s.removeSuffix(f).trim(); changed = true }
            }
        }
        return s
    }

    /** The longest matching word wins, so "the second one" is 2 and not 1. */
    private fun ordinal(t: String, kw: Keywords): Int? {
        var best: Int? = null
        var bestLen = 0
        kw.ordinals.forEachIndexed { i, list ->
            for (w in list) {
                if (w.length > bestLen && has(t, w)) {
                    best = i
                    bestLen = w.length
                }
            }
        }
        return best
    }

    // ------------------------------------------------------------------------------ Keywords

    internal class Keywords(
        val navigate: List<String> = emptyList(),
        val play: List<String> = emptyList(),
        val resume: List<String> = emptyList(),
        val music: List<String> = emptyList(),
        val local: List<String> = emptyList(),
        val bluetooth: List<String> = emptyList(),
        val camera: List<String> = emptyList(),
        val pause: List<String> = emptyList(),
        val next: List<String> = emptyList(),
        val previous: List<String> = emptyList(),
        val cancel: List<String> = emptyList(),
        val sos: List<String> = emptyList(),
        val call: List<String> = emptyList(),
        val auto: List<String> = emptyList(),
        val greeting: List<String> = emptyList(),
        val fillers: List<String> = emptyList(),
        val placeFillers: List<String> = emptyList(),
        /** ordinals[0] = words for "first", ordinals[1] = "second"… */
        val ordinals: List<List<String>> = emptyList(),
    ) {
        /** Same clean-up as the recognised text, so "ष्ट'ৰেজ" or "let's" still match. */
        fun normalized(): Keywords {
            fun n(l: List<String>) = l.map { normalize(it) }.filter { it.isNotEmpty() }.distinct()
            return Keywords(
                n(navigate), n(play), n(resume), n(music), n(local), n(bluetooth), n(camera), n(pause),
                n(next), n(previous), n(cancel), n(sos), n(call), n(auto), n(greeting), n(fillers),
                n(placeFillers), ordinals.map { n(it) },
            )
        }

        operator fun plus(o: Keywords) = Keywords(
            navigate + o.navigate, play + o.play, resume + o.resume, music + o.music, local + o.local,
            bluetooth + o.bluetooth, camera + o.camera, pause + o.pause, next + o.next,
            previous + o.previous, cancel + o.cancel, sos + o.sos, call + o.call, auto + o.auto,
            greeting + o.greeting, fillers + o.fillers, placeFillers + o.placeFillers,
            List(maxOf(ordinals.size, o.ordinals.size)) { i ->
                ordinals.getOrElse(i) { emptyList() } + o.ordinals.getOrElse(i) { emptyList() }
            },
        )
    }

    private val cache = HashMap<String, Keywords>()

    internal fun keywordsFor(language: String): Keywords = cache.getOrPut(language) {
        val extra = OTHER[language]
        val merged = if (extra == null || language == "en" || language == "hi") EN + HI else EN + HI + extra
        merged.normalized()
    }

    private val EN = Keywords(
        navigate = listOf(
            "navigate to", "navigate", "take me to", "take me", "go to", "drive to", "directions to",
            "direction to", "route to", "let s go to", "lets go to", "i want to go to", "i want to go",
            "i need to go to", "i have to go to", "find", "search for", "search", "where is",
            "show me the way to", "how do i get to",
        ),
        play = listOf("play", "put on"),
        resume = listOf("resume", "continue music", "unpause"),
        music = listOf("song", "songs", "music", "track", "something", "some music"),
        local = listOf(
            "storage", "usb", "pen drive", "pendrive", "sd card", "memory card", "this unit",
            "offline", "local", "my files",
        ),
        bluetooth = listOf("bluetooth", "my phone", "the phone", "phone", "mobile"),
        camera = listOf("camera", "cameras", "speed trap", "speed check"),
        pause = listOf("pause", "stop music", "stop the music", "stop the song", "stop song", "stop playing"),
        next = listOf("next song", "next track", "skip", "next"),
        previous = listOf("previous song", "previous track", "previous", "last song", "play the last song"),
        cancel = listOf(
            "cancel", "stop", "never mind", "nevermind", "nothing", "no thanks", "forget it",
            "exit", "close", "bye", "quit", "that s all",
        ),
        sos = listOf("sos", "help me", "emergency", "i need help", "accident", "ambulance"),
        call = listOf("call", "dial", "ring"),
        auto = listOf(
            "android auto", "carplay", "car play", "phone link", "phonelink", "zlink", "z link",
            "autokit", "auto kit", "connect my phone", "connect phone", "connect the phone",
            "mirror my phone", "screen mirror",
        ),
        greeting = listOf(
            "hello", "hi", "hey", "good morning", "good afternoon", "good evening", "how are you",
            "i m fine", "i am fine", "i m good", "namaste",
        ),
        fillers = listOf(
            "the", "a", "an", "to", "from", "on", "my", "me", "please", "some", "by", "of", "in",
            "for", "now", "for me", "one", "song", "music",
        ),
        placeFillers = listOf("nearest", "nearby", "near me", "closest"),
        ordinals = listOf(
            listOf("first", "1st", "one", "1", "number one", "option one"),
            listOf("second", "2nd", "two", "2", "number two", "option two"),
            listOf("third", "3rd", "three", "3", "number three", "option three"),
            listOf("fourth", "4th", "four", "4"),
            listOf("fifth", "5th", "five", "5"),
        ),
    )

    private val HI = Keywords(
        navigate = listOf(
            "जाना है", "जाना", "ले चलो", "चलो", "ले चलें", "चलें", "पहुंचाओ", "पहुँचाओ", "रास्ता दिखाओ",
            "रास्ता", "नेविगेट करो", "नेविगेट", "jana hai", "jaana hai", "jana", "jaana", "le chalo",
            "chalo", "chalen", "rasta dikhao", "raasta dikhao", "rasta", "raasta", "pahunchao",
        ),
        play = listOf(
            "बजाओ", "बजा दो", "चलाओ", "चला दो", "सुनाओ", "लगाओ", "प्ले करो", "प्ले", "bajao", "baja do",
            "chalao", "chala do", "sunao", "lagao", "play karo",
        ),
        resume = listOf("गाना चालू करो", "फिर से चलाओ", "gaana chalu karo"),
        music = listOf("गाना", "गाने", "म्यूजिक", "म्यूज़िक", "संगीत", "gana", "gaana", "gane", "gaane", "sangeet"),
        local = listOf("स्टोरेज", "यूएसबी", "पेन ड्राइव", "पेनड्राइव", "मेमोरी", "memory"),
        bluetooth = listOf("ब्लूटूथ", "फ़ोन", "फोन", "मोबाइल"),
        camera = listOf("कैमरा", "कैमरे", "kamera"),
        pause = listOf(
            "गाना बंद", "गाना रोको", "म्यूजिक बंद", "म्यूज़िक बंद", "संगीत बंद", "gana band", "gaana band",
            "music band", "gana roko", "gaana roko", "music roko",
        ),
        next = listOf("अगला गाना", "अगला", "agla gana", "agla gaana", "agla"),
        previous = listOf("पिछला गाना", "पिछला", "pichla gana", "pichhla gaana", "pichla", "pichhla"),
        cancel = listOf(
            "रद्द करो", "रद्द", "कैंसल", "बस", "छोड़ो", "रहने दो", "कुछ नहीं", "बंद करो", "rehne do",
            "chhodo", "chodo", "bas", "kuch nahi", "band karo", "cancel karo",
        ),
        sos = listOf("मदद", "बचाओ", "आपातकाल", "इमरजेंसी", "एम्बुलेंस", "madad", "bachao"),
        call = listOf(
            "फ़ोन करो", "फोन करो", "कॉल करो", "कॉल लगाओ", "फोन लगाओ", "फ़ोन लगाओ", "phone karo",
            "call karo", "phone lagao", "call lagao",
        ),
        auto = listOf(
            "एंड्रॉयड ऑटो", "एंड्रॉइड ऑटो", "फ़ोन जोड़ो", "फोन जोड़ो", "फ़ोन कनेक्ट", "फोन कनेक्ट",
            "phone jodo", "phone connect karo",
        ),
        greeting = listOf("नमस्ते", "नमस्कार", "हैलो", "हेलो", "कैसे हो", "namaskar", "kaise ho"),
        fillers = listOf(
            "मुझे", "मेरे", "मेरा", "मेरी", "को", "पर", "में", "तक", "से", "के", "की", "का", "ज़रा", "जरा",
            "प्लीज़", "प्लीज", "वाला", "वाली", "वाले", "है", "करो", "mujhe", "mere", "mera", "meri", "ko",
            "par", "mein", "me", "tak", "se", "ke", "ki", "ka", "zara", "jara", "wala", "wali", "hai", "karo",
        ),
        placeFillers = listOf("पास", "नज़दीकी", "नजदीकी", "सबसे", "paas", "nazdeeki", "sabse"),
        ordinals = listOf(
            listOf("पहला", "पहली", "पहले", "एक", "१", "pehla", "pahla", "pehli", "pehle", "ek"),
            listOf("दूसरा", "दूसरी", "दूसरे", "दो", "२", "dusra", "doosra", "dusri", "doosri", "do"),
            listOf("तीसरा", "तीसरी", "तीसरे", "तीन", "३", "teesra", "tisra", "teesri", "teen"),
            listOf("चौथा", "चौथी", "चार", "४", "chautha", "char"),
            listOf("पांचवां", "पाँचवाँ", "पांच", "पाँच", "५", "panchva", "paanch", "panch"),
        ),
    )

    /** Best-effort sets for the other app languages. Native review needed. */
    private val OTHER: Map<String, Keywords> = mapOf(
        "bn" to Keywords(
            navigate = listOf("যেতে চাই", "যাব", "নিয়ে চলো", "চলো", "রাস্তা দেখাও"),
            play = listOf("বাজাও", "চালাও", "শোনাও"),
            music = listOf("গান"),
            local = listOf("স্টোরেজ", "ইউএসবি", "পেন ড্রাইভ"),
            bluetooth = listOf("ব্লুটুথ", "ফোন", "মোবাইল"),
            camera = listOf("ক্যামেরা"),
            pause = listOf("গান বন্ধ", "গান থামাও"),
            next = listOf("পরের গান", "পরেরটা"),
            previous = listOf("আগের গান"),
            cancel = listOf("বাতিল", "থাক", "বন্ধ করো"),
            sos = listOf("সাহায্য", "বাঁচাও", "জরুরি"),
            call = listOf("ফোন করো", "কল করো"),
            auto = listOf("অ্যান্ড্রয়েড অটো", "ফোন কানেক্ট"),
            greeting = listOf("নমস্কার", "হ্যালো"),
            fillers = listOf("আমি", "আমাকে", "আমার", "থেকে", "একটা"),
            ordinals = listOf(listOf("প্রথম", "প্রথমটা", "১"), listOf("দ্বিতীয়", "দুই", "২"), listOf("তৃতীয়", "তিন", "৩")),
        ),
        "ta" to Keywords(
            navigate = listOf("போக வேண்டும்", "போகணும்", "கூட்டிட்டு போ", "வழி காட்டு"),
            play = listOf("பாடு", "போடு", "இயக்கு"),
            music = listOf("பாடல்", "பாட்டு"),
            local = listOf("ஸ்டோரேஜ்", "யூஎஸ்பி", "பென் டிரைவ்"),
            bluetooth = listOf("ப்ளூடூத்", "போன்", "மொபைல்"),
            camera = listOf("கேமரா"),
            pause = listOf("பாட்டை நிறுத்து", "பாடலை நிறுத்து"),
            next = listOf("அடுத்த பாடல்", "அடுத்தது"),
            previous = listOf("முந்தைய பாடல்"),
            cancel = listOf("ரத்து", "வேண்டாம்", "நிறுத்து"),
            sos = listOf("உதவி", "அவசரம்", "காப்பாத்து"),
            call = listOf("கால் பண்ணு", "போன் பண்ணு"),
            auto = listOf("ஆண்ட்ராய்டு ஆட்டோ"),
            greeting = listOf("வணக்கம்", "ஹலோ"),
            fillers = listOf("எனக்கு", "என்", "என்னுடைய", "இருந்து"),
            ordinals = listOf(listOf("முதல்", "முதலாவது", "ஒன்று"), listOf("இரண்டாவது", "இரண்டு", "ரெண்டு"), listOf("மூன்றாவது", "மூன்று", "மூணு")),
        ),
        "te" to Keywords(
            navigate = listOf("వెళ్ళాలి", "తీసుకెళ్ళు", "వెళ్దాం", "దారి చూపించు"),
            play = listOf("ప్లే చేయి", "వినిపించు", "పెట్టు"),
            music = listOf("పాట"),
            local = listOf("స్టోరేజ్", "యూఎస్బీ", "పెన్ డ్రైవ్"),
            bluetooth = listOf("బ్లూటూత్", "ఫోన్", "మొబైల్"),
            camera = listOf("కెమెరా"),
            pause = listOf("పాట ఆపు"),
            next = listOf("తదుపరి పాట", "తర్వాతి పాట"),
            previous = listOf("మునుపటి పాట"),
            cancel = listOf("రద్దు", "వద్దు", "ఆపు"),
            sos = listOf("సహాయం", "కాపాడండి", "అత్యవసరం"),
            call = listOf("కాల్ చేయి", "ఫోన్ చేయి"),
            auto = listOf("ఆండ్రాయిడ్ ఆటో"),
            greeting = listOf("నమస్కారం", "హలో"),
            fillers = listOf("నాకు", "నా", "నుండి"),
            ordinals = listOf(listOf("మొదటి", "మొదటిది", "ఒకటి"), listOf("రెండవ", "రెండవది", "రెండు"), listOf("మూడవ", "మూడవది", "మూడు")),
        ),
        "mr" to Keywords(
            navigate = listOf("जायचं आहे", "जायचे आहे", "जायचंय", "घेऊन चल", "चल", "रस्ता दाखव"),
            play = listOf("वाजव", "लाव", "चालू कर"),
            music = listOf("गाणं", "गाणे"),
            local = listOf("स्टोरेज", "यूएसबी", "पेन ड्राइव्ह"),
            bluetooth = listOf("ब्लूटूथ", "मोबाईल"),
            camera = listOf("कॅमेरा", "कॅमेरे"),
            pause = listOf("गाणं बंद", "गाणे बंद", "गाणं थांबव"),
            next = listOf("पुढचं गाणं", "पुढचे गाणे", "पुढचं"),
            previous = listOf("मागचं गाणं", "मागचे गाणे"),
            cancel = listOf("रद्द", "नको", "राहू दे", "बंद कर"),
            sos = listOf("मदत", "वाचवा", "आणीबाणी"),
            call = listOf("फोन लाव", "कॉल कर"),
            auto = listOf("अँड्रॉइड ऑटो"),
            greeting = listOf("नमस्कार", "हॅलो"),
            fillers = listOf("मला", "माझ्या", "माझे", "ला", "वर", "मधून", "वरून"),
            ordinals = listOf(listOf("पहिला", "पहिले", "पहिली"), listOf("दुसरा", "दुसरे", "दुसरी", "दोन"), listOf("तिसरा", "तिसरे", "तिसरी")),
        ),
        "gu" to Keywords(
            navigate = listOf("જવું છે", "લઈ જા", "ચાલો", "રસ્તો બતાવ"),
            play = listOf("વગાડો", "વગાડ", "ચલાવો"),
            music = listOf("ગીત"),
            local = listOf("સ્ટોરેજ", "યુએસબી", "પેન ડ્રાઇવ"),
            bluetooth = listOf("બ્લૂટૂથ", "ફોન", "મોબાઇલ"),
            camera = listOf("કેમેરા"),
            pause = listOf("ગીત બંધ", "ગીત રોકો"),
            next = listOf("આગલું ગીત", "પછીનું ગીત"),
            previous = listOf("પાછલું ગીત"),
            cancel = listOf("રદ", "રહેવા દો", "બંધ કર"),
            sos = listOf("મદદ", "બચાવો", "ઇમરજન્સી"),
            call = listOf("ફોન કર", "કૉલ કર"),
            auto = listOf("એન્ડ્રોઇડ ઓટો"),
            greeting = listOf("નમસ્તે", "હેલો"),
            fillers = listOf("મને", "મારા", "મારે", "થી", "માં", "પર"),
            ordinals = listOf(listOf("પહેલું", "પહેલો", "પહેલી", "એક"), listOf("બીજું", "બીજો", "બીજી", "બે"), listOf("ત્રીજું", "ત્રીજો", "ત્રીજી", "ત્રણ")),
        ),
        "kn" to Keywords(
            navigate = listOf("ಹೋಗಬೇಕು", "ಕರೆದುಕೊಂಡು ಹೋಗು", "ಹೋಗೋಣ", "ದಾರಿ ತೋರಿಸು"),
            play = listOf("ಪ್ಲೇ ಮಾಡು", "ಹಾಕು", "ಕೇಳಿಸು"),
            music = listOf("ಹಾಡು"),
            local = listOf("ಸ್ಟೋರೇಜ್", "ಯುಎಸ್‌ಬಿ", "ಪೆನ್ ಡ್ರೈವ್"),
            bluetooth = listOf("ಬ್ಲೂಟೂತ್", "ಫೋನ್", "ಮೊಬೈಲ್"),
            camera = listOf("ಕ್ಯಾಮೆರಾ"),
            pause = listOf("ಹಾಡು ನಿಲ್ಲಿಸು"),
            next = listOf("ಮುಂದಿನ ಹಾಡು"),
            previous = listOf("ಹಿಂದಿನ ಹಾಡು"),
            cancel = listOf("ರದ್ದು", "ಬೇಡ", "ನಿಲ್ಲಿಸು"),
            sos = listOf("ಸಹಾಯ", "ಕಾಪಾಡಿ", "ತುರ್ತು"),
            call = listOf("ಕರೆ ಮಾಡು", "ಫೋನ್ ಮಾಡು"),
            auto = listOf("ಆಂಡ್ರಾಯ್ಡ್ ಆಟೋ"),
            greeting = listOf("ನಮಸ್ಕಾರ", "ಹಲೋ"),
            fillers = listOf("ನನಗೆ", "ನನ್ನ"),
            ordinals = listOf(listOf("ಮೊದಲ", "ಮೊದಲನೇ", "ಒಂದು"), listOf("ಎರಡನೇ", "ಎರಡು"), listOf("ಮೂರನೇ", "ಮೂರು")),
        ),
        "ml" to Keywords(
            navigate = listOf("പോകണം", "കൊണ്ടുപോകൂ", "പോകാം", "വഴി കാണിക്കൂ"),
            play = listOf("പ്ലേ ചെയ്യൂ", "പാടൂ", "ഇടൂ"),
            music = listOf("പാട്ട്"),
            local = listOf("സ്റ്റോറേജ്", "യുഎസ്ബി", "പെൻ ഡ്രൈവ്"),
            bluetooth = listOf("ബ്ലൂടൂത്ത്", "ഫോൺ", "മൊബൈൽ"),
            camera = listOf("ക്യാമറ"),
            pause = listOf("പാട്ട് നിർത്തൂ"),
            next = listOf("അടുത്ത പാട്ട്"),
            previous = listOf("മുമ്പത്തെ പാട്ട്"),
            cancel = listOf("റദ്ദാക്കൂ", "വേണ്ട", "നിർത്തൂ"),
            sos = listOf("സഹായം", "രക്ഷിക്കൂ", "അടിയന്തരം"),
            call = listOf("വിളിക്കൂ", "കോൾ ചെയ്യൂ"),
            auto = listOf("ആൻഡ്രോയിഡ് ഓട്ടോ"),
            greeting = listOf("നമസ്കാരം", "ഹലോ"),
            fillers = listOf("എനിക്ക്", "എന്റെ", "നിന്ന്"),
            ordinals = listOf(listOf("ഒന്നാമത്തെ", "ആദ്യത്തെ", "ഒന്ന്"), listOf("രണ്ടാമത്തെ", "രണ്ട്"), listOf("മൂന്നാമത്തെ", "മൂന്ന്")),
        ),
        "pa" to Keywords(
            navigate = listOf("ਜਾਣਾ ਹੈ", "ਲੈ ਚੱਲੋ", "ਚੱਲੋ", "ਰਸਤਾ ਦਿਖਾਓ"),
            play = listOf("ਵਜਾਓ", "ਚਲਾਓ", "ਲਗਾਓ"),
            music = listOf("ਗੀਤ", "ਗਾਣਾ"),
            local = listOf("ਸਟੋਰੇਜ", "ਯੂਐਸਬੀ", "ਪੈਨ ਡਰਾਈਵ"),
            bluetooth = listOf("ਬਲੂਟੁੱਥ", "ਫ਼ੋਨ", "ਫੋਨ", "ਮੋਬਾਈਲ"),
            camera = listOf("ਕੈਮਰਾ"),
            pause = listOf("ਗੀਤ ਬੰਦ", "ਗਾਣਾ ਬੰਦ"),
            next = listOf("ਅਗਲਾ ਗੀਤ", "ਅਗਲਾ ਗਾਣਾ"),
            previous = listOf("ਪਿਛਲਾ ਗੀਤ", "ਪਿਛਲਾ ਗਾਣਾ"),
            cancel = listOf("ਰੱਦ", "ਰਹਿਣ ਦਿਓ", "ਬੰਦ ਕਰੋ"),
            sos = listOf("ਮਦਦ", "ਬਚਾਓ", "ਐਮਰਜੈਂਸੀ"),
            call = listOf("ਫ਼ੋਨ ਕਰੋ", "ਕਾਲ ਕਰੋ"),
            auto = listOf("ਐਂਡਰਾਇਡ ਆਟੋ"),
            greeting = listOf("ਸਤ ਸ੍ਰੀ ਅਕਾਲ", "ਹੈਲੋ"),
            fillers = listOf("ਮੈਨੂੰ", "ਮੇਰੇ", "ਤੋਂ", "ਨੂੰ"),
            ordinals = listOf(listOf("ਪਹਿਲਾ", "ਪਹਿਲੀ", "ਇੱਕ"), listOf("ਦੂਜਾ", "ਦੂਜੀ", "ਦੋ"), listOf("ਤੀਜਾ", "ਤੀਜੀ", "ਤਿੰਨ")),
        ),
        "ur" to Keywords(
            navigate = listOf("جانا ہے", "لے چلو", "چلو", "راستہ دکھاؤ"),
            play = listOf("بجاؤ", "چلاؤ", "لگاؤ"),
            music = listOf("گانا", "گانے"),
            local = listOf("اسٹوریج", "یو ایس بی", "پین ڈرائیو"),
            bluetooth = listOf("بلوٹوتھ", "فون", "موبائل"),
            camera = listOf("کیمرہ", "کیمرا"),
            pause = listOf("گانا بند", "گانا روکو"),
            next = listOf("اگلا گانا", "اگلا"),
            previous = listOf("پچھلا گانا"),
            cancel = listOf("منسوخ", "رہنے دو", "بند کرو", "بس"),
            sos = listOf("مدد", "بچاؤ", "ایمرجنسی"),
            call = listOf("فون کرو", "کال کرو"),
            auto = listOf("اینڈرائیڈ آٹو"),
            greeting = listOf("السلام علیکم", "ہیلو", "آداب"),
            fillers = listOf("مجھے", "میرے", "سے", "کو", "پر"),
            ordinals = listOf(listOf("پہلا", "پہلی", "ایک"), listOf("دوسرا", "دوسری", "دو"), listOf("تیسرا", "تیسری", "تین")),
        ),
        "or" to Keywords(
            navigate = listOf("ଯିବାକୁ ଅଛି", "ନେଇ ଚାଲ", "ଚାଲ", "ରାସ୍ତା ଦେଖାଅ"),
            play = listOf("ବଜାଅ", "ଚଲାଅ"),
            music = listOf("ଗୀତ"),
            local = listOf("ଷ୍ଟୋରେଜ", "ୟୁଏସବି"),
            bluetooth = listOf("ବ୍ଲୁଟୁଥ", "ଫୋନ", "ମୋବାଇଲ"),
            camera = listOf("କ୍ୟାମେରା"),
            pause = listOf("ଗୀତ ବନ୍ଦ"),
            next = listOf("ପରବର୍ତ୍ତୀ ଗୀତ"),
            previous = listOf("ପୂର୍ବ ଗୀତ"),
            cancel = listOf("ବାତିଲ", "ଥାଉ", "ବନ୍ଦ କର"),
            sos = listOf("ସାହାଯ୍ୟ", "ବଞ୍ଚାଅ", "ଜରୁରୀ"),
            call = listOf("ଫୋନ କର", "କଲ କର"),
            auto = listOf("ଆଣ୍ଡ୍ରଏଡ ଅଟୋ"),
            greeting = listOf("ନମସ୍କାର", "ହେଲୋ"),
            fillers = listOf("ମୋତେ", "ମୋର"),
            ordinals = listOf(listOf("ପ୍ରଥମ", "ଏକ"), listOf("ଦ୍ୱିତୀୟ", "ଦୁଇ"), listOf("ତୃତୀୟ", "ତିନି")),
        ),
        "as" to Keywords(
            navigate = listOf("যাব লাগে", "লৈ যোৱা", "বাট দেখুওৱা"),
            play = listOf("বজোৱা", "চলোৱা"),
            music = listOf("গান"),
            local = listOf("ষ্ট'ৰেজ", "ইউএছবি"),
            bluetooth = listOf("ব্লুটুথ", "ফোন", "মোবাইল"),
            camera = listOf("কেমেৰা"),
            pause = listOf("গান বন্ধ"),
            next = listOf("পিছৰ গান"),
            previous = listOf("আগৰ গান"),
            cancel = listOf("বাতিল", "নালাগে", "বন্ধ কৰা"),
            sos = listOf("সহায়", "বচাওক", "জৰুৰী"),
            call = listOf("ফোন কৰা", "কল কৰা"),
            auto = listOf("এণ্ড্ৰইড অটো"),
            greeting = listOf("নমস্কাৰ", "হেলো"),
            fillers = listOf("মোক", "মোৰ"),
            ordinals = listOf(listOf("প্ৰথম", "এক"), listOf("দ্বিতীয়", "দুই"), listOf("তৃতীয়", "তিনি")),
        ),
        "ne" to Keywords(
            navigate = listOf("जानु छ", "लैजाऊ", "जाऔं", "बाटो देखाऊ"),
            play = listOf("बजाऊ", "चलाऊ", "लगाऊ"),
            music = listOf("गीत"),
            local = listOf("स्टोरेज", "युएसबी", "पेन ड्राइभ"),
            bluetooth = listOf("ब्लुटुथ", "मोबाइल"),
            camera = listOf("क्यामेरा"),
            pause = listOf("गीत बन्द", "गीत रोक"),
            next = listOf("अर्को गीत", "अर्को"),
            previous = listOf("अघिल्लो गीत"),
            cancel = listOf("रद्द", "पर्दैन", "बन्द गर"),
            sos = listOf("मद्दत", "बचाऊ", "आपतकाल"),
            call = listOf("फोन गर", "कल गर"),
            auto = listOf("एन्ड्रोइड अटो"),
            greeting = listOf("नमस्ते", "नमस्कार"),
            fillers = listOf("मलाई", "मेरो", "बाट", "मा"),
            ordinals = listOf(listOf("पहिलो"), listOf("दोस्रो", "दुई"), listOf("तेस्रो")),
        ),
        "de" to Keywords(
            navigate = listOf(
                "navigiere zu", "navigiere nach", "navigiere", "bring mich zu", "bring mich zum",
                "bring mich zur", "bring mich nach", "fahre zu", "fahre nach", "fahr zu", "fahr nach",
                "route zu", "route nach", "ich will zu", "ich will nach", "ich möchte nach", "ich möchte zu",
            ),
            play = listOf("spiele", "spiel", "abspielen"),
            resume = listOf("weiterspielen", "fortsetzen"),
            music = listOf("lied", "musik", "titel"),
            local = listOf("speicher", "usb", "usb stick", "sd karte"),
            bluetooth = listOf("handy", "telefon"),
            camera = listOf("blitzer", "kamera", "radarfalle"),
            pause = listOf("musik stoppen", "musik aus", "pausieren"),
            next = listOf("nächstes lied", "nächster titel", "weiter"),
            previous = listOf("vorheriges lied", "voriges lied", "zurück"),
            cancel = listOf("abbrechen", "stopp", "vergiss es", "nichts"),
            sos = listOf("hilfe", "notfall"),
            call = listOf("ruf", "rufe", "anrufen"),
            auto = listOf("handy verbinden", "telefon verbinden"),
            greeting = listOf("hallo", "guten morgen", "guten tag", "guten abend", "wie geht s"),
            fillers = listOf("zu", "zum", "zur", "nach", "von", "vom", "meinem", "meiner", "mein", "das", "die", "der", "den", "dem", "bitte", "an"),
            placeFillers = listOf("nächste", "nächsten", "in der nähe"),
            ordinals = listOf(
                listOf("erste", "ersten", "erster", "eins"), listOf("zweite", "zweiten", "zweiter", "zwei"),
                listOf("dritte", "dritten", "dritter", "drei"), listOf("vierte", "vier"), listOf("fünfte", "fünf"),
            ),
        ),
        "fr" to Keywords(
            navigate = listOf(
                "aller à", "aller a", "emmène moi à", "emmène moi", "conduis moi à", "conduis moi",
                "itinéraire vers", "itinéraire pour", "navigue vers", "je veux aller à", "je veux aller",
            ),
            play = listOf("joue", "jouer", "mets", "lance"),
            resume = listOf("reprends", "reprendre"),
            music = listOf("chanson", "musique", "morceau"),
            local = listOf("stockage", "usb", "clé usb", "carte sd"),
            bluetooth = listOf("téléphone", "portable"),
            camera = listOf("radar", "caméra", "radars"),
            pause = listOf("arrête la musique", "coupe la musique"),
            next = listOf("chanson suivante", "suivant", "piste suivante"),
            previous = listOf("chanson précédente", "précédent"),
            cancel = listOf("annuler", "annule", "arrête", "laisse tomber", "rien"),
            sos = listOf("au secours", "aide moi", "urgence"),
            call = listOf("appelle", "appeler"),
            auto = listOf("connecter mon téléphone", "connecte mon téléphone"),
            greeting = listOf("bonjour", "salut", "bonsoir", "ça va"),
            fillers = listOf("à", "au", "aux", "la", "le", "les", "l", "de", "du", "des", "depuis", "mon", "ma", "mes", "moi", "s il te plaît", "stp"),
            placeFillers = listOf("le plus proche", "la plus proche", "près d ici"),
            ordinals = listOf(
                listOf("premier", "première", "un", "une"), listOf("deuxième", "second", "seconde", "deux"),
                listOf("troisième", "trois"), listOf("quatrième", "quatre"), listOf("cinquième", "cinq"),
            ),
        ),
        "zh" to Keywords(
            navigate = listOf("导航到", "导航去", "带我去", "我要去", "我想去", "去"),
            play = listOf("播放", "放一首", "放"),
            resume = listOf("继续播放"),
            music = listOf("歌", "音乐", "歌曲"),
            local = listOf("存储", "u盘", "本地"),
            bluetooth = listOf("蓝牙", "手机"),
            camera = listOf("摄像头", "测速", "电子眼"),
            pause = listOf("暂停", "停止音乐"),
            next = listOf("下一首", "下一曲"),
            previous = listOf("上一首", "上一曲"),
            cancel = listOf("取消", "算了", "停止"),
            sos = listOf("救命", "紧急", "求救"),
            call = listOf("打电话给", "打给", "呼叫"),
            auto = listOf("安卓汽车", "连接手机"),
            greeting = listOf("你好", "早上好", "下午好", "晚上好"),
            fillers = listOf("的", "我", "吧", "一下", "从", "里", "请"),
            placeFillers = listOf("最近的", "附近的"),
            ordinals = listOf(listOf("第一"), listOf("第二"), listOf("第三"), listOf("第四"), listOf("第五")),
        ),
    )
}
