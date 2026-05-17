package com.mobileftp.domain.model

enum class NetworkKind {
    WIFI_DIRECT, WIFI_5GHZ, WIFI_24GHZ, USB_ETHERNET, HOTSPOT, CELLULAR, UNKNOWN
}

data class NetworkInterfaceInfo(
    val name: String,
    val displayName: String,
    val ipv4: String?,
    val ipv6: String?,
    val kind: NetworkKind,
    val score: Int,
    val isUp: Boolean
)
