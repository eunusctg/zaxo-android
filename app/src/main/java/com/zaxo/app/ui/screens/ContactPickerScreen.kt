package com.zaxo.app.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaxo.app.model.User
import com.zaxo.app.ui.components.*
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.ContactPickerViewModel

// ==================== Contact Picker Mode ====================
enum class ContactPickerMode {
    NEW_CHAT,
    FORWARD,
    ADD_MEMBER,
    SHARE_STATUS
}

// ==================== Contact Picker Screen ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerScreen(
    onBack: () -> Unit,
    onContactSelected: (List<String>) -> Unit,
    mode: ContactPickerMode = ContactPickerMode.NEW_CHAT,
    viewModel: ContactPickerViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val contacts by viewModel.filteredContacts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedIds by viewModel.selectedContactIds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val title = when (mode) {
        ContactPickerMode.NEW_CHAT -> "Select Contacts"
        ContactPickerMode.FORWARD -> "Forward to..."
        ContactPickerMode.ADD_MEMBER -> "Add Members"
        ContactPickerMode.SHARE_STATUS -> "Share Status"
    }

    val isMultiSelect = mode in listOf(ContactPickerMode.NEW_CHAT, ContactPickerMode.ADD_MEMBER)

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
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
                    if (selectedIds.isNotEmpty()) {
                        TextButton(onClick = { onContactSelected(selectedIds.toList()) }) {
                            Text(
                                "Done",
                                color = colors.primary,
                                fontWeight = FontWeight.SemiBold
                            )
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
                placeholder = "Search contacts..."
            )

            // F62: Loading shimmer while contacts load
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else if (contacts.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PersonSearch,
                            "No contacts",
                            modifier = Modifier.size(64.dp),
                            tint = colors.muted
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No contacts found", color = colors.muted, fontSize = 16.sp)
                        Text("Sync your contacts to get started", color = colors.muted, fontSize = 13.sp)
                    }
                }
            } else {
                // Selected contacts chips (F63: show as removable chips)
                if (selectedIds.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedIds.toList()) { uid ->
                            val contact = contacts.find { it.uid == uid }
                            if (contact != null) {
                                SelectedContactChip(
                                    name = contact.displayName,
                                    onRemove = { viewModel.toggleContactSelection(uid) }
                                )
                            }
                        }
                    }
                }

                // Contact list
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(contacts, key = { it.uid }) { contact ->
                        ContactPickerItem(
                            contact = contact,
                            isSelected = contact.uid in selectedIds,
                            isMultiSelect = isMultiSelect,
                            onClick = { viewModel.toggleContactSelection(contact.uid) }
                        )
                    }
                }
            }
        }
    }
}

// ==================== Selected Contact Chip ====================
@Composable
private fun SelectedContactChip(
    name: String,
    onRemove: () -> Unit
) {
    val colors = ZaxoTheme.colors
    Row(
        modifier = Modifier
            .shadow(4.dp, RoundedCornerShape(20.dp), ambientColor = colors.shadowDark, spotColor = colors.shadowLight)
            .background(colors.primary.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            color = colors.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            Icons.Default.Close,
            "Remove",
            tint = colors.primary,
            modifier = Modifier
                .size(16.dp)
                .clickable(onClick = onRemove)
        )
    }
}

// ==================== Contact Picker Item ====================
@Composable
private fun ContactPickerItem(
    contact: User,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    onClick: () -> Unit
) {
    val colors = ZaxoTheme.colors

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
                photoUrl = contact.photoUrl,
                name = contact.displayName,
                size = 48.dp,
                isOnline = contact.isOnline
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (contact.zaxoNumber.isNotEmpty()) {
                    Text(
                        contact.zaxoNumber,
                        fontSize = 13.sp,
                        color = colors.muted
                    )
                }
            }

            // Selection indicator
            if (isMultiSelect) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.primary,
                        uncheckedColor = colors.muted
                    )
                )
            } else {
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
}


