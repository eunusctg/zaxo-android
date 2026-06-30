package com.zaxo.app.viewmodel

import android.icu.text.BreakIterator
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.zaxo.app.data.dao.MutedStatusDao
import com.zaxo.app.data.repository.ChatRepository
import com.zaxo.app.data.repository.StatusRepository
import com.zaxo.app.model.MutedStatus
import com.zaxo.app.model.Status
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ───────────────────────── UI Models ─────────────────────────

/**
 * Represents a grouped status item for the contacts list.
 * Each item contains all active statuses for a single user,
 * along with whether the current user has unviewed statuses from them.
 */
data class ContactStatusItem(
    val userId: String,
    val userName: String,
    val userPhotoUrl: String,
    val statuses: List<Status>,
    val hasUnviewed: Boolean
)

// ───────────────────────── ViewModel ─────────────────────────

@HiltViewModel
class StatusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val statusRepository: StatusRepository,
    private val auth: FirebaseAuth,
    private val chatRepository: ChatRepository,
    private val mutedStatusDao: MutedStatusDao
) : ViewModel() {

    companion object {
        private const val MAX_TEXT_STATUS_GRAPHEME_COUNT = 700
    }

    /** Guard to prevent double-tap status creation (F21) */
    @Volatile
    private var isSending = false

    // ───────────────────────── State Flows ─────────────────────────

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Current user's active statuses */
    private val _myStatuses = MutableStateFlow<List<Status>>(emptyList())
    val myStatuses: StateFlow<List<Status>> = _myStatuses.asStateFlow()

    /** Contact statuses grouped by userId */
    private val _contactStatuses = MutableStateFlow<List<ContactStatusItem>>(emptyList())
    val contactStatuses: StateFlow<List<ContactStatusItem>> = _contactStatuses.asStateFlow()

    /** Muted contact IDs — observed by StatusScreen for MUTED section */
    private val _mutedContactIds = MutableStateFlow<Set<String>>(emptySet())
    val mutedContactIds: StateFlow<Set<String>> = _mutedContactIds.asStateFlow()

    // ───────────────────────── Init ─────────────────────────

    init {
        refresh()
        loadMutedContacts()
    }

    // ───────────────────────── Load Data ─────────────────────────

    /** Load the current user's statuses from Room. */
    fun loadMyStatuses() {
        viewModelScope.launch {
            try {
                statusRepository.getMyStatuses().collect { statuses ->
                    _myStatuses.value = statuses
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading my statuses")
                _error.value = "Failed to load your statuses"
            }
        }
    }

    /**
     * Load all active statuses, group by userId, enrich with user data,
     * and determine which contacts have unviewed statuses.
     */
    fun loadContactStatuses() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                statusRepository.listenToContactStatuses().collect { allStatuses ->
                    val currentUserId = auth.currentUser?.uid ?: ""
                    // Filter out current user's own statuses
                    val contactStatusList = allStatuses.filter { it.userId != currentUserId }

                    // Group by userId
                    val grouped = contactStatusList.groupBy { it.userId }

                    val items = mutableListOf<ContactStatusItem>()
                    for ((userId, statuses) in grouped) {
                        if (statuses.isEmpty()) continue

                        // Take user info from the most recent status
                        val latestStatus = statuses.first()
                        val userName = latestStatus.userName
                        val userPhotoUrl = latestStatus.userPhotoUrl

                        // Check if current user has unviewed statuses from this contact
                        val hasUnviewed = statuses.any { !it.isViewed }

                        // Alternative: use the DAO for precise unviewed count
                        val unviewedCount = try {
                            statusRepository.getUnviewedCount(userId)
                        } catch (e: Exception) {
                            0
                        }

                        items.add(
                            ContactStatusItem(
                                userId = userId,
                                userName = userName,
                                userPhotoUrl = userPhotoUrl,
                                statuses = statuses,
                                hasUnviewed = hasUnviewed || unviewedCount > 0
                            )
                        )
                    }

                    // Sort: contacts with unviewed statuses first, then by most recent status
                    _contactStatuses.value = items.sortedWith(
                        compareByDescending<ContactStatusItem> { it.hasUnviewed }
                            .thenByDescending { it.statuses.maxOfOrNull { s -> s.createdAt } }
                    )

                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading contact statuses")
                _error.value = "Failed to load contact statuses"
                _isLoading.value = false
            }
        }
    }

    // ───────────────────────── Create Status ─────────────────────────

    /**
     * Create a photo status.
     * Protected by isSending guard (F21) to prevent double-tap send.
     */
    fun createPhotoStatus(mediaUrl: String) {
        if (isSending) {
            Timber.w("Status creation already in progress — ignoring double-tap")
            return
        }

        isSending = true
        viewModelScope.launch {
            try {
                val result = statusRepository.createPhotoStatus(mediaUrl)
                result.onSuccess { status ->
                    Timber.d("Photo status created: ${status.id}")
                    // Refresh my statuses to include the new one
                    loadMyStatuses()
                }.onFailure { e ->
                    Timber.e(e, "Failed to create photo status")
                    _error.value = "Failed to create photo status: ${e.message}"
                }
            } finally {
                isSending = false
            }
        }
    }

    /**
     * Create a text status.
     * Validates text length using BreakIterator for grapheme counting (F22).
     * Protected by isSending guard (F21) to prevent double-tap send.
     */
    fun createTextStatus(text: String, backgroundColor: String, fontFamily: String) {
        // F22: Validate text length using BreakIterator for accurate grapheme counting
        val graphemeCount = countGraphemes(text)
        if (graphemeCount == 0) {
            _error.value = "Text status cannot be empty"
            return
        }
        if (graphemeCount > MAX_TEXT_STATUS_GRAPHEME_COUNT) {
            _error.value = "Text exceeds maximum of $MAX_TEXT_STATUS_GRAPHEME_COUNT characters " +
                "(found $graphemeCount)"
            return
        }

        if (isSending) {
            Timber.w("Status creation already in progress — ignoring double-tap")
            return
        }

        isSending = true
        viewModelScope.launch {
            try {
                val result = statusRepository.createTextStatus(text, backgroundColor, fontFamily)
                result.onSuccess { status ->
                    Timber.d("Text status created: ${status.id}")
                    // Refresh my statuses to include the new one
                    loadMyStatuses()
                }.onFailure { e ->
                    Timber.e(e, "Failed to create text status")
                    _error.value = "Failed to create text status: ${e.message}"
                }
            } finally {
                isSending = false
            }
        }
    }

    // ───────────────────────── View Status ─────────────────────────

    /** Mark a status as viewed by the current user. */
    fun viewStatus(statusId: String) {
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
                _error.value = "Failed to mark status as viewed"
            }
        }
    }

    // ───────────────────────── Delete Status ─────────────────────────

    /** Delete a status by ID. */
    fun deleteStatus(statusId: String) {
        viewModelScope.launch {
            try {
                statusRepository.deleteStatus(statusId)
                Timber.d("Status $statusId deleted")
                // Refresh my statuses after deletion
                loadMyStatuses()
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete status $statusId")
                _error.value = "Failed to delete status"
            }
        }
    }

    // ───────────────────────── Refresh / Cleanup ─────────────────────────

    /** Reload all status data. */
    fun refresh() {
        loadMyStatuses()
        loadContactStatuses()
    }

    /** Clean up expired statuses from Room. */
    fun deleteExpired() {
        viewModelScope.launch {
            try {
                statusRepository.deleteExpiredStatuses()
                Timber.d("Expired statuses cleaned up")
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete expired statuses")
            }
        }
    }

    /** Clear the current error state. */
    fun clearError() {
        _error.value = null
    }

    // ───────────────────────── Mute/Unmute (C.4) ─────────────────────────

    /**
     * Load muted contact IDs from Room.
     * F33: Mute offline → Queue in Room, sync on reconnect.
     * F34: Track by UID, not Zaxo Number.
     */
    private fun loadMutedContacts() {
        viewModelScope.launch {
            try {
                mutedStatusDao.getAllMutedStatuses().collect { mutedList ->
                    _mutedContactIds.value = mutedList.map { it.mutedUserId }.toSet()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load muted contacts")
            }
        }
    }

    /**
     * Mute a contact's status updates.
     * Local-first: Room update (instant UI), Firestore sync (background).
     */
    fun muteContact(userId: String, userName: String) {
        viewModelScope.launch {
            try {
                val mutedStatus = MutedStatus(
                    id = java.util.UUID.randomUUID().toString(),
                    mutedUserId = userId,
                    mutedUserName = userName,
                    mutedAt = System.currentTimeMillis()
                )
                mutedStatusDao.insertMutedStatus(mutedStatus)
                Timber.d("Muted status updates from $userName ($userId)")

                // Firestore sync in background
                try {
                    val currentUserId = auth.currentUser?.uid ?: ""
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(currentUserId)
                        .collection("mutedStatuses")
                        .document(userId)
                        .set(mapOf(
                            "mutedUserId" to userId,
                            "mutedUserName" to userName,
                            "mutedAt" to System.currentTimeMillis()
                        ))
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync mute to Firestore")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to mute contact")
                _error.value = "Failed to mute contact"
            }
        }
    }

    /**
     * Unmute a contact's status updates.
     */
    fun unmuteContact(userId: String) {
        viewModelScope.launch {
            try {
                mutedStatusDao.deleteMutedStatus(userId)
                Timber.d("Unmuted status updates from $userId")

                // Firestore sync in background
                try {
                    val currentUserId = auth.currentUser?.uid ?: ""
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(currentUserId)
                        .collection("mutedStatuses")
                        .document(userId)
                        .delete()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync unmute to Firestore")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to unmute contact")
                _error.value = "Failed to unmute contact"
            }
        }
    }

    // ───────────────────────── Helpers ─────────────────────────

    /**
     * F22: Count grapheme clusters using BreakIterator.
     * This correctly handles complex Unicode sequences (emoji with skin tones,
     * ZWJ sequences, combining characters) where a single user-perceived
     * character may span multiple UTF-16 code units or even multiple code points.
     */
    private fun countGraphemes(text: String): Int {
        if (text.isEmpty()) return 0

        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)

        var count = 0
        while (iterator.next() != BreakIterator.DONE) {
            count++
        }
        return count
    }
}
