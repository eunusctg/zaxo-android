package com.zaxo.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Centralized notification channel definitions for the ZAXO app.
 * All channels are created on app startup to ensure notifications
 * are delivered correctly on Android 8.0+ (API 26+).
 *
 * Channel priorities:
 * - Messages: HIGH — sound + vibration + heads-up
 * - Calls: HIGH + bypass DND — always alert for incoming calls
 * - Status: DEFAULT — subtle alerts for status updates
 * - Background: LOW — ongoing call + sync indicators
 */
object NotificationChannels {

    const val CHANNEL_MESSAGES = "zaxo_messages"
    const val CHANNEL_CALLS = "zaxo_calls"
    const val CHANNEL_MISSED_CALLS = "zaxo_missed_calls"
    const val CHANNEL_STATUS = "zaxo_status"
    const val CHANNEL_BACKGROUND = "zaxo_background"

    /**
     * Create all notification channels. Call this from Application.onCreate().
     * Safe to call multiple times — existing channels are not modified.
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)

        // Messages channel — high priority for new messages
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New message notifications"
                enableVibration(true)
                setShowBadge(true)
            }
        )

        // Calls channel — high priority with DND bypass for incoming calls
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CALLS,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming call notifications"
                enableVibration(true)
                setBypassDnd(true)
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )

        // Missed calls channel
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MISSED_CALLS,
                "Missed Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Missed call notifications"
                enableVibration(true)
                setShowBadge(true)
            }
        )

        // Status channel — default priority for status updates
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                "Status Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Status update notifications from contacts"
                setShowBadge(true)
            }
        )

        // Background service channel — low priority for ongoing operations
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BACKGROUND,
                "Background Services",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing call and sync notifications"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
    }
}
