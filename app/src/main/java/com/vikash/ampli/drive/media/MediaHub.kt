package com.vikash.ampli.drive.media

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.service.DriveNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One app with an active media session (head unit BT music, Ampli, a USB player...). */
data class MediaSource(val packageName: String, val label: String)

data class NowPlaying(
    val packageName: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val art: Bitmap?,
    val durationMs: Long,
    /** Position reported by the player at [positionUpdatedAt] (elapsedRealtime). */
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val playbackSpeed: Float,
    val isPlaying: Boolean,
    /** The player's name ("Ampli"), shown when the session gives no title. */
    val sourceLabel: String? = null,
) {
    /** Best estimate of the current position, advancing while playing. */
    fun positionAt(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        if (positionMs < 0) return 0
        val extra = if (isPlaying && positionUpdatedAt > 0) {
            ((nowElapsed - positionUpdatedAt) * playbackSpeed).toLong()
        } else {
            0L
        }
        val p = positionMs + extra
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

data class MediaUiState(
    /** Notification access granted; without it Android hides other apps' sessions. */
    val accessGranted: Boolean = false,
    val sources: List<MediaSource> = emptyList(),
    val selectedPackage: String? = null,
    val nowPlaying: NowPlaying? = null,
)

/**
 * Controls whatever is playing through MediaSessionManager. All calls on the main thread.
 * Call [start] when the home screen shows and [stop] when it hides.
 */
class MediaHub(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val msm: MediaSessionManager? = appContext.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(appContext, DriveNotificationListener::class.java)

    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private var running = false
    private var controllers: List<MediaController> = emptyList()
    private val callbacks = HashMap<MediaSession.Token, MediaController.Callback>()
    private var selected: MediaController? = null

    /** Package the driver picked from the source chips; wins while its session exists. */
    private var pinnedPackage: String? = null

    private var artSource: Bitmap? = null
    private var artScaled: Bitmap? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> onSessions(list.orEmpty()) }

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(appContext).contains(appContext.packageName)

    fun start() {
        if (!hasAccess() || msm == null) {
            _state.value = MediaUiState(accessGranted = false)
            return
        }
        if (running) {
            refresh()
            return
        }
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, main)
            running = true
            onSessions(msm.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            // Access was revoked between the check and the call.
            running = false
            _state.value = MediaUiState(accessGranted = false)
        }
    }

    fun stop() {
        if (running) {
            try {
                msm?.removeOnActiveSessionsChangedListener(sessionsListener)
            } catch (_: Exception) {
            }
        }
        running = false
        unregisterAll()
        controllers = emptyList()
        selected = null
    }

    /** Re-reads the session list, for example after returning from Settings. */
    fun refresh() {
        if (!running) {
            start()
            return
        }
        try {
            onSessions(msm?.getActiveSessions(listenerComponent).orEmpty())
        } catch (e: SecurityException) {
            stop()
            _state.value = MediaUiState(accessGranted = false)
        }
    }

    fun select(packageName: String) {
        pinnedPackage = packageName
        publish()
    }

    fun playPause() {
        val c = selected ?: return
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) c.transportControls.pause() else c.transportControls.play()
    }

    /**
     * Play / pause on the session the music card shows (the rail's Play/Pause key). False when
     * there is no session to control (no notification access, nothing has played yet).
     */
    fun togglePlayback(): Boolean {
        val c = selected ?: return false
        return try {
            if (isPlaying(c)) c.transportControls.pause() else c.transportControls.play()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun next() {
        selected?.transportControls?.skipToNext()
    }

    fun previous() {
        selected?.transportControls?.skipToPrevious()
    }

    /** Package of the session shown in the music card, if any. */
    fun currentPackage(): String? = selected?.packageName

    /** Presses play on [pkg]'s media session, if it has one. Works while the launcher is hidden. */
    fun play(pkg: String): Boolean {
        if (!hasAccess() || msm == null) return false
        return try {
            val c = msm.getActiveSessions(listenerComponent).firstOrNull { it.packageName == pkg } ?: return false
            c.transportControls.play()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Asks [pkg]'s media session to find and play [query] (Ampli MP3 searches the driver's own
     * songs). False when it has no session or refuses. Works while the launcher is hidden.
     */
    fun playFromSearch(pkg: String, query: String): Boolean {
        if (!hasAccess() || msm == null) return false
        return try {
            val c = msm.getActiveSessions(listenerComponent).firstOrNull { it.packageName == pkg } ?: return false
            if (((c.playbackState?.actions ?: 0L) and PlaybackState.ACTION_PLAY_FROM_SEARCH) == 0L) return false
            c.transportControls.playFromSearch(query, android.os.Bundle())
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Apps with a media session right now, playing ones first. Works while the home screen is
     * hidden too (it asks MediaSessionManager directly). Empty without notification access.
     */
    fun activePackages(): List<String> {
        if (!hasAccess() || msm == null) return emptyList()
        return try {
            msm.getActiveSessions(listenerComponent)
                .sortedByDescending { isPlaying(it) }
                .map { it.packageName }
                .filter { it != appContext.packageName }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun onSessions(list: List<MediaController>) {
        unregisterAll()
        controllers = list
        for (c in list) {
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = onControllerChanged(c)
                override fun onMetadataChanged(metadata: MediaMetadata?) = onControllerChanged(c)
                override fun onSessionDestroyed() = refresh()
            }
            try {
                c.registerCallback(cb, main)
                callbacks[c.sessionToken] = cb
            } catch (_: Exception) {
            }
        }
        if (pinnedPackage != null && list.none { it.packageName == pinnedPackage }) pinnedPackage = null
        publish()
    }

    private fun onControllerChanged(c: MediaController) {
        // A player that just started playing takes over the card unless the pinned one is playing.
        if (isPlaying(c) && c.packageName != pinnedPackage) {
            val pinned = controllers.firstOrNull { it.packageName == pinnedPackage }
            if (pinned == null || !isPlaying(pinned)) pinnedPackage = null
        }
        publish()
    }

    private fun publish() {
        val list = controllers
        val last = (appContext as? DriveApp)?.prefs?.lastMediaPackage
        val pick = MediaPick.choose(
            list.map { MediaPick.Candidate(it.packageName, isPlaying(it), titleOf(it.metadata) != null) },
            pinned = pinnedPackage,
            preferred = listOfNotNull(last, AMPLI_PACKAGE),
            self = appContext.packageName,
        )
        val chosen = pick?.let { list.getOrNull(it) }
        selected = chosen
        // Remembered for the Music button, so it reopens the last player after a restart.
        chosen?.packageName?.takeIf { it != appContext.packageName }?.let { pkg ->
            (appContext as? DriveApp)?.prefs?.lastMediaPackage = pkg
        }
        val sources = list.map { it.packageName }.distinct().map { MediaSource(it, labelFor(it)) }
        _state.value = MediaUiState(
            accessGranted = true,
            sources = sources,
            selectedPackage = chosen?.packageName,
            nowPlaying = chosen?.let { toNowPlaying(it) },
        )
    }

    private fun toNowPlaying(c: MediaController): NowPlaying {
        val md = c.metadata
        val ps = c.playbackState
        return NowPlaying(
            packageName = c.packageName,
            title = titleOf(md),
            artist = blankToNull(md?.getString(MediaMetadata.METADATA_KEY_ARTIST))
                ?: blankToNull(md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))
                ?: blankToNull(md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))
                ?: safeDescription(md)?.subtitle?.toString()?.let { blankToNull(it) },
            album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            art = artFrom(md),
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            positionMs = ps?.position ?: 0L,
            positionUpdatedAt = ps?.lastPositionUpdateTime ?: 0L,
            playbackSpeed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            isPlaying = playingState(ps?.state),
            sourceLabel = labelFor(c.packageName),
        )
    }

    /**
     * The track's name: TITLE, then DISPLAY_TITLE, then the description Android builds from the
     * metadata (players that only fill that, or put the name in a compat key). Blank counts as none.
     */
    private fun titleOf(md: MediaMetadata?): String? {
        md ?: return null
        return blankToNull(md.getString(MediaMetadata.METADATA_KEY_TITLE))
            ?: blankToNull(md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))
            ?: safeDescription(md)?.title?.toString()?.let { blankToNull(it) }
    }

    private fun safeDescription(md: MediaMetadata?) = try {
        md?.description
    } catch (_: Exception) {
        null
    }

    private fun blankToNull(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() }

    /** Album art, scaled down once so a 3000 px cover doesn't sit in memory on a 2 GB unit. */
    private fun artFrom(md: MediaMetadata?): Bitmap? {
        val src = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        if (src === artSource) return artScaled
        val scaled = try {
            val max = maxOf(src.width, src.height)
            if (max > MAX_ART_PX) {
                val f = MAX_ART_PX.toFloat() / max
                Bitmap.createScaledBitmap(src, (src.width * f).toInt().coerceAtLeast(1), (src.height * f).toInt().coerceAtLeast(1), true)
            } else {
                src
            }
        } catch (_: Exception) {
            null
        }
        artSource = src
        artScaled = scaled
        return scaled
    }

    private fun unregisterAll() {
        for (c in controllers) {
            val cb = callbacks[c.sessionToken] ?: continue
            try {
                c.unregisterCallback(cb)
            } catch (_: Exception) {
            }
        }
        callbacks.clear()
    }

    private fun isPlaying(c: MediaController): Boolean = playingState(c.playbackState?.state)

    private fun labelFor(pkg: String): String {
        KNOWN_LABELS[pkg]?.let { return it }
        return try {
            val pm = appContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg.substringAfterLast('.')
        }
    }

    companion object {
        /** A session counts as playing (card's bars move, rail shows Pause) only in STATE_PLAYING. */
        fun playingState(state: Int?): Boolean = state == PlaybackState.STATE_PLAYING

        const val AMPLI_PACKAGE = "com.vikash.ampli"
        private const val MAX_ART_PX = 512
        private val KNOWN_LABELS = mapOf(AMPLI_PACKAGE to "Ampli")
    }
}
