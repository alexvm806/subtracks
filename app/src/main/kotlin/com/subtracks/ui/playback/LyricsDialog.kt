package com.subtracks.ui.playback

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.subtracks.R
import com.subtracks.data.model.CoverArtRef
import com.subtracks.data.source.subsonic.LyricLine
import com.subtracks.ui.components.CoverArt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun LyricsPanel(
    title: String,
    artist: String?,
    album: String?,
    artwork: CoverArtRef?,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    advanceMs: Int,
    playerPosition: () -> Long,
    offline: Boolean,
    load: suspend () -> List<LyricLine>,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(ref = artwork, name = title, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    listOfNotNull(artist, album).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                actions()
            }
            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                LyricsContent(
                    offline = offline,
                    load = load,
                    onSeek = { onSeek((it - advanceMs).coerceAtLeast(0)) },
                    playerPosition = playerPosition,
                    playing = playing,
                    positionMs = positionMs,
                    advanceMs = advanceMs,
                )
            }
            if (durationMs > 0) {
                var scrub by remember { mutableStateOf<Float?>(null) }
                Slider(
                    value = scrub ?: (positionMs.toFloat() / durationMs).coerceIn(0f, 1f),
                    onValueChange = { scrub = it },
                    onValueChangeFinished = {
                        scrub?.let { onSeek((it * durationMs).toLong()) }
                        scrub = null
                    },
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = onPrevious) { Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.controls_previous)) }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (playing) R.string.controls_pause else R.string.controls_play),
                    )
                }
                IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.controls_next)) }
            }
        }
    }
}

@Composable
private fun LyricsContent(
    offline: Boolean,
    load: suspend () -> List<LyricLine>,
    onSeek: (Long) -> Unit,
    playerPosition: () -> Long,
    playing: Boolean,
    positionMs: Long,
    advanceMs: Int,
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
            var clock by remember { mutableLongStateOf(playerPosition()) }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(playing, lifecycle) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    if (playing && lyrics.first().startMs != null) {
                        while (true) {
                            clock = playerPosition()
                            delay(40)
                        }
                    }
                }
            }
            LyricList(lyrics, (if (playing) clock else positionMs) + advanceMs, onSeek)
        }
    }
}

@Composable
private fun LyricList(
    lyrics: List<LyricLine>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
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
            val target = (active - 2).coerceAtLeast(0)
            if (kotlin.math.abs(state.firstVisibleItemIndex - target) > 5) state.scrollToItem(target) else state.animateScrollToItem(target)
        }
    }
    Column {
        if (synced && !follow) {
            TextButton(onClick = { follow = true }) { Text(stringResource(R.string.lyrics_follow)) }
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), state = state) {
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
                val foreground = MaterialTheme.colorScheme.onSurface
                val ink by animateColorAsState(
                    if (index == active || !synced) foreground else foreground.copy(alpha = 0.58f),
                    animationSpec = tween(70),
                    label = "lyric emphasis",
                )
                Text(
                    text = label,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(seek)
                            .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                    color = ink,
                )
            }
        }
    }
}
