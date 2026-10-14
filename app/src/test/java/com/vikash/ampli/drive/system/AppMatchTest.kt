package com.vikash.ampli.drive.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The labels and packages from the owner's TS7 / NXOS unit, plus common head-unit apps. */
class AppMatchTest {

    private fun best(role: AppRole, apps: List<Pair<String, String>>): String? =
        apps.map { it to AppMatch.score(role, it.first, it.second) }
            .filter { it.second >= AppMatch.GOOD_ENOUGH }
            .sortedByDescending { it.second }
            .firstOrNull()?.first?.first

    private val ts7 = listOf(
        "Aux" to "com.ts.aux", "AV input" to "com.ts.avin", "BT music" to "com.ts.btmusic", "BT phone" to "com.ts.bt",
        "Chrome" to "com.android.chrome", "Files" to "com.android.documentsui", "Gallery" to "com.ts.gallery",
        "Google" to "com.google.android.googlequicksearchbox", "Instructions" to "com.ts.help", "JioHotstar" to "in.startv.hotstar",
        "Maps" to "com.google.android.apps.maps", "Multimedia" to "com.ts.media", "nxSysClean" to "com.nx.clean",
        "Play Store" to "com.android.vending", "Radio" to "com.ts.radio", "Settings" to "com.android.settings",
        "Sound settings" to "com.ts.eq", "Spotify" to "com.spotify.music", "Theme store" to "com.ts.theme",
        "Video input" to "com.ts.videoin", "Voice Search" to "com.google.android.voicesearch", "YouTube" to "com.google.android.youtube",
    )

    @Test
    fun ts7MusicIsMultimedia() {
        assertEquals("Multimedia", best(AppRole.Music, ts7))
    }

    @Test
    fun ts7PhoneIsBtPhone() {
        assertEquals("BT phone", best(AppRole.Phone, ts7))
    }

    @Test
    fun ts7NavIsGoogleMaps() {
        assertEquals("Maps", best(AppRole.Nav, ts7))
    }

    @Test
    fun btMusicAndSpotifyAreMusicButBelowMultimedia() {
        val multimedia = AppMatch.score(AppRole.Music, "Multimedia", "com.ts.media")
        assertTrue(AppMatch.score(AppRole.Music, "BT music", "com.ts.btmusic") in AppMatch.GOOD_ENOUGH until multimedia)
        assertTrue(AppMatch.score(AppRole.Music, "Spotify", "com.spotify.music") in AppMatch.GOOD_ENOUGH until multimedia)
    }

    @Test
    fun videoAndRadioAreNotMusic() {
        for ((label, pkg) in listOf("Video input" to "com.ts.videoin", "AV input" to "com.ts.avin", "Radio" to "com.ts.radio", "YouTube" to "com.google.android.youtube")) {
            assertEquals(label, 0, AppMatch.score(AppRole.Music, label, pkg))
        }
    }

    @Test
    fun phoneMatchesDialersButNotMirroringOrMusic() {
        assertTrue(AppMatch.score(AppRole.Phone, "电话", "com.fyt.bt") >= AppMatch.GOOD_ENOUGH)
        assertTrue(AppMatch.score(AppRole.Phone, "Dialer", "com.android.dialer") >= AppMatch.GOOD_ENOUGH)
        assertTrue(AppMatch.score(AppRole.Phone, "Bluetooth Phone", "com.syu.bt") >= AppMatch.GOOD_ENOUGH)
        assertEquals(0, AppMatch.score(AppRole.Phone, "Phone Link", "com.ts.phonelink"))
        assertEquals(0, AppMatch.score(AppRole.Phone, "BT music", "com.ts.btmusic"))
    }

    @Test
    fun mirroringAppsAreFoundByPackageOrLabel() {
        assertTrue(AppMatch.score(AppRole.Mirror, "ZLink", "com.zjinnova.zlink") >= 90)
        assertTrue(AppMatch.score(AppRole.Mirror, "AutoKit", "cn.manstep.phonemirrorBox") >= 90)
        assertTrue(AppMatch.score(AppRole.Mirror, "CarbitLink", "com.unknown.x") >= AppMatch.GOOD_ENOUGH)
        assertTrue(AppMatch.score(AppRole.Mirror, "TLink", "com.unknown.y") >= AppMatch.GOOD_ENOUGH)
        assertTrue(AppMatch.score(AppRole.Mirror, "AA", "com.unknown.z") >= AppMatch.GOOD_ENOUGH)
        assertEquals(0, AppMatch.score(AppRole.Mirror, "Multimedia", "com.ts.media"))
    }
}
