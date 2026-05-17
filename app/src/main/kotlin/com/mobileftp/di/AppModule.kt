package com.mobileftp.di

import android.content.Context
import androidx.work.WorkManager
import com.mobileftp.data.local.SecurePreferences
import com.mobileftp.data.local.SettingsStore
import com.mobileftp.network.NetworkInterfaceSelector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        SettingsStore(context)

    @Provides
    @Singleton
    fun provideSecurePreferences(@ApplicationContext context: Context): SecurePreferences =
        SecurePreferences(context)

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideNetworkInterfaceSelector(@ApplicationContext context: Context): NetworkInterfaceSelector =
        NetworkInterfaceSelector(context)
}
