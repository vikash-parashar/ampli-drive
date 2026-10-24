package com.vikash.ampli.drive.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicDefaultTest {
    private val ampli = AmpliMusic.PACKAGE

    @Test
    fun autoPickedSpotifyBecomesAmpliOnceInstalled() {
        assertEquals(ampli, MusicDefault.migrate("com.spotify.music", ampliInstalled = true, alreadyMigrated = false, pickedSinceChange = false))
        assertEquals(ampli, MusicDefault.migrate(null, ampliInstalled = true, alreadyMigrated = false, pickedSinceChange = false))
    }

    @Test
    fun nothingChangesWithoutAmpliOrAfterTheSwitch() {
        assertNull(MusicDefault.migrate("com.spotify.music", ampliInstalled = false, alreadyMigrated = false, pickedSinceChange = false))
        assertNull(MusicDefault.migrate("com.spotify.music", ampliInstalled = true, alreadyMigrated = true, pickedSinceChange = false))
        assertNull(MusicDefault.migrate(ampli, ampliInstalled = true, alreadyMigrated = false, pickedSinceChange = false))
    }

    @Test
    fun aChoiceMadeAfterThisVersionIsKept() {
        assertNull(MusicDefault.migrate("com.spotify.music", ampliInstalled = true, alreadyMigrated = false, pickedSinceChange = true))
    }

    @Test
    fun migrationWaitsForAmpliToBeInstalled() {
        assertFalse(MusicDefault.migrationDone(ampliInstalled = false, alreadyMigrated = false, pickedSinceChange = false))
        assertTrue(MusicDefault.migrationDone(ampliInstalled = true, alreadyMigrated = false, pickedSinceChange = false))
        assertTrue(MusicDefault.migrationDone(ampliInstalled = false, alreadyMigrated = false, pickedSinceChange = true))
    }

    @Test
    fun musicButtonTarget() {
        assertEquals(MusicDefault.Target.Picked, MusicDefault.target("com.spotify.music", ampliInstalled = true))
        assertEquals(MusicDefault.Target.Ampli, MusicDefault.target(ampli, ampliInstalled = true))
        assertEquals(MusicDefault.Target.Ampli, MusicDefault.target(null, ampliInstalled = true))
        // Never Spotify or another guess: the install sheet.
        assertEquals(MusicDefault.Target.GetAmpli, MusicDefault.target(null, ampliInstalled = false))
        assertEquals(MusicDefault.Target.GetAmpli, MusicDefault.target(ampli, ampliInstalled = false))
    }
}
