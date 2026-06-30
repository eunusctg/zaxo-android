package com.zaxo.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaxo.app.data.repository.ChatRepository
import com.zaxo.app.model.Chat
import com.zaxo.app.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository
) : ViewModel() {

    private val chatId: String = savedStateHandle["chatId"] ?: ""

    val chat: StateFlow<Chat?> = repository.getChatById(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val mediaMessages: StateFlow<List<Message>> = repository.getMediaMessagesForChat(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val starredMessages: StateFlow<List<Message>> = repository.getStarredMessagesForChat(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleMute(muted: Boolean) {
        viewModelScope.launch {
            repository.setMuted(chatId, muted)
        }
    }

    fun togglePin(pinned: Boolean) {
        viewModelScope.launch {
            repository.setPinned(chatId, pinned)
        }
    }

    fun archiveChat() {
        viewModelScope.launch {
            repository.setArchived(chatId, true)
        }
    }
}
