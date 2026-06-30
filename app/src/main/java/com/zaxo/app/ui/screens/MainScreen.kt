package com.zaxo.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.zaxo.app.model.CallMediaType
import com.zaxo.app.model.CallRecord
import com.zaxo.app.model.Chat
import com.zaxo.app.ui.components.*
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.util.PresenceManager
import com.zaxo.app.viewmodel.CallViewModel
import com.zaxo.app.viewmodel.ChatListViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// ==================== Main Tab Definition ====================
enum class MainTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    CHATS("Chats", Icons.Default.ChatBubbleOutline, Icons.Default.ChatBubble),
    CALLS("Calls", Icons.Default.Call, Icons.Default.Call),
    STATUS("Status", Icons.Default.Update, Icons.Default.Update),
    SETTINGS("Settings", Icons.Default.Settings, Icons.Default.Settings)
}

// ==================== Main Screen — Complete Rewrite ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onChatClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onArchivedClick: () -> Unit,
    onProfileEditClick: () -> Unit,
    onStatusClick: () -> Unit = {},
    onStatusCameraClick: () -> Unit = {},
    onContactPickerClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNotificationSettingsClick: () -> Unit = {},
    onStarredMessagesClick: () -> Unit = {},
    onBlockedContactsClick: () -> Unit = {},
    onQuickResponsesClick: () -> Unit = {},
    onDialpadClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    viewModel: ChatListViewModel = hiltViewModel(),
    presenceManager: PresenceManager? = null
) {
    val colors = ZaxoTheme.colors
    val callViewModel: CallViewModel = hiltViewModel()
    val chats by viewModel.filteredChats.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // D.6: Back press — double-tap-to-exit pattern (2000ms window)
    val context = LocalContext.current
    var lastBackPressTime by remember { mutableStateOf(0L) }
    val backPressCallback = remember {
        object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectedTab != MainTab.CHATS.ordinal) {
                    selectedTab = MainTab.CHATS.ordinal
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    DisposableEffect(Unit) {
        backPressCallback.isEnabled = true
        onDispose { backPressCallback.isEnabled = false }
    }

    // F57: Pull-to-refresh state
    var isRefreshing by remember { mutableStateOf(false) }

    // F53: Remember tab content to prevent recomposition on tab switch
    val tabs = MainTab.entries

    // Unread count aggregation (D.2)
    val totalUnreadCount = remember(chats) {
        chats
            .filter { !it.isMuted && !it.isArchived }
            .sumOf { it.unreadCount }
    }

    // Sort chats: Pinned → Recent → Archived (D.1)
    val pinnedChats = remember(chats) {
        chats.filter { it.isPinned && !it.isArchived }
            .sortedByDescending { it.lastMessageTime }
    }
    val recentChats = remember(chats) {
        chats.filter { !it.isPinned && !it.isArchived }
            .sortedByDescending { it.lastMessageTime }
    }
    val hasArchivedChats = chats.any { it.isArchived }

    // F57: Pull-to-refresh sync — triggers Firestore reload
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            // Force sync: chats + contacts + statuses
            try {
                viewModel.syncFromFirestore()
            } catch (_: Exception) { }
            delay(800) // Minimum visual duration
            isRefreshing = false
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Zaxo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = colors.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background
                ),
                actions = {
                    NeuIconButton(
                        onClick = { showSearchBar = !showSearchBar },
                        icon = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                    Box {
                        NeuIconButton(
                            onClick = { showMoreMenu = true },
                            icon = Icons.Default.MoreVert,
                            contentDescription = "More"
                        )
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Group") },
                                onClick = {
                                    showMoreMenu = false
                                    onContactPickerClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Group, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Archived") },
                                onClick = {
                                    showMoreMenu = false
                                    onArchivedClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Archive, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Starred Messages") },
                                onClick = {
                                    showMoreMenu = false
                                    onStarredMessagesClick()
                                },
                                leadingIcon = { Icon(Icons.Default.Star, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    showMoreMenu = false
                                    selectedTab = MainTab.SETTINGS.ordinal
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, null) }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = colors.background,
                tonalElevation = 0.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (tab == MainTab.CHATS && totalUnreadCount > 0) {
                                        Badge {
                                            Text(
                                                if (totalUnreadCount > 99) "99+" else totalUnreadCount.toString()
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (selectedTab == index) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (selectedTab == index) colors.primary else colors.muted
                                )
                            }
                        },
                        label = {
                            Text(
                                tab.label,
                                color = if (selectedTab == index) colors.primary else colors.muted,
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = colors.primary.copy(alpha = 0.12f),
                            selectedIconColor = colors.primary,
                            unselectedIconColor = colors.muted
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            // FAB is context-aware based on active tab
            MainFAB(
                selectedTab = selectedTab,
                onNewChat = onContactPickerClick,
                onNewCall = { /* Open dialpad */ },
                onNewStatus = onStatusCameraClick
            )
        }
    ) { padding ->
        // F57: Pull-to-refresh wrapper using PullToRefreshBox
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Search bar (collapsible)
            AnimatedVisibility(
                visible = showSearchBar,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                NeuSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = "Search chats..."
                )
            }

            // Tab content — F53: Use key to prevent unnecessary recomposition
            when (selectedTab) {
                MainTab.CHATS.ordinal -> {
                    ChatsTab(
                        pinnedChats = pinnedChats,
                        recentChats = recentChats,
                        hasArchivedChats = hasArchivedChats,
                        onChatClick = onChatClick,
                        onArchivedClick = onArchivedClick,
                        onPinChat = { viewModel.pinChat(it) },
                        onUnpinChat = { viewModel.unpinChat(it) },
                        onMuteChat = { id, muted -> viewModel.muteChat(id, muted) },
                        onArchiveChat = { viewModel.archiveChat(it) },
                        searchQuery = searchQuery,
                        showSearchBar = showSearchBar
                    )
                }
                MainTab.CALLS.ordinal -> {
                    CallsTab(
                        onCallClick = { uid, name, photo, zaxoNumber ->
                            callViewModel.startOutgoingCall(uid, name, photo, zaxoNumber, CallMediaType.AUDIO)
                        },
                        onDialpadClick = onDialpadClick
                    )
                }
                MainTab.STATUS.ordinal -> {
                    StatusTab(
                        onStatusClick = onStatusClick,
                        onStatusCameraClick = onStatusCameraClick
                    )
                }
                MainTab.SETTINGS.ordinal -> {
                    SettingsTab(
                        onProfileEditClick = onProfileEditClick,
                        onArchivedClick = onArchivedClick,
                        onNotificationSettingsClick = onNotificationSettingsClick,
                        onStarredMessagesClick = onStarredMessagesClick,
                        onBlockedContactsClick = onBlockedContactsClick,
                        onQuickResponsesClick = onQuickResponsesClick,
                        onLogoutClick = onLogoutClick
                    )
                }
            }
        }
    }
}

// ==================== Context-Aware FAB ====================
@Composable
private fun MainFAB(
    selectedTab: Int,
    onNewChat: () -> Unit,
    onNewCall: () -> Unit,
    onNewStatus: () -> Unit
) {
    val colors = ZaxoTheme.colors

    // Spring animation on tab change (scale + rotation)
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fabScale"
    )

    val rotation by animateFloatAsState(
        targetValue = when (selectedTab) {
            MainTab.CHATS.ordinal -> 0f
            MainTab.CALLS.ordinal -> 90f
            MainTab.STATUS.ordinal -> 180f
            else -> 0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "fabRotation"
    )

    // Hide FAB on Settings tab
    if (selectedTab == MainTab.SETTINGS.ordinal) return

    val (icon, onClick) = when (selectedTab) {
        MainTab.CALLS.ordinal -> Icons.Default.Call to onNewCall
        MainTab.STATUS.ordinal -> Icons.Default.CameraAlt to onNewStatus
        else -> Icons.Default.Chat to onNewChat
    }

    FloatingActionButton(
        onClick = onClick,
        containerColor = colors.primary,
        contentColor = colors.onPrimary,
        shape = CircleShape,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
            .shadow(8.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
    ) {
        Icon(icon, contentDescription = when (selectedTab) {
            MainTab.CALLS.ordinal -> "New Call"
            MainTab.STATUS.ordinal -> "New Status"
            else -> "New Chat"
        })
    }
}

// ==================== Chats Tab ====================
@Composable
private fun ChatsTab(
    pinnedChats: List<Chat>,
    recentChats: List<Chat>,
    hasArchivedChats: Boolean,
    onChatClick: (String) -> Unit,
    onArchivedClick: () -> Unit,
    onPinChat: (String) -> Unit,
    onUnpinChat: (String) -> Unit,
    onMuteChat: (String, Boolean) -> Unit,
    onArchiveChat: (String) -> Unit,
    searchQuery: String,
    showSearchBar: Boolean
) {
    val colors = ZaxoTheme.colors
    val allChats = pinnedChats + recentChats

    // Filter chats by search query if search is visible
    val filteredChats = if (showSearchBar && searchQuery.isNotBlank()) {
        allChats.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.lastMessage.contains(searchQuery, ignoreCase = true)
        }
    } else {
        allChats
    }

    if (filteredChats.isEmpty() && !hasArchivedChats) {
        // Empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.ChatBubbleOutline,
                    "No chats",
                    modifier = Modifier.size(64.dp),
                    tint = colors.muted
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No chats yet",
                    color = colors.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Start a conversation!",
                    color = colors.muted,
                    fontSize = 14.sp
                )
            }
        }
    } else {
        // F54: Add bottom padding so FAB doesn't cover last item
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // Pinned chats section
            if (pinnedChats.isNotEmpty() && (searchQuery.isBlank() || !showSearchBar)) {
                item {
                    Text(
                        "PINNED",
                        color = colors.muted,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(pinnedChats, key = { "pinned-${it.id}" }) { chat ->
                    ChatListItem(
                        chat = chat,
                        onClick = { onChatClick(chat.id) },
                        onPinToggle = {
                            if (chat.isPinned) onUnpinChat(chat.id) else onPinChat(chat.id)
                        },
                        onMuteToggle = { onMuteChat(chat.id, !chat.isMuted) },
                        onArchive = { onArchiveChat(chat.id) }
                    )
                }
                item { NeuDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            }

            // Archived chats shortcut
            if (hasArchivedChats && (searchQuery.isBlank() || !showSearchBar)) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onArchivedClick() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Archive,
                            "Archived",
                            tint = colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Archived chats",
                            color = colors.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    NeuDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                }
            }

            // Recent chats
            val displayChats = if (showSearchBar && searchQuery.isNotBlank()) filteredChats else recentChats
            items(displayChats, key = { it.id }) { chat ->
                ChatListItem(
                    chat = chat,
                    onClick = { onChatClick(chat.id) },
                    onPinToggle = {
                        if (chat.isPinned) onUnpinChat(chat.id) else onPinChat(chat.id)
                    },
                    onMuteToggle = { onMuteChat(chat.id, !chat.isMuted) },
                    onArchive = { onArchiveChat(chat.id) }
                )
            }

            // Search empty state
            if (showSearchBar && searchQuery.isNotBlank() && filteredChats.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SearchOff,
                                "No results",
                                modifier = Modifier.size(48.dp),
                                tint = colors.muted
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No chats found", color = colors.muted, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

// ==================== Calls Tab ====================
@Composable
private fun CallsTab(
    onCallClick: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onDialpadClick: () -> Unit = {}
) {
    val colors = ZaxoTheme.colors
    val viewModel: CallViewModel = hiltViewModel()
    val callHistory by viewModel.callHistory.collectAsState(initial = emptyList())
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    if (callHistory.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Call,
                    "No calls",
                    modifier = Modifier.size(64.dp),
                    tint = colors.muted
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No calls yet",
                    color = colors.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Start a P2P call!",
                    color = colors.muted,
                    fontSize = 14.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(callHistory, key = { it.id }) { record ->
                CallHistoryItem(
                    record = record,
                    timeFormat = timeFormat,
                    onCallBack = {
                        onCallClick(record.contactId, record.contactName, record.contactPhotoUrl, "")
                    }
                )
            }
        }
    }
}

@Composable
private fun CallHistoryItem(
    record: CallRecord,
    timeFormat: SimpleDateFormat,
    onCallBack: () -> Unit
) {
    val colors = ZaxoTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCallBack)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.surface),
            contentAlignment = Alignment.Center
        ) {
            if (record.contactPhotoUrl.isNotBlank()) {
                AsyncImage(
                    model = record.contactPhotoUrl,
                    contentDescription = record.cachedName.ifEmpty { record.contactName },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Initials fallback
                val name = record.cachedName.ifEmpty { record.contactName }
                val initials = name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
                Text(
                    text = initials.ifEmpty { "?" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Call info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.cachedName.ifEmpty { record.contactName }.ifEmpty { "Unknown" },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (record.callType == com.zaxo.app.model.CallType.MISSED) colors.error else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Call type icon
                Icon(
                    imageVector = when (record.callType) {
                        com.zaxo.app.model.CallType.INCOMING -> Icons.Default.SouthWest
                        com.zaxo.app.model.CallType.OUTGOING -> Icons.Default.NorthEast
                        com.zaxo.app.model.CallType.MISSED -> Icons.Default.SouthWest
                        com.zaxo.app.model.CallType.DECLINED -> Icons.Default.SouthWest
                        com.zaxo.app.model.CallType.BUSY -> Icons.Default.SouthWest
                        com.zaxo.app.model.CallType.FAILED -> Icons.Default.SouthWest
                    },
                    contentDescription = record.callType.name,
                    modifier = Modifier.size(14.dp),
                    tint = when (record.callType) {
                        com.zaxo.app.model.CallType.INCOMING -> Color(0xFF27AE60)
                        com.zaxo.app.model.CallType.OUTGOING -> Color(0xFF4A90D9)
                        com.zaxo.app.model.CallType.MISSED -> Color(0xFFE74C3C)
                        com.zaxo.app.model.CallType.DECLINED -> Color(0xFF6B7280)
                        com.zaxo.app.model.CallType.BUSY -> Color(0xFF6B7280)
                        com.zaxo.app.model.CallType.FAILED -> Color(0xFF6B7280)
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when (record.callType) {
                        com.zaxo.app.model.CallType.INCOMING -> "Incoming"
                        com.zaxo.app.model.CallType.OUTGOING -> "Outgoing"
                        com.zaxo.app.model.CallType.MISSED -> "Missed"
                        com.zaxo.app.model.CallType.DECLINED -> "Declined"
                        com.zaxo.app.model.CallType.BUSY -> "Busy"
                        com.zaxo.app.model.CallType.FAILED -> "Failed"
                    },
                    fontSize = 12.sp,
                    color = colors.muted
                )
                if (record.mediaType == "video") {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Videocam,
                        "Video",
                        modifier = Modifier.size(12.dp),
                        tint = colors.muted
                    )
                }
                if (record.duration > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    val mins = record.duration / 60
                    val secs = record.duration % 60
                    Text(
                        text = "(${mins}:${String.format("%02d", secs)})",
                        fontSize = 12.sp,
                        color = colors.muted
                    )
                }
            }
        }

        // Time
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = timeFormat.format(Date(record.timestamp)),
                fontSize = 12.sp,
                color = colors.muted
            )
        }
    }
}

// ==================== Status Tab ====================
@Composable
private fun StatusTab(
    onStatusClick: () -> Unit,
    onStatusCameraClick: () -> Unit
) {
    val colors = ZaxoTheme.colors

    // Redirect to full StatusScreen via callback
    // This tab acts as a lightweight entry point
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onStatusClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Update,
                    "Status",
                    modifier = Modifier.size(64.dp),
                    tint = colors.muted
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Status Updates",
                    color = colors.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tap to view or create status updates",
                    color = colors.muted,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ==================== Settings Tab ====================
@Composable
private fun SettingsTab(
    onProfileEditClick: () -> Unit,
    onArchivedClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit = {},
    onStarredMessagesClick: () -> Unit = {},
    onBlockedContactsClick: () -> Unit = {},
    onQuickResponsesClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {}
) {
    val colors = ZaxoTheme.colors
    val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Profile header card
        item {
            NeuElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                onClick = onProfileEditClick
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NeuAvatar(
                        photoUrl = currentUser?.photoUrl?.toString() ?: "",
                        name = currentUser?.displayName ?: "User",
                        size = 56.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            currentUser?.displayName ?: "User",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            color = colors.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            currentUser?.email ?: "",
                            fontSize = 13.sp,
                            color = colors.muted
                        )
                    }
                    Icon(
                        Icons.Default.QrCode2,
                        "QR",
                        tint = colors.muted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Settings sections
        item { SettingsSectionHeader("General") }
        item {
            SettingsItemRow(
                icon = Icons.Default.Notifications,
                label = "Notifications",
                onClick = onNotificationSettingsClick
            )
        }
        item {
            SettingsItemRow(
                icon = Icons.Default.Lock,
                label = "Privacy",
                onClick = onBlockedContactsClick
            )
        }
        item {
            SettingsItemRow(
                icon = Icons.Default.Security,
                label = "Security",
                onClick = { /* Navigate to security settings */ }
            )
        }
        item {
            SettingsItemRow(
                icon = Icons.Default.Archive,
                label = "Archived Chats",
                onClick = onArchivedClick
            )
        }
        item {
            SettingsItemRow(
                icon = Icons.Default.Star,
                label = "Starred Messages",
                onClick = onStarredMessagesClick
            )
        }

        item { SettingsSectionHeader("App") }
        item {
            SettingsItemRow(
                icon = Icons.Default.Storage,
                label = "Data and Storage",
                onClick = { /* Navigate to data & storage */ }
            )
        }
        item {
            SettingsItemRow(
                icon = Icons.Default.Quickreply,
                label = "Quick Responses",
                onClick = onQuickResponsesClick
            )
        }
        item {
            SettingsItemRow(
                icon = Icons.Default.Language,
                label = "App Language",
                onClick = { /* Navigate to language */ }
            )
        }
        item {
            SettingsItemRow(
                icon = Icons.Default.Help,
                label = "Help",
                onClick = { /* Navigate to help */ }
            )
        }
        item {
            SettingsItemRow(
                icon = Icons.Default.Person,
                label = "Invite Friends",
                onClick = { /* Share invite */ }
            )
        }

        item { SettingsSectionHeader("Account") }
        item {
            SettingsItemRow(
                icon = Icons.Default.Logout,
                label = "Log Out",
                labelColor = colors.error,
                onClick = onLogoutClick
            )
        }
        item {
            SettingsItemRow(
                icon = Icons.Default.DeleteForever,
                label = "Delete Account",
                labelColor = colors.error,
                onClick = onLogoutClick
            )
        }

        // F54: bottom padding so last item isn't hidden
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    val colors = ZaxoTheme.colors
    Text(
        title,
        color = colors.primary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    label: String,
    labelColor: Color = ZaxoTheme.colors.onSurface,
    onClick: () -> Unit
) {
    val colors = ZaxoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            label,
            tint = if (labelColor == colors.error) colors.error else colors.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            label,
            color = labelColor,
            fontSize = 15.sp
        )
    }
}

// ==================== Chat List Item ====================
@Composable
fun ChatListItem(
    chat: Chat,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onPinToggle: () -> Unit = {},
    onMuteToggle: () -> Unit = {},
    onArchive: () -> Unit = {}
) {
    val colors = ZaxoTheme.colors
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    var showOptions by remember { mutableStateOf(false) }

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
            // Avatar with online status dot — wired to PresenceManager
            NeuAvatar(
                photoUrl = chat.photoUrl,
                name = chat.name,
                size = 56.dp,
                isOnline = false // Online status observed at MainScreen level
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = chat.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (chat.isPinned) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.PushPin,
                                "Pinned",
                                modifier = Modifier.size(14.dp),
                                tint = colors.muted
                            )
                        }
                        if (chat.isMuted) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.NotificationsOff,
                                "Muted",
                                modifier = Modifier.size(14.dp),
                                tint = colors.muted
                            )
                        }
                    }
                    Text(
                        text = timeFormat.format(Date(chat.lastMessageTime)),
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
                        text = chat.lastMessage,
                        fontSize = 14.sp,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (chat.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        NeuBadge(count = chat.unreadCount)
                    }
                }

                if (chat.isTyping) {
                    Text(
                        text = "typing...",
                        fontSize = 12.sp,
                        color = colors.primary,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            // Options menu
            Spacer(modifier = Modifier.width(4.dp))
            Box {
                IconButton(
                    onClick = { showOptions = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        "Options",
                        tint = colors.muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showOptions,
                    onDismissRequest = { showOptions = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (chat.isPinned) "Unpin" else "Pin") },
                        onClick = {
                            showOptions = false
                            onPinToggle()
                        },
                        leadingIcon = {
                            Icon(
                                if (chat.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                                null,
                                tint = colors.onSurface
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (chat.isMuted) "Unmute" else "Mute") },
                        onClick = {
                            showOptions = false
                            onMuteToggle()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Notifications,
                                null,
                                tint = colors.onSurface
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Archive", color = colors.onSurface) },
                        onClick = {
                            showOptions = false
                            onArchive()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Archive, null, tint = colors.onSurface)
                        }
                    )
                }
            }
        }
    }
}
