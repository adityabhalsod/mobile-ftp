package com.mobileftp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobileftp.domain.model.NetworkInterfaceInfo
import com.mobileftp.domain.model.NetworkKind
import com.mobileftp.ui.theme.LocalRaycastColors
import com.mobileftp.ui.theme.RaycastShapes
import com.mobileftp.ui.theme.RaycastType

/**
 * Compact network interface chip showing the active interface.
 * - WiFi Direct: AccentGreen
 * - WiFi 5GHz: AccentBlue / 2.4GHz: 70% AccentBlue
 * - Hotspot: AccentAmber / Cellular: 60% AccentAmber
 * - USB Ethernet: AccentGreen
 */
@Composable
fun NetworkBadge(
    info: NetworkInterfaceInfo?,
    modifier: Modifier = Modifier
) {
    val colors = LocalRaycastColors.current
    val accent: Color = when (info?.kind) {
        NetworkKind.WIFI_DIRECT -> colors.AccentGreen
        NetworkKind.WIFI_5GHZ -> colors.AccentBlue
        NetworkKind.WIFI_24GHZ -> colors.AccentBlue.copy(alpha = 0.7f)
        NetworkKind.USB_ETHERNET -> colors.AccentGreen
        NetworkKind.HOTSPOT -> colors.AccentAmber
        NetworkKind.CELLULAR -> colors.AccentAmber.copy(alpha = 0.6f)
        else -> colors.TextTertiary
    }
    val label = when (info?.kind) {
        NetworkKind.WIFI_DIRECT -> "WiFi Direct"
        NetworkKind.WIFI_5GHZ -> "WiFi 5GHz"
        NetworkKind.WIFI_24GHZ -> "WiFi 2.4GHz"
        NetworkKind.USB_ETHERNET -> "USB Ethernet"
        NetworkKind.HOTSPOT -> "Hotspot"
        NetworkKind.CELLULAR -> "Cellular"
        NetworkKind.UNKNOWN, null -> "Offline"
    }
    val icon = when (info?.kind) {
        NetworkKind.WIFI_DIRECT, NetworkKind.WIFI_5GHZ, NetworkKind.WIFI_24GHZ -> Icons.Filled.Wifi
        NetworkKind.USB_ETHERNET -> Icons.Filled.Usb
        NetworkKind.HOTSPOT -> Icons.Filled.WifiTethering
        NetworkKind.CELLULAR -> Icons.Filled.NetworkCell
        else -> Icons.Filled.LinkOff
    }

    val shape = RoundedCornerShape(RaycastShapes.ChipRadius)
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.SurfaceCard)
            .border(1.dp, colors.BorderDefault, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
        Text(label, style = RaycastType.LabelMono.copy(color = accent))
        if (info?.ipv4 != null) {
            Text(info.ipv4, style = RaycastType.MetaMono.copy(color = colors.TextSecondary))
        }
    }
}
