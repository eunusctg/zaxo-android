# Task: Create 3 Kotlin Voice Message Feature Files

## Summary
Created 3 Kotlin files for the Zaxo Android voice message feature at `/home/z/zaxo-android/app/src/main/java/com/zaxo/app/ui/components/`.

## Files Created

### 1. VoiceRecordButton.kt
- **Press-and-hold** mic button using `detectDragGestures` for drag-to-record interaction
- **Recording UI**: Pulsing red dot (animated with `infiniteTransition`), mm:ss timer, slide-to-cancel indicator with `graphicsLayer` translation
- **Slide left to cancel**: Drag offset tracked on X axis, cancellation threshold at 120dp; files are deleted on cancel
- **F4: 10-minute cap** enforced by `maxDurationMs` (default 600,000ms), auto-stops when reached
- **Amplitude sampling every 100ms**: Coroutine job captures `MediaRecorder.maxAmplitude`, normalizes to 0-1f range, stores in list for waveform data
- **Callback**: `onRecordingComplete(mediaUrl: String, duration: Long, waveform: List<Float>)`
- **MediaRecorder config**: WEBM output + OPUS encoder, 128kbps bit rate, 48kHz sampling
- **F1: Permission rationale dialog**: `PermissionRationaleDialog` composable with "Open Settings" button that navigates to app settings via `ACTION_APPLICATION_DETAILS_SETTINGS`
- **Edge cases**: Recordings under 1 second are discarded; recorder exceptions handled silently; cleanup in `DisposableEffect`

### 2. VoicePlayer.kt
- **VoicePlaybackManager** (singleton object, F6): Manages a single `MediaPlayer` instance; `startPlayback()` stops any currently playing voice before starting a new one; tracks `currentMessageId` and `onPlaybackStopped` callback
- **WaveformCanvas**: Custom `Canvas` composable drawing 2dp-wide bars with 1dp gaps, max 24dp height; played portion in `primary` color, unplayed in `muted` color; bars drawn as `drawRoundRect` with rounded caps
- **Tap waveform to seek**: `detectTapGestures` on the waveform Box calculates fraction from tap offset, calls `VoicePlaybackManager.seekTo()`
- **Play/pause button**: Neuomorphic circle with `Icons.Default.PlayArrow`/`Pause`, toggles via `VoicePlaybackManager` pause/resume/start
- **Progress updates**: Coroutine polling `getCurrentPosition()` every 50ms during playback
- **Seekbar**: `LinearProgressIndicator` below waveform showing playback progress
- **Waveform helpers**: `parseWaveform()` parses comma-separated string from Message.waveform; `sampleWaveform()` downsamples to target bar count using max-amplitude bucketing
- **Duration formatting**: `formatVoiceDuration(ms)` returns "m:ss" format
- **Edge cases**: Empty waveform defaults to uniform 0.3f bars; MediaDataSource errors handled; disposed coroutine scope

### 3. VoiceMessageBubble.kt
- **Wraps VoicePlayer** inside a `Surface` with `RoundedCornerShape` matching existing `MessageBubble` styling (16dp corners, 4dp tail corner)
- **isOwnMessage**: Changes alignment (End/Start), bubble color (`outgoingBubble`/`incomingBubble`), and corner tail position
- **Sender name**: Shown for group chats when `!isOwnMessage && isGroupChat`
- **Duration display**: "m:ss" format via `formatVoiceDuration()`
- **Status icon**: Private `StatusIcon` composable handles all `MessageStatus` values (SENDING->faded single check, SENT->single check, DELIVERED->double check muted, READ->double check primary, FAILED->red check)
- **Layout**: Matches MessageBubble exactly -- `widthIn(max = 280.dp)`, `padding(10.dp)`, `padding(horizontal = 8.dp, vertical = 2.dp)` on outer Box

## Dependencies
- Uses `ZaxoTheme.colors` for theming (primary, muted, error, shadowDark, shadowLight, background, outgoingBubble, incomingBubble, surface, onSurface, onPrimary)
- Uses `NeuButton` from NeuComponents.kt in PermissionRationaleDialog
- Uses `Message` and `MessageStatus` from `com.zaxo.app.model`
- All files follow existing code style (comment headers, ZaxoTheme access pattern, Surface-based bubbles)
