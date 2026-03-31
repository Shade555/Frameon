
package com.example.frameon.domain.usecase

import com.example.frameon.domain.model.MediaItem
import com.example.frameon.domain.repository.MediaRepository
import javax.inject.Inject

class MoveToSecureFolderUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(item: MediaItem): Boolean = mediaRepository.moveToSecureFolder(item)
}
