package com.mobileftp.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapVerticalCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobileftp.ui.client.ClientScreen
import com.mobileftp.ui.client.ClientViewModel
import com.mobileftp.ui.client.FileBrowserScreen
import com.mobileftp.ui.server.ServerScreen
import com.mobileftp.ui.theme.LocalRaycastColors
import com.mobileftp.ui.theme.RaycastSpacing
import com.mobileftp.ui.theme.RaycastType
import com.mobileftp.ui.theme.ThemePreference
import com.mobileftp.ui.transfers.TransferQueueScreen

enum class Tab(val title: String, val icon: ImageVector) {
    SERVER("Server", Icons.Filled.Storage),
    TRANSFERS("Transfers", Icons.Filled.SwapVerticalCircle),
    CLIENT("Client", Icons.Filled.Cloud)
}

@Composable
fun MobileFtpApp(
    themePreference: ThemePreference,
    onSetTheme: (ThemePreference) -> Unit
) {
    val colors = LocalRaycastColors.current
    var currentTab by remember { mutableStateOf(Tab.SERVER) }
    val clientVm: ClientViewModel = hiltViewModel()
    val connectedProfile by clientVm.connectedProfile.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.Surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
            ) {
                when (currentTab) {
                    Tab.SERVER -> ServerScreen(
                        themePreference = themePreference,
                        onToggleTheme = {
                            val next = when (themePreference) {
                                ThemePreference.SYSTEM -> ThemePreference.LIGHT
                                ThemePreference.LIGHT -> ThemePreference.DARK
                                ThemePreference.DARK -> ThemePreference.SYSTEM
                            }
                            onSetTheme(next)
                        }
                    )
                    Tab.TRANSFERS -> TransferQueueScreen()
                    Tab.CLIENT -> {
                        val profile = connectedProfile
                        if (profile != null) {
                            FileBrowserScreen(
                                profile = profile,
                                onDisconnect = { clientVm.disconnect() }
                            )
                        } else {
                            ClientScreen(onConnected = { /* tab continues to render with browser */ })
                        }
                    }
                }
            }
            BottomNav(
                current = currentTab,
                onSelect = { currentTab = it }
            )
        }
    }
}

@Composable
private fun BottomNav(
    current: Tab,
    onSelect: (Tab) -> Unit
) {
    val colors = LocalRaycastColors.current
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.BorderSubtle)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.Surface)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tab.values().forEach { tab ->
                NavItem(
                    tab = tab,
                    selected = tab == current,
                    onClick = { onSelect(tab) }
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    tab: Tab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalRaycastColors.current
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 28.dp else 0.dp,
        animationSpec = tween(220),
        label = "nav-indicator"
    )

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = RaycastSpacing.lg, vertical = RaycastSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.title,
            tint = if (selected) colors.AccentStart else colors.TextTertiary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            tab.title,
            style = RaycastType.LabelMono.copy(
                color = if (selected) colors.TextPrimary else colors.TextTertiary
            )
        )
        Box(
            modifier = Modifier
                .height(2.dp)
                .background(colors.AccentStart)
                .size(width = indicatorWidth, height = 2.dp)
        )
    }
}
