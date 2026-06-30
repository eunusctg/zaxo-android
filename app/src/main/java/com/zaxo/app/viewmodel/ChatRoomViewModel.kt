package com.zaxo.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zaxo.app.data.repository.ChatRepository
import com.zaxo.app.data.repository.FirestoreMessageRepository
import com.zaxo.app.model.Chat
import com.zaxo.app.model.Message
import com.zaxo.app.model.MessageStatus
import com.zaxo.app.model.MessageType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val firestoreRepo: FirestoreMessageRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val chatId: String = savedStateHandle["chatId"] ?: ""
    private val currentUserId: String = auth.currentUser?.uid ?: ""

    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()

    private val _replyTo = MutableStateFlow<Message?>(null)
    val replyTo: StateFlow<Message?> = _replyTo.asStateFlow()

    private val _selectedMessages = MutableStateFlow<Set<String>>(emptySet())
    val selectedMessages: StateFlow<Set<String>> = _selectedMessages.asStateFlow()

    val selectionMode: StateFlow<Boolean> = _selectedMessages.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val chat: StateFlow<Chat?> = repository.getChatById(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<Message>> = repository.getMessagesForChat(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // F9: Read receipt privacy — check if recipient has read receipts enabled
    private val _recipientReadReceiptsEnabled = MutableStateFlow(true)
    val recipientReadReceiptsEnabled: StateFlow<Boolean> = _recipientReadReceiptsEnabled.asStateFlow()

    init {
        // Mark messages as read when entering chat
        viewModelScope.launch {
            markMessagesAsRead()
        }
        // Mark sent messages as delivered
        viewModelScope.launch {
            markMessagesAsDelivered()
        }
        // F7: Listen for status updates from Firestore
        listenToStatusUpdates()
        // F9: Check recipient's read receipt privacy setting
        checkRecipientReadReceiptsSetting()
    }

    fun updateMessageText(text: String) {
        _messageText.value = text
    }

    fun setReplyTo(message: Message?) {
        _replyTo.value = message
    }

    fun toggleSelectMessage(messageId: String) {
        _selectedMessages.update { current ->
            if (messageId in current) current - messageId
            else current + messageId
        }
    }

    fun clearSelection() {
        _selectedMessages.value = emptySet()
    }

    fun selectAll() {
        viewModelScope.launch {
            val allIds = messages.value.map { it.id }.toSet()
            _selectedMessages.value = allIds
        }
    }

    fun deleteSelectedMessages() {
        viewModelScope.launch {
            val ids = _selectedMessages.value.toList()
            repository.softDeleteMessages(ids)
            _selectedMessages.value = emptySet()
        }
    }

    fun starSelectedMessages() {
        viewModelScope.launch {
            _selectedMessages.value.forEach { messageId ->
                repository.setStarred(messageId, true)
            }
            _selectedMessages.value = emptySet()
        }
    }

    fun sendMessage() {
        val text = _messageText.value.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            val replyToMsg = _replyTo.value
            val senderName = auth.currentUser?.displayName ?: "You"
            val senderPhotoUrl = auth.currentUser?.photoUrl?.toString() ?: ""

            val savedMessage = firestoreRepo.sendEncryptedMessage(
                chatId = chatId,
                senderId = currentUserId,
                senderName = senderName,
                senderPhotoUrl = senderPhotoUrl,
                encryptedContent = text,
                replyToId = replyToMsg?.id ?: "",
                replyToContent = replyToMsg?.content ?: "",
                replyToSender = replyToMsg?.senderName ?: ""
            )
            // Insert to local Room
            repository.insertMessage(savedMessage)
            _messageText.value = ""
            _replyTo.value = null
            repository.updateLastMessage(chatId, text, System.currentTimeMillis(), senderName)
        }
    }

    /**
     * Send voice message with waveform data.
     * F5: Waveform is downsampled to max 100 amplitude samples.
     * F2: On upload failure, message is marked with syncState="pending" for retry.
     */
    fun sendVoiceMessage(mediaUrl: String, duration: Long, waveform: List<Float>) {
        viewModelScope.launch {
            val senderName = auth.currentUser?.displayName ?: "You"
            val senderPhotoUrl = auth.currentUser?.photoUrl?.toString() ?: ""

            // F5: Downsample waveform to max 100 samples
            val finalWaveform = if (waveform.size > 100) {
                waveform.chunked(waveform.size / 100).map { chunk -> chunk.average().toFloat() }
            } else {
                waveform
            }
            val finalWaveformStr = finalWaveform.joinToString(",") { String.format("%.3f", it) }

            val replyToMsg = _replyTo.value
            try {
                val savedMessage = firestoreRepo.sendVoiceMessage(
                    chatId = chatId,
                    senderId = currentUserId,
                    senderName = senderName,
                    senderPhotoUrl = senderPhotoUrl,
                    mediaUrl = mediaUrl,
                    duration = duration,
                    waveform = finalWaveformStr,
                    replyToId = replyToMsg?.id ?: "",
                    replyToContent = replyToMsg?.content ?: "",
                    replyToSender = replyToMsg?.senderName ?: ""
                )
                repository.insertMessage(savedMessage)
                _replyTo.value = null
                repository.updateLastMessage(chatId, "🎤 Voice message", System.currentTimeMillis(), senderName)
            } catch (e: Exception) {
                // F2: Store locally with syncState="pending" for retry
                Timber.e(e, "Voice message send failed — queuing for retry")
                val pendingMessage = Message(
                    id = "voice_pending_${System.currentTimeMillis()}",
                    chatId = chatId,
                    senderId = currentUserId,
                    senderName = senderName,
                    senderPhotoUrl = senderPhotoUrl,
                    content = "🎤 Voice message",
                    type = MessageType.VOICE,
                    mediaUrl = mediaUrl,
                    mediaDuration = duration,
                    waveform = finalWaveformStr,
                    timestamp = System.currentTimeMillis(),
                    status = MessageStatus.SENDING,
                    syncState = "pending",
                    replyToId = replyToMsg?.id ?: "",
                    replyToContent = replyToMsg?.content ?: "",
                    replyToSender = replyToMsg?.senderName ?: ""
                )
                repository.insertMessage(pendingMessage)
                _replyTo.value = null
                repository.updateLastMessage(chatId, "🎤 Voice message (sending...)", System.currentTimeMillis(), senderName)
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.softDeleteMessage(messageId)
        }
    }

    fun starMessage(messageId: String, starred: Boolean) {
        viewModelScope.launch {
            repository.setStarred(messageId, starred)
        }
    }

    fun forwardMessage(
        messageId: String,
        targetChatId: String,
        senderId: String,
        senderName: String,
        senderPhotoUrl: String
    ) {
        viewModelScope.launch {
            val message = repository.getMessageById(messageId) ?: return@launch
            val targetChat = repository.getChatByIdSync(targetChatId) ?: return@launch

            val savedMessage = firestoreRepo.sendEncryptedMessage(
                chatId = targetChatId,
                senderId = senderId,
                senderName = senderName,
                senderPhotoUrl = senderPhotoUrl,
                encryptedContent = message.content,
                type = message.type,
                mediaUrl = message.mediaUrl,
                forwardedFrom = message.senderName,
                isForwarded = true
            )
            repository.insertMessage(savedMessage)
            repository.updateLastMessage(
                targetChatId,
                "Forwarded message",
                System.currentTimeMillis(),
                senderName
            )
        }
    }

    fun addReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            val message = repository.getMessageById(messageId) ?: return@launch
            val updatedReactions = if (message.reactions.isEmpty()) {
                """{"$emoji":"$currentUserId"}"""
            } else {
                message.reactions
            }
            repository.updateMessage(message.copy(reactions = updatedReactions))
        }
    }

    /**
     * Read Receipts: Mark all messages in this chat as read.
     * F9: Respects privacy setting — if recipient has readReceipts disabled, 
     * we still mark locally but don't update Firestore to READ.
     */
    private suspend fun markMessagesAsRead() {
        try {
            // Always mark locally
            repository.markAllAsRead(chatId, currentUserId)

            // F9: Only update remote status if recipient allows read receipts
            if (_recipientReadReceiptsEnabled.value) {
                firestoreRepo.markAsRead(chatId, currentUserId)
                repository.updateStatusForSender(chatId, currentUserId, MessageStatus.READ)
            } else {
                // Even if recipient disabled read receipts, mark as delivered
                repository.updateStatusForSender(chatId, currentUserId, MessageStatus.DELIVERED)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to mark messages as read — offline")
            // F8: Offline — Room queue will sync later
        }
    }

    /**
     * Mark delivered messages from current user.
     */
    private suspend fun markMessagesAsDelivered() {
        try {
            firestoreRepo.markAsDelivered(chatId, currentUserId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to mark messages as delivered — offline")
            // F8: Offline — will sync on reconnect
        }
    }

    /**
     * F7: Listen for message status updates from Firestore in real-time.
     * Updates local Room database when status changes occur (e.g., delivered → read).
     */
    private fun listenToStatusUpdates() {
        viewModelScope.launch {
            try {
                firestoreRepo.listenToStatusUpdates(chatId).collect { updatedMessages ->
                    for (message in updatedMessages) {
                        // Update local message status if it's changed
                        val localMessage = repository.getMessageById(message.id)
                        if (localMessage != null && localMessage.status != message.status) {
                            repository.updateMessageStatus(message.id, message.status)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error listening to status updates")
            }
        }
    }

    /**
     * F9: Check the recipient's read receipt privacy setting.
     * For 1:1 chats, we check the recipient's profile.
     * For group chats, read receipts are always enabled.
     */
    private fun checkRecipientReadReceiptsSetting() {
        viewModelScope.launch {
            try {
                val chatData = repository.getChatByIdSync(chatId) ?: return@launch
                if (chatData.isGroup) {
                    // Group chats: read receipts always enabled
                    _recipientReadReceiptsEnabled.value = true
                    return@launch
                }

                // For 1:1 chat, check recipient's setting
                val recipientId = chatData.recipientId
                if (recipientId.isNotEmpty()) {
                    firestore.collection("users")
                        .document(recipientId)
                        .get()
                        .addOnSuccessListener { document ->
                            val enabled = document?.getBoolean("readReceiptsEnabled") ?: true
                            _recipientReadReceiptsEnabled.value = enabled
                        }
                        .addOnFailureListener { e ->
                            Timber.e(e, "Failed to check recipient read receipt setting")
                            // Default to enabled on failure
                            _recipientReadReceiptsEnabled.value = true
                        }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking read receipt privacy")
            }
        }
    }

    fun isOwnMessage(message: Message): Boolean {
        return message.senderId == currentUserId
    }
}
