package com.zaxo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.zaxo.app.ui.theme.ZaxoTheme

/**
 * H. Call Controls — Neumorphic circular buttons for all call actions.
 * Each button is 56dp (64dp for End Call) with neumorphic shadow.
 * Press animation uses spring scale effect.
 */

@Composable
fun CallControls(
    isMuted: Boolean = false,
    isSpeakerOn: Boolean = false,
    isBluetoothOn: Boolean = false,
    isVideoOn: Boolean = true,
    isVideoCall: Boolean = false,
    isOnHold: Boolean = false,
    isGroupCall: Boolean = false,
    onMuteToggle: () -> Unit = {},
    onSpeakerToggle: () -> Unit = {},
    onBluetoothToggle: () -> Unit = {},
    onVideoToggle: () -> Unit = {},
    onFlipCamera: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onAddParticipant: () -> Unit = {},
    onHoldToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = ZaxoTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // First row: Mute, Speaker, Video, End
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // H.1 Mute Button
            CallControlButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (isMuted) "Unmute" else "Mute",
                isActive = isMuted,
                activeColor = Color(0xFFE74C3C), // Red when muted
                onClick = onMuteToggle
            )

            // H.2 Speaker Button
            CallControlButton(
                icon = when {
                    isBluetoothOn -> Icons.Default.Bluetooth
                    isSpeakerOn -> Icons.Default.VolumeUp
                    else -> Icons.Default.VolumeOff
                },
                label = when {
                    isBluetoothOn -> "Bluetooth"
                    isSpeakerOn -> "Speaker"
                    else -> "Earpiece"
                },
                isActive = isSpeakerOn || isBluetoothOn,
                activeColor = if (isBluetoothOn) Color(0xFF4A90D9) else Color(0xFF27AE60),
                onClick = if (isBluetoothOn) onBluetoothToggle else onSpeakerToggle
            )

            // H.3 Video Toggle (only for video calls)
            if (isVideoCall) {
                CallControlButton(
                    icon = if (isVideoOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    label = if (isVideoOn) "Video On" else "Video Off",
                    isActive = !isVideoOn,
                    activeColor = Color(0xFFE74C3C),
                    onClick = onVideoToggle
                )
            } else {
                // Placeholder for alignment in audio calls
                Spacer(modifier = Modifier.size(56.dp))
            }

            // H.5 End Call Button (64dp, always RED)
            CallControlButton(
                icon = Icons.Default.CallEnd,
                label = "End",
                isEndCall = true,
                onClick = onEndCall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Second row: Flip Camera, Add Participant (context-dependent)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // H.4 Flip Camera (video calls only)
            if (isVideoCall) {
                CallControlButton(
                    icon = Icons.Default.FlipCameraAndroid,
                    label = "Flip",
                    isActive = false,
                    onClick = onFlipCamera
                )
            } else {
                Spacer(modifier = Modifier.size(56.dp))
            }

            // Bluetooth toggle
            CallControlButton(
                icon = Icons.Default.Bluetooth,
                label = "Bluetooth",
                isActive = isBluetoothOn,
                activeColor = Color(0xFF4A90D9),
                onClick = onBluetoothToggle
            )

            // H.6 Add Participant (group calls only)
            if (isGroupCall) {
                CallControlButton(
                    icon = Icons.Default.PersonAdd,
                    label = "Add",
                    isActive = false,
                    onClick = onAddParticipant
                )
            } else {
                Spacer(modifier = Modifier.size(56.dp))
            }

            // Hold button
            CallControlButton(
                icon = if (isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                label = if (isOnHold) "Resume" else "Hold",
                isActive = isOnHold,
                activeColor = Color(0xFFF39C12),
                onClick = onHoldToggle
            )
        }
    }
}

@Composable
fun CallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    activeColor: Color = Color(0xFF27AE60),
    isEndCall: Boolean = false,
    onClick: () -> Unit
) {
    val colors = ZaxoTheme.colors
    val size = if (isEndCall) 64.dp else 56.dp
    val iconSize = if (isEndCall) 32.dp else 24.dp

    // Press animation
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "buttonScale"
    )

    // Background color
    val bgColor = when {
        isEndCall -> Color(0xFFE74C3C)
        isActive -> activeColor.copy(alpha = 0.2f)
        else -> colors.surface
    }

    // Icon tint
    val iconTint = when {
        isEndCall -> Color.White
        isActive -> activeColor
        else -> colors.onSurface
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = if (isEndCall) 0.dp else 6.dp,
                    shape = CircleShape,
                    ambientColor = colors.shadowDark,
                    spotColor = colors.shadowLight
                )
                .background(bgColor, CircleShape)
                .clickable {
                    isPressed = true
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(iconSize),
                tint = iconTint
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isActive) activeColor else colors.muted
        )
    }

    // Reset pressed state
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(150)
            isPressed = false
        }
    }
}
