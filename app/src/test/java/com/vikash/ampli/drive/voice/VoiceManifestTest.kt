package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * voice/lines.json is what the voice-pack workflow (.github/workflows/voice-pack.yml) records. It
 * is generated from HaryanviLines.kt; this test fails when it is out of date. To regenerate:
 *
 *     UPDATE_VOICE_LINES=1 ./gradlew testDebugUnitTest --tests '*VoiceManifestTest'
 */
class VoiceManifestTest {

    @Test
    fun linesJsonIsUpToDate() {
        val file = File("../voice/lines.json")
        val want = VoiceClips.manifestJson()
        if (System.getenv("UPDATE_VOICE_LINES") == "1" || !file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText(want)
        }
        assertEquals(
            "voice/lines.json is stale: run UPDATE_VOICE_LINES=1 ./gradlew testDebugUnitTest --tests '*VoiceManifestTest'",
            want,
            file.readText(),
        )
    }
}
