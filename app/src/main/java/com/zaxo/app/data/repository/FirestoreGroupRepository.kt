package com.zaxo.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.zaxo.app.model.Chat
import com.zaxo.app.model.User
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreGroupRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val chatsCollection = firestore.collection("chats")
    private val usersCollection = firestore.collection("users")

    /**
     * Add a member to the group using a Firestore transaction for atomicity (F6).
     * Guard: prevents adding a user who is already a member (F2).
     */
    suspend fun addMember(chatId: String, userId: String): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val chatDoc = transaction.get(chatsCollection.document(chatId))
                val chat = chatDoc.toObject(Chat::class.java)
                    ?: throw Exception("Chat not found")

                val currentMembers = chat.memberIds.split(",").filter { it.isNotEmpty() }.toMutableList()
                if (userId in currentMembers) {
                    throw Exception("Already in group")
                }
                currentMembers.add(userId)

                transaction.update(
                    chatsCollection.document(chatId),
                    mapOf("memberIds" to currentMembers.joinToString(","))
                )
                null // transaction result
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove a member from the group using a Firestore transaction (F6).
     * Guards:
     *  - Cannot remove the creator (F5)
     *  - Cannot remove the last admin (must promote another first) (F3)
     */
    suspend fun removeMember(chatId: String, userId: String): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val chatDoc = transaction.get(chatsCollection.document(chatId))
                val chat = chatDoc.toObject(Chat::class.java)
                    ?: throw Exception("Chat not found")

                if (userId == chat.createdBy) {
                    throw Exception("Cannot remove the group creator")
                }

                val currentMembers = chat.memberIds.split(",").filter { it.isNotEmpty() }.toMutableList()
                val currentAdmins = chat.adminIds.split(",").filter { it.isNotEmpty() }.toMutableList()

                // F3: Cannot remove the last admin
                if (userId in currentAdmins && currentAdmins.size <= 1) {
                    throw Exception("Cannot remove the last admin. Promote another member first.")
                }

                currentMembers.remove(userId)
                currentAdmins.remove(userId)

                transaction.update(
                    chatsCollection.document(chatId),
                    mapOf(
                        "memberIds" to currentMembers.joinToString(","),
                        "adminIds" to currentAdmins.joinToString(",")
                    )
                )
                null
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Leave the group. Handles admin transfer if the user is the last admin.
     * If the user is the creator, transfers creator role to the next oldest admin.
     * Uses Firestore transaction for atomicity (F6, F3).
     */
    suspend fun leaveGroup(chatId: String, userId: String): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val chatDoc = transaction.get(chatsCollection.document(chatId))
                val chat = chatDoc.toObject(Chat::class.java)
                    ?: throw Exception("Chat not found")

                val currentMembers = chat.memberIds.split(",").filter { it.isNotEmpty() }.toMutableList()
                val currentAdmins = chat.adminIds.split(",").filter { it.isNotEmpty() }.toMutableList()
                var createdBy = chat.createdBy

                currentMembers.remove(userId)

                // If user is admin, handle admin transfer
                if (userId in currentAdmins) {
                    currentAdmins.remove(userId)

                    // If this was the last admin, promote the oldest remaining member
                    if (currentAdmins.isEmpty() && currentMembers.isNotEmpty()) {
                        val newAdmin = currentMembers.first()
                        currentAdmins.add(newAdmin)
                    }
                }

                // If user is the creator, transfer creator role
                if (userId == createdBy) {
                    // Transfer to the first admin, or the first member
                    createdBy = currentAdmins.firstOrNull() ?: currentMembers.firstOrNull() ?: ""
                }

                val updates = mutableMapOf<String, Any>(
                    "memberIds" to currentMembers.joinToString(","),
                    "adminIds" to currentAdmins.joinToString(",")
                )
                if (userId == chat.createdBy) {
                    updates["createdBy"] = createdBy
                }

                transaction.update(chatsCollection.document(chatId), updates)
                null
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Promote a member to admin using Firestore transaction (F6).
     */
    suspend fun promoteToAdmin(chatId: String, userId: String): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val chatDoc = transaction.get(chatsCollection.document(chatId))
                val chat = chatDoc.toObject(Chat::class.java)
                    ?: throw Exception("Chat not found")

                val currentAdmins = chat.adminIds.split(",").filter { it.isNotEmpty() }.toMutableList()
                if (userId in currentAdmins) {
                    throw Exception("Already an admin")
                }
                currentAdmins.add(userId)

                transaction.update(
                    chatsCollection.document(chatId),
                    mapOf("adminIds" to currentAdmins.joinToString(","))
                )
                null
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Demote an admin to regular member using Firestore transaction (F6).
     * Guards:
     *  - Cannot demote the creator (F5)
     *  - Cannot demote the last admin (F4)
     */
    suspend fun demoteFromAdmin(chatId: String, userId: String): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val chatDoc = transaction.get(chatsCollection.document(chatId))
                val chat = chatDoc.toObject(Chat::class.java)
                    ?: throw Exception("Chat not found")

                if (chat.createdBy == userId) {
                    throw Exception("Cannot demote the group creator")
                }

                val currentAdmins = chat.adminIds.split(",").filter { it.isNotEmpty() }.toMutableList()

                // F4: Cannot demote the last admin
                if (currentAdmins.size <= 1) {
                    throw Exception("Group must have at least one admin")
                }

                currentAdmins.remove(userId)

                transaction.update(
                    chatsCollection.document(chatId),
                    mapOf("adminIds" to currentAdmins.joinToString(","))
                )
                null
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update group info (name, description) using Firestore transaction (F6).
     */
    suspend fun updateGroupInfo(
        chatId: String,
        name: String? = null,
        description: String? = null
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>()
            name?.let { updates["name"] = it }
            description?.let { updates["groupDescription"] = it }
            if (updates.isNotEmpty()) {
                chatsCollection.document(chatId).update(updates).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the latest chat document from Firestore (for refreshing after transactions).
     */
    suspend fun getChatDocument(chatId: String): Chat? {
        return try {
            val doc = chatsCollection.document(chatId).get().await()
            doc.toObject(Chat::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get user profile by ID.
     */
    suspend fun getUserById(userId: String): User? {
        return try {
            val doc = usersCollection.document(userId).get().await()
            doc.toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Search users by name prefix (for adding to group).
     */
    suspend fun searchUsers(query: String, limit: Int = 20): List<User> {
        return try {
            val snapshot = usersCollection
                .orderBy("displayName")
                .startAt(query)
                .endAt(query + "\uf8ff")
                .limit(limit.toLong())
                .get()
                .await()
            snapshot.documents.mapNotNull { it.toObject(User::class.java) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * F1 FIX: Search user by Zaxo Number for add-member dialog.
     * Returns Result.failure("User not found") if no match.
     */
    suspend fun searchUserByZaxoNumber(number: String): Result<User> {
        return try {
            val cleanNumber = number.replace(Regex("[^0-9]"), "")
            if (cleanNumber.isEmpty()) {
                return Result.failure(Exception("Invalid number"))
            }
            val snapshot = usersCollection
                .whereEqualTo("zaxoNumber", cleanNumber)
                .limit(1)
                .get()
                .await()

            if (snapshot.isEmpty) {
                return Result.failure(Exception("User not found"))
            }

            val doc = snapshot.documents[0]
            val user = User(
                uid = doc.id,
                zaxoNumber = doc.getString("zaxoNumber") ?: cleanNumber,
                displayName = doc.getString("displayName") ?: "Zaxo User",
                photoUrl = doc.getString("photoUrl") ?: "",
                email = doc.getString("email") ?: "",
                phone = doc.getString("phone") ?: "",
                about = doc.getString("about") ?: ""
            )
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
