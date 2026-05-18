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
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
            contentPadding = PaddingValues(RaycastSpacing.lg),
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
                        onDelete = { viewModel.deleteProfile(profile) },
                        connecting = state.connecting
                    )
                }
            }
            state.error?.let { err ->
                item {
                    RaycastCard(
                        modifier = Modifier.fillMaxWidth(),
                        background = colors.AccentStart.copy(alpha = 0.10f)
                    ) {
                        Text(err, style = RaycastType.BodySmall.copy(color = colors.AccentStart))
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
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeSheet() },
            sheetState = sheetState,
            containerColor = colors.SurfaceElevated,
            contentColor = colors.TextPrimary
        ) {
            ConnectionSheet(
                existing = state.editingProfile,
                onSave = { viewModel.saveProfile(it) },
                onDismiss = { viewModel.closeSheet() }
            )
        }
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
    connecting: Boolean
) {
    val colors = LocalRaycastColors.current
    RaycastCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RaycastSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(profile.name, style = RaycastType.HeadlineMedium.copy(color = colors.TextPrimary))
                    Text(
                        "${profile.host}:${profile.port}",
                        style = RaycastType.LabelMono.copy(color = colors.TextSecondary)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RaycastButton(
                        text = "Edit",
                        onClick = onEdit,
                        style = RaycastButtonStyle.Secondary,
                        leading = { Icon(Icons.Filled.Edit, null, modifier = Modifier.size(14.dp)) }
                    )
                    RaycastButton(
                        text = "Delete",
                        onClick = onDelete,
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
                    "Last connected ${HumanReadableUtils.relative(profile.lastConnectedAt)}",
                    style = RaycastType.MetaMono.copy(color = colors.TextTertiary)
                )
                RaycastButton(
                    text = if (connecting) "Connecting…" else "Connect",
                    onClick = onConnect,
                    style = RaycastButtonStyle.Primary,
                    enabled = !connecting
                )
            }
        }
    }
}

@Composable
private fun ConnectionSheet(
    existing: ConnectionProfile?,
    onSave: (ConnectionProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalRaycastColors.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var port by remember { mutableStateOf((existing?.port ?: 21).toString()) }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf(existing?.password ?: "") }
    var passive by remember { mutableStateOf(existing?.passive ?: true) }
    var ftps by remember { mutableStateOf(existing?.ftps ?: false) }
    var chunks by remember { mutableStateOf((existing?.chunkCount ?: 8).toString()) }

    // Save is only enabled with the bare minimum to attempt a connection.
    val canSave = host.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535

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
            onValueChange = { name = it },
            label = "Profile name",
            placeholder = "Home NAS",
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth()
        )
        RaycastInput(
            value = host,
            onValueChange = { host = it },
            label = "Host",
            placeholder = "192.168.1.10",
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RaycastSpacing.md)
        ) {
            RaycastInput(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                label = "Port",
                placeholder = "21",
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
                modifier = Modifier.weight(1f)
            )
            RaycastInput(
                value = chunks,
                onValueChange = { chunks = it.filter { c -> c.isDigit() }.take(2) },
                label = "Chunks",
                placeholder = "8",
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
                modifier = Modifier.weight(1f)
            )
        }
        RaycastInput(
            value = username,
            onValueChange = { username = it },
            label = "Username",
            placeholder = "anonymous",
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth()
        )
        RaycastInput(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            isPassword = true,
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth()
        )

        SettingRow(label = "PASV Mode", description = "Recommended for most networks", trailing = {
            RaycastSwitch(checked = passive, onCheckedChange = { passive = it })
        })
        SettingRow(label = "FTPS (TLS)", description = "Encrypt control & data channels", trailing = {
            RaycastSwitch(checked = ftps, onCheckedChange = { ftps = it })
        })

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(RaycastSpacing.md)
        ) {
            RaycastButton(
                text = "Cancel",
                onClick = onDismiss,
                style = RaycastButtonStyle.Secondary,
                modifier = Modifier.weight(1f)
            )
            RaycastButton(
                text = "Save",
                onClick = {
                    onSave(
                        ConnectionProfile(
                            id = existing?.id ?: 0L,
                            name = name.ifBlank { host },
                            host = host.trim(),
                            port = port.toIntOrNull()?.coerceIn(1, 65535) ?: 21,
                            username = username,
                            password = password,
                            passive = passive,
                            ftps = ftps,
                            chunkCount = chunks.toIntOrNull()?.coerceIn(2, 32) ?: 8,
                            lastConnectedAt = existing?.lastConnectedAt ?: 0L
                        )
                    )
                },
                style = RaycastButtonStyle.Primary,
                enabled = canSave,
                modifier = Modifier.weight(1f)
            )
        }
    }
}


