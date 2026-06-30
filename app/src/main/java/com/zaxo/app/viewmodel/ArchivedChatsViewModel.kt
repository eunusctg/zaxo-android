package com.zaxo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaxo.app.data.repository.ChatRepository
import com.zaxo.app.model.Chat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArchivedChatsViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    val archivedChats: StateFlow<List<Chat>> = repository.getArchivedChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun unarchiveChat(chatId: String) {
        viewModelScope.launch {
            repository.setArchived(chatId, false)
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            repository.deleteChatById(chatId)
            repository.deleteMessagesForChat(chatId)
        }
    }
}
