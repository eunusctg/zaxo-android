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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.zaxo.app.model.CallMediaType
import com.zaxo.app.model.CallState
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.CallViewModel

/**
 * V. Post-Call Screen — Brief summary shown for 2 seconds after call ends.
 * Shows: Avatar, "Call Ended", Duration, Message and Recall buttons.
 * Also offers "Save to contacts" for unsaved Zaxo Numbers.
 */
@Composable
fun PostCallScreen(
    onDone: () -> Unit = {},
    viewModel: CallViewModel = hiltViewModel()
) {
    val callState by viewModel.callState.collectAsState()
    val currentCall by viewModel.currentCall.collectAsState()
    val timerText by viewModel.callTimerText.collectAsState()
    val colors = ZaxoTheme.colors

    val call = currentCall

    // Auto-dismiss after 3 seconds
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1D23)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Avatar
            AsyncImage(
                model = call?.callerPhotoUrl ?: call?.calleePhotoUrl,
                contentDescription = "Contact",
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2D36)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(16.dp))

            // "Call Ended" text
            Text(
                text = "Call Ended",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Duration
            Text(
                text = "Duration: $timerText",
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFB0B5BD)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Message button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onDone() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFF2A2D36), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Message,
                            "Message",
                            modifier = Modifier.size(24.dp),
                            tint = Color(0xFF4A90D9)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Message", fontSize = 12.sp, color = Color(0xFFB0B5BD))
                }

                // Recall button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        val calleeUid = call?.calleeUid ?: return@clickable
                        val calleeName = call?.calleeName ?: return@clickable
                        val calleePhoto = call?.calleePhotoUrl ?: ""
                        val calleeZaxo = call?.calleeZaxoNumber ?: ""
                        val mediaType = call?.mediaType ?: CallMediaType.AUDIO
                        viewModel.startOutgoingCall(calleeUid, calleeName, calleePhoto, calleeZaxo, mediaType)
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFF27AE60), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Call,
                            "Recall",
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Recall", fontSize = 12.sp, color = Color(0xFFB0B5BD))
                }
            }
        }
    }
}
