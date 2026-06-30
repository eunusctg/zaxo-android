package com.zaxo.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaxo.app.model.MutedStatus
import com.zaxo.app.model.Status
import com.zaxo.app.model.StatusType
import com.zaxo.app.model.User
import com.zaxo.app.ui.components.*
import com.zaxo.app.ui.theme.ZaxoTheme
import java.text.SimpleDateFormat
import java.util.*

// ==================== Status Filter ====================
enum class StatusFilter(val label: String) {
    ALL("All"),
    MY_CONTACTS("My Contacts"),
    MUTED("Muted")
}

// ==================== StatusScreen ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    onMyStatusClick: () -> Unit,
    onStatusClick: (String) -> Unit,
    onAddTextStatus: () -> Unit,
    onAddMediaStatus: () -> Unit,
    viewModel: com.zaxo.app.viewmodel.StatusViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    var selectedFilter by remember { mutableStateOf(StatusFilter.ALL) }

    // Observe ViewModel state
    val myStatuses by viewModel.myStatuses.collectAsState()
    val contactStatuses by viewModel.contactStatuses.collectAsState()
    val currentUser = com.zaxo.app.model.User(
        uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "",
        displayName = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: "",
        photoUrl = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString() ?: ""
    )

    // --- F18: Filter out expired statuses in real-time ---
    val now = System.currentTimeMillis()
    val activeMyStatuses = remember(myStatuses, now) {
        myStatuses.filter { it.expiresAt > now }
    }
    val activeContactStatuses = remember(contactStatuses, now) {
        contactStatuses.filter { it.expiresAt > now }
    }

    // Observe muted contacts from ViewModel
    val mutedContactIds by viewModel.mutedContactIds.collectAsState()
    var showMuteMenuFor by remember { mutableStateOf<String?>(null) } // userId of contact to show mute menu
    var isMutedSectionExpanded by remember { mutableStateOf(false) }

    // Group contact statuses by userId, keeping only the latest per contact
    val groupedStatuses = remember(contactStatuses, selectedFilter, mutedContactIds) {
        contactStatuses
            .filter { item ->
                when (selectedFilter) {
                    StatusFilter.ALL -> item.userId !in mutedContactIds
                    StatusFilter.MY_CONTACTS -> item.userId !in mutedContactIds
                    StatusFilter.MUTED -> item.userId in mutedContactIds
                }
            }
            .associate { it.userId to it }
    }

    // Muted contact statuses
    val mutedStatuses = remember(contactStatuses, mutedContactIds) {
        contactStatuses.filter { it.userId in mutedContactIds }
    }

    // Periodically refresh to handle expiry mid-view
    var refreshTick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L) // refresh every 30 s
            refreshTick = System.currentTimeMillis()
        }
    }

    Scaffold(
        containerColor = colors.background,
        floatingActionButton = {
            // "Add text status" FAB at bottom right
            NeuButton(
                onClick = onAddTextStatus,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Create,
                    contentDescription = "Add text status",
                    tint = colors.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add text status",
                    color = colors.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ---- Section: My Status ----
            MyStatusSection(
                currentUser = currentUser,
                hasActiveStatus = activeMyStatuses.isNotEmpty(),
                latestMyStatus = activeMyStatuses.firstOrNull(),
                onMyStatusClick = onMyStatusClick,
                onAddMediaStatus = onAddMediaStatus
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ---- Filter Chips ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusFilter.entries.forEach { filter ->
                    NeuChip(
                        label = filter.label,
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ---- Section Header: Recent Updates ----
            if (groupedStatuses.isNotEmpty()) {
                Text(
                    text = "Recent Updates",
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // ---- Status List ----
            if (groupedStatuses.isEmpty() && mutedStatuses.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No recent updates",
                            color = colors.muted,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Status updates from your contacts will appear here",
                            color = colors.muted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(
                        items = groupedStatuses.entries.toList(),
                        key = { it.key }
                    ) { (userId, item) ->
                        val latestStatus = item.statuses.firstOrNull()
                        if (latestStatus != null) {
                            Box {
                                StatusContactRow(
                                    status = latestStatus,
                                    hasUnviewedStatus = item.hasUnviewed,
                                    onClick = { onStatusClick(userId) },
                                    onLongClick = { showMuteMenuFor = userId }
                                )
                                // Mute context menu
                                DropdownMenu(
                                    expanded = showMuteMenuFor == userId,
                                    onDismissRequest = { showMuteMenuFor = null }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.NotificationsOff,
                                                    "Mute",
                                                    tint = colors.muted,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Mute ${latestStatus.userName}'s status", color = colors.onSurface)
                                            }
                                        },
                                        onClick = {
                                            showMuteMenuFor = null
                                            viewModel.muteContact(
                                                userId = userId,
                                                userName = latestStatus.userName
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // ---- MUTED UPDATES Section ----
                    if (mutedStatuses.isNotEmpty()) {
                        item {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isMutedSectionExpanded = !isMutedSectionExpanded }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "MUTED UPDATES",
                                        color = colors.muted,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        if (isMutedSectionExpanded) Icons.Default.Close else Icons.Default.Add,
                                        "Expand",
                                        tint = colors.muted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = isMutedSectionExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column {
                                        mutedStatuses.forEach { mutedItem ->
                                            val latestMuted = mutedItem.statuses.firstOrNull()
                                            if (latestMuted != null) {
                                                Box {
                                                    StatusContactRow(
                                                        status = latestMuted,
                                                        hasUnviewedStatus = false, // Muted: always gray ring
                                                        isMuted = true,
                                                        onClick = { onStatusClick(mutedItem.userId) },
                                                        onLongClick = { showMuteMenuFor = mutedItem.userId }
                                                    )
                                                    // Unmute context menu
                                                    DropdownMenu(
                                                        expanded = showMuteMenuFor == mutedItem.userId,
                                                        onDismissRequest = { showMuteMenuFor = null }
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text("Unmute ${latestMuted.userName}'s status", color = colors.onSurface) },
                                                            onClick = {
                                                                showMuteMenuFor = null
                                                                viewModel.unmuteContact(mutedItem.userId)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- F18: Handle case where all statuses expired mid-view ---
            if (activeMyStatuses.isEmpty() && myStatuses.isNotEmpty()) {
                // All my statuses have expired
                AnimatedVisibility(
                    visible = true,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Text(
                        text = "Your status has expired",
                        color = colors.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ==================== My Status Section ====================
@Composable
private fun MyStatusSection(
    currentUser: User,
    hasActiveStatus: Boolean,
    latestMyStatus: Status?,
    onMyStatusClick: () -> Unit,
    onAddMediaStatus: () -> Unit
) {
    val colors = ZaxoTheme.colors

    NeuElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = { if (hasActiveStatus) onMyStatusClick() else onAddMediaStatus() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with + icon overlay (when no active status)
            Box {
                StatusRing(
                    photoUrl = currentUser.photoUrl,
                    name = currentUser.displayName,
                    size = 60.dp,
                    hasActiveStatus = hasActiveStatus,
                    hasUnviewedStatus = false // your own statuses are always "viewed"
                )

                // Add icon overlay when no active statuses
                if (!hasActiveStatus) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.BottomEnd)
                            .shadow(2.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colors.primary,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add status",
                                tint = colors.onPrimary,
                                modifier = Modifier
                                    .size(22.dp)
                                    .padding(2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "My Status",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = colors.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (hasActiveStatus) {
                        "Tap to view your status"
                    } else {
                        "Tap to add status update"
                    },
                    fontSize = 13.sp,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ==================== Contact Status Row ====================
@Composable
private fun StatusContactRow(
    status: Status,
    hasUnviewedStatus: Boolean,
    isMuted: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val colors = ZaxoTheme.colors
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    val timeText = remember(status.createdAt) {
        val cal = Calendar.getInstance().apply { timeInMillis = status.createdAt }
        val today = Calendar.getInstance()
        when {
            isSameDay(cal, today) -> "Today, ${timeFormat.format(Date(status.createdAt))}"
            isSameDay(cal, Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }) ->
                "Yesterday, ${timeFormat.format(Date(status.createdAt))}"
            else -> "${dateFormat.format(Date(status.createdAt))}, ${timeFormat.format(Date(status.createdAt))}"
        }
    }

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
            Box {
                StatusRing(
                    photoUrl = status.userPhotoUrl,
                    name = status.userName,
                    size = 56.dp,
                    hasActiveStatus = true,
                    hasUnviewedStatus = if (isMuted) false else hasUnviewedStatus // Muted = gray ring
                )
                // Mute icon overlay (C.4)
                if (isMuted) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.BottomEnd)
                            .shadow(2.dp, CircleShape, ambientColor = colors.shadowDark, spotColor = colors.shadowLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colors.muted,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                Icons.Default.NotificationsOff,
                                "Muted",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.userName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (isMuted) colors.muted else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = timeText,
                    fontSize = 13.sp,
                    color = colors.muted
                )
            }
        }
    }
}

// ==================== Date Helpers ====================
private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
