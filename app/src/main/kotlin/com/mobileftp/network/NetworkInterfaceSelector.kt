package com.mobileftp.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import com.mobileftp.domain.model.NetworkInterfaceInfo
import com.mobileftp.util.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * P9: Network interface scoring & failover.
 * - Enumerates all up interfaces, scores by kind.
 * - Auto-binds to highest-scoring interface.
 * - Notifies callers when interface changes via Flow.
 */
class NetworkInterfaceSelector(private val context: Context) {

    private val cm: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _interfaces = MutableStateFlow<List<NetworkInterfaceInfo>>(emptyList())
    val interfaces: StateFlow<List<NetworkInterfaceInfo>> = _interfaces.asStateFlow()

    private val _selected = MutableStateFlow<NetworkInterfaceInfo?>(null)
    val selected: StateFlow<NetworkInterfaceInfo?> = _selected.asStateFlow()

    private var listenerCallback: ConnectivityManager.NetworkCallback? = null

    fun refresh() {
        val ifaces = NetworkUtils.enumerateInterfaces()
        _interfaces.value = ifaces
        _selected.value = ifaces.firstOrNull { it.isUp && it.ipv4 != null && it.score > 0 }
    }

    fun start(onChange: (NetworkInterfaceInfo?) -> Unit) {
        refresh()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { refresh(); onChange(_selected.value) }
            override fun onLost(network: Network) { refresh(); onChange(_selected.value) }
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: android.net.NetworkCapabilities
            ) { refresh(); onChange(_selected.value) }
        }
        listenerCallback = cb
        val request = NetworkRequest.Builder().build()
        runCatching { cm.registerNetworkCallback(request, cb) }
    }

    fun stop() {
        listenerCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        listenerCallback = null
    }
}
