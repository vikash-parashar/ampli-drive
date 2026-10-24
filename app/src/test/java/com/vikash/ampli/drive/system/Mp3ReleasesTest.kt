package com.vikash.ampli.drive.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Mp3ReleasesTest {

    private fun release(tag: String, published: String, vararg assets: String, draft: Boolean = false) = """
        {"tag_name":"$tag","draft":$draft,"prerelease":true,"published_at":"$published","assets":[
        ${assets.joinToString(",") { """{"name":"$it","size":1234,"browser_download_url":"https://github.com/vikash-parashar/music-player/releases/download/$tag/$it"}""" }}
        ]}
    """.trimIndent()

    @Test
    fun picksTheNewestPlainApkNotProtestOrAab() {
        val json = "[" + listOf(
            release("build-126", "2026-10-24T11:56:30Z", "Ampli-126-protest.apk", "Ampli-126.aab", "Ampli-126.apk"),
            release("build-124", "2026-10-24T02:15:08Z", "Ampli-124-protest.apk", "Ampli-124.apk"),
        ).joinToString(",") + "]"
        val apk = Mp3Releases.pick(json)!!
        assertEquals("Ampli-126.apk", apk.name)
        assertEquals(126, apk.build)
        assertEquals("build-126", apk.tag)
        assertEquals(1234L, apk.size)
        assertEquals("https://github.com/vikash-parashar/music-player/releases/download/build-126/Ampli-126.apk", apk.url)
    }

    @Test
    fun skipsReleasesWithoutAPlainApkAndDrafts() {
        val json = "[" + listOf(
            release("build-130", "2026-10-25T10:00:00Z", "Ampli-130.apk", draft = true),
            release("build-129", "2026-10-25T09:00:00Z", "Ampli-129-protest.apk", "Ampli-129.aab"),
            release("build-128", "2026-10-25T08:00:00Z", "Ampli-128.apk"),
        ).joinToString(",") + "]"
        assertEquals("Ampli-128.apk", Mp3Releases.pick(json)?.name)
    }

    @Test
    fun orderInTheResponseDoesNotMatter() {
        val json = "[" + listOf(
            release("build-121", "2026-10-20T13:02:52Z", "Ampli-121.apk"),
            release("build-122", "2026-10-20T13:55:28Z", "Ampli-122.apk"),
        ).joinToString(",") + "]"
        assertEquals(122, Mp3Releases.pick(json)?.build)
    }

    @Test
    fun nothingUsable() {
        assertNull(Mp3Releases.pick("[]"))
        assertNull(Mp3Releases.pick("not json"))
        assertNull(Mp3Releases.pick("""{"message":"API rate limit exceeded"}"""))
        assertNull(Mp3Releases.pick("[" + release("x", "2026-01-01T00:00:00Z", "Ampli.apk", "Other-3.apk") + "]"))
    }
}
