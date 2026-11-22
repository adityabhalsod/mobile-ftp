package com.mobileftp.util

import com.mobileftp.domain.model.ConnectionProfile
import java.io.EOFException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns low-level FTP / socket exceptions into messages a user can act on.
 * Before this, the client screen showed raw text such as
 * "failed to connect to /10.0.0.5 (port 21) from /:: (port 0) after 30000ms"
 * or simply "null".
 */
object FtpErrors {

    fun describe(t: Throwable, profile: ConnectionProfile? = null): String {
        val target = profile?.let { "${it.host}:${it.port}" } ?: "the server"
        val chain = generateSequence(t) { it.cause }.toList()
        fun has(type: Class<*>) = chain.any { type.isInstance(it) }
        return when {
            t is FtpClientException -> t.message ?: "FTP error"
            has(UnknownHostException::class.java) -> "Host not found: ${profile?.host ?: ""}. Check the address."
            has(SocketTimeoutException::class.java) -> "Timed out connecting to $target. Is the server running and reachable?"
            has(NoRouteToHostException::class.java) -> "No route to $target. Are you on the same network?"
            has(ConnectException::class.java) -> "Connection refused by $target. Check the host and port."
            has(SSLException::class.java) -> "TLS handshake failed. Check whether the server supports FTPS (explicit TLS)."
            has(EOFException::class.java) -> "The server closed the connection unexpectedly."
            else -> t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
        }
    }
}

/** An FTP-level failure whose message is already user friendly. */
class FtpClientException(message: String) : Exception(message)
