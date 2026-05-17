package com.mobileftp.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.mobileftp.MainActivity
import com.mobileftp.R
import com.mobileftp.data.repository.ConnectionProfileRepository
import com.mobileftp.data.repository.TransferRepository
import com.mobileftp.domain.model.ChunkState
import com.mobileftp.domain.model.TransferChunk
import com.mobileftp.domain.model.TransferDirection
import com.mobileftp.domain.model.TransferState
import com.mobileftp.network.AdaptiveBufferEngine
import com.mobileftp.network.ChunkTransferEngine
import com.mobileftp.network.FtpConnectionPool
import com.mobileftp.network.Lz4CompressionEngine
import com.mobileftp.network.ThroughputMonitor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

@HiltWorker
class FtpTransferWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val transferRepository: TransferRepository,
    private val profileRepository: ConnectionProfileRepository
) : CoroutineWorker(context, params) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val checkpointScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun doWork(): Result {
        ensureChannel()
        val jobId = inputData.getLong(KEY_JOB_ID, -1L)
        if (jobId <= 0L) return Result.failure()

        val job = transferRepository.getJob(jobId) ?: return Result.failure()
        val profile = profileRepository.get(job.profileId) ?: return Result.failure(
            workDataOf(KEY_ERROR to "Profile not found")
        )

        setForeground(buildForegroundInfo(job.fileName, 0))

        val pool = FtpConnectionPool(profile, profile.chunkCount.coerceAtLeast(1))
        val throughput = ThroughputMonitor()
        val buffer = AdaptiveBufferEngine()
        val compression = Lz4CompressionEngine(true)

        try {
            pool.warmUp(profile.chunkCount)
            throughput.start(targetBytes = job.totalBytes)
            transferRepository.setJobState(jobId, TransferState.ACTIVE)

            val engine = ChunkTransferEngine(pool, throughput, buffer, compression)

            // P7: build / resume chunks list
            val existingChunks = transferRepository.listChunks(jobId)
            val chunks = if (existingChunks.isEmpty()) {
                buildChunks(jobId, job.totalBytes, job.chunkCount).also {
                    transferRepository.saveChunks(it)
                }
            } else existingChunks
            val chunkOffsets = chunks.map { it.startOffset + it.transferredBytes }

            // P10: progress checkpoint loop — every 2s persist + emit progress
            val checkpointJob = checkpointScope.launch {
                while (true) {
                    delay(2_000L)
                    val totalNow = throughput.totalBytes()
                    transferRepository.setProgress(jobId, totalNow)
                    setProgressAsync(progressData(totalNow, job.totalBytes, throughput.snapshot.value.current))
                    setForeground(buildForegroundInfo(
                        job.fileName,
                        if (job.totalBytes > 0) (totalNow * 100 / job.totalBytes).toInt() else 0
                    ))
                }
            }

            engine.chunkProgress.onEach { progressList ->
                progressList.forEachIndexed { idx, p ->
                    val chunk = chunks.getOrNull(idx) ?: return@forEachIndexed
                    transferRepository.setChunkProgress(
                        chunk.id,
                        p.transferredBytes,
                        p.state,
                        p.speedBytesPerSec
                    )
                }
            }.launchIn(checkpointScope)

            val totalTransferred = when (job.direction) {
                TransferDirection.DOWNLOAD -> {
                    val localFile = File(job.localPath)
                    engine.download(
                        remotePath = job.remotePath,
                        localFile = localFile,
                        totalBytes = job.totalBytes,
                        chunkCount = job.chunkCount,
                        startOffsets = chunkOffsets,
                        onChunkComplete = { idx, md5 ->
                            chunks.getOrNull(idx)?.let { transferRepository.setChunkMd5(it.id, md5) }
                        }
                    )
                }
                TransferDirection.UPLOAD -> {
                    val localFile = File(job.localPath)
                    engine.upload(
                        remotePath = job.remotePath,
                        localFile = localFile,
                        chunkCount = job.chunkCount,
                        onChunkComplete = { idx, md5 ->
                            chunks.getOrNull(idx)?.let { transferRepository.setChunkMd5(it.id, md5) }
                        }
                    )
                }
            }

            checkpointJob.cancel()
            transferRepository.setProgress(jobId, totalTransferred)
            transferRepository.setJobState(jobId, TransferState.COMPLETED)
            return Result.success(workDataOf(KEY_BYTES to totalTransferred))
        } catch (t: Throwable) {
            transferRepository.setJobState(jobId, TransferState.FAILED, t.message)
            return Result.failure(workDataOf(KEY_ERROR to (t.message ?: "Transfer error")))
        } finally {
            throughput.stop()
            pool.closeAll()
            checkpointScope.cancel()
        }
    }

    private fun buildChunks(jobId: Long, totalBytes: Long, count: Int): List<TransferChunk> {
        val n = count.coerceAtLeast(1)
        val size = if (n > 0) totalBytes / n else totalBytes
        return (0 until n).map { i ->
            val start = i * size
            val end = if (i == n - 1) totalBytes else start + size
            TransferChunk(
                jobId = jobId,
                index = i,
                startOffset = start,
                endOffset = end,
                transferredBytes = 0L,
                state = ChunkState.PENDING
            )
        }
    }

    private fun progressData(total: Long, target: Long, currentBps: Long): Data =
        Data.Builder()
            .putLong(KEY_PROGRESS_BYTES, total)
            .putLong(KEY_PROGRESS_TARGET, target)
            .putLong(KEY_PROGRESS_BPS, currentBps)
            .build()

    private fun buildForegroundInfo(fileName: String, percent: Int): ForegroundInfo {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Transferring $fileName")
            .setContentText("$percent%")
            .setProgress(100, percent, false)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.transfer_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.transfer_channel_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun workDataOf(vararg pairs: Pair<String, Any?>): Data {
        val builder = Data.Builder()
        for ((k, v) in pairs) {
            when (v) {
                is Long -> builder.putLong(k, v)
                is Int -> builder.putInt(k, v)
                is String -> builder.putString(k, v)
                is Boolean -> builder.putBoolean(k, v)
                else -> Unit
            }
        }
        return builder.build()
    }

    companion object {
        const val KEY_JOB_ID: String = "job_id"
        const val KEY_PROGRESS_BYTES: String = "progress_bytes"
        const val KEY_PROGRESS_TARGET: String = "progress_target"
        const val KEY_PROGRESS_BPS: String = "progress_bps"
        const val KEY_BYTES: String = "bytes"
        const val KEY_ERROR: String = "error"
        const val CHANNEL_ID: String = "ftp_transfer_channel"
        const val NOTIFICATION_ID: Int = 2001
    }
}
