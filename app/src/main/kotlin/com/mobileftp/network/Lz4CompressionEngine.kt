package com.mobileftp.network

import net.jpountz.lz4.LZ4Factory

/**
 * P6: Adaptive LZ4 compression.
 * - Probe compressibility on first 64 KB.
 * - If ratio < 0.95 → compress chunk before send.
 * - Otherwise → passthrough (already compressed: video, zip, apk).
 */
class Lz4CompressionEngine(enabled: Boolean = true) {
    private val factory: LZ4Factory = LZ4Factory.fastestInstance()
    private val compressor = factory.fastCompressor()
    private val decompressor = factory.safeDecompressor()

    @Volatile
    private var globallyEnabled: Boolean = enabled

    fun setEnabled(enabled: Boolean) { globallyEnabled = enabled }
    fun isEnabled(): Boolean = globallyEnabled

    /** Returns true if the bytes are likely compressible (ratio < 0.95). */
    fun probe(sample: ByteArray): ProbeResult {
        if (sample.isEmpty()) return ProbeResult(compressible = false, ratio = 1f)
        val maxLen = compressor.maxCompressedLength(sample.size)
        val out = ByteArray(maxLen)
        val compressedLen = compressor.compress(sample, 0, sample.size, out, 0, maxLen)
        val ratio = compressedLen.toFloat() / sample.size.toFloat()
        return ProbeResult(compressible = ratio < 0.95f, ratio = ratio)
    }

    fun compress(input: ByteArray, length: Int = input.size): ByteArray {
        val maxLen = compressor.maxCompressedLength(length)
        val out = ByteArray(maxLen)
        val written = compressor.compress(input, 0, length, out, 0, maxLen)
        return out.copyOf(written)
    }

    fun decompress(input: ByteArray, expectedLength: Int): ByteArray {
        val out = ByteArray(expectedLength)
        decompressor.decompress(input, 0, input.size, out, 0)
        return out
    }

    data class ProbeResult(val compressible: Boolean, val ratio: Float)
}
