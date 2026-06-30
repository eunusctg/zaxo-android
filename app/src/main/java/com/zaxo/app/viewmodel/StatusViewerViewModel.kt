package com.zaxo.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zaxo.app.data.repository.ChatRepository
import com.zaxo.app.data.repository.StatusRepository
import com.zaxo.app.model.Message
import com.zaxo.app.model.MessageType
import com.zaxo.app.model.MessageStatus
import com.zaxo.app.model.Status
import com.zaxo.app.model.StatusView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the full-screen status viewer.
 *
 * Responsibilities:
 * - Load active statuses for a specific user
 * - Track viewing progress and mark statuses as viewed (F20: count() query)
 * - Handle mid-view expiry (F18)
 * - Support block detection (F19) via Firestore listener
 */
@HiltViewModel
class StatusViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val statusRepository: StatusRepository,
    private val chatRepository: ChatRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val userId: String = savedStateHandle["userId"] ?: ""

    private val _statuses = MutableStateFlow<List<Status>>(emptyList())
    val statuses: StateFlow<List<Status>> = _statuses.asStateFlow()

    private val _viewCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val viewCounts: StateFlow<Map<String, Int>> = _viewCounts

    private val _isBlocked = MutableStateFlow(false)
    val isBlocked: StateFlow<Boolean> = _isBlocked.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadStatuses()
        checkBlockStatus() // F19: Check block status on init
    }

    /**
     * Load active statuses for the selected user.
     * F18: Filters out expired statuses in real-time.
     */
    fun loadStatuses() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                statusRepository.getUserStatuses(userId).collect { statusList ->
                    // F18: Filter out expired statuses
                    val now = System.currentTimeMillis()
                    val active = statusList.filter { it.expiresAt > now }
                    _statuses.value = active
                    _isLoading.value = false

                    // Load view counts for own statuses
                    val currentUserId = auth.currentUser?.uid ?: ""
                    if (userId == currentUserId) {
                        loadViewCounts(active.map { it.id })
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading statuses for user $userId")
                _isLoading.value = false
            }
        }
    }

    /**
     * Mark a status as viewed by the current user.
     * F20: View count is computed via Firestore count() query, not an integer field.
     */
    fun markAsViewed(statusId: String) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                statusRepository.markAsViewed(
                    statusId = statusId,
                    viewerId = currentUser.uid,
                    viewerName = currentUser.displayName ?: "",
                    viewerPhotoUrl = currentUser.photoUrl?.toString() ?: ""
                )
                Timber.d("Status $statusId marked as viewed")
            } catch (e: Exception) {
                Timber.e(e, "Failed to mark status $statusId as viewed")
            }
        }
    }

    /**
     * Load view counts for the given status IDs (for own statuses).
     * F20: Uses count() aggregation query.
     */
    private fun loadViewCounts(statusIds: List<String>) {
        viewModelScope.launch {
            val counts = mutableMapOf<String, Int>()
            for (id in statusIds) {
                try {
                    counts[id] = statusRepository.getViewCount(id)
                } catch (e: Exception) {
                    counts[id] = 0
                }
            }
            _viewCounts.value = counts
        }
    }

    /**
     * F19: Check if the current user has been blocked by the status owner.
     * Listens to Firestore for the block document in real-time.
     * If the document exists, the current user is blocked.
     */
    fun checkBlockStatus() {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users")
                    .document(userId)
                    .collection("blockedUsers")
                    .document(currentUser.uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Timber.e(error, "Error checking block status")
                            return@addSnapshotListener
                        }
                        _isBlocked.value = snapshot?.exists() == true
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set up block status listener")
            }
        }
    }

    /**
     * C.3: Send a reply to a status as a private message.
     * Creates or finds existing chat with the status author, then sends a message
     * with a statusReply reference.
     */
    fun sendStatusReply(
        statusId: String,
        statusAuthorId: String,
        statusAuthorName: String,
        replyText: String
    ) {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                val currentUserId = currentUser.uid

                // Create or find chat with the status author
                val chatId = chatRepository.getOrCreateChat(
                    recipientId = statusAuthorId,
                    recipientName = statusAuthorName,
                    currentUserId = currentUserId
                )

                // Send message with statusReply reference
                val message = Message(
                    id = java.util.UUID.randomUUID().toString(),
                    chatId = chatId,
                    senderId = currentUserId,
                    senderName = currentUser.displayName ?: "",
                    senderPhotoUrl = currentUser.photoUrl?.toString() ?: "",
                    content = replyText,
                    type = MessageType.TEXT,
                    timestamp = System.currentTimeMillis(),
                    status = MessageStatus.SENDING,
                    replyToId = statusId,
                    replyToContent = "Status reply",
                    replyToSender = statusAuthorName
                )

                chatRepository.sendMessage(message)
                Timber.d("Status reply sent to $statusAuthorName for status $statusId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to send status reply")
            }
        }
    }
}
