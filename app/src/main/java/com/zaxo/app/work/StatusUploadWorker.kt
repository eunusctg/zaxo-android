package com.zaxo.app.work

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.zaxo.app.data.dao.StatusDao
import com.zaxo.app.model.Status
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * WorkManager worker responsible for retrying failed status uploads (F29).
 *
 * This worker ensures that status uploads survive app closes, process deaths,
 * and transient network failures by leveraging WorkManager's guaranteed execution.
 *
 * ## Retry Policy
 * - Maximum 3 attempts with exponential backoff: 30s → 2min → 8min
 * - On transient errors → [Result.retry] (respects attempt limit)
 * - On permanent errors (auth, quota) → [Result.failure]
 *
 * ## Chunked Upload (F30)
 * Files larger than 5MB are uploaded using Firebase Storage's stream-based upload
 * mechanism to prevent timeouts on large video files. Stream uploads use Firebase's
 * internal resumable upload protocol which automatically handles chunking.
 *
 * ## Storage Quota (F31)
 * If Firebase Storage quota is exceeded, the worker returns [Result.failure]
 * with appropriate logging rather than retrying indefinitely.
 */
@HiltWorker
class StatusUploadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val statusDao: StatusDao,
    private val firestore: FirebaseFirestore,
    private val firebaseStorage: FirebaseStorage,
    private val auth: FirebaseAuth
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        /** Input data key for the status ID to upload. */
        const val KEY_STATUS_ID = "statusId"

        /** Progress data key reported via setProgressAsync. */
        const val KEY_PROGRESS = "progress"

        /** File size threshold (5 MB) above which chunked/stream upload is used (F30). */
        private const val CHUNKED_UPLOAD_THRESHOLD_BYTES = 5L * 1024L * 1024L // 5 MB

        /** Maximum number of retry attempts before giving up. */
        private const val MAX_RETRY_ATTEMPTS = 3

        /** Firebase Storage path template for status media. */
        private const val STORAGE_PATH_TEMPLATE = "users/%s/statuses/%s.jpg"

        /**
         * Firebase Storage error codes that indicate quota exceeded (F31).
         * These are permanent errors — retrying will not help.
         */
        private val QUOTA_EXCEEDED_ERROR_CODES = setOf(
            "quota-exceeded",
            "project-quota-exceeded",
            "bucket-quota-exceeded"
        )

        /**
         * Firebase Storage error codes that indicate authentication failure.
         * These are permanent errors — the user needs to re-authenticate.
         */
        private val AUTH_ERROR_CODES = setOf(
            "unauthenticated",
            "unauthorized"
        )

        // ───────────────────────── Work Request Builders ─────────────────────────

        /**
         * Creates a [OneTimeWorkRequest] for uploading a single status.
         *
         * Configured with:
         * - Exponential backoff starting at 30s (30s → 2min → 8min)
         * - Network connectivity requirement
         *
         * @param statusId The ID of the status to upload
         * @return A [OneTimeWorkRequest] ready to be enqueued
         */
        fun createWorkRequest(statusId: String): OneTimeWorkRequest {
            val inputData = Data.Builder()
                .putString(KEY_STATUS_ID, statusId)
                .build()

            return OneTimeWorkRequest.Builder(StatusUploadWorker::class.java)
                .setInputData(inputData)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30_000L, // Initial backoff: 30 seconds
                    TimeUnit.MILLISECONDS
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        }

        /**
         * Creates a [PeriodicWorkRequest] that scans for all failed statuses
         * and processes them. Runs every 15 minutes (WorkManager minimum interval).
         *
         * @return A [PeriodicWorkRequest] ready to be enqueued
         */
        fun createPeriodicRetryWorkRequest(): PeriodicWorkRequest {
            return PeriodicWorkRequest.Builder(
                StatusUploadWorker::class.java,
                15L,
                TimeUnit.MINUTES
            )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30_000L,
                    TimeUnit.MILLISECONDS
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        }
    }

    // ───────────────────────── Main Entry Point ─────────────────────────

    override suspend fun doWork(): Result {
        val statusId = inputData.getString(KEY_STATUS_ID)

        Timber.d("StatusUploadWorker started — statusId=%s, attempt=%d", statusId, runAttemptCount)

        // If no specific statusId provided, process all failed statuses as a batch
        if (statusId.isNullOrBlank()) {
            return processAllFailedStatuses()
        }

        return processSingleStatus(statusId)
    }

    // ───────────────────────── Batch Processing ─────────────────────────

    /**
     * Process all statuses with syncState = "failed".
     * Used when the worker is triggered by a periodic retry scan without a specific ID.
     */
    private suspend fun processAllFailedStatuses(): Result {
        Timber.d("Processing all failed statuses (batch mode)")

        val failedStatuses = try {
            statusDao.getFailedStatuses()
        } catch (e: Exception) {
            Timber.e(e, "Failed to query failed statuses from Room")
            return resolveRetryOrFailure()
        }

        if (failedStatuses.isEmpty()) {
            Timber.d("No failed statuses found — nothing to do")
            return Result.success()
        }

        Timber.d("Found %d failed status(es) to retry", failedStatuses.size)

        var hasPermanentFailure = false
        var hasTransientFailure = false

        for (status in failedStatuses) {
            when (val result = processSingleStatus(status.id)) {
                is Result.failure -> {
                    hasPermanentFailure = true
                    Timber.w("Permanent failure for status %s", status.id)
                }
                is Result.retry -> {
                    hasTransientFailure = true
                }
                is Result.success -> {
                    Timber.d("Status %s uploaded successfully", status.id)
                }
            }
        }

        return when {
            hasTransientFailure -> Result.retry()
            hasPermanentFailure -> Result.failure()
            else -> Result.success()
        }
    }

    // ───────────────────────── Single Status Processing ─────────────────────────

    /**
     * Process a single status upload by ID.
     *
     * Algorithm:
     * 1. Query Room for the status
     * 2. If not found or already synced → return success
     * 3. If status has a local media file → upload to Firebase Storage
     * 4. Write status to Firestore
     * 5. Update Room: syncState = "synced", mediaUrl = downloadUrl
     * 6. On transient error → Result.retry() (up to [MAX_RETRY_ATTEMPTS])
     * 7. On permanent error (auth, quota) → Result.failure()
     */
    private suspend fun processSingleStatus(statusId: String): Result {
        // Step 1: Query Room for the status
        val status = try {
            statusDao.getStatusById(statusId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to query status %s from Room", statusId)
            return resolveRetryOrFailure()
        }

        // Step 2: If not found or already synced → nothing to do
        if (status == null) {
            Timber.d("Status %s not found in Room — treating as success", statusId)
            return Result.success()
        }

        if (status.syncState == "synced") {
            Timber.d("Status %s already synced — skipping", statusId)
            return Result.success()
        }

        // Guard: ensure user is authenticated before any network operations
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Timber.e("User not authenticated — permanent failure for status %s", statusId)
            return Result.failure()
        }

        val uid = currentUser.uid

        try {
            // Step 3: Upload media if mediaUrl is a local file path
            var finalMediaUrl = status.mediaUrl

            if (status.mediaUrl.isNotBlank() && isLocalFilePath(status.mediaUrl)) {
                Timber.d("Uploading local media for status %s from %s", statusId, status.mediaUrl)
                finalMediaUrl = uploadMediaToStorage(status, uid)
                Timber.d("Media uploaded for status %s — download URL obtained", statusId)
            }

            // Step 4: Write status document to Firestore at statuses/{statusId}
            writeToFirestore(status, finalMediaUrl)

            // Step 5: Update Room — mark as synced with the remote download URL
            statusDao.updateSyncStateAndMediaUrl(statusId, "synced", finalMediaUrl)

            Timber.i("Status %s synced successfully", statusId)
            return Result.success()

        } catch (e: StorageException) {
            return handleStorageException(e, statusId)

        } catch (e: com.google.firebase.auth.FirebaseAuthException) {
            // Auth error from Firebase — permanent failure, retrying won't help
            Timber.e(e, "Firebase auth error — permanent failure for status %s", statusId)
            return Result.failure()

        } catch (e: Exception) {
            Timber.e(e, "Transient error uploading status %s (attempt %d)", statusId, runAttemptCount + 1)
            return resolveRetryOrFailure()
        }
    }

    // ───────────────────────── Media Upload ─────────────────────────

    /**
     * Upload a local media file to Firebase Storage.
     *
     * For files ≤ 5MB, uses [putFile] with a content URI.
     * For files > 5MB (F30), uses [putStream] which leverages Firebase's internal
     * resumable upload protocol — automatically chunking the upload and preventing
     * timeouts on large video files.
     *
     * @param status The status containing the local media file path
     * @param uid The current user's Firebase UID
     * @return The Firebase Storage download URL as a string
     * @throws StorageException If Firebase Storage encounters a permanent error (quota, etc.)
     * @throws Exception For transient errors that should trigger a retry
     */
    private suspend fun uploadMediaToStorage(status: Status, uid: String): String {
        val localFile = File(status.mediaUrl)

        if (!localFile.exists()) {
            throw IllegalStateException("Local media file not found: ${status.mediaUrl}")
        }

        val fileSize = localFile.length()
        val storagePath = STORAGE_PATH_TEMPLATE.format(uid, status.id)
        val storageRef = firebaseStorage.reference.child(storagePath)

        val metadata = StorageMetadata.Builder()
            .setContentType(resolveContentType(status, localFile))
            .setCustomMetadata("statusId", status.id)
            .setCustomMetadata("userId", uid)
            .setCustomMetadata("statusType", status.type.value)
            .build()

        val downloadUrl = if (fileSize > CHUNKED_UPLOAD_THRESHOLD_BYTES) {
            Timber.d(
                "File size %d bytes exceeds threshold %d bytes — using stream/chunked upload (F30)",
                fileSize, CHUNKED_UPLOAD_THRESHOLD_BYTES
            )
            uploadWithStream(storageRef, localFile, metadata)
        } else {
            Timber.d("File size %d bytes — using standard putFile upload", fileSize)
            uploadWithPutFile(storageRef, localFile, metadata)
        }

        return downloadUrl.toString()
    }

    /**
     * Standard upload for files ≤ 5MB using [StorageReference.putFile].
     * Converts the local file path to a content URI via [FileProvider].
     *
     * @return The download [Uri] for the uploaded file
     */
    private suspend fun uploadWithPutFile(
        storageRef: com.google.firebase.storage.StorageReference,
        file: File,
        metadata: StorageMetadata
    ): Uri {
        val contentUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )

        // Perform the upload with progress tracking via suspendCoroutine
        val snapshot = suspendCoroutine { continuation ->
            storageRef.putFile(contentUri, metadata)
                .addOnProgressListener { snap ->
                    reportProgress(snap.bytesTransferred, snap.totalByteCount)
                }
                .addOnSuccessListener { snap ->
                    continuation.resume(snap)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }

        Timber.d("Standard upload complete — %d bytes transferred", snapshot.bytesTransferred)

        // Get the download URL
        return snapshot.storage.downloadUrl.await()
    }

    /**
     * Stream-based upload for files > 5MB (F30).
     *
     * Uses [StorageReference.putStream] which leverages Firebase Storage's internal
     * resumable upload protocol. This protocol automatically breaks the upload into
     * chunks and supports resuming interrupted uploads, preventing timeouts on large
     * video files.
     *
     * @return The download [Uri] for the uploaded file
     */
    private suspend fun uploadWithStream(
        storageRef: com.google.firebase.storage.StorageReference,
        file: File,
        metadata: StorageMetadata
    ): Uri {
        Timber.d("Starting stream upload for %s (%d bytes)", file.name, file.length())

        val stream = FileInputStream(file)

        // Perform the stream upload with progress tracking via suspendCoroutine
        val snapshot = try {
            suspendCoroutine { continuation ->
                storageRef.putStream(stream, metadata)
                    .addOnProgressListener { snap ->
                        reportProgress(snap.bytesTransferred, snap.totalByteCount)
                    }
                    .addOnSuccessListener { snap ->
                        continuation.resume(snap)
                    }
                    .addOnFailureListener { e ->
                        continuation.resumeWithException(e)
                    }
            }
        } finally {
            runCatching { stream.close() }.onFailure { e ->
                Timber.w(e, "Failed to close upload stream")
            }
        }

        Timber.d("Stream upload complete — %d bytes transferred", snapshot.bytesTransferred)

        // Get the download URL
        return snapshot.storage.downloadUrl.await()
    }

    // ───────────────────────── Firestore Write ─────────────────────────

    /**
     * Write a status document to Firestore at `statuses/{statusId}`.
     *
     * Excludes internal fields (syncState) that are only relevant for local
     * Room persistence and should not be written to the remote document.
     *
     * @param status The status to write
     * @param mediaUrl The final media URL (may differ from status.mediaUrl if uploaded)
     */
    private suspend fun writeToFirestore(status: Status, mediaUrl: String) {
        val docData = mapOf<String, Any?>(
            "id" to status.id,
            "userId" to status.userId,
            "userName" to status.userName,
            "userPhotoUrl" to status.userPhotoUrl,
            "type" to status.type.value,
            "mediaUrl" to mediaUrl,
            "textContent" to status.textContent,
            "backgroundColor" to status.backgroundColor,
            "fontFamily" to status.fontFamily,
            "createdAt" to status.createdAt,
            "expiresAt" to status.expiresAt,
            "isViewed" to status.isViewed
        )

        firestore.collection("statuses")
            .document(status.id)
            .set(docData)
            .await()

        Timber.d("Firestore document written for status %s", status.id)
    }

    // ───────────────────────── Error Handling ─────────────────────────

    /**
     * Handle a [StorageException] from Firebase Storage.
     *
     * Inspects the error code to determine whether the error is permanent
     * (quota exceeded F31, auth failure) or transient (network, timeout).
     *
     * @param e The storage exception
     * @param statusId The status ID for logging
     * @return [Result.failure] for permanent errors, [Result.retry] otherwise
     */
    private fun handleStorageException(e: StorageException, statusId: String): Result {
        val errorCode = e.errorCode
        val errorMessage = e.message ?: "Unknown storage error"

        return when {
            // F31: Storage quota exceeded — permanent failure
            QUOTA_EXCEEDED_ERROR_CODES.contains(e.errorCode.toString()) ||
                errorMessage.contains("quota", ignoreCase = true) -> {
                Timber.e(e, "Firebase Storage quota exceeded — permanent failure for status %s (F31)", statusId)
                Result.failure()
            }

            // Auth errors — permanent failure
            AUTH_ERROR_CODES.contains(e.errorCode.toString()) -> {
                Timber.e(e, "Firebase Storage auth error — permanent failure for status %s", statusId)
                Result.failure()
            }

            // Everything else — transient, retry with backoff
            else -> {
                Timber.e(e, "Firebase Storage transient error for status %s (code=%d)", statusId, errorCode)
                resolveRetryOrFailure()
            }
        }
    }

    /**
     * Determine whether to retry or fail based on the current attempt count.
     *
     * After [MAX_RETRY_ATTEMPTS] attempts, the worker gives up and returns [Result.failure].
     *
     * Backoff schedule with exponential policy (30s initial):
     * - Attempt 1 → retry after ~30s
     * - Attempt 2 → retry after ~2min
     * - Attempt 3 → retry after ~8min
     * - Attempt 4+ → permanent failure
     */
    private fun resolveRetryOrFailure(): Result {
        return if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Timber.d("Retrying — attempt %d of %d", runAttemptCount + 1, MAX_RETRY_ATTEMPTS)
            Result.retry()
        } else {
            Timber.w("Max retry attempts (%d) exceeded — giving up", MAX_RETRY_ATTEMPTS)
            Result.failure()
        }
    }

    // ───────────────────────── Helpers ─────────────────────────

    /**
     * Report upload progress via [setProgressAsync] so that observers
     * can track the upload status in real-time.
     */
    private fun reportProgress(bytesTransferred: Long, totalByteCount: Long) {
        val progress = if (totalByteCount > 0) {
            (100.0 * bytesTransferred / totalByteCount).toInt()
        } else {
            0
        }
        setProgressAsync(
            Data.Builder()
                .putInt(KEY_PROGRESS, progress)
                .build()
        )
        Timber.v("Upload progress: %d%% (%d/%d bytes)", progress, bytesTransferred, totalByteCount)
    }

    /**
     * Determine if a [mediaUrl] refers to a local file path (as opposed to a remote URL).
     *
     * Local paths are identified by:
     * - Starting with '/' (absolute path)
     * - Starting with 'file://' scheme
     * - Starting with the app's internal storage or cache directory paths
     */
    private fun isLocalFilePath(path: String): Boolean {
        return path.startsWith("/") ||
            path.startsWith("file://") ||
            path.startsWith(appContext.filesDir.absolutePath) ||
            path.startsWith(appContext.cacheDir.absolutePath)
    }

    /**
     * Resolve the MIME content type for a status media file.
     *
     * Prioritizes the file extension over the status type for accuracy,
     * falling back to the status type, then to a generic binary type.
     */
    private fun resolveContentType(status: Status, file: File): String {
        // Try file extension first (most accurate)
        val extension = file.extension.lowercase()
        val typeFromExtension = when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "3gp" -> "video/3gpp"
            "webm" -> "video/webm"
            else -> null
        }

        if (typeFromExtension != null) return typeFromExtension

        // Fall back to status type
        return when (status.type.value) {
            "video" -> "video/mp4"
            "photo" -> "image/jpeg"
            "text" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
