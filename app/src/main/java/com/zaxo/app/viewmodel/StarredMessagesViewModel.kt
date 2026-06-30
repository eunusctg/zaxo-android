package com.zaxo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.zaxo.app.data.repository.ChatRepository
import com.zaxo.app.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StarredMessageGroup(
    val chatId: String,
    val chatName: String,
    val chatPhotoUrl: String,
    val messages: List<Message>
)

@HiltViewModel
class StarredMessagesViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _starredGroups = MutableStateFlow<List<StarredMessageGroup>>(emptyList())
    val starredGroups: StateFlow<List<StarredMessageGroup>> = _starredGroups.asStateFlow()

    init {
        loadStarredMessages()
    }

    private fun loadStarredMessages() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            repository.getStarredMessagesGlobal(userId).collect { messages ->
                val grouped = messages.groupBy { it.chatId }
                val groups = mutableListOf<StarredMessageGroup>()

                for ((chatId, chatMessages) in grouped) {
                    val chat = repository.getChatByIdSync(chatId) ?: continue
                    groups.add(
                        StarredMessageGroup(
                            chatId = chatId,
                            chatName = chat.name,
                            chatPhotoUrl = chat.photoUrl,
                            messages = chatMessages.sortedByDescending { it.timestamp }
                        )
                    )
                }

                _starredGroups.value = groups.sortedByDescending {
                    it.messages.maxOfOrNull { m -> m.timestamp } ?: 0L
                }
            }
        }
    }

    fun unstarMessage(messageId: String) {
        viewModelScope.launch {
            repository.setStarred(messageId, false)
        }
    }
}
