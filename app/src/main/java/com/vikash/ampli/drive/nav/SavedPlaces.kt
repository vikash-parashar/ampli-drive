package com.vikash.ampli.drive.nav

import android.content.Context
import com.vikash.ampli.drive.cameras.CameraGeometry
import com.vikash.ampli.drive.voice.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Home, Office, or one of the favourites. */
enum class SavedKind { Home, Office, Favourite }

/** A place the driver saved: one tap (or "ghar chalo") starts navigation to it. */
data class SavedPlace(val kind: SavedKind, val name: String, val lat: Double, val lon: Double) {
    fun toPlace(label: String = name): Place = Place(label, null, lat, lon, null)
}

/** The list rules, kept pure for tests. */
object SavedPlacesLogic {
    const val MAX_FAVOURITES = 6

    /** Within this distance a trip start or end is named after the saved place. */
    const val NEAR_M = 200.0

    /**
     * [list] with [place] added. Home and Office replace the old one; a favourite at the same
     * spot (within 50 m) replaces that favourite. Null when the favourites are already full.
     */
    fun upsert(list: List<SavedPlace>, place: SavedPlace): List<SavedPlace>? {
        val cleaned = place.copy(name = place.name.trim().take(40))
        if (cleaned.kind != SavedKind.Favourite) {
            return sorted(list.filterNot { it.kind == cleaned.kind } + cleaned)
        }
        val others = list.filterNot {
            it.kind == SavedKind.Favourite && CameraGeometry.distanceM(it.lat, it.lon, cleaned.lat, cleaned.lon) < 50.0
        }
        if (others.count { it.kind == SavedKind.Favourite } >= MAX_FAVOURITES) return null
        return sorted(others + cleaned)
    }

    /** Home first, then Office, then favourites in the order they were added. */
    fun sorted(list: List<SavedPlace>): List<SavedPlace> = list.sortedBy { it.kind.ordinal }

    /** The saved place nearest to (lat, lon) within [NEAR_M], or null. */
    fun near(list: List<SavedPlace>, lat: Double, lon: Double): SavedPlace? = list
        .map { it to CameraGeometry.distanceM(it.lat, it.lon, lat, lon) }
        .filter { it.second <= NEAR_M }
        .minByOrNull { it.second }
        ?.first

    fun toJson(list: List<SavedPlace>): String {
        val arr = JSONArray()
        for (p in list) arr.put(JSONObject().put("k", p.kind.name).put("n", p.name).put("lat", p.lat).put("lon", p.lon))
        return arr.toString()
    }

    fun fromJson(raw: String?): List<SavedPlace> = try {
        val arr = JSONArray(raw ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val kind = runCatching { SavedKind.valueOf(o.optString("k")) }.getOrNull() ?: return@mapNotNull null
            val lat = o.optDouble("lat", Double.NaN)
            val lon = o.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) null else SavedPlace(kind, o.optString("n"), lat, lon)
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/** Saved places, kept on this unit only (SharedPreferences JSON, like CarStore). */
class SavedPlacesStore(context: Context) {
    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _places = MutableStateFlow(SavedPlacesLogic.sorted(SavedPlacesLogic.fromJson(sp.getString(KEY, null))))
    val places: StateFlow<List<SavedPlace>> = _places.asStateFlow()

    fun get(kind: SavedKind): SavedPlace? = _places.value.firstOrNull { it.kind == kind }

    /** False when the favourites are full. */
    fun save(place: SavedPlace): Boolean {
        val next = SavedPlacesLogic.upsert(_places.value, place) ?: return false
        write(next)
        return true
    }

    fun remove(place: SavedPlace) = write(_places.value.filterNot { it == place })

    val favouritesFull: Boolean
        get() = _places.value.count { it.kind == SavedKind.Favourite } >= SavedPlacesLogic.MAX_FAVOURITES

    private fun write(list: List<SavedPlace>) {
        sp.edit().putString(KEY, SavedPlacesLogic.toJson(list)).apply()
        _places.value = list
    }

    private companion object {
        const val FILE = "places"
        const val KEY = "saved"
    }
}
