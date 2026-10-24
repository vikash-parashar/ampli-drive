package com.vikash.ampli.drive.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitAppsTest {
    @Test
    fun sortsTheUnitsApps() {
        assertEquals("Bluetooth", UnitApps.kind("Bluetooth", "com.ts.bt"))
        assertEquals("Bluetooth", UnitApps.kind("BT", "com.example.x"))
        assertEquals("Phone link", UnitApps.kind("phonelink", "com.ts.phonelink"))
        assertEquals("Music", UnitApps.kind("Music", "com.android.music"))
        assertEquals("Radio", UnitApps.kind("Radio", "com.ts.radio"))
        assertEquals("Boot animation", UnitApps.kind("Boot Animation", "com.ts.bootanim"))
        assertEquals("Factory / settings", UnitApps.kind("Factory", "com.ts.factory"))
        assertEquals("Theme / EQ / AUX", UnitApps.kind("EQ", "com.ts.sound"))
        assertNull(UnitApps.kind("Calculator", "com.android.calculator2"))
    }
}
