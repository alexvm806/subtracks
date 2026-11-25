package com.subtracks.ui.playback

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.subtracks.R
import com.subtracks.data.source.subsonic.LyricLine
import kotlinx.coroutines.CancellationException

@Composable
internal fun LyricsDialog(
    title: String,
    positionMs: Long,
    offline: Boolean,
    load: suspend () -> List<LyricLine>,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.info_close)) }
                }
                LyricsContent(positionMs, offline, load, onSeek, immersive = true)
            }
        }
    }
}

@Composable
internal fun LyricsContent(
    positionMs: Long,
    offline: Boolean,
    load: suspend () -> List<LyricLine>,
    onSeek: (Long) -> Unit,
    immersive: Boolean = false,
) {
    var lines by remember { mutableStateOf<List<LyricLine>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt, offline) {
        failed = false
        lines = null
        try {
            lines = load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    val lyrics = lines
    when {
        failed -> {
            Column {
                Text(stringResource(R.string.lyrics_failed))
                TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.lyrics_retry)) }
            }
        }

        lyrics == null -> {
            Text(stringResource(R.string.lyrics_loading))
        }

        lyrics.isEmpty() -> {
            Text(stringResource(if (offline) R.string.lyrics_offline else R.string.lyrics_empty))
        }

        else -> {
            LyricList(lyrics, positionMs, onSeek, immersive)
        }
    }
}

@Composable
private fun LyricList(
    lyrics: List<LyricLine>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    immersive: Boolean,
) {
    val state = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    val dragged by state.interactionSource.collectIsDraggedAsState()
    val synced = lyrics.first().startMs != null
    val active =
        if (synced) {
            val index = lyrics.binarySearchBy(positionMs) { it.startMs }
            if (index >= 0) index else -index - 2
        } else {
            -1
        }
    LaunchedEffect(dragged) { if (dragged) follow = false }
    LaunchedEffect(active, follow) {
        if (follow && active >= 0) {
            val target = (active - if (immersive) 2 else 1).coerceAtLeast(0)
            state.animateScrollToItem(target)
        }
    }
    Column {
        if (synced && !follow) {
            TextButton(onClick = { follow = true }) { Text(stringResource(R.string.lyrics_follow)) }
        }
        LazyColumn(modifier = if (immersive) Modifier.fillMaxSize() else Modifier.heightIn(max = 400.dp), state = state) {
            itemsIndexed(lyrics) { index, line ->
                val time = line.startMs
                val seek =
                    if (time != null) {
                        Modifier.clickable {
                            onSeek(time)
                            follow = true
                        }
                    } else {
                        Modifier
                    }
                val cue = if (index == active) line.cues.firstOrNull { positionMs in it.startMs until it.endMs } else null
                val highlight = MaterialTheme.colorScheme.primary
                val label =
                    remember(line, cue, highlight) {
                        buildAnnotatedString {
                            append(line.text)
                            if (cue != null) addStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold), cue.from, cue.until)
                        }
                    }
                val ink by animateColorAsState(
                    if (index == active || !synced) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "lyric emphasis",
                )
                Text(
                    text = label,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(seek)
                            .padding(vertical = 8.dp),
                    textAlign = if (immersive) TextAlign.Center else TextAlign.Start,
                    fontWeight = if (immersive) FontWeight.Bold else null,
                    style = if (immersive) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    color = if (!immersive && index == active && line.cues.isEmpty()) highlight else ink,
                )
            }
        }
    }
}
