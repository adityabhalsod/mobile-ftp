package com.mobileftp.domain.model

enum class ChunkState { PENDING, ACTIVE, DONE, ERROR }

data class TransferChunk(
    val id: Long = 0L,
    val jobId: Long,
    val index: Int,
    val startOffset: Long,
    val endOffset: Long,
    val transferredBytes: Long,
    val state: ChunkState,
    val md5: String? = null,
    val speedBytesPerSec: Long = 0L
) {
    val length: Long get() = endOffset - startOffset
    val progress: Float
        get() = if (length <= 0L) 0f else (transferredBytes.toFloat() / length.toFloat()).coerceIn(0f, 1f)
}

data class ChunkProgress(
    val index: Int,
    val state: ChunkState,
    val transferredBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long
)
