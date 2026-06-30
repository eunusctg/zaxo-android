package com.zaxo.app.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.zaxo.app.ui.components.*
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.ChatRoomViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    chatId: String,
    onBack: () -> Unit,
    onChatInfo: (String) -> Unit,
    onForward: (String, String) -> Unit,
    onMediaClick: (String, String) -> Unit = { _, _ -> },
    onWallpaperClick: (String) -> Unit = {},
    viewModel: ChatRoomViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val chat by viewModel.chat.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    val replyTo by viewModel.replyTo.collectAsState()
    val selectedMessages by viewModel.selectedMessages.collectAsState()
    val selectionMode by viewModel.selectionMode.collectAsState()

    var showReactionFor by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // F13: Detect wallpaper brightness for text contrast adjustment
    val wallpaperUrl = chat?.wallpaperUrl
    val isDarkWallpaper = wallpaperUrl != null && isDarkWallpaper(wallpaperUrl)
    val textColor = if (isDarkWallpaper) Color.White else colors.onSurface
    val mutedTextColor = if (isDarkWallpaper) Color.White.copy(alpha = 0.7f) else colors.muted

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = selectedMessages.size,
                    onDelete = { viewModel.deleteSelectedMessages() },
                    onStar = { viewModel.starSelectedMessages() },
                    onForward = {
                        val firstMsgId = selectedMessages.firstOrNull() ?: return@SelectionTopBar
                        onForward(firstMsgId, chatId)
                    },
                    onSelectAll = { viewModel.selectAll() },
                    onClose = { viewModel.clearSelection() }
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onChatInfo(chatId) }
                        ) {
                            NeuAvatar(
                                photoUrl = chat?.photoUrl ?: "",
                                name = chat?.name ?: "",
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    chat?.name ?: "Chat",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp,
                                    color = colors.onSurface
                                )
                                if (chat?.isTyping == true) {
                                    Text(
                                        "typing...",
                                        fontSize = 12.sp,
                                        color = colors.primary,
                                        fontStyle = FontStyle.Italic
                                    )
                                } else {
                                    Text(
                                        if (chat?.isGroup == true) "Group" else "Online",
                                        fontSize = 12.sp,
                                        color = colors.muted
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = colors.onSurface)
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* voice call */ }) {
                            Icon(Icons.Default.Call, "Call", tint = colors.onSurface)
                        }
                        IconButton(onClick = { /* video call */ }) {
                            Icon(Icons.Default.Videocam, "Video", tint = colors.onSurface)
                        }
                    }
                )
            }
        }
    ) { padding ->
        // Chat area with optional wallpaper
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Wallpaper layer (Feature 3: Chat Wallpapers)
            if (!wallpaperUrl.isNullOrEmpty()) {
                if (wallpaperUrl.startsWith("custom:")) {
                    val uri = wallpaperUrl.removePrefix("custom:")
                    AsyncImage(
                        model = uri,
                        contentDescription = "Wallpaper",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.3f
                    )
                } else if (wallpaperUrl.startsWith("builtin_")) {
                    // Built-in wallpaper — render colored background
                    val wallpaperColor = getWallpaperColor(wallpaperUrl)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(wallpaperColor)
                    )
                }
                // F13: Dark scrim for readability — 20% opacity, auto-adjusts for dark wallpapers
                val scrimAlpha = if (isDarkWallpaper) 0.1f else 0.2f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha))
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Messages list
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            isOwnMessage = viewModel.isOwnMessage(message),
                            selectionMode = selectionMode,
                            isSelected = message.id in selectedMessages,
                            onSelect = { viewModel.toggleSelectMessage(message.id) },
                            onForward = { onForward(message.id, chatId) },
                            onReply = { viewModel.setReplyTo(message) },
                            onStar = { viewModel.starMessage(message.id, !message.isStarred) },
                            onDelete = { viewModel.deleteMessage(message.id) },
                            onMediaClick = { onMediaClick(chatId, message.id) },
                            showQuickReactions = showReactionFor == message.id,
                            onReaction = { msg, emoji -> viewModel.addReaction(msg.id, emoji) }
                        )
                    }
                }

                // Reply preview
                AnimatedVisibility(
                    visible = replyTo != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    replyTo?.let { replyMsg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surface)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(40.dp)
                                    .background(colors.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    replyMsg.senderName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary
                                )
                                Text(
                                    replyMsg.content.take(50),
                                    fontSize = 13.sp,
                                    color = colors.onSurface,
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = { viewModel.setReplyTo(null) }) {
                                Icon(Icons.Default.Close, "Cancel reply", tint = colors.muted)
                            }
                        }
                    }
                }

                // Input bar (hidden in selection mode)
                if (!selectionMode) {
                    MessageInputBar(
                        text = messageText,
                        onTextChange = { viewModel.updateMessageText(it) },
                        onSend = { viewModel.sendMessage() },
                        onAttach = { /* attachment picker */ },
                        isRecording = isRecordingVoice,
                        onRecordingChanged = { isRecordingVoice = it },
                        onVoiceComplete = { mediaUrl, duration, waveform ->
                            viewModel.sendVoiceMessage(mediaUrl, duration, waveform)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onDelete: () -> Unit,
    onStar: () -> Unit,
    onForward: () -> Unit,
    onSelectAll: () -> Unit,
    onClose: () -> Unit
) {
    val colors = ZaxoTheme.colors
    TopAppBar(
        title = {
            Text(
                "$selectedCount selected",
                fontWeight = FontWeight.Medium,
                color = colors.primary
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = colors.onSurface)
            }
        },
        actions = {
            IconButton(onClick = onForward) {
                Icon(Icons.Default.Reply, "Forward", tint = colors.onSurface)
            }
            IconButton(onClick = onStar) {
                Icon(Icons.Default.Star, "Star", tint = colors.onSurface)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = colors.error)
            }
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, "Select All", tint = colors.onSurface)
            }
        }
    )
}

/**
 * Message input bar with voice recording support.
 * When the text field is empty, the send button becomes a microphone that
 * triggers voice recording on press-and-hold.
 */
@Composable
fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    isRecording: Boolean = false,
    onRecordingChanged: (Boolean) -> Unit = {},
    onVoiceComplete: (mediaUrl: String, duration: Long, waveform: List<Float>) -> Unit = { _, _, _ -> }
) {
    val colors = ZaxoTheme.colors

    if (isRecording) {
        // Full voice recording UI using VoiceRecordButton
        VoiceRecordButton(
            onRecordingComplete = { mediaUrl, duration, waveform ->
                onVoiceComplete(mediaUrl, duration, waveform)
                onRecordingChanged(false)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Attach button
            NeuIconButton(
                onClick = onAttach,
                icon = Icons.Default.AttachFile,
                contentDescription = "Attach"
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Text input
            NeuCard(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                elevation = 4.dp
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    textStyle = TextStyle(
                        color = colors.onSurface,
                        fontSize = 15.sp
                    ),
                    maxLines = 4,
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text("Type a message...", color = colors.muted, fontSize = 15.sp)
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Send / Voice button
            // When text is empty, long-press the mic to start recording
            val hasText = text.isNotBlank()
            NeuIconButton(
                onClick = if (hasText) onSend else { { onRecordingChanged(true) } },
                icon = if (hasText) Icons.Default.Send else Icons.Default.Mic,
                contentDescription = if (hasText) "Send" else "Voice",
                tint = colors.primary
            )
        }
    }
}

/**
 * Get wallpaper color for built-in wallpaper IDs.
 */
private fun getWallpaperColor(wallpaperId: String): Color {
    return when (wallpaperId) {
        "builtin_dark_dots" -> Color(0xFF1A1A2E)
        "builtin_dark_waves" -> Color(0xFF16213E)
        "builtin_dark_geometric" -> Color(0xFF0F3460)
        "builtin_dark_gradient" -> Color(0xFF1A1A2E)
        "builtin_dark_stars" -> Color(0xFF0D1117)
        "builtin_dark_abstract" -> Color(0xFF1B1B2F)
        "builtin_light_dots" -> Color(0xFFE8F0FE)
        "builtin_light_waves" -> Color(0xFFD4E6F1)
        "builtin_light_geometric" -> Color(0xFFD5F5E3)
        "builtin_light_gradient" -> Color(0xFFFADBD8)
        "builtin_light_floral" -> Color(0xFFFDEBD0)
        "builtin_light_abstract" -> Color(0xFFEBF5FB)
        else -> Color.Transparent
    }
}

/**
 * F13: Detect if a wallpaper is dark based on its ID.
 * Used to adjust text contrast automatically.
 */
private fun isDarkWallpaper(wallpaperId: String): Boolean {
    return wallpaperId.startsWith("builtin_dark_") ||
            wallpaperId.startsWith("custom:") // Custom wallpapers get default bright treatment
}
