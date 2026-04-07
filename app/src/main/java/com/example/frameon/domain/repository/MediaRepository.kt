
package com.example.frameon.domain.repository

import android.app.PendingIntent
import com.example.frameon.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

/**
 * The repository interface for accessing media.
 */
interface MediaRepository {
    fun getMediaItems(): Flow<List<MediaItem>>
    fun getSecureMediaItems(): Flow<List<MediaItem>>
    suspend fun moveToSecureFolder(item: MediaItem): Boolean
    suspend fun moveOutOfSecureFolder(item: MediaItem): Boolean
    fun createDeleteRequest(items: List<MediaItem>): PendingIntent?
}
