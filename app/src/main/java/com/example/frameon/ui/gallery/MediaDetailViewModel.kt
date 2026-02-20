
package com.example.frameon.ui.gallery

import android.content.ContentResolver
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class MediaDetailViewModel @Inject constructor(
    private val contentResolver: ContentResolver
) : ViewModel() {

    private val _exifData = MutableStateFlow<Map<String, String>>(emptyMap())
    val exifData: StateFlow<Map<String, String>> = _exifData.asStateFlow()

    fun loadExifData(uri: Uri) {
        viewModelScope.launch {
            _exifData.value = getExifData(uri)
        }
    }

    private suspend fun getExifData(uri: Uri): Map<String, String> = withContext(Dispatchers.IO) {
        val exifMap = mutableMapOf<String, String>()
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val exifInterface = ExifInterface(inputStream)

                // File Info
                exifMap["File Type"] = contentResolver.getType(uri) ?: "N/A"

                // Image Info
                exifMap["Date"] = exifInterface.getAttribute(ExifInterface.TAG_DATETIME) ?: "N/A"
                exifMap["Image Size"] = "${exifInterface.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)} x ${exifInterface.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)}"

                // Camera Info
                exifMap["Camera Model"] = exifInterface.getAttribute(ExifInterface.TAG_MODEL) ?: "N/A"
                exifMap["Aperture"] = exifInterface.getAttribute(ExifInterface.TAG_F_NUMBER) ?: "N/A"
                exifMap["ISO Speed"] = exifInterface.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS) ?: "N/A"

                // Location Info
                exifInterface.latLong?.let {
                    exifMap["Location"] = "${it[0]}, ${it[1]}"
                } ?: run {
                    exifMap["Location"] = "N/A"
                }
            }
        } catch (e: IOException) {
            // Handle exceptions
        }
        exifMap
    }
}
