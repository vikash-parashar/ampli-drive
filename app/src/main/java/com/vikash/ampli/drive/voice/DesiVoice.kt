package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.prefs.VoiceStyle
import java.util.Locale

/**
 * Speaks [HaryanviLines] when Settings › Voice style is "Haryanvi desi". Hindi voice installed:
 * the Devanagari line with hi-IN. No Hindi voice: the Latin line with en-IN, which reads it
 * well enough. Safety events never get here (forEvent returns null for them).
 */
class DesiVoice(private val app: DriveApp) {
    private val picker = LinePicker()

    val enabled: Boolean get() = app.prefs.voiceStyle.value == VoiceStyle.Haryanvi

    /** A line to speak: the text and the app-language tag to speak it with. */
    data class Spoken(val text: String, val language: String)

    /** The desi line for [event] with [args] filled in, or null when the style is off. */
    suspend fun line(event: HaryanviLines.Event, vararg args: Any): Spoken? {
        if (!enabled) return null
        val line = picker.pick(event) ?: return null
        val hindi = app.speaker.hasVoice(HINDI)
        val template = if (hindi) line.deva else line.roman
        val text = try {
            String.format(Locale.US, template, *args)
        } catch (_: Exception) {
            template
        }
        return Spoken(text, if (hindi) "hi" else "en-IN")
    }

    /** Speaks the desi line; returns false when the style is off so the caller says the standard line. */
    suspend fun say(event: HaryanviLines.Event, vararg args: Any): Boolean {
        val spoken = line(event, *args) ?: return false
        app.speaker.say(spoken.text, spoken.language)
        return true
    }

    private companion object {
        val HINDI: Locale = Locale.forLanguageTag("hi-IN")
    }
}
