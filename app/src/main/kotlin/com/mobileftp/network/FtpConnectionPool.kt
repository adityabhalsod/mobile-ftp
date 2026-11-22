package com.mobileftp.network

import com.mobileftp.domain.model.ConnectionProfile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.net.ftp.FTP
import com.mobileftp.util.FtpClientException
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
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

    /**
     * Borrow a connected, logged-in FTP client. Creates one if the pool is empty
     * and below cap; otherwise waits for a client to be released (bounded).
     */
    suspend fun borrow(): FTPClient {
        val deadline = System.currentTimeMillis() + BORROW_TIMEOUT_MS
        while (true) {
            mutex.withLock {
                if (closed) throw FtpClientException("Not connected")
                while (true) {
                    val client = available.removeFirstOrNull() ?: break
                    if (client.isConnected && runCatching { client.sendNoOp() }.getOrDefault(false)) return client
                    runCatching { client.disconnect() }
                    allClients.remove(client)
                }
                if (allClients.size < maxSize) {
                    val c = createClient()
                    allClients += c
                    return c
                }
            }
            if (System.currentTimeMillis() > deadline) {
                throw FtpClientException("All connections are busy. Try again in a moment.")
            }
            // Pool exhausted — wait briefly and retry.
            kotlinx.coroutines.delay(50L)
        }
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

    private companion object {
        const val BORROW_TIMEOUT_MS: Long = 60_000L
    }

    fun size(): Int = allClients.size
    fun availableCount(): Int = available.size

    private fun createClient(): FTPClient {
        val client: FTPClient = if (profile.ftps) FTPSClient("TLS", false) else FTPClient()
        // UTF-8 control channel so non-ASCII file names list and transfer correctly.
        client.controlEncoding = "UTF-8"
        SocketTuner.preConnect(client)
        try {
            client.connect(profile.host, profile.port)
            // Socket-level options can only be set once the socket actually exists.
            SocketTuner.postConnect(client)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                throw FtpClientException(
                    "Server refused the connection: ${client.replyString?.trim().orEmpty()}"
                )
            }
            // Blank username used to be sent as-is and always failed — fall back to anonymous login.
            val user = profile.username.trim().ifEmpty { "anonymous" }
            val pass = if (profile.username.isBlank() && profile.password.isEmpty()) "anonymous@" else profile.password
            if (!client.login(user, pass)) {
                throw FtpClientException(
                    "Login failed for $user@${profile.host}. Check the username and password."
                )
            }
            if (client is FTPSClient) {
                // Explicit FTPS: protect the data channel too. Without PBSZ/PROT P most
                // servers reject LIST/RETR with "522 Data connections must be encrypted".
                client.execPBSZ(0)
                client.execPROT("P")
            }
            client.setFileType(FTP.BINARY_FILE_TYPE)
            client.setFileTransferMode(FTP.STREAM_TRANSFER_MODE)
            if (profile.passive) client.enterLocalPassiveMode() else client.enterLocalActiveMode()
            client.bufferSize = AdaptiveBufferEngine.DEFAULT_BUFFER
            return client
        } catch (t: Throwable) {
            // Never leak a half-open socket on failure.
            runCatching { if (client.isConnected) client.disconnect() }
            throw t
        }
    }
}
