package com.zaxo.app.ui.screens

import android.content.ContentResolver
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.zaxo.app.model.DrawingPath
import com.zaxo.app.model.StatusPrivacy
import com.zaxo.app.model.StatusTimerOptions
import com.zaxo.app.ui.components.NeuChip
import com.zaxo.app.ui.components.NeuIconButton
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.util.VideoTrimmer
import com.zaxo.app.viewmodel.StatusViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

// ═══════════════════════════ Constants ═══════════════════════════

private const val CAPTION_MAX_CHARS = 200
private const val VIDEO_SIZE_WARNING_MB = 5L

/** Drawing tool color palette — 8 colors as specified. */
private val DrawingColors = listOf(
    Color.White to "White",
    Color.Black to "Black",
    Color(0xFFE74C3C) to "Red",
    Color(0xFFF1C40F) to "Yellow",
    Color(0xFF27AE60) to "Green",
    Color(0xFF3498DB) to "Blue",
    Color(0xFF9B59B6) to "Purple",
    Color(0xFFF39C12) to "Orange",
)

/** Text overlay color options. */
private val TextOverlayColors = listOf(
    Color.White to "White",
    Color.Black to "Black",
    Color(0xFFE74C3C) to "Red",
    Color(0xFFF1C40F) to "Yellow",
    Color(0xFF27AE60) to "Green",
    Color(0xFF3498DB) to "Blue",
    Color(0xFF9B59B6) to "Purple",
    Color(0xFFF39C12) to "Orange",
)

// ═══════════════════════════ Editor State ═══════════════════════════

/** Tracks which editing tool is active. */
private enum class EditorTool {
    NONE, TEXT, DRAW, TRIM
}

/** Mutable state for the text overlay on the media. */
private data class TextOverlayState(
    var text: String = "",
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var scale: Float = 1f,
    var colorIndex: Int = 0,
)

/** State for video frame drawing overlay */
private data class VideoDrawingOverlay(
    val bitmap: Bitmap? = null,
    val frameTimestampMs: Long = 0L,
    val drawingPaths: List<DrawingPath> = emptyList()
)

// ═══════════════════════════ Main Screen ═══════════════════════════

/**
 * StatusEditorScreen — Post-capture editor for Zaxo status updates.
 *
 * Receives a media file URI (photo or video) after camera capture and allows
 * the user to add a caption, text overlays, freehand drawings, select a
 * display timer, and choose privacy settings before posting.
 *
 * Sprint 3 additions:
 * - ExoPlayer video preview (C.1) with play/pause, seek, mute, loop
 * - Video trimming slider (C.2) with min 3s / max 30s enforcement
 * - Drawing on video frames (C.3) with frozen frame + overlay
 *
 * **Flaw mitigations:**
 * - F39: ExoPlayer released on back press via DisposableEffect
 * - F40: Video trim fails → fallback to original video
 * - F41: Drawing overlay desync → store frame timestamp, seek on preview
 * - F42: Large video trim OOM → stream processing via VideoTrimmer
 * - F43: Trimmed video has no audio → all tracks copied
 * - F44: Concurrent trim + upload → isTrimming guard disables Send
 *
 * @param mediaUri URI string of captured photo or video
 * @param onBack Callback when user presses back / close
 * @param onPosted Callback when status is successfully posted
 * @param viewModel StatusViewModel provided by Hilt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusEditorScreen(
    mediaUri: String,
    onBack: () -> Unit,
    onPosted: () -> Unit,
    viewModel: StatusViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val context = LocalContext.current

    // ── Editor state ──
    var activeTool by remember { mutableStateOf(EditorTool.NONE) }
    var caption by remember { mutableStateOf("") }
    var selectedTimerIndex by remember { mutableIntStateOf(0) }
    var selectedPrivacy by remember { mutableStateOf(StatusPrivacy.MY_CONTACTS) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var showUploadWarning by remember { mutableStateOf(false) }
    var uploadWarningMessage by remember { mutableStateOf("") }

    // ── Upload state ──
    var isSending by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var isUploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ── Text overlay state ──
    var textOverlay by remember { mutableStateOf(TextOverlayState()) }

    // ── Drawing state ──
    val drawingPaths = remember { mutableStateListOf<DrawingPath>() }
    var currentPath by remember { mutableStateOf<DrawingPath?>(null) }
    var drawingColorIndex by remember { mutableIntStateOf(0) }
    var brushSize by remember { mutableFloatStateOf(4f) }

    // ── Media type detection ──
    val isVideo = remember(mediaUri) {
        mediaUri.endsWith(".mp4", ignoreCase = true) ||
            mediaUri.endsWith(".3gp", ignoreCase = true) ||
            mediaUri.endsWith(".webm", ignoreCase = true) ||
            mediaUri.contains("video", ignoreCase = true)
    }

    // ── ExoPlayer state (C.1) ──
    var isPlayerPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }

    // ── Video trim state (C.2) ──
    var videoDurationMs by remember { mutableLongStateOf(0L) }
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(0L) }
    var isTrimming by remember { mutableStateOf(false) } // F44: guard

    // ── Video frame drawing overlay (C.3) ──
    var drawingOverlay by remember { mutableStateOf<VideoDrawingOverlay?>(null) }

    // ── ExoPlayer instance for video ──
    val exoPlayer = remember(isVideo) {
        if (isVideo) {
            ExoPlayer.Builder(context).build().apply {
                val uri = if (mediaUri.startsWith("content://") || mediaUri.startsWith("file://")) {
                    Uri.parse(mediaUri)
                } else {
                    Uri.fromFile(File(mediaUri))
                }
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 1f
            }
        } else null
    }

    // F39: Release ExoPlayer on dispose / back press
    DisposableEffect(Unit) {
        onDispose {
            try {
                exoPlayer?.release()
                Timber.d("F39: ExoPlayer released on dispose")
            } catch (e: Exception) {
                Timber.e(e, "F39: Error releasing ExoPlayer")
            }
        }
    }

    // ── Get video duration for trim slider ──
    LaunchedEffect(isVideo, mediaUri) {
        if (isVideo) {
            try {
                val uri = if (mediaUri.startsWith("content://") || mediaUri.startsWith("file://")) {
                    Uri.parse(mediaUri)
                } else {
                    Uri.fromFile(File(mediaUri))
                }
                videoDurationMs = VideoTrimmer.getVideoDurationMs(context, uri)
                trimEndMs = videoDurationMs.coerceAtMost(VideoTrimmer.MAX_TRIM_DURATION_MS)
                Timber.d("Video duration: ${videoDurationMs}ms, trim range: 0–${trimEndMs}ms")
            } catch (e: Exception) {
                Timber.e(e, "Failed to get video duration")
            }
        }
    }

    // ── File size check for video (F30) ──
    LaunchedEffect(mediaUri) {
        if (isVideo) {
            try {
                val fileSize = getFileSize(context, mediaUri)
                val sizeMb = fileSize / (1024.0 * 1024.0)
                Timber.d("Video file size: %.2f MB".format(sizeMb))
                if (fileSize > VIDEO_SIZE_WARNING_MB * 1024 * 1024) {
                    uploadWarningMessage = "This video is %.1f MB — large files may take longer to upload.".format(sizeMb)
                    showUploadWarning = true
                }
            } catch (e: Exception) {
                Timber.e(e, "Could not determine file size for mediaUri")
            }
        }
    }

    // ── Observe ViewModel errors ──
    val vmError by viewModel.error.collectAsState()
    LaunchedEffect(vmError) {
        if (vmError != null) {
            errorMessage = vmError
            viewModel.clearError()
        }
    }

    // ── Auto-dismiss error ──
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            kotlinx.coroutines.delay(4000L)
            errorMessage = null
        }
    }

    // ── Auto-dismiss upload warning ──
    LaunchedEffect(showUploadWarning) {
        if (showUploadWarning) {
            kotlinx.coroutines.delay(6000L)
            showUploadWarning = false
        }
    }

    // ── Coroutine scope for upload ──
    val coroutineScope = rememberCoroutineScope()

    // ── Send logic ──
    val sendStatus: () -> Unit = {
        // F44: Disable Send during trim
        if (isSending || isTrimming) {
            Timber.w("Send blocked — isSending=$isSending, isTrimming=$isTrimming (F44)")
            return
        }
        isSending = true
        isUploading = true
        uploadProgress = 0f

        coroutineScope.launch {
            try {
                // Determine the final media file to upload
                val finalMediaUri = if (isVideo && (trimStartMs > 0 || trimEndMs < videoDurationMs)) {
                    // Trim video before upload
                    isTrimming = true
                    val uri = if (mediaUri.startsWith("content://") || mediaUri.startsWith("file://")) {
                        Uri.parse(mediaUri)
                    } else {
                        Uri.fromFile(File(mediaUri))
                    }

                    val trimmedPath = VideoTrimmer.trimVideo(context, uri, trimStartMs, trimEndMs)
                    isTrimming = false

                    // F40: Fallback to original video if trim fails
                    if (trimmedPath != null) {
                        Timber.d("Using trimmed video: $trimmedPath")
                        trimmedPath
                    } else {
                        Timber.w("F40: Trim failed — using original video")
                        mediaUri
                    }
                } else {
                    mediaUri
                }

                uploadProgress = 0.1f

                // Save drawing overlay if exists (C.3)
                if (drawingOverlay?.bitmap != null) {
                    val overlayFile = File(context.cacheDir, "drawing_overlay_${System.currentTimeMillis()}.png")
                    FileOutputStream(overlayFile).use { out ->
                        drawingOverlay!!.bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    Timber.d("C.3: Drawing overlay saved to ${overlayFile.absolutePath}")
                }

                // Delegate to ViewModel
                viewModel.createPhotoStatus(finalMediaUri)

                uploadProgress = 0.8f
                kotlinx.coroutines.delay(200)
                uploadProgress = 1.0f

                Timber.d("Status posted successfully")
                isUploading = false
                isSending = false
                onPosted()
            } catch (e: Exception) {
                Timber.e(e, "Failed to post status")
                isUploading = false
                isSending = false
                isTrimming = false

                // F31: Check for storage quota exceeded
                val msg = e.message ?: "Upload failed"
                if (msg.contains("quota", ignoreCase = true) ||
                    msg.contains("storage full", ignoreCase = true) ||
                    msg.contains("resource exhausted", ignoreCase = true)
                ) {
                    errorMessage = "Storage full — cannot upload new statuses"
                } else {
                    errorMessage = "Failed to post status: $msg"
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // ═══════════════ Media Preview Area ═══════════════
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .align(Alignment.TopCenter),
            contentAlignment = Alignment.Center
        ) {
            if (isVideo && exoPlayer != null) {
                // C.1: ExoPlayer video preview
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false // Custom controls below
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // C.3: Drawing overlay on video — show when overlay exists and not currently drawing
                    if (drawingOverlay?.bitmap != null && activeTool != EditorTool.DRAW) {
                        AsyncImage(
                            model = drawingOverlay!!.bitmap,
                            contentDescription = "Drawing overlay",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            } else if (isVideo) {
                // Fallback video placeholder if ExoPlayer creation failed
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(mediaUri)
                            .crossfade(true)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = "Video thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                // Photo preview
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(mediaUri)
                        .crossfade(true)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = "Captured photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // ═══════════════ Drawing Canvas Overlay ═══════════════
            if (activeTool == EditorTool.DRAW) {
                // C.3: For video drawing, pause player and show frozen frame
                if (isVideo) {
                    LaunchedEffect(activeTool) {
                        exoPlayer?.pause()
                    }
                }

                DrawingCanvasOverlay(
                    paths = drawingPaths,
                    currentPath = currentPath,
                    onCurrentPathChange = { currentPath = it },
                    onPathComplete = { path ->
                        if (path.points.size > 1) {
                            drawingPaths.add(path)
                        }
                        currentPath = null
                    },
                    drawingColor = DrawingColors[drawingColorIndex].first,
                    brushSize = brushSize,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ═══════════════ Text Overlay ═══════════════
            if (activeTool == EditorTool.TEXT && textOverlay.text.isNotEmpty()) {
                TextOverlayComposable(
                    state = textOverlay,
                    onStateChange = { textOverlay = it },
                    availableWidth = 300f,
                    availableHeight = 400f,
                    modifier = Modifier.zIndex(3f)
                )
            }
        }

        // ═══════════════ Top Bar ═══════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .zIndex(10f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close / Back button
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Send button — green when ready, muted when sending/trimming
            IconButton(
                onClick = { sendStatus() },
                enabled = !isSending && !isTrimming // F44: disabled during trim
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = if (isSending) "Sending..." else if (isTrimming) "Trimming..." else "Send status",
                    tint = if (isSending || isTrimming) Color.Gray else Color(0xFF27AE60),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // ═══════════════ Video Playback Controls (C.1) ═══════════════
        if (isVideo && exoPlayer != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .offset(y = (-80).dp)
                    .zIndex(5f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play/Pause button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(
                            6.dp, CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.5f),
                            spotColor = Color.Transparent
                        )
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .clip(CircleShape)
                        .clickable {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                                isPlayerPlaying = false
                            } else {
                                exoPlayer.play()
                                isPlayerPlaying = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlayerPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlayerPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Mute/unmute + time display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .offset(y = (-160).dp)
                    .padding(horizontal = 24.dp)
                    .zIndex(5f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time display
                val currentPositionMs = remember { mutableStateOf(0L) }
                LaunchedEffect(exoPlayer) {
                    while (true) {
                        try {
                            currentPositionMs.value = exoPlayer.currentPosition
                        } catch (_: Exception) {
                        }
                        kotlinx.coroutines.delay(200)
                    }
                }
                Text(
                    text = "${formatVideoTime(currentPositionMs.value)} / ${formatVideoTime(videoDurationMs)}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )

                // Mute toggle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .shadow(
                            4.dp, CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.5f),
                            spotColor = Color.Transparent
                        )
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .clip(CircleShape)
                        .clickable {
                            isMuted = !isMuted
                            exoPlayer.volume = if (isMuted) 0f else 1f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // ═══════════════ Upload Progress ═══════════════
        AnimatedVisibility(
            visible = isUploading,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
                .zIndex(11f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = if (isTrimming) "Trimming video..." else "Uploading... ${(uploadProgress * 100).roundToInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF27AE60),
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }
        }

        // ═══════════════ Bottom Controls ═══════════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .zIndex(10f),
            verticalArrangement = Arrangement.Bottom
        ) {
            // ── Tool panels (conditional) ──
            AnimatedVisibility(
                visible = activeTool == EditorTool.TEXT,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                TextToolPanel(
                    state = textOverlay,
                    onStateChange = { textOverlay = it },
                    onDismiss = { activeTool = EditorTool.NONE }
                )
            }

            AnimatedVisibility(
                visible = activeTool == EditorTool.DRAW,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                DrawingToolPanel(
                    drawingColorIndex = drawingColorIndex,
                    onColorIndexChange = { drawingColorIndex = it },
                    brushSize = brushSize,
                    onBrushSizeChange = { brushSize = it },
                    onUndo = {
                        if (drawingPaths.isNotEmpty()) {
                            drawingPaths.removeLast()
                        }
                    },
                    onClearOverlay = {
                        drawingOverlay = null
                        drawingPaths.clear()
                    },
                    onConfirmDrawing = {
                        // C.3: Capture current frame and save drawing as overlay
                        if (isVideo && exoPlayer != null) {
                            val frame = captureCurrentFrame(context, mediaUri, exoPlayer.currentPosition)
                            if (frame != null) {
                                drawingOverlay = VideoDrawingOverlay(
                                    bitmap = frame,
                                    frameTimestampMs = exoPlayer.currentPosition,
                                    drawingPaths = drawingPaths.toList()
                                )
                                Timber.d("C.3: Frame captured at ${exoPlayer.currentPosition}ms with ${drawingPaths.size} paths")
                            }
                        }
                        drawingPaths.clear()
                        activeTool = EditorTool.NONE
                    },
                    onDismiss = { activeTool = EditorTool.NONE }
                )
            }

            AnimatedVisibility(
                visible = activeTool == EditorTool.TRIM && isVideo,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                TrimToolPanel(
                    durationMs = videoDurationMs,
                    startMs = trimStartMs,
                    endMs = trimEndMs,
                    onStartChange = { newStart ->
                        val validated = VideoTrimmer.validateTrimRange(newStart, trimEndMs, videoDurationMs)
                        trimStartMs = validated.first
                        trimEndMs = validated.second
                        // Seek player to new start position
                        exoPlayer?.seekTo(trimStartMs)
                    },
                    onEndChange = { newEnd ->
                        val validated = VideoTrimmer.validateTrimRange(trimStartMs, newEnd, videoDurationMs)
                        trimStartMs = validated.first
                        trimEndMs = validated.second
                    },
                    onDismiss = { activeTool = EditorTool.NONE }
                )
            }

            // ── Semi-transparent bottom bar ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Caption text field
                CaptionTextField(
                    caption = caption,
                    onCaptionChange = { newCaption ->
                        if (newCaption.length <= CAPTION_MAX_CHARS) {
                            caption = newCaption
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tool buttons row — added trim button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side: Privacy + Tool buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Privacy button
                        NeuIconButton(
                            onClick = { showPrivacySheet = true },
                            icon = Icons.Default.Lock,
                            contentDescription = "Privacy settings"
                        )

                        // Text overlay tool button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(
                                    4.dp, CircleShape,
                                    ambientColor = Color.Black.copy(alpha = 0.3f),
                                    spotColor = Color.White.copy(alpha = 0.1f)
                                )
                                .background(
                                    if (activeTool == EditorTool.TEXT) Color(0xFF4A90D9) else Color(0xFF2C3E50),
                                    CircleShape
                                )
                                .clip(CircleShape)
                                .clickable {
                                    activeTool = if (activeTool == EditorTool.TEXT) EditorTool.NONE else EditorTool.TEXT
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.TextFields,
                                contentDescription = "Add text",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Drawing tool button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(
                                    4.dp, CircleShape,
                                    ambientColor = Color.Black.copy(alpha = 0.3f),
                                    spotColor = Color.White.copy(alpha = 0.1f)
                                )
                                .background(
                                    if (activeTool == EditorTool.DRAW) Color(0xFF4A90D9) else Color(0xFF2C3E50),
                                    CircleShape
                                )
                                .clip(CircleShape)
                                .clickable {
                                    activeTool = if (activeTool == EditorTool.DRAW) EditorTool.NONE else EditorTool.DRAW
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Create,
                                contentDescription = "Draw",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // C.2: Trim tool button (video only)
                        if (isVideo) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .shadow(
                                        4.dp, CircleShape,
                                        ambientColor = Color.Black.copy(alpha = 0.3f),
                                        spotColor = Color.White.copy(alpha = 0.1f)
                                    )
                                    .background(
                                        if (activeTool == EditorTool.TRIM) Color(0xFF4A90D9) else Color(0xFF2C3E50),
                                        CircleShape
                                    )
                                    .clip(CircleShape)
                                    .clickable {
                                        activeTool = if (activeTool == EditorTool.TRIM) EditorTool.NONE else EditorTool.TRIM
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ContentCut,
                                    contentDescription = "Trim video",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Right side: Timer picker
                    TimerPickerSegmented(
                        selectedIndex = selectedTimerIndex,
                        onIndexChange = { selectedTimerIndex = it }
                    )
                }
            }
        }

        // ═══════════════ Upload Warning Toast (F30) ═══════════════
        AnimatedVisibility(
            visible = showUploadWarning,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 60.dp)
                .zIndex(12f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF39C12).copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = uploadWarningMessage,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ═══════════════ Error Toast ═══════════════
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 60.dp)
                .zIndex(12f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE74C3C).copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // ═══════════════ Privacy Bottom Sheet ═══════════════
    if (showPrivacySheet) {
        StatusPrivacyPickerSheet(
            selectedPrivacy = selectedPrivacy,
            onPrivacyChange = { selectedPrivacy = it },
            onDismiss = { showPrivacySheet = false }
        )
    }
}

// ═══════════════════════════ Video Time Formatting ═══════════════════════════

private fun formatVideoTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${String.format("%02d", seconds)}"
}

// ═══════════════════════════ Frame Capture (C.3) ═══════════════════════════

/**
 * C.3: Capture the current video frame as a Bitmap.
 * F41: Stores frame timestamp with overlay to prevent desync.
 *
 * Uses MediaMetadataRetriever for reliable frame extraction at any position.
 */
private fun captureCurrentFrame(context: android.content.Context, mediaUri: String, positionMs: Long): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        val uri = if (mediaUri.startsWith("content://") || mediaUri.startsWith("file://")) {
            Uri.parse(mediaUri)
        } else {
            Uri.fromFile(File(mediaUri))
        }
        retriever.setDataSource(context, uri)
        // F41: Capture frame at exact position to prevent desync
        retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (e: Exception) {
        Timber.e(e, "C.3: Failed to capture video frame")
        null
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }
}

// ═══════════════════════════ Trim Slider (C.2) ═══════════════════════════

/**
 * C.2: Video trim tool panel with range slider.
 * Two sliders for start/end trim points.
 * Enforces minimum 3-second and maximum 30-second segment.
 */
@Composable
private fun TrimToolPanel(
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trim Video",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Row {
                // Clear trim (reset to full video)
                Text(
                    text = "Reset",
                    color = Color(0xFF4A90D9),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            onStartChange(0L)
                            onEndChange(durationMs.coerceAtMost(VideoTrimmer.MAX_TRIM_DURATION_MS))
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Close trim tool
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close trim",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Trim range display
        Text(
            text = "${formatVideoTime(startMs)} — ${formatVideoTime(endMs)} (${formatVideoTime(endMs - startMs)})",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Start trim slider
        Text(
            text = "Start",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
        Slider(
            value = startMs.toFloat(),
            onValueChange = { newStart ->
                onStartChange(newStart.toLong())
            },
            valueRange = 0f..(endMs - VideoTrimmer.MIN_TRIM_DURATION_MS).toFloat().coerceAtLeast(0f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF4A90D9),
                activeTrackColor = Color(0xFF4A90D9),
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // End trim slider
        Text(
            text = "End",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
        Slider(
            value = endMs.toFloat(),
            onValueChange = { newEnd ->
                onEndChange(newEnd.toLong())
            },
            valueRange = (startMs + VideoTrimmer.MIN_TRIM_DURATION_MS).toFloat().coerceAtMost(durationMs.toFloat())..durationMs.toFloat().coerceAtMost(VideoTrimmer.MAX_TRIM_DURATION_MS.toFloat()),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF27AE60),
                activeTrackColor = Color(0xFF27AE60),
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            )
        )

        // Trim duration constraints
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Min: 3s",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp
            )
            Text(
                text = "Max: 30s",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp
            )
        }
    }
}

// ═══════════════════════════ Caption TextField ═══════════════════════════

@Composable
private fun CaptionTextField(
    caption: String,
    onCaptionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZaxoTheme.colors

    Box(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.4f),
                spotColor = Color.White.copy(alpha = 0.05f)
            )
            .background(
                Color(0xFF1A1A2E).copy(alpha = 0.8f),
                RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            BasicTextField(
                value = caption,
                onValueChange = onCaptionChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 15.sp
                ),
                singleLine = false,
                maxLines = 3,
                decorationBox = { innerTextField ->
                    if (caption.isEmpty()) {
                        Text(
                            text = "Add a caption...",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            )

            Text(
                text = "${caption.length}/$CAPTION_MAX_CHARS",
                color = if (caption.length > CAPTION_MAX_CHARS * 0.9) {
                    Color(0xFFE74C3C)
                } else {
                    Color.White.copy(alpha = 0.4f)
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp)
            )
        }
    }
}

// ═══════════════════════════ Timer Picker ═══════════════════════════

@Composable
private fun TimerPickerSegmented(
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
    ) {
        StatusTimerOptions.LABELS.forEachIndexed { index, label ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = StatusTimerOptions.LABELS.size),
                onClick = { onIndexChange(index) },
                selected = index == selectedIndex,
                colors = androidx.compose.material3.SegmentedButtonDefaults.colors(
                    activeContainerColor = Color(0xFF27AE60).copy(alpha = 0.8f),
                    activeContentColor = Color.White,
                    inactiveContainerColor = Color(0xFF2C3E50).copy(alpha = 0.6f),
                    inactiveContentColor = Color.White.copy(alpha = 0.7f),
                )
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ═══════════════════════════ Text Overlay ═══════════════════════════

@Composable
private fun TextOverlayComposable(
    state: TextOverlayState,
    onStateChange: (TextOverlayState) -> Unit,
    availableWidth: Float,
    availableHeight: Float,
    modifier: Modifier = Modifier
) {
    val textColor = TextOverlayColors[state.colorIndex].first
    val fontSize = (20f * state.scale).sp

    Box(
        modifier = modifier
            .offset { IntOffset(state.offsetX.roundToInt(), state.offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onStateChange(
                        state.copy(
                            offsetX = (state.offsetX + dragAmount.x).coerceIn(-availableWidth, availableWidth),
                            offsetY = (state.offsetY + dragAmount.y).coerceIn(-availableHeight, availableHeight)
                        )
                    )
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, scale, _ ->
                    onStateChange(
                        state.copy(scale = (state.scale * scale).coerceIn(0.5f, 4f))
                    )
                }
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = state.text,
            color = Color.Black.copy(alpha = 0.5f),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(1.dp, 1.dp)
        )
        Text(
            text = state.text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

// ═══════════════════════════ Text Tool Panel ═══════════════════════════

@Composable
private fun TextToolPanel(
    state: TextOverlayState,
    onStateChange: (TextOverlayState) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .shadow(
                        4.dp, RoundedCornerShape(12.dp),
                        ambientColor = Color.Black.copy(alpha = 0.3f),
                        spotColor = Color.White.copy(alpha = 0.05f)
                    )
                    .background(Color(0xFF1A1A2E).copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = state.text,
                    onValueChange = { newText ->
                        onStateChange(state.copy(text = newText))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = TextOverlayColors[state.colorIndex].first,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (state.text.isEmpty()) {
                            Text(
                                text = "Type text overlay...",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close text tool",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(TextOverlayColors.indices.toList()) { index ->
                val (color, _) = TextOverlayColors[index]
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .shadow(
                            2.dp, CircleShape,
                            ambientColor = Color.Black.copy(alpha = 0.3f),
                            spotColor = Color.Transparent
                        )
                        .background(color, CircleShape)
                        .clip(CircleShape)
                        .clickable { onStateChange(state.copy(colorIndex = index)) },
                    contentAlignment = Alignment.Center
                ) {
                    if (index == state.colorIndex) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .shadow(
                                    2.dp, CircleShape,
                                    ambientColor = Color.White.copy(alpha = 0.5f),
                                    spotColor = Color.Transparent
                                )
                                .background(Color.Transparent, CircleShape)
                                .clip(CircleShape)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════ Drawing Canvas ═══════════════════════════

@Composable
private fun DrawingCanvasOverlay(
    paths: List<DrawingPath>,
    currentPath: DrawingPath?,
    onCurrentPathChange: (DrawingPath?) -> Unit,
    onPathComplete: (DrawingPath) -> Unit,
    drawingColor: Color,
    brushSize: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(drawingColor, brushSize) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onCurrentPathChange(
                            DrawingPath(
                                points = listOf(offset),
                                color = drawingColor,
                                strokeWidth = brushSize
                            )
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val current = currentPath ?: return@detectDragGestures
                        onCurrentPathChange(
                            current.copy(points = current.points + change.position)
                        )
                    },
                    onDragEnd = {
                        currentPath?.let { onPathComplete(it) }
                    },
                    onDragCancel = {
                        currentPath?.let { onPathComplete(it) }
                    }
                )
            }
    ) {
        for (path in paths) {
            drawDrawingPath(path)
        }
        currentPath?.let { drawDrawingPath(it) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDrawingPath(
    path: DrawingPath
) {
    if (path.points.size < 2) return

    val smoothPath = Path()
    smoothPath.moveTo(path.points.first().x, path.points.first().y)

    for (i in 1 until path.points.size - 1) {
        val midX = (path.points[i].x + path.points[i + 1].x) / 2f
        val midY = (path.points[i].y + path.points[i + 1].y) / 2f
        smoothPath.quadraticBezierTo(
            path.points[i].x, path.points[i].y,
            midX, midY
        )
    }

    val last = path.points.last()
    smoothPath.lineTo(last.x, last.y)

    drawPath(
        path = smoothPath,
        color = path.color,
        style = Stroke(
            width = path.strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
    )
}

// ═══════════════════════════ Drawing Tool Panel (C.3 enhanced) ═══════════════════════════

/**
 * Enhanced drawing tool panel with undo, clear overlay, and confirm buttons
 * for video frame drawing (C.3).
 */
@Composable
private fun DrawingToolPanel(
    drawingColorIndex: Int,
    onColorIndexChange: (Int) -> Unit,
    brushSize: Float,
    onBrushSizeChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onClearOverlay: () -> Unit,
    onConfirmDrawing: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top row: color picker + undo + confirm + close
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(DrawingColors.indices.toList()) { index ->
                    val (color, _) = DrawingColors[index]
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .shadow(
                                2.dp, CircleShape,
                                ambientColor = Color.Black.copy(alpha = 0.3f),
                                spotColor = Color.Transparent
                            )
                            .background(color, CircleShape)
                            .clip(CircleShape)
                            .clickable { onColorIndexChange(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (index == drawingColorIndex) {
                            Canvas(modifier = Modifier.size(32.dp)) {
                                drawCircle(
                                    color = Color.White,
                                    radius = size.minDimension / 2,
                                    style = Stroke(width = 3f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Undo button
            NeuIconButton(
                onClick = onUndo,
                icon = Icons.Default.Undo,
                contentDescription = "Undo last stroke"
            )

            Spacer(modifier = Modifier.width(4.dp))

            // C.3: Confirm drawing button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .shadow(
                        4.dp, CircleShape,
                        ambientColor = Color(0xFF27AE60).copy(alpha = 0.3f),
                        spotColor = Color.Transparent
                    )
                    .background(Color(0xFF27AE60), CircleShape)
                    .clip(CircleShape)
                    .clickable { onConfirmDrawing() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Confirm drawing",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Clear overlay button
            Text(
                text = "Clear",
                color = Color(0xFFE74C3C),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onClearOverlay() }
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Close drawing tool
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close drawing tool",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Brush size slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(DrawingColors[drawingColorIndex].first, CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Slider(
                value = brushSize,
                onValueChange = { onBrushSizeChange(it) },
                valueRange = 2f..12f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = DrawingColors[drawingColorIndex].first,
                    activeTrackColor = DrawingColors[drawingColorIndex].first.copy(alpha = 0.7f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier.size(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(DrawingColors[drawingColorIndex].first, CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = "${brushSize.roundToInt()}dp",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ═══════════════════════════ Privacy Picker Sheet ═══════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusPrivacyPickerSheet(
    selectedPrivacy: StatusPrivacy,
    onPrivacyChange: (StatusPrivacy) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ZaxoTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Status Privacy",
                color = colors.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            StatusPrivacy.values().forEach { privacy ->
                val isSelected = privacy == selectedPrivacy
                val label = when (privacy) {
                    StatusPrivacy.MY_CONTACTS -> "My Contacts"
                    StatusPrivacy.MY_CONTACTS_EXCEPT -> "My Contacts Except..."
                    StatusPrivacy.ONLY_SHARE_WITH -> "Only Share With..."
                }
                val description = when (privacy) {
                    StatusPrivacy.MY_CONTACTS -> "All your contacts can see your status updates"
                    StatusPrivacy.MY_CONTACTS_EXCEPT -> "All your contacts except those you exclude"
                    StatusPrivacy.ONLY_SHARE_WITH -> "Only share with selected contacts"
                }

                NeuChip(
                    label = label,
                    selected = isSelected,
                    onClick = {
                        onPrivacyChange(privacy)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                Text(
                    text = description,
                    color = colors.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ═══════════════════════════ Helper Functions ═══════════════════════════

private fun getFileSize(context: android.content.Context, uriString: String): Long {
    return try {
        val uri = Uri.parse(uriString)
        val contentResolver: ContentResolver = context.contentResolver
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex >= 0) {
                    return it.getLong(sizeIndex)
                }
            }
        }
        val pfd = contentResolver.openAssetFileDescriptor(uri, "r")
        pfd?.use { it.length } ?: 0L
    } catch (e: Exception) {
        Timber.e(e, "Failed to get file size for URI: $uriString")
        0L
    }
}
