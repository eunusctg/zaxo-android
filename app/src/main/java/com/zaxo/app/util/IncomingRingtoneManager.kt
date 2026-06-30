package com.zaxo.app.util

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import timber.log.Timber

/**
 * Manages ringtone and vibration for incoming calls.
 * Respects user ring mode setting (Ring / Vibrate / Silent) and DND.
 */
class IncomingRingtoneManager(private val context: Context) {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    private val vibrationPattern = longArrayOf(0, 400, 200, 400)

    fun start(ringMode: String, customUri: String? = null) {
        stop()
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        try {
            when (ringMode) {
                "Ring" -> {
                    val uri = customUri?.let { Uri.parse(it) }
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ringtone = RingtoneManager.getRingtone(context, uri)
                    ringtone?.play()
                    vibrator?.vibrate(VibrationEffect.createWaveform(vibrationPattern, 0))
                }
                "Vibrate" -> {
                    vibrator?.vibrate(VibrationEffect.createWaveform(vibrationPattern, 0))
                }
                "Silent" -> {
                    // No sound, no vibration
                }
            }
            Timber.d("Incoming ringtone started with mode: $ringMode")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start incoming ringtone")
        }
    }

    fun stop() {
        try {
            ringtone?.stop()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping ringtone")
        }
        ringtone = null

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Timber.e(e, "Error cancelling vibration")
        }
        vibrator = null
    }
}
