package com.mobileftp.ui.server

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobileftp.data.repository.ServerStatus
import com.mobileftp.data.repository.ServerTransfer
import com.mobileftp.data.repository.ServerTransferDirection
import com.mobileftp.data.repository.ServerTransferStatus
import com.mobileftp.ui.components.NetworkBadge
import com.mobileftp.ui.components.QrCodeCard
import com.mobileftp.ui.components.RaycastButton
import com.mobileftp.ui.components.RaycastButtonStyle
import com.mobileftp.ui.components.RaycastCard
import com.mobileftp.ui.components.RaycastInput
import com.mobileftp.ui.components.RaycastSwitch
import com.mobileftp.ui.components.SettingGroupHeader
import com.mobileftp.ui.components.SettingRow
import com.mobileftp.ui.components.StatusPill
import com.mobileftp.ui.components.StatusPillState
import com.mobileftp.ui.components.ThroughputGraph
import com.mobileftp.ui.theme.LocalRaycastColors
import com.mobileftp.ui.theme.RaycastSpacing
import com.mobileftp.ui.theme.RaycastType
import com.mobileftp.ui.theme.ThemePreference
import com.mobileftp.util.HumanReadableUtils
import com.mobileftp.util.ConfigValidators
import com.mobileftp.util.StorageUtils

@Composable
fun ServerScreen(
    themePreference: ThemePreference,
    onToggleTheme: () -> Unit,
    viewModel: ServerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = LocalRaycastColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var qrExpanded by remember { mutableStateOf(false) }
    var configExpanded by remember { mutableStateOf(false) }
    // Set when user taps "Open Settings"; cleared once we've evaluated the result.
    var awaitingPermissionReturn by remember { mutableStateOf(false) }

    // Re-check MANAGE_EXTERNAL_STORAGE every time the activity resumes,
    // so returning from system Settings hides the warning banner immediately.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
                if (awaitingPermissionReturn) {
                    awaitingPermissionReturn = false
                    if (!com.mobileftp.util.PermissionUtils.hasManageStorage()) {
                        android.widget.Toast.makeText(
                            context,
                            "All Files Access is still not allowed. Please enable it for MobileFTP.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "All Files Access granted",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // One-shot messages from the ViewModel (save result, validation, …).
    androidx.compose.runtime.LaunchedEffect(state.message) {
        state.message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Surface),
        contentPadding = PaddingValues(RaycastSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(RaycastSpacing.lg)
    ) {
        item { ServerHeader(themePreference, onToggleTheme) }

        if (!state.hasAllFilesAccess) {
            item {
                AllFilesAccessBanner(
                    onOpenSettings = { awaitingPermissionReturn = true }
                )
            }
        }

        item {
            StatusHeroCard(
                state = state,
                onStart = { viewModel.startServer() },
                onStop = { viewModel.stopServer() },
                onRefreshPublic = { viewModel.refreshPublicIp() },
                onRefreshLan = { viewModel.refreshLanIp() },
                qrExpanded = qrExpanded,
                onToggleQr = { qrExpanded = !qrExpanded }
            )
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

        item { ConnectionsCard(state) }

        item { ThroughputCard(state) }

        item { ServerTransfersCard(state) }

        item {
            ConfigSection(
                expanded = configExpanded,
                onToggle = { configExpanded = !configExpanded },
                state = state,
                onSave = { viewModel.saveConfig(it) }
            )
        }
    }
}

@Composable
private fun ServerHeader(
    themePreference: ThemePreference,
    onToggleTheme: () -> Unit
) {
    val colors = LocalRaycastColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("FTP Server", style = RaycastType.DisplayLarge.copy(color = colors.TextPrimary))
            Text(
                "Light-Speed File Transfer",
                style = RaycastType.LabelMono.copy(color = colors.TextSecondary)
            )
        }
        RaycastCard(
            paddingValues = PaddingValues(8.dp),
            onClick = onToggleTheme
        ) {
            Icon(
                imageVector = if (themePreference == ThemePreference.LIGHT)
                    Icons.Filled.LightMode else Icons.Filled.DarkMode,
                contentDescription = "Toggle theme",
                tint = colors.TextPrimary
            )
        }
    }
}

@Composable
private fun StatusHeroCard(
    state: ServerScreenState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefreshPublic: () -> Unit,
    onRefreshLan: () -> Unit,
    qrExpanded: Boolean,
    onToggleQr: () -> Unit
) {
    val colors = LocalRaycastColors.current
    val running = state.status == ServerStatus.RUNNING

    val statusPillState = when (state.status) {
        ServerStatus.RUNNING -> StatusPillState.Running
        ServerStatus.STARTING -> StatusPillState.Info
        ServerStatus.ERROR -> StatusPillState.Error
        ServerStatus.STOPPED -> StatusPillState.Stopped
    }
    val statusLabel = when (state.status) {
        ServerStatus.RUNNING -> "RUNNING"
        ServerStatus.STARTING -> "STARTING"
        ServerStatus.ERROR -> "ERROR"
        ServerStatus.STOPPED -> "STOPPED"
    }

    RaycastCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RaycastSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusPill(state = statusPillState, label = statusLabel)
                val busy = state.lifecyclePending || state.status == ServerStatus.STARTING
                if (running) {
                    RaycastButton(
                        text = if (busy) "Stopping…" else "Stop",
                        onClick = onStop,
                        enabled = !busy,
                        style = RaycastButtonStyle.Destructive,
                        leading = {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                } else {
                    RaycastButton(
                        text = if (busy) "Starting…" else "Start",
                        onClick = onStart,
                        enabled = !busy,
                        style = RaycastButtonStyle.Primary,
                        leading = {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("LAN IP", style = RaycastType.LabelMono.copy(color = colors.TextSecondary))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        state.lanIp,
                        style = RaycastType.HeadlineMedium.copy(color = colors.TextPrimary)
                    )
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh LAN IP",
                        tint = if (state.refreshingLan) colors.TextTertiary.copy(alpha = 0.4f) else colors.TextTertiary,
                        modifier = Modifier
                            .clickable(enabled = !state.refreshingLan, onClick = onRefreshLan)
                            .padding(4.dp)
                            .size(16.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("PUBLIC", style = RaycastType.LabelMono.copy(color = colors.TextSecondary))
                Text(
                    if (state.refreshingPublic) "…" else state.publicIp ?: "—",
                    style = RaycastType.BodySmall.copy(color = colors.TextSecondary)
                )
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh public IP",
                    tint = if (state.refreshingPublic) colors.TextTertiary.copy(alpha = 0.4f) else colors.TextTertiary,
                    modifier = Modifier
                        .clickable(enabled = !state.refreshingPublic, onClick = onRefreshPublic)
                        .padding(4.dp)
                        .size(14.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("PORT", style = RaycastType.LabelMono.copy(color = colors.TextSecondary))
                Text(
                    state.config.port.toString(),
                    style = RaycastType.LabelMono.copy(color = colors.AccentBlue)
                )
            }

            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "SHARING",
                    style = RaycastType.LabelMono.copy(color = colors.TextSecondary),
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    state.activeRootPath.ifBlank { "—" },
                    style = RaycastType.MetaMono.copy(color = colors.TextSecondary)
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.interfaces.take(3).forEach { NetworkBadge(info = it) }
            }

            // QR toggle row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = RaycastSpacing.sm)
                    .background(colors.SurfaceCard, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleQr)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (qrExpanded) "Hide QR Code" else "Show QR Code",
                    style = RaycastType.TitleSmall.copy(color = colors.TextPrimary)
                )
                Icon(
                    imageVector = if (qrExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = colors.TextSecondary,
                    modifier = Modifier
                        .height(20.dp)
                )
            }

            AnimatedVisibility(
                visible = qrExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val url = "ftp://${state.config.username}@${state.lanIp}:${state.config.port}"
                val caption = "ftp://${state.config.username}:••••@${state.lanIp}:${state.config.port}"
                QrCodeCard(connectionUrl = url, displayCaption = caption)
            }
        }
    }
}

@Composable
private fun ConnectionsCard(state: ServerScreenState) {
    val colors = LocalRaycastColors.current
    RaycastCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RaycastSpacing.sm)) {
            Text("CONNECTIONS", style = RaycastType.LabelMono.copy(color = colors.TextSecondary))
            Text(
                state.clients.size.toString(),
                style = RaycastType.DisplayLarge.copy(color = colors.AccentGreen)
            )
            if (state.clients.isEmpty()) {
                Text("No connected clients", style = RaycastType.BodySmall.copy(color = colors.TextTertiary))
            } else {
                state.clients.forEach { client ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(client.ip, style = RaycastType.LabelMono.copy(color = colors.TextPrimary))
                            Text(client.username, style = RaycastType.MetaMono.copy(color = colors.TextSecondary))
                            Text(
                                HumanReadableUtils.relative(client.connectedAt),
                                style = RaycastType.MetaMono.copy(color = colors.TextTertiary)
                            )
                        }
                        // What the client is doing: a live transfer, else its last command.
                        val transfer = client.currentTransfer
                        val activity = when {
                            transfer != null -> "${directionArrow(transfer.direction)} ${transfer.fileName} · " +
                                "${HumanReadableUtils.bytes(transfer.bytes)} · " +
                                HumanReadableUtils.bytesPerSecond(transfer.averageBps())
                            client.lastCommand != null ->
                                "${client.lastCommand} · ${HumanReadableUtils.relative(client.lastActivityAt)}"
                            else -> "Connected, waiting for commands"
                        }
                        Text(
                            activity,
                            maxLines = 1,
                            style = RaycastType.MetaMono.copy(
                                color = if (transfer != null) colors.AccentBlue else colors.TextTertiary
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun directionArrow(direction: ServerTransferDirection): String =
    if (direction == ServerTransferDirection.UPLOAD) "↑" else "↓"

/** Transfers made by clients connected to this server: live ones first, then recent history. */
@Composable
private fun ServerTransfersCard(state: ServerScreenState) {
    val colors = LocalRaycastColors.current
    RaycastCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RaycastSpacing.sm)) {
            Text("TRANSFERS", style = RaycastType.LabelMono.copy(color = colors.TextSecondary))
            if (state.transfers.isEmpty()) {
                Text(
                    "No uploads or downloads from connected clients yet",
                    style = RaycastType.BodySmall.copy(color = colors.TextTertiary)
                )
            } else {
                Text(
                    "Total ${HumanReadableUtils.bytes(state.bytesTransferred)}",
                    style = RaycastType.MetaMono.copy(color = colors.TextTertiary)
                )
                state.transfers.forEach { ServerTransferRow(it) }
            }
        }
    }
}

@Composable
private fun ServerTransferRow(transfer: ServerTransfer) {
    val colors = LocalRaycastColors.current
    val (label, tint) = when (transfer.status) {
        ServerTransferStatus.ACTIVE -> HumanReadableUtils.bytesPerSecond(transfer.averageBps()) to colors.AccentBlue
        ServerTransferStatus.COMPLETED -> "Done" to colors.AccentGreen
        ServerTransferStatus.FAILED -> "Failed" to colors.AccentStart
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${directionArrow(transfer.direction)} ${transfer.fileName}",
                maxLines = 1,
                style = RaycastType.LabelMono.copy(color = colors.TextPrimary),
                modifier = Modifier.weight(1f)
            )
            Text(label, style = RaycastType.MetaMono.copy(color = tint))
        }
        val details = buildList {
            add(transfer.clientIp)
            add(HumanReadableUtils.bytes(transfer.bytes))
            if (transfer.status != ServerTransferStatus.ACTIVE) {
                add(HumanReadableUtils.bytesPerSecond(transfer.averageBps()))
                add(HumanReadableUtils.relative(transfer.finishedAt ?: transfer.startedAt))
            }
            transfer.error?.let { add(it) }
        }.joinToString(" · ")
        Text(details, maxLines = 2, style = RaycastType.MetaMono.copy(color = colors.TextTertiary))
    }
}

@Composable
private fun ThroughputCard(state: ServerScreenState) {
    val colors = LocalRaycastColors.current
    RaycastCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        paddingValues = PaddingValues(RaycastSpacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RaycastSpacing.sm), modifier = Modifier.fillMaxSize()) {
            Text("THROUGHPUT", style = RaycastType.LabelMono.copy(color = colors.TextSecondary))
            ThroughputGraph(
                samples = state.throughput.samples,
                currentBps = state.throughput.current,
                peakBps = state.throughput.peak,
                avgBps = state.throughput.average,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ConfigSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    state: ServerScreenState,
    onSave: (com.mobileftp.domain.model.ServerConfig) -> Unit
) {
    val colors = LocalRaycastColors.current
    val context = LocalContext.current
    // The form edits the *persisted* config. It used to be keyed per-field on the
    // config currently shown, which is the *running* config while the server is up:
    // after Save the fields snapped back to the old running values and every
    // upstream emission wiped what the user was typing.
    val saved = state.savedConfig
    var port by remember(saved) { mutableStateOf(saved.port.toString()) }
    var username by remember(saved) { mutableStateOf(saved.username) }
    var password by remember(saved) { mutableStateOf(saved.password) }
    var rootPath by remember(saved) { mutableStateOf(saved.rootDirectoryPath) }
    var rootUri by remember(saved) { mutableStateOf(saved.rootDirectoryUri) }
    var maxConn by remember(saved) { mutableStateOf(saved.maxConnections.coerceIn(1, 32).toFloat()) }
    var chunkCount by remember(saved) { mutableStateOf(saved.chunkCount.coerceIn(2, 32).toFloat()) }
    var anon by remember(saved) { mutableStateOf(saved.anonymousAccess) }
    var pasvStart by remember(saved) { mutableStateOf(saved.pasvPortStart.toString()) }
    var pasvEnd by remember(saved) { mutableStateOf(saved.pasvPortEnd.toString()) }
    var showErrors by remember(saved) { mutableStateOf(false) }

    // Inline validation for every field.
    val portError = ConfigValidators.validatePort(port, ConfigValidators.MIN_UNPRIVILEGED_PORT)
    val usernameError = ConfigValidators.validateServerUsername(username)
    val passwordError = ConfigValidators.validateServerPassword(password)
    val (pasvStartError, pasvEndError) =
        ConfigValidators.validatePasvRange(pasvStart, pasvEnd, port.toIntOrNull())
    val hasErrors = listOf(portError, usernameError, passwordError, pasvStartError, pasvEndError)
        .any { it != null }

    // Show an error once the field differs from what is saved, or after a Save attempt.
    fun shown(error: String?, value: String, original: String): String? =
        if (showErrors || value != original) error else null

    val draft = saved.copy(
        port = port.toIntOrNull() ?: saved.port,
        username = username.trim(),
        password = password,
        rootDirectoryUri = rootUri,
        rootDirectoryPath = rootPath,
        pasvPortStart = pasvStart.toIntOrNull() ?: saved.pasvPortStart,
        pasvPortEnd = pasvEnd.toIntOrNull() ?: saved.pasvPortEnd,
        maxConnections = maxConn.toInt(),
        chunkCount = chunkCount.toInt(),
        // Server-side FTPS is not implemented yet (needs a TLS keystore); never persist it on.
        ftpsEnabled = false,
        anonymousAccess = anon
    )
    val dirty = draft != saved.copy(ftpsEnabled = false)
    // The PASV range depends on three fields, so surface its errors when any of them changed.
    val showPasvErrors = showErrors ||
        port != saved.port.toString() ||
        pasvStart != saved.pasvPortStart.toString() ||
        pasvEnd != saved.pasvPortEnd.toString()

    // SAF directory picker — gives us a content tree URI we translate to a real path.
    val pickDirectory = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Persist permission so the URI keeps working across reboots.
            runCatching {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            val resolved = StorageUtils.pathFromTreeUri(uri)
            if (resolved != null) {
                rootUri = uri.toString()
                rootPath = resolved
                android.widget.Toast.makeText(
                    context,
                    "Folder selected: $resolved",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                android.widget.Toast.makeText(
                    context,
                    "That folder isn't directly accessible. Pick one on internal storage.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    RaycastCard(
        modifier = Modifier.fillMaxWidth(),
        paddingValues = PaddingValues(0.dp)
    ) {
        Column {
            // Only the header row toggles the section — keeps fields/sliders below clickable.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(RaycastSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("CONFIGURATION", style = RaycastType.LabelMono.copy(color = colors.TextSecondary))
                    Text(
                        when {
                            dirty -> "Unsaved changes"
                            state.restartRequired -> "Saved — restart the server to apply"
                            else -> "Port, credentials, advanced"
                        },
                        style = RaycastType.BodySmall.copy(
                            color = if (dirty || state.restartRequired) colors.AccentAmber else colors.TextTertiary
                        )
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse configuration" else "Expand configuration",
                    tint = colors.TextSecondary
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(RaycastSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(RaycastSpacing.md)
                ) {
                    SettingGroupHeader("AUTHENTICATION")
                    RaycastInput(
                        value = username,
                        onValueChange = { username = it.take(ConfigValidators.MAX_USERNAME) },
                        label = "Username",
                        imeAction = ImeAction.Next,
                        error = shown(usernameError, username, saved.username),
                        modifier = Modifier.fillMaxWidth()
                    )
                    RaycastInput(
                        value = password,
                        onValueChange = { password = it.take(ConfigValidators.MAX_PASSWORD) },
                        label = "Password",
                        isPassword = true,
                        imeAction = ImeAction.Next,
                        error = shown(passwordError, password, saved.password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    SettingGroupHeader("NETWORK")
                    RaycastInput(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                        label = "Port",
                        placeholder = "2121",
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                        error = shown(portError, port, saved.port.toString()),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RaycastSpacing.md)
                    ) {
                        RaycastInput(
                            value = pasvStart,
                            onValueChange = { pasvStart = it.filter { c -> c.isDigit() }.take(5) },
                            label = "PASV Start",
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                            error = if (showPasvErrors) pasvStartError else null,
                            modifier = Modifier.weight(1f)
                        )
                        RaycastInput(
                            value = pasvEnd,
                            onValueChange = { pasvEnd = it.filter { c -> c.isDigit() }.take(5) },
                            label = "PASV End",
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                            error = if (showPasvErrors) pasvEndError else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    SettingRow(
                        label = "FTPS (TLS)",
                        description = "Not available for the built-in server yet",
                        trailing = {
                            RaycastSwitch(checked = false, onCheckedChange = {}, enabled = false)
                        }
                    )
                    SettingRow(label = "Anonymous access", description = "Allow login without password (off by default)", trailing = {
                        RaycastSwitch(checked = anon, onCheckedChange = { anon = it })
                    })

                    SettingGroupHeader("STORAGE")
                    SettingRow(
                        label = "Shared directory",
                        description = if (rootPath.isBlank())
                            "Default: app-private external storage"
                        else rootPath,
                        onClick = { pickDirectory.launch(null) },
                        trailing = {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = "Pick folder",
                                tint = colors.AccentAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    if (rootPath.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            RaycastButton(
                                text = "Use default",
                                onClick = {
                                    rootUri = ""
                                    rootPath = ""
                                    android.widget.Toast.makeText(
                                        context,
                                        "Reverted to default folder",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                style = RaycastButtonStyle.Secondary
                            )
                        }
                    }

                    SettingGroupHeader("PERFORMANCE")
                    Text("Max connections: ${maxConn.toInt()}", style = RaycastType.LabelMono.copy(color = colors.TextSecondary))
                    Slider(
                        value = maxConn,
                        onValueChange = { maxConn = it },
                        valueRange = 1f..32f,
                        steps = 30,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.AccentBlue,
                            activeTrackColor = colors.AccentBlue,
                            inactiveTrackColor = colors.BorderDefault
                        )
                    )
                    Text("Chunks: ${chunkCount.toInt()}", style = RaycastType.LabelMono.copy(color = colors.TextSecondary))
                    Slider(
                        value = chunkCount,
                        onValueChange = { chunkCount = it },
                        valueRange = 2f..32f,
                        steps = 29,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.AccentPurple,
                            activeTrackColor = colors.AccentPurple,
                            inactiveTrackColor = colors.BorderDefault
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RaycastSpacing.md)
                    ) {
                        RaycastButton(
                            text = "Reset",
                            onClick = {
                                port = saved.port.toString()
                                username = saved.username
                                password = saved.password
                                rootPath = saved.rootDirectoryPath
                                rootUri = saved.rootDirectoryUri
                                maxConn = saved.maxConnections.coerceIn(1, 32).toFloat()
                                chunkCount = saved.chunkCount.coerceIn(2, 32).toFloat()
                                anon = saved.anonymousAccess
                                pasvStart = saved.pasvPortStart.toString()
                                pasvEnd = saved.pasvPortEnd.toString()
                                showErrors = false
                            },
                            style = RaycastButtonStyle.Secondary,
                            enabled = dirty && !state.saving,
                            modifier = Modifier.weight(1f)
                        )
                        RaycastButton(
                            text = if (state.saving) "Saving…" else "Save",
                            onClick = {
                                if (hasErrors) {
                                    showErrors = true
                                    android.widget.Toast.makeText(
                                        context,
                                        "Please fix the highlighted fields.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    onSave(draft)
                                }
                            },
                            style = RaycastButtonStyle.Primary,
                            enabled = dirty && !state.saving,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}


/**
 * Warning banner shown when MANAGE_EXTERNAL_STORAGE isn't granted.
 * Without it, the FTP server can only serve the app's private external dir,
 * which is the empty folder users see in their FTP client.
 */
@Composable
private fun AllFilesAccessBanner(
    onOpenSettings: () -> Unit
) {
    val colors = LocalRaycastColors.current
    val context = LocalContext.current
    RaycastCard(
        modifier = Modifier.fillMaxWidth(),
        background = colors.AccentAmber.copy(alpha = 0.10f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RaycastSpacing.sm)) {
            Text(
                "All Files Access required",
                style = RaycastType.HeadlineMedium.copy(color = colors.AccentAmber)
            )
            Text(
                "Without this, the FTP root is limited to the app's private folder " +
                    "(empty by default) and your phone's real files won't be visible.",
                style = RaycastType.BodySmall.copy(color = colors.TextSecondary)
            )
            RaycastButton(
                text = "Open Settings",
                onClick = {
                    // Tell the parent to start watching for the result.
                    onOpenSettings()
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                    } else {
                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                    }
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
                style = RaycastButtonStyle.Primary
            )
        }
    }
}
