package com.zaxo.app.util

import android.content.Context
import android.os.PowerManager
import timber.log.Timber

/**
 * Manages the proximity sensor wake lock for audio calls.
 * When active (audio call, earpiece, not Bluetooth), the screen turns off
 * when the phone is near the ear (F90: only for audio calls).
 */
class ProximitySensorManager(context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Determine if proximity sensor should be activated.
     * Only for audio calls on earpiece (not speaker, not Bluetooth, not video).
     */
    fun shouldActivate(
        callType: String,
        isSpeakerOn: Boolean,
        isBluetooth: Boolean,
        isActive: Boolean
    ): Boolean {
        return callType == "audio" && !isSpeakerOn && !isBluetooth && isActive
    }

    fun acquire() {
        try {
            release()
            wakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "Zaxo:proximity"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 10 min timeout safety
            Timber.d("Proximity wake lock acquired")
        } catch (e: Exception) {
            Timber.e(e, "Failed to acquire proximity wake lock")
        }
    }

    fun release() {
        try {
            wakeLock?.apply {
                if (isHeld) release()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to release proximity wake lock")
        }
        wakeLock = null
    }
}
