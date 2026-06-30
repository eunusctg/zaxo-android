package com.zaxo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zaxo.app.data.dao.ChatNotificationSettingsDao
import com.zaxo.app.model.ChatNotificationSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val settingsDao: ChatNotificationSettingsDao,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    val chatSettings: StateFlow<List<ChatNotificationSettings>> = settingsDao.getAllSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateMuteSetting(chatId: String, muted: Boolean) {
        viewModelScope.launch {
            val existing = settingsDao.getSettingsForChat(chatId)
            if (existing != null) {
                settingsDao.insertSettings(existing.copy(isMuted = muted))
            } else {
                settingsDao.insertSettings(ChatNotificationSettings(chatId = chatId, isMuted = muted))
            }
            // F68: Sync to Firestore
            syncToFirestore(chatId)
        }
    }

    fun updateSoundSetting(chatId: String, enabled: Boolean) {
        viewModelScope.launch {
            val existing = settingsDao.getSettingsForChat(chatId)
            if (existing != null) {
                settingsDao.insertSettings(existing.copy(soundEnabled = enabled))
            } else {
                settingsDao.insertSettings(ChatNotificationSettings(chatId = chatId, soundEnabled = enabled))
            }
            syncToFirestore(chatId)
        }
    }

    fun updateVibrationSetting(chatId: String, enabled: Boolean) {
        viewModelScope.launch {
            val existing = settingsDao.getSettingsForChat(chatId)
            if (existing != null) {
                settingsDao.insertSettings(existing.copy(vibrationEnabled = enabled))
            } else {
                settingsDao.insertSettings(ChatNotificationSettings(chatId = chatId, vibrationEnabled = enabled))
            }
            syncToFirestore(chatId)
        }
    }

    fun resetAllSettings() {
        viewModelScope.launch {
            settingsDao.deleteAllSettings()
            // F68: Also clear from Firestore
            val userId = auth.currentUser?.uid ?: return@launch
            try {
                firestore.collection("users")
                    .document(userId)
                    .collection("chatNotifications")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        snapshot.documents.forEach { doc ->
                            doc.reference.delete()
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to reset notification settings in Firestore")
            }
        }
    }

    // F68: Sync per-chat notification settings to Firestore
    private suspend fun syncToFirestore(chatId: String) {
        val userId = auth.currentUser?.uid ?: return
        val settings = settingsDao.getSettingsForChat(chatId) ?: return
        try {
            firestore.collection("users")
                .document(userId)
                .collection("chatNotifications")
                .document(chatId)
                .set(mapOf(
                    "isMuted" to settings.isMuted,
                    "soundEnabled" to settings.soundEnabled,
                    "vibrationEnabled" to settings.vibrationEnabled,
                    "soundUri" to settings.soundUri
                ))
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync notification settings to Firestore for chat $chatId")
        }
    }
}
