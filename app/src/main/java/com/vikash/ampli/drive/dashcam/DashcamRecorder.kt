package com.vikash.ampli.drive.dashcam

import android.annotation.SuppressLint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaCodec
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.os.StatFs
import android.util.Log
import android.util.Size
import android.view.Surface
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.location.FixListener
import java.io.File

/**
 * Camera2 → MediaRecorder loop, all on its own [HandlerThread] (never the main thread). One
 * persistent input surface feeds every segment, so the capture session stays open while the
 * recorder is swapped every [LoopStorage.SEGMENT_MS]. After each segment the folder is pruned with
 * [LoopStorage.toDelete]. A lock asked for while a segment records is applied when it closes.
 */
class DashcamRecorder(private val app: DriveApp, private val cam: Dashcam) {
    private val thread = HandlerThread("dashcam").apply { start() }
    private val handler = Handler(thread.looper)

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var surface: Surface? = null
    private var recorder: MediaRecorder? = null
    private var size = Size(1280, 720)

    private var activeName: String? = null
    private var activeStart = 0L
    private var lockActive = false
    private var stopped = false

    private val jolt = joltDetector()
    private val sensors = app.getSystemService(SensorManager::class.java)
    private val accel: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val v = e.values
            if (jolt.onAccel(System.currentTimeMillis(), v[0], v[1], v[2], e.sensor.type == Sensor.TYPE_ACCELEROMETER)) lockCurrent()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val fixListener = FixListener { _, _, raw ->
        if (raw != null && jolt.onSpeed(System.currentTimeMillis(), raw)) lockCurrent()
    }

    private val nextSegment = Runnable { rollSegment() }

    @SuppressLint("MissingPermission") // Dashcam.startIfEnabled checks CAMERA first.
    fun start() {
        handler.post {
            val manager = app.getSystemService(CameraManager::class.java)
            val id = try {
                manager?.let { pickCamera(it) }
            } catch (_: Exception) {
                null
            }
            if (manager == null || id == null) {
                cam.setError(Dashcam.Error.NoCamera)
                return@post
            }
            if (freeBytes() < LoopStorage.MIN_FREE_BYTES) {
                cam.setError(Dashcam.Error.NoStorage)
                return@post
            }
            val persistent = MediaCodec.createPersistentInputSurface()
            surface = persistent
            // Prepare the first recorder before the session so the surface has a consumer.
            if (!newRecorder()) {
                cam.setError(Dashcam.Error.CameraBusy)
                return@post
            }
            try {
                manager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(d: CameraDevice) {
                        if (stopped) {
                            d.close()
                            return
                        }
                        device = d
                        openSession(d, persistent)
                    }

                    override fun onDisconnected(d: CameraDevice) {
                        d.close()
                        device = null
                        cam.setError(Dashcam.Error.CameraBusy)
                    }

                    override fun onError(d: CameraDevice, error: Int) {
                        d.close()
                        device = null
                        cam.setError(Dashcam.Error.CameraBusy)
                    }
                }, handler)
            } catch (e: Exception) {
                Log.w(TAG, "openCamera: ${e.message}")
                cam.setError(if (e is SecurityException) Dashcam.Error.NoPermission else Dashcam.Error.CameraBusy)
            }
        }
        accel?.let { sensors?.registerListener(accelListener, it, SensorManager.SENSOR_DELAY_UI, handler) }
        app.speedTracker.addFixListener(fixListener)
    }

    @Suppress("DEPRECATION") // createCaptureSession(List) is the API 26 form.
    private fun openSession(d: CameraDevice, target: Surface) {
        try {
            d.createCaptureSession(listOf(target), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (stopped) return
                    session = s
                    try {
                        val req = d.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                            addTarget(target)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        }.build()
                        s.setRepeatingRequest(req, null, handler)
                        beginSegment()
                    } catch (e: Exception) {
                        Log.w(TAG, "repeating request: ${e.message}")
                        cam.setError(Dashcam.Error.CameraBusy)
                    }
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    cam.setError(Dashcam.Error.CameraBusy)
                }
            }, handler)
        } catch (e: Exception) {
            Log.w(TAG, "session: ${e.message}")
            cam.setError(Dashcam.Error.CameraBusy)
        }
    }

    /** A new MediaRecorder on the persistent surface, writing a fresh loop_… file. */
    private fun newRecorder(): Boolean {
        val s = surface ?: return false
        val start = System.currentTimeMillis()
        val name = LoopStorage.fileName(start)
        val file = File(cam.folder, name)
        val r = MediaRecorder()
        return try {
            r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setOutputFile(file.absolutePath)
            r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            r.setVideoEncodingBitRate(Dashcam.BIT_RATE)
            r.setVideoFrameRate(FPS)
            r.setVideoSize(size.width, size.height)
            r.setInputSurface(s)
            r.prepare()
            recorder = r
            activeName = name
            activeStart = start
            true
        } catch (e: Exception) {
            Log.w(TAG, "recorder: ${e.message}")
            r.release()
            file.delete()
            false
        }
    }

    private fun beginSegment() {
        val r = recorder ?: return
        try {
            r.start()
        } catch (e: Exception) {
            Log.w(TAG, "start: ${e.message}")
            cam.setError(Dashcam.Error.CameraBusy)
            return
        }
        cam.setState(Dashcam.State(recording = true, segmentStartMs = activeStart, sinceMs = cam.state.value.sinceMs ?: activeStart))
        prune()
        handler.postDelayed(nextSegment, LoopStorage.SEGMENT_MS)
    }

    /** Close the current file, start the next one, then prune. */
    private fun rollSegment() {
        if (stopped) return
        finishSegment()
        if (freeBytes() < LoopStorage.MIN_FREE_BYTES / 2) {
            cam.setError(Dashcam.Error.NoStorage)
            return
        }
        if (newRecorder()) beginSegment() else cam.setError(Dashcam.Error.CameraBusy)
    }

    private fun finishSegment() {
        handler.removeCallbacks(nextSegment)
        val r = recorder ?: return
        recorder = null
        val name = activeName
        try {
            r.stop()
        } catch (_: Exception) {
            // Stopped within a second of starting: the file is empty.
            name?.let { File(cam.folder, it).delete() }
        }
        r.release()
        if (lockActive && name != null) {
            rename(name)
            cam.onSaved()
        }
        lockActive = false
        activeName = null
    }

    /** "Save kar", a hard brake or a crash: keep this clip (and the one before when it just began). */
    fun lockCurrent() {
        handler.post {
            val clips = list()
            val keep = LoopStorage.toLock(clips, activeName, System.currentTimeMillis())
            var savedNow = false
            for (c in keep) {
                if (c.name == activeName) lockActive = true else if (!c.locked) savedNow = rename(c.name) || savedNow
            }
            // The banner shows at once; the active clip is renamed when it closes.
            if (lockActive || savedNow) cam.onSaved()
        }
    }

    private fun rename(name: String): Boolean {
        val from = File(cam.folder, name)
        return from.exists() && from.renameTo(File(cam.folder, LoopStorage.lockedName(name)))
    }

    private fun list(): List<Clip> =
        (cam.folder.listFiles() ?: emptyArray()).mapNotNull { LoopStorage.parse(it.name, it.length()) }

    /** Delete the oldest clips past the cap (never the one recording). */
    fun prune() {
        handler.post {
            val cap = cam.capBytes()
            val doomed = LoopStorage.toDelete(
                clips = list(),
                capBytes = cap,
                lockedCapBytes = LoopStorage.lockedCap(cap),
                maxLocked = LoopStorage.MAX_LOCKED,
                activeName = activeName,
                freeBytes = freeBytes(),
            )
            doomed.forEach { File(cam.folder, it.name).delete() }
            cam.refreshClips()
        }
    }

    private fun freeBytes(): Long = try {
        StatFs(cam.folder.absolutePath).availableBytes
    } catch (_: Exception) {
        Long.MAX_VALUE
    }

    /** External camera first (a USB dashcam that shows up in Camera2), then rear, then any. */
    private fun pickCamera(m: CameraManager): String? {
        val ids = m.cameraIdList
        if (ids.isEmpty()) return null
        fun facing(id: String) = m.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
        val id = ids.firstOrNull { facing(it) == CameraCharacteristics.LENS_FACING_EXTERNAL }
            ?: ids.firstOrNull { facing(it) == CameraCharacteristics.LENS_FACING_BACK }
            ?: ids.first()
        val map = m.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(MediaRecorder::class.java).orEmpty()
        size = sizes.filter { it.width <= 1280 && it.height <= 720 }.maxByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { it.width * it.height }
            ?: Size(1280, 720)
        return id
    }

    /** Ignition off, the switch, or the service ending. Closes the clip so it plays back. */
    fun stop() {
        sensors?.unregisterListener(accelListener)
        app.speedTracker.removeFixListener(fixListener)
        handler.post {
            stopped = true
            try {
                session?.stopRepeating()
            } catch (_: Exception) {
            }
            finishSegment()
            session?.close()
            session = null
            device?.close()
            device = null
            surface?.release()
            surface = null
            cam.setState(Dashcam.State())
            cam.refreshClips()
            thread.quitSafely()
        }
    }

    private companion object {
        const val TAG = "DashcamRecorder"
        const val FPS = 30
    }
}
