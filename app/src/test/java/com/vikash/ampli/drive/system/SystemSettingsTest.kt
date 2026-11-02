package com.vikash.ampli.drive.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Car settings" detection and the Cleaner's pure rules. */
class SystemSettingsTest {

    private fun c(pkg: String, label: String, system: Boolean = true) = LauncherCandidate(pkg, "$pkg.Main", label, system)

    @Test
    fun androidAndGoogleSettingsAreNeverCarSettings() {
        assertNull(CarSettingsMatch.pick(listOf(c("com.android.settings", "Settings"), c("com.google.android.gms", "Google Settings"))))
    }

    @Test
    fun knownVendorPackageWins() {
        val picked = CarSettingsMatch.pick(listOf(c("com.syu.otherapp", "Vehicle info"), c("com.syu.carsettings", "Car")))
        assertEquals("com.syu.carsettings", picked?.packageName)
    }

    @Test
    fun carWordsInTheLabelOrPackage() {
        assertEquals("com.acme.unit", CarSettingsMatch.pick(listOf(c("com.acme.unit", "汽车设置")))?.packageName)
        assertEquals("com.acme.vehiclesetting", CarSettingsMatch.pick(listOf(c("com.acme.vehiclesetting", "Factory")))?.packageName)
        assertEquals("com.acme.x", CarSettingsMatch.pick(listOf(c("com.acme.x", "Car Settings")))?.packageName)
    }

    @Test
    fun userInstalledAppsDoNotCount() {
        assertNull(CarSettingsMatch.pick(listOf(c("com.random.app", "Car Settings Pro", system = false))))
    }

    @Test
    fun vendorSettingsIsTheLastResort() {
        val picked = CarSettingsMatch.pick(listOf(c("com.acme.settings", "Settings"), c("com.acme.car", "Vehicle")))
        assertEquals("com.acme.car", picked?.packageName)
        assertEquals("com.acme.settings", CarSettingsMatch.pick(listOf(c("com.acme.settings", "Settings")))?.packageName)
    }

    @Test
    fun cleanerFlagsStaleAndHeavyUserApps() {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        val stale = CleanerApp("a", "a.M", "A", 10L, true, now - 9 * day, system = false)
        val fresh = CleanerApp("b", "b.M", "B", 10L, true, now - day, system = false)
        val heavy = CleanerApp("c", "c.M", "C", AppCleaner.HEAVY_BYTES, true, now, system = false)
        val system = CleanerApp("d", "d.M", "D", AppCleaner.HEAVY_BYTES * 2, true, now - 30 * day, system = true)
        assertTrue(AppCleaner.worthCleaning(stale, now))
        assertFalse(AppCleaner.worthCleaning(fresh, now))
        assertTrue(AppCleaner.worthCleaning(heavy, now))
        assertFalse(AppCleaner.worthCleaning(system, now))
        assertEquals(9L, AppCleaner.daysSince(now - 9 * day, now))
        assertNull(AppCleaner.daysSince(null, now))
    }
}
