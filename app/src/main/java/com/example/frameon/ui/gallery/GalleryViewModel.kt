
package com.example.frameon.ui.gallery

import android.app.PendingIntent
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.frameon.domain.model.MediaItem
import com.example.frameon.domain.usecase.CreateDeleteRequestUseCase
import com.example.frameon.domain.usecase.GetMediaUseCase
import com.example.frameon.domain.usecase.GetSecureMediaUseCase
import com.example.frameon.domain.usecase.MoveOutOfSecureFolderUseCase
import com.example.frameon.domain.usecase.MoveToSecureFolderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@Immutable
sealed interface GalleryState {
    data object Loading : GalleryState
    data class Success(val media: Map<String, List<MediaItem>>) : GalleryState
    data class Error(val message: String) : GalleryState
}

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val getMediaUseCase: GetMediaUseCase,
    private val getSecureMediaUseCase: GetSecureMediaUseCase,
    private val moveToSecureFolderUseCase: MoveToSecureFolderUseCase,
    private val moveOutOfSecureFolderUseCase: MoveOutOfSecureFolderUseCase,
    private val createDeleteRequestUseCase: CreateDeleteRequestUseCase
) : ViewModel() {

    private val _galleryState = MutableStateFlow<GalleryState>(GalleryState.Loading)
    val galleryState: StateFlow<GalleryState> = _galleryState.asStateFlow()

    private val _selectedItems = mutableStateListOf<MediaItem>()
    val selectedItems: List<MediaItem> = _selectedItems

    private val _isSecureMode = MutableStateFlow(false)
    val isSecureMode: StateFlow<Boolean> = _isSecureMode.asStateFlow()

    private val _deleteRequest = MutableStateFlow<PendingIntent?>(null)
    val deleteRequest: StateFlow<PendingIntent?> = _deleteRequest.asStateFlow()

    private var loadMediaJob: Job? = null

    val inSelectionMode: Boolean
        get() = selectedItems.isNotEmpty()

    fun toggleSelection(item: MediaItem) {
        if (selectedItems.contains(item)) {
            _selectedItems.remove(item)
        } else {
            _selectedItems.add(item)
        }
    }

    fun clearSelection() {
        _selectedItems.clear()
    }

    fun enterSecureMode() {
        _isSecureMode.value = true
        loadMedia()
    }

    fun exitSecureMode() {
        _isSecureMode.value = false
        loadMedia()
    }

    fun moveSelectedToSecure() {
        viewModelScope.launch {
            val itemsToMove = _selectedItems.toList()
            clearSelection()
            
            // First, copy items to secure folder
            itemsToMove.forEach { moveToSecureFolderUseCase(it) }
            
            // For Android 11+, we need to request deletion permission
            val pendingIntent = createDeleteRequestUseCase(itemsToMove)
            if (pendingIntent != null) {
                _deleteRequest.value = pendingIntent
            } else {
                // For older versions, moveToSecureFolder already handled deletion
                loadMedia()
            }
        }
    }

    fun consumeDeleteRequest() {
        _deleteRequest.value = null
        loadMedia() // Refresh to show items are gone
    }

    fun moveSelectedOutOfSecure() {
        viewModelScope.launch {
            val itemsToMove = _selectedItems.toList()
            clearSelection()
            itemsToMove.forEach { moveOutOfSecureFolderUseCase(it) }
            if (_isSecureMode.value) loadMedia()
        }
    }

    init {
        loadMedia()
    }

    private fun loadMedia() {
        loadMediaJob?.cancel()
        loadMediaJob = viewModelScope.launch {
            val useCaseFlow = if (_isSecureMode.value) getSecureMediaUseCase() else getMediaUseCase()
            useCaseFlow
                .onStart { _galleryState.value = GalleryState.Loading }
                .catch { e -> _galleryState.value = GalleryState.Error(e.message ?: "An unknown error occurred") }
                .flowOn(Dispatchers.Default)
                .collect { media ->
                    val groupedMedia = media.groupBy {
                        val calendar = Calendar.getInstance()
                        calendar.timeInMillis = it.dateAdded * 1000
                        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
                    }
                    _galleryState.value = GalleryState.Success(groupedMedia)
                }
        }
    }
}
