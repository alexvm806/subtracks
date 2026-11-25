package com.subtracks.ui.search

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

private val accents = Regex("\\p{M}+")
private val separators = Regex("[^\\p{L}\\p{N}]+")

private fun normalise(text: String): String =
    Normalizer
        .normalize(text, Normalizer.Form.NFD)
        .replace(accents, "")
        .lowercase(Locale.ROOT)
        .replace(separators, " ")
        .trim()

internal fun spellingProbes(query: String): List<String> {
    if (query.length !in 6..80) return emptyList()
    val half = query.length / 2
    return listOf(query.take(half).trim(), query.drop(half).trim()).filter { searchReady(it) }.distinct()
}

internal fun withinOneEdit(
    query: String,
    value: String,
): Boolean {
    if (abs(query.length - value.length) > 1) return false
    val limit = minOf(query.length, value.length)
    var i = 0
    while (i < limit && query[i] == value[i]) i++
    if (i == limit) return true
    if (query.length != value.length) {
        val longer = if (query.length > value.length) query else value
        val shorter = if (query.length > value.length) value else query
        return longer.regionMatches(i + 1, shorter, i, shorter.length - i)
    }
    if (query.regionMatches(i + 1, value, i + 1, limit - i - 1)) return true
    return i + 1 < limit && query[i] == value[i + 1] && query[i + 1] == value[i] &&
        query.regionMatches(i + 2, value, i + 2, limit - i - 2)
}

internal fun spellingResults(
    text: String,
    candidates: List<SearchResults>,
): SearchResults {
    val query = normalise(text)
    val words = query.count { it == ' ' } + 1

    fun matches(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val tokens = normalise(value).split(' ')
        return tokens.windowed(words).any { withinOneEdit(query, it.joinToString(" ")) }
    }

    return SearchResults(
        albums = candidates.flatMap { it.albums }.distinctBy { it.id }.filter { matches(it.name) || matches(it.albumArtist) },
        artists = candidates.flatMap { it.artists }.distinctBy { it.id }.filter { matches(it.name) },
        playlists = candidates.flatMap { it.playlists }.distinctBy { it.id }.filter { matches(it.name) },
        songs = candidates.flatMap { it.songs }.distinctBy { it.song.id }.filter { matches(it.song.title) || matches(it.song.artist) },
    )
}
