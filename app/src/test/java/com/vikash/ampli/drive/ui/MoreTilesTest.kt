package com.vikash.ampli.drive.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The More sheet: the twelve tiles of the design, in its 4 x 3 order. */
class MoreTilesTest {

    @Test
    fun twelveTilesInDesignOrder() {
        assertEquals(
            listOf(
                MoreTile.Fastag, MoreTile.Fuel, MoreTile.Radar, MoreTile.Papers,
                MoreTile.Trips, MoreTile.Expenses, MoreTile.CarHealth, MoreTile.Tyres,
                MoreTile.Phone, MoreTile.PhoneLink, MoreTile.Apps, MoreTile.Settings,
            ),
            MoreTile.entries,
        )
        assertEquals(4, MoreTile.COLUMNS)
        assertEquals(3, MoreTile.entries.chunked(MoreTile.COLUMNS).size)
    }

    /** Nothing left Home without a way back: every old dock and Car hub destination is a tile. */
    @Test
    fun everyFormerHomeDestinationHasATile() {
        val former = mapOf(
            "FASTag chip" to MoreTile.Fastag, "Car hub › Fuel" to MoreTile.Fuel, "Car hub › Radar" to MoreTile.Radar,
            "Car hub › Papers" to MoreTile.Papers, "Car hub › Trips" to MoreTile.Trips, "Car hub › Expenses" to MoreTile.Expenses,
            "Car hub › OBD" to MoreTile.CarHealth, "Car hub › Tyres" to MoreTile.Tyres, "Dock › Phone" to MoreTile.Phone,
            "Dock › Phone link" to MoreTile.PhoneLink, "Dock › Apps" to MoreTile.Apps, "Dock › Settings" to MoreTile.Settings,
        )
        assertEquals(MoreTile.entries.toSet(), former.values.toSet())
    }
}
