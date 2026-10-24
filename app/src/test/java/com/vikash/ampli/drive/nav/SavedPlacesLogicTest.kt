package com.vikash.ampli.drive.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedPlacesLogicTest {

    @Test
    fun homeAndOfficeReplaceAndSortFirst() {
        var list = SavedPlacesLogic.upsert(emptyList(), SavedPlace(SavedKind.Favourite, "Petrol pump", 28.60, 77.20))!!
        list = SavedPlacesLogic.upsert(list, SavedPlace(SavedKind.Office, "Cyber City", 28.49, 77.08))!!
        list = SavedPlacesLogic.upsert(list, SavedPlace(SavedKind.Home, "Sector 7", 28.70, 77.10))!!
        list = SavedPlacesLogic.upsert(list, SavedPlace(SavedKind.Home, "New flat", 28.71, 77.11))!!
        assertEquals(listOf(SavedKind.Home, SavedKind.Office, SavedKind.Favourite), list.map { it.kind })
        assertEquals("New flat", list.first().name)
    }

    @Test
    fun sixFavouritesAtMost() {
        var list = emptyList<SavedPlace>()
        for (i in 0 until 6) list = SavedPlacesLogic.upsert(list, SavedPlace(SavedKind.Favourite, "F$i", 28.0 + i * 0.1, 77.0))!!
        assertNull(SavedPlacesLogic.upsert(list, SavedPlace(SavedKind.Favourite, "F7", 20.0, 70.0)))
        // Home still fits, and the same spot again just renames.
        assertEquals(7, SavedPlacesLogic.upsert(list, SavedPlace(SavedKind.Home, "H", 1.0, 1.0))!!.size)
        val renamed = SavedPlacesLogic.upsert(list, SavedPlace(SavedKind.Favourite, "Dhaba", 28.0001, 77.0))!!
        assertEquals(6, renamed.size)
        assertEquals("Dhaba", renamed.last().name)
    }

    @Test
    fun nearFindsTheClosestWithin200m() {
        val list = listOf(SavedPlace(SavedKind.Home, "H", 28.7000, 77.1000), SavedPlace(SavedKind.Office, "O", 28.4900, 77.0800))
        assertEquals(SavedKind.Home, SavedPlacesLogic.near(list, 28.7010, 77.1000)?.kind)
        assertNull(SavedPlacesLogic.near(list, 28.7100, 77.1000))
    }
}
