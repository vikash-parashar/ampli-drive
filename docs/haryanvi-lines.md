# Ampli Car Hub: Haryanvi lines for review

Every line the car speaks in the **Haryanvi desi** voice style (Settings › Voice style, the default).
The source of truth is `app/src/main/java/com/vikash/ampli/drive/voice/HaryanviLines.kt`;
change a line there and here together (a unit test checks that every line in the app is listed here).

- 94 events, 570 lines. The car never says the same line twice in a row.
- Left is **उल्टे हाथ** (ulte haath), right is **सुल्टे हाथ** (sulte haath).
- Family-safe only: no abuse, no crude words, no caste or community jokes (a banned-word test runs over every line).
- SOS, crash countdown and emergency prompts are never in this list: they always stay in plain, clear Hindi.
- The Standard voice style keeps its plain Hindi / English lines; moments that only the Haryanvi style speaks are marked *(Haryanvi only)*.
- `%1$s`, `%2$s`, `%1$d` are filled in by the app: place name, distance, kilometres, minutes, rupees, speed limit, song or hours (see each event).
- Settings › Voice › Hear samples plays one line of any event.

## Welcome and goodbye

- **Welcome on car start**: Car start (boot / ACC on). The car never says your name: it shows only on the welcome card.
- **Morning (4 to 11)**: Said right after the welcome and the Hey Ampli greeting, 4 am to 11 am.
- **Afternoon (12 to 4 pm)**: After the welcome / greeting, noon to 4 pm.
- **Evening (5 to 8 pm)**: After the welcome / greeting, 5 pm to 8 pm.
- **Night (9 pm to 3 am)**: After the welcome / greeting, 9 pm to 3 am.
- **Goodbye (ACC off / parked)**: ACC off, or parked (stood still 20 s) after arriving with Car Hub's navigation. *(Haryanvi only)*

| Event | Devanagari | Roman | English meaning |
|---|---|---|---|
| Welcome on car start | जय बाबा की, आज कित जाणा सै? | Jai Baba Ki, aaj kit jaana sai? | Jai Baba Ki! Where are we going today? |
| Welcome on car start | जय माता दी भाई, आज कित का प्लान सै? | Jai Mata Di bhai, aaj kit ka plan sai? | Jai Mata Di brother, what's the plan today? |
| Welcome on car start | और तैयारे? सुणा दे, के हाल सै? | Aur taiyaare? Suna de, ke haal sai? | So, all set? Tell me, how are things? |
| Welcome on car start | राम राम ताई आले, आज कित की तैयारी सै? | Ram Ram Tai Aale, aaj kit ki taiyaari hai? | Ram Ram, dear friend! What are we ready for today? |
| Welcome on car start | जय बाबा की भाई! गाड्डी त्यार सै, बोल कित चालां? | Jai Baba Ki bhai! Gaadi tyaar sai, bol kit chaalaan? | Jai Baba Ki! The car is ready, say where we go. |
| Welcome on car start | भाई कित जाणा सै आज? | Bhai kit jaana sai aaj? | Brother, where are we going today? |
| Welcome on car start | आज कित की तैयारी सै? | Aaj kit ki taiyaari sai? | What are we ready for today? |
| Welcome on car start | आज कित का प्लान बणैगा? | Aaj kit ka plan banega? | What's the plan going to be today? |
| Morning (4 to 11) | सवेरे सवेरे कित चाल पड़े ताऊ? | Savere savere kit chaal pade tau? | Off somewhere so early in the morning, Tau? |
| Morning (4 to 11) | सवेरे सवेरे जय माता दी! चाय पी ली के? | Savere savere Jai Mata Di! Chai pi li ke? | Morning Jai Mata Di! Had your tea? |
| Morning (4 to 11) | सवेरे सवेरे सड़क खाली सै, फेर भी धीरे चलाइए। | Savere savere sadak khaali sai, pher bhi dheere chalaiye. | The road is empty this early, still drive slowly. |
| Morning (4 to 11) | दिन की शुरुआत बढ़िया हो, चाल पड़ां। | Din ki shuruaat badhiya ho, chaal padaan. | May the day start well, let's go. |
| Afternoon (12 to 4 pm) | दोपहर हो री सै, धूप तै बच कै चलाइए। | Dopahar ho ri sai, dhoop te bach ke chalaiye. | It's afternoon, mind the sun. |
| Afternoon (12 to 4 pm) | रोटी खा ली के भाई? फेर चालां। | Roti kha li ke bhai? Pher chaalaan. | Had your lunch, brother? Then let's go. |
| Afternoon (12 to 4 pm) | दोपहर मैं नींद आवै तो बता दिए, रुक ल्यांगे। | Dopahar main neend aave to bata diye, ruk lyaange. | If you feel sleepy this afternoon, tell me and we'll stop. |
| Afternoon (12 to 4 pm) | घाम घणा सै, AC चला ले अर पाणी पी ले। | Ghaam ghana sai, AC chala le ar paani pi le. | It's very hot, turn on the AC and drink some water. |
| Evening (5 to 8 pm) | साँझ हो ली, बत्ती जला ले भाई। | Saanjh ho li, batti jala le bhai. | Evening is here, switch on the lights. |
| Evening (5 to 8 pm) | साँझ का टैम सै, सड़क पै भीड़ होगी, आराम तै चलाइए। | Saanjh ka time sai, sadak pe bheed hogi, aaram te chalaiye. | It's evening rush time, drive easy. |
| Evening (5 to 8 pm) | काम निपट ग्या के? चाल घरां चालां। | Kaam nipat gaya ke? Chaal ghara chaalaan. | Work done? Let's head home. |
| Evening (5 to 8 pm) | साँझ की राम राम जी! | Saanjh ki Ram Ram ji! | Good evening, Ram Ram! |
| Night (9 pm to 3 am) | रात हो री सै, आराम तै चलाइए। | Raat ho ri sai, aaram te chalaiye. | It's night, drive easy. |
| Night (9 pm to 3 am) | रात नै हेडलाइट ठीक राखिए, अर नींद आवै तो रुक जाइए। | Raat nai headlight theek raakhiye, ar neend aave to ruk jaaiye. | Keep the headlights right at night, and stop if you get sleepy. |
| Night (9 pm to 3 am) | इतणी रात नै कित चाल पड़े भाई? ध्यान तै। | Itni raat nai kit chaal pade bhai? Dhyaan te. | Where to this late, brother? Carefully. |
| Night (9 pm to 3 am) | रात का सफर सै, मैं जाग्गूं सूं, तू भी जाग्या रहिए। | Raat ka safar sai, main jaaggun sun, tu bhi jaagya rahiye. | It's a night trip: I'm awake, you stay awake too. |
| Goodbye (ACC off / parked) | चाल फेर मिलांगे। | Chaal pher milaange. | Alright, see you again. |
| Goodbye (ACC off / parked) | गाड्डी ढंग तै लॉक कर दिए। | Gaadi dhang te lock kar diye. | Lock the car properly. |
| Goodbye (ACC off / parked) | राम राम भाई, आराम कर ले, फेर मिलांगे। | Ram Ram bhai, aaram kar le, pher milaange. | Ram Ram brother, get some rest, see you again. |
| Goodbye (ACC off / parked) | पहुँच गे, शीशे चढ़ा ले अर गाड्डी लॉक कर दिए। | Pahunch ge, sheeshe chadha le ar gaadi lock kar diye. | We're here: roll up the windows and lock the car. |
| Goodbye (ACC off / parked) | आज का सफर बढ़िया रया, जय माता दी! | Aaj ka safar badhiya raya, Jai Mata Di! | Today's trip went well, Jai Mata Di! |

## Hey Ampli

- **Hey Ampli greeting**: "Hey Ampli", at most every 3 hours.
- **Hey Ampli reply**: "Hey Ampli" between greetings. *(Haryanvi only; Standard just beeps)*
- **Searching a place**: Fills in: `%1$s` = place.
- **Starting navigation**: Fills in: `%1$s` = place.
- **Command done**: A command done, e.g. "connect my phone" opened Android Auto.

| Event | Devanagari | Roman | English meaning |
|---|---|---|---|
| Hey Ampli greeting | जय बाबा की भाई! कित चालणा सै आज? | Jai Baba Ki bhai! Kit chalna sai aaj? | Jai Baba Ki brother! Where are we going today? |
| Hey Ampli greeting | सुणा ताई आळे, गाड्डी त्यार सै! | Suna tai aale, gaadi tyar sai! | Tell me, friend, the car is ready! |
| Hey Ampli greeting | जय माता दी! बेल्ट ला ले, फेर चालांगे। | Jai Mata Di! Belt laga le, phir chalange. | Jai Mata Di! Put on your belt, then we go. |
| Hey Ampli greeting | आ ग्या भाई? चाल, आज कित की सैर करणी सै? | Aa gaya bhai? Chal, aaj kit ki sair karni sai? | You're here? Come on, where shall we ride today? |
| Hey Ampli greeting | राम राम जी! गाड्डी भी रेडी, मैं भी रेडी। | Ram Ram ji! Gaadi bhi ready, main bhi ready. | Ram Ram! The car is ready, and so am I. |
| Hey Ampli greeting | भाई कैसा सै, सब राजी खुशी? परिवार में सब ठीक? | Bhai kaisa sai, sab raazi khushi? Parivaar mein sab theek? | How are you, brother, all well? Everyone fine at home? |
| Hey Ampli reply | हाँ भाई, बोल। | Haan bhai, bol. | Yes brother, go ahead. |
| Hey Ampli reply | हाँ जी, सुण रया सूं। | Haan ji, sun reya sun. | Yes, I'm listening. |
| Hey Ampli reply | बोल ताऊ, के सेवा करूं? | Bol tau, ke seva karun? | Tell me, Tau, how can I help? |
| Hey Ampli reply | हाँ, बता के करणा सै? | Haan, bata ke karna sai? | Yes, tell me what to do. |
| Asking where to go | कित चालणा सै? | Kit chalna sai? | Where do we go? |
| Asking where to go | बता भाई, कित ले चालूं? | Bata bhai, kit le chalun? | Tell me, brother, where shall I take you? |
| Asking where to go | हां जी, कड़े जाणा सै? | Haan ji, kade jana sai? | Yes, where do you want to go? |
| Asking where to go | बोल, कित का रास्ता दिखाऊं? | Bol, kit ka rasta dikhaun? | Say it, which way shall I show? |
| Asking where to go | बोल भाई, आज कित का प्लान सै? | Bol bhai, aaj kit ka plan sai? | Tell me, brother, what's today's plan? |
| Searching a place | %1$s ढूंढ रया सूं, रुक जा एक मिनट। | %1$s dhoond reya sun, ruk ja ek minute. | Looking for {place}, wait a minute. |
| Searching a place | ठहर जा भाई, %1$s देख रया सूं। | Thahar ja bhai, %1$s dekh reya sun. | Hold on, brother, I'm looking up {place}. |
| Searching a place | %1$s? इबे तलाश करूं। | %1$s? Ibe talash karun. | {place}? Searching right away. |
| Searching a place | चाल, %1$s का पता करूं सूं। | Chaal, %1$s ka pata karun sun. | OK, finding out where {place} is. |
| Starting navigation | चाल भाई, %1$s चालांगे। रास्ता मैं बताऊंगा। | Chal bhai, %1$s chalange. Rasta main bataunga. | Come on, we're going to {place}. I'll show the way. |
| Starting navigation | %1$s का रास्ता लाग ग्या, आराम तै चालियो। | %1$s ka rasta laag gaya, aaram te chaliyo. | Route to {place} is set, drive easy. |
| Starting navigation | ठीक सै, %1$s। ध्यान तै चलाणा भाई। | Theek sai, %1$s. Dhyan te chalana bhai. | OK, {place}. Drive carefully, brother. |
| Starting navigation | चाल पड़े %1$s कान्ही, बेल्ट ला ले। | Chaal pade %1$s kaanhi, belt la le. | Off towards {place}, buckle up. |
| Starting navigation | %1$s चालां सां, रास्ता मेरे पै छोड़ दे। | %1$s chaalaan saan, rasta mere pe chhod de. | We're off to {place}, leave the route to me. |
| Command done | ठीक सै, कर दिया। | Theek sai, kar diya. | OK, done. |
| Command done | हो ग्या भाई। | Ho gaya bhai. | Done, brother. |
| Command done | लै, कर दिया काम। | Lai, kar diya kaam. | There, job done. |
| Command done | बस, हो ग्या। | Bas, ho gaya. | That's it, done. |
| Didn't hear | के कह्या? फेर तै बोल। | Ke kahya? Pher te bol. | What did you say? Say it again. |
| Didn't hear | समझ ना आया भाई, एक बार फेर बता। | Samajh na aaya bhai, ek baar pher bata. | Didn't get it, brother, tell me once more. |
| Didn't hear | थोड़ा जोर तै बोल, सुण ना पाया। | Thoda zor te bol, sun na paaya. | Speak a bit louder, I couldn't hear. |
| Didn't hear | समझ न्ही आया, फेर बोल। | Samajh nahi aaya, pher bol. | Didn't understand, say it again. |
| Didn't understand | या बात मेरे पल्ले ना पड़ी। | Ya baat mere palle na padi. | That went over my head. |
| Didn't understand | भाई, यो काम मेरे बस का ना सै इबे। | Bhai, yo kaam mere bas ka na sai ibe. | Brother, I can't do that one yet. |
| Didn't understand | के कह रया सै? समझ्या ना। | Ke kah reya sai? Samjhya na. | What are you saying? I didn't get it. |
| Didn't understand | भाई, न्यू बोल: रोहतक चाल, या गाणा बजा। | Bhai, nyu bol: Rohtak chaal, ya gaana baja. | Brother, say it like: go to Rohtak, or play a song. |
| Cancelled | ठीक सै भाई, कोए बात ना। | Theek sai bhai, koi baat na. | OK brother, no problem. |
| Cancelled | चाल छोड़, जद चाहिए तद बुला लियो। | Chal chhod, jad chahiye tad bula liyo. | Never mind, call me when you need me. |
| Cancelled | राम राम, मैं उरे ए सूं। | Ram Ram, main ure e sun. | Ram Ram, I'm right here. |
| Cancelled | ठीक सै, जद मर्जी हो, हे एम्पली बोल दिए। | Theek sai, jad marzi ho, Hey Ampli bol diye. | OK, whenever you like, just say Hey Ampli. |

## Navigation

- **Route found**: Car Hub's navigation found the route. *(Haryanvi only)* Fills in: `%1$s` = kilometres, `%2$d` = minutes.
- **Rerouting**: Off the route, a new one is on its way. *(Haryanvi only)*
- **Turn ahead (prefix)**: Fills in: `%1$s` = distance, followed by the turn line.
- **Turn now (prefix)**: Fills in: followed by the turn line.
- **Roundabout exit number**: Fills in: `%1$d` = exit number.

| Event | Devanagari | Roman | English meaning |
|---|---|---|---|
| Route found | चाल, रास्ता मिल गया, %1$s किलोमीटर सै। | Chaal, rasta mil gaya, %1$s kilometre sai. | Come on, got the route, it's {km} km. |
| Route found | रास्ता मिल ग्या भाई, %1$s किलोमीटर अर %2$d मिनट का सफर सै। | Rasta mil gaya bhai, %1$s kilometre ar %2$d minute ka safar sai. | Got the route, brother: {km} km, a {min}-minute trip. |
| Route found | लै, %1$s किलोमीटर का रास्ता त्यार सै, चाल पड़। | Lai, %1$s kilometre ka rasta tyaar sai, chaal pad. | Here, a {km} km route is ready, let's go. |
| Route found | बढ़िया, रास्ता लाग ग्या। %1$s किलोमीटर, कोए %2$d मिनट। | Badhiya, rasta laag gaya. %1$s kilometre, koi %2$d minute. | Great, the route is set: {km} km, about {min} minutes. |
| Rerouting | रास्ता भटक गया भाई, नया रास्ता देख रया सूं। | Rasta bhatak gaya bhai, naya rasta dekh reya sun. | We went off the route, brother, finding a new one. |
| Rerouting | कोए बात ना, दूसरा रास्ता काढूं सूं। | Koi baat na, doosra rasta kaadhun sun. | No problem, I'm finding another route. |
| Rerouting | ओ हो, मोड़ छूट ग्या! रुक, नया रास्ता बणाऊं सूं। | O ho, mod chhoot gaya! Ruk, naya rasta banaaun sun. | Oops, missed the turn! Wait, making a new route. |
| Rerouting | घबरा मत, घूम कै भी पहुँचांगे, नया रास्ता देख रया सूं। | Ghabra mat, ghoom ke bhi pahunchaange, naya rasta dekh reya sun. | Don't worry, we'll get there anyway, finding a new route. |
| Turn ahead (prefix) | आगे %1$s पे | Aage %1$s pe | In {distance} |
| Turn ahead (prefix) | %1$s आगे, | %1$s aage, | {distance} ahead, |
| Turn ahead (prefix) | सुण भाई, %1$s आगे, | Sun bhai, %1$s aage, | Listen brother, {distance} ahead, |
| Turn ahead (prefix) | %1$s बाद, | %1$s baad, | After {distance}, |
| Turn now (prefix) | इब | Ib | Now |
| Turn now (prefix) | अभी, | Abhi, | Right now, |
| Turn now (prefix) | बस इब्बे, | Bas ibbe, | Right this moment, |
| Turn now (prefix) | चाल इब, | Chaal ib, | Now then, |
| Left | उल्टे हाथ ले ले। | ulte haath le le. | take the left. |
| Left | बाएं हाथ नै मुड़ ले भाई। | baayen haath nai mud le bhai. | turn left, brother. |
| Left | उल्टे हाथ नै काट ले। | ulte haath nai kaat le. | cut to the left. |
| Left | बाएं मुड़ ज्या, आराम तै। | baayen mud jya, aaram te. | turn left, easy. |
| Left | लेफ्ट ले ले भाई। | left le le bhai. | take a left, brother. |
| Left | उल्टे हाथ कान्ही मुड़ ले भाई। | ulte haath kaanhi mud le bhai. | turn towards the left, brother. |
| Left | भाई उल्टे हाथ न ले ले। | bhai ulte haath na le le. | brother, take the left. |
| Right | सुल्टे हाथ ले ले। | sulte haath le le. | take the right. |
| Right | सीधे हाथ मुड़ ले। | seedhe haath mud le. | turn right. |
| Right | दाएं हाथ नै मुड़ ले भाई। | daayen haath nai mud le bhai. | turn right, brother. |
| Right | दाएं मुड़ ज्या, आराम तै। | daayen mud jya, aaram te. | turn right, easy. |
| Right | राइट ले ले भाई। | right le le bhai. | take a right, brother. |
| Right | सुल्टे हाथ कान्ही मुड़ ले भाई। | sulte haath kaanhi mud le bhai. | turn towards the right, brother. |
| Right | सुल्टे हाथ न ले ले। | sulte haath na le le. | take the right. |
| Slight left | हल्का सा बाएं नै हो ले। | halka sa baayen nai ho le. | bear slightly left. |
| Slight left | थोड़ा सा उल्टे हाथ नै दबा ले। | thoda sa ulte haath nai daba le. | ease a little to the left. |
| Slight left | बाएं नै हल्का सरक ले। | baayen nai halka sarak le. | slide slightly left. |
| Slight left | उल्टे हाथ नै हल्का सा मोड़ ले। | ulte haath nai halka sa mod le. | turn slightly left. |
| Slight right | हल्का सा दाएं नै हो ले। | halka sa daayen nai ho le. | bear slightly right. |
| Slight right | थोड़ा सा सुल्टे हाथ नै दबा ले। | thoda sa sulte haath nai daba le. | ease a little to the right. |
| Slight right | दाएं नै हल्का सरक ले। | daayen nai halka sarak le. | slide slightly right. |
| Slight right | सुल्टे हाथ नै हल्का सा मोड़ ले। | sulte haath nai halka sa mod le. | turn slightly right. |
| Sharp left | पूरा बाएं नै घुमा ले, तीखा मोड़ सै। | poora baayen nai ghuma le, teekha mod sai. | turn hard left, it's a sharp bend. |
| Sharp left | तीखा मोड़ सै, बाएं नै पूरा काट ले। | teekha mod sai, baayen nai poora kaat le. | sharp bend, cut fully left. |
| Sharp left | संभल कै, तेज बाएं मोड़ सै। | sambhal ke, tez baayen mod sai. | careful, sharp left turn. |
| Sharp left | उल्टे हाथ पूरा घुमा ले, मोड़ तीखा सै। | ulte haath poora ghuma le, mod teekha sai. | steer fully left, the turn is sharp. |
| Sharp right | पूरा दाएं नै घुमा ले, तीखा मोड़ सै। | poora daayen nai ghuma le, teekha mod sai. | turn hard right, it's a sharp bend. |
| Sharp right | तीखा मोड़ सै, दाएं नै पूरा काट ले। | teekha mod sai, daayen nai poora kaat le. | sharp bend, cut fully right. |
| Sharp right | संभल कै, तेज दाएं मोड़ सै। | sambhal ke, tez daayen mod sai. | careful, sharp right turn. |
| Sharp right | सुल्टे हाथ पूरा घुमा ले, मोड़ तीखा सै। | sulte haath poora ghuma le, mod teekha sai. | steer fully right, the turn is sharp. |
| U-turn | गाड्डी घुमा ले, यू-टर्न ले ले। | gaddi ghuma le, U-turn le le. | turn the car around, take a U-turn. |
| U-turn | यू-टर्न मार ले भाई, उल्टा घूम ले। | U-turn maar le bhai, ulta ghoom le. | do a U-turn, brother, turn back. |
| U-turn | गाड्डी पाच्छै नै घुमा ले, यू-टर्न सै। | gaadi paachhe nai ghuma le, U-turn sai. | turn the car back, it's a U-turn. |
| U-turn | यू-टर्न ले ले, धीरे तै। | U-turn le le, dheere te. | take the U-turn, slowly. |
| U-turn | उल्टा घुमा ले भाई, रास्ता पाच्छै सै। | ulta ghuma le bhai, rasta paachhe sai. | turn around, brother, the way is behind us. |
| Keep left | बाएं आळी लेन मैं रहियो। | baayen aali lane mein rahiyo. | stay in the left lane. |
| Keep left | बाएं नै चिपक कै चालिए। | baayen nai chipak ke chaliye. | keep close to the left. |
| Keep left | बाएं पास्सै रह भाई। | baayen paase reh bhai. | keep to the left side, brother. |
| Keep left | उल्टे हाथ आळी लेन पकड़ ले। | ulte haath aali lane pakad le. | take the left lane. |
| Keep right | दाएं आळी लेन मैं रहियो। | daayen aali lane mein rahiyo. | stay in the right lane. |
| Keep right | दाएं नै चिपक कै चालिए। | daayen nai chipak ke chaliye. | keep close to the right. |
| Keep right | दाएं पास्सै रह भाई। | daayen paase reh bhai. | keep to the right side, brother. |
| Keep right | सुल्टे हाथ आळी लेन पकड़ ले। | sulte haath aali lane pakad le. | take the right lane. |
| Flyover | फ्लाईओवर पे ले ले। | flyover pe le le. | take the flyover. |
| Flyover | फ्लाईओवर पे चढ़ ले भाई। | flyover pe chadh le bhai. | go up the flyover, brother. |
| Flyover | ऊपर आळा पुल पकड़ ले। | upar aala pul pakad le. | take the bridge up top. |
| Flyover | फ्लाईओवर ले ले, नीचै मत उतरियो। | flyover le le, neeche mat utariyo. | take the flyover, don't go under. |
| Ramp | रैंप पे ले ले। | ramp pe le le. | take the ramp. |
| Ramp | रैंप पकड़ ले भाई। | ramp pakad le bhai. | take the ramp, brother. |
| Ramp | रैंप पै चढ़ ले, आराम तै। | ramp pe chadh le, aaram te. | go up the ramp, easy. |
| Ramp | साइड आळा रैंप ले ले। | side aala ramp le le. | take the side ramp. |
| Exit | एग्जिट ले ले भाई। | exit le le bhai. | take the exit, brother. |
| Exit | बाहर लिकड़ ले, एग्जिट आ ग्या। | bahar likad le, exit aa gaya. | get off, the exit is here. |
| Exit | हाईवे तै उतर ले, एग्जिट सै। | highway te utar le, exit sai. | leave the highway, it's the exit. |
| Exit | आगे आळे एग्जिट तै लिकड़ ले भाई। | aage aale exit te likad le bhai. | take the exit ahead, brother. |
| Roundabout | गोल चक्कर आ रया सै, संभल कै। | gol chakkar aa reya sai, sambhal ke. | roundabout coming, careful. |
| Roundabout | चौक पै घूम ले भाई, गोल चक्कर सै। | chowk pe ghoom le bhai, gol chakkar sai. | go round at the circle, brother. |
| Roundabout | गोल चक्कर सै, धीरे कर ले। | gol chakkar sai, dheere kar le. | roundabout, slow down. |
| Roundabout | गोल चक्कर पै धीरे हो कै घूम ले। | gol chakkar pe dheere ho ke ghoom le. | slow down and go round the roundabout. |
| Roundabout exit number | गोल चक्कर पै %1$d नंबर आळा रास्ता पकड़ ले। | gol chakkar pe %1$d number aala rasta pakad le. | at the roundabout take exit {n}. |
| Roundabout exit number | चक्कर मैं घूम कै %1$d नंबर तै लिकड़ ले। | chakkar mein ghoom ke %1$d number te likad le. | go round and leave by exit {n}. |
| Roundabout exit number | गोल चक्कर सै, %1$d नंबर का रास्ता लेणा सै। | gol chakkar sai, %1$d number ka rasta lena sai. | roundabout: we take exit {n}. |
| Roundabout exit number | चक्कर पै घूम ले, %1$d नंबर आळे रास्ते तै बाहर लिकड़ ले। | chakkar pe ghoom le, %1$d number aale raste te bahar likad le. | go round and come out on exit {n}. |
| Continue straight | सीधा चाल। | Sidha chal. | Go straight. |
| Continue straight | सीधा चालता रह भाई। | Seedha chalta reh bhai. | Keep going straight, brother. |
| Continue straight | सीधे सीधे चाल, कित मुड़णा कोनी। | Seedhe seedhe chal, kit mudna koni. | Straight on, no turns for now. |
| Continue straight | सीधा रास्ता सै, मौज तै चला। | Seedha rasta sai, mauj te chala. | Straight road, enjoy the drive. |
| Continue straight | भाई सीधा चाल। | Bhai seedha chal. | Go straight, brother. |
| Arrived | पहुँच गए ताऊ! | Pahunch gaye tau! | We've arrived, Tau! |
| Arrived | पहुंच गे भाई, मंजिल आ गी! | Pahunch ge bhai, manzil aa gi! | We're here, brother, destination reached! |
| Arrived | ल्यो जी, आ ग्या ठिकाणा! | Lyo ji, aa gaya thikana! | There you go, we've reached the place! |
| Arrived | बस भाई, याड़े ए सै मंजिल। | Bas bhai, yaade e sai manzil. | That's it, brother, the destination is right here. |
| Arrived | आ गे भाई, गाड्डी साइड ला ले आराम तै। | Aa ge bhai, gaadi side la le aaram te. | We're here, brother, pull over gently. |

## Speed and cameras

- **Over the limit**: Fills in: `%1$d` = speed limit.
- **Back under the limit**: Back under the limit within 90 s of a slow-down warning. *(Haryanvi only)*
- **Camera ahead**: Fills in: `%1$d` = metres, `%2$d` = speed limit.
- **Camera ahead (no limit)**: Fills in: `%1$d` = metres.

| Event | Devanagari | Roman | English meaning |
|---|---|---|---|
| Over the limit | मंदी कर ले भाई, इस रोड पे स्पीड कम सै। | Mandi kar le bhai, is road pe speed kam sai. | Slow down, brother, this road has a lower limit. |
| Over the limit | ताऊ, गाड्डी मंदी कर ले। | Tau, gaddi mandi kar le. | Tau, slow the car down. |
| Over the limit | स्पीड घटा ले भाई, लिमिट %1$d सै। | Speed ghata le bhai, limit %1$d sai. | Reduce speed, brother, the limit is {limit}. |
| Over the limit | धीरे कर ले, आड़े %1$d की लिमिट सै। | Dheere kar le, aade %1$d ki limit sai. | Slow down, the limit here is {limit}. |
| Over the limit | %1$d तै ऊपर ना, चालान कट ज्यागा। | %1$d te upar na, chalaan kat jyaga. | Not above {limit}, or you'll get a fine. |
| Still over the limit | छोड़ दे भाई, रेस छोड़ दे। | Chhod de bhai, race chhod de. | Let it go, brother, this isn't a race. |
| Still over the limit | रेस ना सै भाई, आराम तै चला। | Race na sai bhai, aaram te chala. | It's not a race, brother, drive easy. |
| Still over the limit | पैर हटा ले एक्सीलेटर तै, भाई। | Pair hata le accelerator te, bhai. | Ease off the accelerator, brother. |
| Still over the limit | इतनी जल्दी के सै भाई, घरां सब बाट देखैं सैं। | Itni jaldi ke sai bhai, ghara sab baat dekhe sai. | Why the hurry, brother? Everyone at home is waiting for you. |
| Still over the limit | भाई मंदा कर ले। | Bhai manda kar le. | Brother, slow down. |
| Still over the limit | भाई आराम तै चाल। | Bhai aaram te chal. | Brother, go easy. |
| Back under the limit | शाबाश, न्यू ए चला। | Shaabaash, nyu e chala. | Well done, drive just like this. |
| Back under the limit | ये हुई ना बात! इब ठीक सै स्पीड। | Ye hui na baat! Ib theek sai speed. | That's it! The speed is fine now. |
| Back under the limit | बढ़िया भाई, आराम तै चलाण मैं ए मजा सै। | Badhiya bhai, aaram te chalaan main e maza sai. | Nice, brother, the fun is in driving easy. |
| Back under the limit | शाबाश! घरां आळे भी राजी होंगे। | Shaabaash! Ghara aale bhi raazi honge. | Well done! The family will be happy too. |
| Camera ahead | ध्यान तै भाई, आगे कैमरा सै, स्पीड कम कर ले! %1$d मीटर पै, लिमिट %2$d। | Dhyan te bhai, aage camera sai, speed kam kar le! %1$d metre pe, limit %2$d. | Careful, brother, camera ahead, slow down! In {m} metres, limit {limit}. |
| Camera ahead | %1$d मीटर पै कैमरा आ रया सै, %2$d तै ऊपर मत जाइयो। | %1$d metre pe camera aa reya sai, %2$d te upar mat jaiyo. | Camera in {m} metres, don't go above {limit}. |
| Camera ahead | संभल कै भाई! %1$d मीटर पै कैमरा, लिमिट %2$d सै। | Sambhal ke bhai! %1$d metre pe camera, limit %2$d sai. | Careful, brother! Camera in {m} metres, the limit is {limit}. |
| Camera ahead | आगे कैमरा खड़्या सै %1$d मीटर पै, %2$d तै ऊपर गया तो चालान कट ज्यागा। | Aage camera khadya sai %1$d metre pe, %2$d te upar gaya to chalaan kat jyaga. | A camera is waiting {m} metres ahead; above {limit} you'll get a fine. |
| Camera ahead (no limit) | धीरे कर ले, आगे कैमरा सै! %1$d मीटर। | Dheere kar le, aage camera sai! %1$d metre. | Slow down, camera ahead! {m} metres. |
| Camera ahead (no limit) | ध्यान तै भाई, आगे कैमरा सै! %1$d मीटर। | Dhyan te bhai, aage camera sai! %1$d metre. | Careful, brother, camera ahead! {m} metres. |
| Camera ahead (no limit) | %1$d मीटर पै कैमरा सै, आराम तै। | %1$d metre pe camera sai, aaram te. | Camera in {m} metres, take it easy. |
| Camera ahead (no limit) | संभल कै, %1$d मीटर पै कैमरा। | Sambhal ke, %1$d metre pe camera. | Careful, camera in {m} metres. |
| Over the limit at a camera | मंदी कर ले भाई, इस रोड पे स्पीड कम सै। | Mandi kar le bhai, is road pe speed kam sai. | Slow down, brother, this road has a lower limit. |
| Over the limit at a camera | धीरे कर ले, आगे कैमरा सै। | Dheere kar le, aage camera sai. | Slow down, there's a camera ahead. |
| Over the limit at a camera | ताऊ, गाड्डी मंदी कर ले। | Tau, gaddi mandi kar le. | Tau, slow the car down. |
| Over the limit at a camera | अरे धीरे भाई, चालान कट ज्यागा! | Arre dheere bhai, chalaan kat jyaga! | Hey, slowly, brother, you'll get a fine! |
| Over the limit at a camera | घरां कोए बाट देख रया सै, धीरे चला। | Ghara koi baat dekh reya sai, dheere chala. | Someone at home is waiting for you, drive slowly. |
| Over the limit at a camera | भाई आगे कैमरे लाग रे सैं, मंदा कर ले। | Bhai aage camere laag re sain, manda kar le. | Brother, cameras ahead, slow down. |
| Over the limit at a camera | भाई आगे ब्रेक कर ले। | Bhai aage brake kar le. | Brother, brake ahead. |

## Toll, breaks, parking, weather

- **Toll deducted**: FASTag toll SMS arrives (the toll pop-up). *(Haryanvi only)* Fills in: `%1$s` = rupees deducted, `%2$s` = balance.
- **Toll deducted (no balance)**: FASTag toll SMS without a balance. *(Haryanvi only)* Fills in: `%1$s` = rupees deducted.
- **Break reminder**: Fills in: `%1$s` = hours driven.
- **Parking spot saved**: "Save spot here" on the parked-car screen. *(Haryanvi only)*
- **Seat belt reminder**: Once per trip, when the car first moves (head units have no belt sensor; not again within 45 minutes). *(Haryanvi only)*
- **Where's my car**: "Where is my car": said before the plain answer.
- **Heat**: Route weather: 42 °C or more here or ahead.

| Event | Devanagari | Roman | English meaning |
|---|---|---|---|
| Toll deducted | टोल कट गया, %1$s रुपये, बैलेंस %2$s रुपये बच्या सै। | Toll kat gaya, %1$s rupaye, balance %2$s rupaye bachya sai. | Toll deducted, ₹{amount}; ₹{balance} balance left. |
| Toll deducted | %1$s रुपये का टोल कट ग्या भाई, खाते मैं %2$s बचे सैं। | %1$s rupaye ka toll kat gaya bhai, khaate main %2$s bache sain. | ₹{amount} toll deducted, brother; {balance} left in the account. |
| Toll deducted | फास्टैग तै %1$s रुपये गए, इब %2$s रुपये बचे सैं। | FASTag te %1$s rupaye gaye, ib %2$s rupaye bache sain. | ₹{amount} went from FASTag; ₹{balance} left now. |
| Toll deducted | टोल पार! %1$s कटे, %2$s बाकी सैं। | Toll paar! %1$s kate, %2$s baaki sain. | Past the toll! {amount} deducted, {balance} remaining. |
| Toll deducted (no balance) | टोल कट गया, %1$s रुपये। | Toll kat gaya, %1$s rupaye. | Toll deducted, ₹{amount}. |
| Toll deducted (no balance) | %1$s रुपये का टोल कट ग्या भाई। | %1$s rupaye ka toll kat gaya bhai. | ₹{amount} toll deducted, brother. |
| Toll deducted (no balance) | फास्टैग तै %1$s रुपये गए, चाल आगे। | FASTag te %1$s rupaye gaye, chaal aage. | ₹{amount} went from FASTag, let's move on. |
| Toll deducted (no balance) | टोल पार! %1$s रुपये कटे। | Toll paar! %1$s rupaye kate. | Past the toll! ₹{amount} deducted. |
| Break reminder | %1$s घंटे हो लिए, थक ग्या होगा भाई, एक चाय हो जाए? | %1$s ghante ho liye, thak gaya hoga bhai, ek chai ho jaye? | {hours} hours already, you must be tired, brother; how about a tea? |
| Break reminder | %1$s घंटे तै चला रया सै, थोड़ा रुक कै कमर सीधी कर ले। | %1$s ghante te chala reya sai, thoda ruk ke kamar seedhi kar le. | Driving for {hours} hours; stop a bit and stretch your back. |
| Break reminder | भाई %1$s घंटे हो गए, ढाबा आवै तै रुक लियो। | Bhai %1$s ghante ho gaye, dhaba aave te ruk liyo. | Brother, {hours} hours now; stop at the next dhaba. |
| Break reminder | %1$s घंटे हो लिए, चाय पी ले भाई। | %1$s ghante ho liye, chai pi le bhai. | {hours} hours already, have some tea, brother. |
| Break reminder | %1$s घंटे तै स्टीयरिंग पकड़ राख्या सै, पाँच मिनट टांग सीधी कर ले। | %1$s ghante te steering pakad raakhya sai, paanch minute taang seedhi kar le. | You've held the wheel for {hours} hours; stretch your legs for five minutes. |
| Parking spot saved | गाड्डी की जगह याद कर ली, बेफिकर रह। | Gaadi ki jagah yaad kar li, befikar reh. | I've saved where the car is, relax. |
| Parking spot saved | ठीक सै, गाड्डी याड़े खड़ी सै, मन्नै बेरा सै। | Theek sai, gaadi yaade khadi sai, manne bera sai. | OK, the car is parked here, I know it. |
| Parking spot saved | पार्किंग सेव कर दी, भूल ज्यावै तो मन्नै पूछ लिए। | Parking save kar di, bhool jyaave to manne poochh liye. | Parking saved; if you forget, ask me. |
| Parking spot saved | जगह नोट कर ली भाई, गाड्डी ना गुमैगी। | Jagah note kar li bhai, gaadi na gumegi. | Spot noted, brother, the car won't get lost. |
| Seat belt reminder | भाई सीट बेल्ट पहन ले। | Bhai seat belt pehan le. | Brother, put on your seat belt. |
| Seat belt reminder | बेल्ट ला ले भाई, फेर चालांगे। | Belt la le bhai, pher chaalaange. | Buckle up, brother, then we go. |
| Seat belt reminder | सीट बेल्ट पहन ले, सफर बढ़िया रहवैगा। | Seat belt pehan le, safar badhiya rahvega. | Put on the seat belt, the trip will go well. |
| Seat belt reminder | पहलां बेल्ट, फेर गाड्डी। पहन ले भाई। | Pehlaan belt, pher gaadi. Pehan le bhai. | Belt first, then the car. Put it on, brother. |
| Where's my car | रुक, देखूं गाड्डी कित खड़ी सै। | Ruk, dekhun gaadi kit khadi sai. | Wait, let me see where the car is. |
| Where's my car | थारी गाड्डी का पता मेरे धोरै सै। | Thaari gaadi ka pata mere dhore sai. | I have your car's location. |
| Where's my car | चिंता मत कर, गाड्डी गुमी कोनी। | Chinta mat kar, gaadi gumi koni. | Don't worry, the car isn't lost. |
| Where's my car | सुण, गाड्डी न्यू खड़ी सै। | Sun, gaadi nyu khadi sai. | Listen, here's where the car is. |
| Heavy rain ahead | आगे बारिश सै भाई, धीरे चलाइए अर वाइपर चला ले। | Aage baarish sai bhai, dheere chalaiye ar wiper chala le. | Rain ahead, brother: drive slowly and turn on the wipers. |
| Heavy rain ahead | मींह आण आळा सै, सड़क चिकणी होगी, संभल कै। | Meenh aan aala sai, sadak chikni hogi, sambhal ke. | Rain is coming, the road will be slippery, careful. |
| Heavy rain ahead | बादळ बरसण लाग रे सैं, ब्रेक आराम तै मारिए। | Baadal barsan laag re sain, brake aaram te maariye. | The clouds are pouring, brake gently. |
| Heavy rain ahead | आगे जोर की बारिश सै, दूरी बणा कै चलाइए। | Aage jor ki baarish sai, doori bana ke chalaiye. | Heavy rain ahead, keep your distance. |
| Thunderstorm ahead | आगे आंधी-तूफान सै भाई, कोए सुरक्षित जगह देख कै रुक लिए। | Aage aandhi-toofan sai bhai, koi surakshit jagah dekh ke ruk liye. | Storm ahead, brother; find a safe place and stop. |
| Thunderstorm ahead | आगे बिजळी कड़क री सै, धीरे चलाइए अर पेड़ां तै दूर रहिए। | Aage bijli kadak ri sai, dheere chalaiye ar pedaan te door rahiye. | Lightning ahead: drive slowly and keep away from trees. |
| Thunderstorm ahead | तूफान आण आळा सै, जल्दबाजी मत करिए। | Toofan aan aala sai, jaldbaazi mat kariye. | A storm is coming, don't rush. |
| Thunderstorm ahead | आगे मौसम बिगड़ रया सै, आराम तै अर ध्यान तै। | Aage mausam bigad reya sai, aaram te ar dhyaan te. | The weather is turning bad ahead; easy and careful. |
| Fog ahead | आगे धुंध सै भाई, फॉग लाइट जला ले अर धीरे चला। | Aage dhundh sai bhai, fog light jala le ar dheere chala. | Fog ahead, brother: fog lights on and drive slowly. |
| Fog ahead | धुंध घणी सै, आगे आळी गाड्डी तै दूरी राखिए। | Dhundh ghani sai, aage aali gaadi te doori raakhiye. | Thick fog, keep a distance from the car ahead. |
| Fog ahead | आगे कुछ ना दीखै, मंदी कर ले अर पीली बत्ती जला ले। | Aage kuchh na deekhe, mandi kar le ar peeli batti jala le. | You can't see ahead: slow down and switch on the hazard lights. |
| Fog ahead | धुंध मैं ओवरटेक मत करिए भाई। | Dhundh main overtake mat kariye bhai. | Don't overtake in the fog, brother. |
| Heat | घणी गर्मी सै भाई, पाणी पी ले अर AC चला ले। | Ghani garmi sai bhai, paani pi le ar AC chala le. | It's very hot, brother: drink water and turn on the AC. |
| Heat | बाहर लू चाल री सै, टायर अर इंजन का ध्यान राखिए। | Bahar loo chaal ri sai, tyre ar engine ka dhyaan raakhiye. | A heatwave outside; keep an eye on the tyres and engine. |
| Heat | आज घाम घणा सै, गाड्डी छाँ मैं खड़ी करिए। | Aaj ghaam ghana sai, gaadi chhaan main khadi kariye. | The sun is strong today, park in the shade. |
| Heat | गर्मी मैं सिर दुखै तो रुक कै पाणी पी लिए। | Garmi main sir dukhe to ruk ke paani pi liye. | If the heat gives you a headache, stop and drink water. |

## Car health (OBD, Pro)

- **Engine too hot**: OBD dongle (Pro): coolant 105 °C or more.
- **Battery low**: OBD dongle (Pro): battery under 11.8 V.
- **Battery not charging**: OBD dongle (Pro): engine running but the battery is not charging.

| Event | Devanagari | Roman | English meaning |
|---|---|---|---|
| Engine too hot | भाई, इंजन घणा गरम हो रया सै, गाड्डी साइड लगा कै रोक ले। | Bhai, engine ghana garam ho reya sai, gaadi side laga ke rok le. | Brother, the engine is running very hot; pull over and stop. |
| Engine too hot | इंजन का तापमान ऊपर सै, AC बंद कर अर कित छाँ मैं रुक ले। | Engine ka taapmaan upar sai, AC band kar ar kit chhaan main ruk le. | Engine temperature is high; switch off the AC and stop in some shade. |
| Engine too hot | इंजन तप रया सै, कूलेंट चेक करवा लिए। | Engine tap reya sai, coolant check karva liye. | The engine is heating up; get the coolant checked. |
| Engine too hot | रुक जा भाई, इंजन नै ठंडा होण दे। | Ruk ja bhai, engine nai thanda hon de. | Stop, brother, let the engine cool down. |
| Battery low | बैटरी कमजोर सै भाई, जल्दी चेक करवा ले। | Battery kamzor sai bhai, jaldi check karva le. | The battery is weak, brother, get it checked soon. |
| Battery low | बैटरी का वोल्टेज कम सै, गाड्डी बंद करी तो स्टार्ट होण मैं दिक्कत आ सकै सै। | Battery ka voltage kam sai, gaadi band kari to start hon main dikkat aa sake sai. | Battery voltage is low; once switched off, the car may not start easily. |
| Battery low | बैटरी थकी होई सै, मिस्त्री नै दिखा दिए। | Battery thaki hoi sai, mistri nai dikha diye. | The battery is worn out, show it to a mechanic. |
| Battery low | बैटरी डाउन होण लाग री सै, लाइट अर AC कम कर ले। | Battery down hon laag ri sai, light ar AC kam kar le. | The battery is running down, reduce lights and AC. |
| Battery not charging | बैटरी चार्ज ना हो री भाई, अल्टरनेटर चेक करवा ले। | Battery charge na ho ri bhai, alternator check karva le. | The battery isn't charging, brother; get the alternator checked. |
| Battery not charging | चार्जिंग ठीक कोनी, जल्दी मिस्त्री धोरै चालां। | Charging theek koni, jaldi mistri dhore chaalaan. | Charging isn't right; let's go to a mechanic soon. |
| Battery not charging | बैटरी मैं करंट ना जा रया, लंबा सफर मत करिए। | Battery main current na ja reya, lamba safar mat kariye. | No charge is going into the battery; avoid a long trip. |
| Battery not charging | चार्जिंग मैं गड़बड़ सै, गाड्डी बंद होण तै पहलां दिखा दिए। | Charging main gadbad sai, gaadi band hon te pehlaan dikha diye. | Something is wrong with charging; get it seen before the car dies. |

## Music

- **Play music**: "Play" / "resume" by voice. *(Haryanvi only)*
- **Play a named song**: Fills in: `%1$s` = song.
- **Next song**: "Next song" by voice. *(Haryanvi only)*
- **Previous song**: "Previous song" by voice. *(Haryanvi only)*
- **Pause music**: "Pause" / "stop music" by voice. *(Haryanvi only)*

| Event | Devanagari | Roman | English meaning |
|---|---|---|---|
| Play music | ले, गाणा बजा दिया। | Le, gaana baja diya. | There, the song is playing. |
| Play music | चाल, गाणे चला दिए, मौज कर। | Chaal, gaane chala diye, mauj kar. | Songs are on, enjoy. |
| Play music | ले भाई, म्यूजिक शुरू। | Le bhai, music shuru. | Here, brother, music on. |
| Play music | बजा दिया, आवाज ठीक सै के? | Baja diya, aawaaz theek sai ke? | Playing; is the volume OK? |
| Play a named song | ले, %1$s बजा दिया। | Le, %1$s baja diya. | There, playing {song}. |
| Play a named song | चाल, %1$s सुण। | Chaal, %1$s sun. | Come on, listen to {song}. |
| Play a named song | %1$s लगा दिया भाई, मौज कर। | %1$s laga diya bhai, mauj kar. | Put on {song}, brother, enjoy. |
| Play a named song | %1$s बाज रया सै, आवाज बढ़ा ले। | %1$s baaj reya sai, aawaaz badha le. | {song} is playing, turn it up. |
| Next song | अगला गाणा लगा दिया। | Agla gaana laga diya. | Put on the next song. |
| Next song | ले, दूसरा गाणा। | Le, doosra gaana. | Here, another song. |
| Next song | यो ना जंच्या? चाल अगला। | Yo na jachya? Chaal agla. | Didn't like this one? Next. |
| Next song | बदल दिया भाई। | Badal diya bhai. | Changed it, brother. |
| Previous song | पाछला गाणा फेर लगा दिया। | Paachhla gaana pher laga diya. | Put the previous song back on. |
| Previous song | ले, पहलड़ा गाणा। | Le, pehlada gaana. | Here, the earlier song. |
| Previous song | वो ए गाणा फेर तै। | Vo e gaana pher te. | That same song again. |
| Previous song | पाच्छै कर दिया भाई। | Paachhe kar diya bhai. | Went back, brother. |
| Pause music | गाणा रोक दिया। | Gaana rok diya. | Song paused. |
| Pause music | ले, म्यूजिक चुप कर दिया। | Le, music chup kar diya. | There, music is quiet. |
| Pause music | ठीक सै, थोड़ी शांति। | Theek sai, thodi shaanti. | OK, a little peace. |
| Pause music | रोक दिया भाई, जद मन करै फेर बजा दयूंगा। | Rok diya bhai, jad man kare pher baja dyunga. | Paused, brother; I'll play again whenever you like. |

## Small talk

- **"के हाल सै?"**: "Hey Ampli, के हाल सै?" / "how are you".
- **"कौन सै तू?"**: "Hey Ampli, कौन सै तू?" / "who are you".
- **"चुटकला सुणा"**: "Hey Ampli, चुटकला सुणा" / "tell me a joke".

| Event | Devanagari | Roman | English meaning |
|---|---|---|---|
| "के हाल सै?" | बढ़िया सूं, तू सुणा। | Badhiya sun, tu suna. | I'm great, you tell me. |
| "के हाल सै?" | मौज मैं सूं भाई, तू बता के हाल सै? | Mauj main sun bhai, tu bata ke haal sai? | Having fun, brother; how are you? |
| "के हाल सै?" | एकदम चकाचक! थारे साथ सफर मैं मजा आवै सै। | Ekdam chakachak! Thaare saath safar main maza aave sai. | Absolutely great! Travelling with you is fun. |
| "के हाल सै?" | ठाठ सैं भाई, बस तू धीरे चलाइए। | Thaath sain bhai, bas tu dheere chalaiye. | Living it up, brother; you just drive slowly. |
| "कौन सै तू?" | मैं एम्पली सूं, थारी हरियाणवी गाड्डी। | Main Ampli sun, thaari Haryanvi gaadi. | I'm Ampli, your Haryanvi car. |
| "कौन सै तू?" | मैं एम्पली सूं, दुनिया की पहली गाड्डी जो हरियाणवी बोल्लै सै! | Main Ampli sun, duniya ki pehli gaadi jo Haryanvi bolle sai! | I'm Ampli, the world's first car that talks in Haryanvi! |
| "कौन सै तू?" | थारा साथी एम्पली, रास्ता भी बताऊं, गाणे भी बजाऊं। | Thaara saathi Ampli, rasta bhi bataaun, gaane bhi bajaaun. | Your buddy Ampli: I show the way and play the songs. |
| "कौन सै तू?" | मैं एम्पली सूं, देसी बोली आळी थारी गाड्डी। | Main Ampli sun, desi boli aali thaari gaadi. | I'm Ampli, your car that speaks desi. |
| "चुटकला सुणा" | ताई बोली, गाड्डी धीरे चला। ताऊ बोल्या, धीरे ए चला रया सूं, सड़क ए तावळी भाज री सै! | Tai boli, gaadi dheere chala. Tau bolya, dheere e chala reya sun, sadak e taavli bhaaj ri sai! | Tai said: drive slowly. Tau said: I am; it's the road that's running fast! |
| "चुटकला सुणा" | मन्नै ताऊ तै पूछ्या, हॉर्न क्यूं बजाया? बोल्या, भैंस नै राम राम करी थी! | Manne tau te poochhya, horn kyun bajaya? Bolya, bhains nai Ram Ram kari thi! | I asked Tau why he honked. He said: I was saying Ram Ram to the buffalo! |
| "चुटकला सुणा" | जाम मैं फंस कै ताऊ बोल्या, इतणी देर मैं तो मेरी भैंस भी पहुँच ज्यांदी! | Jaam main phans ke tau bolya, itni der main to meri bhains bhi pahunch jyaandi! | Stuck in a jam, Tau said: my buffalo would have got there by now! |
| "चुटकला सुणा" | ताऊ बोल्या, जीपीएस की के जरूरत, मैं तो हर चौक पै रास्ता पूछ ल्यूं सूं! | Tau bolya, GPS ki ke zaroorat, main to har chowk pe rasta poochh lyun sun! | Tau said: who needs GPS? I ask the way at every crossing! |
| "चुटकला सुणा" | स्पीड ब्रेकर देख कै ताऊ बोल्या, यो सड़क भी थक कै बैठ गी! | Speed breaker dekh ke tau bolya, yo sadak bhi thak ke baith gi! | Seeing a speed breaker, Tau said: even the road got tired and sat down! |

## Papers and FASTag pass

- **Paper expiring soon**: When the car starts, a few days before insurance, PUC, RC, driving licence or FASTag KYC expires (Settings › Car papers; at most once a day per paper). `%1$s` is the paper. *(Standard style says a plain line instead.)*
- **Paper expired**: Same, after the date has passed.
- **Annual Pass trips running out**: When the car starts, with 10 or fewer FASTag Annual Pass trips left. `%1$d` is the trips.

| Event | Devanagari | Roman | English meaning |
|---|---|---|---|
| Paper expiring soon | भाई, %1$s की तारीख नेड़ै आ गी सै, टाइम पै करवा लिए। | Bhai, %1$s ki taareekh nede aa gi sai, time pe karwa liye. | Brother, the {paper} date is close; get it done on time. |
| Paper expiring soon | %1$s खतम होण आळा सै, इसी हफ्ते करवा ले। | %1$s khatam hon aala sai, isi hafte karwa le. | {paper} is about to run out; get it done this week. |
| Paper expiring soon | याद दिला दूं, %1$s की तारीख आण आळी सै। | Yaad dila dun, %1$s ki taareekh aan aali sai. | A reminder: the {paper} date is coming up. |
| Paper expiring soon | %1$s का टाइम पूरा होण आळा सै, चालान तै बच ले। | %1$s ka time poora hon aala sai, chalaan te bach le. | {paper} is nearly due; avoid a fine. |
| Paper expired | भाई, %1$s की तारीख निकल गी सै, जल्दी करवा ले। | Bhai, %1$s ki taareekh nikal gi sai, jaldi karwa le. | Brother, the {paper} has expired; renew it soon. |
| Paper expired | %1$s पुराणा हो ग्या सै, आज ए करवा ले। | %1$s puraana ho gaya sai, aaj e karwa le. | {paper} has lapsed; renew it today. |
| Paper expired | %1$s की तारीख जा ली, चालान कट सकै सै। | %1$s ki taareekh ja li, chalaan kat sake sai. | The {paper} date has passed; you could get a fine. |
| Paper expired | ध्यान दे भाई, %1$s खतम हो लिया सै। | Dhyan de bhai, %1$s khatam ho liya sai. | Note, brother: {paper} has run out. |
| Annual Pass trips running out | भाई, फास्टैग पास मैं बस %1$d ट्रिप बचे सैं। | Bhai, FASTag pass main bas %1$d trip bache sain. | Brother, only {n} trips left on the FASTag pass. |
| Annual Pass trips running out | पास के %1$d ट्रिप रह गे, ध्यान राखिए। | Pass ke %1$d trip reh ge, dhyan raakhiye. | {n} trips left on the pass, keep it in mind. |
| Annual Pass trips running out | सालाना पास मैं %1$d ट्रिप बाकी सैं। | Saalana pass main %1$d trip baaki sain. | {n} trips remain on the annual pass. |
| Annual Pass trips running out | %1$d ट्रिप और, फेर पास खतम। | %1$d trip aur, pher pass khatam. | {n} more trips, then the pass is used up. |

## Fuel finder and radar

- **Red-light camera ahead**: Before a red-light camera (OSM enforcement=traffic_signals). Pro.
- **Average-speed zone starts**: Entering an average-speed section. Pro.
- **Average-speed zone ends**: Leaving it. Pro.
- **Police check ahead**: A police check you reported is ahead (reports stay 2 hours, on this unit only). Pro.
- **Accident ahead**: A reported accident is ahead. Pro.
- **Traffic jam ahead**: A reported traffic jam is ahead. Pro.
- **Mobile camera ahead**: A reported mobile camera / speed gun is ahead. Pro.
- **Report saved**: After "Hey Ampli, police aage hai" or a tap on the report sheet. *(Haryanvi only; the Standard style says it plainly)*
- **Fuel pumps found**: "Hey Ampli, nearest petrol pump": the list and distances are on screen.
- **CNG pumps found**: "CNG pump kahan hai": the same for CNG.
- **EV chargers found**: "Charging station": the same for EV chargers.
- **No station nearby**: Nothing within 50 km, or no station data in this build.

| Event | Devanagari | Roman | English meaning |
|---|---|---|---|
| Red-light camera ahead | आगे लाल बत्ती का कैमरा सै, बत्ती देख कै चालिए। | Aage laal batti ka camera sai, batti dekh ke chaliye. | Red-light camera ahead, watch the signal. |
| Red-light camera ahead | भाई, आगे बत्ती पै कैमरा लाग्या सै। | Bhai, aage batti pe camera laagya sai. | Brother, there's a camera at the signal ahead. |
| Red-light camera ahead | लाल बत्ती आळे चौक पै कैमरा सै, जल्दबाजी ना करिए। | Laal batti aale chauk pe camera sai, jaldbaazi na kariye. | Camera at the red-light crossing ahead, don't rush. |
| Red-light camera ahead | आगे सिग्नल पै कैमरा सै, लाल हो तो रुक जाइयो। | Aage signal pe camera sai, laal ho to ruk jaiyo. | Camera at the signal ahead; stop if it's red. |
| Average-speed zone starts | औसत स्पीड आळा इलाका शुरू, पूरे रास्ते लिमिट मैं चालिए। | Ausat speed aala ilaaka shuru, poore raaste limit main chaliye. | Average-speed zone starts; keep to the limit all the way. |
| Average-speed zone starts | इब तै कैमरे औसत स्पीड नापैंगे, आराम तै चाल। | Ib te camere ausat speed naapenge, aaram te chaal. | From here the cameras measure your average speed; take it easy. |
| Average-speed zone starts | औसत स्पीड का जोन आ ग्या भाई, लिमिट स्क्रीन पै सै। | Ausat speed ka zone aa gaya bhai, limit screen pe sai. | Average-speed zone, brother; the limit is on the screen. |
| Average-speed zone starts | ध्यान दे, इड़ै तै औसत स्पीड गिणी जावैगी। | Dhyan de, ide te ausat speed gini jaavegi. | Careful, the average speed is counted from here. |
| Average-speed zone ends | औसत स्पीड आळा इलाका खतम। | Ausat speed aala ilaaka khatam. | Average-speed zone over. |
| Average-speed zone ends | जोन पार हो ग्या, बढ़िया चलाई भाई। | Zone paar ho gaya, badhiya chalaai bhai. | Zone done, nicely driven, brother. |
| Average-speed zone ends | औसत स्पीड का कैमरा पाछै रह ग्या। | Ausat speed ka camera paachhe reh gaya. | The average-speed camera is behind us. |
| Average-speed zone ends | जोन खतम, फेर भी आराम तै चालियो। | Zone khatam, pher bhi aaram te chaliyo. | Zone over; still drive easy. |
| Police check ahead | आगे पुलिस का नाका सै, कागज त्यार राखिए। | Aage police ka naka sai, kaagaz tyaar raakhiye. | Police check ahead, keep your papers ready. |
| Police check ahead | भाई, आगे पुलिस खड़ी सै, बेल्ट ला ले। | Bhai, aage police khadi sai, belt la le. | Brother, police ahead, put your belt on. |
| Police check ahead | आगे चेकिंग चाल री सै, आराम तै चाल। | Aage checking chaal ri sai, aaram te chaal. | Checking going on ahead, take it easy. |
| Police check ahead | पुलिस का नाका आण आळा सै, स्पीड कम कर ले। | Police ka naka aan aala sai, speed kam kar le. | A police check is coming up, slow down. |
| Accident ahead | आगे हादसा होया सै, धीरे चालिए। | Aage haadsa hoya sai, dheere chaliye. | Accident ahead, go slowly. |
| Accident ahead | भाई, आगे एक्सीडेंट सै, ध्यान तै। | Bhai, aage accident sai, dhyan te. | Brother, accident ahead, careful. |
| Accident ahead | आगे गाड्डी भिड़ री सैं, संभल कै चाल। | Aage gaadi bhid ri sain, sambhal ke chaal. | Vehicles have collided ahead, drive carefully. |
| Accident ahead | आगे हादसे की खबर सै, स्पीड घटा ले। | Aage haadse ki khabar sai, speed ghata le. | Accident reported ahead, reduce speed. |
| Traffic jam ahead | आगे जाम सै भाई, आराम तै। | Aage jaam sai bhai, aaram te. | Jam ahead, brother, take it easy. |
| Traffic jam ahead | आगे गाड्डी अटकी पड़ी सैं, धीरे हो ले। | Aage gaadi atki padi sain, dheere ho le. | Traffic is stuck ahead, slow down. |
| Traffic jam ahead | जाम आण आळा सै, टाइम लागैगा, सबर राखिए। | Jaam aan aala sai, time laagega, sabar raakhiye. | A jam is coming; it will take time, be patient. |
| Traffic jam ahead | आगे ट्रैफिक घणा सै, धीरे चालिए। | Aage traffic ghana sai, dheere chaliye. | Heavy traffic ahead, go slowly. |
| Mobile camera ahead | आगे मोबाइल कैमरा सै, स्पीड कम कर ले। | Aage mobile camera sai, speed kam kar le. | Mobile camera ahead, slow down. |
| Mobile camera ahead | भाई, आगे स्पीड गन आळे खड़े सैं। | Bhai, aage speed gun aale khade sain. | Brother, a speed-gun team is standing ahead. |
| Mobile camera ahead | आगे इंटरसेप्टर गाड्डी सै, लिमिट मैं चाल। | Aage interceptor gaadi sai, limit main chaal. | Interceptor ahead, stay within the limit. |
| Mobile camera ahead | मोबाइल कैमरा की खबर सै, ध्यान तै चालिए। | Mobile camera ki khabar sai, dhyan te chaliye. | Mobile camera reported, drive carefully. |
| Report saved | ठीक सै, निशान ला दिया। दो घंटे याद राखूंगा। | Theek sai, nishaan la diya. Do ghante yaad raakhunga. | OK, marked it. I'll remember it for two hours. |
| Report saved | बढ़िया भाई, इड़ै का निशान ला दिया। | Badhiya bhai, ide ka nishaan la diya. | Nice, brother, marked this spot. |
| Report saved | लिख लिया, इड़ै तै गुजरैगा तै चेता दूंगा। | Likh liya, ide te gujrega te cheta dunga. | Noted; I'll warn you when you pass here. |
| Report saved | रिपोर्ट हो गी, शुक्रिया भाई। | Report ho gi, shukriya bhai. | Reported, thanks brother. |
| Fuel pumps found | लै, नेड़े के पंप स्क्रीन पै सैं। | Lai, nede ke pump screen pe sain. | Here, the nearby pumps are on the screen. |
| Fuel pumps found | पंप मिल ग्या भाई, दूरी स्क्रीन पै देख ले। | Pump mil gaya bhai, doori screen pe dekh le. | Found a pump, brother; see the distance on screen. |
| Fuel pumps found | नेड़े का पंप ढूंढ दिया, बोल तै ले चालूं। | Nede ka pump dhoondh diya, bol te le chaalun. | Found the nearest pump; say so and I'll take you. |
| Fuel pumps found | पंप त्यार सैं, पहला आळा सबतै नेड़े सै। | Pump tyaar sain, pehla aala sabte nede sai. | Pumps ready; the first one is the nearest. |
| CNG pumps found | सीएनजी पंप मिल ग्या, दूरी स्क्रीन पै सै। | CNG pump mil gaya, doori screen pe sai. | Found a CNG pump; the distance is on screen. |
| CNG pumps found | लै भाई, नेड़े के सीएनजी पंप स्क्रीन पै सैं। | Lai bhai, nede ke CNG pump screen pe sain. | Here, brother, the nearby CNG pumps are on screen. |
| CNG pumps found | सीएनजी आळा पंप ढूंढ दिया, बोल तै ले चालूं। | CNG aala pump dhoondh diya, bol te le chaalun. | Found a CNG pump; say so and I'll take you. |
| CNG pumps found | गैस भरवाणी सै? पहला आळा सबतै नेड़े सै। | Gas bharwaani sai? Pehla aala sabte nede sai. | Need gas? The first one is the nearest. |
| EV chargers found | चार्जिंग स्टेशन मिल ग्या, स्क्रीन पै देख ले। | Charging station mil gaya, screen pe dekh le. | Found a charging station; see it on screen. |
| EV chargers found | लै, नेड़े के चार्जर स्क्रीन पै सैं। | Lai, nede ke charger screen pe sain. | Here, the nearby chargers are on screen. |
| EV chargers found | गाड्डी चार्ज करणी सै? पहला आळा सबतै नेड़े सै। | Gaadi charge karni sai? Pehla aala sabte nede sai. | Need a charge? The first one is the nearest. |
| EV chargers found | चार्जिंग स्टेशन ढूंढ दिया, बोल तै ले चालूं। | Charging station dhoondh diya, bol te le chaalun. | Found a charging station; say so and I'll take you. |
| No station nearby | भाई, नेड़े कोए पंप ना मिल्या। | Bhai, nede koye pump na milya. | Brother, no pump found nearby. |
| No station nearby | पचास किलोमीटर मैं कोए स्टेशन ना दिख्या। | Pachaas kilometre main koye station na dikhya. | No station within fifty kilometres. |
| No station nearby | माफ करिए, इड़ै आसपास किमे ना मिल्या। | Maaf kariye, ide aaspaas kime na milya. | Sorry, nothing found around here. |
| No station nearby | इस इलाके का डेटा कोन्या, नक्शे मैं देख ले। | Is ilaake ka data konya, nakshe main dekh le. | No data for this area; check the map. |

Recorded-voice tails for the fuel answer, said right after a recorded distance when it is within 15% of the real one ("दो किलोमीटर" + "पै सीएनजी पंप सै।"); otherwise the *found* line plays and the card shows the distance:

- **Pump after a distance (human voice)**
- **CNG pump after a distance (human voice)**
- **Charger after a distance (human voice)**

| Event | Devanagari | Roman | English meaning |
|---|---|---|---|
| Pump after a distance (human voice) | पै पंप सै। | pe pump sai. | {distance}: a pump there. |
| Pump after a distance (human voice) | दूर पेट्रोल पंप सै। | door petrol pump sai. | {distance} away there's a petrol pump. |
| Pump after a distance (human voice) | पै सबतै नेड़े का पंप सै। | pe sabte nede ka pump sai. | {distance}: the nearest pump. |
| Pump after a distance (human voice) | आगे पंप मिलैगा। | aage pump milega. | {distance} on you'll find a pump. |
| CNG pump after a distance (human voice) | पै सीएनजी पंप सै। | pe CNG pump sai. | {distance}: a CNG pump there. |
| CNG pump after a distance (human voice) | दूर सीएनजी मिलैगी। | door CNG milegi. | {distance} away you'll get CNG. |
| CNG pump after a distance (human voice) | पै सबतै नेड़े का सीएनजी पंप सै। | pe sabte nede ka CNG pump sai. | {distance}: the nearest CNG pump. |
| CNG pump after a distance (human voice) | आगे सीएनजी स्टेशन सै। | aage CNG station sai. | {distance} on there's a CNG station. |
| Charger after a distance (human voice) | पै चार्जिंग स्टेशन सै। | pe charging station sai. | {distance}: a charging station there. |
| Charger after a distance (human voice) | दूर चार्जर मिलैगा। | door charger milega. | {distance} away you'll find a charger. |
| Charger after a distance (human voice) | पै सबतै नेड़े का चार्जर सै। | pe sabte nede ka charger sai. | {distance}: the nearest charger. |
| Charger after a distance (human voice) | आगे चार्जिंग स्टेशन सै। | aage charging station sai. | {distance} on there's a charging station. |

## Human voice only (no names or numbers)

Said only by the recorded voices (Settings › Voice: Male "Ampli Bhai" / Female "Ampli Behen") instead of a line with a place, song, amount, distance or number in it: a recording can't say those, so the screen shows the detail (the toll card shows the rupees, the speed pane the limit, the caption the distance). The phone voice keeps the full lines above.

- **Searching (human voice)**: Instead of *Searching a place*.
- **Starting navigation (human voice)**: Instead of *Starting navigation*.
- **Route found (human voice)**: Instead of *Route found*.
- **After a distance (human voice)**: Said after a recorded distance ("चार सौ मीटर" + "आगे,"), then the turn.
- **Over the limit (human voice)**: Instead of *Over the limit*.
- **Camera after a distance (human voice)**: Said after a recorded distance: "तीन सौ मीटर" + "पै कैमरा सै…".
- **Toll deducted (human voice)**: Instead of both toll lines.
- **Break reminder (human voice)**: Instead of *Break reminder*.
- *Play a named song* uses the *Play music* lines; roundabout exits 1 to 5 are recorded as whole sentences.
- **Paper due after its name (human voice)**: Said right after a recorded paper name ("बीमा" + "की तारीख नेड़ै…") instead of *Paper expiring soon*.
- **Paper expired after its name (human voice)**: Same, instead of *Paper expired*.
- **Pass trips after a number (human voice)**: Said right after a recorded number ("पाँच" + "ट्रिप बचे सैं…") instead of *Annual Pass trips running out*.

| Event | Devanagari | Roman | English meaning |
|---|---|---|---|
| Searching (human voice) | ढूंढ रया सूं, रुक जा एक मिनट। | Dhoond reya sun, ruk ja ek minute. | Looking it up, wait a minute. |
| Searching (human voice) | ठहर जा भाई, देख रया सूं। | Thahar ja bhai, dekh reya sun. | Hold on, brother, I'm looking. |
| Searching (human voice) | इबे तलाश करूं। | Ibe talash karun. | Searching right away. |
| Searching (human voice) | चाल, पता करूं सूं कित सै। | Chaal, pata karun sun kit sai. | OK, finding out where it is. |
| Starting navigation (human voice) | चाल भाई, चालांगे। रास्ता मैं बताऊंगा। | Chal bhai, chalange. Rasta main bataunga. | Come on, let's go. I'll show the way. |
| Starting navigation (human voice) | रास्ता लाग ग्या, आराम तै चालियो। | Rasta laag gaya, aaram te chaliyo. | Route is set, drive easy. |
| Starting navigation (human voice) | ठीक सै, चाल पड़ां। ध्यान तै चलाणा भाई। | Theek sai, chaal padaan. Dhyan te chalana bhai. | OK, let's go. Drive carefully, brother. |
| Starting navigation (human voice) | चाल पड़े, बेल्ट ला ले। | Chaal pade, belt la le. | We're off, buckle up. |
| Route found (human voice) | चाल, रास्ता मिल गया, चाल पड़। | Chaal, rasta mil gaya, chaal pad. | Come on, got the route, let's go. |
| Route found (human voice) | रास्ता मिल ग्या भाई, दूरी स्क्रीन पै देख ले। | Rasta mil gaya bhai, doori screen pe dekh le. | Got the route, brother, the distance is on the screen. |
| Route found (human voice) | लै, रास्ता त्यार सै, चाल पड़। | Lai, rasta tyaar sai, chaal pad. | Here, the route is ready, let's go. |
| Route found (human voice) | बढ़िया, रास्ता लाग ग्या। | Badhiya, rasta laag gaya. | Great, the route is set. |
| After a distance (human voice) | आगे, | aage, | ahead, |
| After a distance (human voice) | आगे जा कै, | aage ja ke, | further on, |
| After a distance (human voice) | बाद, | baad, | after that, |
| After a distance (human voice) | पै, | pe, | at that point, |
| Over the limit (human voice) | स्पीड घटा ले भाई, लिमिट तै ऊपर सै। | Speed ghata le bhai, limit te upar sai. | Reduce speed, brother, you're over the limit. |
| Over the limit (human voice) | धीरे कर ले, आड़े लिमिट कम सै। | Dheere kar le, aade limit kam sai. | Slow down, the limit here is lower. |
| Over the limit (human voice) | लिमिट तै ऊपर ना, चालान कट ज्यागा। | Limit te upar na, chalaan kat jyaga. | Not above the limit, or you'll get a fine. |
| Over the limit (human voice) | भाई, स्पीड घणी सै, लिमिट स्क्रीन पै देख ले। | Bhai, speed ghani sai, limit screen pe dekh le. | Brother, too fast; the limit is on the screen. |
| Over the limit (human voice) | भाई मंदा कर ले, लिमिट तै ऊपर सै। | Bhai manda kar le, limit te upar sai. | Brother, slow down, you're over the limit. |
| Camera after a distance (human voice) | पै कैमरा सै, स्पीड कम कर ले! | pe camera sai, speed kam kar le! | {distance}: camera there, slow down! |
| Camera after a distance (human voice) | पै कैमरा आ रया सै, ध्यान तै। | pe camera aa reya sai, dhyan te. | {distance}: a camera is coming, careful. |
| Camera after a distance (human voice) | पै कैमरा खड़्या सै, लिमिट तै ऊपर मत जाइयो। | pe camera khadya sai, limit te upar mat jaiyo. | {distance}: a camera stands there, don't go over the limit. |
| Camera after a distance (human voice) | पै कैमरा सै, आराम तै। | pe camera sai, aaram te. | {distance}: camera there, take it easy. |
| Toll deducted (human voice) | टोल कट गया भाई, स्क्रीन पै देख ले कितणे कटे। | Toll kat gaya bhai, screen pe dekh le kitne kate. | Toll deducted, brother; see on screen how much. |
| Toll deducted (human voice) | फास्टैग तै टोल कट ग्या, चाल आगे। | FASTag te toll kat gaya, chaal aage. | The toll went from FASTag, let's move on. |
| Toll deducted (human voice) | टोल पार! पैसे कट गए। | Toll paar! Paise kat gaye. | Past the toll! Money deducted. |
| Toll deducted (human voice) | टोल का पैसा कट ग्या, बैलेंस स्क्रीन पै सै। | Toll ka paisa kat gaya, balance screen pe sai. | Toll paid; the balance is on the screen. |
| Break reminder (human voice) | घणी देर हो ली चलाते, थक ग्या होगा भाई, एक चाय हो जाए? | Ghani der ho li chalaate, thak gaya hoga bhai, ek chai ho jaye? | You've been driving a long time, tired? A cup of tea? |
| Break reminder (human voice) | थोड़ा रुक कै कमर सीधी कर ले भाई। | Thoda ruk ke kamar seedhi kar le bhai. | Stop a bit and stretch your back, brother. |
| Break reminder (human voice) | भाई घणी देर हो गी, ढाबा आवै तै रुक लियो। | Bhai ghani der ho gi, dhaba aave te ruk liyo. | Brother, it's been a while; stop at the next dhaba. |
| Break reminder (human voice) | स्टीयरिंग घणी देर तै पकड़ राख्या सै, पाँच मिनट टांग सीधी कर ले। | Steering ghani der te pakad raakhya sai, paanch minute taang seedhi kar le. | You've held the wheel a long time; stretch your legs for five minutes. |
| Paper due after its name (human voice) | की तारीख नेड़ै आ गी सै भाई, टाइम पै करवा लिए। | ki taareekh nede aa gi sai bhai, time pe karwa liye. | {paper}: the date is close, brother; get it done on time. |
| Paper due after its name (human voice) | खतम होण आळा सै, इसी हफ्ते करवा ले। | khatam hon aala sai, isi hafte karwa le. | {paper} is about to run out; get it done this week. |
| Paper due after its name (human voice) | की तारीख आण आळी सै, याद राखिए। | ki taareekh aan aali sai, yaad raakhiye. | {paper}: the date is coming up, remember. |
| Paper due after its name (human voice) | का टाइम पूरा होण आळा सै, चालान तै बच ले। | ka time poora hon aala sai, chalaan te bach le. | {paper} is nearly due; avoid a fine. |
| Paper expired after its name (human voice) | की तारीख निकल गी सै भाई, जल्दी करवा ले। | ki taareekh nikal gi sai bhai, jaldi karwa le. | {paper}: expired, brother; renew it soon. |
| Paper expired after its name (human voice) | पुराणा हो ग्या सै, आज ए करवा ले। | puraana ho gaya sai, aaj e karwa le. | {paper} has lapsed; renew it today. |
| Paper expired after its name (human voice) | की तारीख जा ली, चालान कट सकै सै। | ki taareekh ja li, chalaan kat sake sai. | {paper}: the date has passed; you could get a fine. |
| Paper expired after its name (human voice) | खतम हो लिया सै भाई, ध्यान दे। | khatam ho liya sai bhai, dhyan de. | {paper} has run out, brother, take note. |
| Pass trips after a number (human voice) | ट्रिप बचे सैं फास्टैग पास मैं। | trip bache sain FASTag pass main. | {n} trips left on the FASTag pass. |
| Pass trips after a number (human voice) | ट्रिप रह गे पास मैं, ध्यान राखिए। | trip reh ge pass main, dhyan raakhiye. | {n} trips left on the pass, keep it in mind. |
| Pass trips after a number (human voice) | ट्रिप बाकी सैं सालाना पास मैं। | trip baaki sain saalana pass main. | {n} trips remain on the annual pass. |
| Pass trips after a number (human voice) | ट्रिप और, फेर पास खतम। | trip aur, pher pass khatam. | {n} more trips, then the pass is used up. |

### Recorded distances

Turn and camera distances are rounded to the nearest of these (a tie goes to the shorter one) and stitched in front of the line: पचास, सौ, डेढ़ सौ, दो सौ, ढाई सौ, तीन सौ, साढ़े तीन सौ, चार सौ, साढ़े चार सौ, पाँच सौ, छह सौ, सात सौ, आठ सौ, नौ सौ मीटर; एक, डेढ़, दो, ढाई, तीन, चार, पाँच, दस किलोमीटर.

### Recorded numbers and paper names

Numbers one to ten (the pass "N trips left" and a countdown): एक, दो, तीन, चार, पाँच, छह, सात, आठ, नौ, दस.
Paper names, stitched in front of the paper tails: बीमा, पीयूसी, आरसी, ड्राइविंग लाइसेंस, फास्टैग केवाईसी, फास्टैग पास.

## Round 2 lines (lines-v2)

Added from the owner's lines-v2 list as more variants of the events above, plus three new events. They come after each event's first-round lines, so the recorded clips keep their ids and these continue the numbering (`welcome_09` and on). A recorded voice (Ampli Bhai / Ampli Behen) only uses one of these once the voice-pack workflow has recorded it and the clip's hash matches; until then the phone voice may say it, and `voice/lines.json` lists it for the next recording run.

- **FASTag balance low (after a toll)**: *(Haryanvi only)* said once a day right after the toll line, when the balance from the toll SMS is under the driver's alert level (FASTag › Low balance alert). `%1$s` is the rupees left.
- **FASTag balance low (human voice)**: the recorded voice says one of these instead (the balance shows on the toll card).
- **Night drive reminder**: *(Haryanvi only)* once a night, after 11 pm and more than 30 minutes into the drive.
- Lines marked *male / female* have the female voice's wording in a second row; they are recorded as `<id>_m` and `<id>_f`.
- Over-the-limit and break lines without a number are also used by the recorded voice (human voice stand-ins).

| Event | Devanagari | Roman | English meaning |
|---|---|---|---|
| Welcome on car start | जय माता दी! गाड्डी भी जाग गी अर मैं भी, बोल कित का गेड़ा सै? | Jai Mata Di! Gaadi bhi jaag gi ar main bhi, bol kit ka geda sai? | Jai Mata Di! The car's awake and so am I. Where's today's round? |
| Welcome on car start | ल्यो जी, आपणी सवारी त्यार सै! चूरमा खा कै आए हो के? | Lyo ji, aapni sawaari tyaar sai! Choorma kha ke aaye ho ke? | Here we go, our ride is ready! Had your choorma before coming? |
| Welcome on car start | गाड्डी त्यार, गाणे त्यार, मैं भी त्यार, बस बेल्ट की कसर सै! | Gaadi tyaar, gaane tyaar, main bhi tyaar, bas belt ki kasar sai! | Car ready, songs ready, me ready. Only the seat belt is missing! |
| Welcome on car start | के बात! आज तो गाड्डी भी नहा-धो कै त्यार लाग री सै। | Ke baat! Aaj to gaadi bhi naha-dho ke tyaar laag ri sai. | Wow! Even the car looks freshly bathed and dressed up today. |
| Welcome on car start | सीट सेट, शीशे सेट, बेल्ट ला ले, फेर बता कित चालां। | Seat set, sheeshe set, belt la le, pher bata kit chaalaan. | Seat set, mirrors set, belt on, then tell me where we go. |
| Welcome on car start | राम राम जी! ट्रैक्टर जिसा दम, अर मेरे जिसी बोली, चाल पड़ां? | Ram Ram ji! Tractor jisa dam, ar mere jisi boli, chaal padaan? | Ram Ram! Power like a tractor and a tongue like mine. Shall we go? |
| Welcome on car start | जय बाबा की! आज रास्ता मैं बताऊंगा, तू बस मौज तै चला। | Jai Baba Ki! Aaj raasta main bataaunga, tu bas mauj te chala. | Jai Baba Ki! I'll show the way today, you just drive and enjoy. *(male)* |
| Welcome on car start | जय बाबा की! आज रास्ता मैं बताऊंगी, तू बस मौज तै चला। | Jai Baba Ki! Aaj raasta main bataaungi, tu bas mauj te chala. | Jai Baba Ki! I'll show the way today, you just drive and enjoy. *(female)* |
| Goodbye (ACC off / parked) | पहुँच गे! गाड्डी लॉक कर दिए, अर मेरी याद आवै तो फेर स्टार्ट कर लिए। | Pahunch ge! Gaadi lock kar diye, ar meri yaad aave to pher start kar liye. | We're here! Lock the car, and if you miss me, just start it again. |
| Goodbye (ACC off / parked) | जय बाबा की! आज का गेड़ा कसूत रया, फेर मिलांगे। | Jai Baba Ki! Aaj ka geda kasoot raya, pher milaange. | Jai Baba Ki! Today's round was awesome. See you again. |
| Goodbye (ACC off / parked) | चाल, इब तू आराम कर, मैं भी थोड़ी झपकी ले ल्यूं। | Chaal, ib tu aaraam kar, main bhi thodi jhapki le lyun. | OK, you rest now, I'll grab a little nap too. |
| Goodbye (ACC off / parked) | चाबी, फोन, बटुआ, तीनूं ले लिए? फेर ना कहिए बताया कोनी! | Chaabi, phone, batua, teenun le liye? Pher na kahiye bataaya koni! | Keys, phone, wallet: got all three? Don't say I didn't tell you! |
| Goodbye (ACC off / parked) | गाड्डी नै भी सांस लेण दे, अर तू भी लस्सी पी कै आराम कर। | Gaadi nai bhi saans len de, ar tu bhi lassi pi ke aaraam kar. | Let the car breathe, and you have a lassi and rest. |
| Goodbye (ACC off / parked) | सफर पूरा! शीशे चढ़ा दिए, कोए चीज सीट पै तो ना छूटी? | Safar poora! Sheeshe chadha diye, koi cheez seat pe to na chhooti? | Trip done! Roll up the windows. Nothing left on the seat? |
| Goodbye (ACC off / parked) | चाल लाडले, थारे गेल्यां घणा बढ़िया लाग्या, फेर बुलाइए। | Chaal laadle, thaare gelyaan ghana badhiya laagya, pher bulaaiye. | Bye, dear. Loved riding with you. Call me again. |
| Goodbye (ACC off / parked) | घरां आळे बाट देखैं सैं, जा भीतर, गाड्डी की रखवाली मैं करूंगा। | Gharaan aale baat dekhain sain, ja bheetar, gaadi ki rakhwaali main karunga. | The family's waiting, go on in. I'll keep watch on the car. *(male)* |
| Goodbye (ACC off / parked) | घरां आळे बाट देखैं सैं, जा भीतर, गाड्डी की रखवाली मैं करूंगी। | Gharaan aale baat dekhain sain, ja bheetar, gaadi ki rakhwaali main karungi. | The family's waiting, go on in. I'll keep watch on the car. *(female)* |
| Arrived | ल्यो जी, पहुँच गे! गाड्डी साइड लगा ले आराम तै। | Lyo ji, pahunch ge! Gaadi side laga le aaraam te. | There we are! Pull over gently. |
| Arrived | मंजिल आ गी! बोल, कसूत रास्ता बताया के ना? | Manzil aa gi! Bol, kasoot raasta bataaya ke na? | We've arrived! Say it, wasn't that a cracking route? |
| Arrived | पहुँच गे, ना एक मोड़ छूट्या, ना एक चालान, लठ गाड़ दिए! | Pahunch ge, na ek mod chhootya, na ek chaalaan, lath gaad diye! | Arrived: not one missed turn, not one fine. Nailed it! |
| Arrived | आ ग्या ठिकाणा! उतरण तै पहलां पाच्छै देख लिए, कोए गाड्डी तो ना आ री। | Aa gya thikaana! Utran te pahlaan paachhai dekh liye, koi gaadi to na aa ri. | We're here! Check behind before you open the door for any traffic. |
| Arrived | पहुँच गे जी, टैम पै! इब चाय पाणी की बारी। | Pahunch ge ji, time pe! Ib chai paani ki baari. | Arrived on time! Now it's tea time. |
| Arrived | याड़े सै मंजिल! पार्किंग देख कै ढंग तै लगाइए। | Yaade sai manzil! Parking dekh ke dhang te lagaaiye. | This is the place! Find parking and park properly. |
| Over the limit | स्पीड घटा ले, लिमिट पार हो गी, यो हाईवे सै, अखाड़ा कोनी! | Speed ghata le, limit paar ho gi, yo highway sai, akhaada koni! | Slow down, you've crossed the limit. It's a highway, not a wrestling pit! |
| Over the limit | धीरे कर ले, आड़े की लिमिट सै %1$d | Dheere kar le, aade ki limit sai %1$d | Slow down, the limit here is {limit}. |
| Over the limit | पैर ढीला कर एक्सीलेटर तै, कती तावळ मत कर, पहुँच ज्यांगे। | Pair dheela kar accelerator te, kati taaval mat kar, pahunch jyaange. | Ease off the accelerator, no rush at all, we'll get there. |
| Over the limit | मंदी कर ले, लिमिट तै ऊपर सै, भैंस भी आपणी चाल चालै सै! | Mandi kar le, limit te oopar sai, bhains bhi aapni chaal chaalai sai! | Slow down, you're over. Even the buffalo keeps her own pace! |
| Over the limit | स्पीड कम कर, लिमिट तै बाहर सै, रेस तो कबड्डी मैं जीतिए। | Speed kam kar, limit te baahar sai, race to kabaddi main jeetiye. | Bring the speed down, you're over. Win races on the kabaddi ground. |
| Over the limit | ब्रेक हल्का सा दबा ले, लिमिट तै ऊपर सै, घरां चूरमा ठंडा ना होवैगा। | Brake halka sa daba le, limit te oopar sai, gharaan choorma thanda na hovaiga. | Touch the brake, you're over the limit. The choorma at home won't go cold. |
| Over the limit | धीरे कर ले, तू लिमिट तै आग्गै सै, लिमिट सै %1$d | Dheere kar le, tu limit te aaggai sai, limit sai %1$d | Slow down, you're above the limit. The limit is {limit}. |
| Over the limit | मंदी कर ले, स्पीड घणी सै, मैं गेल्यां सूं, जल्दी किस बात की? | Mandi kar le, speed ghani sai, main gelyaan sun, jaldi kis baat ki? | Slow down, too fast. I'm right here with you, what's the hurry? |
| Still over the limit | इब तो मंदी कर ले, दूसरी बार कहूं सूं, गाड्डी रॉकेट कोनी। | Ib to mandi kar le, doosri baar kahun sun, gaadi rocket koni. | Slow down now, I'm saying it twice. The car isn't a rocket. |
| Still over the limit | स्पीड इबे भी घणी सै, पैर हटा ले, मेरी सांस अटक री सै! | Speed ibe bhi ghani sai, pair hata le, meri saans atak ri sai! | Still too fast, take your foot off. I'm holding my breath here! |
| Still over the limit | मंदी कर ले! के करै सै, घरां सब बाट देखैं सैं। | Mandi kar le! Ke karai sai, gharaan sab baat dekhain sain. | Slow down! What are you doing? Everyone at home is waiting for you. |
| Still over the limit | ब्रेक पै पैर रख ले, इबे भी ऊपर सै, रेस का इनाम कोए ना देवै। | Brake pe pair rakh le, ibe bhi oopar sai, race ka inaam koi na devai. | Foot on the brake, still over. Nobody hands out prizes for this race. |
| Still over the limit | मंदी कर, कती मंदी, सड़क कित भाज री सै? आराम तै। | Mandi kar, kati mandi, sadak kit bhaaj ri sai? Aaraam te. | Slow down, properly slow. The road isn't running away. Easy. |
| Still over the limit | स्पीड कम कर, ना तो मैं ताई नै बता द्यूंगा! | Speed kam kar, na to main Taai nai bata dyunga! | Bring the speed down, or I'm telling Taai! *(male)* |
| Still over the limit | स्पीड कम कर, ना तो मैं ताई नै बता द्यूंगी! | Speed kam kar, na to main Taai nai bata dyungi! | Bring the speed down, or I'm telling Taai! *(female)* |
| Back under the limit | ये हुई ना बात! लठ गाड़ दिए, इब स्पीड बढ़िया सै। | Ye hui na baat! Lath gaad diye, ib speed badhiya sai. | That's it! Nailed it, the speed is right now. |
| Back under the limit | शाबाश! न्यूं ए चला, ताऊ भी राजी होग्या। | Shaabaash! Nyun e chala, Tau bhi raazi hogya. | Well done! Keep it like this, even Tau is pleased. |
| Back under the limit | जमा सही! इब लिमिट मैं सै, मौज तै चाल। | Jama sahi! Ib limit main sai, mauj te chaal. | Spot on! Within the limit now, enjoy the drive. |
| Back under the limit | के बात! ठंडा दिमाग, ठंडी गाड्डी, योए असली पहलवान। | Ke baat! Thanda dimaag, thandi gaadi, yoe asli pahalwaan. | Wow! Cool head, calm car. That's a real wrestler. |
| Back under the limit | बढ़िया! इब मैं भी चैन तै गाणे सुण सकूं सूं। | Badhiya! Ib main bhi chain te gaane sun sakun sun. | Great! Now I can relax and enjoy the songs too. |
| Back under the limit | शाबाश! इसी स्पीड पै तो सफर का नजारा आवै सै। | Shaabaash! Isi speed pe to safar ka nazaara aavai sai. | Well done! At this speed the trip is actually fun. |
| Over the limit at a camera | मंदी कर ले इबे, कैमरा सामणै सै, फेर बात करांगे! | Mandi kar le ibe, camera saamnai sai, pher baat karaange! | Slow down now, the camera is right ahead. We'll talk after! |
| Over the limit at a camera | ब्रेक, ब्रेक! स्पीड घणी सै, कैमरा नेड़ै सै। | Brake, brake! Speed ghani sai, camera nedai sai. | Brake, brake! Too fast, the camera is close. |
| Over the limit at a camera | धीरे कर, कैमरा आग्गै सै, ना तो चालान का लड्डू घरां पहुँचैगा। | Dheere kar, camera aaggai sai, na to chaalaan ka laddu gharaan pahunchaiga. | Slow down, camera ahead, or a 'challan laddoo' gets delivered home. |
| Over the limit at a camera | स्पीड कम कर, कैमरा आ ग्या, फोटो मैं हीरो बणणा जरूरी कोनी। | Speed kam kar, camera aa gya, photo main hero banna zaroori koni. | Slow down, camera's here. No need to be the hero of this photo. |
| Over the limit at a camera | मंदी कर ले तुरंत, कैमरा सै, पीस्से बचा, लस्सी पी। | Mandi kar le turant, camera sai, peesse bacha, lassi pi. | Slow down right now, camera. Save the money, buy a lassi. |
| Over the limit at a camera | इबे धीरे कर, कैमरा सामणै सै, घरां आळां नै चालान ना, तू चाहिए। | Ibe dheere kar, camera saamnai sai, gharaan aalaan nai chaalaan na, tu chaahiye. | Slow down now, camera ahead. Your family wants you home, not a fine. |
| Toll deducted | %1$s रुपये का टोल कट ग्या, खाते मैं बचे %2$s | %1$s rupaye ka toll kat gya, khaate main bache %2$s | ₹{amount} toll paid; {balance} left in the account. |
| Toll deducted | %1$s रुपये गए टोल आळां कै, फास्टैग मैं इब बाकी %2$s | %1$s rupaye gaye toll aalaan kai, FASTag main ib baaki %2$s | ₹{amount} went to the toll folks; {balance} remaining on FASTag. |
| Toll deducted | %1$s रुपये कटे, बैरियर खुल्या! बैलेंस सै %2$s | %1$s rupaye kate, barrier khulya! Balance sai %2$s | ₹{amount} deducted, barrier's up! Balance is {balance}. |
| Toll deducted | %1$s रुपये का टोल, इतणे मैं तो लस्सी के कई गिलास आ ज्यांदे! बचे %2$s | %1$s rupaye ka toll, itne main to lassi ke kai gilaas aa jyaande! Bache %2$s | ₹{amount} toll. That's a lot of lassi glasses! {balance} left. |
| Toll deducted | %1$s रुपये, टोल पार! सड़क आपणी, पीस्से सरकार के, बचे %2$s | %1$s rupaye, toll paar! Sadak aapni, peesse sarkaar ke, bache %2$s | ₹{amount}, through the toll! The road is ours, the money the government's. {balance} left. |
| Toll deducted | %1$s रुपये फास्टैग तै कटे, बिना रुके पार, बाकी बच्या %2$s | %1$s rupaye FASTag te kate, bina ruke paar, baaki bachya %2$s | ₹{amount} from FASTag, crossed without stopping; {balance} left. |
| Toll deducted | %1$s रुपये का टोल भर दिया, जेब हल्की, सड़क चिकणी, बैलेंस %2$s | %1$s rupaye ka toll bhar diya, jeb halki, sadak chikni, balance %2$s | ₹{amount} toll paid: lighter pocket, smoother road. Balance {balance}. |
| Toll deducted | %1$s रुपये कट गे, टोल आळा भी राजी अर हम भी, बाकी %2$s | %1$s rupaye kat ge, toll aala bhi raazi ar ham bhi, baaki %2$s | ₹{amount} gone; the toll man's happy and so are we. {balance} left. |
| Toll deducted (no balance) | %1$s रुपये का टोल कट ग्या, चाल आग्गै। | %1$s rupaye ka toll kat gya, chaal aaggai. | ₹{amount} toll deducted, on we go. |
| Toll deducted (no balance) | %1$s रुपये गए, बैरियर खुल ग्या, के बात! | %1$s rupaye gaye, barrier khul gya, ke baat! | ₹{amount} gone, barrier's open. Nice! |
| Toll deducted (no balance) | %1$s रुपये का टोल, फास्टैग नै चुपचाप भर दिया। | %1$s rupaye ka toll, FASTag nai chupchaap bhar diya. | ₹{amount} toll; FASTag quietly paid it. |
| Toll deducted (no balance) | %1$s रुपये कटे, ताऊ होंदा तो आध घंटा मोल-भाव करदा! | %1$s rupaye kate, Tau honda to aadh ghanta mol-bhaav karda! | ₹{amount} deducted. Tau would have haggled for half an hour! |
| Toll deducted (no balance) | %1$s रुपये टोल मैं गए, बैलेंस रुक कै देख लिए। | %1$s rupaye toll main gaye, balance ruk ke dekh liye. | ₹{amount} went on toll; check the balance once you've stopped. |
| Toll deducted (no balance) | %1$s रुपये, टोल पार! इब सीधा चाल, मौज तै। | %1$s rupaye, toll paar! Ib seedha chaal, mauj te. | ₹{amount}, through the toll! Now straight on, relaxed. |
| Break reminder | %1$s घंटे हो गे चलाते, ढाबे पै रुक, चाय अर कमर दोनूं सीधी कर ले। | %1$s ghante ho ge chalaate, dhaabe pe ruk, chai ar kamar donun seedhi kar le. | {hours} hours of driving; stop at a dhaba, have tea and stretch your back. |
| Break reminder | घणी देर हो ली, अगले ढाबे पै रुकांगे, परांठे बाट देखैं सैं! | Ghani der ho li, agle dhaabe pe rukaange, paraanthe baat dekhain sain! | It's been long, we'll stop at the next dhaba. The parathas are waiting! |
| Break reminder | स्टीयरिंग नै भी आराम चाहिए, पांच मिनट रुक कै टांग सीधी कर ले। | Steering nai bhi aaraam chaahiye, paanch minute ruk ke taang seedhi kar le. | Even the steering needs a rest; stop five minutes and stretch your legs. |
| Break reminder | आंख भारी लागैं तो रुक ज्या, झपकी दस मिनट की, जिंदगी सौ साल की। | Aankh bhaari laagain to ruk jya, jhapki das minute ki, zindagi sau saal ki. | Eyes heavy? Stop. A ten-minute nap for a hundred-year life. |
| Break reminder | थोड़ा ब्रेक ले ले, कबड्डी मैं भी हाफ टाइम होवै सै! | Thoda break le le, kabaddi main bhi half time hovai sai! | Take a short break; even kabaddi has half time! |
| Break reminder | %1$s घंटे का सफर हो ग्या, थोड़ा रुक ले, लस्सी का गिलास बाट देखै सै। | %1$s ghante ka safar ho gya, thoda ruk le, lassi ka gilaas baat dekhai sai. | {hours} hours on the road; stop a bit, a glass of lassi is waiting. |
| Parking spot saved | गाड्डी की जगह याद कर ली, तू भूल ज्या तो मेरे पै छोड़ दे। | Gaadi ki jagah yaad kar li, tu bhool jya to mere pe chhod de. | Saved the car's spot; if you forget, leave it to me. |
| Parking spot saved | पार्किंग सेव! भैंस गुम हो ज्या, पर गाड्डी ना गुमैगी। | Parking save! Bhains gum ho jya, par gaadi na gumaigi. | Parking saved! A buffalo might wander off, but the car won't get lost. |
| Parking spot saved | जगह नोट हो गी, लौट कै बस पूछ लिए, 'मेरी गाड्डी कित सै?' | Jagah note ho gi, laut ke bas poochh liye, 'meri gaadi kit sai?' | Spot noted. When you're back, just ask 'where's my car?' |
| Parking spot saved | ठीक सै, गाड्डी आड़े खड़ी सै, मन्नै बेरा सै, तू बेफिकर जा। | Theek sai, gaadi aade khadi sai, mannai bera sai, tu befikar ja. | OK, the car is parked here, I know it. Go without a worry. |
| Parking spot saved | पार्किंग याद कर ली, ताऊ आळी बात ना होवै, घंटा भर गाड्डी ढूंढते फिरां! | Parking yaad kar li, Tau aali baat na hovai, ghanta bhar gaadi dhoondhte phiraan! | Spot saved, so we don't do a Tau and hunt for the car for an hour! |
| Parking spot saved | जगह सेव हो गी, गाड्डी अर मैं दोनूं आड़े ए मिलांगे। | Jagah save ho gi, gaadi ar main donun aade e milaange. | Spot saved. The car and I will both be right here. |
| Seat belt reminder | बेल्ट ला ले पहलां, फेर बात करांगे, पहलवान भी कमर कस कै उतरै सै! | Belt la le pahlaan, pher baat karaange, pahalwaan bhi kamar kas ke utarai sai! | Belt on first, then we talk. Even a wrestler tightens up before the bout! |
| Seat belt reminder | बेल्ट ला ले, क्लिक की आवाज सुणा दे, मन्नै वोए गाणा सबतै बढ़िया लागै। | Belt la le, click ki aawaaz suna de, mannai voe gaana sabte badhiya laagai. | Belt up, let me hear that click. That's my favourite song. |
| Seat belt reminder | सीट बेल्ट पहन ले, यो फैशन ना, जिंदगी की गारंटी सै। | Seat belt pahan le, yo fashion na, zindagi ki guarantee sai. | Wear the seat belt. It's not fashion, it's a life guarantee. |
| Seat belt reminder | पहलां बेल्ट, फेर गाड्डी, ताई का हुक्म सै, टाळणा कोनी। | Pahlaan belt, pher gaadi, Taai ka hukam sai, taalna koni. | Belt first, then drive. Taai's orders, no dodging. |
| Seat belt reminder | बेल्ट ला ले, बावळी बूच मत बणिए! | Belt la le, baavli booch mat baniye! | Belt on, don't be a silly goose! |
| Seat belt reminder | बेल्ट ला ले जी, ना तो मैं रूस ज्यांगा! | Belt la le ji, na to main roos jyaanga! | Put your belt on, or I'll sulk! *(male)* |
| Seat belt reminder | बेल्ट ला ले जी, ना तो मैं रूस ज्यांगी! | Belt la le ji, na to main roos jyaangi! | Put your belt on, or I'll sulk! *(female)* |
| Heavy rain ahead | आग्गै मींह सै, धीरे कर अर वाइपर चला ले, भिजणा तो भैंस का काम सै! | Aaggai meenh sai, dheere kar ar wiper chala le, bhijna to bhains ka kaam sai! | Rain ahead, slow down and start the wipers. Getting soaked is the buffalo's job! |
| Heavy rain ahead | मींह आ रया सै, मंदी कर ले, ब्रेक हळवे मारिए, सड़क चिकणी सै। | Meenh aa raya sai, mandi kar le, brake halve maariye, sadak chikni sai. | Rain's coming, slow down and brake gently, the road is slippery. |
| Heavy rain ahead | आग्गै जोर का मींह, आग्गै आळी गाड्डी तै दूरी राख, पकौड़ां आळा मौसम सै! | Aaggai jor ka meenh, aaggai aali gaadi te doori raakh, pakaudaan aala mausam sai! | Heavy rain ahead, keep your distance from the car in front. Proper pakora weather! |
| Heavy rain ahead | बादळ बरसण लाग रे, हेडलाइट जला अर धीरे, ना तो गाड्डी नाव बण ज्यागी। | Baadal barsan laag re, headlight jala ar dheere, na to gaadi naav ban jyaagi. | The clouds have opened; headlights on and slow, or the car turns into a boat. |
| Heavy rain ahead | मींह मैं धीरे चलाइए, पाणी भरे खड्डे तै बच कै, यो जोहड़ कोनी, सड़क सै! | Meenh main dheere chalaaiye, paani bhare khadde te bach ke, yo johad koni, sadak sai! | Drive slowly in the rain, avoid flooded potholes. It's a road, not the village pond! |
| Heavy rain ahead | आग्गै मींह सै, स्पीड कम अर दूरी ज्यादा, गाणा भी बरसात आळा लगा द्यूं के? | Aaggai meenh sai, speed kam ar doori jyaada, gaana bhi barsaat aala laga dyun ke? | Rain ahead: less speed, more distance. Shall I put on a rain song too? |
| Thunderstorm ahead | आग्गै आंधी-तूफान सै, सुरक्षित जगह देख कै रुक ले, पेड़ां तै दूर। | Aaggai aandhi-toofaan sai, surakshit jagah dekh ke ruk le, pedaan te door. | Storm ahead; find a safe place to stop, away from trees. |
| Thunderstorm ahead | बिजळी कड़क री सै, धीरे कर, दोनूं हाथ स्टीयरिंग पै। | Bijli kadak ri sai, dheere kar, donun haath steering pe. | Lightning ahead; slow down, both hands on the wheel. |
| Thunderstorm ahead | आंधी आण आळी सै, मंदी कर ले, थोड़ी वार रुक ल्यांगे, कोए बात ना। | Aandhi aan aali sai, mandi kar le, thodi vaar ruk lyaange, koi baat na. | A dust storm is coming; slow down. We can wait a bit, no problem. |
| Thunderstorm ahead | आसमान गरज रया सै, धीरे चला, बादळ आज कती रूसे होए सैं। | Aasmaan garaj raya sai, dheere chala, baadal aaj kati roose hoye sain. | The sky is roaring; drive slowly. The clouds are properly sulking today. |
| Thunderstorm ahead | आग्गै आंधी, हेडलाइट जला अर स्पीड कम, टीन-टप्पर उड़ सकैं सैं। | Aaggai aandhi, headlight jala ar speed kam, teen-tappar ud sakain sain. | Storm ahead; headlights on, speed down. Loose sheets can fly about. |
| Thunderstorm ahead | तूफान आग्गै सै, जल्दबाजी ना, पहुँचणा जरूरी सै, जल्दी पहुँचणा ना। | Toofaan aaggai sai, jaldbaazi na, pahunchna zaroori sai, jaldi pahunchna na. | Storm ahead, no hurrying. Getting there matters, not getting there fast. |
| Fog ahead | आग्गै धुंध सै, धीरे कर अर फॉग लाइट जला ले, दीखै कोनी तो ओवरटेक ना। | Aaggai dhundh sai, dheere kar ar fog light jala le, deekhai koni to overtake na. | Fog ahead, slow down and switch on the fog lights. Can't see? Don't overtake. |
| Fog ahead | धुंध घणी सै, मंदी कर अर दूरी राख, आज सड़क भी कंबल ओढ़ री सै! | Dhundh ghani sai, mandi kar ar doori raakh, aaj sadak bhi kambal odh ri sai! | Thick fog, slow down and keep your distance. Even the road has a blanket on today! |
| Fog ahead | आग्गै कुछ ना दीखता, पीळी बत्ती जला ले अर हळवा चाल। | Aaggai kuchh na deekhta, peeli batti jala le ar halva chaal. | Can't see ahead, switch on the hazard lights and go slowly. |
| Fog ahead | धुंध मैं ओवरटेक कती ना, सफेद पट्टी देख कै चाल, मैं गेल्यां सूं। | Dhundh main overtake kati na, safed patti dekh ke chaal, main gelyaan sun. | No overtaking in fog at all. Follow the white line, I'm with you. |
| Fog ahead | धुंध आ गी, स्पीड कम कर, जणू पूरा गाम एकसाथ चूल्हा जला रया हो! | Dhundh aa gi, speed kam kar, janu poora gaam eksaath choolha jala raya ho! | Fog's here, slow down. Like the whole village lit their stoves at once! |
| Fog ahead | आग्गै धुंध, धीरे अर ध्यान तै, हॉर्न कम, नजर ज्यादा। | Aaggai dhundh, dheere ar dhyaan te, horn kam, nazar jyaada. | Fog ahead, slow and careful. Less horn, more eyes. |
| Heat | घणी गर्मी सै, पाणी पी अर AC चला, बाहर तावड़ा पापड़ सेक रया सै! | Ghani garmi sai, paani pi ar AC chala, baahar taavda paapad sek raya sai! | It's very hot, drink water and switch on the AC. The sun outside is roasting papads! |
| Heat | लू चाल री सै, पाणी पीता रहिए, टंकी गाड्डी की भी, अर थारी भी। | Loo chaal ri sai, paani peeta rahiye, tanki gaadi ki bhi, ar thaari bhi. | Hot winds blowing, keep drinking water. The car's tank and yours. |
| Heat | घाम घणा सै, गाड्डी छां मैं खड़ी करिए, ना तो सीट तवा बण ज्यागी। | Ghaam ghana sai, gaadi chhaan main khadi kariye, na to seat tawa ban jyaagi. | Fierce sun, park in the shade or the seat turns into a griddle. |
| Heat | आज तावड़ा कसूत सै, सिर दुखै तो रुक कै लस्सी पी ले। | Aaj taavda kasoot sai, sir dukhai to ruk ke lassi pi le. | The sun's fierce today; if your head aches, stop and have a lassi. |
| Heat | बाहर भट्टी जली सै, AC चला ले अर शीशे चढ़ा ले। | Baahar bhatti jali sai, AC chala le ar sheeshe chadha le. | It's a furnace outside; AC on and windows up. |
| Heat | गर्मी घणी सै, टायर अर इंजन का ध्यान राख, अर आपणा भी। | Garmi ghani sai, tyre ar engine ka dhyaan raakh, ar aapna bhi. | Very hot: look after the tyres and engine, and yourself. |
| Over the limit (human voice) | स्पीड घटा ले, लिमिट पार हो गी, यो हाईवे सै, अखाड़ा कोनी! | Speed ghata le, limit paar ho gi, yo highway sai, akhaada koni! | Slow down, you've crossed the limit. It's a highway, not a wrestling pit! |
| Over the limit (human voice) | पैर ढीला कर एक्सीलेटर तै, कती तावळ मत कर, पहुँच ज्यांगे। | Pair dheela kar accelerator te, kati taaval mat kar, pahunch jyaange. | Ease off the accelerator, no rush at all, we'll get there. |
| Over the limit (human voice) | मंदी कर ले, लिमिट तै ऊपर सै, भैंस भी आपणी चाल चालै सै! | Mandi kar le, limit te oopar sai, bhains bhi aapni chaal chaalai sai! | Slow down, you're over. Even the buffalo keeps her own pace! |
| Over the limit (human voice) | स्पीड कम कर, लिमिट तै बाहर सै, रेस तो कबड्डी मैं जीतिए। | Speed kam kar, limit te baahar sai, race to kabaddi main jeetiye. | Bring the speed down, you're over. Win races on the kabaddi ground. |
| Over the limit (human voice) | ब्रेक हल्का सा दबा ले, लिमिट तै ऊपर सै, घरां चूरमा ठंडा ना होवैगा। | Brake halka sa daba le, limit te oopar sai, gharaan choorma thanda na hovaiga. | Touch the brake, you're over the limit. The choorma at home won't go cold. |
| Over the limit (human voice) | मंदी कर ले, स्पीड घणी सै, मैं गेल्यां सूं, जल्दी किस बात की? | Mandi kar le, speed ghani sai, main gelyaan sun, jaldi kis baat ki? | Slow down, too fast. I'm right here with you, what's the hurry? |
| Camera after a distance (human voice) | पै कैमरा सै, लिमिट मैं चाल, फोटो खिंचवाणी हो तो रुक कै खिंचवाइए! | pe camera sai, limit main chaal, photo khinchwaani ho to ruk ke khinchwaaiye! | Camera in {distance}, keep to the limit. Want a photo? Stop and pose for one! |
| Camera after a distance (human voice) | पै कैमरा सै, मंदी कर ले, यो सेल्फी आळा कैमरा कोनी! | pe camera sai, mandi kar le, yo selfie aala camera koni! | Camera in {distance}, slow down. This one doesn't take selfies! |
| Camera after a distance (human voice) | पै स्पीड कैमरा, स्पीड कम कर, कैमरा नै बस स्माइल दिखाइए। | pe speed camera, speed kam kar, camera nai bas smile dikhaaiye. | Speed camera in {distance}, slow down. Show it only your smile. |
| Camera after a distance (human voice) | पै कैमरा सै, लिमिट मैं राख, ताऊ बरगी आंख सै उसकी, सब देखै सै! | pe camera sai, limit main raakh, Tau bargi aankh sai uski, sab dekhai sai! | Camera in {distance}, stay in the limit. It has eyes like Tau, sees everything! |
| Camera after a distance (human voice) | पै कैमरा, स्पीड लिमिट मैं, चालान के पीस्यां की जलेबी खा ल्यांगे। | pe camera, speed limit main, chaalaan ke peesyaan ki jalebi kha lyaange. | Camera in {distance}, keep to the limit. We'll spend the fine money on jalebis. |
| Camera after a distance (human voice) | पै कैमरा सै, धीरे कर ले, सरकारी फोटोग्राफर खड़्या सै! | pe camera sai, dheere kar le, sarkaari photographer khadya sai! | Camera in {distance}, slow down. The government photographer is waiting! |
| Camera after a distance (human voice) | पै कैमरा सै, स्पीड लिमिट मैं, गाड्डी का नाम खराब ना होण दे। | pe camera sai, speed limit main, gaadi ka naam kharaab na hon de. | Camera in {distance}, keep to the limit. Don't give the car a bad name. |
| Camera after a distance (human voice) | पै कैमरा सै, मंदी कर ले, शाबाश, तू तो पहलां ए स्याणा सै। | pe camera sai, mandi kar le, shaabaash, tu to pahlaan e syaana sai. | Camera in {distance}, slow down. Good, you're sensible anyway. |
| Toll deducted (human voice) | टोल कट ग्या, बैरियर खुल्या, बिना रुके पार, के बात! | Toll kat gya, barrier khulya, bina ruke paar, ke baat! | Toll paid, barrier up, through without stopping. Nice! |
| Toll deducted (human voice) | फास्टैग नै काम कर दिया, कितणे कटे, स्क्रीन पै सै। | FASTag nai kaam kar diya, kitne kate, screen pe sai. | FASTag did its job; how much went is on the screen. |
| Toll deducted (human voice) | टोल पार! पीस्से कटे, पर सड़क भी चिकणी मिलैगी। | Toll paar! Peesse kate, par sadak bhi chikni milaigi. | Through the toll! Money gone, but the road will be smooth. |
| Toll deducted (human voice) | टोल आळे भाई नै राम राम, पीस्से फास्टैग तै कट गे। | Toll aale bhai nai Ram Ram, peesse FASTag te kat ge. | Ram Ram to the toll man; FASTag paid. |
| Toll deducted (human voice) | ताऊ होंदा तो मोल-भाव करदा, फास्टैग नै चुपचाप भर दिया! | Tau honda to mol-bhaav karda, FASTag nai chupchaap bhar diya! | Tau would have haggled; FASTag just paid quietly! |
| Toll deducted (human voice) | टोल हो ग्या, हिसाब स्क्रीन पै सै, रुक कै देख लिए। | Toll ho gya, hisaab screen pe sai, ruk ke dekh liye. | Toll done; the details are on screen, look once you've stopped. |
| Break reminder (human voice) | घणी देर हो ली, अगले ढाबे पै रुकांगे, परांठे बाट देखैं सैं! | Ghani der ho li, agle dhaabe pe rukaange, paraanthe baat dekhain sain! | It's been long, we'll stop at the next dhaba. The parathas are waiting! |
| Break reminder (human voice) | स्टीयरिंग नै भी आराम चाहिए, पांच मिनट रुक कै टांग सीधी कर ले। | Steering nai bhi aaraam chaahiye, paanch minute ruk ke taang seedhi kar le. | Even the steering needs a rest; stop five minutes and stretch your legs. |
| Break reminder (human voice) | आंख भारी लागैं तो रुक ज्या, झपकी दस मिनट की, जिंदगी सौ साल की। | Aankh bhaari laagain to ruk jya, jhapki das minute ki, zindagi sau saal ki. | Eyes heavy? Stop. A ten-minute nap for a hundred-year life. |
| Break reminder (human voice) | थोड़ा ब्रेक ले ले, कबड्डी मैं भी हाफ टाइम होवै सै! | Thoda break le le, kabaddi main bhi half time hovai sai! | Take a short break; even kabaddi has half time! |
| Red-light camera ahead | आग्गै लाल बत्ती का कैमरा सै, लाल पै रुकिए, बत्ती तै रेस ना जीतदे। | Aaggai laal batti ka camera sai, laal pe rukiye, batti te race na jeetde. | Red-light camera ahead, stop if it's red. Nobody beats a signal in a race. |
| Red-light camera ahead | पीळी बत्ती पै धीरे, लाल पै रुकणा, आग्गै कैमरा देखण लाग रया सै। | Peeli batti pe dheere, laal pe rukna, aaggai camera dekhan laag raya sai. | Slow on amber, stop on red. A camera is watching ahead. |
| Red-light camera ahead | आग्गै बत्ती पै कैमरा सै, लाल पै रुक ज्या, ताऊ भी रुकै सै, भैंस भी! | Aaggai batti pe camera sai, laal pe ruk jya, Tau bhi rukai sai, bhains bhi! | Camera at the signal ahead, stop on red. Tau stops, even the buffalo stops! |
| Red-light camera ahead | लाल बत्ती आळा चौक आ रया, रुकण की त्यारी राख, कैमरा कती चौकस सै। | Laal batti aala chauk aa raya, rukan ki tyaari raakh, camera kati chaukas sai. | Signal junction coming, be ready to stop. That camera is very alert. |
| Red-light camera ahead | आग्गै सिग्नल कैमरा सै, लाल पै रुकिए, दो मिनट मैं कोए पहाड़ ना टूटै। | Aaggai signal camera sai, laal pe rukiye, do minute main koi pahaad na tootai. | Signal camera ahead, stop on red. Two minutes won't bring the sky down. |
| Red-light camera ahead | बत्ती पै कैमरा सै, लाल हो तो रुक, इतणे मैं एक गाणा और सुण ल्यांगे। | Batti pe camera sai, laal ho to ruk, itne main ek gaana aur sun lyaange. | Camera at the signal, stop if red. We'll catch one more song meanwhile. |
| Average-speed zone starts | औसत स्पीड आळा इलाका शुरू, पूरे रास्ते लिमिट मैं, यो कैमरा पूरा हिसाब राखै सै! | Ausat speed aala ilaaka shuru, poore raaste limit main, yo camera poora hisaab raakhai sai! | Average-speed zone starts, stay in the limit all the way. This camera keeps full accounts! |
| Average-speed zone starts | इब तै औसत स्पीड नपैगी, आराम तै चाल, आड़े मुनीम जी बैठे सैं। | Ib te ausat speed napaigi, aaraam te chaal, aade munim ji baithe sain. | Average speed is measured from here, go easy. The accountant is on duty. |
| Average-speed zone starts | औसत स्पीड जोन आ ग्या, लिमिट मैं राख, बीच मैं तावळ तो हिसाब खराब। | Ausat speed zone aa gya, limit main raakh, beech main taaval to hisaab kharaab. | Average-speed zone, keep to the limit. Rushing midway spoils the sum. |
| Average-speed zone starts | औसत स्पीड जोन शुरू, एकसार चलाइए, कछुए अर खरगोश की कहाणी याद सै ना? | Ausat speed zone shuru, eksaar chalaaiye, kachhue ar khargosh ki kahaani yaad sai na? | Average-speed zone starts, drive steady. Remember the tortoise and the hare? |
| Average-speed zone starts | ध्यान दे, आड़े तै औसत स्पीड गिणी जावैगी, लिमिट मैं, मौज मैं। | Dhyaan de, aade te ausat speed gini jaavaigi, limit main, mauj main. | Heads up, average speed is counted from here. In the limit, in good spirits. |
| Average-speed zone starts | जोन शुरू सै, लिमिट मैं चाल, कैमरा आड़े भी देखै, ओड़ै भी। | Zone shuru sai, limit main chaal, camera aade bhi dekhai, odai bhi. | Zone starts, stay in the limit. Cameras watch here and there too. |
| Average-speed zone ends | औसत स्पीड जोन खतम, बढ़िया चलाई, पास हो ग्या, फर्स्ट डिवीजन! | Ausat speed zone khatam, badhiya chalaai, paas ho gya, first division! | Average-speed zone over, well driven. Passed with first division! |
| Average-speed zone ends | जोन पार, शाबाश! फेर भी लिमिट का ध्यान राखिए। | Zone paar, shaabaash! Pher bhi limit ka dhyaan raakhiye. | Zone done, well done! Still mind the limit. |
| Average-speed zone ends | मुनीम जी पाच्छै रह गे, पर हम तो आराम तै ए चालांगे। | Munim ji paachhai rah ge, par ham to aaraam te e chaalaange. | The accountant's behind us, but we'll keep it easy anyway. |
| Average-speed zone ends | औसत स्पीड का इलाका खतम, लठ गाड़ दिए, इब भी मौज तै। | Ausat speed ka ilaaka khatam, lath gaad diye, ib bhi mauj te. | Average-speed stretch over. Nailed it, keep it relaxed. |
| Average-speed zone ends | जोन खतम! कछुआ जीत ग्या, जिसा हमेशा जीतै सै। | Zone khatam! Kachhua jeet gya, jisa hamesha jeetai sai. | Zone over! The tortoise won, like it always does. |
| Average-speed zone ends | पार हो ग्या जोन, सड़क खुली सै, पर लिमिट वोए सै। | Paar ho gya zone, sadak khuli sai, par limit voe sai. | Through the zone. The road's open, but the limit is the same. |
| Police check ahead | आग्गै पुलिस का नाका सै, बेल्ट ला ले अर कागज त्यार राख। | Aaggai police ka naaka sai, belt la le ar kaagaz tyaar raakh. | Police check ahead; belt on and keep your papers ready. |
| Police check ahead | आग्गै चेकिंग सै, स्पीड कम, पुलिस आळे भाइयां नै राम राम करदे जाइए। | Aaggai checking sai, speed kam, police aale bhaiyaan nai Ram Ram karde jaaiye. | Checking ahead, slow down, and greet the police brothers with a Ram Ram. |
| Police check ahead | नाका आ रया, धीरे कर, लाइसेंस, आरसी सब गेल्यां सैं ना? | Naaka aa raya, dheere kar, licence, RC sab gelyaan sain na? | Checkpoint coming, slow down. Licence and RC on you, right? |
| Police check ahead | आग्गै पुलिस खड़ी सै, आराम तै, सीधे ड्राइवर नै के फिकर! | Aaggai police khadi sai, aaraam te, seedhe driver nai ke fikar! | Police ahead, easy. An honest driver has nothing to worry about! |
| Police check ahead | चेकिंग चाल री सै, कहवैं तो रुक ज्याइए, शीशा नीचे अर स्माइल ऊपर। | Checking chaal ri sai, kahvain to ruk jyaaiye, sheesha neeche ar smile oopar. | Checking on; stop if they signal. Window down, smile up. |
| Police check ahead | आग्गै नाका सै, लिमिट मैं अर बेल्ट मैं, पुलिस भी राजी, घरआळे भी। | Aaggai naaka sai, limit main ar belt main, police bhi raazi, gharaale bhi. | Checkpoint ahead; in the limit and belted up. Police happy, family happy. |
| Traffic jam ahead | आग्गै जाम सै, आराम तै, हॉर्न तै जाम ना खुलदा, सबर तै खुलै सै। | Aaggai jaam sai, aaraam te, horn te jaam na khulda, sabar te khulai sai. | Jam ahead, easy. Horns don't clear a jam, patience does. |
| Traffic jam ahead | आग्गै गाड्डी अटकी पड़ी सैं, धीरे हो ले, गाणा बदल द्यूं के? | Aaggai gaadi atki padi sain, dheere ho le, gaana badal dyun ke? | Cars are stuck ahead, slow down. Shall I change the song? |
| Traffic jam ahead | जाम आ रया, टैम लागैगा, चाल, मैं चुटकला सुणाऊं? | Jaam aa raya, time laagaiga, chaal, main chutkala sunaaun? | Jam coming, it'll take time. Shall I tell you a joke? |
| Traffic jam ahead | आग्गै ट्रैफिक घणा सै, बार-बार लेन ना बदलिए, सबनै घरां जाणा सै। | Aaggai traffic ghana sai, baar-baar lane na badaliye, sabnai gharaan jaana sai. | Heavy traffic ahead, don't keep switching lanes. Everyone's going home. |
| Traffic jam ahead | जाम सै आग्गै, ठंडा दिमाग राख, गुस्सा अर क्लच दोनूं हळवे। | Jaam sai aaggai, thanda dimaag raakh, gussa ar clutch donun halve. | Jam ahead; keep a cool head. Go easy on the temper and the clutch. |
| Traffic jam ahead | आग्गै जाम, धीरे हो ले, आज सड़क बारात की चाल चाल री सै। | Aaggai jaam, dheere ho le, aaj sadak baaraat ki chaal chaal ri sai. | Jam ahead, slow down. The road's moving at wedding-procession pace. |
| Mobile camera ahead | आग्गै स्पीड गन सै, लिमिट मैं चाल, पुलिस आळे भाइयां नै राम राम। | Aaggai speed gun sai, limit main chaal, police aale bhaiyaan nai Ram Ram. | Speed gun ahead, stay in the limit. Ram Ram to the police brothers. |
| Mobile camera ahead | आग्गै मोबाइल कैमरा सै, मंदी कर ले, छुप कै खड़्या सै, पर मन्नै बेरा सै! | Aaggai mobile camera sai, mandi kar le, chhup ke khadya sai, par mannai bera sai! | Mobile camera ahead, slow down. It's hiding, but I know! |
| Mobile camera ahead | आग्गै इंटरसेप्टर सै, लिमिट मैं, आज शरीफ ड्राइवर बण कै दिखा। | Aaggai interceptor sai, limit main, aaj shareef driver ban ke dikha. | Interceptor ahead, stay in the limit. Show them a model driver today. |
| Mobile camera ahead | आग्गै स्पीड नापण आळे सैं, धीरे कर, म्हारी तो रोज की आराम आळी चाल सै। | Aaggai speed naapan aale sain, dheere kar, mhaari to roj ki aaraam aali chaal sai. | Speed checkers ahead, slow down. Easy pace is our daily style anyway. |
| Mobile camera ahead | मोबाइल कैमरा की खबर सै, स्पीड कम, फोटो तो ब्याह मैं खिंचवावांगे। | Mobile camera ki khabar sai, speed kam, photo to byaah main khinchwaawange. | Mobile camera reported, slow down. We'll pose for photos at a wedding. |
| Mobile camera ahead | आग्गै स्पीड गन सै, लिमिट मैं राख, बढ़िया, तू तो पहलां ए आराम तै सै। | Aaggai speed gun sai, limit main raakh, badhiya, tu to pahlaan e aaraam te sai. | Speed gun ahead, keep in the limit. Good, you're already easy on it. |
| FASTag balance low (after a toll) | %1$s रुपये ए बचे सैं फास्टैग मैं, रुक कै रिचार्ज कर लिए। | %1$s rupaye e bache sain FASTag main, ruk ke recharge kar liye. | Only ₹{balance} left on FASTag; recharge once you've stopped. |
| FASTag balance low (after a toll) | %1$s रुपये मैं अगला टोल भी मुश्किल, घरां पहुँच कै रिचार्ज कर दिए। | %1$s rupaye main agla toll bhi mushkil, gharaan pahunch ke recharge kar diye. | ₹{balance} may not cover the next toll; recharge when you get home. |
| FASTag balance low (after a toll) | फास्टैग की टंकी खाली होण लाग री, बचे बस %1$s | FASTag ki tanki khaali hon laag ri, bache bas %1$s | The FASTag 'tank' is running dry, just {balance} left. |
| FASTag balance low (after a toll) | फास्टैग भूखा सै, रुक कै रिचार्ज का चूरमा खुवा दिए। | FASTag bhookha sai, ruk ke recharge ka choorma khuwa diye. | FASTag is hungry; once you stop, feed it a recharge 'choorma'. |
| FASTag balance low (after a toll) | बैलेंस कम सै, ना तो टोल पै अटकांगे, गाड्डी रोक कै रिचार्ज करिए। | Balance kam sai, na to toll pe atkaange, gaadi rok ke recharge kariye. | Balance is low or we'll get stuck at a toll; stop the car, then recharge. |
| FASTag balance low (after a toll) | फास्टैग मैं पीस्से थोड़े सैं, अगले ढाबे पै रुक कै रिचार्ज कर लियो। | FASTag main peesse thode sain, agle dhaabe pe ruk ke recharge kar liyo. | Not much money on FASTag; recharge when we stop at the next dhaba. |
| FASTag balance low (human voice) | फास्टैग भूखा सै, रुक कै रिचार्ज का चूरमा खुवा दिए। | FASTag bhookha sai, ruk ke recharge ka choorma khuwa diye. | FASTag is hungry; once you stop, feed it a recharge 'choorma'. |
| FASTag balance low (human voice) | बैलेंस कम सै, ना तो टोल पै अटकांगे, गाड्डी रोक कै रिचार्ज करिए। | Balance kam sai, na to toll pe atkaange, gaadi rok ke recharge kariye. | Balance is low or we'll get stuck at a toll; stop the car, then recharge. |
| FASTag balance low (human voice) | फास्टैग मैं पीस्से थोड़े सैं, अगले ढाबे पै रुक कै रिचार्ज कर लियो। | FASTag main peesse thode sain, agle dhaabe pe ruk ke recharge kar liyo. | Not much money on FASTag; recharge when we stop at the next dhaba. |
| Night drive reminder | रात हो गी, हळवे चलाइए, नींद आवै तो रुक ल्यांगे, कोए बात ना। | Raat ho gi, halve chalaaiye, neend aavai to ruk lyaange, koi baat na. | It's night, drive gently. If you get sleepy we'll stop, no problem. |
| Night drive reminder | रात का सफर सै, हेडलाइट ठीक, स्पीड कम, मैं जाग्गूं सूं थारे गेल्यां। | Raat ka safar sai, headlight theek, speed kam, main jaaggun sun thaare gelyaan. | Night trip: headlights right, speed down. I'm awake with you. |
| Night drive reminder | आंख भारी लागैं तो बता दिए, ढाबे पै चाय पी ल्यांगे। | Aankh bhaari laagain to bata diye, dhaabe pe chai pi lyaange. | If your eyes feel heavy, tell me; we'll have tea at a dhaba. |
| Night drive reminder | सामणै की लाइट चुंधियावै तो उल्टे हाथ की सफेद पट्टी पै नजर राख। | Saamnai ki light chundhiyaavai to ulte haath ki safed patti pe nazar raakh. | If oncoming lights dazzle you, keep your eyes on the left white line. |
| Night drive reminder | रात शांत सै, गाणे हळवे कर द्यूं सूं, तू बस सड़क देख। | Raat shaant sai, gaane halve kar dyun sun, tu bas sadak dekh. | The night is quiet; I'll turn the music down, you just watch the road. |
| Night drive reminder | घणी रात हो गी जी, घरां आळे जाग रे होंगे, आराम तै पहुँचांगे। | Ghani raat ho gi ji, gharaan aale jaag re honge, aaraam te pahunchaange. | It's late; the family must be up waiting. We'll get there safely. |

## Not in this list

- **SOS, crash countdown, emergency**: always plain Hindi / the app language, on purpose. On units without a text-to-speech engine, four fixed plain-Hindi safety clips are recorded in the same two voices (`safety_*` in voice/lines.json: the crash warning, "सेकंड बाकी" after a recorded number for the last ten seconds, "SOS message ready" and "Help panel opening").
- **Incoming call announcement**: skipped, the app does not read call notifications.
- **Traffic / slow ETA**: skipped, the routing server (OSRM) has no live traffic.
- **Engine fault codes and overcharging (OBD)**: the plain description is clearer, so these stay as they are.
