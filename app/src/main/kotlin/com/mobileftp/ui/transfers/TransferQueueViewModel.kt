package com.mobileftp.ui.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.mobileftp.data.repository.TransferRepository
import com.mobileftp.domain.model.TransferChunk
import com.mobileftp.domain.model.TransferJob
import com.mobileftp.domain.model.TransferState
import com.mobileftp.domain.usecase.ResumeTransferUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransferQueueState(
    val active: List<TransferJob> = emptyList(),
    val pending: List<TransferJob> = emptyList(),
    val completed: List<TransferJob> = emptyList(),
    val totalSpeedBps: Long = 0L
)

@HiltViewModel
class TransferQueueViewModel @Inject constructor(
    private val repository: TransferRepository,
    private val workManager: WorkManager,
    private val resumeUseCase: ResumeTransferUseCase
) : ViewModel() {

    val state: StateFlow<TransferQueueState> = repository.observeJobs().map { jobs ->
        TransferQueueState(
            active = jobs.filter { it.state == TransferState.ACTIVE },
            pending = jobs.filter { it.state == TransferState.QUEUED || it.state == TransferState.PAUSED },
            completed = jobs.filter { it.state in setOf(TransferState.COMPLETED, TransferState.FAILED, TransferState.CANCELLED) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), TransferQueueState())

    fun observeChunks(jobId: Long): kotlinx.coroutines.flow.Flow<List<TransferChunk>> =
        repository.observeChunks(jobId)

    fun cancel(jobId: Long) {
        viewModelScope.launch {
            workManager.cancelUniqueWork("ftp-transfer-$jobId")
            repository.setJobState(jobId, TransferState.CANCELLED)
        }
    }

    fun retry(jobId: Long) {
        viewModelScope.launch { resumeUseCase(jobId) }
    }

    fun delete(jobId: Long) {
        viewModelScope.launch {
            workManager.cancelUniqueWork("ftp-transfer-$jobId")
            repository.deleteJob(jobId)
        }
    }

    fun reorder(jobId: Long, newPriority: Int) {
        viewModelScope.launch { repository.setPriority(jobId, newPriority) }
    }

    fun clearFinished() {
        viewModelScope.launch { repository.clearFinished() }
    }
}
