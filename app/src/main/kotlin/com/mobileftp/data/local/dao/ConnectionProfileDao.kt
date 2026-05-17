package com.mobileftp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mobileftp.data.local.entity.ConnectionProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionProfileDao {
    @Query("SELECT * FROM connection_profiles ORDER BY lastConnectedAt DESC, id DESC")
    fun observeAll(): Flow<List<ConnectionProfileEntity>>

    @Query("SELECT * FROM connection_profiles WHERE id = :id")
    suspend fun getById(id: Long): ConnectionProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ConnectionProfileEntity): Long

    @Update
    suspend fun update(entity: ConnectionProfileEntity)

    @Delete
    suspend fun delete(entity: ConnectionProfileEntity)

    @Query("UPDATE connection_profiles SET lastConnectedAt = :ts WHERE id = :id")
    suspend fun touch(id: Long, ts: Long)
}
