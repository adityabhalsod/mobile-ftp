package com.mobileftp.domain.model

enum class TransferDirection { DOWNLOAD, UPLOAD }
enum class TransferState { QUEUED, ACTIVE, PAUSED, COMPLETED, FAILED, CANCELLED }

data class TransferJob(
    val id: Long = 0L,
    val profileId: Long,
    val remotePath: String,
    val localPath: String,
    val fileName: String,
    val direction: TransferDirection,
    val totalBytes: Long,
    val transferredBytes: Long,
    val state: TransferState,
    val chunkCount: Int,
    val startedAt: Long,
    val updatedAt: Long,
    val error: String? = null,
    val md5: String? = null,
    val priority: Int = 0,
    val resumed: Boolean = false,
    val resumedFromBytes: Long = 0L
) {
    val progress: Float
        get() = if (totalBytes <= 0L) 0f else (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}
