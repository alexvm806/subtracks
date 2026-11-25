package com.subtracks.data.repo

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import com.subtracks.R
import com.subtracks.data.model.Album
import com.subtracks.data.model.Artist
import com.subtracks.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID

internal const val DEVICE_SOURCE_ID = -1L

internal fun copyDeviceAudio(
    input: InputStream,
    staging: File,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    staging.outputStream().use { output -> DigestInputStream(input, digest).copyTo(output) }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal class DeviceMusic(
    private val context: Context,
    private val downloads: DownloadRepository,
    private val scope: CoroutineScope,
) {
    private val importing = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = importing

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty() || !importing.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            var added = 0
            var known = 0
            var failed = 0
            try {
                for (uri in uris.distinct()) {
                    try {
                        if (importFile(uri)) added++ else known++
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed++
                    }
                }
                if (added > 0) downloads.refreshDeviceArtists()
                val message = context.getString(R.string.device_import_result, added, known, failed)
                withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
            } finally {
                importing.value = false
            }
        }
    }

    private suspend fun importFile(uri: Uri): Boolean {
        require(uri.scheme == "content")
        val staging = downloads.deviceStagingFile()
        try {
            val resolver = context.contentResolver
            val id = requireNotNull(resolver.openInputStream(uri)).use { copyDeviceAudio(it, staging) }
            val name =
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(staging.absolutePath)

                fun tag(key: Int): String? = metadata.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
                require(tag(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
                val artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val albumArtist = tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: artist
                val album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: context.getString(R.string.device_unknown_album)
                val albumId = metadataId("album", albumArtist.orEmpty(), album)
                val artistId = artist?.let { metadataId("artist", it) }
                val albumArtistId = albumArtist?.let { metadataId("artist", it) }
                val now = System.currentTimeMillis() / 1000
                val song =
                    Song(
                        sourceId = DEVICE_SOURCE_ID,
                        id = id,
                        albumId = albumId,
                        artistId = artistId,
                        title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: name?.substringBeforeLast('.') ?: id,
                        album = album,
                        artist = artist,
                        duration = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.div(1000),
                        track = tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.toLongOrNull(),
                        disc = tag(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.substringBefore('/')?.toLongOrNull(),
                        starred = null,
                        created = now,
                    )
                val record = Album(DEVICE_SOURCE_ID, albumId, albumArtistId, album, albumArtist, now, null, null, null, null, 1)
                val artists =
                    listOfNotNull(artist, albumArtist).distinct().map {
                        Artist(DEVICE_SOURCE_ID, metadataId("artist", it), it, 0, null)
                    }
                return downloads.storeDeviceSong(staging, song, record, artists, context.getString(R.string.device_music))
            } finally {
                metadata.release()
            }
        } finally {
            staging.delete()
        }
    }

    private fun metadataId(vararg parts: String): String = UUID.nameUUIDFromBytes(parts.joinToString("\u0000").toByteArray()).toString()
}
