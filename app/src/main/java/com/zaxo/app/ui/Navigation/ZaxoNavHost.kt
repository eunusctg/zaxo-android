package com.zaxo.app.ui.Navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.zaxo.app.model.Screen
import com.zaxo.app.ui.screens.*

@Composable
fun ZaxoNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        // Splash screen
        composable(Screen.Splash.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            SplashScreen(
                onAuthenticated = {
                    // F60: Check if onboarding should be shown (first launch only)
                    val prefs = context.getSharedPreferences("zaxo_prefs", 0)
                    val hasSeenOnboarding = prefs.getBoolean("hasSeenOnboarding", false)
                    if (hasSeenOnboarding) {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                },
                onUnauthenticated = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                authCheck = {
                    val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    user != null
                }
            )
        }

        // Onboarding screen
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onGetStarted = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // Main screen
        composable(Screen.Main.route) {
            MainScreen(
                onChatClick = { chatId ->
                    navController.navigate(Screen.ChatRoom.withArgs(chatId))
                },
                onSearchClick = {
                    navController.navigate(Screen.Search.route)
                },
                onArchivedClick = {
                    navController.navigate(Screen.ArchivedChats.route)
                },
                onProfileEditClick = {
                    navController.navigate(Screen.ProfileEdit.route)
                },
                onStatusClick = {
                    navController.navigate(Screen.Status.route)
                },
                onStatusCameraClick = {
                    navController.navigate(Screen.StatusCamera.route)
                },
                onContactPickerClick = {
                    navController.navigate(Screen.ContactPicker.route)
                },
                onNotificationSettingsClick = {
                    navController.navigate(Screen.NotificationSettings.route)
                },
                onStarredMessagesClick = {
                    navController.navigate(Screen.StarredMessages.route)
                },
                onBlockedContactsClick = {
                    navController.navigate(Screen.BlockedContacts.route)
                },
                onQuickResponsesClick = {
                    navController.navigate(Screen.QuickResponses.route)
                },
                onDialpadClick = {
                    navController.navigate(Screen.Dialpad.route)
                },
                onLogoutClick = {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screen.Splash.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Chat room
        composable(
            route = Screen.ChatRoom.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            ChatRoomScreen(
                chatId = chatId,
                onBack = { navController.popBackStack() },
                onChatInfo = { id ->
                    navController.navigate(Screen.ChatInfo.withArgs(id))
                },
                onForward = { messageId, sourceChatId ->
                    navController.navigate(Screen.Forward.withArgs(messageId, sourceChatId))
                },
                onMediaClick = { mediaChatId, messageId ->
                    navController.navigate(Screen.MediaViewer.withArgs(mediaChatId, messageId))
                },
                onWallpaperClick = { chatIdArg ->
                    navController.navigate(Screen.WallpaperPicker.withArgs(chatIdArg))
                }
            )
        }

        // Chat info
        composable(
            route = Screen.ChatInfo.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) {
            ChatInfoScreen(
                chatId = it.arguments?.getString("chatId") ?: "",
                onBack = { navController.popBackStack() },
                onGroupAdmin = { chatId ->
                    navController.navigate(Screen.GroupAdmin.withArgs(chatId))
                },
                onMediaClick = { chatId, messageId ->
                    navController.navigate(Screen.MediaViewer.withArgs(chatId, messageId))
                },
                onWallpaperClick = { chatId ->
                    navController.navigate(Screen.WallpaperPicker.withArgs(chatId))
                }
            )
        }

        // Forward (original)
        composable(
            route = Screen.Forward.route,
            arguments = listOf(
                navArgument("messageId") { type = NavType.StringType },
                navArgument("chatId") { type = NavType.StringType }
            )
        ) {
            ForwardScreen(
                onBack = { navController.popBackStack() },
                onForwardComplete = { navController.popBackStack() }
            )
        }

        // Archived chats
        composable(Screen.ArchivedChats.route) {
            ArchivedChatsScreen(
                onBack = { navController.popBackStack() },
                onChatClick = { chatId ->
                    navController.navigate(Screen.ChatRoom.withArgs(chatId))
                }
            )
        }

        // Profile edit
        composable(Screen.ProfileEdit.route) {
            ProfileEditScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Search
        composable(Screen.Search.route) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onChatClick = { chatId ->
                    navController.navigate(Screen.ChatRoom.withArgs(chatId))
                }
            )
        }

        // Group admin
        composable(
            route = Screen.GroupAdmin.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) {
            GroupAdminScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Media viewer
        composable(
            route = Screen.MediaViewer.route,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("messageId") { type = NavType.StringType }
            )
        ) {
            MediaViewerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Wallpaper Picker
        composable(
            route = Screen.WallpaperPicker.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) {
            WallpaperPickerScreen(
                onBack = { navController.popBackStack() },
                onApply = { navController.popBackStack() }
            )
        }

        // Status list screen
        composable(Screen.Status.route) {
            StatusScreen(
                onMyStatusClick = {
                    navController.navigate(Screen.StatusViewer.withArgs("self"))
                },
                onStatusClick = { userId ->
                    navController.navigate(Screen.StatusViewer.withArgs(userId))
                },
                onAddTextStatus = {
                    navController.navigate(Screen.StatusTextComposer.route)
                },
                onAddMediaStatus = {
                    navController.navigate(Screen.StatusCamera.route)
                }
            )
        }

        // Status Viewer
        composable(
            route = Screen.StatusViewer.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) {
            StatusViewerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Status Camera
        composable(Screen.StatusCamera.route) {
            StatusCameraScreen(
                onBack = { navController.popBackStack() },
                onTextComposer = {
                    navController.navigate(Screen.StatusTextComposer.route)
                },
                onCaptureComplete = { mediaUri ->
                    navController.navigate(Screen.StatusEditor.withArgs(java.net.URLEncoder.encode(mediaUri, "UTF-8")))
                }
            )
        }

        // Status Editor
        composable(
            route = Screen.StatusEditor.route,
            arguments = listOf(navArgument("mediaUri") { type = NavType.StringType })
        ) { backStackEntry ->
            val mediaUri = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("mediaUri") ?: "", "UTF-8"
            )
            StatusEditorScreen(
                mediaUri = mediaUri,
                onBack = { navController.popBackStack() },
                onPosted = {
                    navController.popBackStack(Screen.Status.route, inclusive = false)
                }
            )
        }

        // Status Text Composer
        composable(Screen.StatusTextComposer.route) {
            StatusTextComposerScreen(
                onBack = { navController.popBackStack() },
                onPosted = { navController.popBackStack() }
            )
        }

        // Contact Picker
        composable(Screen.ContactPicker.route) {
            ContactPickerScreen(
                onBack = { navController.popBackStack() },
                onContactSelected = { selectedIds ->
                    // Navigate to chat room with the selected contacts
                    navController.popBackStack()
                }
            )
        }

        // Forward Picker (new dedicated screen)
        composable(
            route = Screen.ForwardPicker.route,
            arguments = listOf(navArgument("messageId") { type = NavType.StringType })
        ) {
            ForwardPickerScreen(
                onBack = { navController.popBackStack() },
                onForwardComplete = { navController.popBackStack() }
            )
        }

        // Starred Messages
        composable(Screen.StarredMessages.route) {
            StarredMessagesScreen(
                onBack = { navController.popBackStack() },
                onMessageClick = { chatId, messageId ->
                    navController.navigate(Screen.ChatRoom.withArgs(chatId))
                }
            )
        }

        // Blocked Contacts
        composable(Screen.BlockedContacts.route) {
            BlockedContactsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Notification Settings
        composable(Screen.NotificationSettings.route) {
            NotificationSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // Quick Responses
        composable(Screen.QuickResponses.route) {
            QuickResponsesScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ==================== CALLING SYSTEM ROUTES ====================

        // Dialpad
        composable(Screen.Dialpad.route) {
            DialpadScreen(
                onBack = { navController.popBackStack() },
                onCallStarted = {
                    navController.navigate(Screen.OutgoingCall.withArgs("current"))
                }
            )
        }

        // Outgoing Call
        composable(
            route = Screen.OutgoingCall.route,
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) {
            OutgoingCallScreen(
                onCallConnected = {
                    navController.navigate(Screen.ActiveCall.withArgs(it.arguments?.getString("callId") ?: "current")) {
                        popUpTo(Screen.OutgoingCall.route) { inclusive = true }
                    }
                },
                onCallEnded = {
                    navController.popBackStack()
                }
            )
        }

        // Incoming Call
        composable(
            route = Screen.IncomingCall.route,
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) { backStackEntry ->
            IncomingCallScreen(
                callId = backStackEntry.arguments?.getString("callId") ?: "",
                onCallAccepted = {
                    navController.navigate(Screen.ActiveCall.withArgs(backStackEntry.arguments?.getString("callId") ?: "")) {
                        popUpTo(Screen.IncomingCall.route) { inclusive = true }
                    }
                },
                onCallDeclined = {
                    navController.popBackStack()
                }
            )
        }

        // Active Call
        composable(
            route = Screen.ActiveCall.route,
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) {
            ActiveCallScreen(
                onCallEnded = { navController.popBackStack() }
            )
        }

        // Group Call
        composable(
            route = Screen.GroupCall.route,
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) {
            GroupCallScreen(
                onCallEnded = { navController.popBackStack() }
            )
        }

        // Post Call
        composable(
            route = Screen.PostCall.route,
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) {
            // Brief post-call summary then pop back
            PostCallScreen(
                onDone = { navController.popBackStack() }
            )
        }

        // Call Waiting
        composable(
            route = Screen.CallWaiting.route,
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) {
            // Handled as overlay in ActiveCallScreen
        }
    }
}
