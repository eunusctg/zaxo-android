package com.zaxo.app.util

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zaxo.app.MainActivity
import com.zaxo.app.notification.NotificationChannels
import com.zaxo.app.ui.screens.IncomingCallActivity
import timber.log.Timber

/**
 * FCM message handler for the ZAXO app.
 * Handles all notification types:
 * - Incoming calls → full-screen IncomingCallActivity
 * - New messages → heads-up notification with deep link
 * - Status updates → notification with deep link
 * - Call answered elsewhere → dismiss incoming call UI
 *
 * F70: Validates call message age — discards stale calls > 30s old.
 */
class ZaxoMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "ZaxoFCM"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("FCM token refreshed: ${token.take(10)}...")
        // Persist token — Cloud Functions use it for push delivery
        // The saveFcmToken Cloud Function is called from the app
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val type = data["type"] ?: return

        Timber.d("FCM received: type=$type")

        when (type) {
            "incoming_call", "group_call" -> handleIncomingCall(data)
            "new_message" -> handleNewMessage(data)
            "status_update" -> handleStatusUpdate(data)
            "call_answered_elsewhere" -> handleCallAnsweredElsewhere()
            else -> {
                Timber.d("Unknown FCM type: $type")
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // INCOMING CALL HANDLING
    // ═══════════════════════════════════════════════════════

    /**
     * E.1 FCM Message Handler for incoming calls.
     * Validates timestamp, then launches IncomingCallActivity.
     */
    private fun handleIncomingCall(data: Map<String, String>) {
        val roomId = data["roomId"] ?: return
        val callId = data["callId"] ?: return
        val callType = data["callType"] ?: "audio"
        val callerUid = data["callerUid"] ?: return
        val callerName = data["callerName"] ?: "Unknown"
        val callerZaxoNumber = data["callerZaxoNumber"] ?: ""
        val timestamp = data["timestamp"]?.toLongOrNull() ?: 0L
        val isGroupCall = data["isGroupCall"]?.toBoolean() ?: false
        val groupId = data["groupId"] ?: ""
        val groupName = data["groupName"] ?: ""
        val callerAvatar = data["callerAvatar"] ?: ""

        // F70: Validate age — discard if > 30s old
        val messageAge = System.currentTimeMillis() - timestamp
        if (messageAge > 30_000) {
            Timber.w("Discarding stale call notification (age: ${messageAge}ms)")
            showMissedCallNotification(callerName, callType)
            return
        }

        Timber.d("Incoming call from $callerName ($callerUid), type=$callType, roomId=$roomId")

        // Launch IncomingCallActivity with full-screen intent
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("roomId", roomId)
            putExtra("callId", callId)
            putExtra("callType", callType)
            putExtra("callerUid", callerUid)
            putExtra("callerName", callerName)
            putExtra("callerZaxoNumber", callerZaxoNumber)
            putExtra("callerAvatar", callerAvatar)
            putExtra("isGroupCall", isGroupCall)
            putExtra("groupId", groupId)
            putExtra("groupName", groupName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    // ═══════════════════════════════════════════════════════
    // NEW MESSAGE HANDLING
    // ═══════════════════════════════════════════════════════

    private fun handleNewMessage(data: Map<String, String>) {
        val chatId = data["chatId"] ?: return
        val senderName = data["senderName"] ?: "New message"
        val messageText = data["messageText"] ?: "Sent a message"

        // Only show notification if app is in background
        if (!isAppInForeground()) {
            showMessageNotification(chatId, senderName, messageText)
        } else {
            Timber.d("App in foreground — skipping message notification for $chatId")
        }
    }

    private fun showMessageNotification(chatId: String, senderName: String, message: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("deepLink", """{"type":"new_message","chatId":"$chatId"}""")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, chatId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.CHANNEL_MESSAGES)
            .setSmallIcon(com.zaxo.app.R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(message)
            )
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(chatId.hashCode(), notification)

        Timber.d("Message notification shown: $senderName")
    }

    // ═══════════════════════════════════════════════════════
    // STATUS UPDATE HANDLING
    // ═══════════════════════════════════════════════════════

    private fun handleStatusUpdate(data: Map<String, String>) {
        val contactName = data["contactName"] ?: "A contact"

        if (!isAppInForeground()) {
            showStatusNotification(contactName)
        }
    }

    private fun showStatusNotification(contactName: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigateTo", "status")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 3001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.CHANNEL_STATUS)
            .setSmallIcon(com.zaxo.app.R.drawable.ic_notification)
            .setContentTitle("Status Update")
            .setContentText("$contactName posted a new status")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(3001, notification)

        Timber.d("Status notification shown: $contactName")
    }

    // ═══════════════════════════════════════════════════════
    // CALL ANSWERED ELSEWHERE
    // ═══════════════════════════════════════════════════════

    private fun handleCallAnsweredElsewhere() {
        // Dismiss incoming call UI via broadcast
        sendBroadcast(Intent("com.zaxo.app.CALL_ANSWERED_ELSEWHERE"))
        Timber.d("Call answered elsewhere — dismissing incoming call UI")
    }

    // ═══════════════════════════════════════════════════════
    // MISSED CALL NOTIFICATION
    // ═══════════════════════════════════════════════════════

    private fun showMissedCallNotification(callerName: String, callType: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigateTo", "calls_tab")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isVideo = callType == "video"
        val title = if (isVideo) "Missed video call" else "Missed call"

        val notification = NotificationCompat.Builder(this, NotificationChannels.CHANNEL_MISSED_CALLS)
            .setContentTitle(title)
            .setContentText("From $callerName")
            .setSmallIcon(com.zaxo.app.R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(callerName.hashCode(), notification)

        Timber.d("Missed call notification shown: $callerName ($callType)")
    }

    // ═══════════════════════════════════════════════════════
    // HELPER: Check if app is in foreground
    // ═══════════════════════════════════════════════════════

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses ?: return false
        return runningProcesses.any {
            it.processName == packageName &&
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }
}
