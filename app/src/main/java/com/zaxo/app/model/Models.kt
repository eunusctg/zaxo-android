package com.zaxo.app.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey
import kotlinx.parcelize.Parcelize

// ==================== User ====================
@Parcelize
data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val photoUrl: String = "",
    val about: String = "",
    val lastSeen: Long = 0L,
    val isOnline: Boolean = false,
    val zaxoNumber: String = "",
    val readReceiptsEnabled: Boolean = true
) : Parcelable

// ==================== Chat ====================
@Entity(
    tableName = "chats",
    indices = [Index(value = ["recipientId"])]
)
@Parcelize
data class Chat(
    @PrimaryKey val id: String = "",
    val name: String = "",
    val photoUrl: String = "",
    val recipientId: String = "",
    val isGroup: Boolean = false,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val lastMessageSender: String = "",
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val isTyping: Boolean = false,
    val groupDescription: String = "",
    val createdBy: String = "",
    val adminIds: String = "",
    val memberIds: String = "",
    val encryptionSessionId: String = "",
    val wallpaperUrl: String = ""
) : Parcelable

// ==================== Message ====================
@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = Chat::class,
        parentColumns = ["id"],
        childColumns = ["chatId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["chatId", "timestamp"]),
        Index(value = ["content"])
    ]
)
@Parcelize
data class Message(
    @PrimaryKey val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String = "",
    val content: String = "",
    val type: MessageType = MessageType.TEXT,
    val mediaUrl: String = "",
    val timestamp: Long = 0L,
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,
    val isStarred: Boolean = false,
    val replyToId: String = "",
    val replyToContent: String = "",
    val replyToSender: String = "",
    val forwardedFrom: String = "",
    val isForwarded: Boolean = false,
    val reactions: String = "",
    val isDeleted: Boolean = false,
    val isEncrypted: Boolean = false,
    val status: MessageStatus = MessageStatus.SENDING,
    val mediaDuration: Long = 0L,
    val waveform: String = "",
    val syncState: String = "synced" // "synced" | "pending" | "failed" (F8 offline queue)
) : Parcelable

enum class MessageType(val value: String) {
    TEXT("text"),
    IMAGE("image"),
    VIDEO("video"),
    AUDIO("audio"),
    VOICE("voice"),
    DOCUMENT("document"),
    LOCATION("location"),
    CONTACT("contact"),
    SYSTEM("system")
}

enum class MessageStatus(val value: String) {
    SENDING("sending"),
    SENT("sent"),
    DELIVERED("delivered"),
    READ("read"),
    FAILED("failed")
}

// ==================== Status/Stories ====================
@Entity(
    tableName = "statuses",
    indices = [Index(value = ["userId"]), Index(value = ["expiresAt"])]
)
@Parcelize
data class Status(
    @PrimaryKey val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String = "",
    val type: StatusType = StatusType.PHOTO,
    val mediaUrl: String = "",
    val textContent: String = "",
    val backgroundColor: String = "#4A90D9",
    val fontFamily: String = "default",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val isViewed: Boolean = false,
    val syncState: String = "synced" // "synced" | "pending" | "failed" (F29 upload retry)
) : Parcelable

enum class StatusType(val value: String) {
    PHOTO("photo"),
    VIDEO("video"),
    TEXT("text")
}

@Entity(
    tableName = "status_views",
    indices = [Index(value = ["statusId"]), Index(value = ["viewerId"])]
)
@Parcelize
data class StatusView(
    @PrimaryKey val id: String = "",
    val statusId: String = "",
    val viewerId: String = "",
    val viewerName: String = "",
    val viewerPhotoUrl: String = "",
    val viewedAt: Long = 0L
) : Parcelable

// ==================== Search Models ====================
data class SearchResult(
    val message: Message,
    val chatName: String,
    val chatPhotoUrl: String
)

data class ChatSearchGroup(
    val chatId: String,
    val chatName: String,
    val chatPhotoUrl: String,
    val messages: List<Message>
)

// ==================== Navigation ====================
enum class Screen(val route: String) {
    Splash("splash"),
    Onboarding("onboarding"),
    Main("main"),
    ChatRoom("chat_room/{chatId}"),
    ChatInfo("chat_info/{chatId}"),
    Forward("forward/{messageId}/{chatId}"),
    ArchivedChats("archived_chats"),
    ProfileEdit("profile_edit"),
    Search("search"),
    GroupAdmin("group_admin/{chatId}"),
    MediaViewer("media_viewer/{chatId}/{messageId}"),
    WallpaperPicker("wallpaper_picker/{chatId}"),
    Status("status"),
    StatusViewer("status_viewer/{userId}"),
    StatusCamera("status_camera"),
    StatusTextComposer("status_text_composer"),
    StatusEditor("status_editor/{mediaUri}"),
    ContactPicker("contact_picker"),
    ForwardPicker("forward_picker/{messageId}"),
    StarredMessages("starred_messages"),
    BlockedContacts("blocked_contacts"),
    NotificationSettings("notification_settings"),
    QuickResponses("quick_responses"),
    Dialpad("dialpad"),
    OutgoingCall("outgoing_call/{callId}"),
    IncomingCall("incoming_call/{callId}"),
    ActiveCall("active_call/{callId}"),
    GroupCall("group_call/{callId}"),
    PostCall("post_call/{callId}"),
    CallWaiting("call_waiting/{callId}");

    fun withArgs(vararg args: String): String {
        var route = this.route
        args.forEach { arg ->
            route = route.replaceFirst(Regex("\\{[^}]+\\}"), arg)
        }
        return route
    }
}

// ==================== Status Privacy ====================
enum class StatusPrivacy(val value: String) {
    MY_CONTACTS("my_contacts"),
    MY_CONTACTS_EXCEPT("my_contacts_except"),
    ONLY_SHARE_WITH("only_share_with")
}

// ==================== Muted Status ====================
@Entity(
    tableName = "muted_statuses",
    indices = [Index(value = ["mutedUserId"], unique = true)]
)
@Parcelize
data class MutedStatus(
    @PrimaryKey val id: String = "",
    val mutedUserId: String = "",
    val mutedUserName: String = "",
    val mutedAt: Long = 0L
) : Parcelable

// ==================== Status Reply ====================
data class StatusReply(
    val statusId: String,
    val statusAuthorId: String,
    val statusAuthorName: String,
    val statusThumbnail: String,
    val replyText: String,
    val timestamp: Long = System.currentTimeMillis()
)

// ==================== Quick Reaction ====================
data class QuickReaction(
    val emoji: String,
    val senderIds: List<String> = emptyList()
)

val DEFAULT_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

// ==================== Wallpaper Constants ====================
// ==================== Status Timer Durations ====================
object StatusTimerOptions {
    val PHOTO_DEFAULT = 5_000L
    val OPTIONS = listOf(5_000L, 10_000L, 15_000L, 30_000L)
    val LABELS = listOf("5s", "10s", "15s", "30s")
}

// ==================== Drawing Path ====================
data class DrawingPath(
    val points: List<androidx.compose.ui.geometry.Offset> = emptyList(),
    val color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White,
    val strokeWidth: Float = 4f
)

object BuiltInWallpapers {
    val DARK_WALLPAPERS = listOf(
        "builtin_dark_dots",
        "builtin_dark_waves",
        "builtin_dark_geometric",
        "builtin_dark_gradient",
        "builtin_dark_stars",
        "builtin_dark_abstract"
    )
    val LIGHT_WALLPAPERS = listOf(
        "builtin_light_dots",
        "builtin_light_waves",
        "builtin_light_geometric",
        "builtin_light_gradient",
        "builtin_light_floral",
        "builtin_light_abstract"
    )
    val ALL = DARK_WALLPAPERS + LIGHT_WALLPAPERS
}

// ==================== Blocked Caller ====================
@Entity(
    tableName = "blocked_callers",
    indices = [Index(value = ["blockedUserId"], unique = true)]
)
@Parcelize
data class BlockedCaller(
    @PrimaryKey val id: String = "",
    val blockedUserId: String = "",
    val blockedUserName: String = "",
    val blockedUserZaxoNumber: String = "",
    val blockedAt: Long = 0L
) : Parcelable

// ==================== Presence State ====================
data class PresenceState(
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L
)

// ==================== Chat Notification Settings ====================
@Entity(
    tableName = "chat_notification_settings",
    indices = [Index(value = ["chatId"], unique = true)]
)
@Parcelize
data class ChatNotificationSettings(
    @PrimaryKey val chatId: String = "",
    val isMuted: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundUri: String = ""
) : Parcelable

// ==================== Call History ====================
@Entity(tableName = "call_history", indices = [Index(value = ["contactId"]), Index(value = ["timestamp"])])
@Parcelize
data class CallRecord(
    @PrimaryKey val id: String = "",
    val contactId: String = "",
    val contactName: String = "",
    val contactPhotoUrl: String = "",
    val callType: CallType = CallType.OUTGOING,
    val mediaType: String = "audio",
    val timestamp: Long = 0L,
    val duration: Long = 0L,
    val isGroupCall: Boolean = false,
    val groupId: String = "",
    val groupName: String = "",
    val roomId: String = "",
    val cachedName: String = ""
) : Parcelable

enum class CallType(val value: String) {
    INCOMING("incoming"),
    OUTGOING("outgoing"),
    MISSED("missed"),
    DECLINED("declined"),
    BUSY("busy"),
    FAILED("failed")
}

// ==================== Call State Machine (28 States) ====================
enum class CallState {
    IDLE,
    VALIDATING, CREATING_ROOM, SENDING_PUSH,
    DIALING, RINGING, CONNECTING, ACTIVE, RECONNECTING,
    INCOMING, CALL_WAITING, HELD, ENDED,
    CALL_FAILED, USER_OFFLINE, LINE_BUSY, NO_ANSWER,
    CALL_DECLINED, CALL_CANCELLED, PRIVACY_BLOCKED,
    ANSWERED_ELSEWHERE, POST_CALL,
    GROUP_CREATING, GROUP_RINGING, GROUP_ACTIVE,
    GROUP_PARTICIPANT_JOINED, GROUP_PARTICIPANT_LEFT
}

// ==================== Call Media Type ====================
enum class CallMediaType(val value: String) {
    AUDIO("audio"),
    VIDEO("video")
}

// ==================== Call Session ====================
data class CallSession(
    val callId: String = "",
    val roomId: String = "",
    val callerUid: String = "",
    val callerName: String = "",
    val callerPhotoUrl: String = "",
    val callerZaxoNumber: String = "",
    val calleeUid: String = "",
    val calleeName: String = "",
    val calleePhotoUrl: String = "",
    val calleeZaxoNumber: String = "",
    val mediaType: CallMediaType = CallMediaType.AUDIO,
    val isGroupCall: Boolean = false,
    val groupId: String = "",
    val groupName: String = "",
    val state: CallState = CallState.IDLE,
    val startedAt: Long = 0L,
    val connectTimestamp: Long = 0L,
    val isMuted: Boolean = false,
    val isVideoOn: Boolean = true,
    val isSpeakerOn: Boolean = false,
    val isBluetoothOn: Boolean = false,
    val isOnHold: Boolean = false,
    val isFrontCamera: Boolean = true,
    val participantIds: List<String> = emptyList(),
    val activeSpeakerId: String = ""
)

// ==================== Pre-Flight Check Result ====================
sealed class PreFlightResult {
    object OK : PreFlightResult()
    data class Error(val message: String) : PreFlightResult()
    data class ConfirmEndCurrent(val activeCall: CallSession) : PreFlightResult()
    data class RequestPermission(val permission: String) : PreFlightResult()
    object OfferAudioOnly : PreFlightResult()
}

// ==================== Privacy Gate Result ====================
sealed class GateResult {
    object ALLOWED : GateResult()
    object REJECT : GateResult()
    object SILENT_REJECT : GateResult()
}

// ==================== Ring Behavior ====================
enum class RingBehavior {
    FULL, VIBRATE_ONLY, SILENT
}

// ==================== Zaxo Number Lookup Result ====================
sealed class LookupResult {
    data class Found(val uid: String, val displayName: String, val photoUrl: String, val zaxoNumber: String) : LookupResult()
    object NotFound : LookupResult()
    object Hidden : LookupResult()
}
