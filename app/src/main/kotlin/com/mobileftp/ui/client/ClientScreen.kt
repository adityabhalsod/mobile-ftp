package com.mobileftp.ui.client

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobileftp.domain.model.ConnectionProfile
import com.mobileftp.ui.components.RaycastButton
import com.mobileftp.ui.components.RaycastButtonStyle
import com.mobileftp.ui.components.RaycastCard
import com.mobileftp.ui.components.RaycastInput
import com.mobileftp.ui.components.RaycastSwitch
import com.mobileftp.ui.components.SettingGroupHeader
import com.mobileftp.ui.components.SettingRow
import com.mobileftp.ui.theme.LocalRaycastColors
import com.mobileftp.ui.theme.RaycastShapes
import com.mobileftp.ui.theme.RaycastSpacing
import com.mobileftp.ui.theme.RaycastType
import com.mobileftp.ui.theme.flameGradient
import com.mobileftp.util.ConfigValidators
import com.mobileftp.util.HumanReadableUtils

@Composable
fun ClientScreen(
    onConnected: (ConnectionProfile) -> Unit,
    viewModel: ClientViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsState()
    val connectedProfile by viewModel.connectedProfile.collectAsState()
    val state by viewModel.state.collectAsState()
    val colors = LocalRaycastColors.current

    androidx.compose.runtime.LaunchedEffect(connectedProfile) {
        connectedProfile?.let { onConnected(it) }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(colors.Surface)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Extra bottom padding so the FAB never covers the last card's Connect button.
            contentPadding = PaddingValues(
                start = RaycastSpacing.lg,
                end = RaycastSpacing.lg,
                top = RaycastSpacing.lg,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(RaycastSpacing.md)
        ) {
            item {
                Text("FTP Client", style = RaycastType.DisplayLarge.copy(color = colors.TextPrimary))
                Text("Saved connections", style = RaycastType.LabelMono.copy(color = colors.TextSecondary))
            }
            if (profiles.isEmpty()) {
                item {
                    RaycastCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(RaycastSpacing.md),
                            modifier = Modifier.fillMaxWidth().padding(RaycastSpacing.xl)
                        ) {
                            Icon(
                                Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = colors.AccentStart,
                                modifier = Modifier.size(32.dp)
                            )
                            Text("No saved profiles yet", style = RaycastType.BodyMedium.copy(color = colors.TextSecondary))
                            Text("Tap + to add your first connection", style = RaycastType.MetaMono.copy(color = colors.TextTertiary))
                        }
                    }
                }
            } else {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        onConnect = { viewModel.connect(profile) },
                        onEdit = { viewModel.openSheet(profile) },
                        onDelete = { viewModel.requestDelete(profile) },
                        connecting = state.connectingProfileId == profile.id,
                        anyConnecting = state.connecting
                    )
                }
            }
            state.error?.let { err ->
                item {
                    RaycastCard(
                        modifier = Modifier.fillMaxWidth(),
                        background = colors.AccentStart.copy(alpha = 0.10f),
                        onClick = { viewModel.dismissError() }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(err, style = RaycastType.BodySmall.copy(color = colors.AccentStart))
                            Text("Tap to dismiss", style = RaycastType.MetaMono.copy(color = colors.TextTertiary))
                        }
                    }
                }
            }
        }

        // FAB
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(RaycastSpacing.lg)
        ) {
            FloatingFab(onClick = { viewModel.openSheet() })
        }
    }

    if (state.showSheet) {
        // Open fully expanded: the half-expanded state hid the Save button and,
        // combined with the keyboard, made the lower fields impossible to reach.
        val savingNow by androidx.compose.runtime.rememberUpdatedState(state.saving)
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { target ->
                // Don't let a drag dismiss the sheet while a save is in flight.
                !(target == SheetValue.Hidden && savingNow)
            }
        )
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeSheet() },
            sheetState = sheetState,
            containerColor = colors.SurfaceElevated,
            contentColor = colors.TextPrimary
        ) {
            ConnectionSheet(
                existing = state.editingProfile,
                saving = state.saving,
                externalError = state.sheetError,
                onSave = { viewModel.saveProfile(it) },
                onDismiss = { viewModel.closeSheet() }
            )
        }
    }

    state.pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            containerColor = colors.SurfaceElevated,
            titleContentColor = colors.TextPrimary,
            textContentColor = colors.TextSecondary,
            title = { Text("Delete connection?", style = RaycastType.HeadlineMedium) },
            text = { Text("\"${profile.name}\" (${profile.host}:${profile.port}) will be removed.", style = RaycastType.BodySmall) },
            confirmButton = {
                RaycastButton(text = "Delete", onClick = { viewModel.confirmDelete() }, style = RaycastButtonStyle.Destructive)
            },
            dismissButton = {
                RaycastButton(text = "Cancel", onClick = { viewModel.cancelDelete() }, style = RaycastButtonStyle.Secondary)
            }
        )
    }
}

@Composable
private fun FloatingFab(onClick: () -> Unit) {
    val colors = LocalRaycastColors.current
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(colors.flameGradient())
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Add",
            tint = colors.PrimaryButtonText
        )
    }
}

@Composable
private fun ProfileCard(
    profile: ConnectionProfile,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    connecting: Boolean,
    anyConnecting: Boolean
) {
    val colors = LocalRaycastColors.current
    RaycastCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RaycastSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // weight(1f) + ellipsis: long names/hosts used to push the Edit/Delete buttons off-screen.
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        profile.name,
                        style = RaycastType.HeadlineMedium.copy(color = colors.TextPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${profile.host}:${profile.port}" + if (profile.ftps) " · FTPS" else "",
                        style = RaycastType.LabelMono.copy(color = colors.TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RaycastButton(
                        text = "Edit",
                        onClick = onEdit,
                        enabled = !connecting,
                        style = RaycastButtonStyle.Secondary,
                        leading = { Icon(Icons.Filled.Edit, null, modifier = Modifier.size(14.dp)) }
                    )
                    RaycastButton(
                        text = "Delete",
                        onClick = onDelete,
                        enabled = !connecting,
                        style = RaycastButtonStyle.Destructive,
                        leading = { Icon(Icons.Filled.Delete, null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (profile.lastConnectedAt > 0L)
                        "Last connected ${HumanReadableUtils.relative(profile.lastConnectedAt)}"
                    else "Never connected",
                    style = RaycastType.MetaMono.copy(color = colors.TextTertiary),
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                RaycastButton(
                    text = if (connecting) "Connecting…" else "Connect",
                    onClick = onConnect,
                    style = RaycastButtonStyle.Primary,
                    enabled = !anyConnecting
                )
            }
        }
    }
}

@Composable
private fun ConnectionSheet(
    existing: ConnectionProfile?,
    saving: Boolean,
    externalError: String?,
    onSave: (ConnectionProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalRaycastColors.current
    // Keyed on the profile id so switching between "New" and "Edit X" never
    // shows stale values from the previously edited profile.
    val key = existing?.id ?: -1L
    var name by remember(key) { mutableStateOf(existing?.name ?: "") }
    var host by remember(key) { mutableStateOf(existing?.host ?: "") }
    var port by remember(key) { mutableStateOf((existing?.port ?: 21).toString()) }
    var username by remember(key) { mutableStateOf(existing?.username ?: "") }
    var password by remember(key) { mutableStateOf(existing?.password ?: "") }
    var passive by remember(key) { mutableStateOf(existing?.passive ?: true) }
    var ftps by remember(key) { mutableStateOf(existing?.ftps ?: false) }
    var chunks by remember(key) {
        mutableStateOf((existing?.chunkCount ?: 8).coerceIn(ConfigValidators.MIN_CHUNKS, ConfigValidators.MAX_CHUNKS).toString())
    }
    // Errors appear once a field has been edited, or for every field after a Save attempt.
    var touched by remember(key) { mutableStateOf(setOf<String>()) }
    var submitted by remember(key) { mutableStateOf(false) }
    fun show(field: String, error: String?) = if (submitted || field in touched) error else null

    val nameError = ConfigValidators.validateProfileName(name)
    val hostError = ConfigValidators.validateHost(host)
    val portError = ConfigValidators.validatePort(port)
    val chunksError = ConfigValidators.validateChunks(chunks)
    val usernameError = ConfigValidators.validateClientUsername(username)
    val passwordError = ConfigValidators.validateClientPassword(password)
    val isValid = listOf(nameError, hostError, portError, chunksError, usernameError, passwordError).all { it == null }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Push content above the system keyboard + nav bar so the Save button is reachable.
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(RaycastSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(RaycastSpacing.md)
    ) {
        Text(
            if (existing == null) "New Connection" else "Edit Connection",
            style = RaycastType.HeadlineMedium.copy(color = colors.TextPrimary)
        )

        RaycastInput(
            value = name,
            onValueChange = { name = it.take(ConfigValidators.MAX_PROFILE_NAME); touched = touched + "name" },
            label = "Profile name",
            placeholder = "Home NAS",
            imeAction = ImeAction.Next,
            enabled = !saving,
            error = show("name", nameError),
            modifier = Modifier.fillMaxWidth()
        )
        RaycastInput(
            value = host,
            onValueChange = { raw ->
                // Accept pasted URLs like ftp://user@192.168.1.10:2121/path — keep only the host
                // and move an embedded port into the Port field.
                // Only on paste / URL input, so typing an IPv6 address isn't rewritten mid-way.
                val pasted = raw.length - host.length > 1 || raw.contains("://")
                if (pasted) {
                    val (cleanHost, embeddedPort) = ConfigValidators.normalizeHost(raw)
                    host = cleanHost
                    if (embeddedPort != null && embeddedPort in 1..65535) port = embeddedPort.toString()
                } else {
                    host = raw.trimStart()
                }
                touched = touched + "host"
            },
            label = "Host",
            placeholder = "192.168.1.10",
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
            enabled = !saving,
            error = show("host", hostError),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RaycastSpacing.md)
        ) {
            RaycastInput(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() }.take(5); touched = touched + "port" },
                label = "Port",
                placeholder = "21",
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
                enabled = !saving,
                error = show("port", portError),
                modifier = Modifier.weight(1f)
            )
            RaycastInput(
                value = chunks,
                onValueChange = { chunks = it.filter { c -> c.isDigit() }.take(2); touched = touched + "chunks" },
                label = "Chunks",
                placeholder = "8",
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
                enabled = !saving,
                error = show("chunks", chunksError),
                modifier = Modifier.weight(1f)
            )
        }
        RaycastInput(
            value = username,
            onValueChange = { username = it.take(ConfigValidators.MAX_USERNAME); touched = touched + "username" },
            label = "Username",
            placeholder = "anonymous",
            imeAction = ImeAction.Next,
            enabled = !saving,
            error = show("username", usernameError),
            modifier = Modifier.fillMaxWidth()
        )
        RaycastInput(
            value = password,
            onValueChange = { password = it.take(ConfigValidators.MAX_PASSWORD); touched = touched + "password" },
            label = "Password",
            isPassword = true,
            imeAction = ImeAction.Done,
            enabled = !saving,
            error = show("password", passwordError),
            modifier = Modifier.fillMaxWidth()
        )
        if (username.isBlank()) {
            Text(
                "Leave username empty to log in anonymously.",
                style = RaycastType.MetaMono.copy(color = colors.TextTertiary)
            )
        }

        SettingRow(label = "PASV Mode", description = "Recommended for most networks", trailing = {
            RaycastSwitch(checked = passive, onCheckedChange = { passive = it }, enabled = !saving)
        })
        SettingRow(label = "FTPS (TLS)", description = "Explicit TLS — encrypts control & data channels", trailing = {
            RaycastSwitch(checked = ftps, onCheckedChange = { ftps = it }, enabled = !saving)
        })

        externalError?.let {
            Text(it, style = RaycastType.BodySmall.copy(color = colors.AccentStart))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RaycastSpacing.md)
        ) {
            RaycastButton(
                text = "Cancel",
                onClick = onDismiss,
                style = RaycastButtonStyle.Secondary,
                enabled = !saving,
                modifier = Modifier.weight(1f)
            )
            RaycastButton(
                text = if (saving) "Saving…" else "Save",
                onClick = {
                    submitted = true
                    if (!isValid) return@RaycastButton
                    val cleanHost = host.trim()
                    onSave(
                        ConnectionProfile(
                            id = existing?.id ?: 0L,
                            name = name.trim().ifBlank { cleanHost },
                            host = cleanHost,
                            port = port.trim().toInt(),
                            username = username.trim(),
                            password = password,
                            passive = passive,
                            ftps = ftps,
                            chunkCount = chunks.trim().toInt(),
                            lastConnectedAt = existing?.lastConnectedAt ?: 0L
                        )
                    )
                },
                style = RaycastButtonStyle.Primary,
                // Keep Save tappable when invalid so the tap reveals every field error.
                enabled = !saving,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
