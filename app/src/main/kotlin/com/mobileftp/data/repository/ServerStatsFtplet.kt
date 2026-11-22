package com.mobileftp.data.repository

import org.apache.ftpserver.ftplet.DefaultFtplet
import org.apache.ftpserver.ftplet.FtpReply
import org.apache.ftpserver.ftplet.FtpRequest
import org.apache.ftpserver.ftplet.FtpSession
import org.apache.ftpserver.ftplet.FtpletResult

/**
 * Feeds [ServerActivityTracker] with connections, commands and transfer
 * start/end events from the embedded server. Bytes are counted live by
 * [TrackingFileSystemFactory].
 */
class ServerStatsFtplet(
    private val tracker: ServerActivityTracker
) : DefaultFtplet() {

    private val FtpSession.key: String get() = sessionId.toString()

    override fun onConnect(session: FtpSession): FtpletResult {
        tracker.onConnect(session.key, session.clientAddress?.address?.hostAddress ?: "?")
        return FtpletResult.DEFAULT
    }

    override fun onDisconnect(session: FtpSession): FtpletResult {
        tracker.onDisconnect(session.key)
        return FtpletResult.DEFAULT
    }

    // DefaultFtplet returns null for commands it has no hook for, so these
    // overrides must keep the nullable Java return type.
    override fun beforeCommand(session: FtpSession, request: FtpRequest): FtpletResult? {
        tracker.onCommand(session.key, request.command ?: "?", request.argument)
        val direction = when (request.command?.uppercase()) {
            "STOR", "APPE", "STOU" -> ServerTransferDirection.UPLOAD
            "RETR" -> ServerTransferDirection.DOWNLOAD
            else -> null
        }
        if (direction != null) {
            bindView(session)
            tracker.transferStarted(session.key, displayName(request.argument), direction)
        }
        return super.beforeCommand(session, request)
    }

    override fun afterCommand(session: FtpSession, request: FtpRequest, reply: FtpReply?): FtpletResult? {
        when (request.command?.uppercase()) {
            "STOR", "APPE", "STOU", "RETR" ->
                tracker.transferFinished(session.key, reply?.code ?: 0, reply?.message)
        }
        return super.afterCommand(session, request, reply)
    }

    override fun onLogin(session: FtpSession, request: FtpRequest): FtpletResult? {
        bindView(session)
        tracker.onLogin(session.key, session.user?.name)
        return FtpletResult.DEFAULT
    }

    /** Lets the session's file system view attribute streamed bytes to this session. */
    private fun bindView(session: FtpSession) {
        (session.fileSystemView as? TrackingFileSystemView)?.sessionId = session.key
    }

    private fun displayName(argument: String?): String =
        argument?.trimEnd('/')?.substringAfterLast('/')?.ifBlank { null } ?: argument ?: "(unnamed)"
}
