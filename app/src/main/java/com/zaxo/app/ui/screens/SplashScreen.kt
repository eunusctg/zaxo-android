package com.zaxo.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaxo.app.ui.theme.ZaxoTheme
import kotlinx.coroutines.delay

// ==================== Splash State Machine ====================
enum class SplashState {
    LOADING,
    AUTHENTICATED,
    UNAUTHENTICATED,
    TIMEOUT
}

// ==================== Splash Screen ====================
@Composable
fun SplashScreen(
    onAuthenticated: () -> Unit,
    onUnauthenticated: () -> Unit,
    authCheck: suspend () -> Boolean
) {
    val colors = ZaxoTheme.colors
    var splashState by remember { mutableStateOf(SplashState.LOADING) }
    var showTimeoutMessage by remember { mutableStateOf(false) }

    // Logo pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "splashPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    // Auth check + minimum display time
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        val minDisplayTime = 1500L
        val maxDisplayTime = 5000L

        // Start auth check in parallel with minimum display time
        var authResult: Boolean? = null
        var authTimedOut = false

        // Launch auth check
        val authJob = kotlinx.coroutines.async {
            try {
                authCheck()
            } catch (e: Exception) {
                // F59: Catch token refresh error, treat as unauthenticated
                false
            }
        }

        // Wait for minimum display time
        delay(minDisplayTime)

        // Wait for auth result with timeout
        try {
            authResult = kotlinx.coroutines.withTimeoutOrNull(maxDisplayTime - minDisplayTime) {
                authJob.await()
            }
        } catch (e: Exception) {
            authResult = null
        }

        when (authResult) {
            true -> {
                splashState = SplashState.AUTHENTICATED
                delay(300) // Brief delay for animation
                onAuthenticated()
            }
            false -> {
                splashState = SplashState.UNAUTHENTICATED
                delay(300)
                onUnauthenticated()
            }
            null -> {
                // F58: Timeout — show message but continue waiting
                splashState = SplashState.TIMEOUT
                showTimeoutMessage = true
                // Continue waiting for Firebase
                try {
                    val lateResult = kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                        authJob.await()
                    }
                    splashState = if (lateResult == true) SplashState.AUTHENTICATED else SplashState.UNAUTHENTICATED
                    delay(300)
                    if (lateResult == true) onAuthenticated() else onUnauthenticated()
                } catch (e: Exception) {
                    onUnauthenticated()
                }
            }
        }
    }

    // Splash UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Zaxo logo with pulse animation
            Box(
                modifier = Modifier
                    .size(100.dp * scale)
                    .shadow(12.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
                    .background(colors.primary, CircleShape)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Zaxo",
                    tint = colors.onPrimary,
                    modifier = Modifier.size(48.dp * scale)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Brand text
            Text(
                text = "ZAXO",
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = colors.onSurface,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Private messaging",
                fontSize = 16.sp,
                color = colors.muted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Loading indicator
            if (splashState == SplashState.LOADING || splashState == SplashState.TIMEOUT) {
                CircularProgressIndicator(
                    color = colors.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp)
                )

                // F58: Timeout message
                if (showTimeoutMessage) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Taking longer than expected...",
                        fontSize = 13.sp,
                        color = colors.muted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
