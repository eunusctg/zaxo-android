package com.zaxo.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.zaxo.app.model.CallMediaType
import com.zaxo.app.model.CallState
import com.zaxo.app.ui.components.CallControls
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.CallViewModel

@Composable
fun OutgoingCallScreen(
    onCallConnected: () -> Unit = {},
    onCallEnded: () -> Unit = {},
    viewModel: CallViewModel = hiltViewModel()
) {
    val callState by viewModel.callState.collectAsState()
    val currentCall by viewModel.currentCall.collectAsState()
    val colors = ZaxoTheme.colors

    // Navigate when call connects
    LaunchedEffect(callState) {
        when (callState) {
            CallState.ACTIVE, CallState.GROUP_ACTIVE -> onCallConnected()
            CallState.IDLE, CallState.POST_CALL -> onCallEnded()
            else -> {}
        }
    }

    // Pulse animation for avatar
    val infiniteTransition = rememberInfiniteTransition(label = "outgoing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Animated dots for "Calling..." / "Ringing..."
    val dotProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotProgress"
    )
    val dotCount = (dotProgress.toInt()).coerceIn(1, 3)

    val statusText = when (callState) {
        CallState.VALIDATING, CallState.CREATING_ROOM, CallState.SENDING_PUSH, CallState.DIALING -> {
            "Calling" + ".".repeat(dotCount)
        }
        CallState.RINGING -> "Ringing" + ".".repeat(dotCount)
        CallState.LINE_BUSY -> "Line Busy"
        CallState.CALL_DECLINED -> "Call Declined"
        CallState.NO_ANSWER -> "No Answer"
        CallState.CALL_FAILED -> "Call Failed"
        CallState.USER_OFFLINE -> "User Offline"
        CallState.PRIVACY_BLOCKED -> "Privacy Blocked"
        else -> "Calling..."
    }

    val call = currentCall

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1D23)),
        contentAlignment = Alignment.Center
    ) {
        // Glowing radial background
        Box(
            modifier = Modifier
                .size(240.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF4A90D9).copy(alpha = 0.3f),
                            Color(0xFF4A90D9).copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Avatar with pulse glow
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Glow ring
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .background(
                            Color(0xFF4A90D9).copy(alpha = 0.15f),
                            CircleShape
                        )
                )

                // Avatar
                AsyncImage(
                    model = call?.calleePhotoUrl,
                    contentDescription = call?.calleeName ?: "Contact",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2D36)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Name
            Text(
                text = call?.calleeName ?: "Unknown",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status text
            Text(
                text = statusText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFB0B5BD)
            )

            // Zaxo Number (if available)
            if (!call?.calleeZaxoNumber.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = call!!.calleeZaxoNumber.chunked(3).joinToString(" "),
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Call controls
            CallControls(
                isMuted = call?.isMuted ?: false,
                isSpeakerOn = call?.isSpeakerOn ?: false,
                isBluetoothOn = call?.isBluetoothOn ?: false,
                isVideoOn = call?.isVideoOn ?: true,
                isVideoCall = call?.mediaType == CallMediaType.VIDEO,
                isOnHold = call?.isOnHold ?: false,
                isGroupCall = call?.isGroupCall ?: false,
                onMuteToggle = { viewModel.toggleMute() },
                onSpeakerToggle = { viewModel.toggleSpeaker() },
                onBluetoothToggle = { viewModel.toggleBluetooth() },
                onVideoToggle = { viewModel.toggleVideo() },
                onFlipCamera = { viewModel.flipCamera() },
                onEndCall = { viewModel.endCall() },
                onAddParticipant = { /* TODO */ },
                modifier = Modifier.padding(bottom = 48.dp)
            )
        }

        // Error/busy overlay
        if (callState in listOf(CallState.LINE_BUSY, CallState.CALL_DECLINED, CallState.NO_ANSWER, CallState.CALL_FAILED, CallState.USER_OFFLINE, CallState.PRIVACY_BLOCKED)) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 180.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (callState) {
                    CallState.LINE_BUSY -> {
                        Icon(
                            Icons.Default.CallEnd,
                            "Line Busy",
                            modifier = Modifier.size(80.dp),
                            tint = Color(0xFF6B7280)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "The person you are calling is on another call.",
                            fontSize = 14.sp,
                            color = Color(0xFFB0B5BD),
                            modifier = Modifier.padding(horizontal = 48.dp)
                        )
                    }
                    CallState.NO_ANSWER -> {
                        Text(
                            "The person did not answer.",
                            fontSize = 14.sp,
                            color = Color(0xFFB0B5BD)
                        )
                    }
                    CallState.CALL_DECLINED -> {
                        Text(
                            "The call was declined.",
                            fontSize = 14.sp,
                            color = Color(0xFFB0B5BD)
                        )
                    }
                    CallState.CALL_FAILED -> {
                        Text(
                            "Call failed. Please try again.",
                            fontSize = 14.sp,
                            color = Color(0xFFB0B5BD)
                        )
                    }
                    CallState.USER_OFFLINE -> {
                        Text(
                            "The user is currently offline.",
                            fontSize = 14.sp,
                            color = Color(0xFFB0B5BD)
                        )
                    }
                    CallState.PRIVACY_BLOCKED -> {
                        Text(
                            "This call is blocked by privacy settings.",
                            fontSize = 14.sp,
                            color = Color(0xFFB0B5BD)
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}
