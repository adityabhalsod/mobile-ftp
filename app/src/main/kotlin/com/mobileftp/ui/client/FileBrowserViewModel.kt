package com.mobileftp.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobileftp.data.repository.FtpClientRepository
import com.mobileftp.domain.model.RemoteFile
import com.mobileftp.domain.usecase.DownloadFileUseCase
import com.mobileftp.domain.usecase.ListDirectoryUseCase
import com.mobileftp.domain.usecase.UploadFileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class SortField { NAME, SIZE, DATE }
enum class SortOrder { ASC, DESC }

data class FileBrowserState(
    val path: String = "/",
    val files: List<RemoteFile> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val selected: Set<String> = emptySet(),
    val multiSelectMode: Boolean = false,
    val sortField: SortField = SortField.NAME,
    val sortOrder: SortOrder = SortOrder.ASC,
    val refreshing: Boolean = false
)

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val listUseCase: ListDirectoryUseCase,
    private val downloadUseCase: DownloadFileUseCase,
    private val uploadUseCase: UploadFileUseCase,
    private val clientRepo: FtpClientRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    fun load(path: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(path = path, loading = true, error = null)
            listUseCase(path).onSuccess { list ->
                _state.value = _state.value.copy(
                    loading = false,
                    files = sort(list, _state.value.sortField, _state.value.sortOrder),
                    selected = emptySet(),
                    multiSelectMode = false
                )
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = it.message)
            }
        }
    }

    fun refresh() {
        _state.value = _state.value.copy(refreshing = true)
        load(_state.value.path)
        _state.value = _state.value.copy(refreshing = false)
    }

    fun navigate(path: String) = load(path)

    fun navigateInto(file: RemoteFile) {
        if (file.isDirectory) load(file.path)
    }

    fun navigateUp() {
        val parent = parentPath(_state.value.path)
        load(parent)
    }

    fun toggleSelect(file: RemoteFile) {
        val cur = _state.value.selected.toMutableSet()
        if (cur.contains(file.path)) cur.remove(file.path) else cur.add(file.path)
        _state.value = _state.value.copy(selected = cur, multiSelectMode = cur.isNotEmpty())
    }

    fun enterMultiSelect(file: RemoteFile) {
        _state.value = _state.value.copy(multiSelectMode = true, selected = setOf(file.path))
    }

    fun exitMultiSelect() {
        _state.value = _state.value.copy(multiSelectMode = false, selected = emptySet())
    }

    fun setSort(field: SortField) {
        val newOrder = if (_state.value.sortField == field && _state.value.sortOrder == SortOrder.ASC)
            SortOrder.DESC else SortOrder.ASC
        _state.value = _state.value.copy(
            sortField = field,
            sortOrder = newOrder,
            files = sort(_state.value.files, field, newOrder)
        )
    }

    fun mkdir(name: String) {
        viewModelScope.launch {
            runCatching { clientRepo.mkdir(_state.value.path, name) }
            refresh()
        }
    }

    fun rename(file: RemoteFile, newName: String) {
        viewModelScope.launch {
            runCatching { clientRepo.rename(_state.value.path, file.name, newName) }
            refresh()
        }
    }

    fun delete(files: List<RemoteFile>) {
        viewModelScope.launch {
            files.forEach { runCatching { clientRepo.delete(it) } }
            refresh()
        }
    }

    fun downloadSelected(localDir: File, profileId: Long, chunkCount: Int) {
        val targetFiles = _state.value.files.filter { it.path in _state.value.selected && !it.isDirectory }
        viewModelScope.launch {
            targetFiles.forEach { remote ->
                val localFile = File(localDir, remote.name)
                downloadUseCase(profileId, remote, localFile.absolutePath, chunkCount)
            }
            exitMultiSelect()
        }
    }

    fun downloadSingle(profileId: Long, file: RemoteFile, localPath: String, chunkCount: Int) {
        viewModelScope.launch {
            downloadUseCase(profileId, file, localPath, chunkCount)
        }
    }

    fun uploadFiles(files: List<File>, profileId: Long, chunkCount: Int) {
        viewModelScope.launch {
            files.forEach { f -> uploadUseCase(profileId, f, _state.value.path, chunkCount) }
        }
    }

    private fun parentPath(path: String): String {
        if (path == "/" || path.isBlank()) return "/"
        val trimmed = path.trimEnd('/')
        val idx = trimmed.lastIndexOf('/')
        return if (idx <= 0) "/" else trimmed.substring(0, idx)
    }

    private fun sort(list: List<RemoteFile>, field: SortField, order: SortOrder): List<RemoteFile> {
        val cmp: Comparator<RemoteFile> = when (field) {
            SortField.NAME -> compareBy { it.name.lowercase() }
            SortField.SIZE -> compareBy { it.size }
            SortField.DATE -> compareBy { it.modifiedTimestamp }
        }
        // Folders always first
        val withFolders = compareByDescending<RemoteFile> { it.isDirectory }.then(cmp)
        val sorted = list.sortedWith(withFolders)
        return if (order == SortOrder.DESC) sorted.reversed().sortedByDescending { it.isDirectory } else sorted
    }
}
