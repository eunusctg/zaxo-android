package com.zaxo.app.data.repository

import android.icu.text.BreakIterator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.zaxo.app.data.dao.StatusDao
import com.zaxo.app.model.Status
import com.zaxo.app.model.StatusType
import com.zaxo.app.model.StatusView
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val statusDao: StatusDao
) {
    private val statusesCollection = firestore.collection("statuses")

    /** Guard to prevent double-tap sends (F21) */
    @Volatile
    private var isSending = false

    companion object {
        private const val STATUS_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val MAX_TEXT_STATUS_GRAPHEME_COUNT = 700
    }

    // ───────────────────────── Realtime Firestore Listeners ─────────────────────────

    /**
     * Realtime listener on statuses where expiresAt > now.
     * Returns all active statuses from Firestore and caches them into Room.
     */
    fun listenToContactStatuses(): Flow<List<Status>> = callbackFlow {
        val now = System.currentTimeMillis()
        val subscription = statusesCollection
            .whereGreaterThan("expiresAt", now)
            .orderBy("expiresAt", Query.Direction.ASCENDING)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error listening to contact statuses")
                    close(error)
                    return@addSnapshotListener
                }
                val statuses = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Status::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                // Cache into Room for offline access
                kotlinx.coroutines.runCatching {
                    statusDao.insertStatuses(statuses)
                }.onFailure { e ->
                    Timber.e(e, "Failed to cache statuses into Room")
                }

                trySend(statuses)
            }
        awaitClose { subscription.remove() }
    }

    // ───────────────────────── Room Queries ─────────────────────────

    /** Active statuses for a specific user from Room. */
    fun getUserStatuses(userId: String): Flow<List<Status>> =
        statusDao.getActiveStatusesForUser(userId)

    /** Current user's active statuses from Room. */
    fun getMyStatuses(): Flow<List<Status>> {
        val currentUserId = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return statusDao.getActiveStatusesForUser(currentUserId)
    }

    /** Distinct userIds with active statuses. */
    suspend fun getUsersWithActiveStatuses(): List<String> =
        statusDao.getUsersWithActiveStatuses()

    /** Unviewed status count for a given user. */
    suspend fun getUnviewedCount(userId: String): Int =
        statusDao.getUnviewedStatusCount(userId)

    // ───────────────────────── View Tracking ─────────────────────────

    /**
     * Mark a status as viewed: insert a StatusView record, update isViewed in Room,
     * and update the Firestore document.
     */
    suspend fun markAsViewed(
        statusId: String,
        viewerId: String,
        viewerName: String,
        viewerPhotoUrl: String
    ) {
        val statusView = StatusView(
            id = UUID.randomUUID().toString(),
            statusId = statusId,
            viewerId = viewerId,
            viewerName = viewerName,
            viewerPhotoUrl = viewerPhotoUrl,
            viewedAt = System.currentTimeMillis()
        )

        // Insert view into Room
        statusDao.insertStatusView(statusView)

        // Mark as viewed in Room
        statusDao.markStatusViewed(statusId)

        // Update Firestore — add view to the views sub-collection
        try {
            val viewDocRef = statusesCollection
                .document(statusId)
                .collection("views")
                .document(statusView.id)

            viewDocRef.set(statusView).await()

            // Also update isViewed on the status document
            statusesCollection.document(statusId)
                .update("isViewed", true)
                .await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to mark status as viewed in Firestore")
        }
    }

    /** Get viewers for a status from Room. */
    fun getViewers(statusId: String): Flow<List<StatusView>> =
        statusDao.getViewsForStatus(statusId)

    /**
     * Get view count for a status (F20).
     * Uses Firestore count() aggregation query for accuracy, falls back to Room.
     */
    suspend fun getViewCount(statusId: String): Int {
        return try {
            val countQuery = statusesCollection
                .document(statusId)
                .collection("views")
                .count()
                .get()
                .await()

            countQuery.count.toInt()
        } catch (e: Exception) {
            Timber.e(e, "Firestore count query failed, falling back to Room")
            statusDao.getViewCount(statusId)
        }
    }

    // ───────────────────────── Create Status ─────────────────────────

    /**
     * Create a photo status with 24h expiry.
     * Protected by isSending guard (F21) to prevent double-tap send.
     */
    suspend fun createPhotoStatus(mediaUrl: String): Result<Status> {
        if (isSending) {
            Timber.w("Status creation already in progress — ignoring double-tap")
            return Result.failure(IllegalStateException("A status is already being sent"))
        }

        isSending = true
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(
                IllegalStateException("User not authenticated")
            )

            val now = System.currentTimeMillis()
            val status = Status(
                id = UUID.randomUUID().toString(),
                userId = currentUser.uid,
                userName = currentUser.displayName ?: "",
                userPhotoUrl = currentUser.photoUrl?.toString() ?: "",
                type = StatusType.PHOTO,
                mediaUrl = mediaUrl,
                textContent = "",
                backgroundColor = "#4A90D9",
                fontFamily = "default",
                createdAt = now,
                expiresAt = now + STATUS_DURATION_MS,
                isViewed = false
            )

            // Write to Firestore
            val docRef = statusesCollection.document(status.id)
            docRef.set(status).await()

            // Cache into Room
            statusDao.insertStatus(status)

            Result.success(status)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create photo status")
            Result.failure(e)
        } finally {
            isSending = false
        }
    }

    /**
     * Create a text status with 24h expiry.
     * Validates text length using BreakIterator for grapheme counting (F22).
     * Protected by isSending guard (F21) to prevent double-tap send.
     */
    suspend fun createTextStatus(
        text: String,
        backgroundColor: String,
        fontFamily: String
    ): Result<Status> {
        if (isSending) {
            Timber.w("Status creation already in progress — ignoring double-tap")
            return Result.failure(IllegalStateException("A status is already being sent"))
        }

        // F22: Validate text length using BreakIterator for accurate grapheme counting
        val graphemeCount = countGraphemes(text)
        if (graphemeCount > MAX_TEXT_STATUS_GRAPHEME_COUNT) {
            return Result.failure(
                IllegalArgumentException(
                    "Text status exceeds maximum of $MAX_TEXT_STATUS_GRAPHEME_COUNT characters " +
                        "(found $graphemeCount graphemes)"
                )
            )
        }

        if (graphemeCount == 0) {
            return Result.failure(IllegalArgumentException("Text status cannot be empty"))
        }

        isSending = true
        return try {
            val currentUser = auth.currentUser ?: return Result.failure(
                IllegalStateException("User not authenticated")
            )

            val now = System.currentTimeMillis()
            val status = Status(
                id = UUID.randomUUID().toString(),
                userId = currentUser.uid,
                userName = currentUser.displayName ?: "",
                userPhotoUrl = currentUser.photoUrl?.toString() ?: "",
                type = StatusType.TEXT,
                mediaUrl = "",
                textContent = text,
                backgroundColor = backgroundColor,
                fontFamily = fontFamily,
                createdAt = now,
                expiresAt = now + STATUS_DURATION_MS,
                isViewed = false
            )

            // Write to Firestore
            val docRef = statusesCollection.document(status.id)
            docRef.set(status).await()

            // Cache into Room
            statusDao.insertStatus(status)

            Result.success(status)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create text status")
            Result.failure(e)
        } finally {
            isSending = false
        }
    }

    // ───────────────────────── Delete Status ─────────────────────────

    /** Delete a status from both Room and Firestore. */
    suspend fun deleteStatus(statusId: String) {
        try {
            // Delete from Firestore — status document and its views sub-collection
            val viewsSnapshot = statusesCollection
                .document(statusId)
                .collection("views")
                .get()
                .await()

            val batch = firestore.batch()
            for (doc in viewsSnapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.delete(statusesCollection.document(statusId))
            batch.commit().await()

            // Delete from Room
            statusDao.deleteStatus(statusId)

            Timber.d("Status $statusId deleted successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete status $statusId")
            // Still attempt local deletion even if Firestore fails
            statusDao.deleteStatus(statusId)
        }
    }

    /** Delete expired statuses from Room. */
    suspend fun deleteExpiredStatuses() {
        try {
            statusDao.deleteExpiredStatuses()
            Timber.d("Expired statuses cleaned up from Room")
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete expired statuses from Room")
        }
    }

    // ───────────────────────── Helpers ─────────────────────────

    /**
     * F22: Count grapheme clusters using BreakIterator.
     * This correctly handles complex Unicode sequences (emoji, combining characters, etc.)
     * where a single user-perceived character may span multiple code points.
     */
    private fun countGraphemes(text: String): Int {
        if (text.isEmpty()) return 0

        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)

        var count = 0
        while (iterator.next() != BreakIterator.DONE) {
            count++
        }
        return count
    }
}
