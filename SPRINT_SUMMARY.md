# Zaxo Android — 5-Feature Sprint Summary

## Project Overview
- **App**: Zaxo — WhatsApp-style Android messaging app with Neumorphic UI
- **Path**: `/home/z/zaxo-android/`
- **Package**: `com.zaxo.app`
- **Stack**: Kotlin + Jetpack Compose (BOM 2024.01.00), Gradle 8.5, AGP 8.2.2, Kotlin 1.9.22
- **Firebase BOM 32.7.0** (Auth, Firestore, Messaging, Storage), project `zaxoeucc`
- **DI**: Hilt 2.50 | **Local DB**: Room 2.6.1 (v3 with FTS4) | **Image**: Coil 2.5.0
- **Architecture**: MVVM + Repository + DataStore + Firestore sync
- **E2E Encryption**: Signal Protocol
- **UI Colors**: Light (#E0E5EC bg), Dark (#2A2D36 bg), Accent Blue #4A90D9, Green #27AE60, Red #E74C3C
- **Build Note**: Gradle builds time out in tool environment; verification via code review only

---

## Sprint Stats
- **48 Kotlin files** (up from 32)
- **6 new files** created, **13 files** modified
- **~5,000+ lines** of new code
- **22 flaws** (F1-F22) resolved

---

## 5 Features Implemented

### 1. Voice Messages (Complete)
- **VoiceRecordButton.kt**: Press-and-hold via `awaitPointerEventScope`, slide-to-cancel (>120dp), OPUS/WebM encoding, 10-min cap
- **VoicePlayer.kt**: ExoPlayer playback, waveform seek bar, play/pause, progress tracking
- **VoiceMessageBubble.kt**: Chat bubble with embedded voice player
- **WaveformCanvas.kt**: Canvas-rendered bars (100 downsampled samples, 2dp bars, 1dp gap)
- **VoiceMessageViewModel.kt**: Firebase Storage upload, amplitude sampling every 100ms, WorkManager retry queue
- **VoicePlaybackManager.kt**: Singleton — prevents concurrent playback, earpiece routing (STREAM_VOICE_CALL)

### 2. Read Receipts (Complete)
- **MessageStatusIcon.kt**: SENDING→clock, SENT→✓gray, DELIVERED→✓✓gray, READ→✓✓blue, FAILED→red!
- Firestore batch writes for atomic status updates
- Privacy toggle (readReceiptsEnabled) — prevents READ transition when off
- Room syncState offline queue with WorkManager retry
- Per-member group tracking foundation

### 3. Chat Wallpapers (Complete)
- **WallpaperPickerScreen.kt**: 12 built-in (6 dark + 6 light) + custom photo picker
- Auto contrast detection: brightness <0.4 → dark (10% scrim), else → light (20% scrim)
- Custom image resize to 1080px before upload
- Firestore wallpaperId sync
- Dynamic dark scrim with auto text contrast (white/black switching)

### 4. Message Search (Complete)
- Room migration v2→v3 with FTS4 virtual table `messages_fts(content, chatId, senderId)`
- FTS4 MATCH query with LIKE fallback for partial matches
- 300ms debounce on Dispatchers.IO
- Results grouped by chatId, sorted by timestamp DESC
- 100-result pagination with loadMore()
- Empty state "No messages found"

### 5. Status/Stories (Core Complete — CameraX scaffolded)
- **StatusScreen.kt**: My Status card, contact grouping, filter chips (All/Unviewed/Viewed)
- **StatusViewerScreen.kt**: Auto-advancing viewer (photo=5s, text=7s, video=actual), absolute timestamp timer, reply field, view count
- **StatusCameraScreen.kt**: Scaffolded — CameraX permissions, capture button, flash/flip toggles (no live preview yet)
- **StatusTextComposerScreen.kt**: 12 color presets, 3 font styles, BreakIterator grapheme counting, isSending guard
- **StatusRing.kt**: Green=unviewed, Gray=viewed, Transparent=no status
- **StatusViewModel.kt** + **StatusViewerViewModel.kt**: Loading, posting, viewer state, progress tracking

---

## Key Algorithms
1. **Press-and-Hold**: IDLE→PRESSED→RECORDING→UPLOADING→SENT state machine, awaitPointerEventScope, 120dp cancel threshold
2. **Read Receipt**: SENDING→SENT→DELIVERED→READ flow, Firestore batch atomic writes, privacy gate
3. **Auto-Contrast**: Average brightness calculation → dark/light classification → scrim % + text color switch
4. **FTS4 Search**: MATCH with OR operator, LIKE fallback, group by chatId, 100/batch pagination
5. **Status Auto-Advance**: Absolute timestamps (not LaunchedEffect delay), hold-to-pause, expiry skip on advance

---

## 22 Flaws Fixed
| # | Area | Fix |
|---|------|-----|
| F1 | Voice | Permission rationale dialog with "Open Settings" |
| F2 | Voice | WorkManager retry for failed uploads |
| F3 | Voice | Earpiece routing via STREAM_VOICE_CALL |
| F4 | Voice | 10-minute recording cap |
| F5 | Voice | Waveform downsampled to 100 samples |
| F6 | Voice | VoicePlaybackManager singleton |
| F7 | Receipts | Firestore batch writes (atomic) |
| F8 | Receipts | Room syncState column for offline queue |
| F9 | Receipts | Privacy toggle (readReceiptsEnabled) |
| F10 | Receipts | Per-member tracking foundation |
| F11 | Wallpaper | Custom image resize to 1080px |
| F12 | Wallpaper | Firestore wallpaperId sync |
| F13 | Wallpaper | Auto contrast detection + dynamic scrim |
| F14 | Search | Room migration v2→v3 with FTS4 |
| F15 | Search | Debounced on Dispatchers.IO |
| F16 | Search | Empty state "No messages found" |
| F17 | Search | 100-result pagination |
| F18 | Status | Mid-view expiry check |
| F19 | Status | Firestore listener for block/delete |
| F20 | Status | count() query (not integer field) |
| F21 | Status | isSending guard |
| F22 | Status | BreakIterator grapheme counting |

---

## Dependencies Added
```kotlin
implementation("com.jakewharton.timber:timber:5.0.1")
implementation("androidx.media3:media3-exoplayer:1.2.1")
implementation("androidx.media3:media3-ui:1.2.1")
implementation("androidx.work:work-runtime-ktx:2.9.0")
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")
```

---

## Known Gaps (Next Sprint)
- **Status CameraX**: Live preview not implemented (scaffolded only)
- **Status Upload**: Firebase Storage upload wired but untested
- **Voice**: No animated waveform, haptics, speed control, or transcription
- **Read Receipts**: No "Read by X, Y, Z" detail screen; no per-member group UI
- **Wallpapers**: No live preview before applying; no dimming slider
- **Search**: No highlight in results; no date/type filters; no in-chat search

---

## ⚠️ SECURITY ALERT
**Firebase Admin SDK private key for project `zaxoeucc` was accidentally leaked in a prior session — must be rotated immediately.**

---

## Next Sprint Priorities
1. **Status/Stories Completion** (Week 1-2): CameraX live preview, Firebase Storage upload, status reply, mute, 24h auto-delete Cloud Function
2. **Voice Polish** (Week 3): Animated waveform, haptic feedback, speed control, background upload indicator
3. **Search Enhancement** (Week 4): Highlighted matches, date range picker, type filter chips, in-chat search
4. **Group Chat Features** (Week 5): Per-member read receipts UI, @mentions, polls, group description editing
5. **Production Hardening** (Week 6): Crashlytics, performance profiling, ProGuard/R8, Play Store signing, Firebase security rules audit
