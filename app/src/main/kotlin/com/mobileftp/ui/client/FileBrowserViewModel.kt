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
import com.mobileftp.util.FtpClientException
import com.mobileftp.util.FtpErrors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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
    val refreshing: Boolean = false,
    /** True while mkdir / rename / delete is running. */
    val busy: Boolean = false,
    /** One-shot message (operation result) shown as a Toast. */
    val message: String? = null
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

    private var loadJob: Job? = null

    /**
     * Loads [path]. Any in-flight load is cancelled first: previously each tap
     * (folder row, breadcrumb, refresh) started another listing in parallel and
     * whichever finished last won, so the list could show a different folder
     * than the breadcrumb and pile up pooled connections.
     */
    fun load(path: String, isRefresh: Boolean = false) {
        loadJob?.cancel()
        val previousPath = _state.value.path
        val hadListing = _state.value.files.isNotEmpty() || previousPath != path
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                path = path,
                loading = true,
                refreshing = isRefresh,
                error = null
            )
            val result = listUseCase(path)
            ensureActive()
            result.onSuccess { list ->
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    files = sort(list, _state.value.sortField, _state.value.sortOrder),
                    selected = emptySet(),
                    multiSelectMode = false
                )
            }.onFailure {
                if (it is CancellationException) throw it
                _state.value = _state.value.copy(
                    // Keep the breadcrumb in sync with the list still on screen.
                    path = if (hadListing && previousPath != path) previousPath else path,
                    loading = false,
                    refreshing = false,
                    error = FtpErrors.describe(it)
                )
            }
        }
    }

    /** Re-list the current folder. Taps while a refresh is already running are ignored. */
    fun refresh() {
        if (_state.value.refreshing) return
        load(_state.value.path, isRefresh = true)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
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

    fun mkdir(name: String) = runOperation("Folder created") {
        clientRepo.mkdir(_state.value.path, name.trim())
    }

    fun rename(file: RemoteFile, newName: String) = runOperation("Renamed to ${newName.trim()}") {
        clientRepo.rename(_state.value.path, file.name, newName.trim())
    }

    fun delete(files: List<RemoteFile>) = runOperation(
        if (files.size == 1) "Deleted ${files.first().name}" else "Deleted ${files.size} items"
    ) {
        // Keep going on individual failures, then report the first one.
        val failures = files.mapNotNull { f -> runCatching { clientRepo.delete(f) }.exceptionOrNull() }
        if (failures.isNotEmpty()) {
            throw FtpClientException(
                "${failures.size} of ${files.size} could not be deleted: ${FtpErrors.describe(failures.first())}"
            )
        }
    }

    /**
     * Runs a remote file operation one at a time. Failures used to be swallowed
     * by runCatching, so a rejected mkdir/rename/delete looked like it "did nothing".
     */
    private fun runOperation(successMessage: String, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            val result = runCatching { block() }
            _state.value = _state.value.copy(
                busy = false,
                message = result.fold({ successMessage }, { FtpErrors.describe(it) }),
                multiSelectMode = if (result.isSuccess) false else _state.value.multiSelectMode,
                selected = if (result.isSuccess) emptySet() else _state.value.selected
            )
            load(_state.value.path, isRefresh = true)
        }
    }

    fun downloadSelected(localDir: File, profileId: Long, chunkCount: Int) {
        val targetFiles = _state.value.files.filter { it.path in _state.value.selected && !it.isDirectory }
        if (targetFiles.isEmpty()) {
            _state.value = _state.value.copy(message = "Select at least one file (folders can't be downloaded yet)")
            return
        }
        exitMultiSelect()
        viewModelScope.launch {
            val result = runCatching {
                if (!localDir.exists() && !localDir.mkdirs()) {
                    throw FtpClientException("Cannot create ${localDir.absolutePath}")
                }
                targetFiles.forEach { remote ->
                    val localFile = File(localDir, remote.name)
                    downloadUseCase(profileId, remote, localFile.absolutePath, chunkCount)
                }
            }
            _state.value = _state.value.copy(
                message = result.fold(
                    { "Queued ${targetFiles.size} download(s) — see Transfers" },
                    { "Download failed: ${FtpErrors.describe(it)}" }
                )
            )
        }
    }

    fun downloadSingle(profileId: Long, file: RemoteFile, localPath: String, chunkCount: Int) {
        viewModelScope.launch {
            val result = runCatching {
                File(localPath).parentFile?.let { dir ->
                    if (!dir.exists() && !dir.mkdirs()) throw FtpClientException("Cannot create ${dir.absolutePath}")
                }
                downloadUseCase(profileId, file, localPath, chunkCount)
            }
            _state.value = _state.value.copy(
                message = result.fold(
                    { "Queued ${file.name} — see Transfers" },
                    { "Download failed: ${FtpErrors.describe(it)}" }
                )
            )
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
