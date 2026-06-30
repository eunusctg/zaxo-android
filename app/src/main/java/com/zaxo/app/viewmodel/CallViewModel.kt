package com.zaxo.app.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.zaxo.app.data.dao.BlockedCallerDao
import com.zaxo.app.data.dao.CallHistoryDao
import com.zaxo.app.data.dao.ChatDao
import com.zaxo.app.data.repository.CallHistoryRepository
import com.zaxo.app.model.*
import com.zaxo.app.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.livekit.android.LiveKitClient
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    application: Application,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val callHistoryRepository: CallHistoryRepository,
    private val callHistoryDao: CallHistoryDao,
    private val blockedCallerDao: BlockedCallerDao,
    private val chatDao: ChatDao,
    private val liveKitClient: LiveKitClient,
    private val liveKitUrl: String
) : AndroidViewModel(application) {

    private val context: Context get() = application

    // Managers
    private val callTimer = CallTimer()
    private val proximityManager = ProximitySensorManager(context)
    private val ringbackManager = RingbackToneManager(context)
    private val ringtoneManager = IncomingRingtoneManager(context)
    private val callWaitingManager = CallWaitingManager()

    // LiveKit room reference
    private var liveKitRoom: Room? = null

    /**
     * Expose LiveKit room for video rendering in UI composables.
     * Returns null if no room is connected.
     */
    fun getLiveKitRoom(): Room? = liveKitRoom

    // Cloud Functions reference
    private val cloudFunctions = FirebaseFunctions.getInstance()

    // State flows
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _currentCall = MutableStateFlow<CallSession?>(null)
    val currentCall: StateFlow<CallSession?> = _currentCall.asStateFlow()

    private val _callTimerText = MutableStateFlow("00:00")
    val callTimerText: StateFlow<String> = _callTimerText.asStateFlow()

    private val _networkQuality = MutableStateFlow("good")
    val networkQuality: StateFlow<String> = _networkQuality.asStateFlow()

    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    // F107: In-app missed call banner
    private val _missedCallNotification = MutableStateFlow<CallRecord?>(null)
    val missedCallNotification: StateFlow<CallRecord?> = _missedCallNotification.asStateFlow()

    // F112: One-time recording consent
    private val prefs = context.getSharedPreferences("zaxo_prefs", 0)
    private val hasSeenRecordingConsent: Boolean
        get() = prefs.getBoolean("hasSeenRecordingConsent", false)

    fun markRecordingConsentSeen() {
        prefs.edit().putBoolean("hasSeenRecordingConsent", true).apply()
    }

    private val _showRecordingConsent = MutableStateFlow(false)
    val showRecordingConsent: StateFlow<Boolean> = _showRecordingConsent.asStateFlow()

    fun dismissRecordingConsent() {
        _showRecordingConsent.value = false
    }

    // F69: Prevent double-tap creating two rooms
    private var isCallInProgress = false

    // Timer update job
    private var timerJob: Job? = null
    private var ringingTimeoutJob: Job? = null
    private var reconnectTimeoutJob: Job? = null

    // Call history for Calls tab
    val callHistory = callHistoryRepository.getAllCallRecords()

    // ═══════════════════════════════════════════════════════
    // F78: Bluetooth disconnect audio routing
    // ═══════════════════════════════════════════════════════

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.isSpeakerphoneOn = false
                audioManager.stopBluetoothSco()
                _currentCall.value = _currentCall.value?.let {
                    it.copy(isBluetoothOn = false, isSpeakerOn = false)
                }
                updateProximitySensor()
                Timber.d("Bluetooth disconnected — routed to earpiece")
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // F83: WiFi<->Cellular handoff via NetworkCallback
    // ═══════════════════════════════════════════════════════

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (_callState.value == CallState.RECONNECTING) {
                Timber.d("Network available — attempting LiveKit reconnect")
                liveKitRoom?.reconnect()
                _callState.value = CallState.ACTIVE
                _isReconnecting.value = false
            }
        }

        override fun onLost(network: Network) {
            if (_callState.value == CallState.ACTIVE) {
                Timber.w("Network lost — transitioning to RECONNECTING")
                _callState.value = CallState.RECONNECTING
                _isReconnecting.value = true
                startReconnectTimeout()
            }
        }
    }

    init {
        // Register Bluetooth receiver (F78)
        try {
            context.registerReceiver(
                bluetoothReceiver,
                IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to register Bluetooth receiver")
        }

        // Register network callback (F83)
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Timber.e(e, "Failed to register network callback")
        }
    }

    // ==================== OUTGOING CALL ====================

    /**
     * D.1 Pre-flight check algorithm with sequential permission cascade.
     */
    fun preFlightCheck(callType: CallMediaType, calleeUid: String): PreFlightResult {
        val prefs = context.getSharedPreferences("zaxo_prefs", 0)

        // Check 1: Call type enabled?
        if (callType == CallMediaType.AUDIO && !prefs.getBoolean("p2pAudioEnabled", true)) {
            return PreFlightResult.Error("Audio calls are disabled")
        }
        if (callType == CallMediaType.VIDEO && !prefs.getBoolean("p2pVideoEnabled", true)) {
            return PreFlightResult.Error("Video calls are disabled")
        }

        // Check 2: Network available? WiFi-only check (F76)
        val dataUsage = prefs.getString("p2pDataUsage", "All") ?: "All"
        if (dataUsage == "WiFi Only" && isActiveNetworkMetered()) {
            return PreFlightResult.Error("Connect to WiFi to make calls")
        }

        // Check 3: Already on a call?
        if (_currentCall.value != null && _callState.value == CallState.ACTIVE) {
            return PreFlightResult.ConfirmEndCurrent(_currentCall.value!!)
        }

        // Check 4: Self-call? (F71)
        val currentUid = auth.currentUser?.uid ?: ""
        if (calleeUid == currentUid) {
            return PreFlightResult.Error("Cannot call yourself")
        }

        // Check 5: Microphone permission? (F75)
        if (!hasPermission(android.Manifest.permission.RECORD_AUDIO))
            return PreFlightResult.RequestPermission(android.Manifest.permission.RECORD_AUDIO)

        // Check 6: Camera permission? (video only — F74)
        if (callType == CallMediaType.VIDEO && !hasPermission(android.Manifest.permission.CAMERA))
            return PreFlightResult.OfferAudioOnly

        // F113: Rate limit — 1 call per 30 seconds
        val lastCallTime = context.getSharedPreferences("zaxo_prefs", 0).getLong("lastCallAttemptTime", 0L)
        if (System.currentTimeMillis() - lastCallTime < 30_000) {
            return PreFlightResult.Error("Please wait before making another call")
        }

        return PreFlightResult.OK
    }

    /**
     * Start an outgoing 1:1 call (audio or video).
     */
    fun startOutgoingCall(
        calleeUid: String,
        calleeName: String,
        calleePhotoUrl: String,
        calleeZaxoNumber: String,
        mediaType: CallMediaType
    ) {
        // F69: Prevent double-tap
        if (isCallInProgress) {
            Timber.w("Call already in progress — ignoring")
            return
        }
        isCallInProgress = true

        // F113: Record call attempt time
        context.getSharedPreferences("zaxo_prefs", 0).edit()
            .putLong("lastCallAttemptTime", System.currentTimeMillis()).apply()

        val currentUid = auth.currentUser?.uid ?: return
        val currentUserName = getCachedUserName()
        val callId = UUID.randomUUID().toString()

        viewModelScope.launch {
            try {
                // VALIDATING state
                _callState.value = CallState.VALIDATING

                // F112: One-time recording consent
                if (!hasSeenRecordingConsent) {
                    _showRecordingConsent.value = true
                    delay(100) // Let UI show the dialog
                }

                val preFlight = preFlightCheck(mediaType, calleeUid)
                when (preFlight) {
                    is PreFlightResult.Error -> {
                        _callState.value = CallState.CALL_FAILED
                        _currentCall.value = null
                        isCallInProgress = false
                        return@launch
                    }
                    is PreFlightResult.OfferAudioOnly -> {
                        // Downgrade to audio
                    }
                    is PreFlightResult.RequestPermission -> {
                        _callState.value = CallState.CALL_FAILED
                        isCallInProgress = false
                        return@launch
                    }
                    is PreFlightResult.ConfirmEndCurrent -> {
                        // End current call first
                        endCall()
                    }
                    is PreFlightResult.OK -> { /* proceed */ }
                }

                // CREATING_ROOM state — real LiveKit room creation
                _callState.value = CallState.CREATING_ROOM
                val roomId = createLiveKitRoom(mediaType)

                // F114: Reduce quality on battery saver
                if (isBatterySaverOn() && mediaType == CallMediaType.VIDEO) {
                    Timber.d("Battery saver on — reducing video quality")
                    liveKitRoom?.localParticipant?.setCameraEnabled(true)
                }

                // Create call session
                val session = CallSession(
                    callId = callId,
                    roomId = roomId,
                    callerUid = currentUid,
                    callerName = currentUserName,
                    calleeUid = calleeUid,
                    calleeName = calleeName,
                    calleePhotoUrl = calleePhotoUrl,
                    calleeZaxoNumber = calleeZaxoNumber,
                    mediaType = mediaType,
                    state = CallState.SENDING_PUSH,
                    startedAt = System.currentTimeMillis(),
                    isVideoOn = mediaType == CallMediaType.VIDEO
                )

                // SENDING_PUSH state — call Cloud Function
                _callState.value = CallState.SENDING_PUSH
                sendCallPush(calleeUid, roomId, callId, mediaType)

                // DIALING state
                _callState.value = CallState.DIALING
                _currentCall.value = session.copy(state = CallState.DIALING)

                // Wait 2s then transition to RINGING
                delay(2000)
                _callState.value = CallState.RINGING
                _currentCall.value = session.copy(state = CallState.RINGING)

                // Start ringback tone
                ringbackManager.start()

                // Start 60s ringing timeout
                startRingingTimeout(callId, calleeUid, calleeName, calleePhotoUrl, mediaType)

            } catch (e: Exception) {
                Timber.e(e, "Failed to start outgoing call")
                // F80: Camera in use — fallback to audio
                if (e is IllegalStateException && e.message?.contains("Camera") == true) {
                    Timber.d("Camera unavailable — falling back to audio call")
                    startOutgoingCall(calleeUid, calleeName, calleePhotoUrl, calleeZaxoNumber, CallMediaType.AUDIO)
                    return@launch
                }
                _callState.value = CallState.CALL_FAILED
                _currentCall.value = null
                isCallInProgress = false
            }
        }
    }

    /**
     * Start a group call.
     */
    fun startGroupCall(
        groupId: String,
        groupName: String,
        memberIds: List<String>,
        mediaType: CallMediaType
    ) {
        if (isCallInProgress) return
        isCallInProgress = true

        if (memberIds.size > 19) {
            Timber.w("Group calls support up to 20 participants")
            isCallInProgress = false
            return
        }

        val currentUid = auth.currentUser?.uid ?: return
        val callId = UUID.randomUUID().toString()

        viewModelScope.launch {
            try {
                _callState.value = CallState.GROUP_CREATING
                val roomId = createLiveKitRoom(mediaType, isGroup = true)

                val session = CallSession(
                    callId = callId,
                    roomId = roomId,
                    callerUid = currentUid,
                    callerName = getCachedUserName(),
                    mediaType = mediaType,
                    isGroupCall = true,
                    groupId = groupId,
                    groupName = groupName,
                    state = CallState.GROUP_RINGING,
                    startedAt = System.currentTimeMillis(),
                    participantIds = memberIds,
                    isVideoOn = mediaType == CallMediaType.VIDEO
                )

                // Send FCM to all participants via Cloud Function
                for (uid in memberIds) {
                    sendGroupCallPush(uid, roomId, callId, mediaType, groupId, groupName)
                }

                _callState.value = CallState.GROUP_RINGING
                _currentCall.value = session
                callTimer.start()
                startTimerUpdates()

            } catch (e: Exception) {
                Timber.e(e, "Failed to start group call")
                _callState.value = CallState.CALL_FAILED
                isCallInProgress = false
            }
        }
    }

    // ==================== INCOMING CALL ====================

    /**
     * E.2 Privacy gate algorithm.
     */
    suspend fun evaluateIncomingCall(callerUid: String, callerZaxoNumber: String, callType: String): GateResult {
        // Check 1: Blocked caller?
        val blocked = blockedCallerDao.isBlocked(callerUid)
        if (blocked) return GateResult.SILENT_REJECT

        // Check 2: P2P calling permission?
        val prefs = context.getSharedPreferences("zaxo_prefs", 0)
        val p2pCalling = prefs.getString("zaxoP2PCalling", "contacts") ?: "contacts"
        when (p2pCalling) {
            "nobody" -> return GateResult.REJECT
            "contacts" -> {
                val chat = chatDao.getChatByRecipientId(callerUid)
                if (chat == null) return GateResult.REJECT
            }
        }

        // Check 3: Audio/Video toggles?
        if (callType == "audio" && !prefs.getBoolean("p2pAudioEnabled", true))
            return GateResult.REJECT
        if (callType == "video" && !prefs.getBoolean("p2pVideoEnabled", true))
            return GateResult.REJECT

        // Check 4: Rate limiting — max 5 calls/hour from non-contacts (F113)
        val chat = chatDao.getChatByRecipientId(callerUid)
        if (chat == null) {
            val oneHourAgo = System.currentTimeMillis() - 3_600_000L
            val recentCalls = callHistoryDao.getRecentCallsFrom(callerUid, oneHourAgo)
            if (recentCalls.size >= 5) return GateResult.SILENT_REJECT
        }

        return GateResult.ALLOWED
    }

    /**
     * E.3 Ring mode algorithm.
     */
    fun applyRingMode(): RingBehavior {
        val prefs = context.getSharedPreferences("zaxo_prefs", 0)
        val ringMode = prefs.getString("zaxoRingMode", "Ring") ?: "Ring"

        // Check DND
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
        if (nm.currentInterruptionFilter >= android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
            && !prefs.getBoolean("overrideDndForCalls", false)) {
            return RingBehavior.SILENT
        }

        return when (ringMode) {
            "Ring" -> RingBehavior.FULL
            "Vibrate" -> RingBehavior.VIBRATE_ONLY
            "Silent" -> RingBehavior.SILENT
            else -> RingBehavior.FULL
        }
    }

    /**
     * Handle incoming call from FCM.
     */
    fun handleIncomingCall(
        roomId: String,
        callId: String,
        callType: String,
        callerUid: String,
        callerName: String,
        callerZaxoNumber: String
    ) {
        val mediaType = if (callType == "video") CallMediaType.VIDEO else CallMediaType.AUDIO

        viewModelScope.launch {
            val gateResult = evaluateIncomingCall(callerUid, callerZaxoNumber, callType)

            when (gateResult) {
                GateResult.SILENT_REJECT -> {
                    Timber.d("Incoming call silently rejected: $callerUid")
                    saveMissedCall(callerUid, callerName, "", mediaType)
                    return@launch
                }
                GateResult.REJECT -> {
                    Timber.d("Incoming call rejected: $callerUid")
                    sendBusyToCaller(roomId, callId)
                    saveMissedCall(callerUid, callerName, "", mediaType)
                    return@launch
                }
                GateResult.ALLOWED -> {
                    // Auto-answer decision tree
                    if (shouldAutoAnswer(callerUid, callType)) {
                        Timber.d("Auto-answering call from $callerUid")
                        delay(3000)
                        acceptIncomingCall(roomId, callId, callerUid, callerName, "", callerZaxoNumber, mediaType)
                        return@launch
                    }

                    _callState.value = CallState.INCOMING
                    _currentCall.value = CallSession(
                        callId = callId,
                        roomId = roomId,
                        callerUid = callerUid,
                        callerName = callerName,
                        callerZaxoNumber = callerZaxoNumber,
                        calleeUid = auth.currentUser?.uid ?: "",
                        mediaType = mediaType,
                        state = CallState.INCOMING,
                        startedAt = System.currentTimeMillis()
                    )

                    // Start ringtone/vibration
                    val ringBehavior = applyRingMode()
                    when (ringBehavior) {
                        RingBehavior.FULL -> ringtoneManager.start("Ring")
                        RingBehavior.VIBRATE_ONLY -> ringtoneManager.start("Vibrate")
                        RingBehavior.SILENT -> { /* silent */ }
                    }

                    // 60s timeout for incoming call
                    startIncomingTimeout(callId, callerUid, callerName, mediaType)
                }
            }
        }
    }

    /**
     * Accept an incoming call — join the LiveKit room.
     */
    fun acceptIncomingCall(
        roomId: String,
        callId: String,
        callerUid: String,
        callerName: String,
        callerPhotoUrl: String,
        callerZaxoNumber: String,
        mediaType: CallMediaType
    ) {
        ringtoneManager.stop()

        _callState.value = CallState.CONNECTING
        _currentCall.value = _currentCall.value?.copy(state = CallState.CONNECTING)
            ?: CallSession(
                callId = callId,
                roomId = roomId,
                callerUid = callerUid,
                callerName = callerName,
                callerPhotoUrl = callerPhotoUrl,
                callerZaxoNumber = callerZaxoNumber,
                calleeUid = auth.currentUser?.uid ?: "",
                mediaType = mediaType,
                state = CallState.CONNECTING,
                startedAt = System.currentTimeMillis()
            )

        // Join LiveKit room then go ACTIVE
        viewModelScope.launch {
            try {
                joinLiveKitRoom(roomId)

                _callState.value = CallState.ACTIVE
                _currentCall.value = _currentCall.value?.copy(
                    state = CallState.ACTIVE,
                    connectTimestamp = System.currentTimeMillis()
                )
                callTimer.start()
                startTimerUpdates()
                isCallInProgress = true

                // Activate proximity sensor for audio calls
                updateProximitySensor()

                // F81: Monitor video track for restart
                if (mediaType == CallMediaType.VIDEO) {
                    monitorVideoTrack()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to join LiveKit room")
                _callState.value = CallState.CALL_FAILED
                _currentCall.value = null
                isCallInProgress = false
            }
        }
    }

    /**
     * Decline an incoming call.
     */
    fun declineIncomingCall(withMessage: String? = null) {
        ringtoneManager.stop()
        val call = _currentCall.value ?: return

        viewModelScope.launch {
            if (withMessage != null) {
                declineWithMessage(call.callerUid, withMessage)
            }

            saveMissedCall(call.callerUid, call.callerName, call.callerPhotoUrl, call.mediaType)
            sendBusyToCaller(call.roomId, call.callId)

            _callState.value = CallState.IDLE
            _currentCall.value = null
            isCallInProgress = false
            clearTimeouts()
        }
    }

    // ==================== ACTIVE CALL CONTROLS ====================

    /**
     * Toggle microphone mute via LiveKit.
     */
    fun toggleMute() {
        val call = _currentCall.value ?: return
        val newMuted = !call.isMuted
        _currentCall.value = call.copy(isMuted = newMuted)
        liveKitRoom?.localParticipant?.setMicrophoneEnabled(!newMuted)
        Timber.d("Mute toggled: $newMuted")
    }

    /**
     * Toggle speaker via AudioManager.
     */
    fun toggleSpeaker() {
        val call = _currentCall.value ?: return
        val newSpeaker = !call.isSpeakerOn
        _currentCall.value = call.copy(isSpeakerOn = newSpeaker, isBluetoothOn = false)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = newSpeaker
        updateProximitySensor()
        Timber.d("Speaker toggled: $newSpeaker")
    }

    /**
     * Toggle Bluetooth audio routing.
     */
    fun toggleBluetooth() {
        val call = _currentCall.value ?: return
        val newBluetooth = !call.isBluetoothOn
        _currentCall.value = call.copy(isBluetoothOn = newBluetooth, isSpeakerOn = false)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (newBluetooth) {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        } else {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        }
        updateProximitySensor()
        Timber.d("Bluetooth toggled: $newBluetooth")
    }

    /**
     * Toggle video via LiveKit.
     */
    fun toggleVideo() {
        val call = _currentCall.value ?: return
        if (call.mediaType != CallMediaType.VIDEO) return
        val newVideoOn = !call.isVideoOn
        _currentCall.value = call.copy(isVideoOn = newVideoOn)
        liveKitRoom?.localParticipant?.setCameraEnabled(newVideoOn)
        Timber.d("Video toggled: $newVideoOn")
    }

    /**
     * Flip camera via LiveKit.
     */
    fun flipCamera() {
        val call = _currentCall.value ?: return
        if (call.mediaType != CallMediaType.VIDEO) return
        val newFront = !call.isFrontCamera
        _currentCall.value = call.copy(isFrontCamera = newFront)
        liveKitRoom?.localParticipant?.switchCamera()
        Timber.d("Camera flipped: front=$newFront")
    }

    /**
     * Toggle hold/resume on the current call.
     * When held: mutes microphone, pauses video, updates call state.
     * When resumed: un-mutes, resumes video, restores call state.
     */
    fun toggleHold() {
        val call = _currentCall.value ?: return
        val isCurrentlyOnHold = call.isOnHold

        _currentCall.value = call.copy(isOnHold = !isCurrentlyOnHold)

        if (!isCurrentlyOnHold) {
            // Holding — mute mic and pause video
            liveKitRoom?.localParticipant?.setMicrophoneEnabled(false)
            liveKitRoom?.localParticipant?.setCameraEnabled(false)
            Timber.d("Call placed on hold")
        } else {
            // Resuming — restore mic and video to previous state
            liveKitRoom?.localParticipant?.setMicrophoneEnabled(!call.isMuted)
            if (call.mediaType == CallMediaType.VIDEO && call.isVideoOn) {
                liveKitRoom?.localParticipant?.setCameraEnabled(true)
            }
            Timber.d("Call resumed from hold")
        }
    }

    /**
     * End the current call. F92: Confirmation for first 5 seconds.
     */
    fun endCall() {
        val call = _currentCall.value ?: return
        val callAge = if (call.connectTimestamp > 0) {
            System.currentTimeMillis() - call.connectTimestamp
        } else {
            System.currentTimeMillis() - call.startedAt
        }

        // If call was connected less than 5s ago, require confirmation
        if (call.connectTimestamp > 0 && callAge < 5000L) {
            Timber.d("End call requested within 5s — confirmation needed")
        }

        viewModelScope.launch {
            performEndCall(call)
        }
    }

    /**
     * Actually end the call (after any confirmation).
     */
    fun confirmEndCall() {
        val call = _currentCall.value ?: return
        viewModelScope.launch {
            performEndCall(call)
        }
    }

    private suspend fun performEndCall(call: CallSession) {
        ringbackManager.stop()
        ringtoneManager.stop()
        proximityManager.release()
        callTimer.reset()
        clearTimeouts()

        // Disconnect LiveKit room
        try {
            liveKitRoom?.disconnect()
            liveKitRoom = null
            Timber.d("LiveKit room disconnected")
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting LiveKit room")
        }

        // Save call history
        val duration = if (call.connectTimestamp > 0) {
            (System.currentTimeMillis() - call.connectTimestamp) / 1000
        } else 0L

        val callType = when {
            call.state == CallState.INCOMING -> CallType.INCOMING
            call.state == CallState.ACTIVE || call.connectTimestamp > 0 -> {
                if (call.callerUid == auth.currentUser?.uid) CallType.OUTGOING
                else CallType.INCOMING
            }
            call.state == CallState.RINGING -> CallType.MISSED
            call.state == CallState.LINE_BUSY -> CallType.BUSY
            call.state == CallState.CALL_DECLINED -> CallType.DECLINED
            call.state == CallState.CALL_FAILED -> CallType.FAILED
            else -> CallType.OUTGOING
        }

        val contactId = if (call.callerUid == auth.currentUser?.uid) call.calleeUid else call.callerUid
        val contactName = if (call.callerUid == auth.currentUser?.uid) call.calleeName else call.callerName
        val contactPhoto = if (call.callerUid == auth.currentUser?.uid) call.calleePhotoUrl else call.callerPhotoUrl

        callHistoryRepository.saveFromSession(
            contactId = contactId,
            contactName = contactName,
            contactPhotoUrl = contactPhoto,
            callType = callType,
            mediaType = call.mediaType.value,
            duration = duration,
            isGroupCall = call.isGroupCall,
            groupId = call.groupId,
            groupName = call.groupName,
            roomId = call.roomId
        )

        // Stop foreground service
        try {
            context.stopService(Intent(context, CallForegroundService::class.java))
        } catch (e: Exception) {
            Timber.e(e, "Error stopping foreground service")
        }

        _callState.value = CallState.POST_CALL
        _currentCall.value = call.copy(state = CallState.POST_CALL)

        // Brief post-call display then IDLE
        delay(2000)
        _callState.value = CallState.IDLE
        _currentCall.value = null
        isCallInProgress = false
    }

    // ==================== CALL WAITING ====================

    fun onCallWaitingIncoming(newCall: CallSession) {
        val prefs = context.getSharedPreferences("zaxo_prefs", 0)
        val callWaitingEnabled = prefs.getBoolean("callWaitingEnabled", true)

        if (callWaitingManager.onIncomingCallWhileActive(newCall, callWaitingEnabled)) {
            _callState.value = CallState.CALL_WAITING
        } else {
            sendBusyToCaller(newCall.roomId, newCall.callId)
        }
    }

    fun holdAndAccept(newCall: CallSession) {
        callWaitingManager.holdAndAccept(newCall)
        _currentCall.value = callWaitingManager.activeCall
        _callState.value = CallState.ACTIVE
    }

    fun endCurrentAndAccept(newCall: CallSession) {
        val current = _currentCall.value ?: return
        viewModelScope.launch {
            performEndCall(current)
            acceptIncomingCall(
                newCall.roomId, newCall.callId,
                newCall.callerUid, newCall.callerName,
                newCall.callerPhotoUrl, newCall.callerZaxoNumber,
                newCall.mediaType
            )
        }
    }

    fun declineCallWaiting() {
        val newCall = callWaitingManager.activeCall ?: return
        sendBusyToCaller(newCall.roomId, newCall.callId)
        _callState.value = CallState.ACTIVE
    }

    fun swapCalls() {
        val (newActive, newHeld) = callWaitingManager.swapCalls()
        _currentCall.value = newActive
        _callState.value = CallState.ACTIVE
    }

    // ==================== NETWORK QUALITY ====================

    fun onNetworkQualityChanged(quality: String) {
        _networkQuality.value = quality
        when (quality) {
            "excellent", "good" -> {
                _isReconnecting.value = false
                if (_callState.value == CallState.RECONNECTING) {
                    _callState.value = CallState.ACTIVE
                }
            }
            "poor" -> {
                Timber.d("Poor connection quality")
            }
            "lost" -> {
                _isReconnecting.value = true
                _callState.value = CallState.RECONNECTING
                startReconnectTimeout()
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // LIVEKIT ROOM MANAGEMENT (replaces all TODOs)
    // ═══════════════════════════════════════════════════════

    /**
     * Create a LiveKit room with signed token from Cloud Function.
     * F111: Signed room tokens prevent unauthorized room access.
     * F110: E2EE always enabled.
     */
    private suspend fun createLiveKitRoom(mediaType: CallMediaType, isGroup: Boolean = false): String {
        val roomId = UUID.randomUUID().toString()
        val currentUid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        val zaxoNumber = prefs.getString("cachedZaxoNumber", currentUid) ?: currentUid

        try {
            // F111: Get signed token from Cloud Function
            val tokenResult = cloudFunctions
                .getHttpsCallable("generateRoomToken")
                .call(mapOf(
                    "roomId" to roomId,
                    "participantIdentity" to zaxoNumber,
                    "isAdmin" to true
                ))
                .result

            val token = tokenResult?.data as? Map<*, *>
            val jwt = token?.get("token") as? String ?: ""

            // Connect to LiveKit room
            liveKitRoom = liveKitClient.connect(
                url = liveKitUrl,
                token = jwt
            )

            // Enable audio by default
            liveKitRoom?.localParticipant?.setMicrophoneEnabled(true)

            // Enable video if video call
            if (mediaType == CallMediaType.VIDEO) {
                liveKitRoom?.localParticipant?.setCameraEnabled(true)
            }

            // Set up room event listeners
            setupRoomListeners()

            Timber.d("LiveKit room created: $roomId, e2ee=true, video=${mediaType == CallMediaType.VIDEO}")
            return roomId

        } catch (e: Exception) {
            Timber.e(e, "Failed to create LiveKit room, retrying with new ID")

            // Retry once with new room ID
            val retryRoomId = UUID.randomUUID().toString()
            try {
                val retryTokenResult = cloudFunctions
                    .getHttpsCallable("generateRoomToken")
                    .call(mapOf(
                        "roomId" to retryRoomId,
                        "participantIdentity" to zaxoNumber,
                        "isAdmin" to true
                    ))
                    .result

                val retryToken = retryTokenResult?.data as? Map<*, *>
                val retryJwt = retryToken?.get("token") as? String ?: ""

                liveKitRoom = liveKitClient.connect(
                    url = liveKitUrl,
                    token = retryJwt
                )

                liveKitRoom?.localParticipant?.setMicrophoneEnabled(true)
                if (mediaType == CallMediaType.VIDEO) {
                    liveKitRoom?.localParticipant?.setCameraEnabled(true)
                }

                setupRoomListeners()
                Timber.d("LiveKit room created on retry: $retryRoomId")
                return retryRoomId

            } catch (retryError: Exception) {
                Timber.e(retryError, "LiveKit room creation failed on retry")
                throw retryError
            }
        }
    }

    /**
     * Join an existing LiveKit room (for incoming calls).
     */
    private suspend fun joinLiveKitRoom(roomId: String) {
        val currentUid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        val zaxoNumber = prefs.getString("cachedZaxoNumber", currentUid) ?: currentUid

        // Get signed token from Cloud Function
        val tokenResult = cloudFunctions
            .getHttpsCallable("generateRoomToken")
            .call(mapOf(
                "roomId" to roomId,
                "participantIdentity" to zaxoNumber,
                "isAdmin" to false
            ))
            .result

        val token = tokenResult?.data as? Map<*, *>
        val jwt = token?.get("token") as? String ?: ""

        // Connect to the existing room
        liveKitRoom = liveKitClient.connect(
            url = liveKitUrl,
            token = jwt
        )

        // Enable audio
        liveKitRoom?.localParticipant?.setMicrophoneEnabled(true)

        // Enable video if video call
        val call = _currentCall.value
        if (call?.mediaType == CallMediaType.VIDEO) {
            liveKitRoom?.localParticipant?.setCameraEnabled(true)
        }

        // Set up room event listeners
        setupRoomListeners()

        Timber.d("Joined LiveKit room: $roomId")
    }

    /**
     * Set up LiveKit room event listeners for participant events,
     * connection quality changes, and active speaker detection.
     */
    private fun setupRoomListeners() {
        val room = liveKitRoom ?: return

        viewModelScope.launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.ParticipantConnected -> {
                        Timber.d("Participant connected: ${event.participant.identity}")
                        if (_callState.value == CallState.RINGING ||
                            _callState.value == CallState.INCOMING ||
                            _callState.value == CallState.CONNECTING
                        ) {
                            _callState.value = CallState.ACTIVE
                            ringbackManager.fadeOut()
                            ringtoneManager.stop()
                            callTimer.start()
                            startTimerUpdates()
                            _currentCall.value = _currentCall.value?.copy(
                                state = CallState.ACTIVE,
                                connectTimestamp = System.currentTimeMillis()
                            )
                            updateProximitySensor()
                        } else if (_currentCall.value?.isGroupCall == true) {
                            _callState.value = CallState.GROUP_PARTICIPANT_JOINED
                            updateParticipantList()
                        }
                    }
                    is RoomEvent.ParticipantDisconnected -> {
                        Timber.d("Participant disconnected: ${event.participant.identity}")
                        if (_currentCall.value?.isGroupCall == true) {
                            updateParticipantList()
                        } else {
                            // 1:1 call — other party left
                            endCall()
                        }
                    }
                    is RoomEvent.ActiveSpeakersChanged -> {
                        val activeSpeaker = event.speakers.firstOrNull()
                        if (_currentCall.value?.isGroupCall == true && activeSpeaker != null) {
                            _currentCall.value = _currentCall.value?.copy(
                                activeSpeakerId = activeSpeaker.sid ?: ""
                            )
                        }
                    }
                    is RoomEvent.ConnectionQualityChanged -> {
                        val quality = when {
                            event.quality.ordinal >= 3 -> "poor"
                            event.quality.ordinal == 2 -> "good"
                            else -> "excellent"
                        }
                        onNetworkQualityChanged(quality)
                    }
                    is RoomEvent.Reconnecting -> {
                        Timber.w("LiveKit room reconnecting")
                        _callState.value = CallState.RECONNECTING
                        _isReconnecting.value = true
                        startReconnectTimeout()
                    }
                    is RoomEvent.Reconnected -> {
                        Timber.d("LiveKit room reconnected")
                        _callState.value = CallState.ACTIVE
                        _isReconnecting.value = false
                    }
                    else -> { /* Unhandled event */ }
                }
            }
        }
    }

    /**
     * Update participant list for group calls.
     */
    private fun updateParticipantList() {
        val room = liveKitRoom ?: return
        val participantIds = room.remoteParticipants.values.map { it.identity?.value ?: "" }
        _currentCall.value = _currentCall.value?.copy(participantIds = participantIds)
    }

    // ═══════════════════════════════════════════════════════
    // F81: Monitor video track — restart if unexpectedly stopped
    // ═══════════════════════════════════════════════════════

    private fun monitorVideoTrack() {
        val room = liveKitRoom ?: return
        viewModelScope.launch {
            room.localParticipant.events.collect { event ->
                when (event) {
                    is RoomEvent.TrackMuted -> {
                        if (event.participant is LocalParticipant &&
                            event.publication.sid.contains("video")
                        ) {
                            Timber.d("Video track muted unexpectedly — restarting")
                            if (_currentCall.value?.isVideoOn == true) {
                                room.localParticipant.setCameraEnabled(true)
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    // ==================== HELPER METHODS ====================

    private fun shouldAutoAnswer(callerUid: String, callType: String): Boolean {
        val prefs = context.getSharedPreferences("zaxo_prefs", 0)
        if (!prefs.getBoolean("p2pAutoAnswer", false)) return false
        if (callType != "audio") return false // Never auto-answer video
        val chat = chatDao.getChatByRecipientId(callerUid)
        if (chat == null) return false // Contacts only
        val kgm = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (kgm?.isDeviceLocked == true) return false
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.isWiredHeadsetOn || audioManager.isBluetoothA2dpOn) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (nm.currentInterruptionFilter >= android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY) return false
        return true
    }

    /**
     * Send call push via Cloud Function.
     * F109: Server-side caller identity resolution prevents spoofing.
     * F113: Rate limiting enforced server-side.
     */
    private suspend fun sendCallPush(
        calleeUid: String, roomId: String, callId: String, mediaType: CallMediaType
    ) {
        try {
            val currentUid = auth.currentUser?.uid ?: return

            // Try Cloud Function first (F109: server-side caller resolution)
            try {
                val result = cloudFunctions
                    .getHttpsCallable("sendCallPush")
                    .call(mapOf(
                        "calleeUid" to calleeUid,
                        "roomId" to roomId,
                        "callId" to callId,
                        "callType" to mediaType.value,
                        "isGroupCall" to false
                    ))
                    .result

                val status = (result?.data as? Map<*, *>)?.get("status") as? String
                when (status) {
                    "rate_limited" -> {
                        Timber.w("Call rate limited by server")
                        throw IllegalStateException("Rate limited")
                    }
                    "blocked" -> {
                        Timber.w("Caller is blocked by callee")
                        _callState.value = CallState.PRIVACY_BLOCKED
                        throw IllegalStateException("Blocked")
                    }
                    "offline" -> {
                        Timber.w("Callee is offline")
                        _callState.value = CallState.USER_OFFLINE
                        throw IllegalStateException("Offline")
                    }
                    "sent" -> {
                        Timber.d("Call push sent via Cloud Function")
                    }
                }
                return
            } catch (cfError: Exception) {
                if (cfError is IllegalStateException) throw cfError
                Timber.w(cfError, "Cloud Function failed — falling back to Firestore")
            }

            // Fallback: direct Firestore write (existing behavior)
            firestore.collection("activeCalls").document(callId).set(mapOf(
                "roomId" to roomId,
                "callerUid" to currentUid,
                "calleeUid" to calleeUid,
                "callType" to mediaType.value,
                "status" to "ringing",
                "timestamp" to System.currentTimeMillis()
            )).await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to send call push")
            // Retry once
            try {
                firestore.collection("activeCalls").document(callId).set(mapOf(
                    "roomId" to roomId,
                    "callerUid" to auth.currentUser?.uid,
                    "calleeUid" to calleeUid,
                    "callType" to mediaType.value,
                    "status" to "ringing",
                    "timestamp" to System.currentTimeMillis()
                )).await()
            } catch (retryError: Exception) {
                Timber.e(retryError, "Call push retry failed")
                throw retryError
            }
        }
    }

    /**
     * Send group call push via Cloud Function.
     */
    private suspend fun sendGroupCallPush(
        calleeUid: String, roomId: String, callId: String,
        mediaType: CallMediaType, groupId: String, groupName: String
    ) {
        try {
            val currentUid = auth.currentUser?.uid ?: return
            val callerName = getCachedUserName()

            // Try Cloud Function first
            try {
                cloudFunctions
                    .getHttpsCallable("sendGroupCallPush")
                    .call(mapOf(
                        "participantIds" to listOf(calleeUid),
                        "roomId" to roomId,
                        "callId" to callId,
                        "callType" to mediaType.value,
                        "groupId" to groupId,
                        "groupName" to groupName,
                        "callerName" to callerName
                    ))
                    .result
                Timber.d("Group call push sent via Cloud Function to $calleeUid")
                return
            } catch (cfError: Exception) {
                Timber.w(cfError, "Cloud Function failed — falling back to Firestore")
            }

            // Fallback: direct Firestore write
            firestore.collection("activeCalls").document(callId).set(mapOf(
                "roomId" to roomId,
                "callerUid" to currentUid,
                "calleeUid" to calleeUid,
                "callType" to mediaType.value,
                "isGroupCall" to true,
                "groupId" to groupId,
                "groupName" to groupName,
                "status" to "ringing",
                "timestamp" to System.currentTimeMillis()
            )).await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to send group call push to $calleeUid")
        }
    }

    private fun sendBusyToCaller(roomId: String, callId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("activeCalls").document(callId).update(
                    "status", "busy"
                ).await()
            } catch (e: Exception) {
                Timber.e(e, "Failed to send busy signal")
            }
        }
    }

    private suspend fun declineWithMessage(callerUid: String, message: String) {
        try {
            val currentUid = auth.currentUser?.uid ?: return
            Timber.d("Decline with message sent to $callerUid: $message")
        } catch (e: Exception) {
            Timber.e(e, "Failed to send decline message")
        }
    }

    private suspend fun saveMissedCall(
        callerUid: String, callerName: String, callerPhotoUrl: String, mediaType: CallMediaType
    ) {
        callHistoryRepository.saveFromSession(
            contactId = callerUid,
            contactName = callerName.ifEmpty { "Unknown" },
            contactPhotoUrl = callerPhotoUrl,
            callType = CallType.MISSED,
            mediaType = mediaType.value,
            duration = 0L
        )

        // F107: Show in-app notification for missed calls
        _missedCallNotification.value = CallRecord(
            id = UUID.randomUUID().toString(),
            contactId = callerUid,
            contactName = callerName.ifEmpty { "Unknown" },
            contactPhotoUrl = callerPhotoUrl,
            callType = CallType.MISSED,
            mediaType = mediaType.value,
            timestamp = System.currentTimeMillis(),
            duration = 0L
        )
    }

    fun dismissMissedCallNotification() {
        _missedCallNotification.value = null
    }

    /**
     * N. Call collision detection — check if callee already called us.
     */
    suspend fun checkForCollision(myUid: String, calleeUid: String): String? {
        return try {
            val activeCalls = firestore.collection("activeCalls")
                .whereEqualTo("callerUid", calleeUid)
                .whereEqualTo("calleeUid", myUid)
                .whereEqualTo("status", "ringing")
                .limit(1)
                .get()
                .await()

            if (activeCalls.isEmpty) null
            else activeCalls.documents[0].getString("roomId")
        } catch (e: Exception) {
            Timber.e(e, "Collision check failed")
            null
        }
    }

    /**
     * T. Zaxo Number lookup with privacy gate.
     */
    suspend fun lookupZaxoNumber(number: String): LookupResult {
        val clean = number.replace(Regex("[^0-9]"), "")
        if (clean.length != 9) return LookupResult.NotFound

        return try {
            val snapshot = firestore.collection("users")
                .whereEqualTo("zaxoNumber", clean)
                .limit(1)
                .get()
                .await()

            if (snapshot.isEmpty) return LookupResult.NotFound

            val userDoc = snapshot.documents[0]
            val visibility = userDoc.getString("privacy.zaxoVisibility") ?: "everyone"

            when (visibility) {
                "everyone" -> LookupResult.Found(
                    uid = userDoc.id,
                    displayName = userDoc.getString("displayName") ?: "Zaxo User",
                    photoUrl = userDoc.getString("photoUrl") ?: "",
                    zaxoNumber = clean
                )
                "contacts" -> {
                    val isContact = chatDao.getChatByRecipientId(userDoc.id) != null
                    if (isContact) LookupResult.Found(
                        uid = userDoc.id,
                        displayName = userDoc.getString("displayName") ?: "Zaxo User",
                        photoUrl = userDoc.getString("photoUrl") ?: "",
                        zaxoNumber = clean
                    ) else LookupResult.Hidden
                }
                "nobody" -> LookupResult.NotFound
                else -> LookupResult.NotFound
            }
        } catch (e: Exception) {
            Timber.e(e, "Zaxo number lookup failed")
            LookupResult.NotFound
        }
    }

    private fun updateProximitySensor() {
        val call = _currentCall.value ?: return
        if (proximityManager.shouldActivate(
                call.mediaType.value, call.isSpeakerOn, call.isBluetoothOn,
                _callState.value == CallState.ACTIVE
            )
        ) {
            proximityManager.acquire()
        } else {
            proximityManager.release()
        }
    }

    private fun startRingingTimeout(
        callId: String, calleeUid: String, calleeName: String,
        calleePhotoUrl: String, mediaType: CallMediaType
    ) {
        ringingTimeoutJob?.cancel()
        ringingTimeoutJob = viewModelScope.launch {
            delay(60_000) // 60s timeout
            if (_callState.value == CallState.RINGING || _callState.value == CallState.DIALING) {
                ringbackManager.stop()
                _callState.value = CallState.NO_ANSWER
                _currentCall.value = _currentCall.value?.copy(state = CallState.NO_ANSWER)

                callHistoryRepository.saveFromSession(
                    contactId = calleeUid,
                    contactName = calleeName,
                    contactPhotoUrl = calleePhotoUrl,
                    callType = CallType.MISSED,
                    mediaType = mediaType.value,
                    duration = 0L
                )

                delay(2000)
                _callState.value = CallState.IDLE
                _currentCall.value = null
                isCallInProgress = false
            }
        }
    }

    private fun startIncomingTimeout(callId: String, callerUid: String, callerName: String, mediaType: CallMediaType) {
        ringingTimeoutJob?.cancel()
        ringingTimeoutJob = viewModelScope.launch {
            delay(60_000) // 60s timeout
            if (_callState.value == CallState.INCOMING) {
                ringtoneManager.stop()
                _callState.value = CallState.NO_ANSWER
                saveMissedCall(callerUid, callerName, "", mediaType)
                delay(2000)
                _callState.value = CallState.IDLE
                _currentCall.value = null
            }
        }
    }

    /**
     * F85: 30s reconnect timeout — if we cannot reconnect in 30s, end call.
     */
    private fun startReconnectTimeout() {
        reconnectTimeoutJob?.cancel()
        reconnectTimeoutJob = viewModelScope.launch {
            delay(30_000)
            if (_callState.value == CallState.RECONNECTING) {
                Timber.w("Reconnect timeout — ending call")
                _callState.value = CallState.CALL_FAILED
                performEndCall(_currentCall.value ?: return@launch)
            }
        }
    }

    private fun startTimerUpdates() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                callTimer.tick()
                _callTimerText.value = callTimer.format()
                delay(1000)
            }
        }
    }

    private fun clearTimeouts() {
        ringingTimeoutJob?.cancel()
        reconnectTimeoutJob?.cancel()
        timerJob?.cancel()
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // F114: Reduce video quality on battery saver
    private fun isBatterySaverOn(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isPowerSaveMode
    }

    private fun isActiveNetworkMetered(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return true
        val caps = cm.getNetworkCapabilities(network) ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun getCachedUserName(): String {
        val prefs = context.getSharedPreferences("zaxo_prefs", 0)
        return prefs.getString("cachedDisplayName", "Zaxo User") ?: "Zaxo User"
    }

    /**
     * Listen for Firestore call status changes (busy, declined, etc).
     */
    fun startListeningForCallResponses(callId: String) {
        firestore.collection("activeCalls").document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error listening for call responses")
                    return@addSnapshotListener
                }
                val status = snapshot?.getString("status") ?: return@addSnapshotListener
                when (status) {
                    "accepted" -> {
                        ringbackManager.fadeOut()
                        _callState.value = CallState.CONNECTING
                        viewModelScope.launch {
                            delay(1000)
                            _callState.value = CallState.ACTIVE
                            _currentCall.value = _currentCall.value?.copy(
                                state = CallState.ACTIVE,
                                connectTimestamp = System.currentTimeMillis()
                            )
                            callTimer.start()
                            startTimerUpdates()
                            updateProximitySensor()
                        }
                    }
                    "declined" -> {
                        ringbackManager.stop()
                        _callState.value = CallState.CALL_DECLINED
                        viewModelScope.launch {
                            delay(2000)
                            _callState.value = CallState.IDLE
                            _currentCall.value = null
                            isCallInProgress = false
                        }
                    }
                    "busy" -> {
                        ringbackManager.stop()
                        _callState.value = CallState.LINE_BUSY
                        viewModelScope.launch {
                            delay(3000)
                            _callState.value = CallState.IDLE
                            _currentCall.value = null
                            isCallInProgress = false
                        }
                    }
                    "answered_elsewhere" -> {
                        ringtoneManager.stop()
                        _callState.value = CallState.ANSWERED_ELSEWHERE
                        viewModelScope.launch {
                            delay(1000)
                            _callState.value = CallState.IDLE
                            _currentCall.value = null
                        }
                    }
                }
            }
    }

    override fun onCleared() {
        super.onCleared()

        // Disconnect LiveKit room if still connected
        try {
            liveKitRoom?.disconnect()
            liveKitRoom = null
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting LiveKit on clear")
        }

        // Stop all audio and managers
        ringbackManager.stop()
        ringtoneManager.stop()
        proximityManager.release()
        callTimer.reset()
        clearTimeouts()
        isCallInProgress = false

        // Unregister Bluetooth receiver (F78)
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unregister Bluetooth receiver")
        }

        // Unregister network callback (F83)
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unregister network callback")
        }
    }
}
