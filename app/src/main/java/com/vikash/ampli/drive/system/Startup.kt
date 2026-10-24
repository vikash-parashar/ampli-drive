package com.vikash.ampli.drive.system

import android.content.Context
import android.os.Process
import android.os.SystemClock

/**
 * Cold-start time: from the process start to the first Home frame, shown in Settings › About ›
 * Diagnostics. When the process was started earlier for something else (the drive monitor at
 * boot, a broadcast) the activity start is measured instead, so the number stays honest.
 */
object StartupTiming {
    /** The activity was created this long after the process started: then it was a real cold start. */
    const val COLD_WINDOW_MS = 5_000L

    @Volatile private var activityCreatedAt = 0L

    /** True once Home has drawn its first frame in this process. */
    @Volatile var homeShown = false
        private set

    enum class Kind { Cold, Warm }

    data class Result(val kind: Kind, val ms: Long)

    /** Last measurement this process (null before the first Home frame). */
    @Volatile var last: Result? = null
        private set

    /** MainActivity.onCreate. */
    fun onActivityCreated() {
        if (activityCreatedAt == 0L) activityCreatedAt = SystemClock.uptimeMillis()
    }

    /** Pure: which number to report. */
    fun measure(processStart: Long, activityCreated: Long, firstFrame: Long): Result =
        if (activityCreated == 0L || activityCreated - processStart <= COLD_WINDOW_MS) {
            Result(Kind.Cold, (firstFrame - processStart).coerceAtLeast(0L))
        } else {
            Result(Kind.Warm, (firstFrame - activityCreated).coerceAtLeast(0L))
        }

    /** The first Home frame is on screen. Only the first call per process counts. */
    fun onFirstHomeFrame(context: Context) {
        if (homeShown) return
        homeShown = true
        val r = measure(Process.getStartUptimeMillis(), activityCreatedAt, SystemClock.uptimeMillis())
        last = r
        try {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putLong(if (r.kind == Kind.Cold) KEY_COLD else KEY_WARM, r.ms).apply()
        } catch (_: Exception) {
        }
    }

    /** Last cold and warm start in ms from earlier runs too (0 = not measured yet). */
    fun saved(context: Context): Pair<Long, Long> {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return sp.getLong(KEY_COLD, 0L) to sp.getLong(KEY_WARM, 0L)
    }

    private const val FILE = "startup"
    private const val KEY_COLD = "cold_ms"
    private const val KEY_WARM = "warm_ms"
}

/**
 * Caches that can be dropped when Android asks for memory back (DriveApp.onTrimMemory): app
 * icons, the parking QR, and MapLibre's tile and glyph caches through the live map view.
 */
object MemoryTrim {
    private val listeners = LinkedHashSet<() -> Unit>()

    /** How many times caches were released this run (Diagnostics). */
    @Volatile var trims = 0
        private set

    fun add(listener: () -> Unit) = synchronized(listeners) { listeners += listener }

    fun remove(listener: () -> Unit) = synchronized(listeners) { listeners -= listener }

    /** True for TRIM_MEMORY_UI_HIDDEN (20) and every level above it. */
    fun shouldRelease(level: Int): Boolean = level >= UI_HIDDEN

    fun onTrimMemory(level: Int) {
        if (!shouldRelease(level)) return
        trims++
        InstalledApps.clearCache()
        com.vikash.ampli.drive.car.ParkingQr.clear()
        val copy = synchronized(listeners) { listeners.toList() }
        for (l in copy) {
            try {
                l()
            } catch (_: Exception) {
            }
        }
    }

    /** ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, kept here so the check is testable on the JVM. */
    const val UI_HIDDEN = 20
}
