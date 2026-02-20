
package com.example.frameon.ui.collage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import com.example.frameon.ui.collage.shapes.CollageShape
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.sqrt

sealed interface SaveState {
    object Idle : SaveState
    object Saving : SaveState
    object Success : SaveState
    object Error : SaveState
}

@HiltViewModel
class CollageViewModel @Inject constructor() : ViewModel() {
    var imageUris: List<Uri> = emptyList()
    var selectedShape: CollageShape? = null

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState

    fun saveCollage(context: Context, shape: Shape, imageUris: List<Uri>, collageSizePx: Int) {
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            val result = createAndSaveCollage(context, shape, imageUris, collageSizePx)
            _saveState.value = if (result) SaveState.Success else SaveState.Error
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    private suspend fun createAndSaveCollage(context: Context, shape: Shape, uris: List<Uri>, sizePx: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val imageLoader = context.imageLoader
            val bitmaps = uris.mapNotNull {
                val request = ImageRequest.Builder(context).data(it).allowHardware(false).build()
                (imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
            }

            if (bitmaps.isEmpty()) return@withContext false

            val collageBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(collageBitmap)
            canvas.drawColor(android.graphics.Color.WHITE)

            val gridSize = ceil(sqrt(bitmaps.size.toFloat())).toInt()
            val itemSize = sizePx / gridSize
            bitmaps.forEachIndexed { index, bmp ->
                val (left, top, right, bottom) = Rect(0, 0, bmp.width, bmp.height)
                val dstRect = Rect( (index % gridSize) * itemSize, (index / gridSize) * itemSize,  ((index % gridSize) + 1) * itemSize, ((index / gridSize) + 1) * itemSize)
                canvas.drawBitmap(bmp, Rect(left, top, right, bottom), dstRect, null)
            }

            val finalBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val finalCanvas = Canvas(finalBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val density = Density(context)
            val outline = shape.createOutline(androidx.compose.ui.geometry.Size(sizePx.toFloat(), sizePx.toFloat()), LayoutDirection.Ltr, density)

            when (outline) {
                is Outline.Generic -> finalCanvas.drawPath(outline.path.asAndroidPath(), paint)
                is Outline.Rectangle -> finalCanvas.drawPath(Path().apply { addRect(outline.rect) }.asAndroidPath(), paint)
                is Outline.Rounded -> finalCanvas.drawPath(Path().apply { addRoundRect(outline.roundRect) }.asAndroidPath(), paint)
            }

            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            finalCanvas.drawBitmap(collageBitmap, 0f, 0f, paint)

            saveBitmap(context, finalBitmap, "Frameon_Collage_${System.currentTimeMillis()}.png")

            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap, displayName: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Frameon")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)
            }
        }
    }
}
