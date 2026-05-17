package com.mobileftp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mobileftp.domain.model.ChunkState
import com.mobileftp.domain.model.TransferChunk

@Entity(
    tableName = "transfer_chunks",
    foreignKeys = [
        ForeignKey(
            entity = TransferJobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("jobId")]
)
data class TransferChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val jobId: Long,
    val index: Int,
    val startOffset: Long,
    val endOffset: Long,
    val transferredBytes: Long,
    val state: String,
    val md5: String?,
    val speedBytesPerSec: Long
) {
    fun toDomain(): TransferChunk = TransferChunk(
        id = id,
        jobId = jobId,
        index = index,
        startOffset = startOffset,
        endOffset = endOffset,
        transferredBytes = transferredBytes,
        state = ChunkState.valueOf(state),
        md5 = md5,
        speedBytesPerSec = speedBytesPerSec
    )

    companion object {
        fun fromDomain(c: TransferChunk): TransferChunkEntity = TransferChunkEntity(
            id = c.id,
            jobId = c.jobId,
            index = c.index,
            startOffset = c.startOffset,
            endOffset = c.endOffset,
            transferredBytes = c.transferredBytes,
            state = c.state.name,
            md5 = c.md5,
            speedBytesPerSec = c.speedBytesPerSec
        )
    }
}
