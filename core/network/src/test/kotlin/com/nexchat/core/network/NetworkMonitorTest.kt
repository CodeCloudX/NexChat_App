package com.nexchat.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.google.common.truth.Truth.assertThat
import com.nexchat.core.network.websocket.WebSocketManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test

class NetworkMonitorTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wsManager: WebSocketManager
    private lateinit var networkMonitor: NetworkMonitor

    private lateinit var capturedCallback: ConnectivityManager.NetworkCallback

    @Before
    fun setup() {
        val mockBuilder = mockk<NetworkRequest.Builder>()
        every { mockBuilder.addCapability(any()) } returns mockBuilder
        every { mockBuilder.build() } returns mockk()

        mockkConstructor(NetworkRequest.Builder::class)
        every { anyConstructed<NetworkRequest.Builder>().addCapability(any()) } returns mockBuilder
        every { anyConstructed<NetworkRequest.Builder>().build() } returns mockk()

        context = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)
        wsManager = mockk(relaxed = true)

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        
        // Capture the network callback registered in the init block
        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every { 
            connectivityManager.registerNetworkCallback(any<NetworkRequest>(), capture(callbackSlot)) 
        } answers {
            capturedCallback = callbackSlot.captured
        }

        every { connectivityManager.activeNetwork } returns null

        networkMonitor = NetworkMonitor(context, wsManager)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun testWifiToCellular_CallsReconnectImmediate() {
        // Arrange
        val networkMock = mockk<Network>()
        val wifiCaps = mockk<NetworkCapabilities> {
            every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
            every { hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        }
        val cellCaps = mockk<NetworkCapabilities> {
            every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
            every { hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        }

        // Act 1: Connect to WiFi
        capturedCallback.onAvailable(networkMock)
        capturedCallback.onCapabilitiesChanged(networkMock, wifiCaps)

        // Assert 1
        assertThat(networkMonitor.isWifi()).isTrue()
        assertThat(networkMonitor.networkState.value).isEqualTo(NetworkState.ONLINE)

        // Act 2: Gemini Handover -> Switch to Cellular
        capturedCallback.onCapabilitiesChanged(networkMock, cellCaps)

        // Assert 2
        assertThat(networkMonitor.isWifi()).isFalse()
        // verify reconnectImmediate() was called exactly once during the transition
        verify(exactly = 1) { wsManager.reconnectImmediate() }
    }

    @Test
    fun testSameTransport_NoReconnect() {
        // Arrange
        val networkMock = mockk<Network>()
        val wifiCaps = mockk<NetworkCapabilities> {
            every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
            every { hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        }

        // Act 1: Initial WiFi connect
        capturedCallback.onAvailable(networkMock)
        capturedCallback.onCapabilitiesChanged(networkMock, wifiCaps)

        // Act 2: Capabilities change but it's STILL WiFi (e.g. signal strength changed)
        capturedCallback.onCapabilitiesChanged(networkMock, wifiCaps)

        // Assert
        // We do NOT want to drop the WebSocket if the transport is still WiFi
        verify(exactly = 0) { wsManager.reconnectImmediate() }
    }
}
