package com.mobileftp.network

import org.apache.commons.net.ftp.FTPClient
import java.net.Socket

/**
 * P4: TCP socket tuning — large send/receive windows, no Nagle, keepalive.
 *
 * `FTPClient.setTcpNoDelay()` / `setKeepAlive()` mutate the *currently active*
 * socket. Calling them before `connect()` throws an NPE because the socket
 * field is still null. We therefore split tuning into two phases:
 *
 *   1. [preConnect]  — runs before `connect()`, sets fields the FTPClient
 *                      copies onto its socket factory (timeouts, buffer sizes)
 *   2. [postConnect] — runs after `connect()` succeeds, applies the socket-
 *                      level options that need a live socket
 */
object SocketTuner {

    private const val SEND_BUF: Int = 4 * 1024 * 1024
    private const val RECV_BUF: Int = 4 * 1024 * 1024

    /** Direct socket tuning — used for data sockets we own. */
    fun tune(socket: Socket) {
        runCatching { socket.sendBufferSize = SEND_BUF }
        runCatching { socket.receiveBufferSize = RECV_BUF }
        runCatching { socket.tcpNoDelay = true }
        runCatching { socket.keepAlive = true }
        runCatching { socket.reuseAddress = true }
        runCatching { socket.soTimeout = 60_000 }
    }

    /**
     * Pre-connect tuning — set anything that's stored as an FTPClient field
     * and copied onto the socket during `connect()`. Safe to call before login.
     */
    fun preConnect(client: FTPClient) {
        client.setSendBufferSize(SEND_BUF)
        client.setReceiveBufferSize(RECV_BUF)
        client.connectTimeout = 30_000
        client.defaultTimeout = 30_000
        @Suppress("DEPRECATION")
        client.setDataTimeout(120_000)
        @Suppress("DEPRECATION")
        client.controlKeepAliveTimeout = 30L
    }

    /**
     * Post-connect tuning — must be called *after* `client.connect(host, port)`
     * has succeeded so the underlying socket exists. Failures are swallowed so
     * a missing kernel feature (e.g. keepalive on some Android builds) doesn't
     * tear down a working FTP session.
     */
    fun postConnect(client: FTPClient) {
        runCatching { client.setTcpNoDelay(true) }
        runCatching { client.setKeepAlive(true) }
    }

    /**
     * Convenience for callers that haven't yet connected and don't care about
     * the post-connect step (e.g. one-shot probes).
     */
    @Deprecated(
        "Split call sites into preConnect()+connect()+postConnect()",
        ReplaceWith("preConnect(client)")
    )
    fun applyToFtpClient(client: FTPClient): Unit = preConnect(client)
}
