package com.mobileftp.ui.client

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Sync
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobileftp.domain.model.ConnectionProfile
import com.mobileftp.domain.model.RemoteFile
import com.mobileftp.ui.components.BreadcrumbBar
import com.mobileftp.ui.components.FileBrowserRow
import com.mobileftp.ui.components.RaycastButton
import com.mobileftp.ui.components.RaycastButtonStyle
import com.mobileftp.ui.components.RaycastCard
import com.mobileftp.ui.components.RaycastInput
import com.mobileftp.ui.theme.LocalRaycastColors
import com.mobileftp.ui.theme.RaycastSpacing
import com.mobileftp.ui.theme.RaycastType
import com.mobileftp.util.ConfigValidators
import java.io.File

@Composable
fun FileBrowserScreen(
    profile: ConnectionProfile,
    onDisconnect: () -> Unit,
    viewModel: FileBrowserViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = LocalRaycastColors.current

    LaunchedEffect(profile.id) {
        viewModel.load("/")
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    // System back: leave multi-select, then go up a folder, instead of closing the app.
    BackHandler(enabled = state.multiSelectMode || state.path != "/") {
        if (state.multiSelectMode) viewModel.exitMultiSelect() else viewModel.navigateUp()
    }

    var showContextSheet by remember { mutableStateOf<RemoteFile?>(null) }
    var pendingDelete by remember { mutableStateOf<List<RemoteFile>?>(null) }
    var showRenameDialog by remember { mutableStateOf<RemoteFile?>(null) }
    var showMkdirDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header — title takes the remaining width (ellipsized) so the action
            // buttons are never pushed off-screen on narrow phones.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RaycastSpacing.lg, vertical = RaycastSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        profile.name,
                        style = RaycastType.HeadlineMedium.copy(color = colors.TextPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${profile.username.ifBlank { "anonymous" }}@${profile.host}:${profile.port}",
                        style = RaycastType.LabelMono.copy(color = colors.TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Sync: re-list the current folder. Disabled while a listing is running,
                // so repeated taps can't stack requests.
                HeaderIconButton(
                    icon = Icons.Filled.Sync,
                    contentDescription = "Sync folder",
                    enabled = !state.loading,
                    onClick = { viewModel.refresh() }
                )
                HeaderIconButton(
                    icon = Icons.Filled.CreateNewFolder,
                    contentDescription = "New folder",
                    enabled = !state.busy && !state.loading,
                    onClick = { showMkdirDialog = true }
                )
                HeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Disconnect",
                    tint = colors.AccentStart,
                    onClick = onDisconnect
                )
            }

            // Breadcrumb
            BreadcrumbBar(
                path = state.path,
                onNavigate = { viewModel.navigate(it) }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.BorderSubtle)
            )

            // Sort row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RaycastSpacing.lg, vertical = RaycastSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(RaycastSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = colors.TextSecondary, modifier = Modifier.height(16.dp))
                SortChip("Name", state.sortField == SortField.NAME) { viewModel.setSort(SortField.NAME) }
                SortChip("Size", state.sortField == SortField.SIZE) { viewModel.setSort(SortField.SIZE) }
                SortChip("Date", state.sortField == SortField.DATE) { viewModel.setSort(SortField.DATE) }
                Box(modifier = Modifier.weight(1f))
                Text(
                    "${state.files.size} items",
                    style = RaycastType.MetaMono.copy(color = colors.TextTertiary)
                )
            }

            if (state.loading || state.busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = colors.AccentBlue,
                    trackColor = colors.BorderSubtle
                )
            }

            state.error?.let { err ->
                RaycastCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RaycastSpacing.lg, vertical = RaycastSpacing.sm),
                    background = colors.AccentStart.copy(alpha = 0.10f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RaycastSpacing.sm)
                    ) {
                        Text(
                            err,
                            style = RaycastType.BodySmall.copy(color = colors.AccentStart),
                            modifier = Modifier.weight(1f)
                        )
                        RaycastButton(
                            text = "Retry",
                            onClick = { viewModel.refresh() },
                            style = RaycastButtonStyle.Secondary,
                            enabled = !state.loading
                        )
                    }
                }
            }

            // File list
            if (state.loading && state.files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Loading…", style = RaycastType.BodyMedium.copy(color = colors.TextSecondary))
                }
            } else if (state.files.isEmpty() && state.error == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Empty directory", style = RaycastType.BodyMedium.copy(color = colors.TextTertiary))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(state.files, key = { it.path }) { file ->
                        FileBrowserRow(
                            file = file,
                            selected = file.path in state.selected,
                            multiSelectMode = state.multiSelectMode,
                            onClick = {
                                if (state.multiSelectMode) viewModel.toggleSelect(file)
                                else viewModel.navigateInto(file)
                            },
                            onLongPress = {
                                if (state.multiSelectMode) viewModel.toggleSelect(file)
                                else showContextSheet = file
                            }
                        )
                    }
                }
            }
        }

        // Multi-select FAB row
        AnimatedVisibility(
            visible = state.multiSelectMode,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MultiSelectBar(
                count = state.selected.size,
                busy = state.busy,
                onDownload = {
                    viewModel.downloadSelected(downloadDir(), profile.id, profile.chunkCount)
                },
                onDelete = {
                    val toDelete = state.files.filter { it.path in state.selected }
                    if (toDelete.isNotEmpty()) pendingDelete = toDelete
                },
                onCancel = { viewModel.exitMultiSelect() }
            )
        }
    }

    showContextSheet?.let { file ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showContextSheet = null },
            sheetState = sheetState,
            containerColor = colors.SurfaceElevated
        ) {
            ContextMenu(
                file = file,
                onDownload = {
                    val target = File(downloadDir(), file.name).absolutePath
                    if (!file.isDirectory) {
                        viewModel.exitMultiSelect()
                        viewModel.downloadSingle(profile.id, file, target, profile.chunkCount)
                    }
                    showContextSheet = null
                },
                onRename = {
                    showRenameDialog = file
                    showContextSheet = null
                },
                onDelete = {
                    pendingDelete = listOf(file)
                    showContextSheet = null
                }
            )
        }
    }

    pendingDelete?.let { files ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = colors.SurfaceElevated,
            titleContentColor = colors.TextPrimary,
            textContentColor = colors.TextSecondary,
            title = {
                Text(
                    if (files.size == 1) "Delete ${files.first().name}?" else "Delete ${files.size} items?",
                    style = RaycastType.HeadlineMedium
                )
            },
            text = { Text("This cannot be undone.", style = RaycastType.BodySmall) },
            confirmButton = {
                RaycastButton(
                    text = "Delete",
                    onClick = {
                        viewModel.delete(files)
                        pendingDelete = null
                    },
                    style = RaycastButtonStyle.Destructive
                )
            },
            dismissButton = {
                RaycastButton(text = "Cancel", onClick = { pendingDelete = null }, style = RaycastButtonStyle.Secondary)
            }
        )
    }

    if (showMkdirDialog) {
        InputDialog(
            title = "New folder",
            placeholder = "Folder name",
            existingNames = state.files.map { it.name }.toSet(),
            onDismiss = { showMkdirDialog = false },
            onConfirm = { name ->
                viewModel.mkdir(name)
                showMkdirDialog = false
            }
        )
    }

    showRenameDialog?.let { file ->
        InputDialog(
            title = "Rename ${file.name}",
            placeholder = "New name",
            initial = file.name,
            existingNames = state.files.map { it.name }.toSet() - file.name,
            onDismiss = { showRenameDialog = null },
            onConfirm = { newName ->
                viewModel.rename(file, newName)
                showRenameDialog = null
            }
        )
    }
}

@Composable
private fun SortChip(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalRaycastColors.current
    Box(
        modifier = Modifier
            .background(
                if (active) colors.AccentBlue.copy(alpha = 0.15f) else colors.SurfaceCard,
                androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            style = RaycastType.LabelMono.copy(color = if (active) colors.AccentBlue else colors.TextSecondary)
        )
    }
}

@Composable
private fun MultiSelectBar(
    count: Int,
    busy: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalRaycastColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(RaycastSpacing.lg)
            .background(colors.SurfaceElevated, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(RaycastSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RaycastSpacing.sm)
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = "Cancel",
            tint = colors.TextSecondary,
            modifier = Modifier.clickable(onClick = onCancel).height(20.dp)
        )
        Text(
            "$count selected",
            style = RaycastType.LabelMono.copy(color = colors.TextPrimary),
            modifier = Modifier.weight(1f)
        )
        RaycastButton(
            text = "Download",
            onClick = onDownload,
            enabled = !busy && count > 0,
            style = RaycastButtonStyle.Primary,
            leading = { Icon(Icons.Filled.Download, null, modifier = Modifier.size(14.dp)) }
        )
        RaycastButton(
            text = "Delete",
            onClick = onDelete,
            enabled = !busy && count > 0,
            style = RaycastButtonStyle.Destructive,
            leading = { Icon(Icons.Filled.Delete, null, modifier = Modifier.size(14.dp)) }
        )
    }
}

@Composable
private fun ContextMenu(
    file: RemoteFile,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalRaycastColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(RaycastSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(RaycastSpacing.sm)
    ) {
        Text(file.name, style = RaycastType.HeadlineMedium.copy(color = colors.TextPrimary))
        Text(file.path, style = RaycastType.MetaMono.copy(color = colors.TextTertiary))
        if (!file.isDirectory) {
            RaycastButton(
                text = "Download",
                onClick = onDownload,
                style = RaycastButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth()
            )
        }
        RaycastButton(
            text = "Rename",
            onClick = onRename,
            style = RaycastButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth()
        )
        RaycastButton(
            text = "Delete",
            onClick = onDelete,
            style = RaycastButtonStyle.Destructive,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun InputDialog(
    title: String,
    placeholder: String,
    initial: String = "",
    existingNames: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colors = LocalRaycastColors.current
    var text by remember { mutableStateOf(initial) }
    var touched by remember { mutableStateOf(false) }
    val error = ConfigValidators.validateRemoteName(text)
        ?: if (text.trim() in existingNames) "An item with this name already exists" else null
    val unchanged = text.trim() == initial
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.SurfaceElevated,
        titleContentColor = colors.TextPrimary,
        title = { Text(title, style = RaycastType.HeadlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            RaycastInput(
                value = text,
                onValueChange = { text = it; touched = true },
                placeholder = placeholder,
                imeAction = ImeAction.Done,
                error = if (touched) error else null,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            RaycastButton(
                text = "OK",
                onClick = { onConfirm(text.trim()) },
                enabled = error == null && !unchanged,
                style = RaycastButtonStyle.Primary
            )
        },
        dismissButton = {
            RaycastButton(text = "Cancel", onClick = onDismiss, style = RaycastButtonStyle.Secondary)
        }
    )
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = LocalRaycastColors.current.TextPrimary
) {
    val colors = LocalRaycastColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(colors.SurfaceCard, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Public Downloads/MobileFTP — created lazily by the ViewModel, off the UI thread. */
private fun downloadDir(): File = File(
    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
    "MobileFTP"
)
