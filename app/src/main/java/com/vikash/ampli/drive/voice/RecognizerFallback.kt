package com.vikash.ampli.drive.voice

/**
 * How one turn on Android's own SpeechRecognizer ended, for the offline fallback. Some head
 * units (Unisoc SC7731E and similar) report a recognition service that is only a vendor stub: it
 * never hears anything and ends in "no match", "speech timeout" or our own timeout.
 */
enum class SystemAttempt {
    /** Words came back. */
    Heard,

    /** No words, but the engine did hear sound (speech start, level changes or partials). */
    QuietWithActivity,

    /** No words and no sign the engine ever listened: a stub, or a dead engine. */
    QuietNoActivity,

    /** Network, client, server, busy or another engine error, or it would not start. */
    Failed,

    /** The language is not installed there; the next language tag may work. */
    LanguageMissing,
}

/** Which recogniser served a turn (Settings › Voice › Check voice). */
enum class VoiceEngine(val label: String) {
    None("none yet"),
    System("system recognizer"),
    OfflineEnglish("offline English model"),
    OfflineHindi("offline Hindi model"),
    ;

    val offline: Boolean get() = this == OfflineEnglish || this == OfflineHindi
}

/** Pure decisions behind [Listener]'s fallback from the system recogniser to offline Vosk. */
object RecognizerFallback {
    /** Turns in a row without any sign of life before the system recogniser is skipped. */
    const val DEAD_AFTER = 2

    /** True when [attempt] says the system recogniser is not really working. */
    fun isDead(attempt: SystemAttempt): Boolean =
        attempt == SystemAttempt.QuietNoActivity || attempt == SystemAttempt.Failed

    /** Go on to the offline model in the same turn (instead of reporting silence or failure). */
    fun fallThrough(attempt: SystemAttempt, offlineReady: Boolean): Boolean = offlineReady && isDead(attempt)

    /** Skip the system recogniser entirely and listen offline straight away. */
    fun skipSystem(deadTurns: Int, offlineReady: Boolean): Boolean = offlineReady && deadTurns >= DEAD_AFTER

    /** The dead-turn count after a turn that ended in [attempt]: any sign of life resets it. */
    fun nextDeadTurns(current: Int, attempt: SystemAttempt): Int = when {
        attempt == SystemAttempt.Heard || attempt == SystemAttempt.QuietWithActivity -> 0
        isDead(attempt) -> current + 1
        else -> current
    }

    /**
     * The count kept from an earlier run. Capped below [DEAD_AFTER], so each app start gives the
     * system recogniser one more chance (it may have been fixed or installed meanwhile) while a
     * stub is skipped again after a single dead turn.
     */
    fun restoredDeadTurns(saved: Int): Int = saved.coerceIn(0, DEAD_AFTER - 1)

    /**
     * After a greeting the offline model first listens for commands only. True when that turn
     * found no command (silence, an unknown word, or nothing that parses), so Ampli should ask
     * "where to?" and listen again freely for a place name.
     */
    fun offlineNeedsWhereTo(heard: String?, parserLanguage: String): Boolean {
        if (heard.isNullOrBlank()) return true
        return VoiceIntents.parse(heard, parserLanguage) is VoiceIntent.Unknown
    }

    /**
     * Fetch the Hindi offline model by itself: a Hindi-family app language, and no system
     * recogniser that works, and the driver has not removed the model in Settings.
     */
    fun wantHindiModel(parserLanguage: String, systemWorks: Boolean, removedByUser: Boolean): Boolean =
        parserLanguage == "hi" && !systemWorks && !removedByUser
}
