package com.mobileftp.util

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

object ChecksumUtils {

    fun md5OfBytes(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(bytes).toHex()
    }

    fun md5OfFileRange(file: File, startOffset: Long, length: Long): String {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val channel: FileChannel = fis.channel
            channel.position(startOffset)
            val buffer = ByteBuffer.allocateDirect(64 * 1024)
            var remaining = length
            while (remaining > 0L) {
                buffer.clear()
                if (buffer.limit() > remaining) buffer.limit(remaining.toInt())
                val read = channel.read(buffer)
                if (read <= 0) break
                buffer.flip()
                val temp = ByteArray(read)
                buffer.get(temp)
                digest.update(temp)
                remaining -= read
            }
        }
        return digest.digest().toHex()
    }

    fun md5OfFile(file: File): String =
        md5OfFileRange(file, 0L, file.length())

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) sb.append(String.format("%02x", b))
        return sb.toString()
    }
}
