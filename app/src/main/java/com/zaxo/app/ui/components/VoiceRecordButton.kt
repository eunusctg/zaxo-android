package com.zaxo.app.ui.components

import android.Manifest

import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.async.util
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zaxo.app.ui.theme.ZaxoTheme
import kotlinx.coroutines.*
import java.io.File


// ==================== Permission Rationale Dialog ====================
@Composable
fun PermissionRationaleDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = ZaxoTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = colors.surface,
        title = {
            Text(
                text = "Microphone Access Required",
                color = colors.onSurface,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Zaxo needs access to your microphone to record voice messages. " +
                        "Please grant the permission in Settings to continue.",
                color = colors.onSurface.copy(alpha = 0.8f)
            )
        },
        confirmButton = {
            NeuButton(
                onClick = onOpenSettings,
                containerColor = colors.primary,
                contentColor = colors.onPrimary
            ) {
                Text("Open Settings", color = colors.onPrimary, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.muted)
            }
        }
    )
}

// ==================== Voice Record Button ====================
@Composable
fun VoiceRecordButton(
    onRecordingComplete: (mediaUrl: String, duration: Long, waveform: List<Float>) -> Unit,
    modifier: Modifier = Modifier,
    maxDurationMs: Long = 600_000L // F4: 10 minutes cap
) {
    val colors = ZaxoTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    // F36: Check if device supports haptic feedback
    val hasHaptic = remember {
        try {
            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            vibrator?.hasVibrator() == true
        } catch (_: Exception) {
            false
        }
    }

    var isRecording by remember { mutableStateOf(false) }
    var isCancelled by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val cancelThreshold = with(density) { 120.dp.toPx() }

    // Recording state
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf("") }
    var recordingStartTime by remember { mutableLongStateOf(0L) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    val amplitudeSamples = remember { mutableListOf<Float>() }
    var samplingJob by remember { mutableStateOf<Job?>(null) }
    var timerJob by remember { mutableStateOf<Job?>(null) }
    val scope = remember { CoroutineScope(Dispatchers.Main + SupervisorJob()) }

    // Pulse animation for red dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatableAnimation(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatableAnimation(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Slide-to-cancel alpha
    val cancelAlpha = (dragOffsetX / cancelThreshold).coerceIn(0f, 1f)
    val isSlideCancelling = dragOffsetX < -cancelThreshold * 0.3f

    // D.2: Track haptic milestones — light tap every 30dp of slide progress
    var lastHapticOffset by remember { mutableFloatStateOf(0f) }
    val hapticStepSize = with(density) { 30.dp.toPx() }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    fun startRecording() {
        if (!hasPermission()) {
            showPermissionDialog = true
            return
        }

        val dir = File(context.cacheDir, "voice_messages").apply { mkdirs() }
        val fileName = "voice_${System.currentTimeMillis()}.webm"
        outputFile = File(dir, fileName).absolutePath

        try {
            val mr = createRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.WEBM)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(48000)
                setOutputFile(outputFile)
                prepare()
                start()
            }
            recorder = mr
            recordingStartTime = System.currentTimeMillis()
            elapsedMs = 0L
            amplitudeSamples.clear()
            isRecording = true
            isCancelled = false

            // D.2: Haptic feedback on recording start (strong pulse)
            if (hasHaptic) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            // F4: Cap at 10 minutes — sample amplitude every 100ms
            samplingJob = scope.launch {
                while (isActive) {
                    try {
                        val amplitude = mr.maxAmplitude.toFloat()
                        val normalized = (amplitude / 32768f).coerceIn(0f, 1f)
                        amplitudeSamples.add(normalized)
                    } catch (_: Exception) {
                        amplitudeSamples.add(0f)
                    }
                    delay(100L)
                }
            }

            timerJob = scope.launch {
                while (isActive) {
                    elapsedMs = System.currentTimeMillis() - recordingStartTime
                    if (elapsedMs >= maxDurationMs) {
                        stopRecording(cancel = false)
                        break
                    }
                    delay(100L)
                }
            }
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            isRecording = false
        }
    }

    fun stopRecording(cancel: Boolean) {
        samplingJob?.cancel()
        timerJob?.cancel()
        samplingJob = null
        timerJob = null

        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
        }
        recorder = null

        if (cancel || isCancelled) {
            // Delete the file
            if (outputFile.isNotEmpty()) {
                File(outputFile).delete()
            }
            isCancelled = false
        } else {
            val duration = System.currentTimeMillis() - recordingStartTime
            if (duration >= 1000L && outputFile.isNotEmpty()) {
                // D.2: Haptic feedback on recording stop (medium pulse)
                if (hasHaptic) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onRecordingComplete(
                    outputFile,
                    duration,
                    amplitudeSamples.toList()
                )
            } else if (outputFile.isNotEmpty()) {
                File(outputFile).delete()
            }
        }

        isRecording = false
        dragOffsetX = 0f
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            samplingJob?.cancel()
            timerJob?.cancel()
            try {
                recorder?.apply { stop(); release() }
            } catch (_: Exception) {
            }
            recorder = null
        }
    }

    // F1: Permission rationale dialog
    if (showPermissionDialog) {
        PermissionRationaleDialog(
            onDismiss = { showPermissionDialog = false },
            onOpenSettings = {
                showPermissionDialog = false
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        )
    }

    // ==================== UI ====================
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (!isRecording) {
            // Idle: mic button with press-and-hold
            // Touch down starts recording, slide left cancels, release sends
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(6.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
                    .background(colors.background, CircleShape)
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        // Use awaitPointerEventScope for precise touch control
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                
                                // Start recording on touch down
                                startRecording()
                                
                                // Track drag for slide-to-cancel
                                var cancelled = false
                                var totalDragX = 0f
                                lastHapticOffset = 0f // Reset haptic milestone
                                
                                do {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { change ->
                                        change.consume()
                                        totalDragX += change.positionChange().x
                                        dragOffsetX = totalDragX.coerceAtMost(0f)

                                        // D.2: Haptic feedback on slide progress — light tap every 30dp
                                        val slideDistance = -totalDragX.coerceAtMost(0f)
                                        if (slideDistance - lastHapticOffset >= hapticStepSize) {
                                            lastHapticOffset = slideDistance
                                            if (hasHaptic) {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                            }
                                        }

                                        if (totalDragX < -cancelThreshold) {
                                            isCancelled = true
                                            cancelled = true
                                            // D.2: Haptic feedback on cancel threshold (double rejection pulse)
                                            if (hasHaptic) {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                                
                                // Touch released — stop recording
                                stopRecording(cancel = cancelled || isCancelled)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Hold to record voice message",
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            // Recording UI
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Top row: pulsing red dot + timer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    // Pulsing red dot
                    Box(
                        modifier = Modifier
                            .size((8 * pulseScale).dp)
                            .background(
                                Color.Red.copy(alpha = pulseAlpha),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Timer mm:ss
                    Text(
                        text = formatElapsedTime(elapsedMs),
                        color = colors.error,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Slide-to-cancel indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "← Slide to cancel",
                        color = if (isSlideCancelling) colors.error else colors.muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.graphicsLayer {
                            alpha = 1f - cancelAlpha
                            translationX = dragOffsetX * 0.5f
                        }
                    )
                }

                // Stop button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(6.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
                        .background(colors.error, CircleShape)
                        .clip(CircleShape)
                        .then(
                            Modifier.pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { /* already recording */ },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetX = (dragOffsetX + dragAmount.x).coerceAtMost(0f)

                                        // D.2: Haptic feedback on slide progress
                                        val slideDistance = -dragOffsetX
                                        if (slideDistance - lastHapticOffset >= hapticStepSize) {
                                            lastHapticOffset = slideDistance
                                            if (hasHaptic) {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                            }
                                        }

                                        if (dragOffsetX < -cancelThreshold) {
                                            isCancelled = true
                                            // D.2: Haptic on cancel threshold
                                            if (hasHaptic) {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        stopRecording(cancel = isCancelled)
                                    },
                                    onDragCancel = {
                                        stopRecording(cancel = true)
                                    }
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop recording",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// ==================== Helper ====================
private fun formatElapsedTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = (totalSeconds / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    return String.format("%02d:%02d", minutes, seconds)
}
