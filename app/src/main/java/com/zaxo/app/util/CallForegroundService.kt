package com.zaxo.app.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zaxo.app.MainActivity
import com.zaxo.app.R
import timber.log.Timber

/**
 * Foreground service for ongoing calls.
 * Keeps the app alive during active calls and shows a persistent notification
 * that allows the user to return to the call screen or end the call.
 *
 * Features:
 * - Persistent notification with "Return to call" and "End call" actions
 * - START_STICKY to survive system kills during active calls
 * - Notification channel with low importance (does not make sound)
 * - Proper foreground service type declaration for microphone + camera
 */
class CallForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "zaxo_call_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_END_CALL = "com.zaxo.app.END_CALL"
        const val ACTION_OPEN_CALL = "com.zaxo.app.OPEN_CALL"
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_CALLER_NAME = "callerName"
        const val EXTRA_CALL_TYPE = "callType"
    }

    private var callerName: String = "Zaxo Call"
    private var callType: String = "audio"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_END_CALL -> {
                Timber.d("End call from notification")
                // Broadcast end call event — CallViewModel handles it
                sendBroadcast(Intent("com.zaxo.app.ACTION_END_CALL"))
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (intent?.action != ACTION_END_CALL) {
            callerName = intent?.getStringExtra(EXTRA_CALLER_NAME) ?: "Zaxo Call"
            callType = intent?.getStringExtra(EXTRA_CALL_TYPE) ?: "audio"
            val notification = buildNotification(callerName, callType)

            try {
                startForeground(NOTIFICATION_ID, notification)
                Timber.d("Call foreground service started for: $callerName")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start foreground service")
                // On Android 12+ with foreground service restrictions, this may fail
                // if not called from a valid source. Log but don't crash.
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the service running even if the task is removed
        // The call continues in the background
        Timber.d("Task removed — call foreground service persists")
        super.onTaskRemoved(rootIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Zaxo Calls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing call notifications"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(callerName: String, callType: String): Notification {
        // Open call screen intent — navigates to the active call screen
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_CALL
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("navigateTo", "active_call")
        }

        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // End call action
        val endIntent = Intent(this, CallForegroundService::class.java).apply {
            action = ACTION_END_CALL
        }

        val endPendingIntent = PendingIntent.getService(
            this, 1, endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isVideo = callType == "video"
        val title = if (isVideo) "Video Call" else "Audio Call"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("In call with $callerName")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "End Call",
                endPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .build()
    }
}
