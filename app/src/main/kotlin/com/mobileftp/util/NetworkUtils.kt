package com.mobileftp.util

import android.content.Context
import com.mobileftp.domain.model.NetworkInterfaceInfo
import com.mobileftp.domain.model.NetworkKind
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Locale
import java.util.concurrent.TimeUnit

object NetworkUtils {

    private val publicIpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8L, TimeUnit.SECONDS)
            .readTimeout(8L, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Best-effort LAN IPv4 — picks the highest-scoring up interface from the
     * P9 enumeration. Avoids the deprecated `WifiManager.connectionInfo` path
     * entirely, which keeps the build warning-free on API 31+.
     *
     * The [context] parameter is unused today but kept in the signature for
     * API symmetry with the rest of the network helpers.
     */
    fun primaryLanIpv4(@Suppress("UNUSED_PARAMETER") context: Context): String? =
        enumerateInterfaces()
            .firstOrNull { it.ipv4 != null && it.isUp && it.score > 0 }
            ?.ipv4

    fun fetchPublicIpv4(): String? = try {
        val req = Request.Builder().url("https://api.ipify.org").get().build()
        publicIpClient.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string()?.trim() else null
        }
    } catch (e: Exception) {
        null
    }

    fun enumerateInterfaces(): List<NetworkInterfaceInfo> {
        val list = mutableListOf<NetworkInterfaceInfo>()
        val ifaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        for (iface in ifaces) {
            if (iface.isLoopback || !iface.isUp) {
                if (!iface.isUp) {
                    list += iface.toInfo(false)
                }
                continue
            }
            list += iface.toInfo(true)
        }
        return list.sortedByDescending { it.score }
    }

    private fun NetworkInterface.toInfo(isUp: Boolean): NetworkInterfaceInfo {
        val addresses = inetAddresses.toList()
        val v4 = addresses.filterIsInstance<Inet4Address>().firstOrNull { !it.isLinkLocalAddress }
        val v6 = addresses.filterIsInstance<Inet6Address>().firstOrNull { !it.isLinkLocalAddress }
        val kind = classify(name, v4)
        return NetworkInterfaceInfo(
            name = name,
            displayName = displayName ?: name,
            ipv4 = v4?.hostAddress,
            ipv6 = v6?.hostAddress,
            kind = kind,
            score = scoreOf(kind),
            isUp = isUp
        )
    }

    private fun classify(name: String, ipv4: InetAddress?): NetworkKind {
        val n = name.lowercase(Locale.US)
        val host = ipv4?.hostAddress ?: ""
        return when {
            n.startsWith("p2p") || n.contains("wfd") -> NetworkKind.WIFI_DIRECT
            n.startsWith("wlan") -> {
                if (host.startsWith("192.168.43.") || host.startsWith("172.20.10.")) NetworkKind.HOTSPOT
                else NetworkKind.WIFI_5GHZ
            }
            n.startsWith("eth") || n.contains("usb") -> NetworkKind.USB_ETHERNET
            n.startsWith("ap") || n.startsWith("swlan") -> NetworkKind.HOTSPOT
            n.startsWith("rmnet") || n.startsWith("ccmni") || n.startsWith("pdp") -> NetworkKind.CELLULAR
            else -> NetworkKind.UNKNOWN
        }
    }

    fun scoreOf(kind: NetworkKind): Int = when (kind) {
        NetworkKind.WIFI_DIRECT -> 100
        NetworkKind.WIFI_5GHZ -> 80
        NetworkKind.USB_ETHERNET -> 70
        NetworkKind.WIFI_24GHZ -> 60
        NetworkKind.HOTSPOT -> 50
        NetworkKind.CELLULAR -> 20
        NetworkKind.UNKNOWN -> 10
    }
}
