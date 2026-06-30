package com.zaxo.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaxo.app.model.Message
import com.zaxo.app.model.MessageStatus
import com.zaxo.app.ui.theme.ZaxoTheme

// ==================== Voice Message Bubble ====================
@Composable
fun VoiceMessageBubble(
    message: Message,
    isOwnMessage: Boolean,
    modifier: Modifier = Modifier,
    isGroupChat: Boolean = false
) {
    val colors = ZaxoTheme.colors
    val bubbleColor = if (isOwnMessage) colors.outgoingBubble else colors.incomingBubble
    val alignment = if (isOwnMessage) Alignment.End else Alignment.Start
    val shape = if (isOwnMessage) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Column(horizontalAlignment = alignment) {
            Surface(
                modifier = Modifier.widthIn(max = 280.dp),
                shape = shape,
                color = bubbleColor,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    // Sender name for group chats
                    if (!isOwnMessage && isGroupChat && message.senderName.isNotEmpty()) {
                        Text(
                            text = message.senderName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Voice player
                    VoicePlayer(
                        messageId = message.id,
                        mediaUrl = message.mediaUrl,
                        durationMs = message.mediaDuration.coerceAtLeast(0L),
                        waveform = message.waveform,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // D.1: Transcription caption below waveform
                    // Check if content contains transcription (format: "🎤 Voice message\n{transcription}")
                    val transcription = extractTranscription(message.content)
                    if (transcription != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "\"$transcription\"",
                            fontSize = 13.sp,
                            color = colors.onSurface.copy(alpha = 0.8f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Bottom row: duration + time + status
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Duration in "m:ss" format
                        Text(
                            text = formatVoiceDuration(message.mediaDuration.coerceAtLeast(0L)),
                            fontSize = 11.sp,
                            color = colors.muted,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Timestamp
                        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        Text(
                            text = timeFormat.format(java.util.Date(message.timestamp)),
                            fontSize = 11.sp,
                            color = colors.muted
                        )

                        // Status icon (own messages only)
                        if (isOwnMessage) {
                            Spacer(modifier = Modifier.width(4.dp))
                            StatusIcon(
                                status = message.status,
                                isRead = message.isRead,
                                primaryColor = colors.primary,
                                mutedColor = colors.muted
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Extract transcription text from a voice message content string.
 * Format: "🎤 Voice message\n{transcription text}"
 * Returns null if no transcription is present.
 */
private fun extractTranscription(content: String): String? {
    if (!content.startsWith("🎤")) return null
    val newlineIndex = content.indexOf('\n')
    return if (newlineIndex >= 0 && newlineIndex < content.length - 1) {
        content.substring(newlineIndex + 1).trim().takeIf { it.isNotEmpty() }
    } else {
        null
    }
}

// ==================== Message Status Icon ====================
@Composable
private fun StatusIcon(
    status: MessageStatus,
    isRead: Boolean,
    primaryColor: Color,
    mutedColor: Color
) {
    when (status) {
        MessageStatus.SENDING -> {
            Icon(
                imageVector = Icons.Default.Done,
                contentDescription = "Sending",
                modifier = Modifier.size(14.dp),
                tint = mutedColor.copy(alpha = 0.5f)
            )
        }
        MessageStatus.SENT -> {
            Icon(
                imageVector = Icons.Default.Done,
                contentDescription = "Sent",
                modifier = Modifier.size(14.dp),
                tint = mutedColor
            )
        }
        MessageStatus.DELIVERED -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Delivered",
                modifier = Modifier.size(14.dp),
                tint = mutedColor
            )
        }
        MessageStatus.READ -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Read",
                modifier = Modifier.size(14.dp),
                tint = primaryColor
            )
        }
        MessageStatus.FAILED -> {
            Icon(
                imageVector = Icons.Default.Done,
                contentDescription = "Failed",
                modifier = Modifier.size(14.dp),
                tint = Color(0xFFE74C3C)
            )
        }
    }
}
