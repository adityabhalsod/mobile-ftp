package com.mobileftp.ui.server

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobileftp.data.local.SettingsStore
import com.mobileftp.data.repository.ConnectedClient
import com.mobileftp.data.repository.FtpServerRepository
import com.mobileftp.data.repository.ServerStatus
import com.mobileftp.data.repository.ServerTransfer
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
import com.mobileftp.util.ConfigValidators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    /** Uploads/downloads made by clients connected to this server (active first). */
    val transfers: List<ServerTransfer> = emptyList(),
    val throughput: com.mobileftp.domain.model.ThroughputSnapshot =
        com.mobileftp.domain.model.ThroughputSnapshot(0L, 0L, 0L, emptyList(), 0L),
    val error: String? = null,
    val activeRootPath: String = "",
    val hasAllFilesAccess: Boolean = false,
    /** Config as persisted on disk — this is what the configuration form edits. */
    val savedConfig: ServerConfig = ServerConfig(),
    /** True while a config save is in flight (Save button disabled, no double saves). */
    val saving: Boolean = false,
    /** True between tapping Start/Stop and the service reporting a new status. */
    val lifecyclePending: Boolean = false,
    val refreshingLan: Boolean = false,
    val refreshingPublic: Boolean = false,
    /** One-shot message for a Toast; cleared by [ServerViewModel.consumeMessage]. */
    val message: String? = null
) {
    /** Saved settings differ from what the running server uses. */
    val restartRequired: Boolean
        get() = status == ServerStatus.RUNNING && savedConfig != config
}

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

    private var lanJob: Job? = null
    private var publicJob: Job? = null
    private var lifecycleTimeoutJob: Job? = null

    init {
        networkSelector.start { /* re-renders happen via Flow below */ }
        throughputMonitor.start(0L)

        combine(
            serverRepository.status,
            serverRepository.config,
            settingsStore.serverConfigFlow,
            serverRepository.connectedClients,
            serverRepository.lastError
        ) { status, runningConfig, persistedConfig, clients, lastError ->
            val activeConfig = if (status == ServerStatus.RUNNING) runningConfig else persistedConfig
            val activePath = activeConfig.rootDirectoryPath.ifBlank {
                StorageUtils.bestDefaultRoot(context).absolutePath
            }
            val previous = _state.value
            _state.value = previous.copy(
                status = status,
                config = activeConfig,
                savedConfig = persistedConfig,
                clients = clients,
                activeRootPath = activePath,
                hasAllFilesAccess = PermissionUtils.hasManageStorage(),
                error = if (status == ServerStatus.ERROR) lastError else null,
                // A real status transition means the Start/Stop request has been handled.
                lifecyclePending = previous.lifecyclePending &&
                    previous.status == status &&
                    status != ServerStatus.ERROR
            )
        }.launchIn(viewModelScope)

        var lastTotal: Long? = null
        serverRepository.bytesTransferred.onEach { total ->
            // Feed the throughput monitor with the delta since the last tick. The
            // first value is only a baseline: bytes moved before this screen opened
            // must not show up as one huge spike.
            val previous = lastTotal
            lastTotal = total
            if (previous != null) {
                val delta = (total - previous).coerceAtLeast(0L)
                if (delta > 0L) throughputMonitor.report(delta)
            }
            _state.value = _state.value.copy(bytesTransferred = total)
        }.launchIn(viewModelScope)

        serverRepository.transfers.onEach { transfers ->
            _state.value = _state.value.copy(transfers = transfers)
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

        refreshLanIp()
        refreshPublicIp()
    }

    fun startServer() {
        if (_state.value.lifecyclePending || _state.value.status == ServerStatus.STARTING) return
        val invalid = validate(_state.value.savedConfig)
        if (invalid != null) {
            _state.value = _state.value.copy(message = "Fix the configuration first: $invalid")
            return
        }
        markLifecyclePending()
        FtpServerService.start(context)
    }

    fun stopServer() {
        if (_state.value.lifecyclePending) return
        markLifecyclePending()
        FtpServerService.stop(context)
    }

    /** Re-enable Start/Stop after a while even if the service never reported back. */
    private fun markLifecyclePending() {
        _state.value = _state.value.copy(lifecyclePending = true)
        lifecycleTimeoutJob?.cancel()
        lifecycleTimeoutJob = viewModelScope.launch {
            kotlinx.coroutines.delay(10_000L)
            _state.value = _state.value.copy(lifecyclePending = false)
        }
    }

    /** Ignores taps while a lookup is already running (previously each tap stacked a new request). */
    fun refreshPublicIp() {
        if (publicJob?.isActive == true) return
        publicJob = viewModelScope.launch {
            _state.value = _state.value.copy(refreshingPublic = true)
            try {
                val pub = withContext(Dispatchers.IO) { NetworkUtils.fetchPublicIpv4() }
                _state.value = _state.value.copy(publicIp = pub)
            } finally {
                _state.value = _state.value.copy(refreshingPublic = false)
            }
        }
    }

    /** Interface enumeration is blocking I/O — run it off the main thread, one at a time. */
    fun refreshLanIp() {
        if (lanJob?.isActive == true) return
        lanJob = viewModelScope.launch {
            _state.value = _state.value.copy(refreshingLan = true)
            try {
                val ip = withContext(Dispatchers.IO) {
                    networkSelector.refresh()
                    NetworkUtils.primaryLanIpv4(context)
                }
                _state.value = _state.value.copy(
                    lanIp = ip ?: "—",
                    hasAllFilesAccess = PermissionUtils.hasManageStorage()
                )
            } finally {
                _state.value = _state.value.copy(refreshingLan = false)
            }
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

    /**
     * Validates and persists [newConfig]. Repeated taps while a save is in
     * flight are ignored, so the form can't end up in a half-saved state.
     */
    fun saveConfig(newConfig: ServerConfig) {
        if (_state.value.saving) return
        val invalid = validate(newConfig)
        if (invalid != null) {
            _state.value = _state.value.copy(message = invalid)
            return
        }
        val normalized = newConfig.copy(username = newConfig.username.trim())
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            val result = runCatching { settingsStore.saveServerConfig(normalized) }
            val running = _state.value.status == ServerStatus.RUNNING
            _state.value = _state.value.copy(
                saving = false,
                message = result.fold(
                    onSuccess = {
                        if (running) "Configuration saved. Restart the server to apply." else "Configuration saved."
                    },
                    onFailure = { "Could not save configuration: ${it.message ?: it.javaClass.simpleName}" }
                )
            )
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun validate(c: ServerConfig): String? =
        ConfigValidators.validatePort(c.port.toString(), ConfigValidators.MIN_UNPRIVILEGED_PORT)
            ?: ConfigValidators.validateServerUsername(c.username)
            ?: ConfigValidators.validateServerPassword(c.password)
            ?: ConfigValidators.validatePasvRange(
                c.pasvPortStart.toString(), c.pasvPortEnd.toString(), c.port
            ).let { it.first ?: it.second }
            ?: maxConnectionsError(c.maxConnections)
            ?: chunkCountError(c.chunkCount)

    private fun maxConnectionsError(v: Int): String? =
        if (v !in 1..32) "Max connections must be between 1 and 32" else null

    private fun chunkCountError(v: Int): String? =
        if (v !in 2..32) "Chunks must be between 2 and 32" else null

    override fun onCleared() {
        throughputMonitor.stop()
        networkSelector.stop()
        super.onCleared()
    }
}
