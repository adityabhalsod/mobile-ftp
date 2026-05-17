package com.mobileftp.domain.usecase

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mobileftp.data.repository.TransferRepository
import com.mobileftp.domain.model.TransferState
import com.mobileftp.worker.FtpTransferWorker
import javax.inject.Inject

class ResumeTransferUseCase @Inject constructor(
    private val transfers: TransferRepository,
    private val workManager: WorkManager
) {
    suspend operator fun invoke(jobId: Long): Boolean {
        val job = transfers.getJob(jobId) ?: return false
        if (job.state == TransferState.COMPLETED) return false
        transfers.setJobState(jobId, TransferState.QUEUED)
        val request = OneTimeWorkRequestBuilder<FtpTransferWorker>()
            .setInputData(Data.Builder().putLong(FtpTransferWorker.KEY_JOB_ID, jobId).build())
            .addTag("ftp-transfer")
            .build()
        workManager.enqueueUniqueWork(
            "ftp-transfer-$jobId",
            ExistingWorkPolicy.REPLACE,
            request
        )
        return true
    }
}
