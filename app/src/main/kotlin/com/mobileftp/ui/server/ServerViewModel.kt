package com.mobileftp.ui.server

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobileftp.data.local.SettingsStore
import com.mobileftp.data.repository.ConnectedClient
import com.mobileftp.data.repository.FtpServerRepository
import com.mobileftp.data.repository.ServerStatus
import com.mobileftp.domain.model.NetworkInterfaceInfo
import com.mobileftp.domain.model.ServerConfig
import com.mobileftp.network.NetworkInterfaceSelector
import com.mobileftp.network.ThroughputMonitor
import com.mobileftp.service.FtpServerService
import com.mobileftp.util.NetworkUtils
import com.mobileftp.util.PermissionUtils
import com.mobileftp.util.StorageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ServerScreenState(
    val status: ServerStatus = ServerStatus.STOPPED,
    val config: ServerConfig = ServerConfig(),
    val lanIp: String = "—",
    val publicIp: String? = null,
    val interfaces: List<NetworkInterfaceInfo> = emptyList(),
    val selectedInterface: NetworkInterfaceInfo? = null,
    val clients: List<ConnectedClient> = emptyList(),
    val bytesTransferred: Long = 0L,
    val throughput: com.mobileftp.domain.model.ThroughputSnapshot =
        com.mobileftp.domain.model.ThroughputSnapshot(0L, 0L, 0L, emptyList(), 0L),
    val error: String? = null,
    val activeRootPath: String = "",
    val hasAllFilesAccess: Boolean = false
)

@HiltViewModel
class ServerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val serverRepository: FtpServerRepository,
    private val networkSelector: NetworkInterfaceSelector
) : ViewModel() {

    private val throughputMonitor = ThroughputMonitor()

    private val _state = MutableStateFlow(ServerScreenState())
    val state: StateFlow<ServerScreenState> = _state.asStateFlow()

    init {
        networkSelector.start { /* re-renders happen via Flow below */ }
        throughputMonitor.start(0L)

        combine(
            serverRepository.status,
            serverRepository.config,
            settingsStore.serverConfigFlow,
            serverRepository.connectedClients,
            serverRepository.bytesTransferred
        ) { status, runningConfig, persistedConfig, clients, bytes ->
            val activeConfig = if (status == ServerStatus.RUNNING) runningConfig else persistedConfig
            val activePath = activeConfig.rootDirectoryPath.ifBlank {
                StorageUtils.bestDefaultRoot(context).absolutePath
            }
            _state.value = _state.value.copy(
                status = status,
                config = activeConfig,
                clients = clients,
                bytesTransferred = bytes,
                activeRootPath = activePath,
                hasAllFilesAccess = PermissionUtils.hasManageStorage()
            )
        }.launchIn(viewModelScope)

        serverRepository.bytesTransferred.onEach { total ->
            // On every byte tick, feed the throughput monitor with the delta
            val previous = _state.value.bytesTransferred
            val delta = (total - previous).coerceAtLeast(0L)
            if (delta > 0L) throughputMonitor.report(delta)
        }.launchIn(viewModelScope)

        throughputMonitor.snapshot.onEach { snap ->
            _state.value = _state.value.copy(throughput = snap)
        }.launchIn(viewModelScope)

        networkSelector.interfaces.onEach { ifs ->
            _state.value = _state.value.copy(
                interfaces = ifs,
                selectedInterface = ifs.firstOrNull { it.isUp && it.ipv4 != null && it.score > 0 }
            )
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            _state.value = _state.value.copy(
                lanIp = NetworkUtils.primaryLanIpv4(context) ?: "—"
            )
            val pub = withContext(Dispatchers.IO) { NetworkUtils.fetchPublicIpv4() }
            _state.value = _state.value.copy(publicIp = pub)
        }
    }

    fun startServer() {
        FtpServerService.start(context)
    }

    fun stopServer() {
        FtpServerService.stop(context)
    }

    fun refreshPublicIp() {
        viewModelScope.launch {
            val pub = withContext(Dispatchers.IO) { NetworkUtils.fetchPublicIpv4() }
            _state.value = _state.value.copy(publicIp = pub)
        }
    }

    fun refreshLanIp() {
        viewModelScope.launch {
            networkSelector.refresh()
            _state.value = _state.value.copy(
                lanIp = NetworkUtils.primaryLanIpv4(context) ?: "—",
                hasAllFilesAccess = PermissionUtils.hasManageStorage()
            )
        }
    }

    /**
     * Re-query MANAGE_EXTERNAL_STORAGE and refresh the active root path.
     * Called on activity resume so returning from Settings updates the UI.
     */
    fun refreshPermissions() {
        val granted = PermissionUtils.hasManageStorage()
        val current = _state.value
        val activePath = current.config.rootDirectoryPath.ifBlank {
            StorageUtils.bestDefaultRoot(context).absolutePath
        }
        _state.value = current.copy(
            hasAllFilesAccess = granted,
            activeRootPath = activePath
        )
    }

    fun saveConfig(newConfig: ServerConfig) {
        viewModelScope.launch {
            settingsStore.saveServerConfig(newConfig)
        }
    }

    override fun onCleared() {
        throughputMonitor.stop()
        networkSelector.stop()
        super.onCleared()
    }
}
