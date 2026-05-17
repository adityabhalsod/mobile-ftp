package com.mobileftp.data.repository

import org.apache.ftpserver.ftplet.DefaultFtplet
import org.apache.ftpserver.ftplet.FtpRequest
import org.apache.ftpserver.ftplet.FtpSession
import org.apache.ftpserver.ftplet.FtpletResult
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks connected clients and bytes transferred through the embedded server.
 */
class ServerStatsFtplet(
    private val onBytes: (Long) -> Unit,
    private val onClientsChanged: (List<ConnectedClient>) -> Unit
) : DefaultFtplet() {

    private val sessions: MutableMap<String, ConnectedClient> = ConcurrentHashMap()

    override fun onConnect(session: FtpSession): FtpletResult {
        val client = ConnectedClient(
            sessionId = session.sessionId.toString(),
            ip = session.clientAddress?.address?.hostAddress ?: "?",
            username = session.user?.name ?: "anonymous",
            connectedAt = System.currentTimeMillis()
        )
        sessions[client.sessionId] = client
        onClientsChanged(sessions.values.toList())
        return FtpletResult.DEFAULT
    }

    override fun onDisconnect(session: FtpSession): FtpletResult {
        sessions.remove(session.sessionId.toString())
        onClientsChanged(sessions.values.toList())
        return FtpletResult.DEFAULT
    }

    override fun onLogin(session: FtpSession, request: FtpRequest): FtpletResult {
        val sessionId = session.sessionId.toString()
        sessions[sessionId]?.let {
            sessions[sessionId] = it.copy(username = session.user?.name ?: it.username)
            onClientsChanged(sessions.values.toList())
        }
        return FtpletResult.DEFAULT
    }

    override fun onUploadEnd(session: FtpSession, request: FtpRequest): FtpletResult {
        reportFromArgument(session, request)
        return FtpletResult.DEFAULT
    }

    override fun onDownloadEnd(session: FtpSession, request: FtpRequest): FtpletResult {
        reportFromArgument(session, request)
        return FtpletResult.DEFAULT
    }

    /**
     * Best-effort byte tally — resolves the file from the FTP request argument and
     * reports its current size on transfer completion.
     */
    private fun reportFromArgument(session: FtpSession, request: FtpRequest) {
        val home = session.user?.homeDirectory ?: return
        val arg = request.argument ?: return
        val target = if (arg.startsWith("/")) File(home, arg.removePrefix("/"))
        else File(File(home, session.fileSystemView?.workingDirectory?.absolutePath ?: ""), arg)
        if (target.exists()) onBytes(target.length())
    }
}
