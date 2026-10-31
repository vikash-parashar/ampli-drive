package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.cameras.ReportKind
import com.vikash.ampli.drive.fuel.FuelFilter
import com.vikash.ampli.drive.nav.SavedKind
import java.util.Locale

/** What the driver asked for, after the wake word. */
sealed interface VoiceIntent {
    /** [destination] may be empty ("navigate"), then the assistant asks where to. */
    data class Navigate(val destination: String) : VoiceIntent

    /** "Ghar chalo", "go home", "office le chalo": the saved Home or Office (never a favourite). */
    data class GoSaved(val kind: SavedKind) : VoiceIntent

    /** 0-based pick from the spoken list of places ("the second one" = 1). */
    data class ChooseOption(val index: Int) : VoiceIntent
    data object NextCamera : VoiceIntent

    /** "Nearest CNG pump", "petrol pump kahan hai", "charging station": the offline fuel finder. */
    data class FindFuel(val filter: FuelFilter) : VoiceIntent

    /** "Police aage hai", "accident ahead", "आगे जाम है": a road report at the car's spot (Pro). */
    data class ReportRoad(val kind: ReportKind) : VoiceIntent

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

    /** Dashcam (Pro): "save kar" / "video save karo" locks the last clip. */
    data object DashcamSave : VoiceIntent
    data object Greeting : VoiceIntent

    /** Small talk: "के हाल सै?", "कौन सै तू?", "चुटकला सुणा". */
    data class SmallTalk(val kind: Talk) : VoiceIntent

    enum class Talk { HowAreYou, WhoAreYou, Joke }

    /** Car questions: "how many km to next service", "tyre pressure", "engine status", "where did I park". */
    data object ServiceDue : VoiceIntent
    data object TyrePressure : VoiceIntent
    data object EngineStatus : VoiceIntent
    data object WhereParked : VoiceIntent

    /** "Report a problem", "send feedback": opens Help & feedback (the form waits until parked). */
    data object ReportProblem : VoiceIntent

    /** "Volume up", "awaaz badhao", "volume 5", "mute". [level] only for [VolumeAction.Set]. */
    data class Volume(val action: VolumeAction, val level: Int? = null) : VoiceIntent

    enum class VolumeAction { Up, Down, Max, Mute, Unmute, Set, Show }

    /**
     * A bare "stop", "ruko", "band karo": pauses the music when something is playing, otherwise
     * ends the conversation like [Cancel]. "Stop music" is [Pause]; "stop navigation" is
     * [StopNavigation].
     */
    data object Stop : VoiceIntent
    data object StopNavigation : VoiceIntent

    /** "What is the time", "kitne baje hain"; "aaj kya date hai". */
    data object TimeNow : VoiceIntent
    data object DateToday : VoiceIntent

    /** "How is the weather", "mausam kaisa hai"; [rain] for "baarish hogi kya". */
    data class Weather(val rain: Boolean) : VoiceIntent

    /** "What's my speed", "speed kitni hai". */
    data object Speed : VoiceIntent

    /** "Open maps", "map kholo". */
    data object OpenMaps : VoiceIntent

    /** "Brightness up / down"; [up] null for a bare "brightness" (shows the level). */
    data class Brightness(val up: Boolean?) : VoiceIntent

    /** "Bluetooth settings", "Bluetooth kholo". */
    data object BluetoothSettings : VoiceIntent

    /** "What can I say?", "kya kya bol sakte hain": the full command list. */
    data object WhatCanISay : VoiceIntent
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
        if (STOP_NAV.any { has(t, it) }) return VoiceIntent.StopNavigation
        if (kw.pause.any { has(t, it) }) return VoiceIntent.Pause
        val playWord = kw.play.any { has(t, it) }
        if (!playWord) volume(t)?.let { return it }
        if (trimFillers(t, STOP_FILLERS) in STOP_EXACT) return VoiceIntent.Stop
        if (words <= 4 && kw.cancel.any { has(t, it) }) return VoiceIntent.Cancel
        // "Accident ahead" is a road report; "accident", "help me", "ambulance" alone stay SOS.
        if (!playWord && REPORT_ACCIDENT.any { has(t, it) } && AHEAD.any { has(t, it) } && SOS_STRONG.none { has(t, it) }) {
            roadReport(t, kw)?.let { return it }
        }
        if (kw.sos.any { has(t, it) }) return VoiceIntent.Sos
        if (!playWord && DASHCAM_SAVE.any { has(t, it) }) return VoiceIntent.DashcamSave
        if (REPORT.any { has(t, it) }) return VoiceIntent.ReportProblem
        if (!playWord) {
            roadReport(t, kw)?.let { return it }
            fuel(t)?.let { return it }
        }
        if (WHAT_CAN_I_SAY.any { has(t, it) }) return VoiceIntent.WhatCanISay
        carQuestion(t)?.let { return it }
        // "Barish ke gaane bajao" is music and "take me to the weather station" a place.
        if (!(playWord && kw.music.any { has(t, it) }) && kw.navigate.none { has(t, it) }) infoQuestion(t)?.let { return it }
        if (words <= 8) smallTalk(t)?.let { return it }
        if (kw.camera.any { has(t, it) }) return VoiceIntent.NextCamera
        if (SPEED.any { has(t, it) }) return VoiceIntent.Speed
        if (!playWord) screenCommand(t)?.let { return it }
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
        if (words <= 6) savedPhrase(t, kw.fillers + kw.placeFillers)?.let { return VoiceIntent.GoSaved(it) }
        if (kw.navigate.any { has(t, it) }) {
            val dest = trimFillers(remove(t, kw.navigate), kw.fillers + kw.placeFillers)
            savedKind(dest)?.let { return VoiceIntent.GoSaved(it) }
            return VoiceIntent.Navigate(dest)
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

    // --------------------------------------------------------------- Round 7: volume, time, weather

    /**
     * Volume, English / Hindi / Hinglish in any app language: "volume up", "awaaz badhao",
     * "volume full", "mute", "unmute", "volume 5", "आवाज़ पाँच कर दो". A bare "volume" shows the
     * level (and waits for "up" / "down" / "more"). Null when it isn't about volume.
     */
    private fun volume(t: String): VoiceIntent? {
        if (UNMUTE.any { has(t, it) }) return VoiceIntent.Volume(VoiceIntent.VolumeAction.Unmute)
        if (MUTE.any { has(t, it) }) return VoiceIntent.Volume(VoiceIntent.VolumeAction.Mute)
        val noun = VOLUME_NOUN.any { has(t, it) }
        if (!noun) {
            if (LOUDER.any { has(t, it) }) return VoiceIntent.Volume(VoiceIntent.VolumeAction.Up)
            if (QUIETER.any { has(t, it) }) return VoiceIntent.Volume(VoiceIntent.VolumeAction.Down)
            return null
        }
        // Digits first ("volume up to 8" is 8), then the direction, then a number word, so
        // "volume up kar do" stays "up".
        digitsIn(t)?.let { return VoiceIntent.Volume(VoiceIntent.VolumeAction.Set, it) }
        return when {
            FULL.any { has(t, it) } -> VoiceIntent.Volume(VoiceIntent.VolumeAction.Max)
            DOWN.any { has(t, it) } -> VoiceIntent.Volume(VoiceIntent.VolumeAction.Down)
            UP.any { has(t, it) } -> VoiceIntent.Volume(VoiceIntent.VolumeAction.Up)
            else -> numberWordIn(t)?.let { VoiceIntent.Volume(VoiceIntent.VolumeAction.Set, it) }
                ?: VoiceIntent.Volume(VoiceIntent.VolumeAction.Show)
        }
    }

    /**
     * Right after a volume change Ampli listens once more: "aur" / "more" / "thoda aur" repeats
     * [last] (up or down), "aur kam" / "aur tez" goes that way. Null for anything else.
     */
    fun volumeFollowUp(text: String, last: VoiceIntent.VolumeAction): VoiceIntent.VolumeAction? {
        val t = stripWakeWord(normalize(text))
        if (t.isEmpty()) return null
        if (DOWN.any { has(t, it) } || QUIETER.any { has(t, it) }) return VoiceIntent.VolumeAction.Down
        if (UP.any { has(t, it) } || LOUDER.any { has(t, it) }) return VoiceIntent.VolumeAction.Up
        if (MORE.any { has(t, it) } && (last == VoiceIntent.VolumeAction.Up || last == VoiceIntent.VolumeAction.Down)) return last
        return null
    }

    /** "What time is it", "kitne baje", "aaj kya date hai", "mausam kaisa hai", "baarish hogi kya". */
    private fun infoQuestion(t: String): VoiceIntent? = when {
        DATE.any { has(t, it) } -> VoiceIntent.DateToday
        TIME.any { has(t, it) } -> VoiceIntent.TimeNow
        RAIN.any { has(t, it) } -> VoiceIntent.Weather(rain = true)
        WEATHER.any { has(t, it) } -> VoiceIntent.Weather(rain = false)
        else -> null
    }

    /** "Open maps", "brightness up", "Bluetooth settings". */
    private fun screenCommand(t: String): VoiceIntent? = when {
        OPEN_MAPS.any { has(t, it) } -> VoiceIntent.OpenMaps
        BRIGHTNESS.any { has(t, it) } -> VoiceIntent.Brightness(
            when {
                has(t, "dimmer") || DOWN.any { has(t, it) } || QUIETER.any { has(t, it) } -> false
                has(t, "brighter") || UP.any { has(t, it) } || FULL.any { has(t, it) } -> true
                else -> null
            },
        )
        BT_SETTINGS.any { has(t, it) } -> VoiceIntent.BluetoothSettings
        else -> null
    }

    /** A spoken level in digits ("5", "५"), 0 to 100. */
    internal fun digitsIn(t: String): Int? {
        for (w in t.split(' ')) {
            val digits = w.map { c -> if (c in '०'..'९') '0' + (c - '०') else c }.joinToString("")
            digits.toIntOrNull()?.let { if (it in 0..100) return it }
        }
        return null
    }

    /**
     * A number word up to a hundred, English or Hindi; the longest match wins. Hindi "do" / "दो"
     * (two) is left out: it is also the "kar do" in every request.
     */
    internal fun numberWordIn(t: String): Int? {
        var best: Int? = null
        var bestLen = 0
        for ((word, n) in NUMBER_WORDS) {
            if (word.length > bestLen && has(t, word)) {
                best = n
                bestLen = word.length
            }
        }
        return best
    }

    /**
     * Recognisers write Hindi letters with a dot (ज़, फ़, ड़) either as one character or as the letter
     * plus a nukta sign, and often drop the dot. The round-7 phrases match all three spellings.
     */
    internal fun nuktaForms(phrase: String): List<String> {
        val decomposed = java.text.Normalizer.normalize(phrase, java.text.Normalizer.Form.NFD)
        val bare = decomposed.replace("\u093C", "")
        var composed = decomposed
        for ((pair, single) in NUKTA_PAIRS) composed = composed.replace(pair, single)
        return listOf(phrase, decomposed, bare, composed).distinct()
    }

    private val NUKTA_PAIRS = listOf(
        "\u0915\u093C" to "\u0958", "\u0916\u093C" to "\u0959", "\u0917\u093C" to "\u095A", "\u091C\u093C" to "\u095B",
        "\u0921\u093C" to "\u095C", "\u0922\u093C" to "\u095D", "\u092B\u093C" to "\u095E", "\u092F\u093C" to "\u095F",
    )

    private val NUMBER_WORDS: List<Pair<String, Int>> = listOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20,
        "fifty" to 50, "hundred" to 100,
        "शून्य" to 0, "एक" to 1, "तीन" to 3, "चार" to 4, "पांच" to 5, "पाँच" to 5, "छह" to 6, "छः" to 6,
        "सात" to 7, "आठ" to 8, "नौ" to 9, "दस" to 10, "ग्यारह" to 11, "बारह" to 12, "पंद्रह" to 15, "बीस" to 20,
        "पचास" to 50, "सौ" to 100,
        "ek" to 1, "teen" to 3, "char" to 4, "chaar" to 4, "paanch" to 5, "panch" to 5, "chhe" to 6,
        "chhah" to 6, "saat" to 7, "aath" to 8, "nau" to 9, "das" to 10, "gyarah" to 11, "barah" to 12,
        "pandrah" to 15, "bees" to 20, "pachas" to 50, "sau" to 100,
    ).map { normalize(it.first) to it.second }

    private val VOLUME_NOUN = listOf(
        "volume", "the volume", "वॉल्यूम", "वोल्यूम", "आवाज़", "आवाज", "आवाज़", "awaaz", "awaz", "aawaz",
        "aawaaz", "avaaz", "avaz", "volum",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val UP = listOf(
        "up", "increase", "raise", "louder", "higher", "more", "badhao", "badha do", "badhaao", "badhai", "tez",
        "tej", "tez karo", "zyada", "jyada", "unchi", "oonchi", "upar", "बढ़ाओ", "बढ़ा दो", "बढ़ाइए", "बढ़ा", "बढाओ", "बढा दो",
        "तेज़", "तेज", "ज़्यादा", "ज्यादा", "ऊंची", "ऊँची", "ऊपर", "बधा", "बढ़ा दे", "बधा दे",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val DOWN = listOf(
        "down", "decrease", "lower", "reduce", "less", "softer", "quieter", "kam", "kam karo", "kam kar do", "dheemi",
        "dheema", "dheere", "halki", "neeche", "niche", "कम", "कम करो", "कम कर दो", "धीमी", "धीमा", "धीरे", "हल्की",
        "नीचे", "घटाओ", "ghatao", "थोड़ी कम",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val FULL = listOf(
        "full", "max", "maximum", "highest", "poori", "puri", "pura", "poora", "फुल", "फ़ुल", "पूरी", "पूरा", "सबसे तेज",
        "full karo",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val MUTE = listOf(
        "mute", "mute karo", "sound off", "volume off", "awaaz band", "awaz band", "aawaz band", "म्यूट", "आवाज़ बंद",
        "आवाज बंद", "silent", "silent karo",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val UNMUTE = listOf(
        "unmute", "un mute", "sound on", "volume on", "awaaz chalu", "awaz chalu", "awaaz wapas", "अनम्यूट", "आवाज़ चालू",
        "आवाज चालू", "आवाज़ वापस", "आवाज वापस",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    /** Without the word "volume": only these clear ones. */
    private val LOUDER = listOf(
        "louder", "turn it up", "turn up the music", "make it louder", "a bit louder", "थोड़ा तेज़", "thoda tez",
        "gaana tez karo", "गाना तेज़ करो", "गाना तेज करो",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val QUIETER = listOf(
        "quieter", "softer", "too loud", "turn it down", "turn down the music", "make it quieter", "थोड़ा धीरे",
        "thoda dheere", "gaana dheere karo", "गाना धीरे करो", "gaana kam karo", "गाना कम करो",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    /** "More" after a volume change. */
    private val MORE = listOf(
        "aur", "more", "again", "thoda aur", "aur karo", "aur thoda", "ek baar aur", "और", "थोड़ा और", "और करो",
        "और थोड़ा", "एक बार और", "one more", "a bit more", "little more", "इब और", "hor",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    /** A bare stop: pause the music if any, else end the conversation. */
    private val STOP_EXACT = listOf(
        "stop", "stop it", "stop karo", "stop kar do", "ruko", "rukko", "ruk jao", "ruk ja", "band karo", "band kar do",
        "band kar", "रुको", "रुक जाओ", "रुक जा", "बंद करो", "बंद कर दो", "बंद कर", "स्टॉप", "स्टॉप करो", "थम जा", "tham ja",
    ).flatMap { nuktaForms(normalize(it)) }.toSet()

    private val STOP_FILLERS = listOf("please", "now", "abhi", "अभी", "jara", "zara", "ज़रा", "जरा", "प्लीज़", "प्लीज", "bhai", "भाई")
        .map { normalize(it) }

    private val STOP_NAV = listOf(
        "stop navigation", "stop the navigation", "end navigation", "end the route", "end route", "cancel navigation",
        "cancel the route", "stop the route", "stop route", "exit navigation", "stop directions",
        "navigation band", "navigation band karo", "rasta band karo", "route band karo", "नेविगेशन बंद", "नेविगेशन बंद करो",
        "रास्ता बंद करो", "रूट बंद करो", "नेविगेशन रोको",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val TIME = listOf(
        "what time is it", "what is the time", "what s the time", "whats the time", "tell me the time", "current time",
        "time kya hai", "time kya hua", "time kya ho gaya", "kya time hua", "kya time hai",
        "kitne baje", "kitne baj gaye", "kitna time hua", "time batao", "time bata", "kai baje", "ke baje", "samay kya",
        "कितने बजे", "कितने बज गए", "टाइम क्या", "क्या टाइम", "कितना टाइम हुआ", "टाइम बताओ", "समय क्या", "समय बताओ",
        "कै बजे", "के बजे", "कै बज गे", "टैम के होया", "taim ke hoya",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val DATE = listOf(
        "what is the date", "what s the date", "whats the date", "today s date", "todays date", "the date today",
        "what day is it", "what day is today", "which day is it", "aaj kya date", "aaj ki date", "aaj kya tarikh",
        "aaj ki tarikh", "aaj kaun sa din", "aaj kaunsa din", "date kya hai", "tareekh kya", "tarikh kya",
        "आज क्या डेट", "आज की डेट", "आज क्या तारीख", "आज की तारीख", "आज कौन सा दिन", "आज कौनसा दिन", "तारीख क्या",
        "डेट क्या", "आज के दिन सै", "आज कोणसा दिन",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val RAIN = listOf(
        "will it rain", "is it going to rain", "going to rain", "chance of rain", "rain today", "any rain", "rain forecast",
        "baarish", "barish", "बारिश", "baarish hogi", "barish hogi", "baarish aayegi", "barish aayegi", "baarish ke chance", "barish hogi kya",
        "बारिश होगी", "बारिश आएगी", "बारिश आयेगी", "बारिश के", "बरसात होगी", "मींह आवैगा", "मींह", "meenh",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val WEATHER = listOf(
        "weather", "how is the weather", "temperature outside", "outside temperature", "how hot is it", "how cold is it",
        "mausam", "mosam", "मौसम", "तापमान", "बाहर कितनी गर्मी", "कितनी गर्मी", "कितनी ठंड", "garmi kitni", "thand kitni",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val SPEED = listOf(
        "my speed", "what s my speed", "current speed", "how fast am i", "how fast are we", "speed kitni",
        "kitni speed", "kitni raftar", "raftar kitni", "स्पीड कितनी", "कितनी स्पीड", "रफ़्तार कितनी", "रफ्तार कितनी",
        "कितनी रफ्तार", "कितनी रफ़्तार", "speed kya hai", "स्पीड क्या है",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val OPEN_MAPS = listOf(
        "open maps", "open map", "open the map", "open google maps", "open navigation", "show the map", "show map",
        "show me the map", "maps kholo", "map kholo", "map dikhao", "naksha kholo", "naksha dikhao", "navigation kholo",
        "मैप खोलो", "मैप दिखाओ", "नक्शा खोलो", "नक्शा दिखाओ", "नेविगेशन खोलो", "गूगल मैप खोलो", "maps open karo", "map open karo",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val BRIGHTNESS = listOf(
        "brightness", "screen brightness", "brighter", "dimmer", "screen light", "roshni", "ब्राइटनेस", "रोशनी", "स्क्रीन की रोशनी",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val BT_SETTINGS = listOf(
        "bluetooth settings", "bluetooth setting", "open bluetooth", "bluetooth kholo", "bluetooth on karo",
        "ब्लूटूथ सेटिंग", "ब्लूटूथ खोलो", "ब्लूटूथ सेटिंग्स",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val WHAT_CAN_I_SAY = listOf(
        "what can i say", "what can you do", "what can i ask", "voice commands", "list of commands", "show commands",
        "help with commands", "kya bol sakta", "kya bol sakte", "kya kya bol", "kya kar sakti", "kya kar sakte",
        "commands dikhao", "क्या बोल सकता", "क्या बोल सकते", "क्या क्या बोल", "क्या कर सकती", "क्या कर सकते",
        "कमांड दिखाओ", "के के बोल सकूं", "के कर सकै",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    // ------------------------------------------------------------------ Fuel finder and road reports

    /**
     * "Nearest CNG pump", "CNG pump kahan hai", "petrol pump", "डीज़ल", "charging station". The most
     * specific fuel wins (EV, CNG, diesel, petrol), else any pump. Not "air pump" / "hawa".
     */
    private fun fuel(t: String): VoiceIntent? {
        if (FUEL_NOT.any { has(t, it) }) return null
        return when {
            FUEL_EV.any { has(t, it) } -> VoiceIntent.FindFuel(FuelFilter.Ev)
            FUEL_CNG.any { has(t, it) } -> VoiceIntent.FindFuel(FuelFilter.Cng)
            FUEL_DIESEL.any { has(t, it) } -> VoiceIntent.FindFuel(FuelFilter.Diesel)
            FUEL_PETROL.any { has(t, it) } -> VoiceIntent.FindFuel(FuelFilter.Petrol)
            FUEL_ANY.any { has(t, it) } -> VoiceIntent.FindFuel(FuelFilter.All)
            else -> null
        }
    }

    /**
     * "Police aage hai", "accident ahead", "aage jam hai", "mobile camera yahan hai". Needs the
     * thing seen plus a "there is / ahead / here" word, and is not a question ("jam hai kya?"), a
     * place ("police station le chalo") or a call ("police ko phone karo").
     */
    private fun roadReport(t: String, kw: Keywords): VoiceIntent? {
        if (REPORT_CUE.none { has(t, it) }) return null
        if (REPORT_NOT.any { has(t, it) } || kw.call.any { has(t, it) }) return null
        return when {
            REPORT_MOBILE.any { has(t, it) } -> VoiceIntent.ReportRoad(ReportKind.MobileCamera)
            REPORT_ACCIDENT.any { has(t, it) } -> VoiceIntent.ReportRoad(ReportKind.Accident)
            REPORT_JAM.any { has(t, it) } -> VoiceIntent.ReportRoad(ReportKind.Jam)
            REPORT_POLICE.any { has(t, it) } -> VoiceIntent.ReportRoad(ReportKind.Police)
            else -> null
        }
    }

    private val FUEL_EV = listOf(
        "charging station", "charging stations", "charging point", "charge point", "ev charger", "ev charging",
        "e v charger", "ev station", "car charging", "electric charger", "चार्जिंग स्टेशन", "चार्जिंग पॉइंट",
        "चार्जिंग पॉइंट", "ईवी चार्जर", "ई वी चार्जर", "गाड़ी चार्ज", "charging station kahan", "chaarging station",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val FUEL_CNG = listOf(
        "cng", "c n g", "सीएनजी", "सी एन जी", "सीएनजी पंप", "cng pump", "cng station", "सीएनजी स्टेशन",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val FUEL_DIESEL = listOf("diesel", "deisel", "डीजल", "डीज़ल", "डीज़ल पंप", "diesel pump")
        .flatMap { nuktaForms(normalize(it)) }.distinct()

    private val FUEL_PETROL = listOf(
        "petrol", "petrol pump", "पेट्रोल", "पेट्रोल पंप", "पैट्रोल", "petrol bharwana", "पेट्रोल भरवाना",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val FUEL_ANY = listOf(
        "fuel station", "fuel pump", "filling station", "gas station", "nearest pump", "pump", "fuel", "पंप", "फ्यूल",
        "तेल भरवाना", "तेल डलवाना", "tel bharwana", "tel dalwana", "tel bharwana hai", "तेल कित",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    /** Not a fuel stop: tyre air, a water pump. */
    private val FUEL_NOT = listOf("air pump", "hawa", "हवा", "tyre", "tire", "टायर", "water pump", "पानी")
        .flatMap { nuktaForms(normalize(it)) }.distinct()

    private val REPORT_CUE = listOf(
        "aage", "aagey", "age", "आगे", "ahead", "here", "yahan", "yaha", "यहाँ", "यहां", "ithe", "report", "रिपोर्ट",
        "lagi", "laga", "lagi hai", "लगा", "लगी", "khadi", "khada", "खड़ी", "खड़ा", "hai", "है", "सै", "sai",
        "ho gaya", "हो गया", "hua", "हुआ", "there is", "there s", "spotted", "mark", "chal rahi", "चल रही",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    /** Questions, places and trips are not reports. */
    private val REPORT_NOT = listOf(
        "kya", "क्या", "kahan", "kaha", "कहाँ", "कहां", "kitna", "कितना", "is there", "any", "station", "thana", "थाना",
        "chowki", "चौकी", "take me", "navigate", "go to", "le chalo", "le chal", "ले चलो", "ले चल", "जाना है",
        "jana hai", "where", "kaisa", "kaisi", "कैसा", "कैसी", "how is", "how s",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    /** The accident is ahead of the car, not the car's own. */
    private val AHEAD = listOf("aage", "aagey", "आगे", "ahead", "up ahead", "further on")
        .flatMap { nuktaForms(normalize(it)) }.distinct()

    /** "save kar", "video save karo", "क्लिप सेव कर दे": the dashcam keeps the last clip. */
    private val DASHCAM_SAVE = listOf(
        "save kar", "save karo", "save kar de", "save kar do", "video save", "clip save", "recording save",
        "save the video", "save video", "save clip", "save the clip", "save recording",
        "सेव कर", "सेव करो", "सेव कर दे", "वीडियो सेव", "क्लिप सेव", "रिकॉर्डिंग सेव", "वीडियो बचा", "रिकॉर्डिंग बचा",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    /** Words that keep "accident" an SOS even with "ahead" in it. */
    private val SOS_STRONG = listOf("help", "ambulance", "sos", "emergency", "madad", "bachao", "मदद", "बचाओ", "एम्बुलेंस", "इमरजेंसी", "hurt", "injured", "चोट")
        .flatMap { nuktaForms(normalize(it)) }.distinct()

    private val REPORT_MOBILE = listOf(
        "mobile camera", "speed gun", "interceptor", "radar gun", "मोबाइल कैमरा", "स्पीड गन", "इंटरसेप्टर",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    private val REPORT_ACCIDENT = listOf("accident", "एक्सीडेंट", "ऐक्सीडेंट", "दुर्घटना", "हादसा", "hadsa", "durghatna")
        .flatMap { nuktaForms(normalize(it)) }.distinct()

    private val REPORT_JAM = listOf("traffic jam", "jam", "jaam", "जाम", "ट्रैफिक जाम", "ट्रैफ़िक जाम", "traffic", "ट्रैफिक", "ट्रैफ़िक")
        .flatMap { nuktaForms(normalize(it)) }.distinct()

    private val REPORT_POLICE = listOf(
        "police", "पुलिस", "naka", "नाका", "police check", "police checking", "पुलिस चेकिंग", "checking chal rahi",
        "चेकिंग चल रही", "challan", "चालान",
    ).flatMap { nuktaForms(normalize(it)) }.distinct()

    /** Small talk, Haryanvi / Hindi / English in any app language. Before "play" ("चुटकुला सुनाओ"). */
    private fun smallTalk(t: String): VoiceIntent? = when {
        TALK_JOKE.any { has(t, it) } -> VoiceIntent.SmallTalk(VoiceIntent.Talk.Joke)
        TALK_WHO.any { has(t, it) } -> VoiceIntent.SmallTalk(VoiceIntent.Talk.WhoAreYou)
        TALK_HOW.any { has(t, it) } -> VoiceIntent.SmallTalk(VoiceIntent.Talk.HowAreYou)
        else -> null
    }

    private val TALK_JOKE = listOf(
        "joke", "jokes", "tell me a joke", "something funny", "चुटकला", "चुटकुला", "चुटकले", "चुटकुले",
        "जोक", "chutkula", "chutkala", "chutkule", "chutkale",
    ).map { normalize(it) }

    private val TALK_WHO = listOf(
        "who are you", "what is your name", "what s your name", "your name",
        "कौन सै तू", "तू कौन सै", "कौन है तू", "तू कौन है", "तुम कौन हो", "आप कौन हो", "आप कौन हैं",
        "तेरा नाम", "तुम्हारा नाम", "आपका नाम", "थारा नाम",
        "kaun sai tu", "tu kaun sai", "kaun hai tu", "tu kaun hai", "tum kaun ho", "aap kaun ho",
        "tera naam", "tumhara naam", "aapka naam", "thara naam",
    ).map { normalize(it) }

    private val TALK_HOW = listOf(
        "how are you", "how r you", "how is it going", "what s up",
        "के हाल सै", "के हाल", "क्या हाल है", "क्या हाल", "कैसे हो", "कैसा है", "कैसी हो", "हाल चाल",
        "ke haal", "kya haal", "kaise ho", "kaisa hai", "haal chaal",
    ).map { normalize(it) }

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

    /**
     * [destination] (already cleaned, e.g. after "le chalo" is removed) names the saved Home or
     * Office: "ghar", "घर", "home", "office", "दफ्तर". Only the whole destination counts, so
     * "office depot" or "ghar ka saman" stay normal place searches.
     */
    fun savedKind(destination: String): SavedKind? {
        val d = normalize(destination)
        return when (d) {
            in HOME_WORDS -> SavedKind.Home
            in OFFICE_WORDS -> SavedKind.Office
            else -> null
        }
    }

    /**
     * Short commands without a navigate keyword: "go home", "head home", "घर चल". Only when nothing
     * but fillers is left, so "go to office depot" stays a search for Office Depot.
     */
    private fun savedPhrase(t: String, fillers: List<String>): SavedKind? {
        fun only(phrases: List<String>) = phrases.any { has(t, it) && trimFillers(remove(t, listOf(it)), fillers).isBlank() }
        return when {
            only(HOME_PHRASES) -> SavedKind.Home
            only(OFFICE_PHRASES) -> SavedKind.Office
            else -> null
        }
    }

    private val HOME_WORDS = listOf(
        "home", "my home", "my house", "house", "ghar", "gharr", "ghara", "gharan", "mera ghar", "apna ghar",
        "घर", "घरां", "घरा", "मेरा घर", "अपना घर", "अपणा घर",
    ).map { normalize(it) }.toSet()

    private val OFFICE_WORDS = listOf(
        "office", "the office", "my office", "work", "offis", "daftar", "dafter", "ऑफिस", "ऑफ़िस", "आफिस",
        "दफ्तर", "दफ़्तर", "मेरा ऑफिस",
    ).map { normalize(it) }.toSet()

    private val HOME_PHRASES = listOf(
        "go home", "going home", "head home", "drive home", "back home", "take me home", "navigate home",
        "ghar chalo", "ghar le chalo", "ghar le chal", "ghar chal", "gharan chal", "ghar jana hai",
        "घर चलो", "घर ले चलो", "घर ले चल", "घर चल", "घरां चाल", "घर जाना है",
    ).map { normalize(it) }

    private val OFFICE_PHRASES = listOf(
        "go to office", "go to the office", "go to work", "take me to office", "take me to work",
        "office chalo", "office le chalo", "office le chal", "office chal",
        "ऑफिस चलो", "ऑफिस ले चलो", "ऑफिस ले चल", "दफ्तर चलो", "दफ्तर ले चलो",
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
        resume = listOf("resume", "continue music", "unpause", "resume music", "continue the music", "continue playing"),
        music = listOf("song", "songs", "music", "track", "something", "some music"),
        local = listOf(
            "storage", "usb", "pen drive", "pendrive", "sd card", "memory card", "this unit",
            "offline", "local", "my files",
        ),
        bluetooth = listOf("bluetooth", "my phone", "the phone", "phone", "mobile"),
        camera = listOf("camera", "cameras", "speed trap", "speed check"),
        pause = listOf(
            "pause", "stop music", "stop the music", "stop the song", "stop song", "stop playing", "pause music",
            "stop the songs", "music stop", "song stop",
        ),
        next = listOf("next song", "next track", "skip", "next", "skip this", "change the song", "change song", "next one"),
        // "Preview" is how the recogniser often hears "previous".
        previous = listOf(
            "previous song", "previous track", "previous", "last song", "play the last song", "preview", "previews",
            "privious", "previous one", "go back a song", "song before",
        ),
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
            // Haryanvi: "Rohtak le chal", "Sonipat chalna sai", "Panipat jaana sai".
            "ले चल", "ले चाल", "चलना है", "चालणा सै", "चलणा सै", "जाणा सै", "le chal", "le chaal",
            "chalna hai", "chalna sai", "chalna se", "jaana sai", "jana sai", "jana se",
        ),
        play = listOf(
            "बजाओ", "बजा दो", "चलाओ", "चला दो", "सुनाओ", "लगाओ", "प्ले करो", "प्ले", "bajao", "baja do",
            "chalao", "chala do", "sunao", "lagao", "play karo",
        ),
        resume = listOf("गाना चालू करो", "फिर से चलाओ", "gaana chalu karo", "music chalu karo", "म्यूजिक चालू करो"),
        music = listOf("गाना", "गाने", "म्यूजिक", "म्यूज़िक", "संगीत", "gana", "gaana", "gane", "gaane", "sangeet"),
        local = listOf("स्टोरेज", "यूएसबी", "पेन ड्राइव", "पेनड्राइव", "मेमोरी", "memory"),
        bluetooth = listOf("ब्लूटूथ", "फ़ोन", "फोन", "मोबाइल"),
        camera = listOf("कैमरा", "कैमरे", "kamera"),
        pause = listOf(
            "गाना बंद", "गाना रोको", "म्यूजिक बंद", "म्यूज़िक बंद", "संगीत बंद", "gana band", "gaana band",
            "music band", "gana roko", "gaana roko", "music roko", "गाना रोक दो", "gaana rok do", "pause karo", "पॉज़",
            "पॉज", "पॉज़ करो", "गाने बंद",
        ),
        next = listOf(
            "अगला गाना", "अगला", "agla gana", "agla gaana", "agla", "अगले वाला", "aagla", "आगला", "gaana badlo", "gana badlo",
            "गाना बदलो", "गाना बदल दो", "dusra gaana", "दूसरा गाना",
        ),
        previous = listOf(
            "पिछला गाना", "पिछला", "pichla gana", "pichhla gaana", "pichla", "pichhla", "पिछले वाला", "पहले वाला गाना",
            "pichle wala", "pehle wala gaana", "पाछला", "pachhla",
        ),
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
