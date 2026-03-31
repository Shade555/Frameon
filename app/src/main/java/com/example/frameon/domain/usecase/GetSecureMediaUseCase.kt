
package com.example.frameon.domain.usecase

import com.example.frameon.domain.model.MediaItem
import com.example.frameon.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSecureMediaUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(): Flow<List<MediaItem>> = mediaRepository.getSecureMediaItems()
}
