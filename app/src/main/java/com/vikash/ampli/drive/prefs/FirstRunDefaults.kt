package com.vikash.ampli.drive.prefs

/**
 * What a fresh install starts with. Car Hub ships for Haryana's head units, so it talks
 * Haryanvi out of the box: voice style "Haryanvi desi" and Haryanvi navigation prompts on.
 *
 * Every default here only fills a setting the driver never set: a stored choice always wins,
 * so somebody who picked Standard keeps Standard after an update. Plain Kotlin, unit-tested.
 */
object FirstRunDefaults {
    val VOICE_STYLE: VoiceStyle = VoiceStyle.Haryanvi
    const val NAV_PROMPTS: Boolean = true

    /**
     * App language for a fresh install. Android has no Haryanvi UI locale with full coverage,
     * so the screen text uses Hindi; the driver can switch to "System default" or any other
     * language in Settings › Language.
     */
    const val LANGUAGE: String = "hi"

    /** The voice style to use: the stored key, or the Haryanvi default when none is stored. */
    fun voiceStyle(stored: String?): VoiceStyle = if (stored == null) VOICE_STYLE else VoiceStyle.from(stored)

    /** Haryanvi navigation prompts: the stored switch, or on when it was never touched. */
    fun navPrompts(stored: Boolean?): Boolean = stored ?: NAV_PROMPTS

    /**
     * The language to switch to on first start, or null to leave it alone. Only a fresh install
     * (no earlier run of the app) with no stored language choice gets Hindi; an updated app that
     * was following the system language keeps doing so.
     */
    fun language(stored: String?, freshInstall: Boolean): String? =
        if (stored == null && freshInstall) LANGUAGE else null
}
