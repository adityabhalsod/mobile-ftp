package com.mobileftp.di

import com.mobileftp.network.AdaptiveBufferEngine
import com.mobileftp.network.Lz4CompressionEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    fun provideAdaptiveBufferEngine(): AdaptiveBufferEngine = AdaptiveBufferEngine()

    @Provides
    @Singleton
    fun provideCompressionEngine(): Lz4CompressionEngine = Lz4CompressionEngine(true)
}
