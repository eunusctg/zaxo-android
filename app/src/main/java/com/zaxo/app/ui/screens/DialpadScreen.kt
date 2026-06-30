package com.zaxo.app.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zaxo.app.model.CallMediaType
import com.zaxo.app.model.LookupResult
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.CallViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DialpadScreen(
    onBack: () -> Unit = {},
    onCallStarted: () -> Unit = {},
    viewModel: CallViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

    var dialInput by remember { mutableStateOf("") }
    var lookupResult by remember { mutableStateOf<LookupResult?>(null) }
    var lookupJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // F118: Debounced lookup with coroutine cancellation
    LaunchedEffect(dialInput) {
        lookupJob?.cancel()
        if (dialInput.replace(Regex("[^0-9]"), "").length >= 3) {
            lookupJob = scope.launch {
                delay(300) // 300ms debounce
                val clean = dialInput.replace(Regex("[^0-9]"), "")
                lookupResult = viewModel.lookupZaxoNumber(clean)
            }
        } else {
            lookupResult = null
        }
    }

    // Format display: XXX XXX XXX
    val cleanInput = dialInput.replace(Regex("[^0-9]"), "") // F119: Strip non-digits
    val formattedDisplay = buildString {
        for (i in cleanInput.indices) {
            if (i == 3 || i == 6) append(' ')
            append(cleanInput[i])
        }
    }

    val hasValidLength = cleanInput.length == 9

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = colors.onSurface)
            }
            Text(
                "Dialpad",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (formattedDisplay.isEmpty()) "Enter Zaxo Number" else formattedDisplay,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = if (formattedDisplay.isEmpty()) colors.muted else colors.onSurface,
                    textAlign = TextAlign.Center
                )

                // Lookup result
                when (val result = lookupResult) {
                    is LookupResult.Found -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result.displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.primary
                        )
                    }
                    is LookupResult.Hidden -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Zaxo User (name hidden)",
                            fontSize = 14.sp,
                            color = colors.muted
                        )
                    }
                    is LookupResult.NotFound -> {
                        if (cleanInput.length >= 3) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Zaxo user found",
                                fontSize = 14.sp,
                                color = colors.error
                            )
                        }
                    }
                    null -> {}
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Dialpad grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val keys = listOf(
                listOf("1" to "", "2" to "ABC", "3" to "DEF"),
                listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
                listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
                listOf("*" to "", "0" to "+", "#" to "")
            )

            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { (digit, letters) ->
                        DialpadKey(
                            digit = digit,
                            letters = letters,
                            onPress = {
                                // Haptic feedback
                                vibrator?.let {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        it.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                                    } else {
                                        @Suppress("DEPRECATION")
                                        it.vibrate(30)
                                    }
                                }
                                if (cleanInput.length < 9) {
                                    dialInput += digit
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom row: Video call + Call + Backspace
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video call button
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(6.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
                    .background(colors.surface, CircleShape)
                    .clickable(enabled = hasValidLength && lookupResult is LookupResult.Found) {
                        val result = lookupResult as? LookupResult.Found ?: return@clickable
                        viewModel.startOutgoingCall(
                            calleeUid = result.uid,
                            calleeName = result.displayName,
                            calleePhotoUrl = result.photoUrl,
                            calleeZaxoNumber = result.zaxoNumber,
                            mediaType = CallMediaType.VIDEO
                        )
                        onCallStarted()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Videocam,
                    "Video Call",
                    modifier = Modifier.size(28.dp),
                    tint = if (hasValidLength) colors.primary else colors.muted
                )
            }

            // Audio call button (pulsing green when valid)
            val infiniteTransition = rememberInfiniteTransition(label = "callPulse")
            val callPulse by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (hasValidLength) 1.1f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "callPulse"
            )

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer {
                        scaleX = callPulse
                        scaleY = callPulse
                    }
                    .shadow(8.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
                    .background(
                        if (hasValidLength) Color(0xFF27AE60) else colors.surface,
                        CircleShape
                    )
                    .clickable(enabled = hasValidLength && lookupResult is LookupResult.Found) {
                        val result = lookupResult as? LookupResult.Found ?: return@clickable
                        viewModel.startOutgoingCall(
                            calleeUid = result.uid,
                            calleeName = result.displayName,
                            calleePhotoUrl = result.photoUrl,
                            calleeZaxoNumber = result.zaxoNumber,
                            mediaType = CallMediaType.AUDIO
                        )
                        onCallStarted()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Call,
                    "Call",
                    modifier = Modifier.size(32.dp),
                    tint = if (hasValidLength) Color.White else colors.muted
                )
            }

            // Backspace
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clickable {
                        if (dialInput.isNotEmpty()) {
                            dialInput = dialInput.dropLast(1)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Backspace,
                    "Delete",
                    modifier = Modifier.size(28.dp),
                    tint = if (dialInput.isNotEmpty()) colors.onSurface else colors.muted
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DialpadKey(
    digit: String,
    letters: String,
    onPress: () -> Unit
) {
    val colors = ZaxoTheme.colors

    // Spring press animation
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "keyScale"
    )

    Column(
        modifier = Modifier
            .size(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(4.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
            .background(colors.surface, CircleShape)
            .clickable {
                isPressed = true
                onPress()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = digit,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )
        if (letters.isNotEmpty()) {
            Text(
                text = letters,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = colors.muted,
                letterSpacing = 2.sp
            )
        }
    }

    // Reset pressed state
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(150)
            isPressed = false
        }
    }
}
