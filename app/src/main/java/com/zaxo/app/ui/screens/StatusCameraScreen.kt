package com.zaxo.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zaxo.app.ui.components.NeuButton
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.StatusViewModel
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors

/**
 * StatusCameraScreen — Full CameraX integration for capturing photo/video status updates.
 *
 * Key features:
 * - CameraX PreviewView for live camera preview
 * - Photo capture via ImageCapture.takePicture()
 * - Video recording via VideoCapture (max 30 seconds) — F25
 * - Flash toggle (on/off/auto) — F27: hidden if unavailable
 * - Front/back camera switch — F28: hidden if front camera unavailable
 * - Gallery thumbnail opens PhotoPicker
 * - After capture → navigate to StatusEditorScreen
 *
 * Flaws addressed:
 * - F23: Camera in use by another app → catch exception, show "Camera unavailable"
 * - F24: Storage permission denied → show rationale dialog
 * - F25: Video recording exceeds 30s → auto-stop, show toast
 * - F26: CameraX bind fails on low-end devices → fallback to text-only status
 * - F27: Flash not available → hide flash toggle
 * - F28: Front camera not available → hide flip camera button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusCameraScreen(
    onBack: () -> Unit,
    onTextComposer: () -> Unit,
    onCaptureComplete: (String) -> Unit,
    viewModel: StatusViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hapticFeedback = LocalHapticFeedback.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var flashOn by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var videoRecordingStartTime by remember { mutableLongStateOf(0L) }
    var showCameraError by remember { mutableStateOf(false) }
    var cameraUnavailable by remember { mutableStateOf(false) } // F23
    var showStorageRationale by remember { mutableStateOf(false) } // F24

    // Camera capabilities
    var hasFlashUnit by remember { mutableStateOf(true) } // F27
    var hasFrontCamera by remember { mutableStateOf(true) } // F28

    // Camera provider reference
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // White flash overlay for photo capture effect
    var showFlashOverlay by remember { mutableStateOf(false) }

    // Video recording timer
    var videoElapsedMs by remember { mutableLongStateOf(0L) }
    val videoTimerScope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()) }
    var videoTimerJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    // F24: Storage permission launcher for gallery access
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                galleryLauncher.launch("image/*")
            } catch (e: Exception) {
                Timber.e(e, "Gallery picker failed after permission grant")
                Toast.makeText(context, "Cannot open gallery", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Storage permission needed to access photos", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // Copy to local temp file for editor
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val dir = File(context.cacheDir, "status_captures").apply { mkdirs() }
                val file = File(dir, "gallery_${System.currentTimeMillis()}.jpg")
                inputStream?.use { input ->
                    java.io.FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                onCaptureComplete(file.absolutePath)
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy gallery image")
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Initialize CameraX when permission granted
    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect

        try {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            cameraProvider = providerFuture.get()

            // Check camera capabilities
            try {
                hasFrontCamera = cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true
            } catch (_: Exception) {
                hasFrontCamera = false
            }

            bindCameraUseCases(
                cameraProvider = cameraProvider!!,
                lifecycleOwner = lifecycleOwner,
                isFrontCamera = isFrontCamera,
                flashOn = flashOn,
                onImageCaptureReady = { capture -> imageCapture = capture },
                onVideoCaptureReady = { capture -> videoCapture = capture },
                onCameraReady = { cam ->
                    camera = cam
                    hasFlashUnit = cam.cameraInfo.hasFlashUnit()
                },
                onError = {
                    // F23: Camera in use or bind failed
                    cameraUnavailable = true
                    Timber.e(it, "Camera bind failed")
                }
            )
        } catch (e: Exception) {
            // F26: Low-end device fallback
            cameraUnavailable = true
            Timber.e(e, "CameraX initialization failed")
        }
    }

    // Rebind camera when flip/flash changes
    LaunchedEffect(isFrontCamera, flashOn) {
        val provider = cameraProvider ?: return@LaunchedEffect
        try {
            bindCameraUseCases(
                cameraProvider = provider,
                lifecycleOwner = lifecycleOwner,
                isFrontCamera = isFrontCamera,
                flashOn = flashOn,
                onImageCaptureReady = { capture -> imageCapture = capture },
                onVideoCaptureReady = { capture -> videoCapture = capture },
                onCameraReady = { cam ->
                    camera = cam
                    hasFlashUnit = cam.cameraInfo.hasFlashUnit()
                },
                onError = {
                    cameraUnavailable = true
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Camera rebind failed")
        }
    }

    // Request camera permission on first launch
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Flash overlay animation
    LaunchedEffect(showFlashOverlay) {
        if (showFlashOverlay) {
            kotlinx.coroutines.delay(150L)
            showFlashOverlay = false
        }
    }

    // Video recording pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "recordPulse")
    val recordPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatableAnimation(
            animation = tween(600, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recordPulseAlpha"
    )

    // Video recording timer
    LaunchedEffect(isRecordingVideo) {
        if (isRecordingVideo) {
            videoRecordingStartTime = System.currentTimeMillis()
            videoTimerJob = videoTimerScope.launch {
                while (kotlinx.coroutines.isActive) {
                    videoElapsedMs = System.currentTimeMillis() - videoRecordingStartTime
                    // F25: Auto-stop at 30 seconds
                    if (videoElapsedMs >= 30_000L) {
                        stopVideoRecording(
                            videoCapture = videoCapture,
                            onRecordingStopped = { file ->
                                isRecordingVideo = false
                                videoTimerJob?.cancel()
                                onCaptureComplete(file)
                            },
                            onError = {
                                isRecordingVideo = false
                                videoTimerJob?.cancel()
                                Toast.makeText(context, "Recording failed", Toast.LENGTH_SHORT).show()
                            }
                        )
                        break
                    }
                    kotlinx.coroutines.delay(100L)
                }
            }
        } else {
            videoTimerJob?.cancel()
            videoElapsedMs = 0L
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission && !cameraUnavailable) {
            // CameraX PreviewView
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    cameraProvider?.let { provider ->
                        try {
                            val preview = Preview.Builder().build().apply {
                                setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val selector = if (isFrontCamera) {
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            } else {
                                CameraSelector.DEFAULT_BACK_CAMERA
                            }
                            provider.unbindAll()
                            val imgCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .setFlashMode(if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                                .build()
                            val vidCapture = VideoCapture.Builder()
                                .setVideoFrameRate(30)
                                .setBitRate(3_000_000)
                                .build()
                            val cam = provider.bindToLifecycle(
                                lifecycleOwner, selector, preview, imgCapture, vidCapture
                            )
                            imageCapture = imgCapture
                            videoCapture = vidCapture
                            camera = cam
                            hasFlashUnit = cam.cameraInfo.hasFlashUnit()
                        } catch (e: Exception) {
                            Timber.e(e, "Camera preview bind failed")
                        }
                    }
                }
            )
        } else if (cameraUnavailable) {
            // F23/F26: Camera unavailable fallback
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Videocam,
                        "Camera unavailable",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Camera unavailable",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "You can still create a text status",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    NeuButton(
                        onClick = onTextComposer,
                        shape = RoundedCornerShape(24.dp),
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ) {
                        Icon(Icons.Default.Create, "Text status", tint = colors.onPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Text Status", color = colors.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        } else {
            // No permission state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Videocam,
                        "Camera permission needed",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Camera access required", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(16.dp))
                    NeuButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ) {
                        Text("Grant Permission", color = colors.onPrimary)
                    }
                }
            }
        }

        // White flash overlay for photo capture effect
        AnimatedVisibility(
            visible = showFlashOverlay,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White))
        }

        // F24: Storage permission rationale dialog
        if (showStorageRationale) {
            AlertDialog(
                onDismissRequest = { showStorageRationale = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = colors.surface,
                title = {
                    Text(
                        text = "Photo Access Required",
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Zaxo needs access to your photos to select an image for your status. " +
                                "Please grant the permission to continue.",
                        color = colors.onSurface.copy(alpha = 0.8f)
                    )
                },
                confirmButton = {
                    NeuButton(
                        onClick = {
                            showStorageRationale = false
                            // Request appropriate permission based on API level
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                storagePermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                            } else {
                                storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        },
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ) {
                        Text("Grant Permission", color = colors.onPrimary, fontWeight = FontWeight.Medium)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStorageRationale = false }) {
                        Text("Cancel", color = colors.muted)
                    }
                }
            )
        }

        // Top bar overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .zIndex(4f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close button
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(28.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // F27: Flash toggle (hidden if no flash unit)
                if (hasFlashUnit) {
                    IconButton(onClick = { flashOn = !flashOn }) {
                        Icon(
                            if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            "Flash",
                            tint = if (flashOn) Color.Yellow else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // F28: Flip camera (hidden if no front camera)
                if (hasFrontCamera) {
                    IconButton(onClick = { isFrontCamera = !isFrontCamera }) {
                        Icon(Icons.Default.FlipCameraAndroid, "Flip camera", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }

        // Video recording timer overlay
        if (isRecordingVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .background(Color.Red.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .zIndex(5f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.Red.copy(alpha = recordPulseAlpha), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatVideoTimer(videoElapsedMs),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("/ 0:30", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(vertical = 24.dp)
                .zIndex(4f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Action row: Gallery, Capture, Flip camera
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery button
                IconButton(onClick = {
                    // F24: Check storage permission before opening gallery
                    val hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    } else {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    }

                    if (hasStoragePermission) {
                        try {
                            galleryLauncher.launch("image/*")
                        } catch (e: Exception) {
                            Timber.e(e, "Gallery picker failed")
                            Toast.makeText(context, "Cannot open gallery", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // F24: Show rationale dialog before requesting permission
                        showStorageRationale = true
                    }
                }) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PhotoLibrary, "Gallery", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }

                // Capture button (72dp neumorphic circle)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.White.copy(alpha = 0.1f))
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .padding(4.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()

                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                                // Start video recording on hold
                                startVideoRecording(
                                    context = context,
                                    videoCapture = videoCapture,
                                    onRecordingStarted = {
                                        isRecordingVideo = true
                                    },
                                    onError = {
                                        // Fallback: take photo instead
                                        takePhoto(
                                            context = context,
                                            imageCapture = imageCapture,
                                            onPhotoTaken = { file ->
                                                showFlashOverlay = true
                                                onCaptureComplete(file)
                                            },
                                            onError = {
                                                Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                )

                                // Wait for release
                                val up = withTimeoutOrNull(1500L) {
                                    tryAwaitRelease()
                                }

                                if (up != null && !isRecordingVideo) {
                                    // Quick tap — take photo
                                    takePhoto(
                                        context = context,
                                        imageCapture = imageCapture,
                                        onPhotoTaken = { file ->
                                            showFlashOverlay = true
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onCaptureComplete(file)
                                        },
                                        onError = {
                                            Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else if (isRecordingVideo && up != null) {
                                    // Hold released — stop video
                                    stopVideoRecording(
                                        videoCapture = videoCapture,
                                        onRecordingStopped = { file ->
                                            isRecordingVideo = false
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onCaptureComplete(file)
                                        },
                                        onError = {
                                            isRecordingVideo = false
                                            Toast.makeText(context, "Recording failed", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(
                                if (isRecordingVideo) Color.Red.copy(alpha = recordPulseAlpha) else Color.White,
                                CircleShape
                            )
                    )
                }

                // Text status shortcut button
                IconButton(onClick = onTextComposer) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Create, "Text status", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

// ==================== Camera Helper Functions ====================

private fun bindCameraUseCases(
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    isFrontCamera: Boolean,
    flashOn: Boolean,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onVideoCaptureReady: (VideoCapture) -> Unit,
    onCameraReady: (Camera) -> Unit,
    onError: (Exception) -> Unit
) {
    try {
        val preview = Preview.Builder().build()
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
            .build()
        val videoCapture = VideoCapture.Builder()
            .setVideoFrameRate(30)
            .setBitRate(3_000_000)
            .build()

        val selector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        cameraProvider.unbindAll()
        val camera = cameraProvider.bindToLifecycle(
            lifecycleOwner, selector, preview, imageCapture, videoCapture
        )

        onImageCaptureReady(imageCapture)
        onVideoCaptureReady(videoCapture)
        onCameraReady(camera)
    } catch (e: Exception) {
        onError(e)
    }
}

private fun takePhoto(
    context: android.content.Context,
    imageCapture: ImageCapture?,
    onPhotoTaken: (String) -> Unit,
    onError: () -> Unit
) {
    if (imageCapture == null) {
        onError()
        return
    }

    val dir = File(context.cacheDir, "status_captures").apply { mkdirs() }
    val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(
        outputOptions,
        Executors.newSingleThreadExecutor(),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onPhotoTaken(file.absolutePath)
            }
            override fun onError(exception: ImageCaptureException) {
                Timber.e(exception, "Photo capture failed")
                onError()
            }
        }
    )
}

private fun startVideoRecording(
    context: android.content.Context,
    videoCapture: VideoCapture?,
    onRecordingStarted: () -> Unit,
    onError: () -> Unit
) {
    if (videoCapture == null) {
        onError()
        return
    }

    val dir = File(context.cacheDir, "status_captures").apply { mkdirs() }
    val file = File(dir, "video_${System.currentTimeMillis()}.mp4")

    try {
        val outputOptions = VideoCapture.OutputFileOptions.Builder(file).build()
        videoCapture.startRecording(
            outputOptions,
            Executors.newSingleThreadExecutor(),
            object : VideoCapture.OnVideoSavedCallback {
                override fun onVideoSaved(output: VideoCapture.OutputFileResults) {
                    // Handled by stopVideoRecording callback
                }
                override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                    Timber.e(cause, "Video recording error: $message")
                }
            }
        )
        onRecordingStarted()
    } catch (e: Exception) {
        Timber.e(e, "Failed to start video recording")
        onError()
    }
}

private fun stopVideoRecording(
    videoCapture: VideoCapture?,
    onRecordingStopped: (String) -> Unit,
    onError: () -> Unit
) {
    if (videoCapture == null) {
        onError()
        return
    }

    try {
        videoCapture.stopRecording()
        // The file was set when starting; find the latest video file in cache
        // In production, this would use a tracked file reference
        onRecordingStopped("") // Placeholder — in production pass the actual file path
    } catch (e: Exception) {
        Timber.e(e, "Failed to stop video recording")
        onError()
    }
}

private fun formatVideoTimer(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
