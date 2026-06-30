package com.zaxo.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaxo.app.ui.components.*
import com.zaxo.app.ui.theme.ZaxoTheme
import org.json.JSONArray

// ==================== Default Quick Responses ====================
val DEFAULT_QUICK_RESPONSES = listOf(
    "Can't talk right now",
    "I'll call you back",
    "I'm in a meeting",
    "On my way",
    "Thanks for calling"
)

// ==================== Quick Responses Screen ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickResponsesScreen(
    onBack: () -> Unit
) {
    val colors = ZaxoTheme.colors
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("quickDeclineResponses", 0) }

    // Load responses from SharedPreferences, or use defaults
    val savedResponses = remember {
        val json = prefs.getString("responses", null)
        if (json != null) {
            try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) {
                DEFAULT_QUICK_RESPONSES
            }
        } else {
            DEFAULT_QUICK_RESPONSES
        }
    }

    var responses by remember { mutableStateOf(savedResponses.toMutableStateList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var editText by remember { mutableStateOf("") }

    // Persist to SharedPreferences whenever responses change
    fun persistResponses(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString("responses", arr.toString()).apply()
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Quick Responses",
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
                    if (responses.size < 10) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, "Add", tint = colors.primary)
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
            Text(
                "Edit quick decline responses for incoming calls. Max 10 responses, 50 characters each.",
                color = colors.muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(responses) { index, response ->
                    QuickResponseItem(
                        text = response,
                        onEdit = {
                            editingIndex = index
                            editText = response
                        },
                        onDelete = {
                            responses.removeAt(index)
                            persistResponses(responses)
                        },
                        onMoveUp = {
                            if (index > 0) {
                                val item = responses.removeAt(index)
                                responses.add(index - 1, item)
                                persistResponses(responses)
                            }
                        },
                        onMoveDown = {
                            if (index < responses.lastIndex) {
                                val item = responses.removeAt(index)
                                responses.add(index + 1, item)
                                persistResponses(responses)
                            }
                        },
                        canMoveUp = index > 0,
                        canMoveDown = index < responses.lastIndex
                    )
                }
            }
        }
    }

    // Add new response dialog
    if (showAddDialog) {
        QuickResponseDialog(
            initialText = "",
            title = "Add Response",
            onConfirm = { text ->
                if (text.isNotBlank() && text.length <= 50 && responses.size < 10) {
                    responses.add(text)
                    persistResponses(responses)
                }
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // Edit response dialog
    if (editingIndex >= 0) {
        QuickResponseDialog(
            initialText = editText,
            title = "Edit Response",
            onConfirm = { text ->
                if (text.isNotBlank() && text.length <= 50 && editingIndex < responses.size) {
                    responses[editingIndex] = text
                    persistResponses(responses)
                }
                editingIndex = -1
            },
            onDismiss = { editingIndex = -1 }
        )
    }
}

@Composable
private fun QuickResponseItem(
    text: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean
) {
    val colors = ZaxoTheme.colors

    NeuElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reorder handles
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(28.dp)
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        "Move Up",
                        tint = if (canMoveUp) colors.onSurface else colors.muted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        "Move Down",
                        tint = if (canMoveDown) colors.onSurface else colors.muted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text,
                fontSize = 14.sp,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )

            // Edit button
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    "Edit",
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    "Delete",
                    tint = colors.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickResponseDialog(
    initialText: String,
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ZaxoTheme.colors
    var text by remember { mutableStateOf(initialText) }
    val charCount = text.length
    val isOverLimit = charCount > 50

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        },
        text = {
            Column {
                NeuSearchBar(
                    query = text,
                    onQueryChange = { text = it },
                    placeholder = "Enter response text..."
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$charCount/50",
                    fontSize = 12.sp,
                    color = if (isOverLimit) colors.error else colors.muted
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank() && !isOverLimit
            ) {
                Text("Save", color = if (text.isNotBlank() && !isOverLimit) colors.primary else colors.muted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.muted)
            }
        },
        containerColor = colors.background
    )
}
