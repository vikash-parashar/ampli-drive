package com.vikash.ampli.drive.nav

/** One thing to say: [now] = "abhi" (the turn is close), else "in [distanceM]". */
data class NavPrompt(val turn: ParsedTurn, val now: Boolean, val distanceM: Int?)

/**
 * Decides when a manoeuvre is spoken. Google Maps updates its notification every few metres,
 * so without this Ampli would talk non-stop. Per manoeuvre: once at about [FAR_M] (with the
 * distance), once more at about [NEAR_M] ("abhi"). Without a distance it is said once. Plain
 * Kotlin, unit-tested.
 */
class NavPromptPlanner {
    private var key: String? = null
    private var lastDistance: Int? = null
    private var saidFar = false
    private var saidNear = false

    fun onTurn(turn: ParsedTurn): NavPrompt? {
        val d = turn.distanceM
        val prev = lastDistance
        // Same instruction text but the distance jumped up: Maps moved on to a new, identical
        // instruction ("Turn left" twice in a row).
        val jumped = d != null && prev != null && d > prev + NEW_TURN_JUMP_M
        if (turn.key != key || jumped) {
            key = turn.key
            saidFar = false
            saidNear = false
        }
        lastDistance = d
        return when {
            turn.maneuver == Maneuver.Continue -> once(turn, d, onlyIfFar = true)
            turn.maneuver == Maneuver.Arrive -> if (d == null || d <= ARRIVE_M) once(turn, d) else null
            d == null -> once(turn, null)
            d <= NEAR_M -> if (saidNear) null else {
                saidNear = true
                saidFar = true
                NavPrompt(turn, now = true, distanceM = d)
            }
            d <= FAR_M -> if (saidFar) null else {
                saidFar = true
                NavPrompt(turn, now = false, distanceM = roundDistance(d))
            }
            else -> null
        }
    }

    /** Navigation ended (notification removed). */
    fun reset() {
        key = null
        lastDistance = null
        saidFar = false
        saidNear = false
    }

    private fun once(turn: ParsedTurn, d: Int?, onlyIfFar: Boolean = false): NavPrompt? {
        if (saidFar || saidNear) return null
        if (onlyIfFar && d != null && d < CONTINUE_MIN_M) return null
        saidFar = true
        saidNear = true
        return NavPrompt(turn, now = false, distanceM = d?.let { roundDistance(it) })
    }

    companion object {
        const val FAR_M = 600
        const val NEAR_M = 120
        const val ARRIVE_M = 150

        /** "Continue for 400 m" right before a turn isn't worth a sentence. */
        const val CONTINUE_MIN_M = 1000
        const val NEW_TURN_JUMP_M = 250

        /** 437 → 450, 1260 → 1300: a spoken distance needn't be exact. */
        fun roundDistance(m: Int): Int = when {
            m < 1000 -> ((m + 25) / 50) * 50
            else -> ((m + 50) / 100) * 100
        }
    }
}

/**
 * "Slow down" while navigating: speaks when the speed stays above the known limit (+[MARGIN_KMH])
 * for [HOLD_MS], then waits [COOLDOWN_MS] before saying it again. Plain Kotlin, unit-tested.
 */
class OverLimitNag {
    private var overSinceMs = -1L
    private var lastSaidMs = -1L

    /** True when the slow-down line should be spoken now. */
    fun onSpeed(nowMs: Long, speedKmh: Float?, limitKmh: Int?): Boolean {
        if (speedKmh == null || limitKmh == null || limitKmh <= 0 || speedKmh <= limitKmh + MARGIN_KMH) {
            overSinceMs = -1L
            return false
        }
        if (overSinceMs < 0) overSinceMs = nowMs
        if (nowMs - overSinceMs < HOLD_MS) return false
        if (lastSaidMs >= 0 && nowMs - lastSaidMs < COOLDOWN_MS) return false
        lastSaidMs = nowMs
        return true
    }

    companion object {
        const val MARGIN_KMH = 3
        const val HOLD_MS = 3_000L
        const val COOLDOWN_MS = 60_000L
    }
}
