# Ampli MP3 Player: the Car mode intent Car Hub sends

Car Hub's Music button and "Hey Ampli" music commands open Ampli MP3 Player (`com.vikash.ampli`)
in its Car mode. This is the contract, so the MP3 Player can support it. Code on the Car Hub side:
`app/src/main/java/com/vikash/ampli/drive/system/AmpliMusic.kt`.

## Intent

```
action:  com.vikash.ampli.action.CAR_MODE
package: com.vikash.ampli
flags:   FLAG_ACTIVITY_NEW_TASK
extras:
  autoplay  boolean  true: start playing (resume the last queue, else shuffle all). false: just open.
  query     String   new in Car Hub round 7, optional. What the driver said after "play" / before
                     "bajao": a song title, an artist, an album or a mix of them, as heard
                     ("kesariya", "arijit singh", "tum hi ho arijit"). Not trimmed of typos.
```

Car Hub checks that the action resolves in package `com.vikash.ampli` before sending it; builds
without Car mode get the launcher entry instead.

## What the MP3 Player should do with `query` (requested)

1. Search the library (title, artist, album, file name), forgiving of case, spaces and Hindi or
   Latin spelling where it can.
2. Best match found: play it now, with the rest of the matches (or that artist / album) queued.
3. Nothing found: keep the current queue playing (as with `autoplay`) and show "Kesariya not
   found" briefly in Car mode. Don't open a search screen while driving.
4. Ignore `query` when it is blank.

Until a build supports `query`, the extra is ignored and `autoplay` resumes or shuffles, which is
the agreed fallback.

## Order Car Hub tries for "Hey Ampli, play <song>"

1. The phone over Bluetooth is playing and its session supports play-from-search: ask the phone
   (rare over AVRCP; most phones don't).
2. The song is on this unit (MediaStore match, needs the audio permission): `ACTION_VIEW` of that
   `content://` URI with package `com.vikash.ampli` (works with today's MP3 builds).
3. Ampli MP3 declares `android.media.action.MEDIA_PLAY_FROM_SEARCH`: that intent with
   `SearchManager.QUERY`.
4. The Car mode intent above with `autoplay = true` and `query`.

Play / pause / next / previous by voice go to Ampli's media session when it is the one playing
(or the last one that played), so the MP3 Player needs nothing new for those.
