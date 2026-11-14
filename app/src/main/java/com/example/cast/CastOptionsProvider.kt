package com.example.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.LaunchOptions
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val launchOptions = LaunchOptions.Builder()
            .setRelaunchIfRunning(true)
            .setAndroidReceiverCompatible(false)
            .build()

        return CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setLaunchOptions(launchOptions)
            .setStopReceiverApplicationWhenEndingSession(true)
            .setResumeSavedSession(false)
            .setEnableReconnectionService(false)
            .setSessionTransferEnabled(false)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return null
    }
}
