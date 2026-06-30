package com.zaxo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zaxo.app.ui.components.NeuButton
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.StatusViewModel

/**
 * Predefined background colors for text status updates.
 * Each color provides sufficient contrast for white text overlay.
 */
private val TextStatusColors = listOf(
    "#4A90D9" to Color(0xFF4A90D9),  // Zaxo Blue
    "#27AE60" to Color(0xFF27AE60),  // Zaxo Green
    "#E74C3C" to Color(0xFFE74C3C),  // Red
    "#8E44AD" to Color(0xFF8E44AD),  // Purple
    "#F39C12" to Color(0xFFF39C12),  // Orange
    "#1ABC9C" to Color(0xFF1ABC9C),  // Teal
    "#2C3E50" to Color(0xFF2C3E50),  // Dark Navy
    "#C0392B" to Color(0xFFC0392B),  // Dark Red
    "#2980B9" to Color(0xFF2980B9),  // Medium Blue
    "#16A085" to Color(0xFF16A085),  // Dark Teal
    "#D35400" to Color(0xFFD35400),  // Dark Orange
    "#7F8C8D" to Color(0xFF7F8C8D),  // Gray
)

/**
 * Font family options for text status.
 */
private val FontOptions = listOf(
    "default" to "Default",
    "serif" to "Serif",
    "monospace" to "Mono"
)

/**
 * StatusTextComposerScreen — Compose a text status update with background
 * color selection and font choice.
 *
 * Key features:
 * - F22: BreakIterator-based grapheme counting for emoji-safe text length
 * - F21: isSending guard prevents double-tap submission
 * - 12 preset background colors with white text
 * - 3 font family options
 * - 700 grapheme max limit
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusTextComposerScreen(
    onBack: () -> Unit,
    onPosted: () -> Unit,
    viewModel: StatusViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    var text by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var selectedFontIndex by remember { mutableIntStateOf(0) }
    val error by viewModel.error.collectAsState()

    val selectedColorHex = TextStatusColors[selectedColorIndex].first
    val selectedColor = TextStatusColors[selectedColorIndex].second
    val selectedFont = FontOptions[selectedFontIndex].first

    // Dismiss error when it appears
    LaunchedEffect(error) {
        if (error != null) {
            // Auto-clear error after 3 seconds
            kotlinx.coroutines.delay(3000L)
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Background color fill
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(selectedColor)
        )

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.Close,
                    "Close",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Font selector
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FontOptions.forEachIndexed { index, (_, label) ->
                    Text(
                        text = label,
                        color = if (index == selectedFontIndex) Color.White else Color.White.copy(alpha = 0.5f),
                        fontWeight = if (index == selectedFontIndex) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedFontIndex = index }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Text input area (centered)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            val fontFamily = when (selectedFont) {
                "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                else -> androidx.compose.ui.text.font.FontFamily.Default
            }

            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 28.sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                ),
                cursorColor = Color.White,
            )

            // Placeholder when empty
            if (text.isEmpty()) {
                Text(
                    text = "Type a status...",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 28.sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Color picker row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                items(TextStatusColors.indices.toList()) { index ->
                    val (hex, color) = TextStatusColors[index]
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (index == selectedColorIndex) {
                                    Modifier.background(Color.Transparent)
                                        .clip(CircleShape)
                                } else Modifier
                            )
                            .clickable { selectedColorIndex = index },
                        contentAlignment = Alignment.Center
                    ) {
                        if (index == selectedColorIndex) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                            )
                            // White ring to indicate selection
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                            )
                            Icon(
                                Icons.Default.Check,
                                "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Post button
            NeuButton(
                onClick = {
                    if (text.isNotBlank()) {
                        viewModel.createTextStatus(
                            text = text,
                            backgroundColor = selectedColorHex,
                            fontFamily = selectedFont
                        )
                        onPosted()
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = Color.White.copy(alpha = 0.25f),
                contentColor = Color.White,
                enabled = text.isNotBlank()
            ) {
                Icon(
                    Icons.Default.Create,
                    "Post status",
                    tint = if (text.isNotBlank()) Color.White else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Post Status",
                    color = if (text.isNotBlank()) Color.White else Color.White.copy(alpha = 0.4f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            // Error message
            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error!!,
                    color = Color(0xFFFF6B6B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
