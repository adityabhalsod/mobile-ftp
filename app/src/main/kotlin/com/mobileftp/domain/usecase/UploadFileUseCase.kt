package com.mobileftp.domain.usecase

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mobileftp.data.repository.TransferRepository
import com.mobileftp.domain.model.TransferDirection
import com.mobileftp.domain.model.TransferJob
import com.mobileftp.domain.model.TransferState
import com.mobileftp.worker.FtpTransferWorker
import java.io.File
import javax.inject.Inject

class UploadFileUseCase @Inject constructor(
    private val transfers: TransferRepository,
    private val workManager: WorkManager
) {
    suspend operator fun invoke(
        profileId: Long,
        localFile: File,
        remoteDirectory: String,
        chunkCount: Int
    ): Long {
        val now = System.currentTimeMillis()
        val remotePath = if (remoteDirectory.endsWith("/")) "$remoteDirectory${localFile.name}"
        else "$remoteDirectory/${localFile.name}"
        val job = TransferJob(
            profileId = profileId,
            remotePath = remotePath,
            localPath = localFile.absolutePath,
            fileName = localFile.name,
            direction = TransferDirection.UPLOAD,
            totalBytes = localFile.length(),
            transferredBytes = 0L,
            state = TransferState.QUEUED,
            chunkCount = chunkCount,
            startedAt = now,
            updatedAt = now
        )
        val id = transfers.insertJob(job)
        val request = OneTimeWorkRequestBuilder<FtpTransferWorker>()
            .setInputData(Data.Builder().putLong(FtpTransferWorker.KEY_JOB_ID, id).build())
            .addTag("ftp-transfer")
            .build()
        workManager.enqueueUniqueWork(
            "ftp-transfer-$id",
            ExistingWorkPolicy.KEEP,
            request
        )
        return id
    }
}
