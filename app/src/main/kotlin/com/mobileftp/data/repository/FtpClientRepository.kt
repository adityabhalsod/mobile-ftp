package com.mobileftp.data.repository

import com.mobileftp.domain.model.ConnectionProfile
import com.mobileftp.domain.model.RemoteFile
import com.mobileftp.network.FtpConnectionPool
import com.mobileftp.network.SocketTuner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPSClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FtpClientRepository @Inject constructor() {

    private val _connectedProfile = MutableStateFlow<ConnectionProfile?>(null)
    val connectedProfile: StateFlow<ConnectionProfile?> = _connectedProfile.asStateFlow()

    private val _currentDirectory = MutableStateFlow("/")
    val currentDirectory: StateFlow<String> = _currentDirectory.asStateFlow()

    @Volatile
    private var pool: FtpConnectionPool? = null

    fun activePool(): FtpConnectionPool? = pool

    suspend fun connect(profile: ConnectionProfile): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            disconnectInternal()
            val newPool = FtpConnectionPool(profile, profile.chunkCount.coerceAtLeast(1))
            // Validate by warming a single connection first.
            newPool.warmUp(1)
            pool = newPool
            _connectedProfile.value = profile
            _currentDirectory.value = "/"
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) { disconnectInternal() }

    private suspend fun disconnectInternal() {
        pool?.closeAll()
        pool = null
        _connectedProfile.value = null
    }

    suspend fun list(path: String): List<RemoteFile> = withContext(Dispatchers.IO) {
        val activePool = pool ?: error("Not connected")
        val client = activePool.borrow()
        try {
            client.changeWorkingDirectory(path)
            val files: Array<FTPFile> = client.listFiles(path) ?: emptyArray()
            _currentDirectory.value = path
            files
                .filter { it.name != "." && it.name != ".." }
                .map { it.toRemote(path) }
                .sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() })
        } finally {
            activePool.release(client)
        }
    }

    suspend fun mkdir(path: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val activePool = pool ?: error("Not connected")
        val client = activePool.borrow()
        try {
            val full = joinPath(path, name)
            client.makeDirectory(full)
        } finally {
            activePool.release(client)
        }
    }

    suspend fun rename(path: String, oldName: String, newName: String): Boolean =
        withContext(Dispatchers.IO) {
            val activePool = pool ?: error("Not connected")
            val client = activePool.borrow()
            try {
                client.rename(joinPath(path, oldName), joinPath(path, newName))
            } finally {
                activePool.release(client)
            }
        }

    suspend fun delete(file: RemoteFile): Boolean = withContext(Dispatchers.IO) {
        val activePool = pool ?: error("Not connected")
        val client = activePool.borrow()
        try {
            if (file.isDirectory) client.removeDirectory(file.path) else client.deleteFile(file.path)
        } finally {
            activePool.release(client)
        }
    }

    /** One-off plain client for size queries, etc. */
    suspend fun fetchSize(remotePath: String): Long = withContext(Dispatchers.IO) {
        val activePool = pool ?: error("Not connected")
        val client = activePool.borrow()
        try {
            client.setFileType(FTP.BINARY_FILE_TYPE)
            val size = client.sendCommand("SIZE", remotePath)
            if (size in 200..299) {
                client.replyString.trim().substringAfter(" ").trim().toLongOrNull() ?: -1L
            } else -1L
        } finally {
            activePool.release(client)
        }
    }

    /** Build a one-shot client (for control queries outside the pool). */
    fun newControlClient(profile: ConnectionProfile): FTPClient {
        val client: FTPClient = if (profile.ftps) FTPSClient("TLS", false) else FTPClient()
        SocketTuner.applyToFtpClient(client)
        return client
    }

    private fun FTPFile.toRemote(parent: String): RemoteFile {
        val full = joinPath(parent, name)
        return RemoteFile(
            name = name,
            path = full,
            size = if (isDirectory) 0L else size,
            isDirectory = isDirectory,
            modifiedTimestamp = timestamp?.timeInMillis ?: 0L,
            permissions = formatPermissions(this),
            owner = user.orEmpty(),
            group = group.orEmpty()
        )
    }

    private fun formatPermissions(file: FTPFile): String {
        val sb = StringBuilder()
        sb.append(if (file.isDirectory) 'd' else '-')
        for (access in intArrayOf(FTPFile.USER_ACCESS, FTPFile.GROUP_ACCESS, FTPFile.WORLD_ACCESS)) {
            sb.append(if (file.hasPermission(access, FTPFile.READ_PERMISSION)) 'r' else '-')
            sb.append(if (file.hasPermission(access, FTPFile.WRITE_PERMISSION)) 'w' else '-')
            sb.append(if (file.hasPermission(access, FTPFile.EXECUTE_PERMISSION)) 'x' else '-')
        }
        return sb.toString()
    }

    private fun joinPath(parent: String, name: String): String {
        val p = if (parent.endsWith("/")) parent else "$parent/"
        return "$p$name"
    }
}
