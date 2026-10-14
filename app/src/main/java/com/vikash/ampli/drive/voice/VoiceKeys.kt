package com.vikash.ampli.drive.voice

/**
 * Steering-wheel and hardware "voice" buttons. Head units map that key to different things, so
 * all of these start "Hey Ampli":
 * - KEYCODE_VOICE_ASSIST and KEYCODE_SEARCH, when they reach the launcher while it is in front;
 * - a long press of KEYCODE_HEADSETHOOK or the play/pause media key (the Bluetooth-headset way);
 * - while another app is in front, Android turns those keys into an "assist" / voice-command /
 *   voice-search intent, which [com.vikash.ampli.drive.VoiceKeyActivity] answers once the driver
 *   picks Car Hub as the handler (or as the default assistant app).
 *
 * Key codes and actions are written out as literals so this stays a plain-JVM unit-testable file.
 */
object VoiceKeys {
    const val KEYCODE_HEADSETHOOK = 79
    const val KEYCODE_SEARCH = 84
    const val KEYCODE_MEDIA_PLAY_PAUSE = 85
    const val KEYCODE_VOICE_ASSIST = 231

    /** Starts listening straight away on key down. */
    fun isVoiceKey(keyCode: Int): Boolean = keyCode == KEYCODE_VOICE_ASSIST || keyCode == KEYCODE_SEARCH

    /** Starts listening on a long press; a short press keeps its media meaning. */
    fun isLongPressVoiceKey(keyCode: Int): Boolean =
        keyCode == KEYCODE_HEADSETHOOK || keyCode == KEYCODE_MEDIA_PLAY_PAUSE

    /** Intent actions Android sends for the assist / voice keys (see the manifest filters). */
    val ACTIONS = setOf(
        "android.intent.action.ASSIST",
        "android.intent.action.VOICE_ASSIST",
        "android.intent.action.VOICE_COMMAND",
        "android.intent.action.SEARCH_LONG_PRESS",
        // Long-press headset hook (MediaSessionService): web search when unlocked, hands-free when locked.
        "android.speech.action.WEB_SEARCH",
        "android.speech.action.VOICE_SEARCH_HANDS_FREE",
    )

    /**
     * True for a voice-key intent. A web search that already carries a query (for example
     * "Web search" on selected text in another app) is not a voice key press.
     */
    fun isVoiceAction(action: String?, query: String? = null): Boolean =
        action != null && action in ACTIONS && !(action == "android.speech.action.WEB_SEARCH" && !query.isNullOrBlank())
}
