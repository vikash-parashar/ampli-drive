package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.voice.HaryanviLines.Event
import com.vikash.ampli.drive.voice.HaryanviLines.Line

/*
 * Haryanvi lines, round 4: calls and chat messages (answered by voice), service reminders at car
 * start and the dashcam's "clip saved". Appended after rounds 1 to 3, so earlier clip ids keep
 * their numbers. Like round 2, a recorded voice only picks a line once its clip is in the build
 * ([VoiceGate]); until then the caller says the plain app string instead. Lines with a name or a
 * number in them are always said by the phone voice.
 *
 * The caller's name and the message text come from the phone; they are spoken, never stored.
 */
internal val LINES_V4: Map<Event, List<Line>> = mapOf(
    Event.CallAnnounce to listOf(
        Line("भाई, %1\$s का फोन आ रया सै, ठा ल्यूं?", "Bhai, %1\$s ka phone aa raya sai, tha lyun?", "Brother, {name} is calling, shall I pick up?"),
        Line("%1\$s का फोन सै भाई, ठा ल्यूं के?", "%1\$s ka phone sai bhai, tha lyun ke?", "It's {name} calling, shall I pick up?"),
        Line("फोन बाज रया सै, %1\$s सै। ठा ल्यूं?", "Phone baaj raya sai, %1\$s sai. Tha lyun?", "The phone is ringing, it's {name}. Pick up?"),
        Line("भाई, %1\$s याद कर रया सै, फोन ठा ल्यूं?", "Bhai, %1\$s yaad kar raya sai, phone tha lyun?", "Brother, {name} is thinking of you, pick up?"),
    ),
    Event.CallUnknown to listOf(
        Line("भाई, किसे का फोन आ रया सै, ठा ल्यूं?", "Bhai, kise ka phone aa raya sai, tha lyun?", "Brother, someone is calling, shall I pick up?"),
        Line("फोन बाज रया सै, ठा ल्यूं?", "Phone baaj raya sai, tha lyun?", "The phone is ringing, pick up?"),
        Line("अनजाण नंबर तै फोन सै, ठा ल्यूं के?", "Anjaan number te phone sai, tha lyun ke?", "A call from an unknown number, pick up?"),
    ),
    Event.CallAnswered to listOf(
        Line("ले, फोन ठा लिया, बात कर ले।", "Le, phone tha liya, baat kar le.", "There, picked up, go ahead and talk."),
        Line("ठा लिया भाई, बोल ले।", "Tha liya bhai, bol le.", "Picked up, brother, speak."),
        Line("फोन जुड़ ग्या, बात कर।", "Phone jud gaya, baat kar.", "The call is connected, talk."),
    ),
    Event.CallRejected to listOf(
        Line("ठीक सै, बाद मैं बात कर लियो।", "Theek sai, baad main baat kar liyo.", "OK, talk later."),
        Line("फोन काट दिया, गाड्डी पै ध्यान दे।", "Phone kaat diya, gaaddi pai dhyan de.", "Call declined, keep your eyes on the road."),
        Line("काट दिया भाई, पाछै कर लेंगे।", "Kaat diya bhai, paachhe kar lenge.", "Declined, brother, we'll call back later."),
    ),
    Event.CallManual to listOf(
        Line("भाई, यो फोन मैं ना ठा सकूं, स्क्रीन पै बटन दबा दे।", "Bhai, yo phone main na tha sakun, screen pai button daba de.", "Brother, I can't pick this one up, press the button on the screen."),
        Line("बटन दबा कै ठा ले भाई, मेरे तै ना होया।", "Button daba ke tha le bhai, mere te na hoya.", "Press the button to pick up, I couldn't do it."),
        Line("फोन आळा बटन दबा दे, मैं ना ठा पाया।", "Phone aala button daba de, main na tha paaya.", "Press the phone button, I couldn't pick up."),
    ),
    Event.MessageAnnounce to listOf(
        Line("%1\$s का %2\$s आया सै, सुणाऊं?", "%1\$s ka %2\$s aaya sai, sunaun?", "A {app} message from {name}, shall I read it?"),
        Line("%2\$s पै %1\$s का मैसेज सै, सुणा द्यूं?", "%2\$s pai %1\$s ka message sai, suna dyun?", "{name} sent a message on {app}, read it out?"),
        Line("भाई, %1\$s नै %2\$s भेज्या सै, सुणाऊं के?", "Bhai, %1\$s ne %2\$s bhejya sai, sunaun ke?", "Brother, {name} sent a {app} message, shall I read it?"),
    ),
    Event.MessageAskReply to listOf(
        Line("जवाब भेजणा सै?", "Jawab bhejna sai?", "Want to send a reply?"),
        Line("कुछ जवाब देणा सै भाई?", "Kuchh jawab dena sai bhai?", "Any reply, brother?"),
        Line("जवाब लिख द्यूं?", "Jawab likh dyun?", "Shall I write a reply?"),
    ),
    Event.MessageDictate to listOf(
        Line("बोल भाई, के लिखूं?", "Bol bhai, ke likhun?", "Go ahead, brother, what shall I write?"),
        Line("हाँ, बोल दे के भेजणा सै।", "Haan, bol de ke bhejna sai.", "Yes, say what to send."),
        Line("बोल, मैं लिख रया सूं।", "Bol, main likh raya sun.", "Speak, I'm writing."),
    ),
    Event.MessageConfirm to listOf(
        Line("न्यू लिख्या सै: %1\$s। भेज द्यूं?", "Nyu likhya sai: %1\$s. Bhej dyun?", "I wrote: {text}. Send it?"),
        Line("%1\$s। यो भेज द्यूं?", "%1\$s. Yo bhej dyun?", "{text}. Send this?"),
        Line("जवाब सै: %1\$s। ठीक सै, भेज द्यूं?", "Jawab sai: %1\$s. Theek sai, bhej dyun?", "The reply is: {text}. OK to send?"),
    ),
    Event.MessageSent to listOf(
        Line("भेज दिया भाई।", "Bhej diya bhai.", "Sent, brother."),
        Line("जवाब चला ग्या।", "Jawab chala gaya.", "The reply has gone."),
        Line("मैसेज भेज दिया, इब सड़क पै ध्यान।", "Message bhej diya, ib sadak pai dhyan.", "Message sent, now eyes on the road."),
    ),
    Event.MessageNotSent to listOf(
        Line("ठीक सै, कुछ ना भेज्या।", "Theek sai, kuchh na bhejya.", "OK, nothing was sent."),
        Line("रहण दिया, जवाब ना गया।", "Rahan diya, jawab na gaya.", "Left it, no reply sent."),
        Line("कैंसल कर दिया भाई।", "Cancel kar diya bhai.", "Cancelled, brother."),
    ),
    Event.ServiceDueKm to listOf(
        Line("%1\$s मैं %2\$d किलोमीटर बाकी सै, टाइम पै करवा लिए।", "%1\$s main %2\$d kilometre baaki sai, time pai karwa liye.", "{service} is due in {km} km, get it done on time."),
        Line("भाई, %2\$d किलोमीटर पाछै %1\$s करवाणा सै।", "Bhai, %2\$d kilometre paachhe %1\$s karwana sai.", "Brother, {service} is due after {km} km."),
        Line("%1\$s नेड़ै आ ग्या, बस %2\$d किलोमीटर बचे।", "%1\$s nedai aa gaya, bas %2\$d kilometre bache.", "{service} is close, just {km} km left."),
    ),
    Event.ServiceDueDays to listOf(
        Line("%1\$s की तारीख %2\$d दिन मैं आ री सै।", "%1\$s ki taarikh %2\$d din main aa ri sai.", "{service} is due in {days} days."),
        Line("भाई, %2\$d दिन मैं %1\$s करवाणा सै, भूलिए मत।", "Bhai, %2\$d din main %1\$s karwana sai, bhooliye mat.", "Brother, {service} in {days} days, don't forget."),
        Line("%1\$s का टाइम नेड़ै सै, %2\$d दिन बचे।", "%1\$s ka time nedai sai, %2\$d din bache.", "{service} is near, {days} days left."),
    ),
    Event.ServiceOverdue to listOf(
        Line("%1\$s का टाइम निकल ग्या भाई, जल्दी करवा ले।", "%1\$s ka time nikal gaya bhai, jaldi karwa le.", "{service} is overdue, brother, get it done soon."),
        Line("%1\$s रह ग्या सै, इसे हफ्ते करवा लिए।", "%1\$s reh gaya sai, ise hafte karwa liye.", "{service} was missed, get it done this week."),
        Line("भाई, %1\$s पाछै रह ग्या, गाड्डी का ध्यान राख।", "Bhai, %1\$s paachhe reh gaya, gaaddi ka dhyan raakh.", "Brother, {service} is behind, look after the car."),
    ),
    Event.DashcamSaved to listOf(
        Line("क्लिप सेव कर दी भाई।", "Clip save kar di bhai.", "Clip saved, brother."),
        Line("पिछला वीडियो संभाल लिया।", "Pichhla video sambhaal liya.", "Kept the last video safe."),
        Line("रिकॉर्डिंग बचा ली, मिटैगी कोनी।", "Recording bacha li, mitegi koni.", "Recording saved, it won't be erased."),
    ),
)
