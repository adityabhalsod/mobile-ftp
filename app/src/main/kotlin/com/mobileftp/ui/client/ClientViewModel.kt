package com.mobileftp.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobileftp.data.repository.ConnectionProfileRepository
import com.mobileftp.data.repository.FtpClientRepository
import com.mobileftp.domain.model.ConnectionProfile
import com.mobileftp.domain.usecase.ConnectClientUseCase
import com.mobileftp.util.ConfigValidators
import com.mobileftp.util.FtpErrors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClientScreenState(
    val profiles: List<ConnectionProfile> = emptyList(),
    /** Id of the profile currently connecting, or null. Only that card shows "Connecting…". */
    val connectingProfileId: Long? = null,
    val connectedProfile: ConnectionProfile? = null,
    val error: String? = null,
    val showSheet: Boolean = false,
    val editingProfile: ConnectionProfile? = null,
    /** True while the connection editor is saving — blocks duplicate inserts from repeated taps. */
    val saving: Boolean = false,
    /** Error shown inside the connection editor (e.g. database failure). */
    val sheetError: String? = null,
    /** Profile waiting for the user to confirm deletion. */
    val pendingDelete: ConnectionProfile? = null
) {
    val connecting: Boolean get() = connectingProfileId != null
}

@HiltViewModel
class ClientViewModel @Inject constructor(
    private val profileRepo: ConnectionProfileRepository,
    private val clientRepo: FtpClientRepository,
    private val connectUseCase: ConnectClientUseCase
) : ViewModel() {

    val profiles: StateFlow<List<ConnectionProfile>> =
        profileRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val connectedProfile: StateFlow<ConnectionProfile?> = clientRepo.connectedProfile

    private val _state = MutableStateFlow(ClientScreenState())
    val state: StateFlow<ClientScreenState> = _state.asStateFlow()

    private var connectJob: Job? = null

    fun connect(profile: ConnectionProfile) {
        // Ignore repeated taps while a connection attempt is running.
        if (connectJob?.isActive == true) return
        connectJob = viewModelScope.launch {
            _state.value = _state.value.copy(connectingProfileId = profile.id, error = null)
            val result = runCatching { connectUseCase(profile).getOrThrow() }
            _state.value = _state.value.copy(
                connectingProfileId = null,
                error = result.exceptionOrNull()?.let { FtpErrors.describe(it, profile) }
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch { runCatching { clientRepo.disconnect() } }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Validates and persists [profile]. Previously each tap on Save launched
     * another insert, so double-tapping created duplicate profiles, and a DB
     * error crashed the app.
     */
    fun saveProfile(profile: ConnectionProfile) {
        if (_state.value.saving) return
        val invalid = validate(profile)
        if (invalid != null) {
            _state.value = _state.value.copy(sheetError = invalid)
            return
        }
        _state.value = _state.value.copy(saving = true, sheetError = null)
        viewModelScope.launch {
            runCatching { profileRepo.upsert(profile) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        saving = false,
                        showSheet = false,
                        editingProfile = null,
                        sheetError = null
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        saving = false,
                        sheetError = "Could not save: ${it.message ?: it.javaClass.simpleName}"
                    )
                }
        }
    }

    fun requestDelete(profile: ConnectionProfile) {
        _state.value = _state.value.copy(pendingDelete = profile)
    }

    fun cancelDelete() {
        _state.value = _state.value.copy(pendingDelete = null)
    }

    fun confirmDelete() {
        val profile = _state.value.pendingDelete ?: return
        _state.value = _state.value.copy(pendingDelete = null)
        viewModelScope.launch {
            runCatching { profileRepo.delete(profile) }
                .onFailure {
                    _state.value = _state.value.copy(error = "Could not delete: ${it.message}")
                }
        }
    }

    fun openSheet(existing: ConnectionProfile? = null) {
        if (_state.value.showSheet) return
        _state.value = _state.value.copy(showSheet = true, editingProfile = existing, sheetError = null)
    }

    fun closeSheet() {
        // Don't drop the sheet mid-save; the save result closes it.
        if (_state.value.saving) return
        _state.value = _state.value.copy(showSheet = false, editingProfile = null, sheetError = null)
    }

    private fun validate(p: ConnectionProfile): String? =
        ConfigValidators.validateProfileName(p.name)
            ?: ConfigValidators.validateHost(p.host)
            ?: ConfigValidators.validatePort(p.port.toString())
            ?: ConfigValidators.validateClientUsername(p.username)
            ?: ConfigValidators.validateClientPassword(p.password)
            ?: ConfigValidators.validateChunks(p.chunkCount.toString())
}
