
package com.example.frameon.data.repository

import android.app.PendingIntent
import com.example.frameon.data.source.MediaStoreSource
import com.example.frameon.domain.model.MediaItem
import com.example.frameon.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The implementation of the MediaRepository.
 */
class MediaRepositoryImpl @Inject constructor(
    private val mediaStoreSource: MediaStoreSource
) : MediaRepository {

    override fun getMediaItems(): Flow<List<MediaItem>> {
        return mediaStoreSource.getMedia()
    }

    override fun getSecureMediaItems(): Flow<List<MediaItem>> {
        return mediaStoreSource.getSecureMedia()
    }

    override suspend fun moveToSecureFolder(item: MediaItem): Boolean {
        return mediaStoreSource.moveToSecureFolder(item)
    }

    override suspend fun moveOutOfSecureFolder(item: MediaItem): Boolean {
        return mediaStoreSource.moveOutOfSecureFolder(item)
    }

    override fun createDeleteRequest(items: List<MediaItem>): PendingIntent? {
        return mediaStoreSource.createDeleteRequest(items)
    }
}
