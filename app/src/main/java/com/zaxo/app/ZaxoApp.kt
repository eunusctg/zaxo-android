package com.zaxo.app

import android.app.Application
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.zaxo.app.notification.NotificationChannels
import com.zaxo.app.ui.components.VoicePlaybackManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class ZaxoApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // E.1: Production logging via Crashlytics
            Timber.plant(CrashlyticsTree())
        }

        // E.1: Crashlytics — set custom keys for better crash triage
        Firebase.crashlytics.setCustomKeys {
            key("version", BuildConfig.VERSION_NAME)
            key("build", BuildConfig.VERSION_CODE.toString())
        }

        // D.2: Initialize MediaSession for voice message background playback
        VoicePlaybackManager.initialize(this)

        // D.1: Create notification channels on app startup
        NotificationChannels.createChannels(this)
    }
}

/**
 * E.1: Timber tree that logs to Firebase Crashlytics in release builds.
 * Debug logs are NOT sent to Crashlytics to avoid noise.
 * Only warnings and errors are reported.
 */
private class CrashlyticsTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Only log warnings and above to Crashlytics
        if (priority < android.util.Log.WARN) return

        // Log custom key with the message for context
        Firebase.crashlytics.setCustomKey("last_log", message.take(256))

        // Record the exception if provided
        t?.let { Firebase.crashlytics.recordException(it) }
    }
}
