package com.vikash.ampli.drive.voice

/**
 * Which offline speech models a unit should have, how often a failed fetch is retried, and how far
 * along "Getting Ampli ready… 40%" is. Plain Kotlin, unit-tested (VoiceModelPlanTest); the Android
 * side is [VoiceModels] and [WakeModel].
 *
 * Drivers never download voice packages by hand: Play installs carry both models in the
 * install-time asset pack (unpacked on first start), and sideloaded test APKs fetch them by
 * themselves on any network.
 */
object VoiceModelPlan {
    /** The models to keep on this unit. */
    data class Want(val english: Boolean, val hindi: Boolean)

    /**
     * English (wake word and English commands) always, unless the driver removed it. Hindi for a
     * Hindi-family parser language, and also while the app language is unset ([savedLanguage]
     * empty: following the system), since Car Hub's drivers mostly speak Haryanvi. A model the
     * driver removed in Settings stays removed.
     */
    fun wanted(savedLanguage: String, parserLanguage: String, englishRemoved: Boolean, hindiRemoved: Boolean): Want =
        Want(
            english = !englishRemoved,
            hindi = !hindiRemoved && (savedLanguage.isBlank() || parserLanguage == "hi"),
        )

    /** Longest wait before an automatic retry (the old fixed back-off). */
    const val MAX_RETRY_MS = 6 * 60 * 60_000L

    /**
     * Wait before an automatic retry after [failures] failed attempts in a row. Short at first, so
     * a first start on a flaky hotspot does not wait hours; then up to [MAX_RETRY_MS] so a model
     * that will not download or load is not fetched on every return home.
     */
    fun retryDelayMs(failures: Int): Long = when {
        failures <= 0 -> 0L
        failures == 1 -> 30_000L
        failures == 2 -> 2 * 60_000L
        failures == 3 -> 10 * 60_000L
        failures == 4 -> 30 * 60_000L
        failures == 5 -> 2 * 60 * 60_000L
        else -> MAX_RETRY_MS
    }

    /** A network coming back skips the back-off for the first few failures (not for ever). */
    const val NETWORK_RETRIES = 6

    fun retryOnNetwork(failures: Int): Boolean = failures in 1 until NETWORK_RETRIES

    /** True while an automatic retry should still wait. [failedAt] 0 = never failed. */
    fun backingOff(failures: Int, failedAt: Long, now: Long): Boolean {
        if (failedAt <= 0L) return false
        val age = now - failedAt
        // A clock set back (head units without RTC) counts as "just failed", within the max wait.
        return age < retryDelayMs(failures.coerceAtLeast(1)) && age > -MAX_RETRY_MS
    }

    /** One model's phase, without the Android types. */
    enum class Phase { Missing, Downloading, Unpacking, Ready, Failed }

    /** Share of a network fetch spent downloading; the rest is unpacking. */
    private const val DOWNLOAD_SHARE = 85

    /**
     * How far one model is, 0..100. [fromAssets]: unpacking the copy in the Play asset pack
     * (no download, so unpacking is the whole job).
     */
    fun modelPercent(phase: Phase, downloadPercent: Int, unpackPercent: Int, fromAssets: Boolean): Int = when (phase) {
        Phase.Ready -> 100
        Phase.Missing, Phase.Failed -> 0
        Phase.Downloading -> downloadPercent.coerceIn(0, 100) * DOWNLOAD_SHARE / 100
        Phase.Unpacking -> if (fromAssets) {
            unpackPercent.coerceIn(0, 99)
        } else {
            DOWNLOAD_SHARE + unpackPercent.coerceIn(0, 100) * (100 - DOWNLOAD_SHARE) / 100
        }
    }.coerceIn(0, 100)

    /** One wanted model for the Home strip: its size (weight) and progress. */
    data class Part(val bytes: Long, val phase: Phase, val percent: Int)

    /**
     * The Home strip's percent, or null when it should be hidden: shown only while a wanted model
     * is downloading or unpacking; gone once all are ready (or nothing is happening).
     */
    fun stripPercent(parts: List<Part>): Int? {
        if (parts.none { it.phase == Phase.Downloading || it.phase == Phase.Unpacking }) return null
        val total = parts.sumOf { it.bytes.coerceAtLeast(1L) }
        val done = parts.sumOf { it.bytes.coerceAtLeast(1L) * it.percent.coerceIn(0, 100) }
        return (done / total).toInt().coerceIn(0, 99)
    }
}
