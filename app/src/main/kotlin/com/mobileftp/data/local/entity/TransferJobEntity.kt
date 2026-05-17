package com.mobileftp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mobileftp.domain.model.TransferDirection
import com.mobileftp.domain.model.TransferJob
import com.mobileftp.domain.model.TransferState

@Entity(tableName = "transfer_jobs")
data class TransferJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val profileId: Long,
    val remotePath: String,
    val localPath: String,
    val fileName: String,
    val direction: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val state: String,
    val chunkCount: Int,
    val startedAt: Long,
    val updatedAt: Long,
    val error: String?,
    val md5: String?,
    val priority: Int,
    val resumed: Boolean,
    val resumedFromBytes: Long
) {
    fun toDomain(): TransferJob = TransferJob(
        id = id,
        profileId = profileId,
        remotePath = remotePath,
        localPath = localPath,
        fileName = fileName,
        direction = TransferDirection.valueOf(direction),
        totalBytes = totalBytes,
        transferredBytes = transferredBytes,
        state = TransferState.valueOf(state),
        chunkCount = chunkCount,
        startedAt = startedAt,
        updatedAt = updatedAt,
        error = error,
        md5 = md5,
        priority = priority,
        resumed = resumed,
        resumedFromBytes = resumedFromBytes
    )

    companion object {
        fun fromDomain(j: TransferJob): TransferJobEntity = TransferJobEntity(
            id = j.id,
            profileId = j.profileId,
            remotePath = j.remotePath,
            localPath = j.localPath,
            fileName = j.fileName,
            direction = j.direction.name,
            totalBytes = j.totalBytes,
            transferredBytes = j.transferredBytes,
            state = j.state.name,
            chunkCount = j.chunkCount,
            startedAt = j.startedAt,
            updatedAt = j.updatedAt,
            error = j.error,
            md5 = j.md5,
            priority = j.priority,
            resumed = j.resumed,
            resumedFromBytes = j.resumedFromBytes
        )
    }
}
