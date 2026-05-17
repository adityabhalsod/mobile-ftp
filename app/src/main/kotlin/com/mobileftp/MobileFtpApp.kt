package com.mobileftp

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MobileFtpApp : Application(), Configuration.Provider {

    @Inject lateinit var workConfiguration: Configuration

    override val workManagerConfiguration: Configuration
        get() = workConfiguration

    override fun onCreate() {
        super.onCreate()
        // Initialize WorkManager early so the Hilt-configured factory takes effect.
        WorkManager.initialize(this, workConfiguration)
    }
}
