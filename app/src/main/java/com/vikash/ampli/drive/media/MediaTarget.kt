package com.vikash.ampli.drive.media

/** One media session as the voice commands see it. */
data class SessionInfo(val packageName: String, val playing: Boolean, val bluetooth: Boolean = false)

/**
 * Which player a voice "play / pause / next / previous / stop" goes to. Pure, unit-tested.
 *
 * Order: Ampli MP3 Player when it is the one playing, else whatever else is playing (the head
 * unit's Bluetooth music app counts, it forwards to the phone), else the phone over Bluetooth
 * when it was the last thing playing or nothing else is around, else any session, else (for
 * "play") open Ampli MP3 in its Car mode, else a plain media key that Android routes itself.
 */
object MediaTarget {

    /** The phone over Bluetooth, as [lastSource] remembers it. */
    const val PHONE = "phone"

    sealed interface Target {
        data class Session(val packageName: String) : Target

        /** Media keys that the unit's Bluetooth stack forwards to the phone (AVRCP). */
        data object PhoneKeys : Target

        /** Ampli MP3 Player's Car mode with autoplay: "play" with nothing to resume. */
        data object OpenAmpli : Target

        /** A media key to whoever Android thinks should get it. */
        data object MediaKey : Target
    }

    enum class Action { Play, Pause, Next, Previous }

    /**
     * @param sessions visible sessions (empty without notification access).
     * @param musicActive AudioManager.isMusicActive: something is audible even if no session shows.
     * @param phoneConnected a phone is connected over Bluetooth.
     * @param lastSource what was playing the last time a command looked: a package name, [PHONE], or null.
     */
    fun pick(
        sessions: List<SessionInfo>,
        action: Action,
        ampliPackage: String,
        ampliInstalled: Boolean,
        phoneConnected: Boolean,
        musicActive: Boolean,
        lastSource: String? = null,
    ): Target {
        sessions.firstOrNull { it.playing && it.packageName == ampliPackage }?.let { return Target.Session(it.packageName) }
        sessions.firstOrNull { it.playing }?.let { return Target.Session(it.packageName) }
        // Audible but no session to see: phone audio straight from the Bluetooth stack (or no
        // notification access). Keys are the only way in.
        if (musicActive) return if (phoneConnected) Target.PhoneKeys else Target.MediaKey

        // Nothing playing: go back to what played last.
        if (lastSource == PHONE && phoneConnected) {
            return sessions.firstOrNull { it.bluetooth }?.let { Target.Session(it.packageName) } ?: Target.PhoneKeys
        }
        if (lastSource != null) sessions.firstOrNull { it.packageName == lastSource }?.let { return Target.Session(it.packageName) }
        sessions.firstOrNull { it.packageName == ampliPackage }?.let { return Target.Session(it.packageName) }
        if (phoneConnected) {
            // A paused Bluetooth-app session is the phone too; its controls work better than keys.
            sessions.firstOrNull { it.bluetooth }?.let { return Target.Session(it.packageName) }
            return Target.PhoneKeys
        }
        sessions.firstOrNull()?.let { return Target.Session(it.packageName) }
        if (action == Action.Play && ampliInstalled) return Target.OpenAmpli
        return Target.MediaKey
    }

    /** What to remember as [lastSource] after looking at what plays now. */
    fun playingSource(sessions: List<SessionInfo>, musicActive: Boolean, phoneConnected: Boolean): String? =
        sessions.firstOrNull { it.playing }?.let { if (it.bluetooth) PHONE else it.packageName }
            ?: if (musicActive && phoneConnected) PHONE else null
}
