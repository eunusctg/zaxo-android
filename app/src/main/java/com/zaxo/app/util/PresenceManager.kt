package com.zaxo.app.util

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.zaxo.app.model.PresenceState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * D.3: Online Presence Algorithm — Firestore real-time listener + local cache.
 *
 * Manages the current user's online/offline state and observes other users' presence.
 *
 * Algorithm:
 * 1. On app open: set users/{uid}.online = true, lastSeen = serverTimestamp()
 * 2. On app background: set online = false, lastSeen = serverTimestamp()
 * 3. For each contact: listen to users/{contactUid}.online
 * 4. Online = green dot (#27AE60)
 * 5. Offline < 5 min = "Recently online" (gray dot)
 * 6. Offline > 5 min = "Last seen X min/hours ago"
 */
@Singleton
class PresenceManager @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    companion object {
        private const val RECENTLY_ONLINE_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
    }

    /**
     * Set the current user as online in Firestore.
     */
    fun setOnline() {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(userId)
                .update(mapOf(
                    "isOnline" to true,
                    "lastSeen" to FieldValue.serverTimestamp()
                ))
        } catch (e: Exception) {
            Timber.e(e, "Failed to set online status")
        }
    }

    /**
     * Set the current user as offline in Firestore.
     */
    fun setOffline() {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users").document(userId)
                .update(mapOf(
                    "isOnline" to false,
                    "lastSeen" to FieldValue.serverTimestamp()
                ))
        } catch (e: Exception) {
            Timber.e(e, "Failed to set offline status")
        }
    }

    /**
     * Observe a user's presence state in real-time.
     * Returns a Flow of PresenceState that updates when the Firestore document changes.
     *
     * F56: Show cached status first, update when listener fires.
     */
    fun observePresence(userId: String): Flow<PresenceState> = callbackFlow {
        // Emit cached/default state immediately (F56)
        trySend(PresenceState(isOnline = false, lastSeen = 0L))

        val listener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Presence listener error for user $userId")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val isOnline = snapshot.getBoolean("isOnline") ?: false
                    val lastSeen = snapshot.getLong("lastSeen") ?: 0L
                    trySend(PresenceState(isOnline = isOnline, lastSeen = lastSeen))
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get a human-readable presence description.
     */
    fun getPresenceDescription(presence: PresenceState): String {
        return when {
            presence.isOnline -> "Online"
            presence.lastSeen == 0L -> "Offline"
            else -> {
                val elapsed = System.currentTimeMillis() - presence.lastSeen
                when {
                    elapsed < RECENTLY_ONLINE_THRESHOLD_MS -> "Recently online"
                    elapsed < 60 * 60 * 1000L -> {
                        val minutes = elapsed / (60 * 1000)
                        "Last seen $minutes min ago"
                    }
                    elapsed < 24 * 60 * 60 * 1000L -> {
                        val hours = elapsed / (60 * 60 * 1000)
                        "Last seen $hours hr ago"
                    }
                    else -> {
                        val days = elapsed / (24 * 60 * 60 * 1000)
                        "Last seen $days day${if (days > 1) "s" else ""} ago"
                    }
                }
            }
        }
    }

    /**
     * Check if a user was recently online (within threshold).
     */
    fun isRecentlyOnline(presence: PresenceState): Boolean {
        if (presence.isOnline) return true
        if (presence.lastSeen == 0L) return false
        val elapsed = System.currentTimeMillis() - presence.lastSeen
        return elapsed < RECENTLY_ONLINE_THRESHOLD_MS
    }
}
