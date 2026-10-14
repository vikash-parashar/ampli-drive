# Play Console answers: Data safety and permission declarations

For Ampli Car Hub (`com.vikash.ampli.drive`). Re-check these whenever a feature changes what is sent off the unit. Privacy policy URL: https://vikash-parashar.github.io/ampli-drive/privacy-policy.html (GitHub Pages from `/docs` on `main`; switch Pages on in the repo settings).

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
| Contacts | No | — | — | — | Emergency contacts are typed in by the user and stay on the unit; SOS opens the SMS app. |

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
| `voice.VoiceService` | `microphone` | `FOREGROUND_SERVICE_MICROPHONE` | Ongoing "Hey Ampli is listening" notification | Hands-free wake word while driving; only after the driver turns it on and agrees to the disclosure |

### Notification listener (`BIND_NOTIFICATION_LISTENER_SERVICE`)
Declared core use: **car launcher / media and navigation display**. The listener shows now-playing music and controls, Google Maps' next turn, and reads FASTag toll SMS notifications (debit, recharge, balance) on the unit. No notification content leaves the unit. The user grants it in Android's notification access settings, from Settings in the app.

### Display over other apps (`SYSTEM_ALERT_WINDOW`)
Used only for the optional **speed bubble** (current speed, speed limit, next camera) over Google Maps. Off by default; the app opens Android's "Display over other apps" page only when the driver switches the bubble on.

### Microphone (`RECORD_AUDIO`)
"Hey Ampli" wake word and voice commands, only after the driver turns them on and agrees to the in-app disclosure.

### Bluetooth (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` with `neverForLocation`)
Connected phone name, OBD-II dongle (Pro), BLE tyre-pressure sensors (Pro). Scan results are never used for location.

## Help & feedback (install id and reports)
- The install id is a random UUID stored in the app's preferences, created the first time a report is made. It is not linked to the Android ID, the advertising id, the Google account or Play purchase tokens.
- Reports contain the typed text, optional email and Play order id, app version, device model, Android version, app language and Pro status. Screenshots and app logs are attached only when the user adds them.
- Until the server URL (`OWNER_API_URL` repository variable) is set, reports open in the user's email app instead; nothing is sent automatically.
- Declared above as "App info and performance", "Messages", "Personal info › Email address" (optional) and "Device or other IDs"; none are shared with third parties.
