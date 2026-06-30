package com.zaxo.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zaxo.app.model.MessageStatus

/**
 * Renders the correct status icon based on [MessageStatus] with animated crossfade transitions.
 *
 * - SENDING → Gray clock (Schedule)
 * - SENT → Gray single check (Done)
 * - DELIVERED → Gray double check (DoneAll)
 * - READ → Blue double check (DoneAll)
 * - FAILED → Red error icon, clickable to retry via [onRetry]
 *
 * @param status The current [MessageStatus] to render.
 * @param onRetry Optional callback invoked when the FAILED icon is clicked.
 * @param modifier Modifier for the composable.
 * @param iconSize Size of the status icon. Default is 14.dp.
 */
@Composable
fun MessageStatusIcon(
    status: MessageStatus,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    iconSize: Dp = 14.dp
) {
    AnimatedContent(
        targetState = status,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "message_status_transition"
    ) { targetStatus ->
        when (targetStatus) {
            MessageStatus.SENDING -> {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = "Sending",
                    modifier = Modifier.size(iconSize),
                    tint = Color.Gray
                )
            }

            MessageStatus.SENT -> {
                Icon(
                    imageVector = Icons.Rounded.Done,
                    contentDescription = "Sent",
                    modifier = Modifier.size(iconSize),
                    tint = Color.Gray
                )
            }

            MessageStatus.DELIVERED -> {
                Icon(
                    imageVector = Icons.Rounded.DoneAll,
                    contentDescription = "Delivered",
                    modifier = Modifier.size(iconSize),
                    tint = Color.Gray
                )
            }

            MessageStatus.READ -> {
                Icon(
                    imageVector = Icons.Rounded.DoneAll,
                    contentDescription = "Read",
                    modifier = Modifier.size(iconSize),
                    tint = Color(0xFF4FC3F7)
                )
            }

            MessageStatus.FAILED -> {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Error,
                        contentDescription = "Failed — tap to retry",
                        modifier = Modifier
                            .size(iconSize)
                            .then(
                                if (onRetry != null) {
                                    Modifier.clickable { onRetry.invoke() }
                                } else {
                                    Modifier
                                }
                            ),
                        tint = Color.Red
                    )
                }
            }
        }
    }
}
