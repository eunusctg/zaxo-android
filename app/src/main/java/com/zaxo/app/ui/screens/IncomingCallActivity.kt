package com.zaxo.app.ui.screens

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zaxo.app.ui.theme.ZaxoTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen activity launched by FCM for incoming calls.
 * Shows the IncomingCallScreen over the lock screen.
 * This activity is necessary because Compose Navigation alone
 * cannot show full-screen intents from background.
 *
 * Features:
 * - Shows over lock screen (showWhenLocked + turnScreenOn)
 * - Single-top launch mode prevents duplicate activities
 * - Excluded from recents to avoid lingering in task switcher
 * - Handles onNewIntent for subsequent incoming calls
 * - Manages wakelock via window flags for screen-on behavior
 */
@AndroidEntryPoint
class IncomingCallActivity : ComponentActivity() {

    private var roomId by mutableStateOf("")
    private var callId by mutableStateOf("")
    private var callType by mutableStateOf("audio")
    private var callerUid by mutableStateOf("")
    private var callerName by mutableStateOf("Unknown")
    private var callerZaxoNumber by mutableStateOf("")
    private var callerPhotoUrl by mutableStateOf("")
    private var isGroupCall by mutableStateOf(false)
    private var groupId by mutableStateOf("")
    private var groupName by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn screen on
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // Keep screen on during incoming call
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        // Read extras from intent
        readIntentExtras(intent)

        setContent {
            ZaxoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    IncomingCallScreen(
                        roomId = roomId,
                        callId = callId,
                        callType = callType,
                        callerUid = callerUid,
                        callerName = callerName,
                        callerZaxoNumber = callerZaxoNumber,
                        onCallAccepted = {
                            // After accepting, the CallViewModel transitions to ACTIVE state.
                            // The main app handles the active call screen via navigation.
                            // Finish this activity to return control to the main app.
                            finish()
                        },
                        onCallDeclined = {
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { readIntentExtras(it) }
    }

    private fun readIntentExtras(intent: Intent?) {
        intent?.let {
            roomId = it.getStringExtra("roomId") ?: roomId
            callId = it.getStringExtra("callId") ?: callId
            callType = it.getStringExtra("callType") ?: callType
            callerUid = it.getStringExtra("callerUid") ?: callerUid
            callerName = it.getStringExtra("callerName") ?: callerName
            callerZaxoNumber = it.getStringExtra("callerZaxoNumber") ?: callerZaxoNumber
            callerPhotoUrl = it.getStringExtra("callerPhotoUrl") ?: callerPhotoUrl
            isGroupCall = it.getBooleanExtra("isGroupCall", isGroupCall)
            groupId = it.getStringExtra("groupId") ?: groupId
            groupName = it.getStringExtra("groupName") ?: groupName
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up screen-on flags when activity is destroyed
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
