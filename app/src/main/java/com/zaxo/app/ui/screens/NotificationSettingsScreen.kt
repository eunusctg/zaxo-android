package com.zaxo.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zaxo.app.model.ChatNotificationSettings
import com.zaxo.app.ui.components.*
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.NotificationSettingsViewModel

// ==================== Notification Settings Screen ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val chatSettings by viewModel.chatSettings.collectAsState()

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Notification Settings",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                Text(
                    "Per-Chat Notifications",
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (chatSettings.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Notifications,
                                "No chats",
                                modifier = Modifier.size(48.dp),
                                tint = colors.muted
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No chats to configure", color = colors.muted, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(chatSettings, key = { it.chatId }) { settings ->
                    NotificationSettingItem(
                        settings = settings,
                        onMuteChange = { muted ->
                            viewModel.updateMuteSetting(settings.chatId, muted)
                        },
                        onSoundChange = { enabled ->
                            viewModel.updateSoundSetting(settings.chatId, enabled)
                        },
                        onVibrationChange = { enabled ->
                            viewModel.updateVibrationSetting(settings.chatId, enabled)
                        }
                    )
                }
            }

            // Reset All button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    NeuButton(
                        onClick = { viewModel.resetAllSettings() },
                        shape = RoundedCornerShape(12.dp),
                        containerColor = colors.error
                    ) {
                        Icon(
                            Icons.Default.RestartAlt,
                            "Reset",
                            tint = colors.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Reset All",
                            color = colors.onPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun NotificationSettingItem(
    settings: ChatNotificationSettings,
    onMuteChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit
) {
    val colors = ZaxoTheme.colors

    NeuCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Chat name header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Chat,
                        "Chat",
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        settings.chatId.take(20), // In production, show chat name
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = colors.onSurface
                    )
                }
                // Mute toggle
                NeuToggle(
                    checked = settings.isMuted,
                    onCheckedChange = onMuteChange
                )
            }

            if (!settings.isMuted) {
                Spacer(modifier = Modifier.height(12.dp))

                // Sound toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.VolumeUp,
                            "Sound",
                            tint = colors.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sound", fontSize = 14.sp, color = colors.onSurface)
                    }
                    NeuToggle(
                        checked = settings.soundEnabled,
                        onCheckedChange = onSoundChange
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Vibration toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Vibration,
                            "Vibration",
                            tint = colors.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vibration", fontSize = 14.sp, color = colors.onSurface)
                    }
                    NeuToggle(
                        checked = settings.vibrationEnabled,
                        onCheckedChange = onVibrationChange
                    )
                }
            }
        }
    }
}
