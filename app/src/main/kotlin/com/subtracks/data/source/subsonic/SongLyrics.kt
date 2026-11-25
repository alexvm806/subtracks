package com.subtracks.data.source.subsonic

import android.util.LruCache
import okio.buffer
import okio.source
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal data class LyricLine(
    val text: String,
    val startMs: Long?,
)

internal object SongLyrics {
    private const val MAX_BYTES = 512 * 1024L
    private val extensions = LruCache<String, Boolean>(16)

    fun load(
        cacheDir: File,
        serverKey: String,
        id: String,
        artist: String?,
        title: String,
        offline: Boolean,
        client: SubsonicClient,
    ): List<LyricLine> {
        val directory = File(cacheDir, "lyrics-v1")
        val file = File(directory, digest("$serverKey\u0000$id"))
        if (file.length() in 1..MAX_BYTES) {
            val cached = runCatching { parse(JSONObject(file.readText())) }.getOrNull()
            if (!cached.isNullOrEmpty()) {
                file.setLastModified(System.currentTimeMillis())
                return cached
            }
        }
        if (offline) return emptyList()
        val structured =
            extensions.get(serverKey) ?: supportsLyrics(client).also { extensions.put(serverKey, it) }
        val response =
            if (structured) {
                request(client, "getLyricsBySongId", mapOf("id" to id))
            } else {
                if (artist.isNullOrBlank()) return emptyList()
                request(client, "getLyrics", mapOf("artist" to artist, "title" to title))
            }
        val lines = parse(response)
        if (lines.isNotEmpty()) {
            runCatching {
                directory.mkdirs()
                val temp = File.createTempFile("lyrics-", ".tmp", directory)
                try {
                    temp.writeText(response.toString())
                    if (temp.renameTo(file)) {
                        directory
                            .listFiles { entry -> entry.name.length == 64 }
                            ?.sortedByDescending { it.lastModified() }
                            ?.drop(128)
                            ?.forEach { it.delete() }
                    }
                } finally {
                    temp.delete()
                }
            }
        }
        return lines
    }

    private fun digest(key: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun supportsLyrics(client: SubsonicClient): Boolean {
        val response =
            try {
                request(client, "getOpenSubsonicExtensions", emptyMap())
            } catch (failure: SubsonicException) {
                if (failure.code in listOf(0, 70) || failure.message == "HTTP 404") return false
                throw failure
            }
        val entries = response.optJSONArray("openSubsonicExtensions") ?: return false
        return (0 until entries.length()).any { entries.optJSONObject(it)?.optString("name") == "songLyrics" }
    }

    private fun request(
        client: SubsonicClient,
        method: String,
        params: Map<String, String>,
    ): JSONObject =
        client.stream(method, params + ("f" to "json"), timeoutMs = 12_000L) { input ->
            val source = input.source().buffer()
            check(!source.request(MAX_BYTES + 1)) { "Lyrics response too large" }
            val response = JSONObject(source.readUtf8()).getJSONObject("subsonic-response")
            if (response.optString("status") != "ok") {
                val error = response.optJSONObject("error")
                throw SubsonicException(error?.optInt("code") ?: -1, error?.optString("message").orEmpty())
            }
            response
        }

    internal fun parse(response: JSONObject): List<LyricLine> {
        val groups = response.optJSONObject("lyricsList")?.optJSONArray("structuredLyrics")
        var fallback = emptyList<LyricLine>()
        for (i in 0 until (groups?.length() ?: 0)) {
            val group = groups?.optJSONObject(i) ?: continue
            if (group.optString("kind", "main") != "main") continue
            val rows = group.optJSONArray("line") ?: continue
            val offset = group.optInt("offset").toLong()
            val lines =
                buildList {
                    for (j in 0 until rows.length()) {
                        val row = rows.optJSONObject(j) ?: continue
                        val value = row.optString("value")
                        val start = row.optLong("start", -1L)
                        val time = if (start >= 0) (start - offset).coerceAtLeast(0) else null
                        if (value.isNotBlank()) add(LyricLine(value, time))
                    }
                }
            if (lines.isEmpty()) continue
            if (group.optBoolean("synced") && lines.all { it.startMs != null }) return lines.sortedBy { it.startMs }
            if (fallback.isEmpty()) fallback = lines.map { it.copy(startMs = null) }
        }
        if (fallback.isNotEmpty()) return fallback
        val plain = response.optJSONObject("lyrics")?.optString("value").orEmpty()
        return plain.lines().filter { it.isNotBlank() }.map { LyricLine(it, null) }
    }
}
