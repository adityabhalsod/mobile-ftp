package com.mobileftp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mobileftp.data.local.dao.ConnectionProfileDao
import com.mobileftp.data.local.dao.TransferChunkDao
import com.mobileftp.data.local.dao.TransferJobDao
import com.mobileftp.data.local.entity.ConnectionProfileEntity
import com.mobileftp.data.local.entity.TransferChunkEntity
import com.mobileftp.data.local.entity.TransferJobEntity

@Database(
    entities = [
        ConnectionProfileEntity::class,
        TransferJobEntity::class,
        TransferChunkEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionProfileDao(): ConnectionProfileDao
    abstract fun transferJobDao(): TransferJobDao
    abstract fun transferChunkDao(): TransferChunkDao

    companion object {
        const val NAME: String = "mobile_ftp.db"

        /** Stub migration scaffold for future schema bumps. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reserved for future: add migration ALTER TABLE statements here.
            }
        }
    }
}
