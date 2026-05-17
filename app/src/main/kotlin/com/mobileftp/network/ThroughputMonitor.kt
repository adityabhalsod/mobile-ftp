package com.mobileftp.network

import com.mobileftp.domain.model.SpeedSample
import com.mobileftp.domain.model.ThroughputSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * P10: Real-time throughput monitor.
 * - Sample every 250 ms.
 * - 2-second sliding window for "current" speed.
 * - Tracks current / peak / average + ETA.
 */
class ThroughputMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val totalBytes = AtomicLong(0L)
    private val targetBytes = AtomicLong(0L)
    @Volatile private var lastSampledTotal: Long = 0L
    @Volatile private var lastSampledAt: Long = System.currentTimeMillis()
    @Volatile private var peakBps: Long = 0L

    private val window: ArrayDeque<SpeedSample> = ArrayDeque()
    private val windowMaxSize: Int = 8 // 8 * 250ms = 2 second sliding window
    private val historyMaxSize: Int = 60 // last 60 samples for graph

    private val _snapshot = MutableStateFlow(
        ThroughputSnapshot(0L, 0L, 0L, emptyList(), 0L)
    )
    val snapshot: StateFlow<ThroughputSnapshot> = _snapshot.asStateFlow()

    private var loop: Job? = null

    fun start(targetBytes: Long = 0L) {
        this.targetBytes.set(targetBytes)
        this.totalBytes.set(0L)
        this.lastSampledTotal = 0L
        this.lastSampledAt = System.currentTimeMillis()
        this.peakBps = 0L
        this.window.clear()
        loop?.cancel()
        loop = scope.launch {
            val history = ArrayDeque<SpeedSample>()
            while (true) {
                delay(SAMPLE_INTERVAL_MS)
                val now = System.currentTimeMillis()
                val total = totalBytes.get()
                val deltaBytes = total - lastSampledTotal
                val deltaMs = (now - lastSampledAt).coerceAtLeast(1L)
                val bps = (deltaBytes * 1000L) / deltaMs
                lastSampledTotal = total
                lastSampledAt = now

                synchronized(window) {
                    window.addLast(SpeedSample(now, bps))
                    while (window.size > windowMaxSize) window.removeFirst()
                }

                history.addLast(SpeedSample(now, bps))
                while (history.size > historyMaxSize) history.removeFirst()

                if (bps > peakBps) peakBps = bps

                val avg = synchronized(window) {
                    if (window.isEmpty()) 0L else window.sumOf { it.bytesPerSecond } / window.size
                }
                val target = this@ThroughputMonitor.targetBytes.get()
                val eta = if (target > 0L && avg > 0L) {
                    val remaining = (target - total).coerceAtLeast(0L)
                    (remaining * 1000L) / avg
                } else 0L

                _snapshot.value = ThroughputSnapshot(
                    current = avg,
                    peak = peakBps,
                    average = if (history.isNotEmpty()) history.sumOf { it.bytesPerSecond } / history.size else 0L,
                    samples = history.toList(),
                    etaMillis = eta
                )
            }
        }
    }

    fun report(byteDelta: Long) {
        if (byteDelta > 0L) totalBytes.addAndGet(byteDelta)
    }

    fun setTarget(bytes: Long) { targetBytes.set(bytes) }
    fun totalBytes(): Long = totalBytes.get()

    fun stop() {
        loop?.cancel()
        loop = null
    }

    fun reset() {
        totalBytes.set(0L)
        targetBytes.set(0L)
        lastSampledTotal = 0L
        lastSampledAt = System.currentTimeMillis()
        peakBps = 0L
        synchronized(window) { window.clear() }
        _snapshot.value = ThroughputSnapshot(0L, 0L, 0L, emptyList(), 0L)
    }

    companion object {
        const val SAMPLE_INTERVAL_MS: Long = 250L
    }
}
