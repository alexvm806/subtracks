package com.subtracks.data.source.subsonic

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SongLyricsTest {
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
    fun classicLyricsRetainLineOrder() {
        val response = JSONObject().put("lyrics", JSONObject().put("value", "First\nSecond"))
        assertEquals(listOf(LyricLine("First", null), LyricLine("Second", null)), SongLyrics.parse(response))
    }
}
