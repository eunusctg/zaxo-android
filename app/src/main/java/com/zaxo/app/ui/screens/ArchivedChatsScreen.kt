package com.zaxo.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zaxo.app.model.Chat
import com.zaxo.app.ui.components.*
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.ArchivedChatsViewModel
import java.text.SimpleDateFormat
import java.util.*

// ==================== Archived Chats Screen (Rewritten with F65) ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedChatsScreen(
    onBack: () -> Unit,
    onChatClick: (String) -> Unit,
    viewModel: ArchivedChatsViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val archivedChats by viewModel.archivedChats.collectAsState()

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Archived Chats",
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
        if (archivedChats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Archive,
                        "No archived",
                        modifier = Modifier.size(64.dp),
                        tint = colors.muted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No archived chats", color = colors.muted, fontSize = 16.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(archivedChats, key = { it.id }) { chat ->
                    ArchivedChatItem(
                        chat = chat,
                        onClick = { onChatClick(chat.id) },
                        onUnarchive = { viewModel.unarchiveChat(chat.id) },
                        onDelete = { viewModel.deleteChat(chat.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ArchivedChatItem(
    chat: Chat,
    onClick: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = ZaxoTheme.colors
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    var showOptions by remember { mutableStateOf(false) }

    // F65: If chat has new messages while archived, show indicator
    val hasNewMessages = chat.unreadCount > 0

    NeuElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeuAvatar(
                photoUrl = chat.photoUrl,
                name = chat.name,
                size = 48.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        chat.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        timeFormat.format(Date(chat.lastMessageTime)),
                        fontSize = 12.sp,
                        color = colors.muted
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        chat.lastMessage.take(40),
                        fontSize = 13.sp,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // F65: Show unread count badge for archived chats with new messages
                    if (hasNewMessages) {
                        Spacer(modifier = Modifier.width(8.dp))
                        NeuBadge(count = chat.unreadCount)
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box {
                IconButton(onClick = { showOptions = true }) {
                    Icon(Icons.Default.MoreVert, "Options", tint = colors.muted)
                }
                DropdownMenu(
                    expanded = showOptions,
                    onDismissRequest = { showOptions = false }
                ) {
                    // F65: Unarchive option (also auto-triggers on new message)
                    DropdownMenuItem(
                        text = { Text("Unarchive") },
                        onClick = {
                            showOptions = false
                            onUnarchive()
                        },
                        leadingIcon = { Icon(Icons.Default.Unarchive, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = colors.error) },
                        onClick = {
                            showOptions = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = colors.error) }
                    )
                }
            }
        }
    }
}
