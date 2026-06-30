package com.zaxo.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaxo.app.ui.components.NeuButton
import com.zaxo.app.ui.theme.ZaxoTheme

// ==================== Onboarding Page ====================
data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String
)

val onboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Default.Lock,
        title = "Private Messaging",
        description = "End-to-end encrypted messages. Only you and the recipient can read them. Your conversations stay truly private with Signal Protocol encryption."
    ),
    OnboardingPage(
        icon = Icons.Default.Phone,
        title = "P2P Calls",
        description = "Crystal-clear voice and video calls with your Zaxo Number. Connect directly with friends and family anywhere in the world."
    ),
    OnboardingPage(
        icon = Icons.Default.Update,
        title = "Status Updates",
        description = "Share moments that disappear after 24 hours. Express yourself with photos, videos, and text statuses."
    )
)

// ==================== Onboarding Screen ====================
@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit
) {
    val colors = ZaxoTheme.colors
    var currentPage by remember { mutableIntStateOf(0) }
    val isLastPage = currentPage == onboardingPages.lastIndex

    // F60: Mark onboarding as seen when user completes or skips
    val context = androidx.compose.ui.platform.LocalContext.current
    val markOnboardingSeen = remember {
        {
            val prefs = context.getSharedPreferences("zaxo_prefs", 0)
            prefs.edit().putBoolean("hasSeenOnboarding", true).apply()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Skip button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = {
                markOnboardingSeen()
                onSkip()
            }) {
                Text(
                    "Skip",
                    color = colors.muted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Page content with animated transitions
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "onboardingPage"
        ) { page ->
            val pageData = onboardingPages[page]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Feature icon
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .shadow(12.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
                        .background(colors.primary.copy(alpha = 0.12f), CircleShape)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = pageData.icon,
                        contentDescription = pageData.title,
                        tint = colors.primary,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Title
                Text(
                    text = pageData.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Description
                Text(
                    text = pageData.description,
                    fontSize = 16.sp,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Page indicator dots
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            onboardingPages.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentPage) 10.dp else 8.dp)
                        .background(
                            if (index == currentPage) colors.primary else colors.muted.copy(alpha = 0.4f),
                            CircleShape
                        )
                )
                if (index < onboardingPages.lastIndex) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Get Started / Next button
        NeuButton(
            onClick = {
                if (isLastPage) {
                    markOnboardingSeen()
                    onGetStarted()
                } else {
                    currentPage++
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (isLastPage) "Get Started" else "Next",
                color = colors.onPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
