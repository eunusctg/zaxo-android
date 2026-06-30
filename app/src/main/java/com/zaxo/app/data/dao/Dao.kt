package com.zaxo.app.data.dao

import androidx.room.*
import com.zaxo.app.model.Chat
import com.zaxo.app.model.Message
import com.zaxo.app.model.MessageStatus
import com.zaxo.app.model.Status
import com.zaxo.app.model.MutedStatus
import com.zaxo.app.model.StatusView
import com.zaxo.app.model.BlockedCaller
import com.zaxo.app.model.ChatNotificationSettings
import com.zaxo.app.model.CallRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chats WHERE isArchived = 0 ORDER BY isPinned DESC, lastMessageTime DESC")
    fun getAllChats(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE isArchived = 1 ORDER BY lastMessageTime DESC")
    fun getArchivedChats(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: String): Chat?

    @Query("SELECT * FROM chats WHERE id = :chatId")
    fun getChatByIdFlow(chatId: String): Flow<Chat?>

    @Query("SELECT * FROM chats WHERE recipientId = :recipientId")
    suspend fun getChatByRecipientId(recipientId: String): Chat?

    @Query("SELECT * FROM chats WHERE name LIKE '%' || :query || '%' ORDER BY lastMessageTime DESC")
    fun searchChats(query: String): Flow<List<Chat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: Chat)

    @Update
    suspend fun updateChat(chat: Chat)

    @Query("UPDATE chats SET isArchived = :archived WHERE id = :chatId")
    suspend fun setArchived(chatId: String, archived: Boolean)

    @Query("UPDATE chats SET isPinned = :pinned WHERE id = :chatId")
    suspend fun setPinned(chatId: String, pinned: Boolean)

    @Query("UPDATE chats SET isMuted = :muted WHERE id = :chatId")
    suspend fun setMuted(chatId: String, muted: Boolean)

    @Query("UPDATE chats SET unreadCount = 0 WHERE id = :chatId")
    suspend fun clearUnreadCount(chatId: String)

    @Query("UPDATE chats SET lastMessage = :message, lastMessageTime = :time, lastMessageSender = :sender WHERE id = :chatId")
    suspend fun updateLastMessage(chatId: String, message: String, time: Long, sender: String)

    @Query("UPDATE chats SET isTyping = :typing WHERE id = :chatId")
    suspend fun setTyping(chatId: String, typing: Boolean)

    @Delete
    suspend fun deleteChat(chat: Chat)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: String)

    @Query("UPDATE chats SET memberIds = :memberIds WHERE id = :chatId")
    suspend fun updateMembers(chatId: String, memberIds: String)

    @Query("UPDATE chats SET adminIds = :adminIds WHERE id = :chatId")
    suspend fun updateAdmins(chatId: String, adminIds: String)

    @Query("UPDATE chats SET name = :name WHERE id = :chatId")
    suspend fun updateName(chatId: String, name: String)

    @Query("UPDATE chats SET groupDescription = :description WHERE id = :chatId")
    suspend fun updateGroupDescription(chatId: String, description: String)

    @Query("UPDATE chats SET name = :name, groupDescription = :description WHERE id = :chatId")
    suspend fun updateGroupInfo(chatId: String, name: String, description: String)

    @Query("UPDATE chats SET wallpaperUrl = :wallpaperUrl WHERE id = :chatId")
    suspend fun updateWallpaper(chatId: String, wallpaperUrl: String)
}

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isDeleted = 0 ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): Message?

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isDeleted = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessage(chatId: String): Message?

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND content LIKE '%' || :query || '%' AND isDeleted = 0 ORDER BY timestamp DESC")
    fun searchMessagesInChat(chatId: String, query: String): Flow<List<Message>>

    // FTS4 search within a specific chat
    @Query("""
        SELECT m.* FROM messages m
        JOIN messages_fts fts ON m.rowid = fts.docid
        WHERE messages_fts MATCH :query
        AND m.chatId = :chatId
        AND m.isDeleted = 0
        ORDER BY m.timestamp DESC
    """)
    fun searchMessagesInChatFts(chatId: String, query: String): Flow<List<Message>>

    @Query("""
        SELECT m.* FROM messages m
        INNER JOIN chats c ON m.chatId = c.id
        WHERE m.content LIKE '%' || :query || '%' 
        AND m.isDeleted = 0
        AND c.isArchived = 0
        ORDER BY m.timestamp DESC
    """)
    fun searchMessagesGlobal(query: String): Flow<List<Message>>

    @Query("""
        SELECT m.* FROM messages m
        INNER JOIN chats c ON m.chatId = c.id
        WHERE m.content LIKE '%' || :query || '%' 
        AND m.isDeleted = 0
        AND c.isArchived = 0
        ORDER BY m.timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchMessagesGlobalPaged(query: String, limit: Int = 50, offset: Int = 0): List<Message>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND type IN ('image','video') AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getMediaMessagesForChat(chatId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isStarred = 1 AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getStarredMessagesForChat(chatId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE senderId = :userId AND isStarred = 1 AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getStarredMessagesGlobal(userId: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<Message>)

    @Update
    suspend fun updateMessage(message: Message)

    @Query("UPDATE messages SET isRead = 1 WHERE chatId = :chatId AND senderId != :currentUserId")
    suspend fun markAllAsRead(chatId: String, currentUserId: String)

    @Query("UPDATE messages SET isStarred = :starred WHERE id = :messageId")
    suspend fun setStarred(messageId: String, starred: Boolean)

    @Query("UPDATE messages SET isDeleted = 1 WHERE id = :messageId")
    suspend fun softDeleteMessage(messageId: String)

    @Query("UPDATE messages SET isDeleted = 1 WHERE id IN (:messageIds)")
    suspend fun softDeleteMessages(messageIds: List<String>)

    @Query("UPDATE messages SET isDelivered = 1 WHERE id IN (:messageIds)")
    suspend fun markDelivered(messageIds: List<String>)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND isRead = 0 AND senderId != :currentUserId")
    fun getUnreadCount(chatId: String, currentUserId: String): Flow<Int>

    // Read Receipts: Update message status
    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: MessageStatus)

    @Query("UPDATE messages SET status = :status WHERE chatId = :chatId AND senderId = :senderId AND status != 'read'")
    suspend fun updateStatusForSender(chatId: String, senderId: String, status: MessageStatus)

    // Voice messages: Get voice messages for a chat
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND type = 'voice' AND isDeleted = 0 ORDER BY timestamp ASC")
    fun getVoiceMessagesForChat(chatId: String): Flow<List<Message>>

    // FTS4 full-text search (F14)
    @Query("""
        SELECT m.* FROM messages m
        JOIN messages_fts fts ON m.rowid = fts.docid
        WHERE messages_fts MATCH :query
        AND m.isDeleted = 0
        ORDER BY m.timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchMessagesFts(query: String, limit: Int = 100, offset: Int = 0): List<Message>

    // F8: Offline sync queue
    @Query("SELECT * FROM messages WHERE syncState = 'pending' ORDER BY timestamp ASC")
    suspend fun getPendingMessages(): List<Message>

    @Query("SELECT * FROM messages WHERE syncState = 'failed' ORDER BY timestamp ASC")
    suspend fun getFailedMessages(): List<Message>

    @Query("UPDATE messages SET syncState = :syncState WHERE id = :messageId")
    suspend fun updateSyncState(messageId: String, syncState: String)

    // F10: Per-member read tracking for group chats
    @Query("""
        UPDATE messages SET status = :status 
        WHERE chatId = :chatId 
        AND senderId = :senderId 
        AND status IN ('sent', 'delivered')
    """)
    suspend fun updateStatusForSenderExcludingRead(chatId: String, senderId: String, status: MessageStatus)
}

@Dao
interface StatusDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatus(status: Status)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatuses(statuses: List<Status>)

    @Query("SELECT * FROM statuses WHERE userId = :userId AND expiresAt > :currentTime ORDER BY createdAt DESC")
    fun getActiveStatusesForUser(userId: String, currentTime: Long = System.currentTimeMillis()): Flow<List<Status>>

    @Query("SELECT * FROM statuses WHERE expiresAt > :currentTime ORDER BY createdAt DESC")
    fun getAllActiveStatuses(currentTime: Long = System.currentTimeMillis()): Flow<List<Status>>

    @Query("SELECT * FROM statuses WHERE userId = :userId ORDER BY createdAt DESC")
    fun getAllStatusesForUser(userId: String): Flow<List<Status>>

    @Query("SELECT DISTINCT userId FROM statuses WHERE expiresAt > :currentTime")
    suspend fun getUsersWithActiveStatuses(currentTime: Long = System.currentTimeMillis()): List<String>

    @Query("DELETE FROM statuses WHERE expiresAt <= :currentTime")
    suspend fun deleteExpiredStatuses(currentTime: Long = System.currentTimeMillis())

    @Query("UPDATE statuses SET isViewed = 1 WHERE id = :statusId")
    suspend fun markStatusViewed(statusId: String)

    @Query("SELECT COUNT(*) FROM statuses WHERE userId = :userId AND isViewed = 0 AND expiresAt > :currentTime")
    suspend fun getUnviewedStatusCount(userId: String, currentTime: Long = System.currentTimeMillis()): Int

    // Status Views
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatusView(statusView: StatusView)

    @Query("SELECT * FROM status_views WHERE statusId = :statusId ORDER BY viewedAt DESC")
    fun getViewsForStatus(statusId: String): Flow<List<StatusView>>

    @Query("SELECT COUNT(*) FROM status_views WHERE statusId = :statusId")
    suspend fun getViewCount(statusId: String): Int

    @Query("DELETE FROM statuses WHERE id = :statusId")
    suspend fun deleteStatus(statusId: String)

    // F29: Status upload sync state queries
    @Query("SELECT * FROM statuses WHERE syncState = 'failed' ORDER BY createdAt ASC")
    suspend fun getFailedStatuses(): List<Status>

    @Query("SELECT * FROM statuses WHERE id = :statusId")
    suspend fun getStatusById(statusId: String): Status?

    @Query("UPDATE statuses SET syncState = :syncState WHERE id = :statusId")
    suspend fun updateSyncState(statusId: String, syncState: String)

    @Query("UPDATE statuses SET syncState = :syncState, mediaUrl = :mediaUrl WHERE id = :statusId")
    suspend fun updateSyncStateAndMediaUrl(statusId: String, syncState: String, mediaUrl: String)
}

@Dao
interface MutedStatusDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMutedStatus(mutedStatus: MutedStatus)

    @Query("SELECT * FROM muted_statuses ORDER BY mutedAt DESC")
    fun getAllMutedStatuses(): Flow<List<MutedStatus>>

    @Query("SELECT mutedUserId FROM muted_statuses")
    suspend fun getMutedUserIds(): List<String>

    @Query("DELETE FROM muted_statuses WHERE mutedUserId = :userId")
    suspend fun deleteMutedStatus(userId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM muted_statuses WHERE mutedUserId = :userId)")
    suspend fun isMuted(userId: String): Boolean
}

// ==================== Blocked Caller DAO ====================
@Dao
interface BlockedCallerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedCaller(caller: BlockedCaller)

    @Query("SELECT * FROM blocked_callers ORDER BY blockedAt DESC")
    fun getAllBlockedCallers(): Flow<List<BlockedCaller>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_callers WHERE blockedUserId = :userId)")
    suspend fun isBlocked(userId: String): Boolean

    @Query("DELETE FROM blocked_callers WHERE blockedUserId = :userId")
    suspend fun deleteBlockedCaller(userId: String)

    @Query("SELECT blockedUserId FROM blocked_callers")
    suspend fun getBlockedUserIds(): List<String>
}

// ==================== Chat Notification Settings DAO ====================
@Dao
interface ChatNotificationSettingsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: ChatNotificationSettings)

    @Query("SELECT * FROM chat_notification_settings WHERE chatId = :chatId")
    suspend fun getSettingsForChat(chatId: String): ChatNotificationSettings?

    @Query("SELECT * FROM chat_notification_settings")
    fun getAllSettings(): Flow<List<ChatNotificationSettings>>

    @Query("DELETE FROM chat_notification_settings WHERE chatId = :chatId")
    suspend fun deleteSettingsForChat(chatId: String)

    @Query("DELETE FROM chat_notification_settings")
    suspend fun deleteAllSettings()
}

// ==================== Call History DAO ====================
@Dao
interface CallHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallRecord(record: CallRecord)

    @Query("SELECT * FROM call_history ORDER BY timestamp DESC")
    fun getAllCallRecords(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_history WHERE contactId = :userId ORDER BY timestamp DESC")
    fun getCallsForContact(userId: String): Flow<List<CallRecord>>

    @Query("DELETE FROM call_history WHERE id = :id")
    suspend fun deleteCallRecord(id: String)

    @Query("DELETE FROM call_history")
    suspend fun deleteAllCallRecords()

    @Query("SELECT * FROM call_history WHERE contactId = :userId AND timestamp > :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun getRecentCallsFrom(userId: String, sinceTimestamp: Long): List<CallRecord>

    @Query("SELECT * FROM call_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentCallRecords(limit: Int = 50): Flow<List<CallRecord>>

    @Query("SELECT COUNT(*) FROM call_history WHERE contactId = :userId AND timestamp > :sinceTimestamp")
    suspend fun getCallCountFromUser(userId: String, sinceTimestamp: Long): Int

    @Query("UPDATE call_history SET duration = :duration WHERE id = :id")
    suspend fun updateDuration(id: String, duration: Long)
}
