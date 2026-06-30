package com.zaxo.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.livekit.android.LiveKitClient
import io.livekit.android.room.RoomOptions
import javax.inject.Singleton

/**
 * Hilt module providing LiveKit client singleton and server URL.
 * LiveKit SDK handles WebRTC media transport, room management,
 * and E2EE for all ZAXO calls.
 */
@Module
@InstallIn(SingletonComponent::class)
object LiveKitModule {

    // LiveKit Cloud server URL
    private const val LIVEKIT_URL = "wss://zaxo-02zyt0px.livekit.cloud"

    @Provides
    @Singleton
    fun provideLiveKitClient(@ApplicationContext context: Context): LiveKitClient {
        return LiveKitClient(
            context = context,
            options = RoomOptions(
                // F77: Acoustic Echo Canceler enabled by default
                echoCancellation = true,
                // F82: Audio resampling handled automatically
                // F86: TURN relay fallback built into LiveKit
                // F87: Jitter buffer active by default
                adaptiveStream = true
            )
        )
    }

    @Provides
    @Singleton
    fun provideLiveKitUrl(): String = LIVEKIT_URL
}
