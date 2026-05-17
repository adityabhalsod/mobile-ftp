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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobileftp.data.repository.ServerStatus
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
import com.mobileftp.util.StorageUtils

@Composable
fun ServerScreen(
    themePreference: ThemePreference,
    onToggleTheme: () -> Unit,
    viewModel: ServerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = LocalRaycastColors.current
    var qrExpanded by remember { mutableStateOf(false) }
    var configExpanded by remember { mutableStateOf(false) }

    // Re-check MANAGE_EXTERNAL_STORAGE every time the activity resumes,
    // so returning from system Settings hides the warning banner immediately.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            item { AllFilesAccessBanner() }
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

        item { ConnectionsCard(state) }

        item { ThroughputCard(state) }

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
                if (running) {
                    RaycastButton(
                        text = "Stop",
                        onClick = onStop,
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
                        text = "Start",
                        onClick = onStart,
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
                        tint = colors.TextTertiary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onRefreshLan)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("PUBLIC", style = RaycastType.LabelMono.copy(color = colors.TextSecondary))
                Text(
                    state.publicIp ?: "—",
                    style = RaycastType.BodySmall.copy(color = colors.TextSecondary)
                )
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh public IP",
                    tint = colors.TextTertiary,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onRefreshPublic)
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(client.ip, style = RaycastType.LabelMono.copy(color = colors.TextPrimary))
                        Text(client.username, style = RaycastType.MetaMono.copy(color = colors.TextSecondary))
                        Text(
                            HumanReadableUtils.relative(client.connectedAt),
                            style = RaycastType.MetaMono.copy(color = colors.TextTertiary)
                        )
                    }
                }
            }
        }
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
    var port by remember(state.config.port) { mutableStateOf(state.config.port.toString()) }
    var username by remember(state.config.username) { mutableStateOf(state.config.username) }
    var password by remember(state.config.password) { mutableStateOf(state.config.password) }
    var rootPath by remember(state.config.rootDirectoryPath) { mutableStateOf(state.config.rootDirectoryPath) }
    var rootUri by remember(state.config.rootDirectoryUri) { mutableStateOf(state.config.rootDirectoryUri) }
    var maxConn by remember(state.config.maxConnections) { mutableStateOf(state.config.maxConnections.toFloat()) }
    var chunkCount by remember(state.config.chunkCount) { mutableStateOf(state.config.chunkCount.toFloat()) }
    var ftps by remember(state.config.ftpsEnabled) { mutableStateOf(state.config.ftpsEnabled) }
    var anon by remember(state.config.anonymousAccess) { mutableStateOf(state.config.anonymousAccess) }
    var pasvStart by remember(state.config.pasvPortStart) { mutableStateOf(state.config.pasvPortStart.toString()) }
    var pasvEnd by remember(state.config.pasvPortEnd) { mutableStateOf(state.config.pasvPortEnd.toString()) }

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
                Column {
                    Text("CONFIGURATION", style = RaycastType.LabelMono.copy(color = colors.TextSecondary))
                    Text("Port, credentials, advanced", style = RaycastType.BodySmall.copy(color = colors.TextTertiary))
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
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
                        onValueChange = { username = it },
                        label = "Username",
                        modifier = Modifier.fillMaxWidth()
                    )
                    RaycastInput(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        isPassword = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SettingGroupHeader("NETWORK")
                    RaycastInput(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = "Port",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(RaycastSpacing.md)
                    ) {
                        RaycastInput(
                            value = pasvStart,
                            onValueChange = { pasvStart = it.filter { c -> c.isDigit() } },
                            label = "PASV Start",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        RaycastInput(
                            value = pasvEnd,
                            onValueChange = { pasvEnd = it.filter { c -> c.isDigit() } },
                            label = "PASV End",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    SettingRow(label = "FTPS (TLS)", description = "Encrypt control & data channels", trailing = {
                        RaycastSwitch(checked = ftps, onCheckedChange = { ftps = it })
                    })
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

                    RaycastButton(
                        text = "Save Configuration",
                        onClick = {
                            val newConfig = state.config.copy(
                                port = port.toIntOrNull() ?: state.config.port,
                                username = username,
                                password = password,
                                rootDirectoryUri = rootUri,
                                rootDirectoryPath = rootPath,
                                pasvPortStart = pasvStart.toIntOrNull() ?: state.config.pasvPortStart,
                                pasvPortEnd = pasvEnd.toIntOrNull() ?: state.config.pasvPortEnd,
                                maxConnections = maxConn.toInt(),
                                chunkCount = chunkCount.toInt(),
                                ftpsEnabled = ftps,
                                anonymousAccess = anon
                            )
                            onSave(newConfig)
                            val msg = if (state.status == ServerStatus.RUNNING)
                                "Configuration saved. Restart the server to apply."
                            else
                                "Configuration saved."
                            android.widget.Toast.makeText(
                                context,
                                msg,
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        },
                        style = RaycastButtonStyle.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
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
private fun AllFilesAccessBanner() {
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
