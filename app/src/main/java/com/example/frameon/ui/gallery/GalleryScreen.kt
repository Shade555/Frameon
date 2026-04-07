
package com.example.frameon.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.frameon.R
import com.example.frameon.domain.model.MediaItem
import com.example.frameon.domain.model.MediaType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel = hiltViewModel(),
    onMediaClick: (MediaItem) -> Unit,
    onNavigateToCollage: (List<MediaItem>) -> Unit,
    onTitleLongClick: () -> Unit
) {
    val state by viewModel.galleryState.collectAsState()
    val selectedItems = viewModel.selectedItems
    val inSelectionMode = selectedItems.isNotEmpty()
    val isSecureMode by viewModel.isSecureMode.collectAsState()

    if (inSelectionMode || isSecureMode) {
        BackHandler {
            if (inSelectionMode) {
                viewModel.clearSelection()
            } else {
                viewModel.exitSecureMode()
            }
        }
    }

    Scaffold(
        topBar = {
            if (inSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedItems.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isSecureMode) "Secure Folder" else stringResource(id = R.string.app_name),
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(onLongPress = { onTitleLongClick() })
                            }
                        )
                    },
                    navigationIcon = {
                        if (isSecureMode) {
                            IconButton(onClick = { viewModel.exitSecureMode() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Secure Mode")
                            }
                        }
                    },
                    actions = {
                        if (isSecureMode) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (inSelectionMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onNavigateToCollage(selectedItems) }) {
                        Text("Collage")
                    }
                    if (isSecureMode) {
                        Button(onClick = { viewModel.moveSelectedOutOfSecure() }) {
                            Text("Move Out")
                        }
                    } else {
                        Button(onClick = { viewModel.moveSelectedToSecure() }) {
                            Text("Secure")
                        }
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(it)) {
            when (val galleryState = state) {
                is GalleryState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is GalleryState.Success -> {
                    MediaGrid(
                        mediaItemsByMonth = galleryState.media,
                        selectedItems = selectedItems,
                        onMediaClick = {
                            if (inSelectionMode) {
                                viewModel.toggleSelection(it)
                            } else {
                                onMediaClick(it)
                            }
                        },
                        onMediaLongClick = { viewModel.toggleSelection(it) }
                    )
                }
                is GalleryState.Error -> {
                    Text(
                        text = galleryState.message,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Immutable
data class MediaItemsByMonth(val items: Map<String, List<MediaItem>>)

@Composable
fun MediaGrid(
    mediaItemsByMonth: Map<String, List<MediaItem>>,
    selectedItems: List<MediaItem>,
    onMediaClick: (MediaItem) -> Unit,
    onMediaLongClick: (MediaItem) -> Unit
) {
    val gridCells = GridCells.Adaptive(minSize = 128.dp)
    LazyVerticalGrid(
        columns = gridCells
    ) {
        val immutableItems = MediaItemsByMonth(mediaItemsByMonth)
        immutableItems.items.forEach { (month, mediaItems) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = month,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(mediaItems, key = { it.id }) {
                MediaItemView(
                    mediaItem = it,
                    isSelected = selectedItems.contains(it),
                    onClick = { onMediaClick(it) },
                    onLongClick = { onMediaLongClick(it) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaItemView(
    mediaItem: MediaItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(1.dp)
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = mediaItem.uri,
            contentDescription = mediaItem.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        if (mediaItem.mediaType == MediaType.VIDEO && !isSelected) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play Video",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
