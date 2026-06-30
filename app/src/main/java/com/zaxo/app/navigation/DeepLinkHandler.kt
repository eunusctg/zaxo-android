package com.zaxo.app.navigation

import android.content.Intent
import android.net.Uri

/**
 * Sealed class representing all possible deep link destinations.
 * Each destination carries the data needed to navigate to the
 * correct screen with the right arguments.
 */
sealed class DeepLinkDestination {
    data class Chat(val chatId: String) : DeepLinkDestination()
    data class Call(
        val callId: String,
        val roomId: String,
        val callerName: String,
        val callType: String
    ) : DeepLinkDestination()
    data class Status(val contactId: String) : DeepLinkDestination()
    data class MessageInChat(val chatId: String, val messageId: String) : DeepLinkDestination()
    object MainScreen : DeepLinkDestination()
}

/**
 * Deep link handler that parses FCM data payloads and URI deep links
 * into navigation destinations. This enables:
 * - Tapping a message notification → opens the chat
 * - Tapping a call notification → opens the call screen
 * - Tapping a status notification → opens status viewer
 * - URL deep links (zaxo://chat/123) → navigates to screen
 */
object DeepLinkHandler {

    /**
     * Parse FCM data payload into a deep link destination.
     * Called from ZaxoMessagingService when a notification is received.
     */
    fun parseFcmData(data: Map<String, String>): DeepLinkDestination {
        return when (data["type"]) {
            "new_message" -> {
                val chatId = data["chatId"] ?: return DeepLinkDestination.MainScreen
                DeepLinkDestination.Chat(chatId)
            }
            "incoming_call", "group_call" -> {
                DeepLinkDestination.Call(
                    callId = data["callId"] ?: "",
                    roomId = data["roomId"] ?: "",
                    callerName = data["callerName"] ?: "Unknown",
                    callType = data["callType"] ?: "audio"
                )
            }
            "status_update" -> {
                val contactId = data["contactId"] ?: return DeepLinkDestination.MainScreen
                DeepLinkDestination.Status(contactId)
            }
            "call_answered_elsewhere" -> {
                DeepLinkDestination.MainScreen
            }
            else -> DeepLinkDestination.MainScreen
        }
    }

    /**
     * Parse a URI deep link into a navigation destination.
     * Supports: zaxo://chat/{chatId}, zaxo://call?callId=X&roomId=Y,
     *           zaxo://status/{contactId}
     */
    fun parseUri(uri: Uri): DeepLinkDestination {
        return when (uri.host) {
            "chat" -> DeepLinkDestination.Chat(uri.lastPathSegment ?: "")
            "call" -> DeepLinkDestination.Call(
                callId = uri.getQueryParameter("callId") ?: "",
                roomId = uri.getQueryParameter("roomId") ?: "",
                callerName = uri.getQueryParameter("callerName") ?: "Unknown",
                callType = uri.getQueryParameter("callType") ?: "audio"
            )
            "status" -> DeepLinkDestination.Status(uri.lastPathSegment ?: "")
            else -> DeepLinkDestination.MainScreen
        }
    }

    /**
     * Parse intent extras for deep link data set by FCM notifications.
     */
    fun parseIntent(intent: Intent): DeepLinkDestination {
        // Check for JSON deep link data
        val deepLinkJson = intent.getStringExtra("deepLink")
        if (deepLinkJson != null) {
            // Parse simple JSON-like format: {"type":"new_message","chatId":"abc123"}
            return parseJsonDeepLink(deepLinkJson)
        }

        // Check for direct navigation target
        val navigateTo = intent.getStringExtra("navigateTo")
        if (navigateTo != null) {
            return when (navigateTo) {
                "calls_tab" -> DeepLinkDestination.MainScreen
                "status" -> DeepLinkDestination.MainScreen
                "active_call" -> DeepLinkDestination.MainScreen
                else -> DeepLinkDestination.MainScreen
            }
        }

        // Check for URI data
        val data = intent.data
        if (data != null) {
            return parseUri(data)
        }

        return DeepLinkDestination.MainScreen
    }

    private fun parseJsonDeepLink(json: String): DeepLinkDestination {
        // Simple parser for key-value pairs in format: {"type":"X","chatId":"Y"}
        val pairs = json.trimStart('{').trimEnd('}').split(",")
        val map = mutableMapOf<String, String>()
        for (pair in pairs) {
            val kv = pair.split(":", limit = 2)
            if (kv.size == 2) {
                val key = kv[0].trim().trim('"')
                val value = kv[1].trim().trim('"')
                map[key] = value
            }
        }
        return parseFcmData(map)
    }
}
