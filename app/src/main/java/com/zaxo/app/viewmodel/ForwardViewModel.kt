package com.zaxo.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaxo.app.data.repository.ChatRepository
import com.zaxo.app.model.Chat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForwardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository
) : ViewModel() {

    private val messageId: String = savedStateHandle["messageId"] ?: ""
    private val sourceChatId: String = savedStateHandle["chatId"] ?: ""

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val chats: StateFlow<List<Chat>> = repository.getAllChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredChats: StateFlow<List<Chat>> = combine(
        chats,
        _searchQuery
    ) { chatList, query ->
        if (query.isBlank()) chatList
        else chatList.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedChatIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedChatIds: StateFlow<Set<String>> = _selectedChatIds.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleChatSelection(chatId: String) {
        _selectedChatIds.update { current ->
            if (chatId in current) current - chatId else current + chatId
        }
    }

    fun getMessageId(): String = messageId
    fun getSourceChatId(): String = sourceChatId

    fun clearSelection() {
        _selectedChatIds.value = emptySet()
    }
}
