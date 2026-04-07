
package com.example.frameon.domain.model

import android.net.Uri

/**
 * Represents a single media item (image or video) in the gallery.
 * This is a plain data class that resides in the domain layer.
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val path: String?,
    val mediaType: MediaType
)
