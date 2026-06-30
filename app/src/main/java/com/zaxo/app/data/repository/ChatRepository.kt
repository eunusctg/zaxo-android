package com.zaxo.app.data.repository

import com.zaxo.app.data.dao.ChatDao
import com.zaxo.app.data.dao.MessageDao
import com.zaxo.app.model.Chat
import com.zaxo.app.model.Message
import com.zaxo.app.model.MessageStatus
import com.zaxo.app.model.ChatSearchGroup
import com.zaxo.app.model.SearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao
) {
    fun getAllChats(): Flow<List<Chat>> = chatDao.getAllChats()

    fun getArchivedChats(): Flow<List<Chat>> = chatDao.getArchivedChats()

    fun getChatById(chatId: String): Flow<Chat?> = chatDao.getChatByIdFlow(chatId)

    suspend fun getChatByIdSync(chatId: String): Chat? = chatDao.getChatById(chatId)

    suspend fun getChatByRecipientId(recipientId: String): Chat? = chatDao.getChatByRecipientId(recipientId)

    fun searchChats(query: String): Flow<List<Chat>> = chatDao.searchChats(query)

    suspend fun insertChat(chat: Chat) = chatDao.insertChat(chat)

    suspend fun updateChat(chat: Chat) = chatDao.updateChat(chat)

    suspend fun setArchived(chatId: String, archived: Boolean) = chatDao.setArchived(chatId, archived)

    suspend fun setPinned(chatId: String, pinned: Boolean) = chatDao.setPinned(chatId, pinned)

    suspend fun setMuted(chatId: String, muted: Boolean) = chatDao.setMuted(chatId, muted)

    suspend fun clearUnreadCount(chatId: String) = chatDao.clearUnreadCount(chatId)

    suspend fun updateLastMessage(chatId: String, message: String, time: Long, sender: String) =
        chatDao.updateLastMessage(chatId, message, time, sender)

    suspend fun setTyping(chatId: String, typing: Boolean) = chatDao.setTyping(chatId, typing)

    suspend fun deleteChat(chat: Chat) = chatDao.deleteChat(chat)

    suspend fun deleteChatById(chatId: String) = chatDao.deleteChatById(chatId)

    suspend fun updateMembers(chatId: String, memberIds: String) = chatDao.updateMembers(chatId, memberIds)

    suspend fun updateAdmins(chatId: String, adminIds: String) = chatDao.updateAdmins(chatId, adminIds)

    suspend fun updateGroupInfo(chatId: String, name: String, description: String) =
        chatDao.updateGroupInfo(chatId, name, description)

    suspend fun updateWallpaper(chatId: String, wallpaperUrl: String) =
        chatDao.updateWallpaper(chatId, wallpaperUrl)

    fun getMessagesForChat(chatId: String): Flow<List<Message>> = messageDao.getMessagesForChat(chatId)

    suspend fun getMessageById(messageId: String): Message? = messageDao.getMessageById(messageId)

    suspend fun getLatestMessage(chatId: String): Message? = messageDao.getLatestMessage(chatId)

    fun searchMessagesInChat(chatId: String, query: String): Flow<List<Message>> =
        messageDao.searchMessagesInChat(chatId, query)

    fun searchMessagesGlobal(query: String): Flow<List<Message>> =
        messageDao.searchMessagesGlobal(query)

    suspend fun searchMessagesGlobalPaged(query: String, limit: Int = 50, offset: Int = 0): List<Message> =
        messageDao.searchMessagesGlobalPaged(query, limit, offset)

    fun getMediaMessagesForChat(chatId: String): Flow<List<Message>> =
        messageDao.getMediaMessagesForChat(chatId)

    fun getStarredMessagesForChat(chatId: String): Flow<List<Message>> =
        messageDao.getStarredMessagesForChat(chatId)

    fun getStarredMessagesGlobal(userId: String): Flow<List<Message>> =
        messageDao.getStarredMessagesGlobal(userId)

    suspend fun insertMessage(message: Message) = messageDao.insertMessage(message)

    suspend fun insertMessages(messages: List<Message>) = messageDao.insertMessages(messages)

    suspend fun updateMessage(message: Message) = messageDao.updateMessage(message)

    suspend fun markAllAsRead(chatId: String, currentUserId: String) =
        messageDao.markAllAsRead(chatId, currentUserId)

    suspend fun setStarred(messageId: String, starred: Boolean) =
        messageDao.setStarred(messageId, starred)

    suspend fun softDeleteMessage(messageId: String) = messageDao.softDeleteMessage(messageId)

    suspend fun softDeleteMessages(messageIds: List<String>) = messageDao.softDeleteMessages(messageIds)

    suspend fun deleteMessagesForChat(chatId: String) = messageDao.deleteMessagesForChat(chatId)

    fun getUnreadCount(chatId: String, currentUserId: String): Flow<Int> =
        messageDao.getUnreadCount(chatId, currentUserId)

    // Read Receipts
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus) =
        messageDao.updateStatus(messageId, status)

    suspend fun updateStatusForSender(chatId: String, senderId: String, status: MessageStatus) =
        messageDao.updateStatusForSender(chatId, senderId, status)

    // Voice Messages
    fun getVoiceMessagesForChat(chatId: String): Flow<List<Message>> =
        messageDao.getVoiceMessagesForChat(chatId)

    /**
     * Search globally using FTS4 and group results by chat (F14)
     * Falls back to LIKE search if FTS4 query fails.
     */
    suspend fun searchMessagesGrouped(query: String): List<ChatSearchGroup> {
        val messages = try {
            // F14: Use FTS4 MATCH query for fast full-text search
            messageDao.searchMessagesFts(query)
        } catch (e: Exception) {
            // Fallback to LIKE search if FTS4 table doesn't exist or query fails
            messageDao.searchMessagesGlobalPaged(query)
        }
        val grouped = messages.groupBy { it.chatId }
        val result = mutableListOf<ChatSearchGroup>()
        for ((chatId, chatMessages) in grouped) {
            val chat = chatDao.getChatById(chatId) ?: continue
            result.add(
                ChatSearchGroup(
                    chatId = chatId,
                    chatName = chat.name,
                    chatPhotoUrl = chat.photoUrl,
                    messages = chatMessages
                )
            )
        }
        return result.sortedByDescending { it.messages.maxOf { m -> m.timestamp } }
    }

    // F8: Offline sync queue methods
    suspend fun getPendingMessages(): List<Message> = messageDao.getPendingMessages()
    suspend fun getFailedMessages(): List<Message> = messageDao.getFailedMessages()
    suspend fun updateSyncState(messageId: String, syncState: String) = messageDao.updateSyncState(messageId, syncState)

    // F10: Per-member read tracking
    suspend fun updateStatusForSenderExcludingRead(chatId: String, senderId: String, status: MessageStatus) =
        messageDao.updateStatusForSenderExcludingRead(chatId, senderId, status)

    // ==================== Status Reply Support (C.3) ====================

    /**
     * Get or create a 1-on-1 chat with a recipient.
     * Used by status reply to ensure a chat exists before sending a message.
     * Returns the chatId of the existing or newly created chat.
     */
    suspend fun getOrCreateChat(recipientId: String, recipientName: String, currentUserId: String): String {
        // Check if chat already exists
        val existingChat = chatDao.getChatByRecipientId(recipientId)
        if (existingChat != null) return existingChat.id

        // Create new chat
        val chatId = java.util.UUID.randomUUID().toString()
        val chat = Chat(
            id = chatId,
            name = recipientName,
            recipientId = recipientId,
            isGroup = false
        )
        chatDao.insertChat(chat)
        return chatId
    }

    /**
     * Send a message — insert into Room and mark as pending for Firestore sync.
     */
    suspend fun sendMessage(message: Message) {
        messageDao.insertMessage(message.copy(syncState = "pending"))
    }
}
