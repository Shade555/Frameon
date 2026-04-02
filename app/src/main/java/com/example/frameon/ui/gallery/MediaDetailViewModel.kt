
package com.example.frameon.ui.gallery

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
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
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class MediaDetailViewModel @Inject constructor(
    private val contentResolver: ContentResolver
) : ViewModel() {

    private val _exifData = MutableStateFlow<Map<String, String>>(emptyMap())
    val exifData: StateFlow<Map<String, String>> = _exifData.asStateFlow()

    private val _geotagState = MutableStateFlow<GeotagState>(GeotagState.Idle)
    val geotagState: StateFlow<GeotagState> = _geotagState.asStateFlow()

    sealed interface GeotagState {
        object Idle : GeotagState
        object Processing : GeotagState
        object Success : GeotagState
        object Error : GeotagState
    }

    fun loadExifData(uri: Uri) {
        viewModelScope.launch {
            _exifData.value = getExifData(uri)
        }
    }

    fun addGeotag(context: Context, originalUri: Uri, lat: Double, lng: Double) {
        viewModelScope.launch {
            _geotagState.value = GeotagState.Processing
            val result = saveCopyWithVisibleGeotag(context, originalUri, lat, lng)
            _geotagState.value = if (result) GeotagState.Success else GeotagState.Error
        }
    }

    fun resetGeotagState() {
        _geotagState.value = GeotagState.Idle
    }

    private suspend fun saveCopyWithVisibleGeotag(context: Context, originalUri: Uri, lat: Double, lng: Double): Boolean = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            val addressObj = addresses?.firstOrNull()
            
            val cityLine = "${addressObj?.locality ?: ""}, ${addressObj?.adminArea ?: ""}, ${addressObj?.countryName ?: ""}".trim(',', ' ')
            val fullAddress = addressObj?.getAddressLine(0) ?: "Unknown Location"
            
            val inputStream = contentResolver.openInputStream(originalUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (originalBitmap == null) return@withContext false

            val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            val w = mutableBitmap.width.toFloat()
            val h = mutableBitmap.height.toFloat()
            
            // Refined Unit Scale
            val unit = w / 160f
            val padding = unit * 6f 
            val spacing = unit * 2.2f
            
            val headerTextSize = unit * 4.2f
            val infoTextSize = unit * 3.0f
            val mapSize = unit * 22f
            val textLeft = padding + mapSize + padding
            
            // INCREASED SAFETY ZONE: 20% gutter on the right side
            val safeRightMargin = w * 0.20f
            val availableWidth = (w - textLeft - safeRightMargin).toInt().coerceAtLeast(100)

            // 1. Prepare Text Layouts with forced wrapping
            textPaint.color = Color.WHITE
            
            textPaint.textSize = headerTextSize
            textPaint.typeface = Typeface.DEFAULT_BOLD
            val cityLayout = StaticLayout.Builder.obtain(cityLine, 0, cityLine.length, textPaint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

            textPaint.textSize = infoTextSize
            textPaint.typeface = Typeface.DEFAULT
            val addressLayout = StaticLayout.Builder.obtain(fullAddress, 0, fullAddress.length, textPaint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

            val latText = "Lat $lat°"
            val latLayout = StaticLayout.Builder.obtain(latText, 0, latText.length, textPaint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            
            val lngText = "Long $lng°"
            val lngLayout = StaticLayout.Builder.obtain(lngText, 0, lngText.length, textPaint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

            val sdf = SimpleDateFormat("dd/MM/yy hh:mm a 'GMT' Z", Locale.getDefault())
            val dateText = sdf.format(Date())
            val dateLayout = StaticLayout.Builder.obtain(dateText, 0, dateText.length, textPaint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

            // Dynamic height calculation
            val totalTextHeight = cityLayout.height + spacing + addressLayout.height + spacing + 
                                 latLayout.height + (spacing / 2) + lngLayout.height + spacing + dateLayout.height
            val finalOverlayHeight = maxOf(totalTextHeight, mapSize) + (padding * 2)
            val barTop = h - finalOverlayHeight

            // 2. Draw Main Overlay background
            paint.color = Color.parseColor("#B0000000")
            canvas.drawRect(0f, barTop, w, h, paint)

            // 3. Draw "Frameon" Tag (Top Right, extreme edge)
            val tagText = "Frameon"
            textPaint.textSize = unit * 3.8f
            textPaint.typeface = Typeface.DEFAULT_BOLD
            val tagTextWidth = textPaint.measureText(tagText)
            val tagHorizPadding = unit * 5f
            val tagVertPadding = unit * 2f
            val tagBoxWidth = tagTextWidth + (tagHorizPadding * 2)
            val tagBoxHeight = textPaint.textSize + (tagVertPadding * 2)
            val tagOverlap = unit * 0.1f
            
            val tagLeft = w - tagBoxWidth
            val tagTop = barTop - tagBoxHeight + tagOverlap
            
            paint.color = Color.parseColor("#B0000000")
            canvas.drawRect(tagLeft, tagTop, w, barTop + tagOverlap, paint)
            canvas.drawText(tagText, tagLeft + tagHorizPadding, tagTop + tagVertPadding + (textPaint.textSize * 0.85f), textPaint)

            // 4. Draw Map Thumbnail
            val mapTop = barTop + (finalOverlayHeight - mapSize) / 2
            paint.color = Color.parseColor("#40FFFFFF")
            canvas.drawRect(padding, mapTop, padding + mapSize, mapTop + mapSize, paint)
            paint.color = Color.RED
            canvas.drawCircle(padding + (mapSize/2), mapTop + (mapSize/2), mapSize/10, paint)

            // 5. Draw Content Sections
            var currentY = barTop + padding
            
            fun drawBlock(layout: StaticLayout, verticalGap: Float) {
                canvas.save()
                canvas.translate(textLeft, currentY)
                layout.draw(canvas)
                canvas.restore()
                currentY += layout.height + verticalGap
            }

            drawBlock(cityLayout, spacing)
            drawBlock(addressLayout, spacing)
            drawBlock(latLayout, spacing / 2)
            drawBlock(lngLayout, spacing)
            drawBlock(dateLayout, 0f)

            // 6. Save image
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Geotag_Pro_${System.currentTimeMillis()}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Frameon_Geotagged")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val targetUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            targetUri?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { out ->
                    mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(uri, contentValues, null, null)
                }
                return@withContext true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getExifData(uri: Uri): Map<String, String> = withContext(Dispatchers.IO) {
        val exifMap = mutableMapOf<String, String>()
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val exifInterface = ExifInterface(inputStream)
                exifMap["File Type"] = contentResolver.getType(uri) ?: "N/A"
                exifMap["Date"] = exifInterface.getAttribute(ExifInterface.TAG_DATETIME) ?: "N/A"
                exifMap["Image Size"] = "${exifInterface.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)} x ${exifInterface.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)}"
                exifMap["Camera Model"] = exifInterface.getAttribute(ExifInterface.TAG_MODEL) ?: "N/A"
                
                exifInterface.latLong?.let {
                    exifMap["Location"] = "${it[0]}, ${it[1]}"
                    exifMap["Latitude"] = it[0].toString()
                    exifMap["Longitude"] = it[1].toString()
                } ?: run {
                    exifMap["Location"] = "N/A"
                }
            }
        } catch (e: IOException) {}
        exifMap
    }
}
