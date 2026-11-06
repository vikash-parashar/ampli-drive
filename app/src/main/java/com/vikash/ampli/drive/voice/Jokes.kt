package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.voice.ChatLines.Line
import kotlin.random.Random

/**
 * Ampli's own short jokes ("chutkula suna", "hasa de"): original, family-safe, about cars, chai,
 * traffic, the weather, family and the village. No caste, religion, gender or body jokes and no
 * politics; AssistantChatTest checks a banned-word list over all of them. Each is in the voice
 * pack ([BankClips], "joke_<nn>" by position): recorded when its clip is up to date, else TTS.
 */
object Jokes {
    val ALL: List<Line> = listOf(
        Line(
            "ताऊ नै जीपीएस लगाया। जीपीएस बोल्या, यू-टर्न ले लो। ताऊ बोल्या, पहल्यां तू अपणा मन बणा ले, मैं तो सीधा ए जांगा!",
            "Tau ne GPS lagaya. GPS bola, U-turn le lo. Tau bola, pehle tu apna mann bana le, main to seedha e jaanga!",
            "Tau turned on the GPS. It said: take a U-turn. Tau said: make up your mind first, I'm going straight!",
        ),
        Line(
            "चाय आळे तै पूछ्या, चाय गरम सै? बोल्या, इतणी गरम कि गाड्डी का हीटर बंद कर दे!",
            "Chai aale te poochha, chai garam sai? Bola, itni garam ki gaadi ka heater band kar de!",
            "I asked the tea seller if the tea was hot. He said: so hot you can switch off the car heater!",
        ),
        Line(
            "जाम इतणा लांबा था कि मन्नै गाड्डी मैं ए चाय पी ली, रोटी खा ली, अर इब रात की नींद की तैयारी सै।",
            "Jam itna lamba tha ki manne gaadi mein e chai pi li, roti kha li, ar ib raat ki neend ki taiyaari sai.",
            "The jam was so long I had tea and lunch in the car, and now I'm getting ready for bed.",
        ),
        Line(
            "मौसम आळे नै कहया धूप रहवैगी, ताऊ छाता ले कै चाल पड़्या। बोल्या, मौसम का भरोसा कोनी, छाते का सै।",
            "Mausam aale ne kaha dhoop rahvegi, Tau chhaata le ke chaal padya. Bola, mausam ka bharosa koni, chhaate ka sai.",
            "The forecast said sunshine; Tau took his umbrella anyway. Can't trust the weather, can trust the umbrella.",
        ),
        Line(
            "गाम की सड़क पै भैंस नै रास्ता रोक लिया। मन्नै हॉर्न बजाया, तो भैंस नै इसा देख्या जणूं सड़क उसकी हो अर गलती मेरी।",
            "Gaam ki sadak pe bhains ne raasta rok liya. Manne horn bajaya, to bhains ne isa dekhya jaanu sadak uski ho ar galti meri.",
            "A buffalo blocked the village road. I honked, and she looked at me as if the road was hers and the fault mine.",
        ),
        Line(
            "पेट्रोल पंप आळे नै पूछ्या, कितणे का डालूं? ताऊ बोल्या, इतणे का कि रोहतक जा कै उल्टा आ ज्याऊं अर घर आळयां नै कहाणी सुणा सकूं।",
            "Petrol pump aale ne poochha, kitne ka daalun? Tau bola, itne ka ki Rohtak ja ke ulta aa jyaun ar ghar aalyan ne kahaani suna sakun.",
            "The pump attendant asked how much. Tau said: enough to reach Rohtak, come back, and still tell the family the story.",
        ),
        Line(
            "स्पीड ब्रेकर नै गाड्डी तै पूछ्या, इतणी जल्दी कित की? गाड्डी बोली, तेरे तै मिलण की, पर धीरे धीरे।",
            "Speed breaker ne gaadi te poochha, itni jaldi kit ki? Gaadi boli, tere te milan ki, par dheere dheere.",
            "The speed breaker asked the car: why the hurry? The car said: to meet you, but slowly.",
        ),
        Line(
            "मन्नै ताई तै कहया, AC चला दूं? ताई बोली, खिड़की खोल दे, गाम की हवा फ्री सै अर असली भी।",
            "Manne Tai te kaha, AC chala doon? Tai boli, khidki khol de, gaam ki hawa free sai ar asli bhi.",
            "I asked Tai if I should turn on the AC. She said: open the window, village air is free and real.",
        ),
        Line(
            "नई गाड्डी ली, तो पड़ोसी बोल्या, बधाई हो! मन्नै कहया, बधाई तो किस्त नै दे, वा हर महीने मिलण आवैगी।",
            "Nayi gaadi li, to padosi bola, badhaai ho! Manne kaha, badhaai to kisht ne de, wo har mahine milan aavegi.",
            "I bought a new car and the neighbour said congratulations. I said: congratulate the instalment, it'll visit every month.",
        ),
        Line(
            "मोबाइल की बैटरी अर ताऊ का सबर, दोनूं जाम मैं एकसाथ मुक ज्यां सैं।",
            "Mobile ki battery ar Tau ka sabar, donu jam mein eksaath muk jyaan sain.",
            "The phone battery and Tau's patience run out together in a traffic jam.",
        ),
        Line(
            "छोरे नै पूछ्या, ताऊ, सबतै तेज कौण भाज्जै सै? ताऊ बोल्या, महीने की तनखा, आंदे ए चाली जावै सै।",
            "Chhore ne poochha, Tau, sabte tez kaun bhaajje sai? Tau bola, mahine ki tankha, aande e chaali jaave sai.",
            "A boy asked Tau what runs fastest. Tau said: the monthly salary, gone as soon as it comes.",
        ),
        Line(
            "मन्नै गाड्डी धोई, अर उस्से दिन मींह आ ग्या। इब तो बादळ भी मेरा टाइम टेबल देख कै आवैं सैं।",
            "Manne gaadi dhoyi, ar usse din meenh aa gya. Ib to baadal bhi mera time table dekh ke aave sain.",
            "I washed the car and it rained that very day. Now even the clouds follow my timetable.",
        ),
        Line(
            "गाम मैं एक दुकान पै लिख्या था: आज नकद, कल उधार। ताऊ रोज जावै, अर रोज पूछै, कल कद आवैगी?",
            "Gaam mein ek dukaan pe likhya tha: aaj nakad, kal udhaar. Tau roj jaave, ar roj poochhe, kal kad aavegi?",
            "A village shop sign said: cash today, credit tomorrow. Tau goes every day and asks: when does tomorrow come?",
        ),
        Line(
            "ताई नै नया फोन लिया, अर पहले ए दिन चालीस सेल्फी ले ली। फोन बोल्या, मेरी मेमोरी भर गई, ताई की खुशी ना भरी।",
            "Tai ne naya phone liya, ar pehle e din chaalis selfie le li. Phone bola, meri memory bhar gayi, Tai ki khushi na bhari.",
            "Tai took forty selfies on her new phone's first day. The phone said: my memory is full, her joy isn't.",
        ),
        Line(
            "ठंड इतणी सै कि चाय का कप हाथ मैं ले कै बैठ्या सूं, पीणा भूल ग्या, सेकणा याद सै।",
            "Thand itni sai ki chai ka cup haath mein le ke baithya sun, peena bhool gaya, sekna yaad sai.",
            "It's so cold I hold the tea cup only to warm my hands; I forgot to drink it.",
        ),
        Line(
            "गर्मी इतणी सै कि गाड्डी की सीट पै बैठदे ए मन करै, पहल्यां इसनै भी पाणी पिला दूं।",
            "Garmi itni sai ki gaadi ki seat pe baithde e mann kare, pehle isne bhi paani pila doon.",
            "It's so hot that before sitting down I feel like giving the car seat some water.",
        ),
        Line(
            "ताऊ तै पूछ्या, गाड्डी कितणा एवरेज देवै सै? ताऊ बोल्या, एवरेज तो ठीक सै, बस पंप आळे नै मेरा बजट बिगाड़ दिया।",
            "Tau te poochha, gaadi kitna average deve sai? Tau bola, average to theek sai, bas pump aale ne mera budget bigaad diya.",
            "I asked Tau about his car's mileage. He said: the mileage is fine, the pump ruined my budget.",
        ),
        Line(
            "मन्नै एम्पली तै कहया, चुटकला सुणा। एम्पली बोली, पहल्यां बेल्ट लगा ले, हंसी मैं भी सेफ्टी जरूरी सै!",
            "Manne Ampli te kaha, chutkula suna. Ampli boli, pehle belt laga le, hasi mein bhi safety zaroori sai!",
            "I asked Ampli for a joke. Ampli said: belt on first, safety matters even while laughing!",
        ),
        Line(
            "छोरे नै कहया, ताऊ, मैं ड्राइवर बणूंगा। ताऊ बोल्या, पहल्यां घर की साइकिल की चेन चढ़ा कै दिखा।",
            "Chhore ne kaha, Tau, main driver banunga. Tau bola, pehle ghar ki cycle ki chain chadha ke dikha.",
            "A boy said: I'll be a driver. Tau said: first put the chain back on the bicycle at home.",
        ),
        Line(
            "टोल पै लाइन इतणी लांबी थी कि मन्नै पाछली गाड्डी आळे तै दोस्ती कर ली, इब वो ब्याह मैं भी बुलावैगा।",
            "Toll pe line itni laambi thi ki manne paachhli gaadi aale te dosti kar li, ib wo byaah mein bhi bulaavega.",
            "The toll queue was so long I made friends with the driver behind me; now he'll invite me to a wedding.",
        ),
        Line(
            "ताऊ नै पार्किंग मैं गाड्डी इतणी सीधी खड़ी करी कि लाइन भी शरमा गई।",
            "Tau ne parking mein gaadi itni seedhi khadi kari ki line bhi sharma gayi.",
            "Tau parked so straight that even the parking line blushed.",
        ),
        Line(
            "डॉक्टर नै ताऊ तै कहया, रोज चाल्या कर। ताऊ बोल्या, चालूं तो सूं, रोज गाड्डी मैं रोहतक तक!",
            "Doctor ne Tau te kaha, roj chalya kar. Tau bola, chaalun to sun, roj gaadi mein Rohtak tak!",
            "The doctor told Tau to walk every day. Tau said: I do go every day, by car to Rohtak!",
        ),
        Line(
            "गाड्डी का हॉर्न खराब हो ग्या, तो ताऊ खिड़की तै बाहर मुंह काढ़ कै खुद ए पीं पीं करण लाग ग्या।",
            "Gaadi ka horn kharaab ho gaya, to Tau khidki te baahar munh kaadh ke khud e peen peen karan laag gaya.",
            "The horn broke, so Tau leaned out of the window and went beep beep himself.",
        ),
        Line(
            "मन्नै पूछ्या, ताऊ, जीपीएस पै भरोसा सै? ताऊ बोल्या, हां, पर चाय की दुकान तो खुद टोहूंगा, जीपीएस नै स्वाद का के बेरा।",
            "Manne poochha, Tau, GPS pe bharosa sai? Tau bola, haan, par chai ki dukaan to khud tohunga, GPS ne swaad ka ke bera.",
            "I asked Tau if he trusts the GPS. Yes, he said, but I'll find the tea stall myself; GPS knows nothing about taste.",
        ),
        Line(
            "कोहरा इतणा था कि आगे आळी गाड्डी की बत्ती देख कै चाल रया था, बेरा पाट्या वो भी मेरी बत्ती देख कै चाल रया था।",
            "Kohra itna tha ki aage aali gaadi ki batti dekh ke chaal raya tha, bera paatya wo bhi meri batti dekh ke chaal raya tha.",
            "The fog was so thick I followed the lights of the car ahead; turned out he was following mine.",
        ),
        Line(
            "नई सड़क बणी, ताऊ बोल्या, इतणी चिकणी सै कि गाड्डी भी सोचैगी, आज किस खुशी मैं?",
            "Nayi sadak bani, Tau bola, itni chikni sai ki gaadi bhi sochegi, aaj kis khushi mein?",
            "A new road was built. Tau said: it's so smooth the car will wonder what the occasion is.",
        ),
        Line(
            "पप्पू नै गाड्डी धुलवाई, अर रास्ते मैं भैंस नै कीचड़ उछाळ दिया। भैंस बोली, नजर ना लाग ज्या!",
            "Pappu ne gaadi dhulwaayi, ar raaste mein bhains ne keechad uchhaal diya. Bhains boli, nazar na laag jya!",
            "Pappu had the car washed, and on the way a buffalo splashed mud on it: so it won't catch the evil eye!",
        ),
        Line(
            "ताऊ नै नया स्मार्ट फोन लिया। इब ताऊ फोन तै बोलै, अर फोन ताऊ तै। ताई कहवै, मेरे तै भी कोई बात कर ल्यो!",
            "Tau ne naya smart phone liya. Ib Tau phone te bole, ar phone Tau te. Tai kahve, mere te bhi koi baat kar lyo!",
            "Tau got a smartphone. Now Tau talks to the phone and the phone to Tau. Tai says: someone talk to me too!",
        ),
        Line(
            "चाय इतणी मीठी थी कि चाय आळे नै बिल गेल्लां डॉक्टर का नंबर भी दे दिया।",
            "Chai itni meethi thi ki chai aale ne bill gellan doctor ka number bhi de diya.",
            "The tea was so sweet the seller handed over a doctor's number with the bill.",
        ),
        Line(
            "मन्नै ताऊ तै कहया, गाड्डी मैं गाणा लगा दूं? ताऊ बोल्या, लगा दे, पर रागणी, ना तो गाड्डी भी ना चालैगी।",
            "Manne Tau te kaha, gaadi mein gaana laga doon? Tau bola, laga de, par ragni, na to gaadi bhi na chaalegi.",
            "I asked Tau if I should play a song. Sure, he said, but a ragni, or even the car won't move.",
        ),
        Line(
            "ससुराल जांदे होए ताऊ नै गाड्डी इतणी चमकाई कि रास्ते मैं लोग बोले, कोई बारात आ री सै के?",
            "Sasuraal jaande hoye Tau ne gaadi itni chamkaayi ki raaste mein log bole, koi baaraat aa ri sai ke?",
            "On the way to the in-laws Tau polished the car so much that people asked if a wedding party was coming.",
        ),
        Line(
            "हाईवे पै बोर्ड लिख्या था: धीरे चलो, घर पै कोई इंतजार कर रया सै। ताऊ बोल्या, हां, छोरा अपणा होमवर्क ले कै!",
            "Highway pe board likhya tha: dheere chalo, ghar pe koi intezaar kar raya sai. Tau bola, haan, chhora apna homework le ke!",
            "A highway sign said: drive slowly, someone is waiting at home. Tau said: yes, the boy with his homework!",
        ),
        Line(
            "गाम मैं नेट इतणा धीरे चालै सै कि वीडियो लोड होंदे होंदे खबर पुराणी हो जावै।",
            "Gaam mein net itna dheere chaale sai ki video load honde honde khabar puraani ho jaave.",
            "The village internet is so slow that the news is old by the time the video loads.",
        ),
        Line(
            "ताऊ तै किसे नै पूछ्या, सबतै बढ़िया गाड्डी कौणसी? ताऊ बोल्या, जो घर तै ढाबे तक बिना रुके पहुंचा दे।",
            "Tau te kise ne poochha, sabte badhiya gaadi kaunsi? Tau bola, jo ghar te dhabe tak bina ruke pahuncha de.",
            "Someone asked Tau which car is best. He said: the one that gets you from home to the dhaba without stopping.",
        ),
        Line(
            "मन करया डाइट करूं, फेर ढाबे पै परांठा देख्या, अर डाइट नै खुद कहया, काल तै शुरू करांगे।",
            "Mann karya diet karun, fer dhabe pe parantha dekhya, ar diet ne khud kaha, kaal te shuru karaange.",
            "I wanted to start a diet, then saw a paratha at the dhaba, and the diet itself said: from tomorrow.",
        ),
        Line(
            "रिवर्स करदे टैम गाड्डी बीप बीप करै। ताऊ बोल्या, हां भाई, देख रया सूं, तू तो मेरे मास्टर तै भी घणा टोक्कै सै।",
            "Reverse karde time gaadi beep beep kare. Tau bola, haan bhai, dekh raya sun, tu to mere maastar te bhi ghana tokke sai.",
            "Reversing, the car kept beeping. Tau said: yes, I can see, you correct me more than my old schoolteacher.",
        ),
        Line(
            "बादळ गरज्या, बिजळी चमकी, अर गाम के सारे छात तै कपड़े ठाण भाज्जे। मौसम बताण मैं तो गाम की दादी सबतै तेज सै।",
            "Baadal garjya, bijli chamki, ar gaam ke saare chhaat te kapde thaan bhaajje. Mausam bataan mein to gaam ki daadi sabte tez sai.",
            "Thunder, lightning, and the whole village ran to take the washing off the roofs. For forecasts, the village grandma beats everyone.",
        ),
        Line(
            "मन्नै एम्पली तै पूछ्या, तू थकदी कोनी? एम्पली बोली, मैं तो बस बोलूं सूं, चलाणा तो तन्नै सै, तू थक ज्या तो रुक जाइए।",
            "Manne Ampli te poochha, tu thakdi koni? Ampli boli, main to bas bolun sun, chalaana to tanne sai, tu thak jya to ruk jaaiye.",
            "I asked Ampli if she ever gets tired. She said: I only talk, you do the driving, so stop if you get tired.",
        ),
        Line(
            "ताऊ की गाड्डी इतणी पुराणी सै कि उसकी आरसी पै फोटो भी ब्लैक एंड व्हाइट सै।",
            "Tau ki gaadi itni puraani sai ki uski RC pe photo bhi black and white sai.",
            "Tau's car is so old that even the photo on its papers is black and white.",
        ),
        Line(
            "छोरी नै पूछ्या, पापा, गाड्डी की सबतै जरूरी चीज के सै? पापा बोले, ब्रेक, क्यूंकि रुकणा भी एक कला सै।",
            "Chhori ne poochha, Papa, gaadi ki sabte zaroori cheez ke sai? Papa bole, brake, kyunki rukna bhi ek kala sai.",
            "A girl asked her father what the most important part of a car is. The brakes, he said, because stopping is an art too.",
        ),
        Line(
            "ताऊ नै कहया, म्हारे टैम मैं गाड्डी मैं जीपीएस ना था। मैं बोल्या, फेर रास्ता कींकर मिलता? ताऊ बोल्या, हर चौराहे पै एक ताऊ खड़ा मिलता!",
            "Tau ne kaha, mhaare time mein gaadi mein GPS na tha. Main bola, fer raasta kinkar milta? Tau bola, har chauraahe pe ek Tau khada milta!",
            "Tau said: in my day cars had no GPS. Then how did you find the way? Every crossroads had a Tau standing there!",
        ),
        Line(
            "गाड्डी की डिग्गी मैं इतणा सामान था कि लाग्या पूरा गाम ए साथ ले आए।",
            "Gaadi ki diggi mein itna saamaan tha ki laagya poora gaam e saath le aaye.",
            "The boot was so full it felt as if we had brought the whole village along.",
        ),
    )
}

/**
 * The jokes in a shuffled order: none repeats until all have been told, and a new round never
 * starts with the joke that ended the last one (unit-tested).
 */
class JokeDeck(private val jokes: List<Line> = Jokes.ALL, private val random: Random = Random.Default) {
    private val queue = ArrayDeque<Int>()
    private var last = -1

    fun next(): Line {
        if (queue.isEmpty()) {
            val order = jokes.indices.shuffled(random).toMutableList()
            if (order.size > 1 && order.first() == last) {
                val swap = 1 + random.nextInt(order.size - 1)
                order[0] = order[swap].also { order[swap] = order[0] }
            }
            queue.addAll(order)
        }
        last = queue.removeAt(0)
        return jokes[last]
    }
}
