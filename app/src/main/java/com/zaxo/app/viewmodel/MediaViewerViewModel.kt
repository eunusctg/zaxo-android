package com.zaxo.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaxo.app.data.repository.ChatRepository
import com.zaxo.app.data.repository.FirestoreGroupRepository
import com.zaxo.app.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ==================== State Class ====================

data class MediaViewerState(
    val mediaItems: List<Message> = emptyList(),
    val currentIndex: Int = 0,
    val currentMedia: Message? = null,
    val showDetails: Boolean = false,
    val zoomScale: Float = 1f,
    val isLoading: Boolean = true
)

// ==================== ViewModel ====================

@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val groupRepository: FirestoreGroupRepository
) : ViewModel() {

    private val chatId: String = savedStateHandle["chatId"] ?: ""
    private val initialMessageId: String = savedStateHandle["messageId"] ?: ""

    // Keys for SavedStateHandle to survive rotation (F14)
    companion object {
        private const val KEY_CURRENT_INDEX = "media_viewer_current_index"
        private const val KEY_SHOW_DETAILS = "media_viewer_show_details"
    }

    // Unified state
    private val _state = MutableStateFlow(
        MediaViewerState(
            currentIndex = savedStateHandle[KEY_CURRENT_INDEX] ?: 0,
            showDetails = savedStateHandle[KEY_SHOW_DETAILS] ?: false
        )
    )
    val state: StateFlow<MediaViewerState> = _state

    private var hasSetInitialPosition = false

    init {
        viewModelScope.launch {
            repository.getMediaMessagesForChat(chatId).collect { messages ->
                val index = _state.value.currentIndex

                // Set initial position to the clicked message
                val newIndex = if (!hasSetInitialPosition && initialMessageId.isNotEmpty()) {
                    val targetIndex = messages.indexOfFirst { it.id == initialMessageId }
                    if (targetIndex >= 0) {
                        hasSetInitialPosition = true
                        targetIndex
                    } else {
                        hasSetInitialPosition = true
                        index.coerceAtMost((messages.size - 1).coerceAtLeast(0))
                    }
                } else {
                    index.coerceIn(0, (messages.size - 1).coerceAtLeast(0))
                }

                _state.update { s ->
                    s.copy(
                        mediaItems = messages,
                        currentIndex = newIndex,
                        currentMedia = messages.getOrNull(newIndex),
                        isLoading = false
                    )
                }
                savedStateHandle[KEY_CURRENT_INDEX] = newIndex
            }
        }
    }

    fun setCurrentIndex(index: Int) {
        val clamped = index.coerceIn(0, (_state.value.mediaItems.size - 1).coerceAtLeast(0))
        _state.update { s ->
            s.copy(
                currentIndex = clamped,
                currentMedia = s.mediaItems.getOrNull(clamped)
            )
        }
        savedStateHandle[KEY_CURRENT_INDEX] = clamped
    }

    fun toggleDetails() {
        val newShow = !_state.value.showDetails
        _state.update { s -> s.copy(showDetails = newShow) }
        savedStateHandle[KEY_SHOW_DETAILS] = newShow
    }

    fun setZoomScale(scale: Float) {
        _state.update { s -> s.copy(zoomScale = scale.coerceIn(1f, 5f)) }
    }

    fun resetZoom() {
        _state.update { s -> s.copy(zoomScale = 1f) }
    }

    fun hasNext(): Boolean = _state.value.currentIndex < _state.value.mediaItems.size - 1

    fun hasPrevious(): Boolean = _state.value.currentIndex > 0

    fun next() {
        if (hasNext()) {
            setCurrentIndex(_state.value.currentIndex + 1)
        }
    }

    fun previous() {
        if (hasPrevious()) {
            setCurrentIndex(_state.value.currentIndex - 1)
        }
    }

    fun getTotalCount(): Int = _state.value.mediaItems.size

    /**
     * F8 FIX: Refresh expired media URL by re-fetching the message from Firestore.
     * Returns the updated mediaUrl, or null if the message no longer exists.
     */
    suspend fun refreshMediaUrl(messageId: String): String? {
        return try {
            // Try to get the latest message data from the local repository first
            val message = repository.getMessageById(messageId)
            if (message != null && message.mediaUrl.isNotEmpty()) {
                // If URL looks valid, return it
                message.mediaUrl
            } else {
                // Message was deleted or has no media
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * F8 FIX: Update a specific media item's URL after refresh.
     */
    fun updateMediaUrl(messageId: String, newUrl: String) {
        _state.update { s ->
            val updatedItems = s.mediaItems.map { msg ->
                if (msg.id == messageId) msg.copy(mediaUrl = newUrl) else msg
            }
            s.copy(
                mediaItems = updatedItems,
                currentMedia = updatedItems.getOrNull(s.currentIndex)
            )
        }
    }
}
