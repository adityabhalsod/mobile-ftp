package com.mobileftp.data.repository

import android.content.Context
import android.util.Log
import com.mobileftp.domain.model.ServerConfig
import com.mobileftp.util.StorageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.ftplet.FtpException
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory
import org.apache.ftpserver.usermanager.SaltedPasswordEncryptor
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission
import org.apache.ftpserver.usermanager.impl.WritePermission
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ServerStatus { STOPPED, STARTING, RUNNING, ERROR }

data class ConnectedClient(
    val sessionId: String,
    val ip: String,
    val username: String,
    val connectedAt: Long
)

@Singleton
class FtpServerRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var ftpServer: FtpServer? = null

    private val _status = MutableStateFlow(ServerStatus.STOPPED)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _config = MutableStateFlow(ServerConfig())
    val config: StateFlow<ServerConfig> = _config.asStateFlow()

    private val _bytesTransferred = MutableStateFlow(0L)
    val bytesTransferred: StateFlow<Long> = _bytesTransferred.asStateFlow()

    private val _connectedClients = MutableStateFlow<List<ConnectedClient>>(emptyList())
    val connectedClients: StateFlow<List<ConnectedClient>> = _connectedClients.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    suspend fun start(config: ServerConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            stopInternal()
            _status.value = ServerStatus.STARTING
            _config.value = config

            val rootDir = resolveRoot(config)
            if (!rootDir.exists()) rootDir.mkdirs()

            val serverFactory = FtpServerFactory()
            val listenerFactory = ListenerFactory().apply {
                port = config.port
                idleTimeout = 300
            }
            serverFactory.addListener("default", listenerFactory.createListener())

            val userMgrFactory = PropertiesUserManagerFactory().apply {
                passwordEncryptor = SaltedPasswordEncryptor()
            }
            val userManager = userMgrFactory.createUserManager()
            val authorities: List<Authority> = listOf(
                WritePermission(),
                ConcurrentLoginPermission(config.maxConnections, config.maxConnectionsPerIp)
            )
            val user = BaseUser().apply {
                name = config.username
                password = config.password
                homeDirectory = rootDir.absolutePath
                this.authorities = authorities
                maxIdleTime = 300
            }
            try { userManager.save(user) } catch (e: FtpException) { _lastError.value = e.message }

            if (config.anonymousAccess) {
                val anon = BaseUser().apply {
                    name = "anonymous"
                    password = ""
                    homeDirectory = rootDir.absolutePath
                    this.authorities = listOf(WritePermission(), ConcurrentLoginPermission(config.maxConnections, config.maxConnectionsPerIp))
                }
                try { userManager.save(anon) } catch (e: FtpException) { _lastError.value = e.message }
            }
            serverFactory.userManager = userManager

            // Stats ftplet to count bytes through STOR/RETR
            val statsFtplet = ServerStatsFtplet(
                onBytes = { delta -> _bytesTransferred.value = _bytesTransferred.value + delta },
                onClientsChanged = { clients -> _connectedClients.value = clients }
            )
            serverFactory.ftplets = mapOf("stats" to statsFtplet)

            val server = serverFactory.createServer()
            server.start()
            ftpServer = server
            _status.value = ServerStatus.RUNNING
            _lastError.value = null
        }.onFailure {
            _status.value = ServerStatus.ERROR
            _lastError.value = it.message
        }
    }

    suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { stopInternal() }
    }

    private fun stopInternal() {
        ftpServer?.let {
            runCatching { it.stop() }
        }
        ftpServer = null
        _status.value = ServerStatus.STOPPED
        _connectedClients.value = emptyList()
    }

    fun resetCounters() {
        _bytesTransferred.value = 0L
    }

    private fun resolveRoot(config: ServerConfig): File {
        if (config.rootDirectoryPath.isNotBlank()) {
            val f = File(config.rootDirectoryPath)
            if (f.exists() || f.mkdirs()) {
                Log.i(TAG, "FTP root (configured): ${f.absolutePath}")
                return f
            }
            Log.w(TAG, "Configured FTP root not usable, falling back: ${f.absolutePath}")
        }
        val fallback = StorageUtils.bestDefaultRoot(context)
        Log.i(TAG, "FTP root (default): ${fallback.absolutePath}")
        return fallback
    }

    companion object {
        private const val TAG: String = "FtpServerRepository"
    }
}
