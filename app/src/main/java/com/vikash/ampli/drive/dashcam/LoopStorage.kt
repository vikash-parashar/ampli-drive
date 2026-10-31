package com.vikash.ampli.drive.dashcam

import com.vikash.ampli.drive.car.CrashDetector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One recorded segment on disk. [locked] clips were saved by a jolt or "save kar". */
data class Clip(val name: String, val bytes: Long, val startMs: Long, val locked: Boolean)

/**
 * Loop-recording storage rules, plain Kotlin (unit-tested; the Android side is [DashcamRecorder]).
 *
 * - Normal segments share [capBytes]; when the folder is over, the oldest normal segments go first.
 * - Saved (locked) clips are never overwritten by the loop. They have their own, smaller budget
 *   ([lockedCapBytes]) and at most [maxLocked] files, so a bumpy road can't fill the unit:
 *   past either limit the oldest saved clip goes.
 * - The segment being written ([activeName]) is never deleted.
 */
object LoopStorage {
    const val SEGMENT_MS = 3 * 60_000L
    const val DEFAULT_CAP_MB = 4_096L
    val CAP_CHOICES_MB = listOf(1_024L, 2_048L, 4_096L, 8_192L, 16_384L)

    /** Saved clips may use a quarter of the cap. */
    fun lockedCap(capBytes: Long): Long = capBytes / 4

    const val MAX_LOCKED = 30

    /** Keep this much free on the volume whatever the cap says. */
    const val MIN_FREE_BYTES = 300L * 1024 * 1024

    /** The clips to delete, oldest first. */
    fun toDelete(
        clips: List<Clip>,
        capBytes: Long,
        lockedCapBytes: Long = lockedCap(capBytes),
        maxLocked: Int = MAX_LOCKED,
        activeName: String? = null,
        freeBytes: Long = Long.MAX_VALUE,
    ): List<Clip> {
        val out = ArrayList<Clip>()
        val locked = clips.filter { it.locked && it.name != activeName }.sortedBy { it.startMs }
        var lockedBytes = locked.sumOf { it.bytes }
        var lockedCount = locked.size
        for (c in locked) {
            if (lockedBytes <= lockedCapBytes && lockedCount <= maxLocked) break
            out += c
            lockedBytes -= c.bytes
            lockedCount--
        }
        val normal = clips.filter { !it.locked && it.name != activeName }.sortedBy { it.startMs }
        val active = clips.firstOrNull { it.name == activeName }?.bytes ?: 0L
        var normalBytes = normal.sumOf { it.bytes } + active
        var free = freeBytes
        for (c in normal) {
            if (normalBytes + lockedBytes <= capBytes && free >= MIN_FREE_BYTES) break
            out += c
            normalBytes -= c.bytes
            if (free < Long.MAX_VALUE / 2) free += c.bytes
        }
        return out
    }

    /**
     * The clips a save locks: the one being written and the one before it, which together hold at
     * least the last [SAVE_BACK_MS] (a save early in a segment needs the previous one too).
     */
    fun toLock(clips: List<Clip>, activeName: String?, nowMs: Long): List<Clip> {
        val sorted = clips.sortedBy { it.startMs }
        val active = sorted.firstOrNull { it.name == activeName } ?: sorted.lastOrNull() ?: return emptyList()
        val out = arrayListOf(active)
        if (nowMs - active.startMs < SAVE_BACK_MS) {
            sorted.lastOrNull { it.startMs < active.startMs }?.let { out.add(0, it) }
        }
        return out.filter { !it.locked }
    }

    const val SAVE_BACK_MS = 30_000L

    private const val STAMP = "yyyyMMdd_HHmmss"
    const val PREFIX = "loop_"
    const val LOCKED_PREFIX = "saved_"
    const val EXT = ".mp4"

    /** "loop_20261031_211304.mp4" (UTC, so a time-zone change never reorders the loop). */
    fun fileName(startMs: Long, locked: Boolean = false): String =
        (if (locked) LOCKED_PREFIX else PREFIX) + format().format(Date(startMs)) + EXT

    /** The clip a file name describes, or null for any other file in the folder. */
    fun parse(name: String, bytes: Long): Clip? {
        val locked = name.startsWith(LOCKED_PREFIX)
        if (!locked && !name.startsWith(PREFIX)) return null
        if (!name.endsWith(EXT)) return null
        val stamp = name.removePrefix(if (locked) LOCKED_PREFIX else PREFIX).removeSuffix(EXT)
        val t = try {
            format().parse(stamp)?.time
        } catch (_: Exception) {
            null
        } ?: return null
        return Clip(name, bytes, t, locked)
    }

    /** The saved name of a loop clip. */
    fun lockedName(name: String): String = if (name.startsWith(PREFIX)) LOCKED_PREFIX + name.removePrefix(PREFIX) else name

    private fun format() = SimpleDateFormat(STAMP, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    /** About how long [bytes] of video lasts at [bitRate] bits per second. */
    fun hoursFor(bytes: Long, bitRate: Int): Double = if (bitRate <= 0) 0.0 else bytes * 8.0 / bitRate / 3600.0
}

/**
 * A hard brake or a jolt on the unit's G-sensor: a lighter threshold than a crash ([CrashDetector]
 * at 4 g). About 0.9 g after gravity is removed, or the GPS speed falling from 35 to 12 km/h within
 * two seconds. Quiet for 30 s afterwards, so one pothole saves one clip.
 */
fun joltDetector(): CrashDetector = CrashDetector(impactG = 0.9, fromKmh = 35f, toKmh = 12f, windowMs = 2_000L, cooldownMs = 30_000L)
