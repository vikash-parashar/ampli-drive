package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.bhajan.Deity
import com.vikash.ampli.drive.weather.Sky
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/**
 * Ampli's conversation lines (Hey Ampli small talk, "aaj ka din kaisa hai", the news and the
 * jokes' companions), plain Kotlin and unit-tested. Each line without a blank is in the voice
 * pack ([BankClips], ids "chat_<topic>_<nn>"): the recorded voice says it when its clip is bundled
 * and up to date, else the phone voice (TTS). An edited line falls back to TTS until it is
 * recorded again.
 *
 * Each [Line] has [Line.deva] for the Hindi voice, [Line.roman] for the English voice when the
 * unit has no Hindi voice, and [Line.en], spoken when the app is in English and the Haryanvi
 * style is off. Family-safe only: AssistantChatTest checks a banned-word list over every line.
 */
object ChatLines {

    data class Line(val deva: String, val roman: String, val en: String) {
        fun text(script: Script): String = when (script) {
            Script.Deva -> deva
            Script.Roman -> roman
            Script.English -> en
        }
    }

    /** Which wording is spoken: Devanagari (Hindi voice), Latin Haryanvi (English voice), English. */
    enum class Script { Deva, Roman, English }

    enum class Topic {
        HowAreYou, WhoAreYou, Thanks, Bored,
        Morning, Afternoon, Evening, Night,
        DayWish, NewsMore, NewsEnd, NewsOffline, NewsFailed, NewsNotSetUp, NewsNoHindiVoice,
        WeatherOffline, WeatherNoLocation, WeatherFailed, RainTip,
    }

    private val LINES: Map<Topic, List<Line>> = mapOf(
        Topic.HowAreYou to listOf(
            Line("मैं तो एकदम बढ़िया सूं, तू सुणा, के हाल सै?", "Main to ekdam badhiya sun, tu suna, ke haal sai?", "I'm great. How are you?"),
            Line("मौज मैं सूं भाई! तेरा दिन किसा जा रया सै?", "Mauj mein sun bhai! Tera din kisa ja raya sai?", "Having fun! How is your day going?"),
            Line("चकाचक सूं, गाड्डी भी राजी, मैं भी राजी। तू बता, कित चालणा सै?", "Chakachak sun, gaadi bhi raazi, main bhi raazi. Tu bata, kit chalna sai?", "All great: the car is happy and so am I. Where to?"),
            Line("बढ़िया सूं जी, थारे गेल्यां सफर मैं घणा मजा आवै सै। तू ठीक सै ना?", "Badhiya sun ji, thaare gelyan safar mein ghana maza aave sai. Tu theek sai na?", "I'm fine, travelling with you is fun. Are you well?"),
            Line("सब बढ़िया सै भाई! गाणा हो या चुटकला, बोल दिए, मैं हाजिर सूं।", "Sab badhiya sai bhai! Gaana ho ya chutkula, bol diye, main haazir sun.", "All good! A song or a joke, just ask."),
        ),
        Topic.WhoAreYou to listOf(
            Line("मैं एम्पली सूं, थारी गाड्डी की साथण। रास्ता, गाणे, खबर अर चुटकले, सब मेरे जिम्मे।", "Main Ampli sun, thaari gaadi ki saathan. Raasta, gaane, khabar ar chutkule, sab mere jimme.", "I'm Ampli, your car's companion: routes, songs, news and jokes are on me."),
            Line("मेरा नाम एम्पली सै, थारी गाड्डी की देसी साथण, ठेठ हरियाणवी बोलूं सूं।", "Mera naam Ampli sai, thaari gaadi ki desi saathan, theth Haryanvi bolun sun.", "My name is Ampli, your car assistant who speaks desi."),
            Line("एम्पली सूं भाई! तू बोल, मैं हाजिर।", "Ampli sun bhai! Tu bol, main haazir.", "I'm Ampli! Just say it, I'm here."),
            Line("मैं एम्पली सूं, थारे सफर की साथण। नेट ना हो तो भी बात कर ल्यूं सूं।", "Main Ampli sun, thaare safar ki saathan. Net na ho to bhi baat kar lyun sun.", "I'm Ampli, your travel buddy. I talk even without internet."),
        ),
        Topic.Thanks to listOf(
            Line("कोए बात ना भाई, यो तो मेरा काम सै।", "Koye baat na bhai, yo to mera kaam sai.", "No problem, that's my job."),
            Line("राम राम जी, जद मर्जी बुला लिए।", "Ram Ram ji, jad marzi bula liye.", "Anytime, just call me."),
            Line("जीता रह भाई, आराम तै चलाइए।", "Jeeta reh bhai, aaram te chalaiye.", "Thank you too. Drive easy."),
            Line("अरै इसमैं धन्यवाद की के बात, आपां तो अपणे सां।", "Arai ismein dhanyawad ki ke baat, aapaan to apne saan.", "No need for thanks, we're family."),
            Line("खुश रह भाई, थारा सफर बढ़िया रहवै।", "Khush reh bhai, thaara safar badhiya rahve.", "Stay happy, have a good trip."),
        ),
        // Each ends by offering a joke: "haan" then tells one.
        Topic.Bored to listOf(
            Line("बोर क्यूं होवै भाई, मैं सूं ना! एक चुटकला सुणाऊं?", "Bore kyun hove bhai, main sun na! Ek chutkula sunaaun?", "Why be bored, I'm here! Shall I tell a joke?"),
            Line("चाल, बोरियत भगावां। गाणा लगा द्यूं, खबर सुणा द्यूं, पर पहलां एक चुटकला हो ज्या?", "Chaal, boriyat bhagaavaan. Gaana laga dyun, khabar suna dyun, par pahlaan ek chutkula ho jya?", "Let's chase boredom away. I can play songs or read the news. A joke first?"),
            Line("गपशप का टैम सै! हंसी मजाक तै शुरू करां, चुटकला सुणैगा?", "Gapshap ka time sai! Hasi mazaak te shuru karaan, chutkula sunega?", "Chat time! Shall we start with a joke?"),
            Line("तू एकला कोनी, मैं गेल्यां सूं। एक मजेदार बात सुणाऊं?", "Tu ekla koni, main gelyan sun. Ek mazedaar baat sunaaun?", "You're not alone, I'm with you. Shall I tell you something funny?"),
        ),
        Topic.Morning to listOf(
            Line("सवेरे की राम राम!", "Savere ki Ram Ram!", "Good morning!"),
            Line("सवेरे सवेरे राम राम जी!", "Savere savere Ram Ram ji!", "Good morning to you!"),
        ),
        Topic.Afternoon to listOf(
            Line("दोपहर की राम राम!", "Dopahar ki Ram Ram!", "Good afternoon!"),
            Line("राम राम जी, दोपहर हो ली!", "Ram Ram ji, dopahar ho li!", "Good afternoon to you!"),
        ),
        Topic.Evening to listOf(
            Line("साँझ की राम राम!", "Saanjh ki Ram Ram!", "Good evening!"),
            Line("साँझ हो ली, राम राम जी!", "Saanjh ho li, Ram Ram ji!", "Good evening to you!"),
        ),
        Topic.Night to listOf(
            Line("रात की राम राम!", "Raat ki Ram Ram!", "Good evening!"),
            Line("राम राम जी, रात हो री सै!", "Ram Ram ji, raat ho ri sai!", "Hello, it's getting late!"),
        ),
        Topic.DayWish to listOf(
            Line("आराम तै चलाइए, सफर बढ़िया रहे।", "Aaram te chalaiye, safar badhiya rahe.", "Drive easy, have a good trip."),
            Line("बेल्ट लगा ले अर मौज तै चाल।", "Belt laga le ar mauj te chaal.", "Belt up and enjoy the drive."),
            Line("पाणी साथ राखिए अर धीरे चलाइए।", "Paani saath raakhiye ar dheere chalaiye.", "Keep water with you and drive slowly."),
            Line("दिन बढ़िया जावै, सड़क पै ध्यान राखिए।", "Din badhiya jaave, sadak pe dhyaan raakhiye.", "Have a great day, keep your eyes on the road."),
        ),
        Topic.NewsMore to listOf(
            Line("और सुणणी हो तो बोल दे, और खबर।", "Aur sunni ho to bol de, aur khabar.", "Say 'more news' for more."),
        ),
        Topic.NewsEnd to listOf(
            Line("बस, इब खातर इतणी ए खबर सैं।", "Bas, ib khaatir itni e khabar sain.", "That's all the headlines for now."),
            Line("इब की खबर पूरी, थोड़ी वार पाछै फेर पूछ लिए।", "Ib ki khabar poori, thodi vaar paachhe fer poochh liye.", "That's the news for now, ask again later."),
        ),
        Topic.NewsOffline to listOf(
            Line("नेट कोनी चाल रया, खबर कोनी मिली।", "Net koni chaal raya, khabar koni mili.", "There's no internet, so I couldn't get the news."),
            Line("नेट कोनी भाई, फोन का हॉटस्पॉट जोड़ ले, फेर खबर सुणा द्यूं।", "Net koni bhai, phone ka hotspot jod le, fer khabar suna dyun.", "No internet. Connect the phone hotspot and I'll read the news."),
        ),
        Topic.NewsFailed to listOf(
            Line("खबर आळी साइट तै जवाब कोनी आया, थोड़ी वार पाछै फेर पूछ लिए।", "Khabar aali site te jawab koni aaya, thodi vaar paachhe fer poochh liye.", "The news site didn't answer. Ask again in a bit."),
            Line("खबर कोनी मिली भाई, नेट ढीला चाल रया सै शायद।", "Khabar koni mili bhai, net dheela chaal raya sai shaayad.", "Couldn't get the news, the connection may be slow."),
        ),
        Topic.NewsNotSetUp to listOf(
            Line("खबर आळा जुगाड़ इब ताईं कोनी लाग्या।", "Khabar aala jugaad ib taayin koni laagya.", "News isn't set up yet."),
        ),
        Topic.NewsNoHindiVoice to listOf(
            Line("खबर स्क्रीन पै दिखा दी सै।", "Khabar screen pe dikha di sai, Hindi awaaz koni mili.", "The headlines are on the screen; there's no Hindi voice to read them."),
        ),
        Topic.WeatherOffline to listOf(
            Line("मौसम का हाल कोनी मिल्या, नेट कोनी चाल रया।", "Mausam ka haal koni milya, net koni chaal raya.", "No weather right now: there's no internet."),
        ),
        Topic.WeatherNoLocation to listOf(
            Line("गाड्डी की जगहां का इब ताईं बेरा कोनी पाट्या, मौसम थोड़ी वार मैं बताऊं।", "Gaadi ki jagahaan ka ib taayin bera koni paatya, mausam thodi vaar mein bataaun.", "No weather: I don't know where the car is yet."),
        ),
        Topic.WeatherFailed to listOf(
            Line("इब मौसम का हाल कोनी मिल सक्या।", "Ib mausam ka haal koni mil sakya.", "Couldn't get the weather just now."),
        ),
        Topic.RainTip to listOf(
            Line("मींह आण के आसार सैं, छाता गेल्यां राखिए।", "Meenh aan ke aasaar sain, chhaata gelyan raakhiye.", "Rain is likely, keep an umbrella handy."),
        ),
    )

    fun lines(topic: Topic): List<Line> = LINES[topic].orEmpty()

    /** Every line, for the tests. */
    val all: Map<Topic, List<Line>> get() = LINES

    /** The greeting for the hour: subah (4 to 11), dopahar (12 to 4 pm), shaam (5 to 8 pm), raat. */
    fun timeOfDay(hour: Int): Topic = when (hour) {
        in 4..11 -> Topic.Morning
        in 12..16 -> Topic.Afternoon
        in 17..20 -> Topic.Evening
        else -> Topic.Night
    }

    // ------------------------------------------------------------------ "Aaj ka din kaisa hai"

    private val WEEKDAY_DEVA = mapOf(
        DayOfWeek.MONDAY to "सोमवार", DayOfWeek.TUESDAY to "मंगलवार", DayOfWeek.WEDNESDAY to "बुधवार",
        DayOfWeek.THURSDAY to "गुरुवार", DayOfWeek.FRIDAY to "शुक्रवार", DayOfWeek.SATURDAY to "शनिवार", DayOfWeek.SUNDAY to "रविवार",
    )
    private val WEEKDAY_ROMAN = mapOf(
        DayOfWeek.MONDAY to "Somvaar", DayOfWeek.TUESDAY to "Mangalvaar", DayOfWeek.WEDNESDAY to "Budhvaar",
        DayOfWeek.THURSDAY to "Guruvaar", DayOfWeek.FRIDAY to "Shukravaar", DayOfWeek.SATURDAY to "Shanivaar", DayOfWeek.SUNDAY to "Ravivaar",
    )
    private val MONTH_DEVA = listOf(
        "जनवरी", "फरवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर",
    )
    private val MONTH_EN = listOf(
        "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December",
    )

    /** The deity's call in Latin letters ([Deity.greeting] is Devanagari). */
    private val DEITY_ROMAN = mapOf(
        Deity.Shiv to "Har Har Mahadev!", Deity.Hanuman to "Jai Bajrangbali!", Deity.Ganesh to "Ganpati Bappa Morya!",
        Deity.Vishnu to "Om Namo Narayanaya!", Deity.Durga to "Jai Mata Di!", Deity.Shani to "Jai Shanidev!", Deity.Surya to "Jai Surya Dev!",
    )

    fun weekdayDeva(day: DayOfWeek): String = WEEKDAY_DEVA.getValue(day)
    fun weekdayRoman(day: DayOfWeek): String = WEEKDAY_ROMAN.getValue(day)

    /** "Aaj Mangalvaar sai, 4 November." */
    fun dateLine(date: LocalDate, script: Script): String = when (script) {
        Script.Deva -> "आज ${weekdayDeva(date.dayOfWeek)} सै, ${date.dayOfMonth} ${MONTH_DEVA[date.monthValue - 1]}।"
        Script.Roman -> "Aaj ${weekdayRoman(date.dayOfWeek)} sai, ${date.dayOfMonth} ${MONTH_EN[date.monthValue - 1]}."
        Script.English -> "Today is ${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${date.dayOfMonth} ${MONTH_EN[date.monthValue - 1]}."
    }

    /** "Hanuman ji ka din sai, Jai Bajrangbali!" (the bhajan feature's weekday deity). Saturday adds Hanuman ji. */
    fun deityLine(day: DayOfWeek, script: Script): String {
        val d = Deity.of(day)
        val saturday = d == Deity.Shani
        return when (script) {
            Script.Deva -> if (saturday) "शनिदेव अर हनुमान जी का दिन सै, ${d.greeting}" else "${d.nameHi} का दिन सै, ${d.greeting}"
            Script.Roman -> if (saturday) "Shani Dev ar Hanuman ji ka din sai, ${DEITY_ROMAN.getValue(d)}" else "${d.nameEn} ka din sai, ${DEITY_ROMAN.getValue(d)}"
            Script.English -> if (saturday) "It's Shani Dev's and Hanuman ji's day." else "It's ${d.nameEn}'s day."
        }
    }

    fun skyName(sky: Sky, script: Script): String = when (script) {
        Script.Deva -> when (sky) {
            Sky.Clear -> "आसमान साफ सै"
            Sky.PartlyCloudy -> "थोड़े बादळ सैं"
            Sky.Cloudy -> "बादळ छाए सैं"
            Sky.Rain -> "मींह पड़ रया सै"
            Sky.HeavyRain -> "जोर का मींह सै"
            Sky.Thunder -> "गरज अर बिजळी सै"
            Sky.Fog -> "धुंध सै"
            Sky.Snow -> "बरफ पड़ री सै"
        }
        Script.Roman -> when (sky) {
            Sky.Clear -> "aasmaan saaf sai"
            Sky.PartlyCloudy -> "thode baadal sain"
            Sky.Cloudy -> "baadal chhaaye sain"
            Sky.Rain -> "meenh pad raya sai"
            Sky.HeavyRain -> "jor ka meenh sai"
            Sky.Thunder -> "garaj ar bijli sai"
            Sky.Fog -> "dhundh sai"
            Sky.Snow -> "barf pad ri sai"
        }
        Script.English -> when (sky) {
            Sky.Clear -> "clear skies"
            Sky.PartlyCloudy -> "partly cloudy"
            Sky.Cloudy -> "cloudy"
            Sky.Rain -> "rain"
            Sky.HeavyRain -> "heavy rain"
            Sky.Thunder -> "thunderstorms"
            Sky.Fog -> "fog"
            Sky.Snow -> "snow"
        }
    }

    /** "Mausam: aasmaan saaf sai, 24 degree." Only from a real reading. */
    fun weatherLine(sky: Sky, temperatureC: Int?, script: Script): String = when (script) {
        Script.Deva -> "मौसम: ${skyName(sky, script)}" + (temperatureC?.let { ", $it डिग्री" } ?: "") + "।"
        Script.Roman -> "Mausam: ${skyName(sky, script)}" + (temperatureC?.let { ", $it degree" } ?: "") + "."
        Script.English -> "Weather: ${skyName(sky, script)}" + (temperatureC?.let { ", $it degrees" } ?: "") + "."
    }

    /** What the weather part of the day answer can be. */
    sealed interface DayWeather {
        data class Now(val sky: Sky, val temperatureC: Int?, val rainLikely: Boolean) : DayWeather
        data object Offline : DayWeather
        data object NoLocation : DayWeather
        data object Failed : DayWeather
    }

    /**
     * The whole "aaj ka din kaisa hai" answer: date and vaar, the day's deity, the real weather (or
     * honestly why there is none), and a short driving wish from [picker].
     */
    fun dayAnswer(date: LocalDate, weather: DayWeather, script: Script, picker: ChatPicker): String {
        val parts = mutableListOf(dateLine(date, script), deityLine(date.dayOfWeek, script))
        when (weather) {
            is DayWeather.Now -> {
                parts += weatherLine(weather.sky, weather.temperatureC, script)
                if (weather.rainLikely) parts += lines(Topic.RainTip).first().text(script)
            }
            DayWeather.Offline -> parts += lines(Topic.WeatherOffline).first().text(script)
            DayWeather.NoLocation -> parts += lines(Topic.WeatherNoLocation).first().text(script)
            DayWeather.Failed -> parts += lines(Topic.WeatherFailed).first().text(script)
        }
        parts += picker.pick(Topic.DayWish).text(script)
        return parts.joinToString(" ")
    }

    /** "Ke haal sai" answered with the time-of-day greeting first: "Subah ki Ram Ram! Main to ekdam badhiya sun…". */
    fun howAreYou(hour: Int, script: Script, picker: ChatPicker): String =
        howAreYou(picker.pick(timeOfDay(hour)), picker.pick(Topic.HowAreYou), script)

    /** [greeting] (the time of day) then [answer] (a [Topic.HowAreYou] line). */
    fun howAreYou(greeting: Line, answer: Line, script: Script): String = greeting.text(script) + " " + answer.text(script)
}

/**
 * Picks a variant at random, never the same one twice in a row for the same topic (unit-tested).
 */
class ChatPicker(private val random: Random = Random.Default) {
    private val last = HashMap<Any, Int>()

    fun pick(topic: ChatLines.Topic): ChatLines.Line = pickFrom(topic, ChatLines.lines(topic))

    fun <T> pickFrom(key: Any, list: List<T>): T {
        require(list.isNotEmpty()) { "no lines for $key" }
        if (list.size == 1) return list[0]
        val prev = last[key]
        var i = random.nextInt(list.size)
        if (i == prev) i = (i + 1 + random.nextInt(list.size - 1)) % list.size
        last[key] = i
        return list[i]
    }
}
