package com.vikash.ampli.drive.bhajan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek

class BhajanPlanTest {
    private val catalog = BhajanPlan.parse(File("src/main/assets/bhajan/manifest.json").readText())
    private val all: (Bhajan) -> Boolean = { true }
    private val on = BhajanSettings(enabled = true)

    private fun decide(
        s: BhajanSettings = on,
        day: DayOfWeek = DayOfWeek.TUESDAY,
        hour: Int = 8,
        last: Long = -1,
        pro: Boolean = true,
        exists: (Bhajan) -> Boolean = all,
    ) = BhajanPlan.decide(s, catalog, day, hour, epochDay = 100, lastPlayedDay = last, isPro = pro, exists = exists)

    @Test
    fun manifestIsShippedAndOriginal() {
        assertTrue(catalog.size >= 3)
        assertTrue(catalog.all { it.original && it.file == "bhajan/${it.id}.mp3" })
        assertEquals(1, catalog.count { it.free })
        assertEquals(catalog.size, catalog.map { it.id }.toSet().size)
        // No audio is bundled yet: every bhajan is skipped quietly until its file is added.
        for (b in catalog) assertFalse(File("src/main/assets/${b.file}").exists() && File("src/main/assets/${b.file}").length() == 0L)
    }

    @Test
    fun offByDefault() {
        assertFalse(BhajanSettings().enabled)
        assertEquals(BhajanDecision.Skip(BhajanDecision.Reason.Off), decide(s = BhajanSettings()))
    }

    @Test
    fun dayOfWeekPicksItsBhajan() {
        assertEquals("hanuman_short", (decide(day = DayOfWeek.TUESDAY) as BhajanDecision.Play).bhajan.id)
        assertEquals("shanidev_aarti", (decide(day = DayOfWeek.SATURDAY) as BhajanDecision.Play).bhajan.id)
        // Monday isn't chosen by default.
        assertEquals(BhajanDecision.Skip(BhajanDecision.Reason.NotToday), decide(day = DayOfWeek.MONDAY))
        // Every morning: any day, before noon.
        val morning = on.copy(everyMorning = true)
        assertEquals("ram_naam_dhun", (decide(s = morning, day = DayOfWeek.MONDAY, hour = 7) as BhajanDecision.Play).bhajan.id)
        assertEquals(BhajanDecision.Skip(BhajanDecision.Reason.NotToday), decide(s = morning, day = DayOfWeek.MONDAY, hour = 18))
    }

    @Test
    fun driversPickPerDayWins() {
        val s = on.copy(choice = mapOf(DayOfWeek.TUESDAY to "ram_naam_dhun"))
        assertEquals("ram_naam_dhun", (decide(s = s) as BhajanDecision.Play).bhajan.id)
        // An id no longer in the manifest falls back to the day's default.
        val gone = on.copy(choice = mapOf(DayOfWeek.TUESDAY to "removed"))
        assertEquals("hanuman_short", (decide(s = gone) as BhajanDecision.Play).bhajan.id)
    }

    @Test
    fun freeUsersGetTheFreeBhajan() {
        val play = decide(day = DayOfWeek.SATURDAY, pro = false) as BhajanDecision.Play
        assertEquals("hanuman_short", play.bhajan.id)
        assertTrue(BhajanPlan.locked(catalog.first { it.id == "shanidev_aarti" }, isPro = false))
        assertFalse(BhajanPlan.locked(catalog.first { it.id == "shanidev_aarti" }, isPro = true))
    }

    @Test
    fun oncePerDayAndMissingFilesAreSkipped() {
        assertEquals(BhajanDecision.Skip(BhajanDecision.Reason.AlreadyPlayed), decide(last = 100))
        assertTrue(decide(last = 99) is BhajanDecision.Play)
        assertEquals(BhajanDecision.Skip(BhajanDecision.Reason.FileMissing), decide(exists = { false }))
        assertEquals(
            BhajanDecision.Skip(BhajanDecision.Reason.NoBhajans),
            BhajanPlan.decide(on, emptyList(), DayOfWeek.TUESDAY, 8, 100, -1, true, all),
        )
    }

    @Test
    fun volumeIsClamped() {
        assertEquals(0.1f, (decide(s = on.copy(volume = 0f)) as BhajanDecision.Play).volume)
        assertEquals(0.55f, (decide() as BhajanDecision.Play).volume)
    }

    @Test
    fun storageRoundTrips() {
        val days = setOf(DayOfWeek.SATURDAY, DayOfWeek.TUESDAY)
        assertEquals("TUE,SAT", BhajanPlan.encodeDays(days))
        assertEquals(days, BhajanPlan.decodeDays("TUE,SAT"))
        assertEquals(emptySet<DayOfWeek>(), BhajanPlan.decodeDays(""))
        assertNull(BhajanPlan.decodeDays(null))
        val choice = mapOf(DayOfWeek.TUESDAY to "hanuman_short", DayOfWeek.SATURDAY to "shanidev_aarti")
        assertEquals(choice, BhajanPlan.decodeChoice(BhajanPlan.encodeChoice(choice)))
        assertEquals(DayOfWeek.MONDAY, BhajanPlan.dayOf("monday"))
        assertNull(BhajanPlan.dayOf("xx"))
    }

    @Test
    fun badOrUnoriginalEntriesAreIgnored() {
        val json = """{"bhajans":[{"id":"ok_one","original":true,"free":true},{"id":"Bad Id!"},{"id":"copied","original":false},{"id":"x","file":"../../secret","original":true}]}"""
        val list = BhajanPlan.parse(json)
        assertEquals(listOf("ok_one", "copied", "x"), list.map { it.id })
        assertEquals("bhajan/x.mp3", list.last().file)
        assertEquals("ok_one", BhajanPlan.pickFor(on, list, DayOfWeek.TUESDAY, isPro = true)!!.id)
        assertTrue(BhajanPlan.parse("not json").isEmpty())
    }
}
