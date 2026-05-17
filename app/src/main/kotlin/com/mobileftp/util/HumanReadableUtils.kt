package com.mobileftp.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

object HumanReadableUtils {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.US)
    private val shortDateFormat = SimpleDateFormat("MMM d HH:mm", Locale.US)

    fun bytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format(Locale.US, "%.2f %sB", bytes / 1024.0.pow(exp.toDouble()), pre)
    }

    fun bytesPerSecond(bps: Long): String = "${bytes(bps)}/s"

    fun durationMillis(ms: Long): String {
        if (ms <= 0L) return "—"
        val totalSeconds = ms / 1000L
        val h = totalSeconds / 3600L
        val m = (totalSeconds % 3600L) / 60L
        val s = totalSeconds % 60L
        return when {
            h > 0L -> String.format(Locale.US, "%dh %02dm", h, m)
            m > 0L -> String.format(Locale.US, "%dm %02ds", m, s)
            else -> String.format(Locale.US, "%ds", s)
        }
    }

    fun timestamp(epochMillis: Long): String =
        if (epochMillis <= 0L) "—" else dateFormat.format(Date(epochMillis))

    fun shortTimestamp(epochMillis: Long): String =
        if (epochMillis <= 0L) "—" else shortDateFormat.format(Date(epochMillis))

    fun relative(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        if (epochMillis <= 0L) return "never"
        val diff = abs(now - epochMillis)
        return when {
            diff < 60_000L -> "just now"
            diff < 3_600_000L -> "${diff / 60_000L}m ago"
            diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
            diff < 30L * 86_400_000L -> "${diff / 86_400_000L}d ago"
            else -> shortTimestamp(epochMillis)
        }
    }
}
