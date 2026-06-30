package com.zaxo.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zaxo.app.ui.components.*
import com.zaxo.app.ui.theme.ZaxoTheme
import com.zaxo.app.viewmodel.ProfileEditViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    onBack: () -> Unit,
    viewModel: ProfileEditViewModel = hiltViewModel()
) {
    val colors = ZaxoTheme.colors
    val displayName by viewModel.displayName.collectAsState()
    val about by viewModel.about.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveComplete by viewModel.saveComplete.collectAsState()
    val user by viewModel.user.collectAsState()

    LaunchedEffect(saveComplete) {
        if (saveComplete) {
            onBack()
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Edit Profile",
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
                    TextButton(
                        onClick = { viewModel.saveProfile() },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = colors.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save", color = colors.primary, fontWeight = FontWeight.Bold)
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Profile photo
            NeuAvatar(
                photoUrl = user.photoUrl,
                name = displayName,
                size = 96.dp
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = { /* change photo */ }) {
                Text("Change photo", color = colors.primary, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Display Name
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Display Name",
                        fontSize = 12.sp,
                        color = colors.muted,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    BasicTextField(
                        value = displayName,
                        onValueChange = { viewModel.updateDisplayName(it) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            color = colors.onSurface,
                            fontSize = 16.sp
                        ),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // About
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "About",
                        fontSize = 12.sp,
                        color = colors.muted,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    BasicTextField(
                        value = about,
                        onValueChange = { viewModel.updateAbout(it) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            color = colors.onSurface,
                            fontSize = 16.sp
                        ),
                        maxLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Phone (read-only)
            NeuCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Phone",
                        fontSize = 12.sp,
                        color = colors.muted,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        user.phone.ifEmpty { "Not set" },
                        fontSize = 16.sp,
                        color = colors.onSurface
                    )
                }
            }
        }
    }
}
