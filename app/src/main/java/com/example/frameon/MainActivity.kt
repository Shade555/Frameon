
package com.example.frameon

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
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
import com.example.frameon.ui.gallery.MapPickerScreen
import com.example.frameon.ui.gallery.MediaDetailScreen
import com.example.frameon.ui.gallery.MediaDetailViewModel
import com.example.frameon.ui.theme.FrameonTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrameonTheme {
                var hasPermissions by remember {
                    mutableStateOf(hasRequiredPermissions())
                }

                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissionsMap ->
                    hasPermissions = permissionsMap.values.all { it }
                }

                LaunchedEffect(key1 = hasPermissions) {
                    if (!hasPermissions) {
                        val permissions = mutableListOf<String>()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                        launcher.launch(permissions.toTypedArray())
                    }
                }

                if (hasPermissions) {
                    GalleryContent()
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Please grant permissions to access media and notifications.")
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
                val deleteLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        viewModel.consumeDeleteRequest()
                    }
                }

                val deleteRequest by viewModel.deleteRequest.collectAsState()
                var showExplanation by remember { mutableStateOf(false) }

                LaunchedEffect(deleteRequest) {
                    if (deleteRequest != null) {
                        showExplanation = true
                    }
                }

                if (showExplanation) {
                    AlertDialog(
                        onDismissRequest = { 
                            showExplanation = false
                            viewModel.consumeDeleteRequest()
                        },
                        title = { Text("Secure Folder") },
                        text = { Text("Allow Frameon to move it to secure folder?\n\n(Note: Android will now ask for permission to 'delete' the photo. This is required to hide it from your public gallery.)") },
                        confirmButton = {
                            Button(onClick = {
                                showExplanation = false
                                deleteRequest?.let {
                                    val request = IntentSenderRequest.Builder(it.intentSender).build()
                                    deleteLauncher.launch(request)
                                }
                            }) {
                                Text("Allow")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showExplanation = false
                                viewModel.consumeDeleteRequest()
                            }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                GalleryScreen(
                    viewModel = viewModel,
                    onMediaClick = {
                        val encodedUrl = Uri.encode(it.uri.toString())
                        navController.navigate("detail/$encodedUrl")
                    },
                    onNavigateToCollage = {
                        val uris = it.joinToString(",") { item -> Uri.encode(item.uri.toString()) }
                        navController.navigate("collage/$uris")
                    },
                    onTitleLongClick = {
                        showBiometricPrompt {
                            viewModel.enterSecureMode()
                        }
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
                    onBackClick = { navController.popBackStack() },
                    onNavigateToMapPicker = {
                        val encodedUrl = Uri.encode(uriString)
                        navController.navigate("map_picker/$encodedUrl")
                    }
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
            composable(
                "map_picker/{uri}",
                arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) {
                val uriString = it.arguments?.getString("uri") ?: ""
                val viewModel: MediaDetailViewModel = hiltViewModel()
                MapPickerScreen(
                    onLocationSelected = { lat, lng ->
                        viewModel.addGeotag(this@MainActivity, Uri.decode(uriString).toUri(), lat, lng)
                        navController.popBackStack()
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        
        if (biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(this)
            val biometricPrompt = BiometricPrompt(this, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Secure Folder Access")
                .setSubtitle("Allow Frameon to move it to secure folder")
                .setAllowedAuthenticators(authenticators)
                .build()

            biometricPrompt.authenticate(promptInfo)
        } else {
            onSuccess()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
