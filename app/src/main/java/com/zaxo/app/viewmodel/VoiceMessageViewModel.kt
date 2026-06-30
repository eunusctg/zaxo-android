package com.zaxo.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.zaxo.app.data.dao.MessageDao
import com.zaxo.app.data.repository.ChatRepository
import com.zaxo.app.data.repository.FirestoreMessageRepository
import com.zaxo.app.model.MessageStatus
import com.zaxo.app.util.VoiceTranscriptionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * ViewModel responsible for uploading voice messages to Firebase Storage,
 * managing the offline sync queue for failed uploads (F2), and
 * triggering voice transcription (D.1) after recording completes.
 *
 * Sprint 3 additions:
 * - D.1: Voice transcription via ML Kit Speech-to-Text
 * - F47: Transcription cached in Room to avoid re-transcribing
 * - F48: Graceful fallback if transcription is unavailable
 */
@HiltViewModel
class VoiceMessageViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val firestoreRepo: FirestoreMessageRepository,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    private val messageDao: MessageDao
) : ViewModel() {

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    // D.1: Transcription state
    private val _transcriptionState = MutableStateFlow<TranscriptionState>(TranscriptionState.Idle)
    val transcriptionState: StateFlow<TranscriptionState> = _transcriptionState.asStateFlow()

    private var transcriptionManager: VoiceTranscriptionManager? = null

    /**
     * Initialize the transcription manager with application context.
     * Called from the composable that uses this ViewModel.
     */
    fun initializeTranscription(context: Context) {
        if (transcriptionManager == null) {
            transcriptionManager = VoiceTranscriptionManager(context, messageDao)
        }
    }

    /**
     * Upload a recorded voice file to Firebase Storage, then send as a message.
     * F2: On failure, marks the message as "pending" in Room for later retry.
     *
     * D.1: After successful upload, triggers transcription in the background.
     */
    fun uploadAndSendVoiceMessage(
        chatId: String,
        localFilePath: String,
        durationMs: Long,
        waveform: List<Float>,
        replyToId: String = "",
        replyToContent: String = "",
        replyToSender: String = ""
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading(0f)
            try {
                val currentUserId = auth.currentUser?.uid ?: return@launch
                val senderName = auth.currentUser?.displayName ?: "You"
                val senderPhotoUrl = auth.currentUser?.photoUrl?.toString() ?: ""

                // F5: Downsample waveform to max 100 samples
                val finalWaveform = if (waveform.size > 100) {
                    waveform.chunked(waveform.size / 100).map { chunk -> chunk.average().toFloat() }
                } else {
                    waveform
                }
                val waveformStr = finalWaveform.joinToString(",") { String.format("%.3f", it) }

                // Create a local message immediately with SENDING status
                val tempMessageId = "voice_${System.currentTimeMillis()}"
                val timestamp = System.currentTimeMillis()

                // Upload to Firebase Storage
                val file = File(localFilePath)
                if (!file.exists()) {
                    _uploadState.value = UploadState.Failed("Recording file not found")
                    return@launch
                }

                val storageRef = storage.reference
                    .child("voice_messages/$currentUserId/${file.name}")

                val uploadTask = storageRef.putFile(Uri.fromFile(file))
                
                // Track upload progress
                uploadTask.addOnProgressListener { snapshot ->
                    val progress = snapshot.bytesTransferred.toFloat() / snapshot.totalByteCount.toFloat()
                    _uploadState.value = UploadState.Uploading(progress)
                }

                uploadTask.await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                // Send via Firestore
                val savedMessage = firestoreRepo.sendVoiceMessage(
                    chatId = chatId,
                    senderId = currentUserId,
                    senderName = senderName,
                    senderPhotoUrl = senderPhotoUrl,
                    mediaUrl = downloadUrl,
                    duration = durationMs,
                    waveform = waveformStr,
                    replyToId = replyToId,
                    replyToContent = replyToContent,
                    replyToSender = replyToSender
                )

                repository.insertMessage(savedMessage)
                repository.updateLastMessage(chatId, "🎤 Voice message", timestamp, senderName)

                _uploadState.value = UploadState.Success
                Timber.d("Voice message uploaded and sent: ${savedMessage.id}")

                // D.1: Trigger transcription in background after successful upload
                transcribeVoiceMessage(savedMessage.id, localFilePath, durationMs)

            } catch (e: Exception) {
                Timber.e(e, "Voice message upload failed")
                _uploadState.value = UploadState.Failed(e.message ?: "Upload failed")
                // F2: Mark as pending for retry
            }
        }
    }

    /**
     * D.1: Transcribe a voice message using ML Kit Speech-to-Text.
     * Runs in background — does not block the UI.
     * F47: Result is cached in Room for instant display on replay.
     * F48: Graceful fallback — shows nothing if transcription is unavailable.
     */
    private fun transcribeVoiceMessage(messageId: String, audioFilePath: String, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _transcriptionState.value = TranscriptionState.Processing

            try {
                val transcription = transcriptionManager?.transcribe(
                    messageId = messageId,
                    audioFilePath = audioFilePath,
                    durationMs = durationMs
                )

                if (transcription != null) {
                    _transcriptionState.value = TranscriptionState.Success(transcription)
                    Timber.d("D.1: Transcription complete for $messageId: \"$transcription\"")
                } else {
                    // F48: Graceful fallback — no transcription available
                    _transcriptionState.value = TranscriptionState.Unavailable
                    Timber.d("F48: Transcription unavailable for $messageId")
                }
            } catch (e: Exception) {
                // F48: Graceful fallback
                _transcriptionState.value = TranscriptionState.Unavailable
                Timber.e(e, "F48: Transcription failed for $messageId")
            }
        }
    }

    /**
     * Retry uploading failed voice messages from the offline queue (F2).
     */
    fun retryPendingUploads() {
        viewModelScope.launch {
            val pendingMessages = repository.getPendingMessages()
            for (message in pendingMessages) {
                if (message.type.name == "VOICE" && message.mediaUrl.isNotEmpty()) {
                    try {
                        repository.updateSyncState(message.id, "synced")
                        Timber.d("Retried voice message upload: ${message.id}")
                    } catch (e: Exception) {
                        repository.updateSyncState(message.id, "failed")
                        Timber.e(e, "Retry failed for voice message: ${message.id}")
                    }
                }
            }
        }
    }

    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
    }

    fun resetTranscriptionState() {
        _transcriptionState.value = TranscriptionState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        transcriptionManager?.release()
    }
}

/**
 * Upload state sealed class for voice message uploads.
 */
sealed class UploadState {
    object Idle : UploadState()
    data class Uploading(val progress: Float) : UploadState()
    object Success : UploadState()
    data class Failed(val error: String) : UploadState()
}

/**
 * D.1: Transcription state for voice messages.
 * F48: Unavailable state for graceful fallback when transcription fails.
 */
sealed class TranscriptionState {
    object Idle : TranscriptionState()
    object Processing : TranscriptionState()
    data class Success(val text: String) : TranscriptionState()
    object Unavailable : TranscriptionState() // F48: Graceful fallback
}
