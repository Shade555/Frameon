
package com.example.frameon.domain.usecase

import com.example.frameon.domain.model.MediaItem
import com.example.frameon.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for retrieving media items.
 * This encapsulates the business logic for getting media and depends on the MediaRepository interface.
 */
class GetMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(): Flow<List<MediaItem>> {
        return mediaRepository.getMediaItems()
    }
}
