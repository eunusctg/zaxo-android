package com.zaxo.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zaxo.app.model.Message
import com.zaxo.app.ui.components.*
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.StarredMessagesViewModel
import java.text.SimpleDateFormat
import java.util.*

// ==================== Starred Messages Screen ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredMessagesScreen(
    onBack: () -> Unit,
    onMessageClick: (String, String) -> Unit = { _, _ -> },
    viewModel: StarredMessagesViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val starredGroups by viewModel.starredGroups.collectAsState()

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Starred Messages",
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
        if (starredGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.StarOutline,
                        "No starred",
                        modifier = Modifier.size(64.dp),
                        tint = colors.muted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No starred messages", color = colors.muted, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Long press a message to star it", color = colors.muted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                starredGroups.forEach { group ->
                    // Chat header
                    item(key = "header-${group.chatId}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NeuAvatar(
                                photoUrl = group.chatPhotoUrl,
                                name = group.chatName,
                                size = 28.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                group.chatName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = colors.primary
                            )
                        }
                    }

                    // Messages in this chat
                    items(group.messages, key = { it.id }) { message ->
                        StarredMessageItem(
                            message = message,
                            onClick = { onMessageClick(message.chatId, message.id) },
                            onUnstar = { viewModel.unstarMessage(message.id) }
                        )
                    }

                    // Divider between groups
                    item(key = "divider-${group.chatId}") {
                        NeuDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StarredMessageItem(
    message: Message,
    onClick: () -> Unit,
    onUnstar: () -> Unit
) {
    val colors = ZaxoTheme.colors
    val timeFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    // F66: Show placeholder for deleted messages
    val isDeleted = message.isDeleted

    NeuElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = if (isDeleted) ({}::invoke) else onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Star icon
            Icon(
                Icons.Default.Star,
                "Starred",
                tint = AccentOrange,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isDeleted) "Message deleted" else message.content,
                    fontSize = 14.sp,
                    color = if (isDeleted) colors.muted else colors.onSurface,
                    fontStyle = if (isDeleted) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        message.senderName,
                        fontSize = 12.sp,
                        color = colors.muted,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        timeFormat.format(Date(message.timestamp)),
                        fontSize = 11.sp,
                        color = colors.muted
                    )
                }
            }

            // Unstar button
            IconButton(
                onClick = onUnstar,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    "Unstar",
                    tint = colors.muted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Need to import AccentOrange
private val AccentOrange = androidx.compose.ui.graphics.Color(0xFFF39C12)
