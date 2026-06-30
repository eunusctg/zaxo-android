package com.zaxo.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zaxo.app.data.dao.ChatDao
import com.zaxo.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel responsible for managing chat wallpaper selection and persistence.
 *
 * State:
 * - [selectedWallpaper]: The currently selected wallpaper in the picker UI (nullable, null = default).
 * - [currentWallpaper]: The wallpaper currently saved for the chat from the database.
 * - [isApplying]: Whether an apply/reset operation is in progress.
 *
 * Features:
 * - F11: Custom wallpaper images are resized to max 1080px dimension before saving
 * - F12: Syncs wallpaper changes to Firestore at `users/{uid}/chats/{chatId}.wallpaper`
 * - Persists wallpaper locally via Room ([ChatDao.updateWallpaper])
 * - Supports built-in wallpaper IDs and custom photo URIs (prefixed with "custom:")
 */
@HiltViewModel
class WallpaperViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val chatDao: ChatDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val application: Application
) : ViewModel() {

    companion object {
        /** F11: Maximum dimension for custom wallpaper images. */
        private const val CUSTOM_WALLPAPER_MAX_DIMENSION = 1080

        /** JPEG quality for saved wallpapers (0-100). */
        private const val WALLPAPER_JPEG_QUALITY = 85
    }

    private val chatId: String = savedStateHandle["chatId"] ?: ""

    private val _selectedWallpaper = MutableStateFlow<String?>(null)
    val selectedWallpaper: StateFlow<String?> = _selectedWallpaper.asStateFlow()

    private val _currentWallpaper = MutableStateFlow<String?>(null)
    val currentWallpaper: StateFlow<String?> = _currentWallpaper.asStateFlow()

    private val _isApplying = MutableStateFlow(false)
    val isApplying: StateFlow<Boolean> = _isApplying.asStateFlow()

    init {
        loadCurrentWallpaper()
    }

    /**
     * Loads the current wallpaper for the chat from the local database.
     * Initializes [selectedWallpaper] to match so the picker reflects the active wallpaper.
     */
    fun loadCurrentWallpaper() {
        viewModelScope.launch {
            val chat = repository.getChatByIdSync(chatId)
            val wallpaper = chat?.wallpaperUrl
            _currentWallpaper.value = wallpaper
            // Initialize selection to current wallpaper (empty string means default)
            _selectedWallpaper.value = if (wallpaper.isNullOrEmpty()) null else wallpaper
        }
    }

    /**
     * Updates the selected wallpaper state in the UI without persisting.
     *
     * @param wallpaperId The wallpaper ID to select (built-in ID or "custom:" URI),
     *                    or null to indicate no selection / default.
     */
    fun selectWallpaper(wallpaperId: String?) {
        _selectedWallpaper.value = wallpaperId
    }

    /**
     * F11: Resize a custom wallpaper image URI to max 1080px dimension.
     *
     * This method reads the image from the content URI, calculates the appropriate
     * sample size to reduce memory usage during decoding, decodes the bitmap at
     * the reduced size, then further scales it if needed to fit within
     * [CUSTOM_WALLPAPER_MAX_DIMENSION]. The resized bitmap is saved to internal
     * storage as a JPEG and the local file path is returned.
     *
     * Algorithm:
     * 1. Read BitmapFactory.Options.inJustDecodeBounds to get original dimensions
     * 2. Calculate inSampleSize = floor(original / target)
     * 3. Decode with inSampleSize for memory-efficient loading
     * 4. If still larger than target, use Matrix.setScale for exact resize
     * 5. Save to app's wallpaper directory as JPEG
     *
     * @param uri The content URI of the selected image
     * @return The local file path of the resized image, or null on failure
     */
    suspend fun resizeAndSaveCustomWallpaper(uri: Uri): String? {
        return try {
            val contentResolver = application.contentResolver

            // Step 1: Read bounds without decoding the full bitmap
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: return null

            val originalWidth = boundsOptions.outWidth
            val originalHeight = boundsOptions.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) return null

            // Step 2: Calculate sample size for memory-efficient decoding
            val targetDim = CUSTOM_WALLPAPER_MAX_DIMENSION
            val sampleSize = calculateInSampleSize(originalWidth, originalHeight, targetDim, targetDim)

            // Step 3: Decode at sampled size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val sampledBitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // Step 4: Further scale if still larger than target
            val finalBitmap = if (sampledBitmap.width > targetDim || sampledBitmap.height > targetDim) {
                val scale = minOf(
                    targetDim.toFloat() / sampledBitmap.width,
                    targetDim.toFloat() / sampledBitmap.height
                )
                val matrix = Matrix().apply { setScale(scale, scale) }
                Bitmap.createBitmap(
                    sampledBitmap, 0, 0,
                    sampledBitmap.width, sampledBitmap.height,
                    matrix, true
                ).also { if (it != sampledBitmap) sampledBitmap.recycle() }
            } else {
                sampledBitmap
            }

            // Step 5: Save to internal storage
            val wallpaperDir = File(application.filesDir, "wallpapers").apply { mkdirs() }
            val fileName = "wallpaper_${chatId}_${UUID.randomUUID()}.jpg"
            val outputFile = File(wallpaperDir, fileName)

            FileOutputStream(outputFile).use { fos ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, WALLPAPER_JPEG_QUALITY, fos)
                fos.flush()
            }

            finalBitmap.recycle()

            Timber.d("F11: Wallpaper resized from ${originalWidth}x${originalHeight} to ${finalBitmap.width}x${finalBitmap.height}, saved to ${outputFile.absolutePath}")
            outputFile.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "F11: Failed to resize custom wallpaper")
            null
        }
    }

    /**
     * Calculate inSampleSize for BitmapFactory decoding.
     *
     * The sample size is the largest power of 2 such that both dimensions
     * after sampling are still >= the requested width/height. This avoids
     * loading the full-resolution image into memory.
     */
    private fun calculateInSampleSize(
        originalWidth: Int, originalHeight: Int,
        reqWidth: Int, reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (originalHeight > reqHeight || originalWidth > reqWidth) {
            val halfHeight = originalHeight / 2
            val halfWidth = originalWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Applies the currently selected wallpaper to the chat.
     *
     * Persists locally via Room ([ChatDao.updateWallpaper]) and syncs to Firestore
     * at `users/{uid}/chats/{chatId}` with the `wallpaper` field (F12).
     *
     * If no wallpaper is selected, this is a no-op.
     */
    fun applyWallpaper() {
        val wallpaper = _selectedWallpaper.value ?: return
        viewModelScope.launch {
            _isApplying.value = true
            try {
                // Save to local Room database
                chatDao.updateWallpaper(chatId, wallpaper)

                // Update current wallpaper state
                _currentWallpaper.value = wallpaper

                // F12: Sync wallpaper to Firestore
                syncWallpaperToFirestore(wallpaper)
            } finally {
                _isApplying.value = false
            }
        }
    }

    /**
     * Resets the chat wallpaper to the default (empty string).
     *
     * Persists the reset locally via Room and syncs to Firestore (F12).
     */
    fun resetWallpaper() {
        viewModelScope.launch {
            _isApplying.value = true
            try {
                // Reset to empty string (default) in local Room database
                chatDao.updateWallpaper(chatId, "")

                // Update state
                _selectedWallpaper.value = null
                _currentWallpaper.value = null

                // F12: Sync reset to Firestore
                syncWallpaperToFirestore("")
            } finally {
                _isApplying.value = false
            }
        }
    }

    /**
     * F12: Syncs the wallpaper value to Firestore at `users/{uid}/chats/{chatId}.wallpaper`.
     *
     * This ensures the wallpaper preference is available across devices.
     * Failures are silently caught to avoid disrupting the user experience;
     * the local Room database remains the source of truth.
     *
     * @param wallpaperUrl The wallpaper value to sync. Empty string means default/no wallpaper.
     */
    private fun syncWallpaperToFirestore(wallpaperUrl: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore
            .collection("users")
            .document(uid)
            .collection("chats")
            .document(chatId)
            .update("wallpaper", wallpaperUrl)
            .addOnFailureListener { e ->
                // Silent failure — local persistence is the source of truth.
                // In production, you may want to log this or schedule a retry.
            }
    }
}
