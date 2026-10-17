# Ampli Car Hub: Haryanvi lines for review

Every line the car speaks in the **Haryanvi desi** voice style (Settings › Voice style, the default).
The source of truth is `app/src/main/java/com/vikash/ampli/drive/voice/HaryanviLines.kt`;
change a line there and here together (a unit test checks that every line in the app is listed here).

- 76 events, 331 lines. The car never says the same line twice in a row.
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
| Welcome on car start | राम राम जी, आज कित जाणा सै? | Ram Ram ji, aaj kit jaana sai? | Ram Ram! Where are we going today? |
| Welcome on car start | राम राम भाई, आज कित का प्लान सै? | Ram Ram bhai, aaj kit ka plan sai? | Ram Ram brother, what's the plan today? |
| Welcome on car start | और तैयारे? सुणा दे, के हाल सै? | Aur taiyaare? Suna de, ke haal sai? | So, all set? Tell me, how are things? |
| Welcome on car start | राम राम ताई आले, आज कित की तैयारी सै? | Ram Ram Tai Aale, aaj kit ki taiyaari hai? | Ram Ram, dear friend! What are we ready for today? |
| Welcome on car start | राम राम भाई! गाड्डी त्यार सै, बोल कित चालां? | Ram Ram bhai! Gaadi tyaar sai, bol kit chaalaan? | Ram Ram! The car is ready, say where we go. |
| Welcome on car start | भाई कित जाणा सै आज? | Bhai kit jaana sai aaj? | Brother, where are we going today? |
| Welcome on car start | आज कित की तैयारी सै? | Aaj kit ki taiyaari sai? | What are we ready for today? |
| Welcome on car start | आज कित का प्लान बणैगा? | Aaj kit ka plan banega? | What's the plan going to be today? |
| Morning (4 to 11) | सवेरे सवेरे कित चाल पड़े ताऊ? | Savere savere kit chaal pade tau? | Off somewhere so early in the morning, Tau? |
| Morning (4 to 11) | सवेरे की राम राम! चाय पी ली के? | Savere ki Ram Ram! Chai pi li ke? | Morning Ram Ram! Had your tea? |
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
| Goodbye (ACC off / parked) | आज का सफर बढ़िया रया, राम राम! | Aaj ka safar badhiya raya, Ram Ram! | Today's trip went well, Ram Ram! |

## Hey Ampli

- **Hey Ampli greeting**: "Hey Ampli", at most every 3 hours.
- **Hey Ampli reply**: "Hey Ampli" between greetings. *(Haryanvi only; Standard just beeps)*
- **Searching a place**: Fills in: `%1$s` = place.
- **Starting navigation**: Fills in: `%1$s` = place.
- **Command done**: A command done, e.g. "connect my phone" opened Android Auto.

| Event | Devanagari | Roman | English meaning |
|---|---|---|---|
| Hey Ampli greeting | राम राम भाई! कित चालणा सै आज? | Ram Ram bhai! Kit chalna sai aaj? | Ram Ram brother! Where are we going today? |
| Hey Ampli greeting | सुणा ताई आळे, गाड्डी त्यार सै! | Suna tai aale, gaadi tyar sai! | Tell me, friend, the car is ready! |
| Hey Ampli greeting | राम राम! बेल्ट ला ले, फेर चालांगे। | Ram Ram! Belt laga le, phir chalange. | Ram Ram! Put on your belt, then we go. |
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

## Not in this list

- **SOS, crash countdown, emergency**: always plain Hindi / the app language, on purpose. On units without a text-to-speech engine, four fixed plain-Hindi safety clips are recorded in the same two voices (`safety_*` in voice/lines.json: the crash warning, "सेकंड बाकी" after a recorded number for the last ten seconds, "SOS message ready" and "Help panel opening").
- **Incoming call announcement**: skipped, the app does not read call notifications.
- **Traffic / slow ETA**: skipped, the routing server (OSRM) has no live traffic.
- **Engine fault codes and overcharging (OBD)**: the plain description is clearer, so these stay as they are.
