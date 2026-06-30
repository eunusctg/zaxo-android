package com.zaxo.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.zaxo.app.model.BuiltInWallpapers
import com.zaxo.app.ui.components.NeuButton
import com.zaxo.app.ui.components.NeuCard
import com.zaxo.app.ui.components.NeuDivider
import com.zaxo.app.ui.components.NeuElevatedCard
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.WallpaperViewModel

/**
 * Maps each built-in wallpaper ID to a representative preview color.
 * Dark wallpapers use dark tones; light wallpapers use light tones.
 */
private val WallpaperPreviewColors: Map<String, Color> = mapOf(
    "builtin_dark_dots" to Color(0xFF1A1A2E),
    "builtin_dark_waves" to Color(0xFF16213E),
    "builtin_dark_geometric" to Color(0xFF0F3460),
    "builtin_dark_gradient" to Color(0xFF1A1A2E),
    "builtin_dark_stars" to Color(0xFF0D1117),
    "builtin_dark_abstract" to Color(0xFF2D2D3F),
    "builtin_light_dots" to Color(0xFFF5F5DC),
    "builtin_light_waves" to Color(0xFFE8F0FE),
    "builtin_light_geometric" to Color(0xFFD4E6F1),
    "builtin_light_gradient" to Color(0xFFFDF2E9),
    "builtin_light_floral" to Color(0xFFFDEBD0),
    "builtin_light_abstract" to Color(0xFFEBF5FB)
)

/**
 * Determines if a wallpaper ID corresponds to a dark wallpaper.
 */
private fun isDarkWallpaper(wallpaperId: String): Boolean {
    return BuiltInWallpapers.DARK_WALLPAPERS.contains(wallpaperId)
}

/**
 * Extracts a human-readable label from a wallpaper ID.
 * e.g. "builtin_dark_dots" → "Dots"
 */
private fun wallpaperLabel(wallpaperId: String): String {
    return wallpaperId
        .removePrefix("builtin_dark_")
        .removePrefix("builtin_light_")
        .replaceFirstChar { it.uppercase() }
}

/**
 * F11: Maximum dimension for custom wallpaper images to avoid OOM and keep performance.
 */
private const val CUSTOM_WALLPAPER_MAX_DIMENSION = 1080

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperPickerScreen(
    chatId: String,
    onBack: () -> Unit,
    onApply: () -> Unit,
    viewModel: WallpaperViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val selectedWallpaper by viewModel.selectedWallpaper.collectAsState()
    val currentWallpaper by viewModel.currentWallpaper.collectAsState()
    val isApplying by viewModel.isApplying.collectAsState()

    // F13: Auto-detect brightness of selected wallpaper
    val isDarkDetected = selectedWallpaper != null && isDarkWallpaper(selectedWallpaper!!)

    // Photo picker launcher for custom wallpaper
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // F11: Resize the image to max 1080px dimension before saving
            kotlinx.coroutines.MainScope().launch {
                val resizedPath = viewModel.resizeAndSaveCustomWallpaper(it)
                if (resizedPath != null) {
                    viewModel.selectWallpaper("custom:$resizedPath")
                } else {
                    // Fallback: use URI directly if resize fails
                    viewModel.selectWallpaper("custom:${it.toString()}")
                }
            }
        }
    }

    // Determine if a custom photo is selected
    val isCustomPhoto = selectedWallpaper?.startsWith("custom:") == true
    val customPhotoUri = if (isCustomPhoto) {
        selectedWallpaper!!.removePrefix("custom:")
    } else null

    // Track whether we are showing the "no wallpaper" (default) state
    val isDefault = selectedWallpaper.isNullOrEmpty()

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Wallpaper",
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = colors.onSurface)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // ==================== Section 1: Built-in Wallpapers ====================
            SectionHeader(title = "Built-in Wallpapers")

            Spacer(modifier = Modifier.height(8.dp))

            // Dark wallpapers label
            Text(
                text = "Dark",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.muted,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                items(BuiltInWallpapers.DARK_WALLPAPERS) { wallpaperId ->
                    WallpaperGridItem(
                        wallpaperId = wallpaperId,
                        isSelected = selectedWallpaper == wallpaperId,
                        onClick = { viewModel.selectWallpaper(wallpaperId) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Light wallpapers label
            Text(
                text = "Light",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.muted,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false
            ) {
                items(BuiltInWallpapers.LIGHT_WALLPAPERS) { wallpaperId ->
                    WallpaperGridItem(
                        wallpaperId = wallpaperId,
                        isSelected = selectedWallpaper == wallpaperId,
                        onClick = { viewModel.selectWallpaper(wallpaperId) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            NeuDivider()

            Spacer(modifier = Modifier.height(16.dp))

            // ==================== Section 2: Custom Photo ====================
            SectionHeader(title = "Custom Photo")

            Spacer(modifier = Modifier.height(8.dp))

            NeuElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    photoPickerLauncher.launch("image/*")
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        "Choose from Photos",
                        tint = colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Choose from Photos",
                        fontSize = 15.sp,
                        color = colors.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.ChevronRight,
                        "Open",
                        tint = colors.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // F11 hint for custom wallpaper resizing
            if (isCustomPhoto) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Custom images are resized to max ${CUSTOM_WALLPAPER_MAX_DIMENSION}px",
                    fontSize = 11.sp,
                    color = colors.muted,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            NeuDivider()

            Spacer(modifier = Modifier.height(16.dp))

            // ==================== Section 3: Preview ====================
            SectionHeader(title = "Preview")

            Spacer(modifier = Modifier.height(8.dp))

            WallpaperPreviewBox(
                wallpaperId = selectedWallpaper,
                customPhotoUri = customPhotoUri
            )

            // F13: Auto-detect brightness hint
            if (isDarkDetected) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DarkMode,
                        contentDescription = "Dark mode",
                        tint = colors.muted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dark wallpaper detected",
                        fontSize = 12.sp,
                        color = colors.muted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ==================== Bottom Actions ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Reset to Default button
                NeuButton(
                    onClick = { viewModel.resetWallpaper() },
                    modifier = Modifier.weight(1f),
                    containerColor = colors.surface,
                    contentColor = colors.onSurface
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        "Reset",
                        modifier = Modifier.size(18.dp),
                        tint = colors.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Reset to Default",
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface
                    )
                }

                // Apply button
                NeuButton(
                    onClick = {
                        viewModel.applyWallpaper()
                        onApply()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isApplying && !isDefault && selectedWallpaper != currentWallpaper
                ) {
                    if (isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.onPrimary
                        )
                    } else {
                        Icon(
                            Icons.Default.Check,
                            "Apply",
                            modifier = Modifier.size(18.dp),
                            tint = colors.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Apply",
                        fontWeight = FontWeight.Medium,
                        color = if (!isApplying && !isDefault && selectedWallpaper != currentWallpaper)
                            colors.onPrimary else colors.onPrimary.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// ==================== Helper Composables ====================

@Composable
private fun SectionHeader(title: String) {
    val colors = ZaxoTheme.colors
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.onSurface
    )
}

/**
 * A single grid item representing a built-in wallpaper with a colored preview box.
 */
@Composable
private fun WallpaperGridItem(
    wallpaperId: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = ZaxoTheme.colors
    val previewColor = WallpaperPreviewColors[wallpaperId] ?: colors.surface
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary else Color.Transparent,
        animationSpec = tween(200),
        label = "selection_border"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(previewColor)
                .border(
                    width = if (isSelected) 2.5.dp else 0.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Subtle pattern indicator inside the preview box
            val textColor = if (isDarkWallpaper(wallpaperId)) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)
            Text(
                text = wallpaperLabel(wallpaperId).take(1).uppercase(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            // Selected checkmark overlay
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.TopEnd)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = wallpaperLabel(wallpaperId),
            fontSize = 11.sp,
            color = if (isSelected) colors.primary else colors.muted,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * Full-width preview box showing the currently selected wallpaper.
 * For built-in wallpapers, shows a colored preview. For custom photos, loads the image.
 * Includes a dimming overlay to simulate chat background appearance.
 */
@Composable
private fun WallpaperPreviewBox(
    wallpaperId: String?,
    customPhotoUri: String?
) {
    val colors = ZaxoTheme.colors

    NeuCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
        ) {
            when {
                wallpaperId == null -> {
                    // Default / no wallpaper — show plain background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Wallpaper,
                                contentDescription = "Default wallpaper",
                                tint = colors.muted.copy(alpha = 0.5f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Default",
                                fontSize = 13.sp,
                                color = colors.muted
                            )
                        }
                    }
                }
                customPhotoUri != null -> {
                    // Custom photo wallpaper
                    AsyncImage(
                        model = customPhotoUri,
                        contentDescription = "Custom wallpaper preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    // Built-in wallpaper preview
                    val previewColor = WallpaperPreviewColors[wallpaperId] ?: colors.surface
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(previewColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = wallpaperLabel(wallpaperId),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkWallpaper(wallpaperId))
                                Color.White.copy(alpha = 0.2f)
                            else
                                Color.Black.copy(alpha = 0.12f)
                        )
                    }
                }
            }

            // Dimming overlay to simulate chat background appearance
            if (wallpaperId != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.08f))
                )
            }

            // Preview label
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = if (wallpaperId != null) "Preview" else "No wallpaper",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
