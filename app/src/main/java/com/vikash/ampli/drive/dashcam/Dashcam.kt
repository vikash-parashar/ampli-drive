package com.vikash.ampli.drive.dashcam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import com.vikash.ampli.drive.DriveApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Car › Dashcam (Pro, off until the driver switches it on). Loop recording in [LoopStorage.SEGMENT_MS]
 * pieces into the app's own folder (Android/data/…/files/dashcam); the oldest clips go when the
 * storage cap is reached. A hard brake, a crash or "save kar" keeps the current clip (renamed
 * saved_…). Video only, no sound, so the wake word keeps its microphone. Nothing leaves the unit.
 *
 * Head units without a camera (most of them, unless a reversing or front camera shows up as a
 * Camera2 device) see "No camera found" and nothing else happens.
 */
class Dashcam(private val app: DriveApp) {
    private val sp = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(sp.getBoolean(KEY_ON, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _capMb = MutableStateFlow(sp.getLong(KEY_CAP, LoopStorage.DEFAULT_CAP_MB))
    val capMb: StateFlow<Long> = _capMb.asStateFlow()

    /** What the dashcam screen shows. */
    data class State(
        val recording: Boolean = false,
        /** When the current segment started (the REC timer). */
        val segmentStartMs: Long? = null,
        /** When this recording session started (the REC timer). */
        val sinceMs: Long? = null,
        val error: Error? = null,
    )

    enum class Error { NoCamera, CameraBusy, NoPermission, NoStorage }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _clips = MutableStateFlow<List<Clip>>(emptyList())
    val clips: StateFlow<List<Clip>> = _clips.asStateFlow()

    /** Time of the last "clip saved", for the green banner (cleared by the screen). */
    private val _saved = MutableStateFlow<Long?>(null)
    val saved: StateFlow<Long?> = _saved.asStateFlow()

    /** The running recorder, set by [DashcamService]. */
    @Volatile
    internal var recorder: DashcamRecorder? = null

    /** FEATURE_CAMERA_ANY and at least one Camera2 device (USB UVC cameras often aren't one). */
    val hasCamera: Boolean by lazy {
        try {
            app.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) &&
                app.getSystemService(CameraManager::class.java)?.cameraIdList?.isNotEmpty() == true
        } catch (_: Exception) {
            false
        }
    }

    val folder: File get() = File(app.getExternalFilesDir(null) ?: app.filesDir, DIR).apply { mkdirs() }

    fun hasPermission(): Boolean = app.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun capBytes(): Long = _capMb.value * 1024L * 1024L

    fun setCapMb(mb: Long) {
        _capMb.value = mb
        sp.edit().putLong(KEY_CAP, mb).apply()
        recorder?.prune()
    }

    /** The screen's switch. Starts recording at once when everything allows it. */
    fun setEnabled(on: Boolean) {
        _enabled.value = on
        sp.edit().putBoolean(KEY_ON, on).apply()
        if (on) startIfEnabled() else DashcamService.stop(app)
    }

    /** Launcher in front (MainActivity.onStart) or the switch: a camera service may only start from the foreground. */
    fun startIfEnabled(): Boolean {
        if (!_enabled.value || !app.prefs.pro.value.isPro) return false
        if (!hasCamera) {
            setError(Error.NoCamera)
            return false
        }
        if (!hasPermission()) {
            setError(Error.NoPermission)
            return false
        }
        if (DashcamService.running) return true
        return DashcamService.start(app)
    }

    /** Ignition off / shutdown: close the clip so it is playable. */
    fun onIgnitionOff() {
        if (DashcamService.running) DashcamService.stop(app)
    }

    /** "Save kar", the screen's Save button. False when nothing is recording. */
    fun saveNow(byVoice: Boolean = false): Boolean {
        val r = recorder ?: return false
        if (!_state.value.recording) return false
        r.lockCurrent()
        return true
    }

    /** A crash (CarMonitor) or a hard brake (the recorder's own sensor): keep the clip quietly. */
    fun onJolt() {
        recorder?.lockCurrent()
    }

    fun refreshClips() {
        val files = folder.listFiles() ?: emptyArray()
        _clips.value = files.mapNotNull { LoopStorage.parse(it.name, it.length()) }.sortedByDescending { it.startMs }
    }

    fun delete(clip: Clip) {
        File(folder, clip.name).delete()
        refreshClips()
    }

    fun clearSaved() {
        _saved.value = null
    }

    internal fun setState(s: State) {
        _state.value = s
    }

    internal fun setError(e: Error?) {
        _state.value = _state.value.copy(error = e, recording = if (e != null) false else _state.value.recording)
    }

    internal fun onSaved() {
        _saved.value = System.currentTimeMillis()
        refreshClips()
    }

    companion object {
        private const val FILE = "ampli_dashcam"
        private const val KEY_ON = "on"
        private const val KEY_CAP = "cap_mb"
        const val DIR = "dashcam"

        /** 720p H.264 at about 4 Mbit/s: roughly 1.8 GB an hour. */
        const val BIT_RATE = 4_000_000
    }
}
