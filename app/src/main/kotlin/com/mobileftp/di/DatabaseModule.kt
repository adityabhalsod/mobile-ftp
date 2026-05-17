package com.mobileftp.di

import android.content.Context
import androidx.room.Room
import com.mobileftp.data.local.AppDatabase
import com.mobileftp.data.local.dao.ConnectionProfileDao
import com.mobileftp.data.local.dao.TransferChunkDao
import com.mobileftp.data.local.dao.TransferJobDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideConnectionProfileDao(db: AppDatabase): ConnectionProfileDao = db.connectionProfileDao()

    @Provides
    fun provideTransferJobDao(db: AppDatabase): TransferJobDao = db.transferJobDao()

    @Provides
    fun provideTransferChunkDao(db: AppDatabase): TransferChunkDao = db.transferChunkDao()
}
