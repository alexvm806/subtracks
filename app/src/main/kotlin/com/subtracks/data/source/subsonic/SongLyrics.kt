package com.subtracks.data.source.subsonic

import android.util.AtomicFile
import android.util.LruCache
import okio.buffer
import okio.source
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal data class LyricLine(
    val text: String,
    val startMs: Long?,
    val cues: List<LyricCue> = emptyList(),
)

internal data class LyricCue(
    val from: Int,
    val until: Int,
    val startMs: Long,
    val endMs: Long,
)

internal object SongLyrics {
    private const val MAX_BYTES = 512 * 1024L
    private val extensions = LruCache<String, Int>(16)

    private val stamp = Regex("""\[(\d{1,5}):([0-5]\d)(?:[.:](\d{1,3}))?]""")
    private val shift = Regex("""(?im)^\s*\[offset:\s*([+-]?\d{1,8})]\s*$""")

    internal fun parseLrc(raw: String): List<LyricLine> {
        val offset = shift.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return buildList {
            for (row in raw.removePrefix("\uFEFF").lineSequence()) {
                val matches = stamp.findAll(row).toList()
                val last = matches.lastOrNull() ?: continue
                val words = row.substring(last.range.last + 1).trim()
                if (words.isEmpty()) continue
                for (match in matches) {
                    val (minutes, seconds, fraction) = match.destructured
                    val time = minutes.toLong() * 60_000 + seconds.toLong() * 1000 + fraction.padEnd(3, '0').toLong()
                    add(LyricLine(words, (time - offset).coerceAtLeast(0)))
                }
            }
        }.sortedBy { it.startMs }
    }

    internal fun overrideFile(
        filesDir: File,
        sourceId: Long,
        songId: String,
    ): AtomicFile = AtomicFile(File(File(filesDir, "timed-lyrics"), digest("$sourceId\u0000$songId")))

    internal fun importLrc(
        file: AtomicFile,
        input: InputStream,
    ) {
        val raw = input.source().buffer().use { source ->
            require(!source.request(MAX_BYTES + 1))
            source.readUtf8()
        }
        require(parseLrc(raw).isNotEmpty())
        synchronized(this) {
            val out = file.startWrite()
            try {
                out.write(raw.toByteArray())
                file.finishWrite(out)
            } catch (error: Exception) {
                file.failWrite(out)
                throw error
            }
        }
    }

    internal fun readOverride(file: AtomicFile): List<LyricLine> =
        synchronized(this) {
            if (file.baseFile.exists()) parseLrc(file.readFully().decodeToString()) else emptyList()
        }

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
        val version =
            extensions.get(serverKey) ?: lyricsVersion(client).also { extensions.put(serverKey, it) }
        val response =
            if (version > 0) {
                val params = if (version >= 2) mapOf("id" to id, "enhanced" to "true") else mapOf("id" to id)
                request(client, "getLyricsBySongId", params)
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

    private fun lyricsVersion(client: SubsonicClient): Int {
        val response =
            try {
                request(client, "getOpenSubsonicExtensions", emptyMap())
            } catch (failure: SubsonicException) {
                if (failure.code in listOf(0, 70) || failure.message == "HTTP 404") return 0
                throw failure
            }
        val entries = response.optJSONArray("openSubsonicExtensions") ?: return 0
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            if (entry.optString("name") != "songLyrics") continue
            val versions = entry.optJSONArray("versions") ?: continue
            val supported = (0 until versions.length()).map { versions.optInt(it) }
            return supported.filter { it in 1..2 }.maxOrNull() ?: 0
        }
        return 0
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
            val cueLines = group.optJSONArray("cueLine")
            val cuesByIndex = mutableMapOf<Int, JSONObject>()
            for (k in 0 until (cueLines?.length() ?: 0)) {
                val cueLine = cueLines?.optJSONObject(k) ?: continue
                cuesByIndex[cueLine.optInt("index", -1)] = cueLine
            }
            val lines =
                buildList {
                    for (j in 0 until rows.length()) {
                        val row = rows.optJSONObject(j) ?: continue
                        val value = row.optString("value")
                        val start = row.optLong("start", -1L)
                        val time = if (start >= 0) (start - offset).coerceAtLeast(0) else null
                        if (value.isNotBlank()) add(LyricLine(value, time, parseCues(value, cuesByIndex[j], offset)))
                    }
                }
            if (lines.isEmpty()) continue
            if (group.optBoolean("synced") && lines.all { it.startMs != null }) return lines.sortedBy { it.startMs }
            if (fallback.isEmpty()) fallback = lines.map { it.copy(startMs = null, cues = emptyList()) }
        }
        if (fallback.isNotEmpty()) return fallback
        val plain = response.optJSONObject("lyrics")?.optString("value").orEmpty()
        val timed = parseLrc(plain)
        if (timed.isNotEmpty()) return timed
        return plain.lines().filter { it.isNotBlank() }.map { LyricLine(it, null) }
    }

    private fun parseCues(
        text: String,
        line: JSONObject?,
        offset: Long,
    ): List<LyricCue> {
        val cues = line?.optJSONArray("cue") ?: return emptyList()
        val bytes = text.toByteArray()
        var byteCursor = 0
        var charCursor = 0
        return buildList {
            for (i in 0 until cues.length()) {
                val cue = cues.optJSONObject(i) ?: continue
                val first = cue.optInt("byteStart", -1)
                val last = cue.optInt("byteEnd", -1)
                val start = cue.optLong("start", -1)
                val end = cue.optLong("end", -1)
                if (first < byteCursor || last !in first until bytes.size || start < 0 || end <= start) continue
                val word = cue.optString("value")
                if (word.isEmpty() || bytes.decodeToString(first, last + 1) != word) continue
                val from = charCursor + bytes.decodeToString(byteCursor, first).length
                val until = from + word.length
                if (until > text.length) continue
                add(LyricCue(from, until, (start - offset).coerceAtLeast(0), (end - offset).coerceAtLeast(0)))
                charCursor = until
                byteCursor = last + 1
            }
        }
    }
}
