
package com.example.frameon.ui.gallery

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreen(
    uri: Uri,
    viewModel: MediaDetailViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onNavigateToMapPicker: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }
    var showGeotagDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val exifData by viewModel.exifData.collectAsState()
    val geotagState by viewModel.geotagState.collectAsState()

    LaunchedEffect(uri) {
        viewModel.loadExifData(uri, context)
    }

    LaunchedEffect(geotagState) {
        if (geotagState is MediaDetailViewModel.GeotagState.Success) {
            Toast.makeText(context, "Photo copied with geotag!", Toast.LENGTH_SHORT).show()
            viewModel.resetGeotagState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val state = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offset += pan
        }

        Image(
            painter = rememberAsyncImagePainter(uri),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .transformable(state = state)
        )

        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { showBottomSheet = true }) {
                Text("Show Info")
            }
            Button(onClick = { showGeotagDialog = true }) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Geotag")
            }
        }

        if (showGeotagDialog) {
            AlertDialog(
                onDismissRequest = { showGeotagDialog = false },
                title = { Text("Add Geotag") },
                text = { Text("Create a copy of this photo with a geotag. How would you like to provide the location?") },
                confirmButton = {
                    TextButton(onClick = {
                        val lat = exifData["Latitude"]?.toDoubleOrNull()
                        val lng = exifData["Longitude"]?.toDoubleOrNull()
                        if (lat != null && lng != null) {
                            viewModel.addGeotag(context, uri, lat, lng)
                        } else {
                            Toast.makeText(context, "No location found in EXIF", Toast.LENGTH_SHORT).show()
                        }
                        showGeotagDialog = false
                    }) {
                        Text("Use EXIF")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        onNavigateToMapPicker()
                        showGeotagDialog = false
                    }) {
                        Text("Pick on Map")
                    }
                }
            )
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                windowInsets = WindowInsets(0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    exifData.forEach { (key, value) ->
                        Text("$key: $value")
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) showBottomSheet = false
                        }
                    }) {
                        Text("Hide")
                    }
                }
            }
        }
    }
}
