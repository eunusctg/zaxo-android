package com.zaxo.app.util

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import timber.log.Timber

/**
 * Manages ringback tone playback for outgoing calls.
 * The caller hears this tone while the callee's phone is ringing.
 * Respects the device's ringer mode — no ringback if on silent/vibrate.
 */
class RingbackToneManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    fun start(customUrl: String? = null) {
        stop()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            Timber.d("Device on silent/vibrate — skipping ringback")
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                if (customUrl != null) {
                    setDataSource(customUrl)
                    prepareAsync()
                    setOnPreparedListener { start() }
                } else {
                    // Use default system ringback tone — fallback to standard tone
                    val uri = Uri.parse("android.resource://${context.packageName}/raw/zaxo_ringback")
                    try {
                        setDataSource(context, uri)
                        prepare()
                        start()
                    } catch (e: Exception) {
                        // Resource might not exist; use system default
                        setDataSource(context, Uri.parse("file:///system/media/audio/ringtones/default.ogg"))
                        prepare()
                        start()
                    }
                }
                isLooping = true
                setVolume(0.7f, 0.7f)
            }
            Timber.d("Ringback tone started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start ringback tone")
        }
    }

    fun fadeOut(durationMs: Long = 300) {
        val startVolume = 0.7f
        val steps = 10
        for (i in 0..steps) {
            handler.postDelayed({
                try {
                    val v = startVolume * (1f - i.toFloat() / steps)
                    mediaPlayer?.setVolume(v, v)
                    if (i == steps) stop()
                } catch (e: Exception) {
                    stop()
                }
            }, i * (durationMs / steps))
        }
    }

    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error stopping ringback")
        }
        mediaPlayer = null
    }
}
