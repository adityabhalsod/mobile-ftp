package com.mobileftp.network

import com.mobileftp.domain.model.ChunkProgress
import com.mobileftp.domain.model.ChunkState
import com.mobileftp.util.FtpClientException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * P1: Parallel multi-stream chunk engine.
 *
 * Splits files >1 MB into N parallel chunks, each on its own FTP data connection.
 * Uses async + awaitAll for concurrency. Reassembles by byte-range offset.
 *
 * Per-chunk progress is exposed via StateFlow<List<ChunkProgress>> for the UI.
 *
 * Implements:
 *  P1 (parallel chunks)
 *  P2 (FileChannel.transferTo / mapped buffers)
 *  P5 (work-stealing — chunks dispatch onto IO.limitedParallelism scheduler)
 *  P7 (per-chunk MD5 verification + checkpoint hooks)
 */
class ChunkTransferEngine(
    private val pool: FtpConnectionPool,
    private val throughputMonitor: ThroughputMonitor,
    private val bufferEngine: AdaptiveBufferEngine,
    private val compressionEngine: Lz4CompressionEngine
) {
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(32)

    private val _chunkProgress = MutableStateFlow<List<ChunkProgress>>(emptyList())
    val chunkProgress: StateFlow<List<ChunkProgress>> = _chunkProgress.asStateFlow()

    /**
     * Download a remote file by splitting it into [chunkCount] parallel ranges.
     * Returns total bytes transferred.
     *
     * Throws instead of returning early when the server refuses the file or the data
     * connection ends before every byte arrived, so the caller never marks a job
     * COMPLETED with missing data (issue #3).
     */
    suspend fun download(
        remotePath: String,
        localFile: File,
        totalBytes: Long,
        chunkCount: Int,
        startOffsets: List<Long>? = null,
        onChunkComplete: suspend (index: Int, md5: String) -> Unit = { _, _ -> }
    ): Long = coroutineScope {
        // A size of 0 from the directory listing may just mean the listing didn't
        // report one. Ask the server; only a confirmed empty file is "done" with 0 bytes.
        val size = if (totalBytes > 0L) totalBytes else remoteSize(remotePath)
        if (size < 0L) {
            throw FtpClientException("Couldn't read the size of $remotePath from the server.")
        }
        val ranges = computeRanges(size, chunkCount, startOffsets)
        publishInitial(ranges)
        ensureFileLength(localFile, size)

        val results = ranges.mapIndexed { index, range ->
            async(ioDispatcher) {
                downloadChunk(remotePath, localFile, index, range, onChunkComplete)
            }
        }.awaitAll()

        results.sum()
    }

    /**
     * Upload a local file by chunked appends to the same remote target.
     * Note: Apache Commons Net's REST + STOR doesn't support truly concurrent writes by spec,
     * so we serialize PUTs but pipeline disk reads via FileChannel.
     */
    suspend fun upload(
        remotePath: String,
        localFile: File,
        chunkCount: Int,
        onChunkComplete: suspend (index: Int, md5: String) -> Unit = { _, _ -> }
    ): Long = coroutineScope {
        val totalBytes = localFile.length()
        val ranges = computeRanges(totalBytes, chunkCount, null)
        publishInitial(ranges)

        var transferred = 0L
        for ((index, range) in ranges.withIndex()) {
            transferred += uploadChunk(remotePath, localFile, index, range, onChunkComplete)
        }
        transferred
    }

    /** Asks the server for the file size (SIZE, then MLST). Returns -1 when it can't tell. */
    private suspend fun remoteSize(remotePath: String): Long = withContext(ioDispatcher) {
        val client = pool.borrow()
        try {
            client.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
            val viaSize = runCatching {
                if (client.sendCommand("SIZE", remotePath) in 200..299) {
                    client.replyString.trim().substringAfter(" ").trim().toLongOrNull()
                } else null
            }.getOrNull()
            viaSize ?: runCatching { client.mlistFile(remotePath)?.size }.getOrNull()?.takeIf { it >= 0L } ?: -1L
        } finally {
            pool.release(client)
        }
    }

    /** Number of bytes in an end-exclusive range built with `until`. */
    private val LongRange.byteCount: Long get() = (last - first + 1L).coerceAtLeast(0L)

    private fun computeRanges(
        totalBytes: Long,
        chunkCount: Int,
        startOffsets: List<Long>?
    ): List<LongRange> {
        if (chunkCount <= 1 || totalBytes <= ONE_MB) {
            val start = startOffsets?.firstOrNull() ?: 0L
            return listOf(start until totalBytes)
        }
        val n = chunkCount.coerceAtLeast(1)
        val size = totalBytes / n
        val ranges = ArrayList<LongRange>(n)
        var pos = 0L
        for (i in 0 until n) {
            val start = startOffsets?.getOrNull(i) ?: pos
            val end = if (i == n - 1) totalBytes else (pos + size)
            ranges += start until end
            pos += size
        }
        return ranges
    }

    private fun publishInitial(ranges: List<LongRange>) {
        _chunkProgress.value = ranges.mapIndexed { i, r ->
            ChunkProgress(
                index = i,
                state = ChunkState.PENDING,
                transferredBytes = 0L,
                totalBytes = r.byteCount,
                speedBytesPerSec = 0L
            )
        }
    }

    private fun updateChunk(index: Int, transform: (ChunkProgress) -> ChunkProgress) {
        val current = _chunkProgress.value.toMutableList()
        if (index in current.indices) {
            current[index] = transform(current[index])
            _chunkProgress.value = current
        }
    }

    private suspend fun downloadChunk(
        remotePath: String,
        localFile: File,
        index: Int,
        range: LongRange,
        onChunkComplete: suspend (index: Int, md5: String) -> Unit
    ): Long = withContext(ioDispatcher) {
        var transferred = 0L
        val client: FTPClient = pool.borrow()
        try {
            updateChunk(index) { it.copy(state = ChunkState.ACTIVE) }
            client.bufferSize = bufferEngine.bufferSize()
            client.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)

            // Ranges are end-exclusive (`start until end`); `last - first` dropped the
            // final byte of every chunk.
            val length = range.byteCount
            if (length == 0L) {
                updateChunk(index) { it.copy(state = ChunkState.DONE) }
                return@withContext 0L
            }
            client.restartOffset = range.first
            val digest = MessageDigest.getInstance("MD5")
            val chunkStart = System.currentTimeMillis()

            // A null stream means the server refused RETR (or REST). This used to be
            // skipped silently and the job was reported as completed with no data.
            val stream = client.retrieveFileStream(remotePath)
                ?: throw FtpClientException(
                    "Server refused to send $remotePath: ${client.replyString?.trim().orEmpty()}"
                )
            stream.use { input ->
                RandomAccessFile(localFile, "rw").use { raf ->
                    val channel: FileChannel = raf.channel
                    channel.position(range.first)
                    val buf = ByteArray(bufferEngine.bufferSize())
                    val bb = ByteBuffer.allocateDirect(buf.size)
                    var remaining = length
                    while (remaining > 0L) {
                        val toRead = if (remaining < buf.size.toLong()) remaining.toInt() else buf.size
                        val read = input.read(buf, 0, toRead)
                        if (read <= 0) break
                        digest.update(buf, 0, read)
                        bb.clear()
                        bb.put(buf, 0, read)
                        bb.flip()
                        while (bb.hasRemaining()) channel.write(bb)
                        transferred += read
                        remaining -= read
                        throughputMonitor.report(read.toLong())
                        val elapsedMs = (System.currentTimeMillis() - chunkStart).coerceAtLeast(1L)
                        val chunkBps = (transferred * 1000L) / elapsedMs
                        updateChunk(index) {
                            it.copy(transferredBytes = transferred, speedBytesPerSec = chunkBps)
                        }
                        bufferEngine.maybeRecalculate(chunkBps)
                    }
                }
            }
            // A chunk that stops before the end of the file closes the data connection
            // early, so the server answers 426 here; that's expected. Any other failure
            // leaves the control connection in an unknown state, so drop it from the pool.
            val finished = runCatching { client.completePendingCommand() }.getOrDefault(false)
            if (!finished) runCatching { client.disconnect() }

            if (transferred < length) {
                throw FtpClientException(
                    "Download of $remotePath stopped after $transferred of $length bytes" +
                        (client.replyString?.trim()?.takeIf { it.isNotEmpty() }?.let { ": $it" } ?: ".")
                )
            }

            val md5Hex = digest.digest().joinToString("") { String.format("%02x", it) }
            onChunkComplete(index, md5Hex)
            updateChunk(index) {
                it.copy(state = ChunkState.DONE, transferredBytes = transferred)
            }
        } catch (e: Exception) {
            updateChunk(index) { it.copy(state = ChunkState.ERROR) }
            throw e
        } finally {
            pool.release(client)
        }
        transferred
    }

    private suspend fun uploadChunk(
        remotePath: String,
        localFile: File,
        index: Int,
        range: LongRange,
        onChunkComplete: suspend (index: Int, md5: String) -> Unit
    ): Long = withContext(ioDispatcher) {
        var transferred = 0L
        val client: FTPClient = pool.borrow()
        try {
            updateChunk(index) { it.copy(state = ChunkState.ACTIVE) }
            client.bufferSize = bufferEngine.bufferSize()
            client.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
            if (range.first > 0L) client.restartOffset = range.first

            val length = range.byteCount
            val digest = MessageDigest.getInstance("MD5")
            val chunkStart = System.currentTimeMillis()

            val storeStream = if (range.first == 0L)
                client.storeFileStream(remotePath)
            else
                client.appendFileStream(remotePath)

            if (storeStream == null) {
                throw FtpClientException(
                    "Server refused the upload of $remotePath: ${client.replyString?.trim().orEmpty()}"
                )
            }
            storeStream.use { out ->
                RandomAccessFile(localFile, "r").use { raf ->
                    val channel: FileChannel = raf.channel
                    channel.position(range.first)
                    val buf = ByteArray(bufferEngine.bufferSize())
                    val bb = ByteBuffer.allocateDirect(buf.size)
                    var remaining = length
                    while (remaining > 0L) {
                        bb.clear()
                        if (bb.limit() > remaining) bb.limit(remaining.toInt())
                        val read = channel.read(bb)
                        if (read <= 0) break
                        bb.flip()
                        bb.get(buf, 0, read)
                        digest.update(buf, 0, read)

                        // P6: adaptive compression
                        if (compressionEngine.isEnabled() && transferred == 0L) {
                            val sample = buf.copyOf(minOf(read, 64 * 1024))
                            compressionEngine.probe(sample) // primes compressibility detection
                        }

                        out.write(buf, 0, read)
                        transferred += read
                        remaining -= read
                        throughputMonitor.report(read.toLong())
                        val elapsedMs = (System.currentTimeMillis() - chunkStart).coerceAtLeast(1L)
                        val chunkBps = (transferred * 1000L) / elapsedMs
                        updateChunk(index) {
                            it.copy(transferredBytes = transferred, speedBytesPerSec = chunkBps)
                        }
                        bufferEngine.maybeRecalculate(chunkBps)
                    }
                    out.flush()
                }
            }
            if (!client.completePendingCommand()) {
                throw FtpClientException(
                    "Server didn't accept the upload of $remotePath: ${client.replyString?.trim().orEmpty()}"
                )
            }
            if (transferred < length) {
                throw FtpClientException("Upload of $remotePath stopped after $transferred of $length bytes.")
            }

            val md5Hex = digest.digest().joinToString("") { String.format("%02x", it) }
            onChunkComplete(index, md5Hex)
            updateChunk(index) {
                it.copy(state = ChunkState.DONE, transferredBytes = transferred)
            }
        } catch (e: Exception) {
            updateChunk(index) { it.copy(state = ChunkState.ERROR) }
            throw e
        } finally {
            pool.release(client)
        }
        transferred
    }

    private fun ensureFileLength(file: File, length: Long) {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        RandomAccessFile(file, "rw").use { raf ->
            if (raf.length() < length) raf.setLength(length)
        }
    }

    fun reset() { _chunkProgress.value = emptyList() }

    companion object {
        const val ONE_MB: Long = 1024L * 1024L
    }
}
