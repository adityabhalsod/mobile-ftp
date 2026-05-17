package com.mobileftp.network

import kotlin.math.max
import kotlin.math.min

/**
 * P3: Adaptive Buffer Sizing.
 * - 200 ms bandwidth probe at start: sample bytes-per-second using small probe payload.
 * - bufferSize = clamp(bandwidth / 100, 64KB, 4MB)
 * - Recalculates every 5 seconds based on observed throughput.
 */
class AdaptiveBufferEngine(
    initialBufferSize: Int = DEFAULT_BUFFER
) {
    @Volatile
    private var currentBufferSize: Int = initialBufferSize.coerceIn(MIN_BUFFER, MAX_BUFFER)

    @Volatile
    private var lastRecalcAt: Long = 0L

    fun bufferSize(): Int = currentBufferSize

    /** Run a 200ms bandwidth probe by transferring [probeSize] bytes via [transfer] and time it. */
    fun probe(probeSize: Int = 512 * 1024, transfer: (Int) -> Long): Int {
        val start = System.nanoTime()
        val transferred = transfer(probeSize)
        val elapsedNs = max(System.nanoTime() - start, 1L)
        val bytesPerSec = (transferred * 1_000_000_000L) / elapsedNs
        currentBufferSize = computeBufferSize(bytesPerSec)
        lastRecalcAt = System.currentTimeMillis()
        return currentBufferSize
    }

    /** Update buffer based on recently observed throughput, throttled to once every 5s. */
    fun maybeRecalculate(currentBytesPerSec: Long): Int {
        val now = System.currentTimeMillis()
        if (now - lastRecalcAt < RECALC_INTERVAL_MS) return currentBufferSize
        currentBufferSize = computeBufferSize(currentBytesPerSec)
        lastRecalcAt = now
        return currentBufferSize
    }

    private fun computeBufferSize(bytesPerSec: Long): Int {
        val target = (bytesPerSec / 100L).toInt()
        return min(max(target, MIN_BUFFER), MAX_BUFFER)
    }

    companion object {
        const val MIN_BUFFER: Int = 64 * 1024
        const val MAX_BUFFER: Int = 4 * 1024 * 1024
        const val DEFAULT_BUFFER: Int = 256 * 1024
        const val RECALC_INTERVAL_MS: Long = 5_000L
    }
}
