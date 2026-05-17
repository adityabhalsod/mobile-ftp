package com.mobileftp.data.repository

import com.mobileftp.data.local.dao.TransferChunkDao
import com.mobileftp.data.local.dao.TransferJobDao
import com.mobileftp.data.local.entity.TransferChunkEntity
import com.mobileftp.data.local.entity.TransferJobEntity
import com.mobileftp.domain.model.ChunkState
import com.mobileftp.domain.model.TransferChunk
import com.mobileftp.domain.model.TransferJob
import com.mobileftp.domain.model.TransferState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepository @Inject constructor(
    private val jobDao: TransferJobDao,
    private val chunkDao: TransferChunkDao
) {

    fun observeJobs(): Flow<List<TransferJob>> =
        jobDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeJob(id: Long): Flow<TransferJob?> =
        jobDao.observeById(id).map { it?.toDomain() }

    fun observeChunks(jobId: Long): Flow<List<TransferChunk>> =
        chunkDao.observeForJob(jobId).map { list -> list.map { it.toDomain() } }

    suspend fun insertJob(job: TransferJob): Long =
        jobDao.insert(TransferJobEntity.fromDomain(job))

    suspend fun saveChunks(chunks: List<TransferChunk>) {
        chunkDao.insertAll(chunks.map(TransferChunkEntity::fromDomain))
    }

    suspend fun setJobState(id: Long, state: TransferState, error: String? = null) {
        jobDao.updateState(id, state.name, System.currentTimeMillis(), error)
    }

    suspend fun setProgress(id: Long, transferredBytes: Long) {
        jobDao.updateProgress(id, transferredBytes, System.currentTimeMillis())
    }

    suspend fun setChunkState(id: Long, state: ChunkState) {
        chunkDao.updateState(id, state.name)
    }

    suspend fun setChunkProgress(id: Long, transferredBytes: Long, state: ChunkState, speed: Long) {
        chunkDao.updateProgress(id, transferredBytes, state.name, speed)
    }

    suspend fun setChunkMd5(id: Long, md5: String) {
        chunkDao.updateMd5(id, md5)
    }

    suspend fun setPriority(id: Long, priority: Int) {
        jobDao.updatePriority(id, priority)
    }

    suspend fun deleteJob(id: Long) {
        jobDao.deleteById(id)
    }

    suspend fun clearFinished() {
        jobDao.clearFinished()
    }

    suspend fun getJob(id: Long): TransferJob? = jobDao.getById(id)?.toDomain()

    suspend fun listChunks(jobId: Long): List<TransferChunk> =
        chunkDao.listForJob(jobId).map { it.toDomain() }
}
