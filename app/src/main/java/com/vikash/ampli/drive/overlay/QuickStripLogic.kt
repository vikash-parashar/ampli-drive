package com.vikash.ampli.drive.overlay

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Geometry and gesture rules for the top-edge touch strip that opens quick controls over other
 * apps (QuickStrip). Pure, so the thresholds are unit-tested.
 *
 * The strip spans the middle half of the screen width: vendor status bars on head units keep
 * their own buttons (back, home, volume, clock) in the left and right corners, which stay free.
 */
object QuickStripLogic {
    /** Strip height. Thin, because Android cannot pass a touch that lands on it to the app below. */
    const val STRIP_DP = 14f

    /** A downward drag longer than this opens the panel (same as Home's own pull-down). */
    const val OPEN_DP = 48f

    /** Movement below this is still a tap / jitter, not a direction. */
    const val SLOP_DP = 10f

    /** Share of the width the strip covers, centred. */
    const val WIDTH_FRACTION = 0.5f

    data class Strip(val x: Int, val width: Int, val height: Int) {
        fun contains(px: Float, py: Float): Boolean = px >= x && px < x + width && py >= 0 && py < height
    }

    /** Where the strip sits for a screen [screenWidthPx] wide at [density] (px per dp). */
    fun strip(screenWidthPx: Int, density: Float): Strip {
        val w = (screenWidthPx * WIDTH_FRACTION).roundToInt().coerceAtLeast(1)
        val x = ((screenWidthPx - w) / 2).coerceAtLeast(0)
        val h = (STRIP_DP * density).roundToInt().coerceAtLeast(1)
        return Strip(x, w, h)
    }

    enum class Swipe { Pending, Open, Cancel }

    /**
     * What a drag of [dx], [dy] pixels (from where the finger went down; y grows downwards) means:
     * [Swipe.Open] once it is mostly downward and longer than [OPEN_DP]; [Swipe.Cancel] as soon as
     * it is clearly sideways or upward; otherwise [Swipe.Pending].
     */
    fun classify(dx: Float, dy: Float, density: Float): Swipe {
        val slop = SLOP_DP * density
        return when {
            dy > OPEN_DP * density && dy >= abs(dx) * 1.5f -> Swipe.Open
            abs(dx) > slop && abs(dx) > dy -> Swipe.Cancel
            dy < -slop -> Swipe.Cancel
            else -> Swipe.Pending
        }
    }

    /** An upward swipe on the open panel longer than [OPEN_DP] closes it. */
    fun isCloseSwipe(dy: Float, density: Float): Boolean = dy < -OPEN_DP * density

    /** One finger's gesture on the strip; [move] says [Swipe.Open] exactly once. */
    class Tracker(private val density: Float) {
        private var downX = 0f
        private var downY = 0f
        private var state = Swipe.Cancel

        fun down(x: Float, y: Float) {
            downX = x
            downY = y
            state = Swipe.Pending
        }

        /** Returns [Swipe.Open] on the move that crosses the threshold, else Pending/Cancel. */
        fun move(x: Float, y: Float): Swipe {
            if (state != Swipe.Pending) return if (state == Swipe.Open) Swipe.Cancel else state
            val s = classify(x - downX, y - downY, density)
            if (s != Swipe.Pending) state = s
            return s
        }

        fun up() {
            state = Swipe.Cancel
        }
    }

    /**
     * Whether the strip should be on screen: the switch is on, overlays are allowed, no panel is
     * open, no crash countdown / SOS is up, and Car Hub's Home (which has its own pull-down) is
     * not in front.
     */
    fun visible(enabled: Boolean, canOverlay: Boolean, panelOpen: Boolean, crash: Boolean, hubInFront: Boolean, hubScreenBlocks: Boolean): Boolean =
        enabled && canOverlay && !panelOpen && !crash && !(hubInFront && hubScreenBlocks)
}
