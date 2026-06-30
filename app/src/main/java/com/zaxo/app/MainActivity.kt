package com.zaxo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.zaxo.app.navigation.DeepLinkDestination
import com.zaxo.app.navigation.DeepLinkHandler
import com.zaxo.app.ui.Navigation.ZaxoNavHost
import com.zaxo.app.ui.theme.ZaxoAppTheme
import com.zaxo.app.util.PresenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var presenceManager: PresenceManager

    // Mutable state for deep link navigation — updated by onNewIntent
    private var _deepLinkDestination = mutableStateOf<DeepLinkDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // D.3: Set user online on app open
        presenceManager.setOnline()

        // Parse initial deep link from launch intent
        _deepLinkDestination.value = intent?.let { DeepLinkHandler.parseIntent(it) }

        setContent {
            ZaxoAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    // Observe deep link destination changes (from both onCreate and onNewIntent)
                    val deepLinkDestination by _deepLinkDestination

                    // Navigate to deep link destination after NavHost is ready
                    LaunchedEffect(deepLinkDestination) {
                        deepLinkDestination?.let { destination ->
                            // Small delay to let NavHost initialize
                            kotlinx.coroutines.delay(500)
                            navigateToDeepLink(navController, destination)
                            // Clear the destination after navigation to prevent re-navigation
                            _deepLinkDestination.value = null
                        }
                    }

                    ZaxoNavHost(navController = navController)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Parse and navigate to deep link from subsequent notifications while app is open
        val destination = DeepLinkHandler.parseIntent(intent)
        if (destination !is DeepLinkDestination.MainScreen) {
            _deepLinkDestination.value = destination
        }
    }

    override fun onResume() {
        super.onResume()
        // D.3: Set user online when app comes to foreground
        presenceManager.setOnline()
    }

    override fun onPause() {
        super.onPause()
        // D.3: Set user offline when app goes to background
        presenceManager.setOffline()
    }

    override fun onDestroy() {
        super.onDestroy()
        // D.3: Set user offline when activity is destroyed
        presenceManager.setOffline()
    }

    companion object {
        /**
         * Navigate to the deep link destination from a notification tap.
         */
        fun navigateToDeepLink(
            navController: NavHostController,
            destination: com.zaxo.app.navigation.DeepLinkDestination
        ) {
            when (destination) {
                is com.zaxo.app.navigation.DeepLinkDestination.Chat -> {
                    navController.navigate("chat_room/${destination.chatId}") {
                        popUpTo("main") { inclusive = false }
                    }
                }
                is com.zaxo.app.navigation.DeepLinkDestination.Call -> {
                    navController.navigate("active_call/${destination.callId}") {
                        popUpTo("main") { inclusive = false }
                    }
                }
                is com.zaxo.app.navigation.DeepLinkDestination.Status -> {
                    navController.navigate("status_viewer/${destination.contactId}") {
                        popUpTo("main") { inclusive = false }
                    }
                }
                is com.zaxo.app.navigation.DeepLinkDestination.MessageInChat -> {
                    navController.navigate("chat_room/${destination.chatId}") {
                        popUpTo("main") { inclusive = false }
                    }
                }
                is com.zaxo.app.navigation.DeepLinkDestination.MainScreen -> {
                    navController.navigate("main") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            }
        }
    }
}
