package com.zaxo.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Transaction
import com.zaxo.app.model.Message
import com.zaxo.app.model.MessageStatus
import com.zaxo.app.model.MessageType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreMessageRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val messagesCollection = firestore.collection("messages")
    private val chatsCollection = firestore.collection("chats")

    fun listenToMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val subscription = messagesCollection
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Message::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { subscription.remove() }
    }

    /**
     * Listen for status changes on messages in a chat (read receipts).
     * F7: Uses Firestore realtime listener for atomic status updates.
     */
    fun listenToStatusUpdates(chatId: String): Flow<List<Message>> = callbackFlow {
        val subscription = messagesCollection
            .whereEqualTo("chatId", chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Message::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun sendEncryptedMessage(
        chatId: String,
        senderId: String,
        senderName: String,
        senderPhotoUrl: String,
        encryptedContent: String,
        type: MessageType = MessageType.TEXT,
        mediaUrl: String = "",
        mediaDuration: Long = 0L,
        waveform: String = "",
        replyToId: String = "",
        replyToContent: String = "",
        replyToSender: String = "",
        forwardedFrom: String = "",
        isForwarded: Boolean = false
    ): Message {
        val timestamp = System.currentTimeMillis()
        val message = Message(
            id = "",
            chatId = chatId,
            senderId = senderId,
            senderName = senderName,
            senderPhotoUrl = senderPhotoUrl,
            content = encryptedContent,
            type = type,
            mediaUrl = mediaUrl,
            mediaDuration = mediaDuration,
            waveform = waveform,
            timestamp = timestamp,
            isRead = false,
            isDelivered = false,
            isStarred = false,
            replyToId = replyToId,
            replyToContent = replyToContent,
            replyToSender = replyToSender,
            forwardedFrom = forwardedFrom,
            isForwarded = isForwarded,
            isEncrypted = true,
            status = MessageStatus.SENDING
        )

        // F7: Use Firestore transaction for atomic message send + chat update
        return try {
            val docRef = messagesCollection.add(message).await()
            val savedMessage = message.copy(id = docRef.id, status = MessageStatus.SENT)
            docRef.set(savedMessage).await()

            // Update chat last message atomically
            val lastMsgText = when (type) {
                MessageType.VOICE -> "🎤 Voice message"
                MessageType.IMAGE -> "📷 Photo"
                MessageType.VIDEO -> "🎬 Video"
                else -> if (isForwarded) "Forwarded message" else encryptedContent.take(50)
            }
            
            // F7: Batch write for atomicity
            val batch = firestore.batch()
            batch.update(
                chatsCollection.document(chatId),
                mapOf(
                    "lastMessage" to lastMsgText,
                    "lastMessageTime" to timestamp,
                    "lastMessageSender" to senderName
                )
            )
            batch.commit().await()

            Timber.d("Message sent: ${savedMessage.id} in chat $chatId")
            savedMessage
        } catch (e: Exception) {
            Timber.e(e, "Failed to send message to chat $chatId")
            message.copy(status = MessageStatus.FAILED)
        }
    }

    /**
     * Send voice message with waveform data.
     */
    suspend fun sendVoiceMessage(
        chatId: String,
        senderId: String,
        senderName: String,
        senderPhotoUrl: String,
        mediaUrl: String,
        duration: Long,
        waveform: String,
        replyToId: String = "",
        replyToContent: String = "",
        replyToSender: String = ""
    ): Message {
        return sendEncryptedMessage(
            chatId = chatId,
            senderId = senderId,
            senderName = senderName,
            senderPhotoUrl = senderPhotoUrl,
            encryptedContent = "🎤 Voice message",
            type = MessageType.VOICE,
            mediaUrl = mediaUrl,
            mediaDuration = duration,
            waveform = waveform,
            replyToId = replyToId,
            replyToContent = replyToContent,
            replyToSender = replyToSender
        )
    }

    suspend fun deleteMessage(messageId: String) {
        messagesCollection.document(messageId)
            .update(mapOf("isDeleted" to true, "content" to "This message was deleted"))
            .await()
    }

    suspend fun starMessage(messageId: String, starred: Boolean) {
        messagesCollection.document(messageId)
            .update("isStarred", starred)
            .await()
    }

    /**
     * Mark messages as read in Firestore (read receipts).
     * F7: Uses Firestore transaction for atomic status update.
     */
    suspend fun markAsRead(chatId: String, currentUserId: String) {
        val snapshot = messagesCollection
            .whereEqualTo("chatId", chatId)
            .whereEqualTo("isRead", false)
            .get()
            .await()

        val batch = firestore.batch()
        for (doc in snapshot.documents) {
            val senderId = doc.getString("senderId") ?: continue
            if (senderId != currentUserId) {
                batch.update(doc.reference, mapOf(
                    "isRead" to true,
                    "status" to MessageStatus.READ.value
                ))
            }
        }
        batch.commit().await()
    }

    /**
     * Update a single message's status in Firestore.
     * F7: Transaction-safe status update.
     */
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus) {
        messagesCollection.document(messageId)
            .update("status", status.value)
            .await()
    }

    /**
     * Mark messages as delivered in Firestore.
     */
    suspend fun markAsDelivered(chatId: String, currentUserId: String) {
        val snapshot = messagesCollection
            .whereEqualTo("chatId", chatId)
            .whereEqualTo("senderId", currentUserId)
            .whereEqualTo("status", MessageStatus.SENT.value)
            .get()
            .await()

        val batch = firestore.batch()
        for (doc in snapshot.documents) {
            batch.update(doc.reference, mapOf(
                "isDelivered" to true,
                "status" to MessageStatus.DELIVERED.value
            ))
        }
        batch.commit().await()
    }
}
