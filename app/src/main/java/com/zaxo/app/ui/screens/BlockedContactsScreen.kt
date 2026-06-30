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
import com.zaxo.app.model.BlockedCaller
import com.zaxo.app.ui.components.*
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.BlockedContactsViewModel
import java.text.SimpleDateFormat
import java.util.*

// ==================== Blocked Contacts Screen ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedContactsScreen(
    onBack: () -> Unit,
    viewModel: BlockedContactsViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val blockedContacts by viewModel.blockedContacts.collectAsState()

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Blocked Contacts",
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
        if (blockedContacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Block,
                        "No blocked",
                        modifier = Modifier.size(64.dp),
                        tint = colors.muted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No blocked contacts", color = colors.muted, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Blocked contacts will appear here", color = colors.muted, fontSize = 13.sp)
                }
            }
        } else {
            // F67: Check if blocked user is in shared groups — display warning
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // F67: Warning banner for blocked users in shared groups
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            "Warning",
                            tint = colors.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Blocked users may still appear in shared groups",
                            fontSize = 12.sp,
                            color = colors.muted
                        )
                    }
                }

                items(blockedContacts, key = { it.id }) { blocked ->
                    BlockedContactItem(
                        blocked = blocked,
                        onUnblock = { viewModel.unblockContact(blocked.blockedUserId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedContactItem(
    blocked: BlockedCaller,
    onUnblock: () -> Unit
) {
    val colors = ZaxoTheme.colors
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    NeuElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Block icon avatar
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                NeuAvatar(
                    photoUrl = "",
                    name = blocked.blockedUserName,
                    size = 48.dp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    blocked.blockedUserName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colors.onSurface
                )
                if (blocked.blockedUserZaxoNumber.isNotEmpty()) {
                    Text(
                        blocked.blockedUserZaxoNumber,
                        fontSize = 13.sp,
                        color = colors.muted
                    )
                }
                Text(
                    "Blocked ${dateFormat.format(Date(blocked.blockedAt))}",
                    fontSize = 12.sp,
                    color = colors.muted
                )
            }

            // Unblock button
            NeuButton(
                onClick = onUnblock,
                shape = RoundedCornerShape(12.dp),
                containerColor = colors.error,
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    "Unblock",
                    color = colors.onPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
