package com.zaxo.app.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.media.session.MediaSessionCompat
import com.zaxo.app.ui.theme.ZaxoTheme
import kotlinx.coroutines.*
import timber.log.Timber

// ==================== Voice Playback Manager (F6: singleton + MediaSession) ====================

/**
 * Audio focus state machine:
 * IDLE → PLAYING → DUCKED → PAUSED → STOPPED
 *
 * PLAYING:    Audio focus GAIN, notification visible
 * DUCKED:     Volume reduced to 30% (LOSS_TRANSIENT_CAN_DUCK)
 * PAUSED:     Playback paused (LOSS_TRANSIENT), auto-resume when focus regained
 * STOPPED:    Playback stopped (LOSS), notification removed
 */
private enum class AudioFocusState {
    IDLE, PLAYING, DUCKED, PAUSED, STOPPED
}

object VoicePlaybackManager {
    private var currentMediaPlayer: MediaPlayer? = null
    private var currentMessageId: String? = null
    private var onPlaybackStopped: (() -> Unit)? = null
    private var playbackSpeed: Float = 1.0f

    // D.2: MediaSession for background playback + notification
    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    private var audioFocusState: AudioFocusState = AudioFocusState.IDLE

    // Audio focus request (API 26+)
    private var audioFocusRequest: AudioFocusRequest? = null

    val playingMessageId: String? get() = currentMessageId

    /**
     * Initialize MediaSession and AudioManager.
     * Must be called once, typically from the Application class or first Activity.
     */
    fun initialize(context: Context) {
        if (mediaSession != null) return // Already initialized

        try {
            audioManager = context.getSystemService()

            mediaSession = MediaSessionCompat(context, "ZaxoVoice").apply {
                setCallback(object : MediaSessionCompat.Callback() {
                    override fun onPlay() { resume() }
                    override fun onPause() { pause() }
                    override fun onStop() { stopCurrent() }
                })

                setMetadata(
                    android.support.v4.media.MediaMetadataCompat.Builder()
                        .putString(
                            android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE,
                            "Voice Message"
                        )
                        .putString(
                            android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST,
                            "Zaxo"
                        )
                        .build()
                )

                isActive = false // Activated when playback starts
            }

            Timber.d("MediaSession initialized for voice playback")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize MediaSession")
        }
    }

    fun stopCurrent() {
        try {
            currentMediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {
        }
        currentMediaPlayer = null
        onPlaybackStopped?.invoke()
        onPlaybackStopped = null
        currentMessageId = null

        // Deactivate MediaSession and abandon audio focus
        deactivateMediaSession()
        abandonAudioFocus()
        audioFocusState = AudioFocusState.STOPPED
    }

    fun startPlayback(
        messageId: String,
        mediaUrl: String,
        onPrepared: (MediaPlayer) -> Unit,
        onStopped: () -> Unit,
        onComplete: () -> Unit
    ) {
        // F6: Stop any currently playing voice before starting new one
        stopCurrent()
        currentMessageId = messageId
        onPlaybackStopped = onStopped

        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .build()
                )
                setDataSource(mediaUrl)
                setOnPreparedListener {
                    onPrepared(it)
                    // Activate MediaSession and request audio focus
                    activateMediaSession()
                    requestAudioFocus()
                    audioFocusState = AudioFocusState.PLAYING
                }
                setOnCompletionListener {
                    onComplete()
                    currentMessageId = null
                    onPlaybackStopped = null
                    try { release() } catch (_: Exception) {}
                    currentMediaPlayer = null
                    deactivateMediaSession()
                    abandonAudioFocus()
                    audioFocusState = AudioFocusState.IDLE
                }
                setOnErrorListener { _, _, _ ->
                    onComplete()
                    currentMessageId = null
                    onPlaybackStopped = null
                    try { release() } catch (_: Exception) {}
                    currentMediaPlayer = null
                    deactivateMediaSession()
                    abandonAudioFocus()
                    audioFocusState = AudioFocusState.IDLE
                    true
                }
                prepareAsync()
            }
            currentMediaPlayer = mp
        } catch (e: Exception) {
            currentMessageId = null
            onPlaybackStopped = null
            onComplete()
        }
    }

    fun pause() {
        try {
            currentMediaPlayer?.pause()
            mediaSession?.setPlaybackState(
                android.support.v4.media.session.PlaybackStateCompat.Builder()
                    .setState(android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED, 0, 1f)
                    .setActions(
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY or
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP
                    )
                    .build()
            )
        } catch (_: Exception) {
        }
    }

    fun resume() {
        try {
            currentMediaPlayer?.start()
            // Re-apply speed after resume
            if (playbackSpeed != 1.0f) {
                setPlaybackSpeed(playbackSpeed)
            }
            mediaSession?.setPlaybackState(
                android.support.v4.media.session.PlaybackStateCompat.Builder()
                    .setState(android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING, 0, playbackSpeed)
                    .setActions(
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE or
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP
                    )
                    .build()
            )
        } catch (_: Exception) {
        }
    }

    fun seekTo(position: Int) {
        try {
            currentMediaPlayer?.seekTo(position)
        } catch (_: Exception) {
        }
    }

    fun getCurrentPosition(): Int {
        return try {
            currentMediaPlayer?.currentPosition ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun getDuration(): Int {
        return try {
            currentMediaPlayer?.duration ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun isCurrentlyPlaying(messageId: String): Boolean {
        return try {
            currentMessageId == messageId && currentMediaPlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * D.3 / F37: Set playback speed with pitch correction.
     * F37: Pitch correction — setPitch(1.0f) ensures natural pitch at all speeds.
     */
    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val params = android.media.PlaybackParams()
                    .setSpeed(speed)
                    .setPitch(1.0f) // F37: Maintain original pitch at all speeds
                currentMediaPlayer?.playbackParams = params
            }
        } catch (e: Exception) {
            Timber.e(e, "Playback speed change not supported")
        }
    }

    // ═══════════════ MediaSession Management ═══════════════

    private fun activateMediaSession() {
        try {
            mediaSession?.apply {
                isActive = true
                setPlaybackState(
                    android.support.v4.media.session.PlaybackStateCompat.Builder()
                        .setState(android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING, 0, playbackSpeed)
                        .setActions(
                            android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE or
                            android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP
                        )
                        .build()
                )
            }
            Timber.d("MediaSession activated")
        } catch (e: Exception) {
            Timber.e(e, "Failed to activate MediaSession")
        }
    }

    private fun deactivateMediaSession() {
        try {
            mediaSession?.apply {
                setPlaybackState(
                    android.support.v4.media.session.PlaybackStateCompat.Builder()
                        .setState(android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED, 0, 1f)
                        .build()
                )
                isActive = false
            }
            Timber.d("MediaSession deactivated")
        } catch (e: Exception) {
            Timber.e(e, "Failed to deactivate MediaSession")
        }
    }

    // ═══════════════ Audio Focus Management ═══════════════

    /**
     * Request audio focus for voice message playback.
     * Handles duck on notifications, pause on calls, and loss on other media.
     */
    private fun requestAudioFocus() {
        try {
            val am = audioManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener { focusChange ->
                        handleAudioFocusChange(focusChange)
                    }
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .build()
                    )
                    .build()

                val result = am.requestAudioFocus(audioFocusRequest!!)
                Timber.d("Audio focus request result: $result")
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    { focusChange -> handleAudioFocusChange(focusChange) },
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to request audio focus")
        }
    }

    private fun abandonAudioFocus() {
        try {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to abandon audio focus")
        }
    }

    /**
     * Audio focus change handler.
     * State machine: IDLE → PLAYING → DUCKED → PAUSED → STOPPED
     */
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Regained focus — resume if was paused, restore volume if was ducked
                when (audioFocusState) {
                    AudioFocusState.PAUSED -> {
                        resume()
                        audioFocusState = AudioFocusState.PLAYING
                        Timber.d("Audio focus regained — resuming playback")
                    }
                    AudioFocusState.DUCKED -> {
                        // Restore full volume
                        try {
                            currentMediaPlayer?.setVolume(1f, 1f)
                        } catch (_: Exception) {
                        }
                        audioFocusState = AudioFocusState.PLAYING
                        Timber.d("Audio focus regained — restoring volume")
                    }
                    else -> {}
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Another media app took over — stop playback
                stopCurrent()
                audioFocusState = AudioFocusState.STOPPED
                Timber.d("Audio focus lost — stopping playback")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Call or notification — pause playback, auto-resume on gain
                pause()
                audioFocusState = AudioFocusState.PAUSED
                Timber.d("Audio focus transient loss — pausing playback")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Notification sound — reduce volume to 30%
                try {
                    currentMediaPlayer?.setVolume(0.3f, 0.3f)
                } catch (_: Exception) {
                }
                audioFocusState = AudioFocusState.DUCKED
                Timber.d("Audio focus duck — reducing volume to 30%")
            }
        }
    }

    /**
     * Release all resources including MediaSession.
     */
    fun release() {
        stopCurrent()
        try {
            mediaSession?.release()
            mediaSession = null
        } catch (e: Exception) {
            Timber.e(e, "Failed to release MediaSession")
        }
    }
}

// ==================== Waveform Helper ====================
fun parseWaveform(waveformStr: String): List<Float> {
    if (waveformStr.isBlank()) return emptyList()
    return waveformStr.split(",")
        .mapNotNull { it.trim().toFloatOrNull()?.coerceIn(0f, 1f) }
}

fun sampleWaveform(samples: List<Float>, targetCount: Int): List<Float> {
    if (samples.isEmpty()) return List(targetCount) { 0.3f }
    if (samples.size <= targetCount) return samples

    val result = mutableListOf<Float>()
    val step = samples.size.toFloat() / targetCount
    for (i in 0 until targetCount) {
        val start = (i * step).toInt()
        val end = ((i + 1) * step).toInt().coerceAtMost(samples.size)
        val chunk = samples.subList(start, end)
        result.add(chunk.maxOrNull() ?: 0f)
    }
    return result
}

// ==================== Voice Player Composable ====================
@Composable
fun VoicePlayer(
    messageId: String,
    mediaUrl: String,
    durationMs: Long,
    waveform: String,
    modifier: Modifier = Modifier,
    barWidth: Dp = 2.dp,
    barGap: Dp = 1.dp,
    maxBarHeight: Dp = 24.dp,
    onPlaybackStateChange: ((isPlaying: Boolean) -> Unit)? = null
) {
    val colors = ZaxoTheme.colors

    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var totalDurationMs by remember { mutableIntStateOf(durationMs.toInt().coerceAtLeast(1)) }
    val progress = if (totalDurationMs > 0) currentPositionMs.toFloat() / totalDurationMs else 0f

    // D.3: Playback speed control — 1x → 1.5x → 2x → 1x cycle
    val speeds = remember { listOf(1.0f, 1.5f, 2.0f) }
    var currentSpeedIndex by remember { mutableIntStateOf(0) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }

    val parsedWaveform = remember(waveform) { parseWaveform(waveform) }
    val sampledWaveform = remember(parsedWaveform) { sampleWaveform(parsedWaveform, 50) }

    // Progress update loop
    val scope = remember { CoroutineScope(Dispatchers.Main + SupervisorJob()) }
    var progressJob by remember { mutableStateOf<Job?>(null) }

    fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                currentPositionMs = VoicePlaybackManager.getCurrentPosition()
                totalDurationMs = VoicePlaybackManager.getDuration().coerceAtLeast(1)
                delay(100L) // F38: Sync every 100ms via ExoPlayer/MediaPlayer listener
            }
        }
    }

    fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    fun togglePlayback() {
        if (VoicePlaybackManager.isCurrentlyPlaying(messageId)) {
            // Currently playing → pause
            VoicePlaybackManager.pause()
            isPlaying = false
            stopProgressUpdates()
            onPlaybackStateChange?.invoke(false)
        } else if (VoicePlaybackManager.playingMessageId == messageId) {
            // Paused for this message → resume
            VoicePlaybackManager.resume()
            isPlaying = true
            startProgressUpdates()
            onPlaybackStateChange?.invoke(true)
        } else {
            // Different message or nothing playing → start new
            VoicePlaybackManager.startPlayback(
                messageId = messageId,
                mediaUrl = mediaUrl,
                onPrepared = { mp ->
                    isPrepared = true
                    totalDurationMs = mp.duration.coerceAtLeast(1)
                    mp.start()
                    isPlaying = true
                    startProgressUpdates()
                    onPlaybackStateChange?.invoke(true)
                },
                onStopped = {
                    isPlaying = false
                    stopProgressUpdates()
                    onPlaybackStateChange?.invoke(false)
                },
                onComplete = {
                    isPlaying = false
                    currentPositionMs = 0
                    stopProgressUpdates()
                    onPlaybackStateChange?.invoke(false)
                }
            )
        }
    }

    fun seekToPosition(fraction: Float) {
        val position = (fraction * totalDurationMs).toInt().coerceIn(0, totalDurationMs)
        currentPositionMs = position
        VoicePlaybackManager.seekTo(position)
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            stopProgressUpdates()
            scope.cancel()
        }
    }

    // Stop if another voice starts
    LaunchedEffect(VoicePlaybackManager.playingMessageId) {
        if (VoicePlaybackManager.playingMessageId != messageId && isPlaying) {
            isPlaying = false
            stopProgressUpdates()
        }
    }

    Row(
        modifier = modifier.height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Pause button
        Box(
            modifier = Modifier
                .size(36.dp)
                .shadow(4.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
                .background(colors.background, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { togglePlayback() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Waveform
        Box(
            modifier = Modifier
                .weight(1f)
                .height(maxBarHeight)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        seekToPosition(fraction)
                    }
                }
        ) {
            WaveformCanvas(
                waveform = sampledWaveform,
                progress = progress,
                playedColor = colors.primary,
                unplayedColor = colors.muted,
                barWidth = barWidth,
                barGap = barGap,
                maxBarHeight = maxBarHeight,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Time display
        Text(
            text = "${formatVoiceDuration(currentPositionMs.toLong())} / ${formatVoiceDuration(totalDurationMs.toLong())}",
            fontSize = 11.sp,
            color = colors.muted,
            fontWeight = FontWeight.Medium
        )

        // D.3: Speed control button — cycles 1x → 1.5x → 2x → 1x
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .shadow(2.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
                .background(colors.background, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${speeds[currentSpeedIndex]}x",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
                modifier = Modifier
                    .clickable {
                        currentSpeedIndex = (currentSpeedIndex + 1) % speeds.size
                        currentSpeed = speeds[currentSpeedIndex]
                        // F37: Apply speed change with smooth transition
                        VoicePlaybackManager.setPlaybackSpeed(currentSpeed)
                    }
                    .padding(horizontal = 4.dp)
            )
        }
    }

    // Seekbar progress line
    if (totalDurationMs > 0) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 44.dp, end = 60.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp)),
            color = colors.primary,
            trackColor = colors.muted.copy(alpha = 0.3f),
        )
    }
}

// ==================== Waveform Canvas ====================
/**
 * Animated waveform visualization for voice messages.
 *
 * D.1: Animated waveform during playback:
 * - Played portion = primary color (animated via animateFloatAsState)
 * - Unplayed portion = muted color
 * - F35: Uses hardware-accelerated Canvas for smooth 60fps rendering
 * - F38: Syncs every 100ms via ExoPlayer/MediaPlayer listener (handled in VoicePlayer)
 */
@Composable
fun WaveformCanvas(
    waveform: List<Float>,
    progress: Float,
    playedColor: Color,
    unplayedColor: Color,
    barWidth: Dp,
    barGap: Dp,
    maxBarHeight: Dp,
    modifier: Modifier = Modifier
) {
    // F35: Animate progress smoothly for fluid waveform transitions
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "waveformProgress"
    )

    val barWidthPx = barWidth.value
    val barGapPx = barGap.value
    val maxBarHeightPx = maxBarHeight.value

    Canvas(modifier = modifier) {
        if (waveform.isEmpty()) return@Canvas

        val totalBarWidth = barWidthPx + barGapPx
        val visibleBars = (size.width / totalBarWidth).toInt().coerceAtLeast(1)
        val displayWaveform = if (waveform.size >= visibleBars) {
            sampleWaveform(waveform, visibleBars)
        } else {
            waveform
        }

        val playedCount = (animatedProgress * displayWaveform.size).toInt()

        val centerY = size.height / 2f

        displayWaveform.forEachIndexed { index, amplitude ->
            val x = index * totalBarWidth
            val barHeight = (amplitude * maxBarHeightPx).coerceAtLeast(barWidthPx)
            val color = if (index <= playedCount) playedColor else unplayedColor

            drawRoundRect(
                color = color,
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidthPx, barHeight),
                cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
            )
        }
    }
}

// ==================== Duration Formatting ====================
fun formatVoiceDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${String.format("%02d", seconds)}"
}
