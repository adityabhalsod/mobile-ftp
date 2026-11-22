package com.mobileftp.data.repository

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class ServerTransferDirection { UPLOAD, DOWNLOAD }

enum class ServerTransferStatus { ACTIVE, COMPLETED, FAILED }

/** One STOR/APPE/STOU/RETR handled by the embedded server. */
data class ServerTransfer(
    val id: Long,
    val sessionId: String,
    val clientIp: String,
    val fileName: String,
    val direction: ServerTransferDirection,
    val bytes: Long,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: ServerTransferStatus = ServerTransferStatus.ACTIVE,
    /** FTP reply for a failed transfer, e.g. "426 Data connection error". */
    val error: String? = null
) {
    /** Average speed over the life of the transfer, in bytes per second. */
    fun averageBps(now: Long = System.currentTimeMillis()): Long {
        val elapsed = ((finishedAt ?: now) - startedAt).coerceAtLeast(1L)
        return bytes * 1000L / elapsed
    }
}

/** Everything the dashboard shows about the server's clients at one point in time. */
data class ServerActivitySnapshot(
    val clients: List<ConnectedClient>,
    /** Active transfers first, then the most recent finished ones. */
    val transfers: List<ServerTransfer>,
    val totalBytes: Long
)

/**
 * Thread-safe record of what FTP clients are doing on the embedded server.
 *
 * Fed by [ServerStatsFtplet] (connections, commands, transfer start/end) and by
 * [TrackingFileSystemFactory] (bytes as they flow through the data connection),
 * so the dashboard sees live progress instead of one lump when a transfer ends.
 */
class ServerActivityTracker(
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxFinished: Int = MAX_FINISHED
) {
    private val clients = ConcurrentHashMap<String, ConnectedClient>()
    private val active = ConcurrentHashMap<String, ActiveTransfer>()
    private val finished = ArrayDeque<ServerTransfer>()
    private val totalBytes = AtomicLong(0L)
    private val nextId = AtomicLong(1L)

    private class ActiveTransfer(val base: ServerTransfer) {
        val bytes = AtomicLong(0L)
        fun snapshot(): ServerTransfer = base.copy(bytes = bytes.get())
    }

    fun onConnect(sessionId: String, ip: String) {
        val now = clock()
        clients[sessionId] = ConnectedClient(
            sessionId = sessionId,
            ip = ip,
            username = "anonymous",
            connectedAt = now,
            lastActivityAt = now
        )
    }

    fun onLogin(sessionId: String, username: String?) {
        clients.computeIfPresent(sessionId) { _, c ->
            c.copy(username = username ?: c.username, lastActivityAt = clock())
        }
    }

    /** Records the latest FTP command so idle-looking sessions still show what they are doing. */
    fun onCommand(sessionId: String, command: String, argument: String?) {
        val shown = when (command.uppercase()) {
            // Never show credentials on screen.
            "PASS" -> "PASS ••••"
            else -> if (argument.isNullOrBlank()) command.uppercase() else "${command.uppercase()} $argument"
        }
        clients.computeIfPresent(sessionId) { _, c ->
            c.copy(lastCommand = shown, lastActivityAt = clock())
        }
    }

    fun onDisconnect(sessionId: String) {
        clients.remove(sessionId)
        // A client that drops mid-transfer never gets an "end" callback.
        active.remove(sessionId)?.let { finish(it, ServerTransferStatus.FAILED, "Client disconnected") }
    }

    fun transferStarted(sessionId: String, fileName: String, direction: ServerTransferDirection) {
        // A new transfer on the same session means the previous one is over.
        active.remove(sessionId)?.let { finish(it, ServerTransferStatus.FAILED, "Interrupted") }
        val ip = clients[sessionId]?.ip ?: "?"
        active[sessionId] = ActiveTransfer(
            ServerTransfer(
                id = nextId.getAndIncrement(),
                sessionId = sessionId,
                clientIp = ip,
                fileName = fileName,
                direction = direction,
                bytes = 0L,
                startedAt = clock()
            )
        )
    }

    /** Called from the data connection thread for every chunk read or written. */
    fun addBytes(sessionId: String?, delta: Long) {
        if (sessionId == null || delta <= 0L) return
        val transfer = active[sessionId] ?: return
        transfer.bytes.addAndGet(delta)
        totalBytes.addAndGet(delta)
    }

    fun transferFinished(sessionId: String, replyCode: Int, replyMessage: String?) {
        val transfer = active.remove(sessionId) ?: return
        val ok = replyCode in 200..299
        val error = if (ok) null else listOfNotNull(replyCode.toString(), replyMessage?.trim()).joinToString(" ")
        finish(transfer, if (ok) ServerTransferStatus.COMPLETED else ServerTransferStatus.FAILED, error)
        clients.computeIfPresent(sessionId) { _, c -> c.copy(lastActivityAt = clock()) }
    }

    private fun finish(transfer: ActiveTransfer, status: ServerTransferStatus, error: String?) {
        val done = transfer.snapshot().copy(finishedAt = clock(), status = status, error = error)
        synchronized(finished) {
            finished.addFirst(done)
            while (finished.size > maxFinished) finished.removeLast()
        }
    }

    fun snapshot(): ServerActivitySnapshot {
        val activeNow = active.values.map { it.snapshot() }.sortedBy { it.startedAt }
        val bySession = activeNow.associateBy { it.sessionId }
        val clientList = clients.values
            .sortedBy { it.connectedAt }
            .map { c -> c.copy(currentTransfer = bySession[c.sessionId]) }
        val recent = synchronized(finished) { finished.toList() }
        return ServerActivitySnapshot(clientList, activeNow + recent, totalBytes.get())
    }

    /** Called when the server stops: sessions are gone, history is kept for the dashboard. */
    fun clearSessions() {
        active.keys.toList().forEach { id ->
            active.remove(id)?.let { finish(it, ServerTransferStatus.FAILED, "Server stopped") }
        }
        clients.clear()
    }

    fun resetCounters() {
        totalBytes.set(0L)
        synchronized(finished) { finished.clear() }
    }

    companion object {
        const val MAX_FINISHED: Int = 20
    }
}
