package com.mobileftp.network

import com.mobileftp.domain.model.ConnectionProfile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient

/**
 * P8: Connection pool with borrow/return semantics.
 * - ArrayDeque + Mutex for thread-safe borrow/return.
 * - Pre-warm N connections on connect before first transfer.
 * - Pool size tracks chunk count N.
 * - No teardown between files — connections are recycled.
 */
class FtpConnectionPool(
    private val profile: ConnectionProfile,
    private val maxSize: Int
) {
    private val mutex = Mutex()
    private val available: ArrayDeque<FTPClient> = ArrayDeque()
    private val allClients: MutableList<FTPClient> = mutableListOf()

    @Volatile
    private var closed: Boolean = false

    /** Pre-warm [count] connections — limit to maxSize. */
    suspend fun warmUp(count: Int = maxSize) {
        val target = count.coerceAtMost(maxSize)
        mutex.withLock {
            if (closed) return
            while (allClients.size < target) {
                val c = createClient()
                allClients += c
                available.addLast(c)
            }
        }
    }

    /** Borrow a connected, logged-in FTP client. Creates one if pool is empty and below cap. */
    suspend fun borrow(): FTPClient {
        mutex.withLock {
            if (closed) error("Connection pool is closed")
            available.removeFirstOrNull()?.let { client ->
                if (client.isConnected && client.sendNoOp()) return client
                runCatching { client.disconnect() }
                allClients.remove(client)
            }
            if (allClients.size < maxSize) {
                val c = createClient()
                allClients += c
                return c
            }
        }
        // Pool exhausted — wait briefly and retry once.
        kotlinx.coroutines.delay(50L)
        return borrow()
    }

    /** Return a client to the pool. If broken, drop it. */
    suspend fun release(client: FTPClient) {
        mutex.withLock {
            if (closed) {
                runCatching { client.logout() }
                runCatching { client.disconnect() }
                allClients.remove(client)
                return
            }
            if (!client.isConnected) {
                allClients.remove(client)
                return
            }
            available.addLast(client)
        }
    }

    suspend fun closeAll() {
        mutex.withLock {
            closed = true
            for (c in allClients) {
                runCatching { c.logout() }
                runCatching { c.disconnect() }
            }
            allClients.clear()
            available.clear()
        }
    }

    fun size(): Int = allClients.size
    fun availableCount(): Int = available.size

    private fun createClient(): FTPClient {
        val client: FTPClient = if (profile.ftps) FTPSClient("TLS", false) else FTPClient()
        SocketTuner.applyToFtpClient(client)
        client.connect(profile.host, profile.port)
        if (!client.login(profile.username, profile.password)) {
            runCatching { client.disconnect() }
            error("FTP login failed for ${profile.username}@${profile.host}")
        }
        client.setFileType(FTP.BINARY_FILE_TYPE)
        client.setFileTransferMode(FTP.STREAM_TRANSFER_MODE)
        if (profile.passive) client.enterLocalPassiveMode() else client.enterLocalActiveMode()
        client.bufferSize = AdaptiveBufferEngine.DEFAULT_BUFFER
        return client
    }
}
