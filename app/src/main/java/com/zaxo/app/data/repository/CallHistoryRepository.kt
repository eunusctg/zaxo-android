package com.zaxo.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.zaxo.app.data.dao.CallHistoryDao
import com.zaxo.app.model.CallRecord
import com.zaxo.app.model.CallType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for call history with Room (immediate, offline-safe) + Firestore (background sync).
 * F105: Dual write ensures history survives reinstall if synced.
 * F106: Server timestamp for accurate duration.
 * F108: Cache contact name at call time to handle deleted contacts.
 */
@Singleton
class CallHistoryRepository @Inject constructor(
    private val callHistoryDao: CallHistoryDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    fun getAllCallRecords(): Flow<List<CallRecord>> = callHistoryDao.getAllCallRecords()

    fun getRecentCallRecords(limit: Int = 50): Flow<List<CallRecord>> =
        callHistoryDao.getRecentCallRecords(limit)

    fun getCallsForContact(userId: String): Flow<List<CallRecord>> =
        callHistoryDao.getCallsForContact(userId)

    suspend fun getRecentCallsFrom(userId: String, sinceTimestamp: Long): List<CallRecord> =
        callHistoryDao.getRecentCallsFrom(userId, sinceTimestamp)

    /**
     * Save a call record immediately to Room, then sync to Firestore.
     */
    suspend fun saveCallRecord(record: CallRecord) {
        // Immediate local save
        callHistoryDao.insertCallRecord(record)
        Timber.d("Call record saved locally: ${record.id}")

        // Background Firestore sync
        syncToFirestore(record)
    }

    /**
     * Update call duration after call ends.
     */
    suspend fun updateDuration(callId: String, duration: Long) {
        callHistoryDao.updateDuration(callId, duration)
        Timber.d("Call duration updated: $callId = ${duration}s")
    }

    /**
     * Delete a call record.
     */
    suspend fun deleteCallRecord(id: String) {
        callHistoryDao.deleteCallRecord(id)
    }

    /**
     * Delete all call records.
     */
    suspend fun deleteAllCallRecords() {
        callHistoryDao.deleteAllCallRecords()
    }

    /**
     * Create a call record from a completed call session.
     */
    suspend fun saveFromSession(
        contactId: String,
        contactName: String,
        contactPhotoUrl: String,
        callType: CallType,
        mediaType: String,
        duration: Long,
        isGroupCall: Boolean = false,
        groupId: String = "",
        groupName: String = "",
        roomId: String = ""
    ) {
        val record = CallRecord(
            id = UUID.randomUUID().toString(),
            contactId = contactId,
            contactName = contactName, // F108: Cache name at call time
            contactPhotoUrl = contactPhotoUrl,
            callType = callType,
            mediaType = mediaType,
            timestamp = System.currentTimeMillis(),
            duration = duration,
            isGroupCall = isGroupCall,
            groupId = groupId,
            groupName = groupName,
            roomId = roomId,
            cachedName = contactName
        )
        saveCallRecord(record)
    }

    /**
     * Sync call record to Firestore for cross-device access.
     */
    private suspend fun syncToFirestore(record: CallRecord) {
        try {
            val uid = auth.currentUser?.uid ?: return
            firestore.collection("users")
                .document(uid)
                .collection("callHistory")
                .document(record.id)
                .set(mapOf(
                    "id" to record.id,
                    "contactId" to record.contactId,
                    "contactName" to record.contactName,
                    "contactPhotoUrl" to record.contactPhotoUrl,
                    "callType" to record.callType.value,
                    "mediaType" to record.mediaType,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "clientTimestamp" to record.timestamp,
                    "duration" to record.duration,
                    "isGroupCall" to record.isGroupCall,
                    "groupId" to record.groupId,
                    "groupName" to record.groupName,
                    "roomId" to record.roomId,
                    "cachedName" to record.cachedName
                ))
                .await()
            Timber.d("Call record synced to Firestore: ${record.id}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync call record to Firestore")
        }
    }
}
