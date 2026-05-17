package com.mobileftp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mobileftp.data.local.entity.TransferJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferJobDao {
    @Query("SELECT * FROM transfer_jobs ORDER BY priority DESC, startedAt DESC")
    fun observeAll(): Flow<List<TransferJobEntity>>

    @Query("SELECT * FROM transfer_jobs WHERE state IN ('ACTIVE','QUEUED','PAUSED') ORDER BY priority DESC, startedAt ASC")
    fun observeActive(): Flow<List<TransferJobEntity>>

    @Query("SELECT * FROM transfer_jobs WHERE id = :id")
    suspend fun getById(id: Long): TransferJobEntity?

    @Query("SELECT * FROM transfer_jobs WHERE id = :id")
    fun observeById(id: Long): Flow<TransferJobEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransferJobEntity): Long

    @Update
    suspend fun update(entity: TransferJobEntity)

    @Query("UPDATE transfer_jobs SET state = :state, updatedAt = :ts, error = :error WHERE id = :id")
    suspend fun updateState(id: Long, state: String, ts: Long, error: String?)

    @Query("UPDATE transfer_jobs SET transferredBytes = :bytes, updatedAt = :ts WHERE id = :id")
    suspend fun updateProgress(id: Long, bytes: Long, ts: Long)

    @Query("UPDATE transfer_jobs SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: Long, priority: Int)

    @Query("DELETE FROM transfer_jobs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transfer_jobs WHERE state IN ('COMPLETED','CANCELLED','FAILED')")
    suspend fun clearFinished()
}
