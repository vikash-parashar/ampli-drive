# Round 7 voice lines to record

"Hey Ampli" now answers the time, the date, the weather, the speed and volume changes. The car screen has no text-to-speech engine, so these answers are stitched from short recorded pieces, the same way a camera alert is "चार सौ मीटर" + its tail. Until the pieces are recorded the answer card shows the answer and a short recorded acknowledgement plays (`command_done`, or the matching `weather_rain` / `weather_thunder` / `weather_fog` / `weather_heat` clip for weather).

The pieces are already in `voice/lines.json` (group "Answers" and "Number"), generated from `app/src/main/java/com/vikash/ampli/drive/voice/AnswerClips.kt`, so the **Voice pack** workflow (Actions, manual) records them in both voices with `only_missing` on. This file is the review copy: change a text in `AnswerClips.kt`, not here, then regenerate `voice/lines.json`.

Rules: family-safe, no slang beyond friendly Haryanvi, no names, safety prompts stay plain Hindi (none here). Record each piece as a separate short clip with a natural pause at both ends; numbers and months are said on their own (they get stitched).


## How answers are stitched

| Answer | Pieces (Standard style) | Haryanvi style |
|---|---|---|
| 5:20 pm | `t_now` अभी + `t_evening` शाम के + `num_5` पाँच + `t_baj_kar` बजकर + `num_20` बीस + `t_minutes` मिनट हुए हैं। | same, last piece `t_minutes_hr` मिनट होगे। |
| 9:00 am | `t_now` + `t_morning` सुबह के + `num_9` नौ + `t_oclock` बजे हैं। | `t_oclock_hr` बज गे सैं। |
| 30 Oct, Thursday | `d_today` आज + `wd_4` गुरुवार + `num_30` तीस + `mon_10` अक्टूबर + `d_is` है। | `d_is_hr` सै। |
| Weather | `wx_partly` अभी हल्के बादल हैं। + `wx_temp` तापमान + `num_31` इकतीस + `wx_degrees` डिग्री है। + `wx_rain_no` | the `_hr` twins |
| "Will it rain?" | `wx_rain_yes` / `wx_rain_no` first, then sky and temperature | the `_hr` twins |
| Speed (up to 59 km/h) | `sp_prefix` अभी स्पीड + `num_54` चौवन + `sp_kmh` किलोमीटर प्रति घंटा है। | `sp_kmh_hr` |

Day parts: सुबह 4 to 11, दोपहर 12 to 3 pm, शाम 4 to 7 pm, रात 8 pm to 3 am.


## Sentence pieces (Standard Hindi and Haryanvi)

| id | Used for | Hindi (Standard style) | Haryanvi (`<id>_hr`) |
|---|---|---|---|
| `t_minutes` | Time: after the minutes | मिनट हुए हैं। | मिनट होगे। |
| `t_oclock` | Time: on the hour | बजे हैं। | बज गे सैं। |
| `d_is` | Date: end of the sentence | है। | सै। |
| `wx_clear` | Weather: clear sky | अभी मौसम साफ़ है। | मौसम एकदम साफ सै। |
| `wx_partly` | Weather: partly cloudy | अभी हल्के बादल हैं। | थोड़े-थोड़े बादळ सैं। |
| `wx_cloudy` | Weather: cloudy | अभी बादल छाए हैं। | बादळ छाए होए सैं। |
| `wx_rain` | Weather: raining now | अभी बारिश हो रही है। | मींह पड़ रया सै। |
| `wx_heavy_rain` | Weather: heavy rain now | अभी तेज़ बारिश हो रही है, ध्यान से चलाइए। | जोर का मींह पड़ रया सै, संभल कै चलाइए। |
| `wx_thunder` | Weather: thunderstorm | अभी आंधी-तूफ़ान का मौसम है, ध्यान से चलाइए। | आंधी-तूफान का मौसम सै, ध्यान राखिए। |
| `wx_fog` | Weather: fog | अभी धुंध है। | धुंध घणी सै। |
| `wx_snow` | Weather: snow | अभी बर्फ़ पड़ रही है। | बर्फ पड़ री सै। |
| `wx_degrees` | Weather: after the temperature number | डिग्री है। | डिग्री सै। |
| `wx_rain_yes` | Weather: rain likely in the next hours | अगले कुछ घंटों में बारिश के आसार हैं। | अगले कुछ घंट्यां मैं मींह आण के आसार सैं। |
| `wx_rain_no` | Weather: no rain expected | अगले कुछ घंटों में बारिश के आसार नहीं हैं। | अगले कुछ घंट्यां मैं मींह का कोए आसार कोनी। |
| `wx_offline` | Weather: no internet | इंटरनेट नहीं है, अभी मौसम नहीं देख सकते। | इंटरनेट कोनी चाल रया, इब मौसम ना देख सकां। |
| `wx_failed` | Weather: server did not answer | अभी मौसम की जानकारी नहीं मिल रही, थोड़ी देर बाद पूछिए। | मौसम का हाल इब कोनी मिल रया, थोड़ी वार पाछै पूछ लिए। |
| `no_location` | Weather / speed: no GPS fix yet | अभी लोकेशन नहीं मिल रही। | लोकेशन कोनी मिल री। |
| `sp_kmh` | Speed: after the number | किलोमीटर प्रति घंटा है। | किलोमीटर प्रति घंटा सै। |
| `vol_up` | Volume up done | आवाज़ बढ़ा दी। | आवाज बढ़ा दी भाई। |
| `vol_down` | Volume down done | आवाज़ कम कर दी। | आवाज कम कर दी। |
| `vol_max` | Volume full done | आवाज़ पूरी कर दी। | आवाज पूरी खोल दी। |
| `vol_mute` | Muted (not spoken today: a muted stream is silent; recorded for a later chime) | आवाज़ बंद कर दी। | आवाज बंद कर दी। |
| `vol_unmute` | Unmuted | आवाज़ फिर से चालू। | आवाज फेर चालू। |
| `nav_stopped` | In-app navigation stopped | नेविगेशन बंद कर दिया। | रास्ता बंद कर दिया। |

## Shared pieces (both styles)

| id | Text |
|---|---|
| `t_now` | अभी |
| `t_morning` | सुबह के |
| `t_afternoon` | दोपहर के |
| `t_evening` | शाम के |
| `t_night` | रात के |
| `t_baj_kar` | बजकर |
| `d_today` | आज |
| `wx_temp` | तापमान |
| `sp_prefix` | अभी स्पीड |
| `wd_1` | सोमवार |
| `wd_2` | मंगलवार |
| `wd_3` | बुधवार |
| `wd_4` | गुरुवार |
| `wd_5` | शुक्रवार |
| `wd_6` | शनिवार |
| `wd_7` | रविवार |
| `mon_1` | जनवरी |
| `mon_2` | फ़रवरी |
| `mon_3` | मार्च |
| `mon_4` | अप्रैल |
| `mon_5` | मई |
| `mon_6` | जून |
| `mon_7` | जुलाई |
| `mon_8` | अगस्त |
| `mon_9` | सितंबर |
| `mon_10` | अक्टूबर |
| `mon_11` | नवंबर |
| `mon_12` | दिसंबर |

## Numbers 11 to 59

`num_1` to `num_10` are already recorded. New: `num_11` ग्यारह, `num_12` बारह, `num_13` तेरह, `num_14` चौदह, `num_15` पंद्रह, `num_16` सोलह, `num_17` सत्रह, `num_18` अठारह, `num_19` उन्नीस, `num_20` बीस, `num_21` इक्कीस, `num_22` बाईस, `num_23` तेईस, `num_24` चौबीस, `num_25` पच्चीस, `num_26` छब्बीस, `num_27` सत्ताईस, `num_28` अट्ठाईस, `num_29` उनतीस, `num_30` तीस, `num_31` इकतीस, `num_32` बत्तीस, `num_33` तैंतीस, `num_34` चौंतीस, `num_35` पैंतीस, `num_36` छत्तीस, `num_37` सैंतीस, `num_38` अड़तीस, `num_39` उनतालीस, `num_40` चालीस, `num_41` इकतालीस, `num_42` बयालीस, `num_43` तैंतालीस, `num_44` चवालीस, `num_45` पैंतालीस, `num_46` छियालीस, `num_47` सैंतालीस, `num_48` अड़तालीस, `num_49` उनचास, `num_50` पचास, `num_51` इक्यावन, `num_52` बावन, `num_53` तिरेपन, `num_54` चौवन, `num_55` पचपन, `num_56` छप्पन, `num_57` सत्तावन, `num_58` अट्ठावन, `num_59` उनसठ.


Total new clips per voice: 125 (Standard 24, Haryanvi 24, shared 28, numbers 49).


## Not recorded on purpose

- Speeds of 60 km/h and more: the card shows them; a later round can add tens ("साठ", "सत्तर", … "एक सौ बीस").
- Temperatures below 1 °C: the card shows them, the voice skips the number.
- Navigation-stop for Google Maps, brightness and Bluetooth replies: the card or the plain acknowledgement covers them.
