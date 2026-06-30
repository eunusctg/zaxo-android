package com.zaxo.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.zaxo.app.model.Chat
import com.zaxo.app.ui.components.*
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.ChatRoomViewModel
import com.zaxo.app.viewmodel.ForwardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardScreen(
    onBack: () -> Unit,
    onForwardComplete: () -> Unit,
    chatRoomViewModel: ChatRoomViewModel? = null,
    viewModel: ForwardViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val chats by viewModel.filteredChats.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedChatIds by viewModel.selectedChatIds.collectAsState()
    val messageId = viewModel.getMessageId()
    val sourceChatId = viewModel.getSourceChatId()

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Forward to...",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            color = colors.onSurface
                        )
                        if (selectedChatIds.isNotEmpty()) {
                            Text(
                                "${selectedChatIds.size} selected",
                                fontSize = 13.sp,
                                color = colors.primary
                            )
                        }
                    }
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
                            selectedChatIds.forEach { targetChatId ->
                                chatRoomViewModel?.forwardMessage(
                                    messageId = messageId,
                                    targetChatId = targetChatId,
                                    senderId = "",
                                    senderName = "",
                                    senderPhotoUrl = ""
                                )
                            }
                            onForwardComplete()
                        }) {
                            Text("Send", color = colors.primary, fontWeight = FontWeight.Bold)
                        }
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
            // Search bar
            NeuSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = "Search chats..."
            )

            // Chat list for selection
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(chats, key = { it.id }) { chat ->
                    ForwardChatItem(
                        chat = chat,
                        isSelected = chat.id in selectedChatIds,
                        onClick = { viewModel.toggleChatSelection(chat.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ForwardChatItem(
    chat: Chat,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = ZaxoTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = colors.onSurface
            )
            Text(
                chat.lastMessage.take(40),
                fontSize = 13.sp,
                color = colors.muted,
                maxLines = 1
            )
        }

        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                "Selected",
                tint = colors.primary,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Icon(
                Icons.Default.RadioButtonUnchecked,
                "Not selected",
                tint = colors.muted,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
