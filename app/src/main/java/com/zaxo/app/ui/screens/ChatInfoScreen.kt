package com.zaxo.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zaxo.app.ui.components.*
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.ChatInfoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(
    chatId: String,
    onBack: () -> Unit,
    onGroupAdmin: (String) -> Unit = {},
    onMediaClick: (String, String) -> Unit = { _, _ -> },
    onWallpaperClick: (String) -> Unit = {},
    viewModel: ChatInfoViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val chat by viewModel.chat.collectAsState()
    val mediaMessages by viewModel.mediaMessages.collectAsState()
    val starredMessages by viewModel.starredMessages.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Media", "Starred", "Details")

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        chat?.name ?: "Chat Info",
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
        ) {
            // Profile section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NeuAvatar(
                    photoUrl = chat?.photoUrl ?: "",
                    name = chat?.name ?: "",
                    size = 80.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    chat?.name ?: "",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.onSurface
                )
                if (chat?.isGroup == true && chat?.groupDescription?.isNotEmpty() == true) {
                    Text(
                        chat?.groupDescription ?: "",
                        fontSize = 13.sp,
                        color = colors.muted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NeuIconButton(
                    onClick = { /* audio call */ },
                    icon = Icons.Default.Call,
                    contentDescription = "Call"
                )
                NeuIconButton(
                    onClick = { /* video call */ },
                    icon = Icons.Default.Videocam,
                    contentDescription = "Video"
                )
                NeuIconButton(
                    onClick = { viewModel.toggleMute(chat?.isMuted != true) },
                    icon = if (chat?.isMuted == true) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                    contentDescription = "Mute"
                )
                NeuIconButton(
                    onClick = { viewModel.togglePin(chat?.isPinned != true) },
                    icon = if (chat?.isPinned == true) Icons.Default.PushPin else Icons.Default.PushPin,
                    contentDescription = "Pin"
                )
                NeuIconButton(
                    onClick = { onWallpaperClick(chatId) },
                    icon = Icons.Default.Wallpaper,
                    contentDescription = "Wallpaper"
                )
            }

            // Group admin button (only for groups)
            if (chat?.isGroup == true) {
                Spacer(modifier = Modifier.height(12.dp))
                NeuElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    onClick = { onGroupAdmin(chatId) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            "Group Admin",
                            tint = colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Group Settings & Members",
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tab row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = colors.background,
                contentColor = colors.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // Tab content
            when (selectedTab) {
                0 -> {
                    // Media tab
                    if (mediaMessages.isEmpty()) {
                        EmptyTabContent("No media yet")
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            items(mediaMessages) { message ->
                                Box(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .size(100.dp)
                                        .clickable {
                                            onMediaClick(chatId, message.id)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (message.type.name == "VIDEO") {
                                        Icon(
                                            Icons.Default.PlayCircleFilled,
                                            "Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    } else {
                                        Text("📷", fontSize = 24.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Starred tab
                    if (starredMessages.isEmpty()) {
                        EmptyTabContent("No starred messages")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(starredMessages) { message ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Star,
                                        "Starred",
                                        tint = Color(0xFFF39C12),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        message.content.take(60),
                                        fontSize = 14.sp,
                                        color = colors.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Details tab
                    Column(modifier = Modifier.padding(16.dp)) {
                        DetailRow("Muted", if (chat?.isMuted == true) "Yes" else "No")
                        DetailRow("Pinned", if (chat?.isPinned == true) "Yes" else "No")
                        DetailRow("Encrypted", if (chat?.encryptionSessionId?.isNotEmpty() == true) "Yes" else "No")
                        if (chat?.isGroup == true) {
                            val memberCount = chat?.memberIds?.split(",")?.filter { it.isNotEmpty() }?.size ?: 0
                            val adminCount = chat?.adminIds?.split(",")?.filter { it.isNotEmpty() }?.size ?: 0
                            DetailRow("Members", "$memberCount")
                            DetailRow("Admins", "$adminCount")
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Danger zone
                        NeuButton(
                            onClick = { viewModel.archiveChat() },
                            containerColor = colors.error,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Archive, "Archive", modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Archive Chat", fontWeight = FontWeight.Medium, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyTabContent(message: String) {
    val colors = ZaxoTheme.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = colors.muted, fontSize = 15.sp)
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    val colors = ZaxoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = colors.muted)
        Text(value, fontSize = 14.sp, color = colors.onSurface, fontWeight = FontWeight.Medium)
    }
}
