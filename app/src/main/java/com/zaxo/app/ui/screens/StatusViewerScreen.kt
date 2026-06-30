package com.zaxo.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import com.zaxo.app.model.Status
import com.zaxo.app.model.StatusType
import com.zaxo.app.ui.components.NeuAvatar
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.StatusViewerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ==================== StatusViewerScreen ====================

/**
 * Full-screen auto-advancing status viewer (WhatsApp / Instagram Stories style).
 *
 * Key design decisions:
 *   • F20 – Uses **absolute timestamps** for the timer rather than a
 *     `LaunchedEffect`-based delay counter.  This prevents the timer from
 *     resetting on recomposition and ensures accurate progress even when
 *     the composition is recreated.
 *   • F18 – When a status expires mid-view, it is silently skipped.
 *     If **all** statuses in the sequence have expired, an "expired"
 *     message is shown and the viewer calls [onBack].
 *   • Tap-left → previous, tap-right → next.
 *   • Long-press → pause (progress bars freeze); release → resume.
 *   • Photo = 5 s, Text = 7 s, Video = actual duration (placeholder 15 s).
 */
@Composable
fun StatusViewerScreen(
    onBack: () -> Unit,
    onNavigateToChat: (String) -> Unit = {},
    viewModel: StatusViewerViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val scope = rememberCoroutineScope()

    // Observe ViewModel state
    val statuses by viewModel.statuses.collectAsState()
    val viewCounts by viewModel.viewCounts.collectAsState()

    // ---- Filter expired statuses (F18) ----
    val now = System.currentTimeMillis()
    val activeStatuses = remember(statuses) {
        statuses.filter { it.expiresAt > now }
    }

    // If every status expired, show a message and exit
    var showExpiredMessage by remember { mutableStateOf(false) }

    LaunchedEffect(activeStatuses) {
        if (activeStatuses.isEmpty() && statuses.isNotEmpty()) {
            showExpiredMessage = true
            delay(2000L)
            onBack()
        }
    }

    if (showExpiredMessage && activeStatuses.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "This status has expired",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
        return
    }

    // ---- Current index ----
    var currentIndex by remember { mutableIntStateOf(0) }

    // Mark current status as viewed when it becomes active
    LaunchedEffect(currentIndex) {
        val status = activeStatuses.getOrNull(currentIndex)
        if (status != null) {
            viewModel.markAsViewed(status.id)
        }
    }

    // Clamp index when active list shrinks
    LaunchedEffect(activeStatuses.size) {
        if (currentIndex >= activeStatuses.size) {
            currentIndex = (activeStatuses.size - 1).coerceAtLeast(0)
        }
    }

    // ---- Pause state (long-press) ----
    var isPaused by remember { mutableStateOf(false) }

    // ---- Transition animation key ----
    val currentStatus = activeStatuses.getOrNull(currentIndex)

    // ---- Absolute-timestamp timer (F20) ----
    // durationMs = total display duration for current status
    val durationMs = remember(currentStatus) {
        when (currentStatus?.type) {
            StatusType.PHOTO -> 5_000L
            StatusType.TEXT -> 7_000L
            StatusType.VIDEO -> 15_000L // placeholder; real impl would query video duration
            null -> 5_000L
        }
    }

    // startTime is recorded when each status begins (or when we resume from pause)
    var startTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // How much progress was already consumed before the current "run"
    var progressBeforePause by remember { mutableFloatStateOf(0f) }

    // ---- Progress state ----
    var progress by remember { mutableFloatStateOf(0f) }

    // ---- Auto-advance loop ----
    LaunchedEffect(currentIndex, isPaused) {
        if (isPaused || currentStatus == null) return@LaunchedEffect

        startTime = System.currentTimeMillis()
        val baseProgress = progressBeforePause // progress accumulated before pause

        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val additionalProgress = elapsed.toFloat() / durationMs.toFloat()
            progress = (baseProgress + additionalProgress).coerceIn(0f, 1f)

            if (progress >= 1f) {
                // Move to next status
                if (currentIndex < activeStatuses.lastIndex) {
                    currentIndex++
                    progressBeforePause = 0f
                    progress = 0f
                    startTime = System.currentTimeMillis()
                } else {
                    // End of all statuses
                    onBack()
                }
                break
            }

            delay(16L) // ~60 fps refresh
        }
    }

    // When currentIndex changes, reset progress
    LaunchedEffect(currentIndex) {
        progressBeforePause = 0f
        progress = 0f
    }

    // ---- Reply state ----
    var replyText by remember { mutableStateOf("") }
    var showReplySent by remember { mutableStateOf(false) }

    // ==================== UI ====================
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (currentStatus != null) {
            // ---- Full-screen content ----
            StatusContent(
                status = currentStatus,
                modifier = Modifier.fillMaxSize()
            )

            // ---- Touch overlay (tap left/right, long-press pause) ----
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
                    .pointerInput(currentIndex) {
                        detectTapGestures(
                            onLongPress = {
                                isPaused = true
                                progressBeforePause = progress
                            },
                            onPress = {
                                // When press is released, resume
                                tryAwaitRelease()
                                if (isPaused) {
                                    isPaused = false
                                }
                            },
                            onTap = { offset ->
                                val screenWidth = size.width
                                if (offset.x < screenWidth / 3f) {
                                    // Tap left → previous
                                    if (currentIndex > 0) {
                                        currentIndex--
                                        progressBeforePause = 0f
                                        progress = 0f
                                    }
                                } else {
                                    // Tap right → next (or advance current)
                                    progress = 1f
                                    if (currentIndex < activeStatuses.lastIndex) {
                                        currentIndex++
                                        progressBeforePause = 0f
                                        progress = 0f
                                    } else {
                                        onBack()
                                    }
                                }
                            }
                        )
                    }
            )

            // ---- Top overlay: progress bars + user info ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(3f)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            ) {
                // Progress bars
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    activeStatuses.forEachIndexed { index, _ ->
                        val segmentProgress = when {
                            index < currentIndex -> 1f           // already completed
                            index == currentIndex -> progress    // currently playing
                            else -> 0f                           // not yet reached
                        }
                        StatusProgressBar(
                            progress = segmentProgress,
                            isPaused = isPaused && index == currentIndex,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // User info row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeuAvatar(
                        photoUrl = currentStatus.userPhotoUrl,
                        name = currentStatus.userName,
                        size = 36.dp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentStatus.userName,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        val timeText = remember(currentStatus.createdAt) {
                            val diff = System.currentTimeMillis() - currentStatus.createdAt
                            when {
                                diff < 60_000L -> "Just now"
                                diff < 3_600_000L -> "${diff / 60_000L} min ago"
                                else -> {
                                    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                                    sdf.format(Date(currentStatus.createdAt))
                                }
                            }
                        }
                        Text(
                            text = timeText,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }

                    // Close button
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // ---- Bottom overlay: Reply field ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .zIndex(3f)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reply text field
                BasicTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp
                    ),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(24.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            if (replyText.isEmpty()) {
                                Text(
                                    text = "Reply to ${currentStatus.userName}...",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 15.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send button — sends reply as a private message to status author
                if (replyText.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            // C.3: Status reply — send message to status author
                            if (currentStatus != null) {
                                viewModel.sendStatusReply(
                                    statusId = currentStatus.id,
                                    statusAuthorId = currentStatus.userId,
                                    statusAuthorName = currentStatus.userName,
                                    replyText = replyText
                                )
                                replyText = ""
                                showReplySent = true
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(colors.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send reply",
                            tint = colors.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Brief "✓ Sent" confirmation after reply
                androidx.compose.animation.AnimatedVisibility(
                    visible = showReplySent,
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandHorizontally(),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkHorizontally()
                ) {
                    Text(
                        text = "✓ Sent",
                        color = colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // Auto-hide the "Sent" confirmation after 2 seconds
    LaunchedEffect(showReplySent) {
        if (showReplySent) {
            delay(2000L)
            showReplySent = false
        }
    }
}

// ==================== Progress Bar Segment ====================
@Composable
private fun StatusProgressBar(
    progress: Float,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = ZaxoTheme.colors

    Box(
        modifier = modifier
            .height(2.5.dp)
            .clip(RoundedCornerShape(1.25.dp))
            .background(Color.White.copy(alpha = 0.25f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .clip(RoundedCornerShape(1.25.dp))
                .background(
                    if (isPaused) Color.White.copy(alpha = 0.6f) else Color.White
                )
        )
    }
}

// ==================== Status Content ====================
@Composable
private fun StatusContent(
    status: Status,
    modifier: Modifier = Modifier
) {
    when (status.type) {
        StatusType.PHOTO -> {
            AsyncImage(
                model = status.mediaUrl,
                contentDescription = "Status photo",
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        }

        StatusType.VIDEO -> {
            // Video playback placeholder – in production this would use ExoPlayer
            Box(
                modifier = modifier.background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (status.mediaUrl.isNotEmpty()) {
                    AsyncImage(
                        model = status.mediaUrl,
                        contentDescription = "Video thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                // Play icon overlay to indicate video
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = Color.White, fontSize = 28.sp, textAlign = TextAlign.Center)
                }
            }
        }

        StatusType.TEXT -> {
            val bgColor = try {
                Color(android.graphics.Color.parseColor(status.backgroundColor))
            } catch (_: Exception) {
                Color(0xFF4A90D9)
            }

            Box(
                modifier = modifier.background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = status.textContent,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
}
