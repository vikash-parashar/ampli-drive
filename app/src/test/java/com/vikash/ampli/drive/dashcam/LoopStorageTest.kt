package com.vikash.ampli.drive.dashcam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopStorageTest {
    private val mb = 1024L * 1024
    private fun clip(i: Int, sizeMb: Long = 100, locked: Boolean = false) = Clip("c$i", sizeMb * mb, i * LoopStorage.SEGMENT_MS, locked)

    @Test
    fun underTheCapNothingGoes() {
        val clips = (0 until 5).map { clip(it) }
        assertTrue(LoopStorage.toDelete(clips, capBytes = 1000 * mb).isEmpty())
    }

    @Test
    fun overTheCapTheOldestLoopClipsGo() {
        val clips = (0 until 12).map { clip(it) }
        val gone = LoopStorage.toDelete(clips, capBytes = 1000 * mb)
        assertEquals(listOf("c0", "c1"), gone.map { it.name })
    }

    @Test
    fun savedClipsSurviveTheLoop() {
        val clips = listOf(clip(0, locked = true), clip(1), clip(2), clip(3, locked = true)) + (4 until 12).map { clip(it) }
        val gone = LoopStorage.toDelete(clips, capBytes = 1000 * mb, lockedCapBytes = 250 * mb)
        assertTrue(gone.none { it.locked })
        // 12 × 100 MB, 2 saved: the loop gives up the two oldest normal clips.
        assertEquals(listOf("c1", "c2"), gone.map { it.name })
    }

    @Test
    fun savedClipsHaveTheirOwnBudget() {
        val clips = (0 until 5).map { clip(it, locked = true) }
        val gone = LoopStorage.toDelete(clips, capBytes = 1000 * mb, lockedCapBytes = 250 * mb)
        assertEquals(listOf("c0", "c1", "c2"), gone.map { it.name })
        val many = (0 until 35).map { clip(it, sizeMb = 1, locked = true) }
        assertEquals(5, LoopStorage.toDelete(many, capBytes = 1000 * mb).size)
    }

    @Test
    fun theActiveSegmentIsNeverDeleted() {
        val clips = (0 until 12).map { clip(it) }
        val gone = LoopStorage.toDelete(clips, capBytes = 100 * mb, activeName = "c0")
        assertFalse(gone.any { it.name == "c0" })
        assertEquals(11, gone.size)
    }

    @Test
    fun lowFreeSpaceDeletesMore() {
        val clips = (0 until 3).map { clip(it) }
        val gone = LoopStorage.toDelete(clips, capBytes = 10_000 * mb, freeBytes = 150 * mb)
        assertEquals(listOf("c0", "c1"), gone.map { it.name })
    }

    @Test
    fun saveLocksTheLastThirtySeconds() {
        val clips = (0 until 3).map { clip(it) }
        // 10 s into c2: c1 holds the 20 s before it.
        assertEquals(listOf("c1", "c2"), LoopStorage.toLock(clips, "c2", clips[2].startMs + 10_000).map { it.name })
        // A minute in: c2 alone is enough.
        assertEquals(listOf("c2"), LoopStorage.toLock(clips, "c2", clips[2].startMs + 60_000).map { it.name })
        assertTrue(LoopStorage.toLock(emptyList(), null, 0).isEmpty())
    }

    @Test
    fun fileNamesRoundTrip() {
        val t = 1_793_480_000_000L // 2026-10-31
        val name = LoopStorage.fileName(t)
        assertTrue(name, name.startsWith("loop_2026") && name.endsWith(".mp4"))
        val c = LoopStorage.parse(name, 5)!!
        assertEquals(t / 1000 * 1000, c.startMs)
        assertFalse(c.locked)
        val saved = LoopStorage.parse(LoopStorage.lockedName(name), 5)!!
        assertTrue(saved.locked)
        assertEquals(c.startMs, saved.startMs)
        assertNull(LoopStorage.parse("notes.txt", 1))
        assertNull(LoopStorage.parse("loop_garbage.mp4", 1))
    }

    @Test
    fun joltSavesButGentleBrakingDoesNot() {
        val d = joltDetector()
        assertFalse(d.onAccel(0, 0f, 2f, 0f, includesGravity = false)) // ~0.2 g
        assertTrue(d.onAccel(100, 0f, 9.5f, 0f, includesGravity = false)) // ~1 g jolt
        assertFalse(d.onAccel(5_000, 0f, 12f, 0f, includesGravity = false)) // cooling down
        assertTrue(d.onAccel(40_000, 0f, 12f, 0f, includesGravity = false))
        val brake = joltDetector()
        assertFalse(brake.onSpeed(0, 50f))
        assertTrue(brake.onSpeed(1_500, 10f))
    }
}
