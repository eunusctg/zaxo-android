package com.zaxo.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import android.os.Build
import android.view.View
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import com.zaxo.app.model.CallMediaType
import com.zaxo.app.model.CallState
import com.zaxo.app.ui.components.CallControls
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.CallViewModel
import io.livekit.android.room.Room
import kotlinx.coroutines.delay

@Composable
fun ActiveCallScreen(
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

    // F93: Lock audio calls to portrait
    val activity = LocalContext.current as? android.app.Activity
    DisposableEffect(isVideoCall) {
        activity?.requestedOrientation = if (!isVideoCall) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // F94: Override back press during call
    BackHandler(enabled = callState == CallState.ACTIVE) {
        // Do nothing — prevent accidental back navigation during call
    }

    // F115/R: Picture-in-Picture for video calls
    DisposableEffect(isVideoCall, activity) {
        if (isVideoCall && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.enterPictureInPictureMode(
                android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9))
                    .build()
            )
        }
        onDispose { /* PIP exits when activity finishes */ }
    }

    // Navigate when call ends
    LaunchedEffect(callState) {
        if (callState == CallState.IDLE || callState == CallState.POST_CALL) {
            onCallEnded()
        }
    }

    // Controls auto-hide timer for video calls (3 seconds)
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(controlsVisible, isVideoCall) {
        if (isVideoCall && controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }

    // Local PIP position for video call
    var pipOffsetX by remember { mutableStateOf(0f) }
    var pipOffsetY by remember { mutableStateOf(0f) }

    // End call confirmation state (F92)
    var showEndConfirmation by remember { mutableStateOf(false) }
    val callAge = if (call?.connectTimestamp != null && call.connectTimestamp > 0) {
        System.currentTimeMillis() - call.connectTimestamp
    } else Long.MAX_VALUE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isVideoCall) Color.Black else Color(0xFF1A1D23))
            .clickable(enabled = isVideoCall) {
                controlsVisible = !controlsVisible
            }
    ) {
        if (isVideoCall) {
            // ==================== VIDEO CALL LAYOUT ====================

            // Remote video — LiveKit SurfaceViewRenderer (full screen)
            RemoteVideoSurface(
                modifier = Modifier.fillMaxSize()
            )

            // Local video PIP — LiveKit SurfaceViewRenderer (draggable)
            LocalVideoPip(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(pipOffsetX.toInt(), pipOffsetY.toInt()) }
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 16.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            pipOffsetX += dragAmount.x
                            pipOffsetY += dragAmount.y
                        }
                    }
            )

            // Overlay: Name + Timer (top-left)
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = call?.callerName ?: call?.calleeName ?: "Unknown",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = timerText,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFB0B5BD)
                    )
                }
            }

        } else {
            // ==================== AUDIO CALL LAYOUT ====================

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // Pulsing glow avatar
                val infiniteTransition = rememberInfiniteTransition(label = "activeAudio")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                Box(contentAlignment = Alignment.Center) {
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
                    AsyncImage(
                        model = call?.callerPhotoUrl ?: call?.calleePhotoUrl,
                        contentDescription = "Contact",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A2D36)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = call?.callerName ?: call?.calleeName ?: "Unknown",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = timerText,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFB0B5BD)
                )

                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // ==================== SHARED OVERLAYS ====================

        // Network quality indicator
        if (networkQuality == "poor") {
            Row(
                modifier = Modifier
                    .align(if (isVideoCall) Alignment.TopCenter else Alignment.Center)
                    .padding(if (isVideoCall) PaddingValues(top = 80.dp) else PaddingValues())
                    .background(Color(0xFFF39C12).copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.SignalCellularAlt,
                    "Poor connection",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Poor connection",
                    fontSize = 12.sp,
                    color = Color.White
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
                    val infiniteTransition = rememberInfiniteTransition(label = "reconnect")
                    val spin by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "spin"
                    )
                    Icon(
                        Icons.Default.Refresh,
                        "Reconnecting",
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer { rotationZ = spin },
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Reconnecting...", fontSize = 16.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Please wait", fontSize = 14.sp, color = Color(0xFFB0B5BD))
                }
            }
        }

        // Call controls at bottom (auto-hide for video)
        AnimatedVisibility(
            visible = if (isVideoCall) controlsVisible else true,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CallControls(
                isMuted = call?.isMuted ?: false,
                isSpeakerOn = call?.isSpeakerOn ?: false,
                isBluetoothOn = call?.isBluetoothOn ?: false,
                isVideoOn = call?.isVideoOn ?: true,
                isVideoCall = isVideoCall,
                isOnHold = call?.isOnHold ?: false,
                isGroupCall = call?.isGroupCall ?: false,
                onMuteToggle = { viewModel.toggleMute() },
                onSpeakerToggle = { viewModel.toggleSpeaker() },
                onBluetoothToggle = { viewModel.toggleBluetooth() },
                onVideoToggle = { viewModel.toggleVideo() },
                onFlipCamera = { viewModel.flipCamera() },
                onEndCall = {
                    if (callAge < 5000L) {
                        showEndConfirmation = true
                    } else {
                        viewModel.confirmEndCall()
                    }
                },
                onAddParticipant = { /* ContactPicker ADD_TO_CALL */ },
                onHoldToggle = { viewModel.toggleHold() },
                modifier = Modifier.padding(bottom = 48.dp)
            )
        }

        // End call confirmation dialog (F92)
        if (showEndConfirmation) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showEndConfirmation = false },
                title = { Text("End Call?") },
                text = { Text("Are you sure you want to end this call?") },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showEndConfirmation = false
                            viewModel.confirmEndCall()
                        }
                    ) { Text("End Call", color = Color(0xFFE74C3C)) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showEndConfirmation = false }
                    ) { Text("Cancel") }
                }
            )
        }

        // Call waiting overlay
        if (callState == CallState.CALL_WAITING) {
            CallWaitingOverlay(
                onHoldAndAccept = {
                    viewModel.holdAndAccept(
                        com.zaxo.app.model.CallSession(state = CallState.INCOMING)
                    )
                },
                onEndCurrentAndAccept = {
                    viewModel.endCurrentAndAccept(
                        com.zaxo.app.model.CallSession(state = CallState.INCOMING)
                    )
                },
                onDecline = { viewModel.declineCallWaiting() }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// Remote Video Surface — LiveKit SurfaceViewRenderer
// Renders the remote participant's video in full screen.
// ═══════════════════════════════════════════════════════

@Composable
fun RemoteVideoSurface(
    modifier: Modifier = Modifier
) {
    val viewModel: CallViewModel = hiltViewModel()
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            org.webrtc.SurfaceViewRenderer(ctx).apply {
                init(org.webrtc.EglBase.create().eglBaseContext, null)
                setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                setEnableHardwareScaler(true)
                setZOrderMediaOverlay(false)
            }
        },
        modifier = modifier.background(Color(0xFF1A1A1A)),
        update = { surfaceView ->
            // Attach remote video track when available
            try {
                val room = viewModel.getLiveKitRoom()
                val remoteParticipant = room?.remoteParticipants?.values?.firstOrNull()
                val videoTrack = remoteParticipant?.cameraTrack
                if (videoTrack != null) {
                    videoTrack.addRenderer(surfaceView)
                }
            } catch (e: Exception) {
                // Track not available yet — will be attached on update
            }
        }
    )
}

// ═══════════════════════════════════════════════════════
// Local Video PIP — LiveKit SurfaceViewRenderer
// Renders the local camera feed in a draggable PIP window.
// ═══════════════════════════════════════════════════════

@Composable
fun LocalVideoPip(
    modifier: Modifier = Modifier
) {
    val viewModel: CallViewModel = hiltViewModel()
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            org.webrtc.SurfaceViewRenderer(ctx).apply {
                init(org.webrtc.EglBase.create().eglBaseContext, null)
                setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                setEnableHardwareScaler(true)
                setZOrderMediaOverlay(true)
                setZOrderOnTop(true)
            }
        },
        modifier = modifier
            .size(120.dp, 160.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        update = { surfaceView ->
            try {
                val room = viewModel.getLiveKitRoom()
                val localVideoTrack = room?.localParticipant?.cameraTrack
                if (localVideoTrack != null) {
                    localVideoTrack.addRenderer(surfaceView)
                }
            } catch (e: Exception) {
                // Track not available yet
            }
        }
    )
}

@Composable
private fun CallWaitingOverlay(
    onHoldAndAccept: () -> Unit,
    onEndCurrentAndAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .background(Color(0xFF2A2D36), RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Incoming Call",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // End Current & Accept
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFFE74C3C), CircleShape)
                            .clickable(onClick = onEndCurrentAndAccept),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CallEnd, "End", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("End Current", fontSize = 11.sp, color = Color(0xFFB0B5BD))
                }

                // Hold & Accept
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFF27AE60), CircleShape)
                            .clickable(onClick = onHoldAndAccept),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Call, "Accept", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Hold & Accept", fontSize = 11.sp, color = Color(0xFFB0B5BD))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Decline
            Text(
                "Decline",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFB0B5BD),
                modifier = Modifier.clickable(onClick = onDecline)
            )
        }
    }
}
