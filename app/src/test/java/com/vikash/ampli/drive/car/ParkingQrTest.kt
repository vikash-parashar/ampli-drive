package com.vikash.ampli.drive.car

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingQrTest {

    @Test
    fun linkIsAGoogleMapsQueryWithSixDecimals() {
        assertEquals("https://maps.google.com/?q=28.613900,77.209000", ParkingQr.link(28.6139, 77.209))
        assertEquals("https://maps.google.com/?q=-33.856800,151.215300", ParkingQr.link(-33.8568, 151.2153))
        assertEquals(ParkingQr.link(12.9716, 77.5946), ParkedSpot(12.9716, 77.5946, 0L).mapsUrl)
    }

    @Test
    fun qrDecodesBackToTheLink() {
        val link = ParkingQr.link(28.6139, 77.209)
        val mods = ParkingQr.modules(link)
        assertTrue("small: ${mods.size}", mods.size in 21..45)
        // Scale up 4x for the reader, as the screen does.
        val scale = 4
        val n = mods.size * scale
        val px = IntArray(n * n) { i ->
            val y = i / n / scale
            val x = i % n / scale
            if (mods[y][x]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(n, n, px))))
        assertEquals(link, result.text)
    }
}
