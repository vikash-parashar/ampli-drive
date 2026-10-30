package com.vikash.ampli.drive.voice

import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.media.MediaHub
import com.vikash.ampli.drive.media.MediaTarget
import com.vikash.ampli.drive.media.SessionInfo
import com.vikash.ampli.drive.system.AmpliMusic
import com.vikash.ampli.drive.service.DriveNotificationListener
import com.vikash.ampli.drive.system.InstalledApps
import com.vikash.ampli.drive.system.Launchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The things the assistant does in other apps. Main thread unless marked suspend. */
object VoiceActions {

    // ----------------------------------------------------------------------------- Navigation

    /** Google Maps turn-by-turn straight away; any map app through geo: when Maps is missing. */
    fun startNavigation(context: Context, place: Place): Boolean {
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${place.lat},${place.lon}&mode=d"))
            .setPackage(Launchers.GOOGLE_MAPS)
        if (start(context, nav)) return true
        val label = Uri.encode(place.name)
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${place.lat},${place.lon}?q=${place.lat},${place.lon}($label)"))
        return start(context, geo)
    }

    /**
     * Place search failed (no internet, server down): let Google Maps search [query] itself and
     * start turn-by-turn to its best match.
     */
    fun startNavigationByName(context: Context, query: String): Boolean {
        if (query.isBlank()) return false
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(query)}&mode=d"))
            .setPackage(Launchers.GOOGLE_MAPS)
        if (start(context, nav)) return true
        return start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}")))
    }

    // ---------------------------------------------------------------------------- Local music

    fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasAudioPermission(context: Context): Boolean =
        context.checkSelfPermission(audioPermission()) == PackageManager.PERMISSION_GRANTED

    data class Song(val uri: Uri, val title: String)

    /** Title, artist or album match in MediaStore (internal storage, SD card and USB). */
    suspend fun findSong(context: Context, query: String): Song? = withContext(Dispatchers.IO) {
        val tries = buildList {
            add(query.trim())
            // "kesariya by arijit" → "kesariya"
            query.split(" by ", " से ", " ka ", " का ").firstOrNull()?.trim()?.let { add(it) }
        }.distinct()
        for (q in tries) {
            querySong(context, q)?.let { return@withContext it }
        }
        null
    }

    private fun querySong(context: Context, q: String): Song? {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE)
        val (selection, args) = if (q.isEmpty()) {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0" to null
        } else {
            val like = "%$q%"
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND (${MediaStore.Audio.Media.TITLE} LIKE ? OR " +
                "${MediaStore.Audio.Media.ARTIST} LIKE ? OR ${MediaStore.Audio.Media.ALBUM} LIKE ? OR " +
                "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?)" to arrayOf(like, like, like, like)
        }
        return try {
            context.contentResolver.query(
                collection, projection, selection, args, "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { c ->
                if (!c.moveToFirst()) return null
                val id = c.getLong(0)
                Song(ContentUris.withAppendedId(collection, id), c.getString(1) ?: q)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Opens [song] in the Ampli MP3 Player when installed, else the default music player. */
    fun playSong(context: Context, song: Song): Boolean {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(song.uri, "audio/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (start(context, Intent(view).setPackage(MediaHub.AMPLI_PACKAGE))) return true
        return start(context, view)
    }

    /** Asks a player to search and play by itself (Ampli first, then any player that supports it). */
    fun playFromSearchIntent(context: Context, query: String): Boolean {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        if (start(context, Intent(intent).setPackage(MediaHub.AMPLI_PACKAGE))) return true
        return start(context, intent)
    }

    // ------------------------------------------------------------------------ Media sessions

    /** Head-unit Bluetooth-music apps (A2DP sink). Others are recognised by name. */
    private val BT_MUSIC_PACKAGES = setOf(
        "com.android.bluetooth", "com.syu.bt", "com.syu.btmusic", "com.microntek.btmusic",
        "com.fyt.btmusic", "com.tw.bt",
    )

    private fun isBluetoothPackage(pkg: String): Boolean =
        pkg in BT_MUSIC_PACKAGES || pkg.contains("bluetooth", true) || pkg.contains("btmusic", true) ||
            pkg.endsWith(".bt", true)

    /** One-shot list of media sessions; empty without notification access. */
    private fun sessions(context: Context): List<MediaController> = try {
        val msm = context.getSystemService(MediaSessionManager::class.java)
        msm?.getActiveSessions(ComponentName(context, DriveNotificationListener::class.java)).orEmpty()
    } catch (_: SecurityException) {
        emptyList()
    }

    private fun playing(c: MediaController) = c.playbackState?.state == PlaybackState.STATE_PLAYING

    enum class BtResult { Playing, NoSession }

    fun playBluetooth(context: Context, query: String): BtResult {
        val bt = sessions(context).firstOrNull { isBluetoothPackage(it.packageName) } ?: return BtResult.NoSession
        val actions = bt.playbackState?.actions ?: 0L
        if (query.isNotEmpty() && actions and PlaybackState.ACTION_PLAY_FROM_SEARCH != 0L) {
            bt.transportControls.playFromSearch(query, null)
        } else {
            bt.transportControls.play()
        }
        return BtResult.Playing
    }

    /** Asks the playing app's own session to search, if it supports it. */
    fun playFromSearchSession(context: Context, query: String): Boolean {
        val c = sessions(context).sortedByDescending { playing(it) }.firstOrNull {
            (it.playbackState?.actions ?: 0L) and PlaybackState.ACTION_PLAY_FROM_SEARCH != 0L &&
                !isBluetoothPackage(it.packageName)
        } ?: return false
        c.transportControls.playFromSearch(query, null)
        return true
    }

    enum class Transport { Play, Pause, Next, Previous }

    /** What played last when a command looked ([MediaTarget.PHONE] or a package). Process memory only. */
    @Volatile private var lastSource: String? = null

    private fun infos(list: List<MediaController>): List<SessionInfo> =
        list.map { SessionInfo(it.packageName, playing(it), isBluetoothPackage(it.packageName)) }

    private fun phoneConnected(app: DriveApp): Boolean = try {
        app.phoneBt.refresh()
        app.phoneBt.connectedPhone()?.let { it.music != false } == true
    } catch (_: Exception) {
        false
    }

    private fun musicActive(context: Context): Boolean = try {
        context.getSystemService(AudioManager::class.java)?.isMusicActive == true
    } catch (_: Exception) {
        false
    }

    /** True while any music is audible or a session says it is playing. */
    fun isPlaying(context: Context): Boolean = musicActive(context) || sessions(context).any { playing(it) }

    /**
     * Music keys by voice, sent to the current player ([MediaTarget.pick]): Ampli MP3 when it is
     * the one playing, else the phone over Bluetooth or any other session, else (for play) Ampli
     * MP3's Car mode. False when nothing could be sent.
     */
    fun transport(app: DriveApp, action: Transport): Boolean {
        val list = sessions(app)
        val info = infos(list)
        val phone = phoneConnected(app)
        val active = musicActive(app)
        MediaTarget.playingSource(info, active, phone)?.let { lastSource = it }
        val target = MediaTarget.pick(
            sessions = info,
            action = MediaTarget.Action.valueOf(action.name),
            ampliPackage = AmpliMusic.PACKAGE,
            ampliInstalled = AmpliMusic.installed(app),
            phoneConnected = phone,
            musicActive = active,
            lastSource = lastSource,
        )
        val code = when (action) {
            Transport.Play -> KeyEvent.KEYCODE_MEDIA_PLAY
            Transport.Pause -> KeyEvent.KEYCODE_MEDIA_PAUSE
            Transport.Next -> KeyEvent.KEYCODE_MEDIA_NEXT
            Transport.Previous -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        return when (target) {
            is MediaTarget.Target.Session -> {
                val c = list.firstOrNull { it.packageName == target.packageName } ?: return mediaKey(app, code)
                val t = c.transportControls
                try {
                    when (action) {
                        Transport.Play -> t.play()
                        Transport.Pause -> t.pause()
                        Transport.Next -> t.skipToNext()
                        Transport.Previous -> t.skipToPrevious()
                    }
                } catch (_: Exception) {
                    return mediaKey(app, code)
                }
                if (action != Transport.Pause) lastSource = if (isBluetoothPackage(c.packageName)) MediaTarget.PHONE else c.packageName
                true
            }
            MediaTarget.Target.PhoneKeys -> {
                app.phoneBt.key(code)
                if (action != Transport.Pause) lastSource = MediaTarget.PHONE
                true
            }
            MediaTarget.Target.OpenAmpli -> {
                lastSource = AmpliMusic.PACKAGE
                AmpliMusic.open(app, autoplay = true)
            }
            MediaTarget.Target.MediaKey -> mediaKey(app, code)
        }
    }

    private fun mediaKey(context: Context, code: Int): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        val now = SystemClock.uptimeMillis()
        return try {
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * "Play <song>" while the phone is the one playing: its Bluetooth session's own search, only
     * when that session says it supports it (most phones over AVRCP don't). False otherwise.
     */
    fun phoneSearch(context: Context, query: String): Boolean {
        val c = sessions(context).firstOrNull { playing(it) && isBluetoothPackage(it.packageName) } ?: return false
        if ((c.playbackState?.actions ?: 0L) and PlaybackState.ACTION_PLAY_FROM_SEARCH == 0L) return false
        return try {
            c.transportControls.playFromSearch(query, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Ampli MP3 Player's own search intent, only when it declares one. */
    fun ampliPlayFromSearch(context: Context, query: String): Boolean {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .setPackage(AmpliMusic.PACKAGE)
        return start(context, intent)
    }

    // ---------------------------------------------------------------- Android Auto / phone link

    private val PHONE_LINK_PACKAGES get() = Launchers.PHONE_LINK_PACKAGES
    private val PHONE_LINK_LABELS get() = Launchers.PHONE_LINK_LABELS

    /** Opens the first phone-link app found and returns its label, or null when there is none. */
    suspend fun openPhoneLink(context: Context, pinned: String?): String? {
        val pm = context.packageManager
        val packages = listOfNotNull(pinned) + PHONE_LINK_PACKAGES
        for (pkg in packages) {
            if (pm.getLaunchIntentForPackage(pkg) != null && Launchers.openPackage(context, pkg, quiet = true)) {
                return labelOf(context, pkg)
            }
        }
        val app = InstalledApps.load(context).firstOrNull { a ->
            PHONE_LINK_LABELS.any { a.label.contains(it, ignoreCase = true) }
        } ?: return null
        return if (Launchers.openComponent(context, app.packageName, app.activityName)) app.label else null
    }

    private fun labelOf(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        "Android Auto"
    }

    // ------------------------------------------------------------------------------- Helpers

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
