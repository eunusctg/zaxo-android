package com.zaxo.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zaxo.app.model.ChatSearchGroup
import com.zaxo.app.model.Message
import com.zaxo.app.ui.components.*
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.SearchViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onChatClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val recentChats by viewModel.recentChats.collectAsState()

    val hasQuery = query.trim().length >= 2

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    NeuSearchBar(
                        query = query,
                        onQueryChange = { viewModel.updateSearchQuery(it) },
                        placeholder = "Search messages...",
                        modifier = Modifier.fillMaxWidth()
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
            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.primary)
                }
            }

            if (!hasQuery) {
                // Show recent chats as suggestions
                if (recentChats.isNotEmpty()) {
                    Text(
                        "Recent Chats",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = colors.muted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyColumn {
                        items(recentChats) { chat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onChatClick(chat.id) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NeuAvatar(
                                    photoUrl = chat.photoUrl,
                                    name = chat.name,
                                    size = 40.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    chat.name,
                                    fontSize = 15.sp,
                                    color = colors.onSurface
                                )
                            }
                        }
                    }
                }
            } else if (results.isEmpty() && !isSearching) {
                // No results
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff,
                            "No results",
                            modifier = Modifier.size(48.dp),
                            tint = colors.muted
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No messages found", color = colors.muted, fontSize = 16.sp)
                        Text(
                            "Try different keywords",
                            color = colors.muted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                // Search results grouped by chat
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    results.forEach { group ->
                        // Chat header
                        item(key = "header_${group.chatId}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onChatClick(group.chatId) }
                                    .background(colors.surface.copy(alpha = 0.5f))
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NeuAvatar(
                                    photoUrl = group.chatPhotoUrl,
                                    name = group.chatName,
                                    size = 36.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    group.chatName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = colors.primary
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "${group.messages.size} result${if (group.messages.size != 1) "s" else ""}",
                                    fontSize = 12.sp,
                                    color = colors.muted
                                )
                            }
                        }

                        // Messages in this chat
                        items(group.messages, key = { it.id }) { message ->
                            SearchResultItem(
                                message = message,
                                searchQuery = query.trim(),
                                onClick = { onChatClick(group.chatId) }
                            )
                        }

                        // Spacing between groups
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            NeuDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    message: Message,
    searchQuery: String,
    onClick: () -> Unit
) {
    val colors = ZaxoTheme.colors
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Sender and time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    message.senderName.ifEmpty { "Unknown" },
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = colors.onSurface
                )
                Text(
                    timeFormat.format(Date(message.timestamp)),
                    fontSize = 11.sp,
                    color = colors.muted
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Message preview with highlighted query
            val content = message.content
            val highlightStart = content.indexOf(searchQuery, ignoreCase = true)

            if (highlightStart >= 0) {
                val before = content.substring(0, highlightStart)
                val match = content.substring(
                    highlightStart,
                    minOf(highlightStart + searchQuery.length, content.length)
                )
                val after = content.substring(
                    minOf(highlightStart + searchQuery.length, content.length)
                )

                Row {
                    if (before.isNotEmpty()) {
                        Text(
                            before.takeLast(30),
                            fontSize = 14.sp,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        match,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                    if (after.isNotEmpty()) {
                        Text(
                            after.take(50),
                            fontSize = 14.sp,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Text(
                    content.take(80),
                    fontSize = 14.sp,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
