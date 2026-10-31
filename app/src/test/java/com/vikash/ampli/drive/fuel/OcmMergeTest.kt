package com.vikash.ampli.drive.fuel

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * scripts/fetch_fuel.py's Open Charge Map merge, on a fixture (no network): chargers within 50 m
 * of an OpenStreetMap EV station are dropped, closed-licence and removed POIs are skipped.
 * Robolectric only for org.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class OcmMergeTest {

    private fun find(vararg paths: String): File = paths.map(::File).first { it.exists() }

    @Test
    fun mergesAndDedupes() {
        val script = find("../scripts/fetch_fuel.py", "scripts/fetch_fuel.py")
        val fixture = find("src/test/resources/ocm", "app/src/test/resources/ocm")
        val p = ProcessBuilder("python3", script.path, "--merge-check", fixture.path).redirectErrorStream(true).start()
        assertTrue("python3 timed out", p.waitFor(60, TimeUnit.SECONDS))
        val out = p.inputStream.bufferedReader().readText().trim()
        assertEquals(out, 0, p.exitValue())
        val json = JSONObject(out.lines().last())
        assertEquals("usable OCM POIs (closed licence and removed left out)", 6, json.getInt("ocm"))
        assertEquals("POIs skipped for their licence", 1, json.getInt("skipped"))
        assertEquals("new chargers (30 m and 40 m duplicates and an OCM twin dropped)", 3, json.getInt("added"))
        assertEquals(6, json.getInt("stations"))
        assertEquals(5, json.getInt("ev"))
        val names = json.getJSONArray("names").let { a -> (0 until a.length()).map { a.getString(it) } }
        assertTrue("Statiq Connaught Place" in names)
        assertTrue("OSM 80 m away is a different place" in names)
        assertFalse("Same as OSM (30 m away)" in names)
        assertFalse("Closed licence feed" in names)
        assertFalse("Removed charger" in names)
    }
}
