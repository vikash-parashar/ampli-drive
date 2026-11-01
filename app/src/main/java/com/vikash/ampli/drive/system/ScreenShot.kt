package com.vikash.ampli.drive.system

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/**
 * The quick panel's Screenshot tile. Android 9+ with the Ampli power helper on: Android's own
 * screenshot. Android 8.x (and 9 without the helper): Android's "start capturing?" prompt, then
 * one frame from a virtual display, saved as a PNG in Pictures/Ampli. Nothing leaves the unit.
 */
object ScreenShot {

    /** Android's own screenshot through the helper (Android 9+). */
    fun viaHelper(): Boolean = PowerHelperService.screenshot()

    /** The one-frame capture needs no foreground service only below Android 10. */
    val projectionWorks: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** Android's capture prompt, or null when the unit has none. */
    fun captureIntent(context: Context): Intent? = try {
        context.getSystemService(MediaProjectionManager::class.java)?.createScreenCaptureIntent()
    } catch (_: Exception) {
        null
    }

    /**
     * One frame after the driver allowed the prompt ([resultCode], [data]); the saved file, or null.
     * Waits a moment first so the prompt itself is gone from the picture.
     */
    suspend fun capture(context: Context, resultCode: Int, data: Intent): File? {
        val app = context.applicationContext
        val projection = try {
            app.getSystemService(MediaProjectionManager::class.java)?.getMediaProjection(resultCode, data)
        } catch (_: Exception) {
            null
        } ?: return null
        delay(PROMPT_GONE_MS)
        val bitmap = try {
            grab(app, projection)
        } finally {
            try {
                projection.stop()
            } catch (_: Exception) {
            }
        } ?: return null
        return withContext(Dispatchers.IO) { save(app, bitmap) }
    }

    @Suppress("DEPRECATION")
    private suspend fun grab(context: Context, projection: MediaProjection): Bitmap? {
        val metrics = DisplayMetrics()
        try {
            context.getSystemService(WindowManager::class.java)?.defaultDisplay?.getRealMetrics(metrics)
        } catch (_: Exception) {
            return null
        }
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        if (w <= 0 || h <= 0) return null
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        val handler = Handler(Looper.getMainLooper())
        var display: VirtualDisplay? = null
        try {
            return withTimeoutOrNull(FRAME_WAIT_MS) {
                suspendCancellableCoroutine<Bitmap?> { cont ->
                    reader.setOnImageAvailableListener({ r ->
                        val image = try {
                            r.acquireLatestImage()
                        } catch (_: Exception) {
                            null
                        } ?: return@setOnImageAvailableListener
                        val bmp = try {
                            val plane = image.planes[0]
                            val pixelStride = plane.pixelStride
                            val rowPadding = plane.rowStride - pixelStride * w
                            val padded = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888)
                            padded.copyPixelsFromBuffer(plane.buffer)
                            if (padded.width == w) padded else Bitmap.createBitmap(padded, 0, 0, w, h).also { padded.recycle() }
                        } catch (_: Exception) {
                            null
                        } finally {
                            image.close()
                        }
                        r.setOnImageAvailableListener(null, null)
                        if (cont.isActive) cont.resume(bmp)
                    }, handler)
                    display = try {
                        projection.createVirtualDisplay(
                            "ampli-screenshot", w, h, metrics.densityDpi,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, handler,
                        )
                    } catch (_: Exception) {
                        null
                    }
                    if (display == null && cont.isActive) cont.resume(null)
                }
            }
        } finally {
            try {
                display?.release()
            } catch (_: Exception) {
            }
            reader.close()
        }
    }

    /** Pictures/Ampli when storage may be written (asked once), else Car Hub's own Pictures folder. */
    @Suppress("DEPRECATION")
    private fun save(context: Context, bitmap: Bitmap): File? {
        val name = "Ampli_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date()) + ".png"
        val dirs = buildList {
            if (QuickActions.hasStorageWrite(context) && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Ampli"))
            }
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { add(File(it, "Ampli")) }
        }
        try {
            for (dir in dirs) {
                val file = File(dir, name)
                val ok = try {
                    dir.mkdirs()
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } catch (_: Exception) {
                    false
                }
                if (ok) {
                    try {
                        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
                    } catch (_: Exception) {
                    }
                    return file
                }
            }
            return null
        } finally {
            bitmap.recycle()
        }
    }

    private const val PROMPT_GONE_MS = 450L
    private const val FRAME_WAIT_MS = 3_000L
}
