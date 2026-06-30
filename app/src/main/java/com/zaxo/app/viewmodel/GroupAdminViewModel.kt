package com.zaxo.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.zaxo.app.data.repository.ChatRepository
import com.zaxo.app.data.repository.FirestoreGroupRepository
import com.zaxo.app.model.Chat
import com.zaxo.app.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ==================== State Classes ====================

data class MemberInfo(
    val uid: String,
    val displayName: String,
    val photoUrl: String,
    val isAdmin: Boolean,
    val isCreator: Boolean
)

data class GroupAdminState(
    val groupName: String = "",
    val groupDescription: String = "",
    val groupAvatar: String? = null,
    val members: List<MemberInfo> = emptyList(),
    val adminIds: List<String> = emptyList(),
    val createdBy: String = "",
    val isCurrentUserAdmin: Boolean = false,
    val isCurrentUserCreator: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

// ==================== ViewModel ====================

@OptIn(FlowPreview::class)
@HiltViewModel
class GroupAdminViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val groupRepository: FirestoreGroupRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val chatId: String = savedStateHandle["chatId"] ?: ""
    private val currentUserId: String = auth.currentUser?.uid ?: ""

    // Chat data from local DB
    val chat: StateFlow<Chat?> = repository.getChatById(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Unified state
    private val _state = MutableStateFlow(GroupAdminState())
    val state: StateFlow<GroupAdminState> = _state

    // Editing state
    private val _isEditingGroupInfo = MutableStateFlow(false)
    val isEditingGroupInfo: StateFlow<Boolean> = _isEditingGroupInfo

    private val _editGroupName = MutableStateFlow("")
    val editGroupName: StateFlow<String> = _editGroupName

    private val _editGroupDescription = MutableStateFlow("")
    val editGroupDescription: StateFlow<String> = _editGroupDescription

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    // Snackbar
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage

    // Add member dialog
    private val _showAddMemberDialog = MutableStateFlow(false)
    val showAddMemberDialog: StateFlow<Boolean> = _showAddMemberDialog

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _contactSearchResults = MutableStateFlow<List<User>>(emptyList())
    val contactSearchResults: StateFlow<List<User>> = _contactSearchResults

    // F1: Zaxo number lookup state
    private val _zaxoNumberLookupError = MutableStateFlow<String?>(null)
    val zaxoNumberLookupError: StateFlow<String?> = _zaxoNumberLookupError

    private val _zaxoNumberLookupResult = MutableStateFlow<User?>(null)
    val zaxoNumberLookupResult: StateFlow<User?> = _zaxoNumberLookupResult

    // Debounced search (300ms)
    private val _searchTrigger = MutableSharedFlow<String>()

    init {
        // Observe chat data and rebuild state
        viewModelScope.launch {
            chat.collect { chatData ->
                if (chatData != null) {
                    rebuildState(chatData)
                }
            }
        }

        // Debounced search: only fire after 300ms of no new input
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .filter { it.length >= 2 }
                .distinctUntilChanged()
                .collect { query ->
                    performSearch(query)
                }
        }
    }

    // ==================== State Rebuilding ====================

    private fun rebuildState(chatData: Chat) {
        val adminList = chatData.adminIds.split(",").filter { it.isNotEmpty() }
        val isCreator = currentUserId == chatData.createdBy
        val isAdmin = adminList.contains(currentUserId)

        _state.update { s ->
            s.copy(
                groupName = chatData.name,
                groupDescription = chatData.groupDescription,
                groupAvatar = chatData.photoUrl.ifEmpty { null },
                adminIds = adminList,
                createdBy = chatData.createdBy,
                isCurrentUserAdmin = isAdmin,
                isCurrentUserCreator = isCreator,
                isLoading = false
            )
        }

        // Reload member info
        loadMemberInfo(chatData)
    }

    private fun loadMemberInfo(chatData: Chat) {
        val memberIds = chatData.memberIds.split(",").filter { it.isNotEmpty() }
        val adminIds = chatData.adminIds.split(",").filter { it.isNotEmpty() }
        val creatorId = chatData.createdBy

        viewModelScope.launch {
            val memberList = mutableListOf<MemberInfo>()
            for (uid in memberIds) {
                val user = groupRepository.getUserById(uid)
                memberList.add(
                    MemberInfo(
                        uid = uid,
                        displayName = user?.displayName ?: "Unknown User",
                        photoUrl = user?.photoUrl ?: "",
                        isAdmin = adminIds.contains(uid),
                        isCreator = uid == creatorId
                    )
                )
            }
            // Sort: Creator → Admin → Member, then alphabetical within each tier
            _state.update { s ->
                s.copy(
                    members = memberList.sortedWith(
                        compareBy<MemberInfo> {
                            when {
                                it.isCreator -> 0
                                it.isAdmin -> 1
                                else -> 2
                            }
                        }.thenBy { it.displayName.lowercase() }
                    )
                )
            }
        }
    }

    // ==================== Add Member ====================

    fun showAddMemberDialog() {
        _showAddMemberDialog.value = true
    }

    fun hideAddMemberDialog() {
        _showAddMemberDialog.value = false
        _searchQuery.value = ""
        _contactSearchResults.value = emptyList()
        _zaxoNumberLookupError.value = null
        _zaxoNumberLookupResult.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.length < 2) {
            _contactSearchResults.value = emptyList()
        }
    }

    private suspend fun performSearch(query: String) {
        val currentMemberIds = chat.value?.memberIds?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        _contactSearchResults.value = groupRepository.searchUsers(query)
            .filter { it.uid != currentUserId && !currentMemberIds.contains(it.uid) }
    }

    fun addMember(user: User) {
        viewModelScope.launch {
            val result = groupRepository.addMember(chatId, user.uid)
            if (result.isSuccess) {
                // Update local DB
                val chatData = chat.value ?: return@launch
                val currentMembers = chatData.memberIds.split(",").filter { it.isNotEmpty() }.toMutableList()
                currentMembers.add(user.uid)
                repository.updateMembers(chatId, currentMembers.joinToString(","))

                _snackbarMessage.value = "${user.displayName} added to group"
                hideAddMemberDialog()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Failed to add member"
                _snackbarMessage.value = error
            }
        }
    }

    // F1 FIX: Search user by Zaxo Number and add directly
    fun searchByZaxoNumber(number: String) {
        _zaxoNumberLookupError.value = null
        _zaxoNumberLookupResult.value = null

        if (number.isBlank()) {
            _zaxoNumberLookupError.value = "Enter a Zaxo number"
            return
        }

        viewModelScope.launch {
            val result = groupRepository.searchUserByZaxoNumber(number)
            if (result.isSuccess) {
                val user = result.getOrNull()!!
                // Check if already a member
                val currentMemberIds = chat.value?.memberIds?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
                if (user.uid in currentMemberIds) {
                    _zaxoNumberLookupError.value = "${user.displayName} is already in the group"
                } else {
                    _zaxoNumberLookupResult.value = user
                }
            } else {
                _zaxoNumberLookupError.value = result.exceptionOrNull()?.message ?: "User not found"
            }
        }
    }

    fun addMemberFromLookup(user: User) {
        addMember(user)
        _zaxoNumberLookupResult.value = null
        _zaxoNumberLookupError.value = null
    }

    fun clearZaxoLookup() {
        _zaxoNumberLookupError.value = null
        _zaxoNumberLookupResult.value = null
    }

    // ==================== Remove Member ====================

    fun removeMember(memberUid: String, memberName: String) {
        val chatData = chat.value ?: return
        val currentAdmins = chatData.adminIds.split(",").filter { it.isNotEmpty() }

        // F5: Cannot remove the creator
        if (memberUid == chatData.createdBy) {
            _snackbarMessage.value = "Cannot remove the group creator"
            return
        }

        // F3: Cannot remove the last admin
        if (currentAdmins.contains(memberUid) && currentAdmins.size <= 1) {
            _snackbarMessage.value = "Cannot remove the last admin. Promote another member first."
            return
        }

        viewModelScope.launch {
            val result = groupRepository.removeMember(chatId, memberUid)
            if (result.isSuccess) {
                val currentMembers = chatData.memberIds.split(",").filter { it.isNotEmpty() }.toMutableList()
                currentMembers.remove(memberUid)
                repository.updateMembers(chatId, currentMembers.joinToString(","))

                if (currentAdmins.contains(memberUid)) {
                    val updatedAdmins = currentAdmins.toMutableList()
                    updatedAdmins.remove(memberUid)
                    repository.updateAdmins(chatId, updatedAdmins.joinToString(","))
                }

                _snackbarMessage.value = "$memberName removed from group"
            } else {
                _snackbarMessage.value = result.exceptionOrNull()?.message ?: "Failed to remove member"
            }
        }
    }

    // ==================== Promote / Demote ====================

    fun promoteToAdmin(memberUid: String, memberName: String) {
        viewModelScope.launch {
            val result = groupRepository.promoteToAdmin(chatId, memberUid)
            if (result.isSuccess) {
                val chatData = chat.value ?: return@launch
                val currentAdmins = chatData.adminIds.split(",").filter { it.isNotEmpty() }.toMutableList()
                currentAdmins.add(memberUid)
                repository.updateAdmins(chatId, currentAdmins.joinToString(","))

                _snackbarMessage.value = "$memberName is now an admin"
            } else {
                _snackbarMessage.value = result.exceptionOrNull()?.message ?: "Failed to promote"
            }
        }
    }

    fun demoteAdmin(memberUid: String, memberName: String) {
        val chatData = chat.value ?: return

        // F5: Cannot demote the creator
        if (memberUid == chatData.createdBy) {
            _snackbarMessage.value = "Cannot demote the group creator"
            return
        }

        // F4: Cannot demote the last admin
        val currentAdmins = chatData.adminIds.split(",").filter { it.isNotEmpty() }
        if (currentAdmins.size <= 1) {
            _snackbarMessage.value = "Group must have at least one admin"
            return
        }

        viewModelScope.launch {
            val result = groupRepository.demoteFromAdmin(chatId, memberUid)
            if (result.isSuccess) {
                val updatedAdmins = currentAdmins.toMutableList()
                updatedAdmins.remove(memberUid)
                repository.updateAdmins(chatId, updatedAdmins.joinToString(","))

                _snackbarMessage.value = "$memberName is no longer an admin"
            } else {
                _snackbarMessage.value = result.exceptionOrNull()?.message ?: "Failed to demote"
            }
        }
    }

    // ==================== Group Info Editing ====================

    fun startEditingGroupInfo() {
        _isEditingGroupInfo.value = true
        _editGroupName.value = _state.value.groupName
        _editGroupDescription.value = _state.value.groupDescription
    }

    fun cancelEditingGroupInfo() {
        _isEditingGroupInfo.value = false
    }

    fun updateEditGroupName(name: String) {
        _editGroupName.value = name
    }

    fun updateEditGroupDescription(description: String) {
        _editGroupDescription.value = description
    }

    fun saveGroupInfo() {
        val name = _editGroupName.value.trim()
        if (name.isEmpty()) {
            _snackbarMessage.value = "Group name cannot be empty"
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val description = _editGroupDescription.value.trim()
                val result = groupRepository.updateGroupInfo(chatId, name, description)
                if (result.isSuccess) {
                    repository.updateGroupInfo(chatId, name, description)
                    _isEditingGroupInfo.value = false
                    _snackbarMessage.value = "Group info updated"
                } else {
                    _snackbarMessage.value = result.exceptionOrNull()?.message ?: "Failed to update"
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    // ==================== Leave Group ====================

    fun leaveGroup() {
        viewModelScope.launch {
            val result = groupRepository.leaveGroup(chatId, currentUserId)
            if (result.isSuccess) {
                // Update local DB: remove self from members
                val chatData = chat.value ?: return@launch
                val currentMembers = chatData.memberIds.split(",").filter { it.isNotEmpty() }.toMutableList()
                currentMembers.remove(currentUserId)
                repository.updateMembers(chatId, currentMembers.joinToString(","))

                val currentAdmins = chatData.adminIds.split(",").filter { it.isNotEmpty() }.toMutableList()
                if (currentAdmins.contains(currentUserId)) {
                    currentAdmins.remove(currentUserId)
                    // If we were last admin, the repository already promoted someone
                    // We need to figure out who was promoted and update local DB
                    // For now, refresh from Firestore
                    val freshChat = groupRepository.getChatDocument(chatId)
                    if (freshChat != null) {
                        repository.updateAdmins(chatId, freshChat.adminIds)
                        if (currentUserId == chatData.createdBy) {
                            repository.updateChat(freshChat)
                        }
                    } else {
                        repository.updateAdmins(chatId, currentAdmins.joinToString(","))
                    }
                }

                _snackbarMessage.value = "You left the group"
            } else {
                _snackbarMessage.value = result.exceptionOrNull()?.message ?: "Failed to leave group"
            }
        }
    }

    // ==================== Utility ====================

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}
