
package com.example.frameon.domain.usecase

import android.app.PendingIntent
import com.example.frameon.domain.model.MediaItem
import com.example.frameon.domain.repository.MediaRepository
import javax.inject.Inject

class CreateDeleteRequestUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    operator fun invoke(items: List<MediaItem>): PendingIntent? = mediaRepository.createDeleteRequest(items)
}
