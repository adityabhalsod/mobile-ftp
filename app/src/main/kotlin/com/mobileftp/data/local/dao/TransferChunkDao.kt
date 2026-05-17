package com.mobileftp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mobileftp.data.local.entity.TransferChunkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferChunkDao {
    @Query("SELECT * FROM transfer_chunks WHERE jobId = :jobId ORDER BY `index` ASC")
    fun observeForJob(jobId: Long): Flow<List<TransferChunkEntity>>

    @Query("SELECT * FROM transfer_chunks WHERE jobId = :jobId ORDER BY `index` ASC")
    suspend fun listForJob(jobId: Long): List<TransferChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<TransferChunkEntity>): List<Long>

    @Update
    suspend fun update(entity: TransferChunkEntity)

    @Query("UPDATE transfer_chunks SET transferredBytes = :bytes, state = :state, speedBytesPerSec = :speed WHERE id = :id")
    suspend fun updateProgress(id: Long, bytes: Long, state: String, speed: Long)

    @Query("UPDATE transfer_chunks SET state = :state WHERE id = :id")
    suspend fun updateState(id: Long, state: String)

    @Query("UPDATE transfer_chunks SET md5 = :md5 WHERE id = :id")
    suspend fun updateMd5(id: Long, md5: String)

    @Query("DELETE FROM transfer_chunks WHERE jobId = :jobId")
    suspend fun deleteForJob(jobId: Long)
}
