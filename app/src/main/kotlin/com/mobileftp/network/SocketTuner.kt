package com.mobileftp.network

import org.apache.commons.net.ftp.FTPClient
import java.net.Socket

/**
 * P4: TCP socket tuning — large send/receive windows, no Nagle, keepalive.
 * Applied to every data and control socket the app uses.
 */
object SocketTuner {

    private const val SEND_BUF: Int = 4 * 1024 * 1024
    private const val RECV_BUF: Int = 4 * 1024 * 1024

    fun tune(socket: Socket) {
        runCatching { socket.sendBufferSize = SEND_BUF }
        runCatching { socket.receiveBufferSize = RECV_BUF }
        runCatching { socket.tcpNoDelay = true }
        runCatching { socket.keepAlive = true }
        runCatching { socket.reuseAddress = true }
        runCatching { socket.soTimeout = 60_000 }
    }

    fun applyToFtpClient(client: FTPClient) {
        client.setSendBufferSize(SEND_BUF)
        client.setReceiveBufferSize(RECV_BUF)
        client.setTcpNoDelay(true)
        client.setKeepAlive(true)
        client.connectTimeout = 30_000
        client.defaultTimeout = 30_000
        // Apache Commons Net 3.10 still uses primitive timeouts in milliseconds/seconds
        // for these two setters.
        @Suppress("DEPRECATION")
        client.setDataTimeout(120_000)
        @Suppress("DEPRECATION")
        client.controlKeepAliveTimeout = 30L
    }
}
