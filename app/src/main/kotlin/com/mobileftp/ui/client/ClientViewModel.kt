package com.mobileftp.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobileftp.data.repository.ConnectionProfileRepository
import com.mobileftp.data.repository.FtpClientRepository
import com.mobileftp.domain.model.ConnectionProfile
import com.mobileftp.domain.usecase.ConnectClientUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClientScreenState(
    val profiles: List<ConnectionProfile> = emptyList(),
    val connecting: Boolean = false,
    val connectedProfile: ConnectionProfile? = null,
    val error: String? = null,
    val showSheet: Boolean = false,
    val editingProfile: ConnectionProfile? = null
)

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

    fun connect(profile: ConnectionProfile) {
        viewModelScope.launch {
            _state.value = _state.value.copy(connecting = true, error = null)
            val result = connectUseCase(profile)
            _state.value = _state.value.copy(
                connecting = false,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch { clientRepo.disconnect() }
    }

    fun saveProfile(profile: ConnectionProfile) {
        viewModelScope.launch {
            profileRepo.upsert(profile)
            _state.value = _state.value.copy(showSheet = false, editingProfile = null)
        }
    }

    fun deleteProfile(profile: ConnectionProfile) {
        viewModelScope.launch { profileRepo.delete(profile) }
    }

    fun openSheet(existing: ConnectionProfile? = null) {
        _state.value = _state.value.copy(showSheet = true, editingProfile = existing)
    }

    fun closeSheet() {
        _state.value = _state.value.copy(showSheet = false, editingProfile = null)
    }
}
