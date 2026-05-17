package com.mobileftp.domain.model

data class SpeedSample(
    val timestampMillis: Long,
    val bytesPerSecond: Long
)

data class ThroughputSnapshot(
    val current: Long,
    val peak: Long,
    val average: Long,
    val samples: List<SpeedSample>,
    val etaMillis: Long = 0L
)
