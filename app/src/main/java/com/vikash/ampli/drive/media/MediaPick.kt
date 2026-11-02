package com.vikash.ampli.drive.media

/**
 * Which media session the music card (and the rail's Play/Pause) follows. Pure, so the order is
 * unit-tested.
 *
 * Head units such as the TS7 keep idle sessions of their own (Bluetooth music, the radio, the
 * stock player) that carry no metadata. Taking "the first session" when nothing plays showed
 * one of those as "Unknown track" although Ampli MP3 had been playing a moment before. Order:
 * 1. the one the driver picked from the source chips, 2. one that is playing (with a title
 * first), 3. a session with a title from the last-used player or Ampli, 4. any session with a
 * title, 5. the first one.
 */
object MediaPick {
    data class Candidate(val packageName: String, val playing: Boolean, val hasTitle: Boolean)

    /** Index of the chosen candidate, or null for an empty list. */
    fun choose(list: List<Candidate>, pinned: String?, preferred: List<String>, self: String? = null): Int? {
        if (list.isEmpty()) return null
        val idx = list.indices
        idx.firstOrNull { list[it].packageName == pinned }?.let { return it }
        idx.firstOrNull { list[it].playing && list[it].hasTitle }?.let { return it }
        idx.firstOrNull { list[it].playing }?.let { return it }
        for (pkg in preferred) {
            idx.firstOrNull { list[it].packageName == pkg && list[it].hasTitle }?.let { return it }
        }
        idx.firstOrNull { list[it].hasTitle && list[it].packageName != self }?.let { return it }
        idx.firstOrNull { list[it].hasTitle }?.let { return it }
        return 0
    }
}
