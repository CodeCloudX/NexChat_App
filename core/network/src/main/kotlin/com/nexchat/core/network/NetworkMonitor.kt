package com.nexchat.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.nexchat.core.network.websocket.WebSocketManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkState {
    ONLINE,
    OFFLINE
}

enum class Transport {
    WIFI,
    CELLULAR,
    OTHER
}

@Singleton
class NetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val wsManager: WebSocketManager
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkState = MutableStateFlow(NetworkState.OFFLINE)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val _lastTransport = MutableStateFlow<Transport?>(null)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        
        override fun onAvailable(network: Network) {
            Timber.tag("NetworkMonitor").d("Network Available")
            _networkState.value = NetworkState.ONLINE
            
            // Reconnect the socket if it was dead while offline
            wsManager.reconnect()
        }

        override fun onLost(network: Network) {
            Timber.tag("NetworkMonitor").d("Network Lost")
            _networkState.value = NetworkState.OFFLINE
            _lastTransport.value = null
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val newTransport = when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
                else -> Transport.OTHER
            }

            val currentTransport = _lastTransport.value

            // The critical "Gemini Handover Fix".
            // onCapabilitiesChanged fires BEFORE onLost when switching from WiFi to Cellular.
            // By detecting the transport change here, we force an immediate WebSocket reconnect,
            // avoiding the deadly 30-90s TCP timeout stall on Android.
            if (currentTransport != null && currentTransport != newTransport) {
                Timber.tag("NetworkMonitor").d("Transport changed from $currentTransport to $newTransport. Forcing instant WebSocket reconnect.")
                wsManager.reconnectImmediate()
            }

            _lastTransport.value = newTransport
            _networkState.value = NetworkState.ONLINE
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Set initial state
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork != null) {
            _networkState.value = NetworkState.ONLINE
        }
    }

    fun isWifi(): Boolean {
        return _lastTransport.value == Transport.WIFI
    }
}
