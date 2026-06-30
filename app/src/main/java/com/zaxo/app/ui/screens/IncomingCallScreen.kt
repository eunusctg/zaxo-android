package com.zaxo.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.CallViewModel

private val QUICK_RESPONSES = listOf(
    "Can't talk right now",
    "In a meeting",
    "Call you back soon",
    "Text me instead",
    "Driving \u2014 call you later"
)

@Composable
fun IncomingCallScreen(
    roomId: String = "",
    callId: String = "",
    callType: String = "audio",
    callerUid: String = "",
    callerName: String = "Unknown",
    callerZaxoNumber: String = "",
    onCallAccepted: () -> Unit = {},
    onCallDeclined: () -> Unit = {},
    viewModel: CallViewModel = hiltViewModel()
) {
    val callState by viewModel.callState.collectAsState()
    val currentCall by viewModel.currentCall.collectAsState()
    val colors = ZaxoTheme.colors
    var showQuickResponses by remember { mutableStateOf(false) }

    // Navigate when call is accepted or declined
    LaunchedEffect(callState) {
        when (callState) {
            CallState.ACTIVE -> onCallAccepted()
            CallState.IDLE -> onCallDeclined()
            else -> {}
        }
    }

    // Pulse animation for incoming call
    val infiniteTransition = rememberInfiniteTransition(label = "incoming")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1D23)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Pulsing ring + Avatar
            Box(contentAlignment = Alignment.Center) {
                // Outer ring
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                            alpha = ringAlpha
                        }
                        .background(Color(0xFF27AE60), CircleShape)
                )
                // Inner ring
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .graphicsLayer {
                            scaleX = pulseScale * 0.95f
                            scaleY = pulseScale * 0.95f
                            alpha = ringAlpha * 0.5f
                        }
                        .background(Color(0xFF27AE60), CircleShape)
                )

                // Avatar
                AsyncImage(
                    model = currentCall?.callerPhotoUrl,
                    contentDescription = callerName,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2D36)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Caller name
            Text(
                text = currentCall?.callerName ?: callerName,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            // Zaxo Number (if available)
            if (!currentCall?.callerZaxoNumber.isNullOrBlank() || callerZaxoNumber.isNotBlank()) {
                val number = currentCall?.callerZaxoNumber ?: callerZaxoNumber
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = number.chunked(3).joinToString(" "),
                    fontSize = 16.sp,
                    color = Color(0xFFB0B5BD)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Call type indicator
            val mediaLabel = if (currentCall?.mediaType == CallMediaType.VIDEO || callType == "video")
                "Incoming Video Call" else "Incoming Call"
            Text(
                text = mediaLabel,
                fontSize = 16.sp,
                color = Color(0xFFB0B5BD)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Accept / Decline buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decline button (red)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFFE74C3C), CircleShape)
                            .clickable {
                                viewModel.declineIncomingCall()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            "Decline",
                            modifier = Modifier.size(32.dp),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Decline", fontSize = 12.sp, color = Color(0xFFB0B5BD))
                }

                // Accept button (green)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFF27AE60), CircleShape)
                            .clickable {
                                viewModel.acceptIncomingCall(
                                    roomId = roomId,
                                    callId = callId,
                                    callerUid = callerUid,
                                    callerName = callerName,
                                    callerPhotoUrl = "",
                                    callerZaxoNumber = callerZaxoNumber,
                                    mediaType = if (callType == "video") CallMediaType.VIDEO else CallMediaType.AUDIO
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Call,
                            "Accept",
                            modifier = Modifier.size(32.dp),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Accept", fontSize = 12.sp, color = Color(0xFFB0B5BD))
                }
            }

            // Quick responses section
            if (showQuickResponses) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2A2D36), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        "Send a quick message",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    QUICK_RESPONSES.forEach { response ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.declineIncomingCall(withMessage = response)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Message,
                                response,
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFFB0B5BD)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                response,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                // Show "message" icon to reveal quick responses
                TextButton(
                    onClick = { showQuickResponses = true },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        Icons.Default.Message,
                        "Send message",
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFFB0B5BD)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Send message instead",
                        fontSize = 14.sp,
                        color = Color(0xFFB0B5BD)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
