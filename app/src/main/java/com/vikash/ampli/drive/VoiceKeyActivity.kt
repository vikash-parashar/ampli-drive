package com.vikash.ampli.drive

import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.vikash.ampli.drive.voice.VoiceKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Invisible trampoline for the steering-wheel voice key while another app (Google Maps) is in
 * front. Android sends ASSIST / VOICE_COMMAND / voice-search intents for that key; this starts
 * "Hey Ampli", which talks over the current app.
 *
 * It stays (fully transparent) until the conversation ends: while it is on screen the app counts
 * as "in use", so Android lets it use the microphone without a microphone foreground service.
 */
class VoiceKeyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DriveApp
        if (!VoiceKeys.isVoiceAction(intent?.action, intent?.getStringExtra("query"))) {
            finish()
            return
        }
        app.voice.start()
        lifecycleScope.launch {
            // start() sets busy synchronously, so this waits for the session to end.
            app.voice.busy.first { !it }
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        // Another app or the launcher took over: don't linger in the back stack.
        if (!isFinishing) finish()
    }
}
