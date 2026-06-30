package com.zaxo.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.zaxo.app.model.Message
import com.zaxo.app.model.MessageType
import com.zaxo.app.viewmodel.MediaViewerViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MediaViewerScreen(
    onBack: () -> Unit,
    viewModel: MediaViewerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    var isUiVisible by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    val pageCount = state.mediaItems.size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (state.isLoading) {
            // Loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (pageCount == 0) {
            // F13: No media in chat
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        "No media",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No photos or videos yet",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            val pagerState = rememberPagerState(
                initialPage = state.currentIndex.coerceAtMost(pageCount - 1),
                pageCount = { pageCount }
            )

            // Sync pager → ViewModel
            LaunchedEffect(pagerState.currentPage) {
                viewModel.setCurrentIndex(pagerState.currentPage)
            }

            // Per-page zoom states
            val zoomScales = remember { mutableStateListOf<Float>().also { list ->
                repeat(pageCount) { list.add(1f) }
            } }
            val zoomOffsets = remember { mutableStateListOf<Offset>().also { list ->
                repeat(pageCount) { list.add(Offset.Zero) }
            } }

            // Reset zoom when page count changes (new media loaded)
            LaunchedEffect(pageCount) {
                while (zoomScales.size < pageCount) { zoomScales.add(1f); zoomOffsets.add(Offset.Zero) }
                while (zoomScales.size > pageCount) { zoomScales.removeLast(); zoomOffsets.removeLast() }
            }

            // Enable/disable pager swiping based on zoom level (F10)
            // When zoomed in (scale > 1), disable HorizontalPager swiping so gesture goes to pan
            val isZoomed = zoomScales.getOrNull(pagerState.currentPage)?.let { it > 1f } ?: false

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isZoomed // F10: only navigate at 1x zoom
            ) { page ->
                val message = state.mediaItems.getOrNull(page) ?: return@HorizontalPager
                val pageScale = zoomScales.getOrNull(page) ?: 1f
                val pageOffset = zoomOffsets.getOrNull(page) ?: Offset.Zero

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (message.type) {
                        MessageType.IMAGE -> {
                            ZoomableImage(
                                imageUrl = message.mediaUrl,
                                scale = pageScale,
                                offset = pageOffset,
                                onTransform = { s, o ->
                                    if (page < zoomScales.size) {
                                        zoomScales[page] = s
                                        zoomOffsets[page] = o
                                    }
                                    viewModel.setZoomScale(s)
                                },
                                onDoubleTap = {
                                    // Toggle between 1x and 2.5x
                                    val newScale = if (pageScale > 1.1f) 1f else 2.5f
                                    if (page < zoomScales.size) {
                                        zoomScales[page] = newScale
                                        zoomOffsets[page] = Offset.Zero
                                    }
                                    viewModel.setZoomScale(newScale)
                                },
                                onSingleTap = {
                                    // Toggle UI visibility only when not zoomed
                                    if (pageScale <= 1f) {
                                        isUiVisible = !isUiVisible
                                    }
                                },
                                onRetry = {
                                    // F8: Retry loading with refreshed URL
                                    coroutineScope.launch {
                                        val newUrl = viewModel.refreshMediaUrl(message.id)
                                        if (newUrl != null) {
                                            viewModel.updateMediaUrl(message.id, newUrl)
                                        }
                                    }
                                }
                            )
                        }
                        MessageType.VIDEO -> {
                            VideoPlaceholder(
                                message = message,
                                onClick = { /* TODO: ExoPlayer integration */ }
                            )
                        }
                        else -> {
                            // Document or other type placeholder
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.InsertDriveFile,
                                    "Document",
                                    tint = Color.White,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    message.content.take(50),
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // === Top Bar Overlay ===
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(2f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            state.currentMedia?.senderName ?: "",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        state.currentMedia?.let { msg ->
                            Text(
                                formatDate(msg.timestamp),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))

                    // E.3: Media counter
                    if (pageCount > 1) {
                        Text(
                            "${state.currentIndex + 1} of $pageCount",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }

                    // E.4: Share button
                    val context = LocalContext.current
                    IconButton(onClick = {
                        state.currentMedia?.let { msg ->
                            shareMedia(context, msg)
                        }
                    }) {
                        Icon(Icons.Default.Share, "Share", tint = Color.White)
                    }

                    // Details button
                    IconButton(onClick = { viewModel.toggleDetails() }) {
                        Icon(
                            Icons.Default.Info,
                            "Details",
                            tint = if (state.showDetails) Color(0xFF4A90D9) else Color.White
                        )
                    }
                }
            }

            // === Navigation arrows ===
            if (pageCount > 1 && isUiVisible) {
                if (state.currentIndex > 0) {
                    IconButton(
                        onClick = {
                            pagerState.currentPage.let { page ->
                                if (page > 0) {
                                    zoomScales[page] = 1f
                                    zoomOffsets[page] = Offset.Zero
                                }
                            }
                            viewModel.previous()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                if (state.currentIndex < pageCount - 1) {
                    IconButton(
                        onClick = {
                            pagerState.currentPage.let { page ->
                                if (page < pageCount) {
                                    zoomScales[page] = 1f
                                    zoomOffsets[page] = Offset.Zero
                                }
                            }
                            viewModel.next()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            "Next",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // === Bottom Details Panel ===
            AnimatedVisibility(
                visible = state.showDetails && isUiVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .zIndex(2f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .padding(16.dp)
                ) {
                    state.currentMedia?.let { msg ->
                        DetailRow("Type", msg.type.name.lowercase().replaceFirstChar { it.uppercase() })
                        DetailRow("Sender", msg.senderName)
                        DetailRow("Date", formatDateFull(msg.timestamp))
                        if (msg.mediaUrl.isNotEmpty()) {
                            DetailRow("File", msg.mediaUrl.takeLast(40))
                        }
                        // Show caption for images
                        if (msg.content.isNotEmpty() && msg.type != MessageType.IMAGE) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                msg.content,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 13.sp
                            )
                        }
                        // Show image caption if present
                        if (msg.content.isNotEmpty() && msg.type == MessageType.IMAGE && msg.content != msg.mediaUrl) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Caption: ${msg.content}",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== Zoomable Image ====================

@Composable
private fun ZoomableImage(
    imageUrl: String,
    scale: Float,
    offset: Offset,
    onTransform: (scale: Float, offset: Offset) -> Unit,
    onDoubleTap: () -> Unit,
    onSingleTap: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Single-tap: toggle UI overlay; Double-tap: toggle zoom
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onTap = { onSingleTap() }
                )
            }
            // Pinch-to-zoom and pan (separate pointerInput to avoid gesture conflict)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f) // 1x to 5x
                    val newOffset = if (newScale > 1f) offset + pan else Offset.Zero
                    onTransform(newScale, newOffset)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .size(Size.ORIGINAL)
                .build(),
            contentDescription = "Media image",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            },
            error = {
                // F8 + F12: Media deleted or URL expired — show retry option
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.BrokenImage,
                        "Media unavailable",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Media no longer available",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    if (onRetry != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { onRetry() }) {
                            Icon(
                                Icons.Default.Refresh,
                                "Retry",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        }
                    }
                }
            }
        )
    }
}

// ==================== Video Placeholder ====================

@Composable
private fun VideoPlaceholder(
    message: Message,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            if (message.mediaUrl.isNotEmpty()) {
                AsyncImage(
                    model = message.mediaUrl,
                    contentDescription = "Video thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
            }
            // Play button overlay
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Video - Tap to play",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
    }
}

// ==================== Detail Row ====================

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ==================== Share Intent ====================

private fun shareMedia(context: Context, message: Message) {
    // E.4: Share media via Intent
    // For now, share the URL as text. Full implementation would download file + use FileProvider.
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, message.mediaUrl)
        type = when (message.type) {
            MessageType.IMAGE -> "image/*"
            MessageType.VIDEO -> "video/*"
            else -> "text/plain"
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share media"))
}

// ==================== Date Formatting ====================

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDateFull(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEEE, MMMM dd, yyyy 'at' HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
