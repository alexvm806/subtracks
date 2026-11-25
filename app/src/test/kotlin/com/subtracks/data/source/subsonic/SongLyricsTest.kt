package com.subtracks.data.source.subsonic

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SongLyricsTest {
    @get:Rule
    val cache = TemporaryFolder()

    @Test
    fun enhancedLookupIsGatedAndCachedLyricsWorkOffline() {
        MockWebServer().use { server ->
            server.start()
            val url = server.url("/")
            val client = SubsonicClient(url, "listener", "", true, OkHttpClient())
            val extensions = """{"openSubsonicExtensions":[{"name":"songLyrics","versions":[1,2]}]}"""
            val lyrics = """{"lyricsList":{"structuredLyrics":[{"synced":false,"line":[{"value":"saved"}]}]}}"""
            for (body in listOf(extensions, lyrics)) {
                val response = JSONObject().put("subsonic-response", JSONObject(body).put("status", "ok"))
                server.enqueue(MockResponse().setBody(response.toString()))
            }
            val online = SongLyrics.load(cache.root, url.toString(), "track", null, "title", false, client)
            assertEquals(listOf(LyricLine("saved", null)), online)
            assertEquals("/rest/getOpenSubsonicExtensions.view", server.takeRequest().requestUrl?.encodedPath)
            val fetch = server.takeRequest().requestUrl
            assertEquals("/rest/getLyricsBySongId.view", fetch?.encodedPath)
            assertEquals("true", fetch?.queryParameter("enhanced"))
            assertEquals(online, SongLyrics.load(cache.root, url.toString(), "track", null, "title", true, client))
            assertEquals(emptyList<LyricLine>(), SongLyrics.load(cache.root, "other", "track", null, "title", true, client))
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun prefersTimedLinesAndAppliesServerOffset() {
        val response =
            JSONObject(
                """
                {"lyricsList":{"structuredLyrics":[
                  {"synced":false,"line":[{"value":"plain"}]},
                  {"synced":true,"offset":150,"line":[{"value":"two","start":1150},{"value":"one","start":0}]}
                ]}}
                """.trimIndent(),
            )
        assertEquals(listOf(LyricLine("one", 0L), LyricLine("two", 1000L)), SongLyrics.parse(response))
    }

    @Test
    fun partiallyTimedLyricsRemainReadableWithoutInventingTimes() {
        val response =
            JSONObject(
                """{"lyricsList":{"structuredLyrics":[{"synced":true,"line":[{"value":"one","start":0},{"value":"two"}]}]}}""",
            )
        assertEquals(listOf(LyricLine("one", null), LyricLine("two", null)), SongLyrics.parse(response))
    }

    @Test
    fun karaokeUsesSuppliedUtf8ByteRangesAndTimes() {
        val response =
            JSONObject(
                """
                {"lyricsList":{"structuredLyrics":[{"synced":true,"offset":100,
                "line":[{"value":"é two","start":0}],"cueLine":[{"index":0,"cue":[
                {"value":"two","start":600,"end":800,"byteStart":3,"byteEnd":5}]}]}]}}
                """.trimIndent(),
            )
        assertEquals(listOf(LyricCue(2, 5, 500, 700)), SongLyrics.parse(response).single().cues)
    }

    @Test
    fun timedFileKeepsRepeatedTimestampsAndAppliesOffset() {
        val raw = "[offset:100]\n[00:04.005][00:01.2]Repeated\n[00:02.34]Middle"
        val expected = listOf(LyricLine("Repeated", 1100), LyricLine("Middle", 2240), LyricLine("Repeated", 3905))
        assertEquals(expected, SongLyrics.parseLrc(raw))
        val file = SongLyrics.overrideFile(cache.root, 1, "track")
        SongLyrics.importLrc(file, raw.byteInputStream())
        assertEquals(expected, SongLyrics.readOverride(file))
        assertEquals(emptyList<LyricLine>(), SongLyrics.readOverride(SongLyrics.overrideFile(cache.root, 2, "track")))
        val rejected = runCatching { SongLyrics.importLrc(file, "untimed".byteInputStream()) }
        assertEquals(true, rejected.isFailure)
        assertEquals(expected, SongLyrics.readOverride(file))
    }

    @Test
    fun classicLyricsRetainLineOrder() {
        val response = JSONObject().put("lyrics", JSONObject().put("value", "First\nSecond"))
        assertEquals(listOf(LyricLine("First", null), LyricLine("Second", null)), SongLyrics.parse(response))
    }
}
