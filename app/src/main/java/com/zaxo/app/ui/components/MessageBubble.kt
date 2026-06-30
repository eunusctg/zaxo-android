package com.zaxo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaxo.app.model.Message
import com.zaxo.app.model.MessageType
import com.zaxo.app.model.MessageStatus
import com.zaxo.app.model.DEFAULT_REACTIONS
import com.zaxo.app.ui.theme.ZaxoTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(
    message: Message,
    isOwnMessage: Boolean,
    modifier: Modifier = Modifier,
    onForward: ((Message) -> Unit)? = null,
    onReply: ((Message) -> Unit)? = null,
    onStar: ((Message) -> Unit)? = null,
    onDelete: ((Message) -> Unit)? = null,
    onCopy: ((Message) -> Unit)? = null,
    onSelect: ((Message) -> Unit)? = null,
    onMediaClick: ((Message) -> Unit)? = null,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    showQuickReactions: Boolean = false,
    onReaction: ((Message, String) -> Unit)? = null
) {
    val colors = ZaxoTheme.colors
    val bubbleColor = if (isOwnMessage) colors.outgoingBubble else colors.incomingBubble
    val alignment = if (isOwnMessage) Alignment.End else Alignment.Start
    val shape = if (isOwnMessage) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Column(horizontalAlignment = alignment) {
            // Reply preview
            if (message.replyToContent.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .padding(bottom = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = colors.primary.copy(alpha = 0.15f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = message.replyToSender,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                        Text(
                            text = message.replyToContent.take(60),
                            fontSize = 11.sp,
                            color = colors.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Forwarded label
            if (message.isForwarded) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Reply,
                        contentDescription = "Forwarded",
                        modifier = Modifier.size(12.dp),
                        tint = colors.muted
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Forwarded",
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = colors.muted
                    )
                }
            }

            // Main bubble — VOICE messages use dedicated VoiceMessageBubble
            if (message.type == MessageType.VOICE) {
                VoiceMessageBubble(
                    message = message,
                    isOwnMessage = isOwnMessage,
                    isGroupChat = false
                )
            } else {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .then(
                            if (selectionMode) Modifier.clickable { onSelect?.invoke(message) }
                            else Modifier
                        ),
                    shape = shape,
                    color = if (isSelected) colors.primary.copy(alpha = 0.3f) else bubbleColor,
                    tonalElevation = if (isSelected) 2.dp else 0.dp
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        // Sender name for group chats
                        if (!isOwnMessage && message.senderName.isNotEmpty()) {
                            Text(
                                text = message.senderName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        // Message content
                        when (message.type) {
                            MessageType.IMAGE -> {
                                if (message.mediaUrl.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onMediaClick?.invoke(message) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("📷 Photo", color = colors.onSurface)
                                    }
                                }
                                if (message.content.isNotEmpty()) {
                                    Text(
                                        text = message.content,
                                        color = colors.onSurface,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                            MessageType.VIDEO -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { onMediaClick?.invoke(message) }
                                ) {
                                    Icon(Icons.Default.PlayCircleFilled, "Video", tint = colors.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("🎬 Video", color = colors.onSurface, fontSize = 15.sp)
                                }
                            }
                            MessageType.AUDIO -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, "Audio", tint = colors.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("🎵 Voice message", color = colors.onSurface, fontSize = 15.sp)
                                    if (message.mediaDuration > 0L) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = formatAudioDuration(message.mediaDuration),
                                            fontSize = 12.sp,
                                            color = colors.muted
                                        )
                                    }
                                }
                            }
                            MessageType.DOCUMENT -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.InsertDriveFile, "Document", tint = colors.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("📄 Document", color = colors.onSurface, fontSize = 15.sp)
                                }
                            }
                            MessageType.SYSTEM -> {
                                Text(
                                    text = message.content,
                                    color = colors.muted,
                                    fontSize = 13.sp,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                            else -> {
                                Text(
                                    text = message.content,
                                    color = colors.onSurface,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        // Time and status
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = timeFormat.format(Date(message.timestamp)),
                                fontSize = 11.sp,
                                color = colors.muted
                            )
                            if (isOwnMessage) {
                                Spacer(modifier = Modifier.width(4.dp))
                                MessageStatusIcon(
                                    status = message.status,
                                    onRetry = { onDelete?.invoke(message) }
                                )
                            }
                            if (message.isStarred) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Starred",
                                    modifier = Modifier.size(12.dp),
                                    tint = Color(0xFFF39C12)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Reactions Bar
            AnimatedVisibility(
                visible = showQuickReactions,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .widthIn(max = 280.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DEFAULT_REACTIONS.forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .clickable { onReaction?.invoke(message, emoji) }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formats a duration in milliseconds to "m:ss" format.
 */
private fun formatAudioDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
