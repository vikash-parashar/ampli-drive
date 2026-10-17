package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class WakeModelHashTest {
    @Test
    fun sha256OfKnownText() {
        val f = File.createTempFile("hash", ".bin")
        try {
            f.writeText("abc")
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", WakeModel.sha256Hex(f))
        } finally {
            f.delete()
        }
    }
}
