package com.zaxo.app.util

import com.zaxo.app.model.CallSession
import com.zaxo.app.model.CallState
import timber.log.Timber

/**
 * Manages two concurrent calls (active + held).
 * Handles the call waiting scenario when a new incoming call arrives
 * while already on an active call (F101-F104).
 *
 * Rules:
 * - Only one active call at a time
 * - Held call is muted and video is frozen (F102, F104)
 * - Separate timer per call (F103)
 * - Swap toggles active/held
 */
class CallWaitingManager {

    private var _activeCall: CallSession? = null
    private var _heldCall: CallSession? = null

    val activeCall: CallSession? get() = _activeCall
    val heldCall: CallSession? get() = _heldCall
    val isCallWaiting: Boolean get() = _heldCall != null

    // F103: Separate timer per call for call waiting
    private val activeCallTimer = CallTimer()
    private val heldCallTimer = CallTimer()

    fun getActiveCallTimer(): CallTimer = activeCallTimer
    fun getHeldCallTimer(): CallTimer = heldCallTimer

    /**
     * Set the current active call (used when first call starts).
     */
    fun setActiveCall(call: CallSession) {
        _activeCall = call
        Timber.d("Active call set: ${call.callId}")
    }

    /**
     * Called when a new incoming call arrives while already on a call.
     * Returns true if call waiting UI should be shown, false if busy signal sent.
     */
    fun onIncomingCallWhileActive(
        newCall: CallSession,
        callWaitingEnabled: Boolean
    ): Boolean {
        if (!callWaitingEnabled) {
            Timber.d("Call waiting disabled — sending busy signal")
            return false
        }
        Timber.d("Call waiting — showing overlay for ${newCall.callerName}")
        return true
    }

    /**
     * Hold current active call and accept the new incoming call.
     */
    fun holdAndAccept(newCall: CallSession): CallSession? {
        val previousActive = _activeCall
        _heldCall = previousActive?.copy(state = CallState.HELD, isOnHold = true, isMuted = true, isVideoOn = false) // F104: freeze video
        _activeCall = newCall.copy(state = CallState.CONNECTING)
        Timber.d("Held call ${previousActive?.callId}, accepting ${newCall.callId}")
        activeCallTimer.start() // F103: start tracking active call time
        heldCallTimer.start() // F103: start tracking held call time
        return _heldCall
    }

    /**
     * End current active call and accept the new incoming call.
     */
    fun endCurrentAndAccept(newCall: CallSession): CallSession? {
        val endedCall = _activeCall
        _activeCall = newCall.copy(state = CallState.CONNECTING)
        _heldCall = null
        Timber.d("Ended call ${endedCall?.callId}, accepting ${newCall.callId}")
        return endedCall
    }

    /**
     * Decline the new incoming call, keep current active.
     */
    fun declineNewCall(): CallSession? {
        Timber.d("Declined new incoming call, keeping active")
        return _activeCall
    }

    /**
     * Swap active and held calls.
     */
    fun swapCalls(): Pair<CallSession?, CallSession?> {
        val temp = _activeCall
        _activeCall = _heldCall?.copy(state = CallState.ACTIVE, isOnHold = false, isMuted = false)
        _heldCall = temp?.copy(state = CallState.HELD, isOnHold = true, isMuted = true)
        Timber.d("Swapped calls — active: ${_activeCall?.callId}, held: ${_heldCall?.callId}")
        // F103: Pause/resume timers appropriately
        heldCallTimer.hold()
        activeCallTimer.resume()
        return Pair(_activeCall, _heldCall)
    }

    /**
     * Clear all call state when calls end.
     */
    fun clear() {
        _activeCall = null
        _heldCall = null
    }

    /**
     * Remove held call after it ends.
     */
    fun clearHeldCall() {
        _heldCall = null
    }
}
