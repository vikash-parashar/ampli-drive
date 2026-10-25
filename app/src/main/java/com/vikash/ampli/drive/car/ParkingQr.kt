package com.vikash.ampli.drive.car

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.Locale

/**
 * The parking spot as a QR code: a phone camera scans it and Google Maps opens at the car. Made
 * on the unit (no internet). The bitmap has one pixel per QR module (about 30x30), and Compose
 * scales it up without smoothing, so it costs almost no memory.
 */
object ParkingQr {

    /** https://maps.google.com/?q=lat,lon with six decimals (about 10 cm). */
    fun link(lat: Double, lon: Double): String = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lon)

    /** QR modules for [text]: true = dark. Pure Java (zxing core), so it is unit-tested on the JVM. */
    fun modules(text: String, quietZone: Int = 2): Array<BooleanArray> {
        val hints = mapOf(
            EncodeHintType.MARGIN to quietZone,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        return Array(m.height) { y -> BooleanArray(m.width) { x -> m.get(x, y) } }
    }

    private var cached: Pair<String, Bitmap>? = null

    /** One pixel per module, black on white. The last one is kept until memory is trimmed. */
    @Synchronized
    fun bitmap(text: String): Bitmap? {
        cached?.let { (t, b) -> if (t == text && !b.isRecycled) return b }
        return try {
            val mods = modules(text)
            val h = mods.size
            val w = mods.firstOrNull()?.size ?: return null
            val px = IntArray(w * h) { i -> if (mods[i / w][i % w]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
            Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).also { cached = text to it }
        } catch (_: Exception) {
            null
        }
    }

    /** MemoryTrim: drop the cached bitmap (made again in a millisecond when needed). */
    @Synchronized
    fun clear() {
        cached = null
    }
}
