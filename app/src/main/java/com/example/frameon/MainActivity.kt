
package com.example.frameon

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.frameon.ui.collage.CollageScreen
import com.example.frameon.ui.collage.CollageViewModel
import com.example.frameon.ui.gallery.GalleryScreen
import com.example.frameon.ui.gallery.GalleryViewModel
import com.example.frameon.ui.gallery.MediaDetailScreen
import com.example.frameon.ui.gallery.MediaDetailViewModel
import com.example.frameon.ui.theme.FrameonTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrameonTheme {
                var hasPermissions by remember {
                    mutableStateOf(hasReadMediaPermission())
                }

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissionsMap ->
                    hasPermissions = permissionsMap.values.all { it }
                }

                LaunchedEffect(key1 = hasPermissions) {
                    if (!hasPermissions) {
                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
                        } else {
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        launcher.launch(permissions)
                    }
                }

                if (hasPermissions) {
                    GalleryContent()
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Please grant permissions to access media.")
                    }
                }
            }
        }
    }

    @Composable
    private fun GalleryContent() {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "gallery") {
            composable("gallery") {
                val viewModel: GalleryViewModel = hiltViewModel()
                GalleryScreen(
                    viewModel = viewModel,
                    onMediaClick = {
                        val encodedUrl = Uri.encode(it.uri.toString())
                        navController.navigate("detail/$encodedUrl")
                    },
                    onNavigateToCollage = {
                        val uris = it.joinToString(",") { item -> Uri.encode(item.uri.toString()) }
                        navController.navigate("collage/$uris")
                    }
                )
            }
            composable(
                "detail/{uri}",
                arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) {
                val uriString = it.arguments?.getString("uri") ?: ""
                val viewModel: MediaDetailViewModel = hiltViewModel()
                MediaDetailScreen(
                    uri = Uri.decode(uriString).toUri(),
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                "collage/{uris}",
                arguments = listOf(navArgument("uris") { type = NavType.StringType })
            ) {
                val urisString = it.arguments?.getString("uris") ?: ""
                val uris = urisString.split(",").map { uri -> Uri.decode(uri).toUri() }
                val viewModel: CollageViewModel = hiltViewModel()
                CollageScreen(
                    imageUris = uris,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }

    private fun hasReadMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
