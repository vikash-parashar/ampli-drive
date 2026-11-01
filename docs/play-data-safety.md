# Play Console answers: Data safety and permission declarations

For Ampli Car Hub (`com.vikash.ampli.drive`). Re-check these whenever a feature changes what is sent off the unit. Privacy policy URL: https://ampliapps.in/privacy/carhub/ (GitHub Pages from `/docs` on `main`; switch Pages on in the repo settings).

## Data safety form

**Does your app collect or share any of the required user data types?** Yes.

Google counts data as "collected" when it leaves the device. Data that only stays on the head unit (papers, expenses, FASTag tolls, parking spot, emergency profile, odometer) is **not** collected and is not declared.

**Is all of the user data collected by your app encrypted in transit?** Yes (every call is HTTPS).

**Do you provide a way for users to request that their data is deleted?** Yes. In-app data: clear storage or uninstall. Help & feedback reports: email the support address with the install id.

| Data type (Play category) | Collected | Shared | Optional | Purpose | Notes |
| --- | --- | --- | --- | --- | --- |
| Location › Precise location | Yes | No | Yes | App functionality | Sent to Open-Meteo for weather ahead, to Photon for place search and to the routing server (OSRM) for in-app navigation, with no identifier. Processed ephemerally: Play lets you mark data as "processed ephemerally" when it is used only to answer the request. Not sent to us. |
| App activity › Other user-generated content (Help & feedback text) | Yes | No | Yes | Developer communications, App functionality | Only what the user types in a report. |
| Personal info › Email address | Yes | No | Yes | Developer communications | Only if the user types it in a report, to get a reply. |
| Messages › Other in-app messages | Yes | No | Yes | Developer communications | Support messages and feature requests (spec: "Messages, optional, not shared"). |
| App info and performance › Crash logs / Diagnostics | Yes | No | Yes | App functionality (fixing bugs) | Only when the user ticks "Attach app logs" (off by default) or adds a screenshot. |
| Device or other IDs | Yes | No | Yes | Developer communications | The random install id sent with reports (not the Android ID or advertising id). |
| Financial info › Purchase history | No | — | — | — | Play Billing is handled by Google; the app only asks Play whether Pro is owned. |
| Audio › Voice or sound recordings | No | — | — | — | The wake word is spotted offline; the command goes to the device's speech recognition service (Google's or the OEM's), not to us. If Play review asks, explain that the app uses Android's `SpeechRecognizer`. |
| Messages › SMS | No | — | — | — | FASTag SMS notifications are parsed on the unit; nothing is sent. |
| Messages › Other in-app messages (WhatsApp, Telegram) | No | — | — | — | WhatsApp / Telegram read-aloud (Pro, off by default) speaks the notification text with the unit's speech engine; Car Hub never stores it or sends it to us. If the driver's chosen speech engine is an online one, that engine's provider receives the text to speak it (third-party engine, not Car Hub). Reply by voice (Pro, off by default, own disclosure): the dictated reply is recognised by the device's `SpeechRecognizer` and handed to WhatsApp / Telegram through the notification's RemoteInput reply action; the chat app sends it, Car Hub keeps and uploads nothing. |
| Contacts | No | — | — | — | Emergency contacts are typed in by the user and stay on the unit; SOS opens the SMS app. Announce incoming calls (free, off by default) looks up the ringing caller's name with `READ_CONTACTS` on the unit only; nothing is stored or sent. |
| Phone › Calls (call state, caller number) | No | — | — | — | `READ_PHONE_STATE` / `ANSWER_PHONE_CALLS` are used on the unit only to announce and answer / decline a ringing call when the driver asks. Nothing is logged or sent. |
| Photos and videos › Videos | No | — | — | — | Dashcam (Pro, off by default): video-only clips saved in the app's own external-files folder on the unit; never uploaded. |

**Security practices:** data encrypted in transit: yes. Users can request deletion: yes. Committed to the Families policy: no (not for children). Independent security review: no.

**No ads, no analytics, no advertising id.** The app does not declare `com.google.android.gms.permission.AD_ID`.

## Permission declarations

### Location in the background
The app does **not** request `ACCESS_BACKGROUND_LOCATION`. Background alerts run in a **location foreground service** that is started only while the launcher is on screen, so Android keeps the while-in-use location grant. Play still expects the in-app prominent disclosure for location used while the app is not in use, which the app shows before the service first starts (and again if the driver switches Background alerts on later).

If Play review asks for the background-location declaration anyway:
- **Feature:** speed-camera alerts, break reminder, crash detection and parking-spot save while Google Maps or another app is on screen.
- **Why it needs location when not in use:** the driver navigates with Google Maps in front; the alerts must keep working.
- **Video:** record the disclosure dialog, then switch to Google Maps and show a camera alert / the speed bubble.

### Foreground service types (Android 14+ declaration form)
| Service | Type | Permission | What the user sees | Why it can't wait / be deferred |
| --- | --- | --- | --- | --- |
| `car.CarMonitorService` | `location` | `FOREGROUND_SERVICE_LOCATION` | Ongoing "Watching your drive" notification, next camera in the text | Real-time alerts while driving; stops when switched off in Settings › Car |
| `voice.VoiceService` | `microphone` | `FOREGROUND_SERVICE_MICROPHONE` | Ongoing "Hey Ampli is listening" notification | Hands-free wake word while driving; on by default for new installs, but only after the driver agrees to the disclosure and allows the microphone |
| `dashcam.DashcamService` | `camera` | `FOREGROUND_SERVICE_CAMERA` | Ongoing "Dashcam recording" notification | Loop recording must continue while Google Maps is in front; Pro, off by default, started only from the visible launcher or the dashcam switch |

### Notification listener (`BIND_NOTIFICATION_LISTENER_SERVICE`)
Declared core use: **car launcher / media and navigation display**. The listener shows now-playing music and controls, Google Maps' next turn, and reads FASTag toll SMS notifications (debit, recharge, balance) on the unit. Optional (Pro, off by default, behind an in-app prominent disclosure): WhatsApp / WhatsApp Business message notifications read aloud while driving, never shown or stored. A second opt-in disclosure covers Telegram notifications and voice replies sent through the notification's own reply action after a spoken confirmation. Optional (free, off by default): ringing call notifications, to announce the caller and press the notification's Answer / Decline action when the driver says so. Car Hub sends no notification content off the unit; only an online speech engine the driver chose may receive text it is asked to speak. Before release, re-check the declaration form for this use and record the disclosure + Settings toggle for the review video. The user grants it in Android's notification access settings, from Settings in the app.

### Display over other apps (`SYSTEM_ALERT_WINDOW`)
Used for the optional **speed bubble** (current speed, speed limit, next camera) over Google Maps, and to help the **crash SOS** message open faster after a crash while another app is in front. On Android 15+ this works only while the speed bubble is showing; otherwise (and always when Car Hub is not in front) a full-screen / heads-up alert brings the SOS screen up for a tap. The app opens Android's "Display over other apps" page only when the driver switches the bubble on, or from the explanation card / status row in Settings › Car while crash detection is on.

### Full-screen intent (`USE_FULL_SCREEN_INTENT`, Android 14+)
Used only for the **crash SOS alarm**: when a crash countdown runs out while Car Hub is not in front, a full-screen alert brings the SOS screen forward (nothing is sent without a tap). Fill in the Play Console "Full-screen intent" declaration on the App content page as a safety alert. On Android 14+ the driver can also grant it from the status row in Settings › Car (opens Android's full-screen notification setting); without it the alert is a heads-up notification.

### Microphone (`RECORD_AUDIO`)
"Hey Ampli" wake word and voice commands (on by default for new installs), only after the driver agrees to the in-app disclosure and allows the microphone.

### Phone and contacts (`READ_PHONE_STATE`, `READ_CONTACTS`, `ANSWER_PHONE_CALLS`)
Requested only from Settings › Announce incoming calls (free, off by default): know a call is ringing, speak the caller's contact name, and answer / decline on a spoken "haan / naa". Each is optional; nothing is stored or sent. Car Hub is not the default dialer and makes no calls by itself.

### Camera (`CAMERA`)
Requested only from Car › Dashcam (Pro, off by default) on units that have a camera (`android.hardware.camera.any` is not required). Video only; clips stay on the unit.

### Bluetooth (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` with `neverForLocation`)
Connected phone name, OBD-II dongle (Pro), BLE tyre-pressure sensors (Pro). Scan results are never used for location.

### Accessibility service (`BIND_ACCESSIBILITY_SERVICE`): "Ampli power helper"
Off until the driver turns it on from a one-time card that explains it (prominent disclosure: `disclosure_power_body` / `disclosure_power_private`, shown before Android's Accessibility settings open). Purpose: head units without physical buttons. It calls only `performGlobalAction` for `GLOBAL_ACTION_POWER_DIALOG` (Restart / Power off, which the driver then taps in Android's own menu), `GLOBAL_ACTION_RECENTS` (long press on Apps) and `GLOBAL_ACTION_TAKE_SCREENSHOT` (Android 9+), each only after a tap in Car Hub. Config (`res/xml/power_helper.xml`): no event types, `canRetrieveWindowContent="false"`, `canPerformGestures="false"`. It collects, stores and sends nothing, so it adds nothing to the Data safety form. Not declared as an accessibility tool (`isAccessibilityTool` is not set); the Play declaration describes it as above.

### Device admin (`BIND_DEVICE_ADMIN`, `force-lock` only)
Settings › Power › "Turn the display fully off" (off by default) asks Android's "Activate device admin app?" screen so Screen off can call `lockNow()`. Only the `force-lock` policy is declared (`res/xml/device_admin.xml`); turning the setting off calls `removeActiveAdmin`. No data involved.

### Quick panel permissions
`CHANGE_WIFI_STATE` (Wi-Fi tile on Android 8/9), `KILL_BACKGROUND_PROCESSES` (Clear memory), `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"` (screenshots in Pictures/Ampli, asked once), `WRITE_SETTINGS` (brightness slider, already declared). Screenshots use Android's MediaProjection prompt on Android 8/9 and stay on the unit, so nothing is collected.

## Help & feedback (install id and reports)
- The install id is a random UUID stored in the app's preferences, created the first time a report is made. It is not linked to the Android ID, the advertising id, the Google account or Play purchase tokens.
- Reports contain the typed text, optional email and Play order id, app version, device model, Android version, app language and Pro status. Screenshots and app logs are attached only when the user adds them.
- Until the server URL (`OWNER_API_URL` repository variable) is set, reports open in the user's email app instead; nothing is sent automatically.
- Declared above as "App info and performance", "Messages", "Personal info › Email address" (optional) and "Device or other IDs"; none are shared with third parties.
