# Ampli Car Hub privacy policy

_Last updated: 31 October 2026_

Ampli Car Hub ("the app") is a home-screen launcher for Android car head units, made by **Ampli Studio** ("we"). This policy explains what the app does with your data.

**Contact:** Ampli Studio, support@ampliapps.in · Website: https://ampliapps.in

**Online copy:** https://ampliapps.in/privacy/carhub/

## The short version

- The app has **no account** and **no ads**, and contains no analytics or tracking SDK.
- What the app learns about your car and your drives is **kept on the head unit**. We don't receive it.
- A few features call public web services, listed below. They get only what that feature needs, with no name, account or advertising id.
- Help & feedback reports are sent to us only when you send one, and contain only what you type plus basic app and device facts.

## Data kept on this unit only

The app stores these in its private storage on the head unit. Uninstalling the app or clearing its data deletes them.

| Data | Why |
| --- | --- |
| Location fixes (in memory), parking spot, odometer | Speedometer, map, speed-camera alerts, break reminder, crash detection, "where did I park" |
| Car papers and service dates, fuel and expense log | Reminders, mileage and running-cost figures, CSV export (Pro) |
| FASTag tolls read from bank SMS notifications | Toll balance, toll totals, toll expenses (see Notification access) |
| Emergency contacts, blood group, medical notes | The SOS message you can send from the Help panel |
| Paired tyre-sensor ids, OBD-II dongle address | Tyre pressure and engine readings (Pro) |
| Settings, theme, language, wake-word model | Your preferences; the offline speech models for "Hey Ampli" and offline commands (English about 36 MB, Hindi about 42 MB) |
| Your name, if you enter it | The spoken welcome when the car starts ("Ram Ram {name} Ji"). Optional; if you say it with the mic, the audio goes to your device's own speech recognition service like a "Hey Ampli" command |
| Dashcam clips (Pro, off by default) | Loop recording from the unit's camera, video only (no sound), in the app's own folder; the oldest clips are deleted when the storage limit you set is reached. Never uploaded |
| Bhajan settings (off by default) | Which days a short bhajan plays when the car starts, and its volume |
| A random install id | Only used to link your Help & feedback reports (see below) |

## Location, including while the app is closed

The app collects your precise location to show your speed and the map, and, while **Background alerts** is on, to give speed-camera alerts, break reminders, crash detection and to save your parking spot **even when the app is closed or not in use**, for example while Google Maps is on screen. The app shows an explanation and asks you to agree before this starts. A notification is shown whenever it runs, and you can switch it off in Settings › Car.

Location is used on the unit. It is sent off the unit only in these cases:

- **Open-Meteo** (api.open-meteo.com): your position and two points ahead on your route, to warn of storms, heavy rain or fog. No name, account or id.
- **Photon** (photon.komoot.io, OpenStreetMap data): when you ask "Hey Ampli" for a place, the place name and your position (to find the nearest results).
- **Routing server** (OSRM, OpenStreetMap data; during testing the OSRM project's public server router.project-osrm.org): when you navigate on the Ampli map, your position and the destination's coordinates, to work out the route. Sent again if you leave the route.
- **OpenFreeMap** (tiles.openfreemap.org): map tiles for the area shown, as any map does.
- **SOS**: only if you send it, your position goes in the SMS or message you send to your own contacts (see below).

## Microphone and "Hey Ampli"

New installs have **Always listen for "Hey Ampli"** switched on, but nothing listens until you agree to the explanation and allow the microphone. With it on, the app uses the microphone to listen for the wake word, **including when the app is closed or not in use**. The app explains this and asks you to agree first. The wake word is recognised offline on the unit with the Vosk model, and audio is never recorded or sent to us. After the wake word, your spoken command is handled by your device's speech recognition service (for example Google's), under that provider's own privacy policy. A notification shows while listening, and you can switch it off in Settings › Voice.

## Phone calls and contacts (announce incoming calls)

Only if you switch on **Announce incoming calls** (free, off by default), the app asks for these permissions from that switch, and each one is optional:

- **Phone state** (READ_PHONE_STATE): to know when a call rings on the device running the app, and on older Android versions the caller's number;
- **Contacts** (READ_CONTACTS): to look up the caller's name on the unit, so it can be spoken;
- **Answer calls** (ANSWER_PHONE_CALLS): to answer or decline when you say "haan" or "naa" (or tap the card).

A head unit's Bluetooth call is detected from its ringing notification (see Notification access), and its own Answer / Decline buttons are pressed. The caller's name or number is spoken and shown on the call card only while it rings. Nothing about your calls or contacts is stored, and nothing is sent to us or anyone else. After the name, the app listens once for "haan / naa"; that short answer is handled by your device's speech recognition service like a "Hey Ampli" command.

## Camera (dashcam)

Only if you have Pro and switch on **Dashcam** (off by default), the app asks for the camera permission and records video (no sound) from a camera on the unit while Car Hub is running, with a notification showing whenever it records. Clips stay in the app's own folder on the unit and are deleted with the app. A hard jolt, a crash or saying "save kar" keeps the current clip from being overwritten. Clips are never uploaded or sent anywhere; you can open one in your video player.

## Notification access (FASTag SMS, music, navigation, calls and WhatsApp / Telegram read-aloud)

With your permission (Android's notification access), the app reads:

- notifications from your SMS app that mention FASTag, to pick out toll debits, recharges and the balance. Other SMS are ignored and not stored. The app has no SMS permission and cannot read your inbox;
- the media player's now-playing information, to show and control music;
- Google Maps' navigation notification, to show the next turn;
- only if you switch on **Read WhatsApp messages aloud** (Pro, off by default, after its own explanation screen): WhatsApp and WhatsApp Business message notifications, to say the sender's name and the message out loud while you drive. The message is spoken only: it is never shown on screen and never stored, Ampli Car Hub never sends it to us or anyone else, and the app never replies or acts on the notification unless you also switch on Reply by voice (below). It is spoken by your unit's text-to-speech engine. Most engines work on the unit; if yours is an online engine, the text is sent to that engine's provider to be turned into speech, under that provider's own privacy policy. Group messages are skipped unless you also switch on group messages. You can switch it off in Settings at any time.
- only if you also switch on **Telegram messages too** (after its own explanation screen): Telegram message notifications, read in the same way;
- only if you also switch on **Reply by voice** (after the same explanation screen): when a message has a reply button, the app asks whether you want to reply, listens for your reply, reads it back and sends it only after you say "haan, bhej de". Your spoken reply is turned into text by your device's speech recognition service (for example Google's), under that provider's own privacy policy, and is then handed to WhatsApp or Telegram through the notification's own reply button; WhatsApp or Telegram send it. Ampli Car Hub does not store the reply and never sends it to us or anyone else;
- ringing call notifications, only if you switch on **Announce incoming calls**, to read the caller's name and press the notification's own Answer / Decline button when you ask.

Ampli Car Hub keeps all of this on the unit (apart from what an online speech engine you chose may process, as above).

## SOS sharing

The Help panel can prepare an SOS message with your location and emergency profile. It opens your SMS app or the share sheet with the message filled in; **nothing is sent until you or a passenger press Send**. The app has no permission to send SMS by itself.

## Purchases (Google Play Billing)

Ampli Car Hub Pro is a one-time purchase through Google Play. Payment is handled entirely by Google; we don't see your card or payment details. The app only checks with Google Play whether Pro is owned. See Google's privacy policy.

## Help & feedback

When you send a bug report, feature request or support message from Settings › Help & feedback, the app sends:

- what you type (title, description, and, if you enter them, your email address and Play order id);
- the app version, device make and model, Android version, app language and whether Pro is active;
- a random **install id** created by the app (not the Android ID or advertising id), so you can see your own requests and their replies;
- a screenshot or the app's own recent log lines, only if you add them (logs are off by default).

These go to Ampli Studio's support server (or, until it is live, open in your email app addressed to our support address). We use them only to answer you and improve the app, and we don't sell or share them. Reports waiting to be sent while offline are kept on the unit until they go out.

## Other network use

- **Speech model download** (alphacephei.com): the offline speech models (English, and Hindi for Hindi-family languages), once, on first start, only when the app did not come with them (Play installs include them).
- Speed-camera data is built into the app (OpenStreetMap, ODbL); checking cameras needs no network.

## Children

The app is meant for drivers and is not directed at children.

## Your choices

Every feature that uses location in the background, the microphone, the camera, phone and contacts permissions or notification access can be switched off in Settings, and Android lets you revoke any permission at any time. To delete everything the app keeps, clear its storage or uninstall it. To ask about or delete Help & feedback reports, write to the contact address above with your install id (shown in the report email or available on request).

## Changes

We will update this page when the app's data use changes, and change the date at the top.
