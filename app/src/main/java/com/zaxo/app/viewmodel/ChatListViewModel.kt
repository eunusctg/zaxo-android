package com.zaxo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zaxo.app.data.repository.ChatRepository
import com.zaxo.app.model.Chat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val chats: StateFlow<List<Chat>> = repository.getAllChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredChats: StateFlow<List<Chat>> = combine(
        chats,
        _searchQuery
    ) { chatList, query ->
        if (query.isBlank()) chatList
        else chatList.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.lastMessage.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun archiveChat(chatId: String) {
        viewModelScope.launch {
            repository.setArchived(chatId, true)
        }
    }

    fun unarchiveChat(chatId: String) {
        viewModelScope.launch {
            repository.setArchived(chatId, false)
        }
    }

    fun pinChat(chatId: String) {
        viewModelScope.launch {
            repository.setPinned(chatId, true)
        }
    }

    fun unpinChat(chatId: String) {
        viewModelScope.launch {
            repository.setPinned(chatId, false)
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            repository.deleteChatById(chatId)
            repository.deleteMessagesForChat(chatId)
        }
    }

    fun muteChat(chatId: String, muted: Boolean) {
        viewModelScope.launch {
            repository.setMuted(chatId, muted)
        }
    }

    // F57: Force sync from Firestore for pull-to-refresh
    fun syncFromFirestore() {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                val snapshot = firestore.collection("users")
                    .document(userId)
                    .collection("chats")
                    .get()
                    .await()
                // Sync will happen automatically via Firestore real-time listeners
                // This triggers a manual fetch to ensure latest data
                Timber.d("Synced chats from Firestore: ${snapshot.documents.size} chats")
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync chats from Firestore")
            }
        }
    }
}
