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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zaxo.app.model.Chat
import com.zaxo.app.ui.components.*
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.ChatListViewModel
import java.text.SimpleDateFormat
import java.util.*

// ==================== Forward Picker Screen ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardPickerScreen(
    onBack: () -> Unit,
    onForwardComplete: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val chats by viewModel.filteredChats.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedChatIds = remember { mutableStateSetOf<String>() }
    var showSearch by remember { mutableStateOf(false) }

    // Only show non-archived chats for forwarding
    val availableChats = remember(chats) {
        chats.filter { !it.isArchived }
            .sortedByDescending { it.lastMessageTime }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Forward to...",
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = colors.onSurface)
                    }
                },
                actions = {
                    if (selectedChatIds.isNotEmpty()) {
                        TextButton(onClick = {
                            // F64: Validate chat exists before forwarding
                            val validChatIds = availableChats.filter { it.id in selectedChatIds }.map { it.id }
                            if (validChatIds.isEmpty()) {
                                // Show error — no valid chats selected
                                return@TextButton
                            }
                            onForwardComplete()
                        }) {
                            Text(
                                "Forward (${selectedChatIds.size})",
                                color = colors.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    NeuIconButton(
                        onClick = { showSearch = !showSearch },
                        icon = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            AnimatedVisibility(
                visible = showSearch,
                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
            ) {
                NeuSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = "Search chats..."
                )
            }

            if (availableChats.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Forward,
                            "No chats",
                            modifier = Modifier.size(64.dp),
                            tint = colors.muted
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No chats to forward to", color = colors.muted, fontSize = 16.sp)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(availableChats, key = { it.id }) { chat ->
                        ForwardChatItem(
                            chat = chat,
                            isSelected = chat.id in selectedChatIds,
                            onClick = {
                                if (chat.id in selectedChatIds) {
                                    selectedChatIds.remove(chat.id)
                                } else {
                                    selectedChatIds.add(chat.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardChatItem(
    chat: Chat,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = ZaxoTheme.colors
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    NeuElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
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
                Text(
                    chat.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    chat.lastMessage.take(40),
                    fontSize = 13.sp,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    "Selected",
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
