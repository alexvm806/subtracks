package com.subtracks.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.subtracks.R
import com.subtracks.data.repo.LibraryRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AllSongsScreen(
    library: LibraryRepository,
    onPlay: (Long, String) -> Unit,
    onBack: () -> Unit,
) {
    val tracks = remember(library) { library.allTracks() }.collectAsLazyPagingItems()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.device_all_songs)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.navigation_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            if (tracks.itemCount == 0) {
                item {
                    if (tracks.loadState.refresh is LoadState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    } else {
                        Text(stringResource(R.string.device_no_songs), modifier = Modifier.padding(16.dp))
                    }
                }
            }
            items(tracks.itemCount) { index ->
                val track = tracks[index] ?: return@items
                ListItem(
                    headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val subtitle = listOfNotNull(track.artist, track.sourceName).joinToString(" · ")
                        Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    modifier = Modifier.fillMaxWidth().clickable { onPlay(track.sourceId, track.songId) },
                )
            }
        }
    }
}
