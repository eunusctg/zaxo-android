package com.zaxo.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.zaxo.app.model.CallMediaType
import com.zaxo.app.model.CallSession
import com.zaxo.app.model.CallState
import com.zaxo.app.ui.components.CallControls
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.CallViewModel

data class GroupParticipant(
    val uid: String,
    val name: String,
    val photoUrl: String,
    val isMuted: Boolean = false,
    val isSpeaking: Boolean = false,
    val isVideoOn: Boolean = false,
    val identity: String = ""
)

@Composable
fun GroupCallScreen(
    onCallEnded: () -> Unit = {},
    viewModel: CallViewModel = hiltViewModel()
) {
    val callState by viewModel.callState.collectAsState()
    val currentCall by viewModel.currentCall.collectAsState()
    val timerText by viewModel.callTimerText.collectAsState()
    val networkQuality by viewModel.networkQuality.collectAsState()
    val isReconnecting by viewModel.isReconnecting.collectAsState()
    val colors = ZaxoTheme.colors

    val call = currentCall
    val isVideoCall = call?.mediaType == CallMediaType.VIDEO

    // Navigate when call ends
    LaunchedEffect(callState) {
        if (callState == CallState.IDLE || callState == CallState.POST_CALL) {
            onCallEnded()
        }
    }

    // Build participant list from LiveKit room when available
    val participants = remember(call) {
        listOf(
            GroupParticipant("self", "You", "", isMuted = call?.isMuted ?: false, isVideoOn = call?.isVideoOn ?: false),
            GroupParticipant(call?.calleeUid ?: "", call?.calleeName ?: "Participant", call?.calleePhotoUrl ?: "")
        )
    }

    var showParticipantList by remember { mutableStateOf(false) }

    // Participant change announcements
    var lastParticipantCount by remember { mutableStateOf(participants.size) }
    LaunchedEffect(participants.size) {
        if (participants.size < lastParticipantCount && lastParticipantCount > 0) {
            val context = LocalContext.current
            android.widget.Toast.makeText(context, "A participant left the call", android.widget.Toast.LENGTH_SHORT).show()
        } else if (participants.size > lastParticipantCount && lastParticipantCount > 0) {
            val context = LocalContext.current
            android.widget.Toast.makeText(context, "A participant joined the call", android.widget.Toast.LENGTH_SHORT).show()
        }
        lastParticipantCount = participants.size
    }

    // Controls auto-hide for video
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, isVideoCall) {
        if (isVideoCall && controlsVisible) {
            kotlinx.coroutines.delay(3000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isVideoCall) Color.Black else Color(0xFF1A1D23))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header: Group name + timer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = call?.groupName ?: "Group Call",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = timerText,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFB0B5BD)
                    )
                }

                // Participant count + toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Group,
                        "Participants",
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFFB0B5BD)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${participants.size}",
                        fontSize = 14.sp,
                        color = Color(0xFFB0B5BD)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.List,
                        "List",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { showParticipantList = !showParticipantList },
                        tint = Color(0xFFB0B5BD)
                    )
                }
            }

            // F97: Late join context
            if (call?.startedAt != null && call.startedAt > 0L) {
                val callAgeSeconds = (System.currentTimeMillis() - call.startedAt) / 1000
                if (callAgeSeconds > 30) {
                    Text(
                        text = "Call started ${callAgeSeconds / 60} min ago",
                        fontSize = 12.sp,
                        color = Color(0xFFB0B5BD),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // Participant grid area
            if (isVideoCall) {
                // Video grid with LiveKit SurfaceViewRenderer
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (participants.size <= 2) 1 else if (participants.size <= 4) 2 else 3),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(participants) { participant ->
                        VideoParticipantTile(
                            participant = participant,
                            isActive = participant.isSpeaking
                        )
                    }
                }
            } else {
                // Audio participant grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(participants) { participant ->
                        AudioParticipantTile(
                            participant = participant,
                            isActive = participant.isSpeaking
                        )
                    }
                }
            }

            // Active speaker indicator
            val activeSpeaker = participants.find { it.isSpeaking }
            if (activeSpeaker != null) {
                Text(
                    text = "Speaking: ${activeSpeaker.name}",
                    fontSize = 12.sp,
                    color = Color(0xFF27AE60),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Call controls
            AnimatedVisibility(
                visible = if (isVideoCall) controlsVisible else true,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                CallControls(
                    isMuted = call?.isMuted ?: false,
                    isSpeakerOn = call?.isSpeakerOn ?: false,
                    isBluetoothOn = call?.isBluetoothOn ?: false,
                    isVideoOn = call?.isVideoOn ?: true,
                    isVideoCall = isVideoCall,
                    isOnHold = call?.isOnHold ?: false,
                    isGroupCall = true,
                    onMuteToggle = { viewModel.toggleMute() },
                    onSpeakerToggle = { viewModel.toggleSpeaker() },
                    onBluetoothToggle = { viewModel.toggleBluetooth() },
                    onVideoToggle = { viewModel.toggleVideo() },
                    onFlipCamera = { viewModel.flipCamera() },
                    onEndCall = { viewModel.endCall() },
                    onAddParticipant = { /* ContactPicker ADD_TO_CALL */ },
                    onHoldToggle = { viewModel.toggleHold() },
                    modifier = Modifier.padding(bottom = 48.dp)
                )
            }
        }

        // Reconnecting overlay
        if (isReconnecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Reconnecting...", fontSize = 16.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Please wait", fontSize = 14.sp, color = Color(0xFFB0B5BD))
                }
            }
        }

        // Participant list panel
        if (showParticipantList) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.7f)
                    .fillMaxHeight()
                    .background(Color(0xFF2A2D36).copy(alpha = 0.95f))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Participants", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Icon(
                            Icons.Default.Close,
                            "Close",
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { showParticipantList = false },
                            tint = Color(0xFFB0B5BD)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    participants.forEach { participant ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = participant.photoUrl,
                                contentDescription = participant.name,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3A3D46))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                participant.name,
                                fontSize = 14.sp,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            if (participant.isMuted) {
                                Icon(Icons.Default.MicOff, "Muted", modifier = Modifier.size(16.dp), tint = Color(0xFFE74C3C))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioParticipantTile(
    participant: GroupParticipant,
    isActive: Boolean
) {
    val bgColor = if (isActive) Color(0xFF27AE60).copy(alpha = 0.15f) else Color(0xFF2A2D36)

    Column(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = participant.photoUrl,
            contentDescription = participant.name,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF3A3D46)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = participant.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Icon(
            if (participant.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            if (participant.isMuted) "Muted" else "Microphone",
            modifier = Modifier.size(16.dp),
            tint = if (participant.isMuted) Color(0xFFE74C3C)
                   else if (participant.isSpeaking) Color(0xFF27AE60)
                   else Color(0xFF6B7280)
        )
    }
}

// ═══════════════════════════════════════════════════════
// Video Participant Tile — LiveKit SurfaceViewRenderer
// Renders participant video with name overlay, mute indicator,
// and active speaker highlight border.
// ═══════════════════════════════════════════════════════

@Composable
private fun VideoParticipantTile(
    participant: GroupParticipant,
    isActive: Boolean
) {
    val viewModel: CallViewModel = hiltViewModel()
    val borderColor = if (isActive) Color(0xFF27AE60) else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .background(Color(0xFF1A1A1A))
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isActive) Modifier.border(3.dp, Color(0xFF27AE60), RoundedCornerShape(8.dp))
                else Modifier
            )
    ) {
        if (participant.isVideoOn) {
            // LiveKit SurfaceViewRenderer for participant video
            AndroidView(
                factory = { ctx ->
                    org.webrtc.SurfaceViewRenderer(ctx).apply {
                        init(org.webrtc.EglBase.create().eglBaseContext, null)
                        setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        setEnableHardwareScaler(true)
                        setZOrderMediaOverlay(false)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { surfaceView ->
                    try {
                        val room = viewModel.getLiveKitRoom()
                        if (participant.uid == "self") {
                            room?.localParticipant?.cameraTrack?.addRenderer(surfaceView)
                        } else {
                            val remoteParticipant = room?.remoteParticipants?.values?.find {
                                it.identity?.value == participant.identity || it.identity?.value == participant.uid
                            }
                            remoteParticipant?.cameraTrack?.addRenderer(surfaceView)
                        }
                    } catch (e: Exception) {
                        // Track not available yet — will attach on update
                    }
                }
            )
        } else {
            // Show avatar when video is off
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = participant.photoUrl,
                    contentDescription = participant.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3A3D46))
                )
            }
        }

        // Name overlay at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = participant.name,
                fontSize = 12.sp,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            if (participant.isMuted) {
                Icon(Icons.Default.MicOff, "Muted", modifier = Modifier.size(14.dp), tint = Color(0xFFE74C3C))
            }
        }

        // Active speaker highlight
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF27AE60).copy(alpha = 0.1f))
            )
        }
    }
}
