package com.zaxo.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zaxo.app.model.User
import com.zaxo.app.ui.components.*
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.GroupAdminState
import com.zaxo.app.viewmodel.GroupAdminViewModel
import com.zaxo.app.viewmodel.MemberInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupAdminScreen(
    onBack: () -> Unit,
    viewModel: GroupAdminViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val state by viewModel.state.collectAsState()
    val isEditingGroupInfo by viewModel.isEditingGroupInfo.collectAsState()
    val editGroupName by viewModel.editGroupName.collectAsState()
    val editGroupDescription by viewModel.editGroupDescription.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val showAddMemberDialog by viewModel.showAddMemberDialog.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val contactSearchResults by viewModel.contactSearchResults.collectAsState()
    val zaxoNumberLookupError by viewModel.zaxoNumberLookupError.collectAsState()
    val zaxoNumberLookupResult by viewModel.zaxoNumberLookupResult.collectAsState()

    // Expandable member action state
    var expandedMemberUid by remember { mutableStateOf<String?>(null) }

    // Confirm dialog state
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Group Settings",
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface
                        )
                        if (!state.isCurrentUserAdmin) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Lock,
                                "Read-only",
                                modifier = Modifier.size(16.dp),
                                tint = colors.muted
                            )
                        }
                    }
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
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // === Group Info Section ===
                item {
                    GroupInfoSection(
                        state = state,
                        isEditing = isEditingGroupInfo,
                        editName = editGroupName,
                        editDescription = editGroupDescription,
                        isSaving = isSaving,
                        onEditNameChange = { viewModel.updateEditGroupName(it) },
                        onEditDescriptionChange = { viewModel.updateEditGroupDescription(it) },
                        onStartEdit = { viewModel.startEditingGroupInfo() },
                        onCancelEdit = { viewModel.cancelEditingGroupInfo() },
                        onSaveEdit = { viewModel.saveGroupInfo() }
                    )
                }

                // === Members Section Header ===
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Members (${state.members.size})",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = colors.onSurface
                        )
                        if (state.isCurrentUserAdmin) {
                            NeuIconButton(
                                onClick = { viewModel.showAddMemberDialog() },
                                icon = Icons.Default.PersonAdd,
                                contentDescription = "Add Member"
                            )
                        }
                    }
                    NeuDivider()
                }

                // === Member List ===
                items(state.members) { member ->
                    MemberItem(
                        member = member,
                        isAdmin = state.isCurrentUserAdmin,
                        creatorUid = state.createdBy,
                        isExpanded = expandedMemberUid == member.uid,
                        onExpand = {
                            expandedMemberUid = if (expandedMemberUid == member.uid) null else member.uid
                        },
                        onPromote = {
                            confirmAction = ConfirmAction.Promote(member.uid, member.displayName)
                        },
                        onDemote = {
                            confirmAction = ConfirmAction.Demote(member.uid, member.displayName)
                        },
                        onRemove = {
                            confirmAction = ConfirmAction.Remove(member.uid, member.displayName)
                        }
                    )
                }

                // === Leave Group ===
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    NeuButton(
                        onClick = {
                            confirmAction = ConfirmAction.LeaveGroup
                        },
                        containerColor = colors.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            Icons.Default.ExitToApp,
                            "Leave",
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Leave Group", fontWeight = FontWeight.Medium, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // === Add Member Dialog ===
    if (showAddMemberDialog) {
        AddMemberDialog(
            query = searchQuery,
            onQueryChange = { viewModel.updateSearchQuery(it) },
            results = contactSearchResults,
            onAddMember = { viewModel.addMember(it) },
            onDismiss = { viewModel.hideAddMemberDialog() },
            zaxoNumberLookupError = zaxoNumberLookupError,
            zaxoNumberLookupResult = zaxoNumberLookupResult,
            onSearchByZaxoNumber = { viewModel.searchByZaxoNumber(it) },
            onAddFromLookup = { viewModel.addMemberFromLookup(it) },
            onClearLookup = { viewModel.clearZaxoLookup() }
        )
    }

    // === Confirm Dialog ===
    confirmAction?.let { action ->
        ConfirmDialog(
            action = action,
            onConfirm = {
                when (it) {
                    is ConfirmAction.Promote -> viewModel.promoteToAdmin(it.uid, it.name)
                    is ConfirmAction.Demote -> viewModel.demoteAdmin(it.uid, it.name)
                    is ConfirmAction.Remove -> viewModel.removeMember(it.uid, it.name)
                    is ConfirmAction.LeaveGroup -> viewModel.leaveGroup()
                }
                confirmAction = null
            },
            onDismiss = { confirmAction = null }
        )
    }
}

// ==================== Confirm Actions ====================

private sealed class ConfirmAction {
    data class Promote(val uid: String, val name: String) : ConfirmAction()
    data class Demote(val uid: String, val name: String) : ConfirmAction()
    data class Remove(val uid: String, val name: String) : ConfirmAction()
    data object LeaveGroup : ConfirmAction()
}

// ==================== Group Info Section ====================

@Composable
private fun GroupInfoSection(
    state: GroupAdminState,
    isEditing: Boolean,
    editName: String,
    editDescription: String,
    isSaving: Boolean,
    onEditNameChange: (String) -> Unit,
    onEditDescriptionChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit
) {
    val colors = ZaxoTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NeuAvatar(
            photoUrl = state.groupAvatar ?: "",
            name = state.groupName,
            size = 72.dp
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (isEditing && state.isCurrentUserAdmin) {
            // Editable fields
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = onEditNameChange,
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.muted,
                        cursorColor = colors.primary,
                        focusedLabelColor = colors.primary
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                OutlinedTextField(
                    value = editDescription,
                    onValueChange = onEditDescriptionChange,
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.muted,
                        cursorColor = colors.primary,
                        focusedLabelColor = colors.primary
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NeuButton(
                    onClick = onCancelEdit,
                    containerColor = colors.muted,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", color = Color.White, fontWeight = FontWeight.Medium)
                }
                NeuButton(
                    onClick = onSaveEdit,
                    enabled = !isSaving,
                    containerColor = colors.primary,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Save", color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            // Read-only display (non-admin sees this; admin sees this with edit button)
            Text(
                state.groupName,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = colors.onSurface
            )
            if (state.groupDescription.isNotEmpty()) {
                Text(
                    state.groupDescription,
                    fontSize = 13.sp,
                    color = colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            // Only admins can edit
            if (state.isCurrentUserAdmin) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onStartEdit) {
                    Icon(
                        Icons.Default.Edit,
                        "Edit",
                        modifier = Modifier.size(16.dp),
                        tint = colors.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit group info", color = colors.primary, fontSize = 13.sp)
                }
            } else {
                // Non-admin: show read-only hint
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Lock,
                        "Read-only",
                        modifier = Modifier.size(14.dp),
                        tint = colors.muted
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Only admins can edit group info", color = colors.muted, fontSize = 12.sp)
                }
            }
        }
    }
}

// ==================== Member Item ====================

@Composable
private fun MemberItem(
    member: MemberInfo,
    isAdmin: Boolean,
    creatorUid: String,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onPromote: () -> Unit,
    onDemote: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = ZaxoTheme.colors

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (isAdmin && !member.isCreator) onExpand() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeuAvatar(
                photoUrl = member.photoUrl,
                name = member.displayName,
                size = 42.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        member.displayName,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = colors.onSurface
                    )
                    if (member.isCreator) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Star,
                            "Creator",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFF39C12)
                        )
                    } else if (member.isAdmin) {
                        Spacer(modifier = Modifier.width(6.dp))
                        NeuChip(
                            label = "Admin",
                            selected = true,
                            onClick = {},
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }

            if (isAdmin && !member.isCreator) {
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    "Actions",
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Expandable action chips
        AnimatedVisibility(
            visible = isExpanded && isAdmin && !member.isCreator,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 70.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (member.isAdmin) {
                    NeuChip(
                        label = "Demote",
                        selected = false,
                        onClick = onDemote
                    )
                } else {
                    NeuChip(
                        label = "Make Admin",
                        selected = false,
                        onClick = onPromote
                    )
                }
                NeuChip(
                    label = "Remove",
                    selected = false,
                    onClick = onRemove
                )
            }
        }

        NeuDivider(modifier = Modifier.padding(horizontal = 70.dp))
    }
}

// ==================== Add Member Dialog ====================

@Composable
private fun AddMemberDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<User>,
    onAddMember: (User) -> Unit,
    onDismiss: () -> Unit,
    zaxoNumberLookupError: String?,
    zaxoNumberLookupResult: User?,
    onSearchByZaxoNumber: (String) -> Unit,
    onAddFromLookup: (User) -> Unit,
    onClearLookup: () -> Unit
) {
    val colors = ZaxoTheme.colors
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Name search, 1 = Zaxo Number
    var zaxoNumberInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = {
            Text(
                "Add Member",
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface
            )
        },
        text = {
            Column {
                // Tab selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NeuChip(
                        label = "By Name",
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    NeuChip(
                        label = "By Zaxo #",
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // === Name Search Tab ===
                    NeuSearchBar(
                        query = query,
                        onQueryChange = onQueryChange,
                        placeholder = "Search by name..."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (results.isEmpty() && query.length >= 2) {
                        Text("No users found", color = colors.muted, fontSize = 14.sp)
                    } else if (results.isEmpty()) {
                        Text("Type a name to search", color = colors.muted, fontSize = 14.sp)
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 240.dp)
                        ) {
                            items(results) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onAddMember(user) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    NeuAvatar(
                                        photoUrl = user.photoUrl,
                                        name = user.displayName,
                                        size = 36.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            user.displayName,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = colors.onSurface
                                        )
                                        if (user.about.isNotEmpty()) {
                                            Text(
                                                user.about,
                                                fontSize = 12.sp,
                                                color = colors.muted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(
                                        Icons.Default.PersonAdd,
                                        "Add",
                                        tint = colors.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // === Zaxo Number Tab (F1 FIX) ===
                    OutlinedTextField(
                        value = zaxoNumberInput,
                        onValueChange = { zaxoNumberInput = it },
                        label = { Text("Zaxo Number") },
                        placeholder = { Text("e.g. 1234567") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.muted,
                            cursorColor = colors.primary,
                            focusedLabelColor = colors.primary
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.Dialpad, "Number", tint = colors.muted)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Search button
                    NeuButton(
                        onClick = { onSearchByZaxoNumber(zaxoNumberInput) },
                        containerColor = colors.primary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Search, "Search", modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Find", color = Color.White, fontWeight = FontWeight.Medium)
                    }

                    // Lookup result
                    zaxoNumberLookupResult?.let { user ->
                        Spacer(modifier = Modifier.height(12.dp))
                        NeuCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddFromLookup(user) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NeuAvatar(
                                    photoUrl = user.photoUrl,
                                    name = user.displayName,
                                    size = 40.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        user.displayName,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = colors.onSurface
                                    )
                                    Text(
                                        "Zaxo #${user.zaxoNumber}",
                                        fontSize = 12.sp,
                                        color = colors.muted
                                    )
                                }
                                Icon(
                                    Icons.Default.PersonAdd,
                                    "Add",
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Error state (F1)
                    zaxoNumberLookupError?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                "Error",
                                modifier = Modifier.size(16.dp),
                                tint = colors.error
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(error, color = colors.error, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.primary)
            }
        }
    )
}

// ==================== Confirm Dialog ====================

@Composable
private fun ConfirmDialog(
    action: ConfirmAction,
    onConfirm: (ConfirmAction) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ZaxoTheme.colors
    val (title, message, confirmLabel) = when (action) {
        is ConfirmAction.Promote -> Triple(
            "Promote to Admin",
            "Make ${action.name} an admin? They will be able to edit group info and manage members.",
            "Promote"
        )
        is ConfirmAction.Demote -> Triple(
            "Demote Admin",
            "Remove ${action.name} as admin? They will become a regular member.",
            "Demote"
        )
        is ConfirmAction.Remove -> Triple(
            "Remove Member",
            "Remove ${action.name} from the group? They will no longer see group messages.",
            "Remove"
        )
        is ConfirmAction.LeaveGroup -> Triple(
            "Leave Group",
            "Are you sure you want to leave this group? You will no longer receive messages.",
            "Leave"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = {
            Text(title, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        },
        text = {
            Text(message, fontSize = 14.sp, color = colors.onSurface)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(action) }) {
                Text(confirmLabel, color = colors.error, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.muted)
            }
        }
    )
}
