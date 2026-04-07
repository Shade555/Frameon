
package com.example.frameon.data.source

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.frameon.domain.model.MediaItem
import com.example.frameon.domain.model.MediaType
import com.example.frameon.worker.SensitiveDataWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class MediaStoreSource @Inject constructor(
    private val contentResolver: ContentResolver,
    @ApplicationContext private val context: Context
) {

    private val secureFolder = File(context.filesDir, "secure_folder").apply { if (!exists()) mkdirs() }
    
    private val prefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
    private val hiddenIds: MutableSet<String> = prefs.getStringSet("hidden_ids", emptySet())?.toMutableSet() ?: mutableSetOf()

    private fun saveHiddenIds() {
        prefs.edit().putStringSet("hidden_ids", hiddenIds).apply()
    }

    fun getMedia(): Flow<List<MediaItem>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                launch { 
                    send(queryMedia())
                }
            }
        }

        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver.registerContentObserver(MediaStore.Downloads.EXTERNAL_CONTENT_URI, true, observer)
        }

        launch { send(queryMedia()) }
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }

    fun getSecureMedia(): Flow<List<MediaItem>> = flow {
        emit(querySecureFolder())
    }

    private fun querySecureFolder(): List<MediaItem> {
        return secureFolder.listFiles()?.map { file ->
            MediaItem(
                id = file.hashCode().toLong(),
                uri = Uri.fromFile(file),
                displayName = file.name,
                dateAdded = file.lastModified() / 1000,
                path = file.absolutePath,
                mediaType = if (file.extension.lowercase() in listOf("mp4", "mkv", "avi")) MediaType.VIDEO else MediaType.IMAGE
            )
        }?.sortedByDescending { it.dateAdded } ?: emptyList()
    }

    fun createDeleteRequest(items: List<MediaItem>): PendingIntent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createDeleteRequest(contentResolver, items.map { it.uri })
        }
        return null
    }

    suspend fun moveToSecureFolder(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val destinationFile = File(secureFolder, "${System.currentTimeMillis()}_${item.displayName}")
            contentResolver.openInputStream(item.uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            hiddenIds.add(item.id.toString())
            saveHiddenIds()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                contentResolver.delete(item.uri, null, null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun moveOutOfSecureFolder(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(item.path!!)
            if (!sourceFile.exists()) return@withContext false

            val cleanName = item.displayName.substringAfter("_")
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
                put(MediaStore.MediaColumns.MIME_TYPE, if (item.mediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Frameon_Restored")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = if (item.mediaType == MediaType.VIDEO) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = contentResolver.insert(collection, contentValues)

            uri?.let { targetUri ->
                contentResolver.openOutputStream(targetUri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(targetUri, contentValues, null, null)
                }
                
                sourceFile.delete()
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun queryMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaItem>()
        val addedIds = mutableSetOf<Long>()

        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DATA)
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "${MediaStore.MediaColumns.IS_PENDING} = 0" else null

        val uris = mutableListOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) uris.add(MediaStore.Downloads.EXTERNAL_CONTENT_URI)

        for (queryUri in uris) {
            contentResolver.query(queryUri, projection, selection, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    if (!hiddenIds.contains(id.toString()) && addedIds.add(id)) {
                        val path = cursor.getString(dataCol)
                        val type = if (queryUri == MediaStore.Video.Media.EXTERNAL_CONTENT_URI) MediaType.VIDEO else MediaType.IMAGE
                        mediaList.add(MediaItem(id, ContentUris.withAppendedId(queryUri, id), cursor.getString(nameCol), cursor.getLong(dateCol), path, type))
                    }
                }
            }
        }
        mediaList.sortedByDescending { it.dateAdded }
    }
}
