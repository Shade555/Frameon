
package com.example.frameon.ui.collage

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(
    imageUris: List<Uri>,
    viewModel: CollageViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    viewModel.imageUris = imageUris
    var selectedShape by remember { mutableStateOf<Shape>(RectangleShape) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val saveState by viewModel.saveState.collectAsState()

    LaunchedEffect(saveState) {
        when (saveState) {
            is SaveState.Success -> {
                scope.launch {
                    snackbarHostState.showSnackbar("Collage saved successfully!")
                }
                viewModel.resetSaveState()
            }
            is SaveState.Error -> {
                scope.launch {
                    snackbarHostState.showSnackbar("Error saving collage.")
                }
                viewModel.resetSaveState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Collage") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Preview Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .border(1.dp, Color.Gray)
                    .clip(selectedShape)
            ) {
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 128.dp)) {
                    items(imageUris) { uri ->
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = null,
                            modifier = Modifier.aspectRatio(1f),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Shape selection
            Text("Choose a shape:")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { selectedShape = CircleShape }) { Text("Circle") }
                Button(onClick = { selectedShape = RectangleShape }) { Text("Square") }
                Button(onClick = { selectedShape = createStarShape() }) { Text("Star") }
            }

            Spacer(Modifier.height(16.dp))

            when (saveState) {
                is SaveState.Saving -> CircularProgressIndicator()
                else -> {
                    Button(onClick = { viewModel.saveCollage(context, selectedShape, imageUris, 1024) }) {
                        Text("Save Collage")
                    }
                }
            }
        }
    }
}

private fun createStarShape(): Shape {
    return GenericShape { size, _ ->
        val outerRadius = size.minDimension / 2f
        val innerRadius = outerRadius / 2.5f
        val numPoints = 5

        val angle = (2 * Math.PI / numPoints).toFloat()
        val halfAngle = angle / 2.0f
        val startAngle = - (Math.PI / 2.0f).toFloat()

        val centerX = size.width / 2f
        val centerY = size.height / 2f

        var currentAngle = startAngle
        moveTo(
            centerX + outerRadius * cos(currentAngle),
            centerY + outerRadius * sin(currentAngle)
        )
        for (i in 0 until numPoints) {
            currentAngle += halfAngle
            lineTo(
                centerX + innerRadius * cos(currentAngle),
                centerY + innerRadius * sin(currentAngle)
            )
            currentAngle += halfAngle
            lineTo(
                centerX + outerRadius * cos(currentAngle),
                centerY + outerRadius * sin(currentAngle)
            )
        }
        close()
    }
}
