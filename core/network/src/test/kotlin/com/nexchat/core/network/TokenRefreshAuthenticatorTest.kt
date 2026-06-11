package com.nexchat.core.network

import com.google.common.truth.Truth.assertThat
import com.nexchat.core.auth.LogoutEventBus
import com.nexchat.core.auth.TokenStorage
import com.nexchat.core.common.AppDispatchers
import com.nexchat.core.network.interceptor.TokenRefreshAuthenticator
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TokenRefreshAuthenticatorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tokenStorage: TokenStorage
    private lateinit var logoutBus: LogoutEventBus
    private lateinit var dispatchers: AppDispatchers
    private lateinit var okHttpClient: OkHttpClient
    
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        tokenStorage = mockk(relaxed = true)
        logoutBus = mockk(relaxed = true)
        dispatchers = mockk()
        
        every { dispatchers.io } returns testDispatcher
        every { dispatchers.main } returns testDispatcher
        every { dispatchers.default } returns testDispatcher

        val authenticator = TokenRefreshAuthenticator(tokenStorage, logoutBus, dispatchers)
        okHttpClient = OkHttpClient.Builder()
            .authenticator(authenticator)
            .build()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    // Since TokenRefreshAuthenticator hardcodes the API_BASE_URL internally via a direct OkHttp call,
    // we cannot easily test the *outgoing* refresh request to MockWebServer without injecting the base URL.
    // However, we CAN test the scenario where a 401 triggers it, and the hardcoded OkHttp call fails (resulting in logout).
    
    @Test
    fun testRefreshFail_EmitsLogout() = runTest {
        // Arrange
        every { tokenStorage.getAccessToken() } returns "old_token"
        every { tokenStorage.getRefreshToken() } returns "old_refresh"
        
        // This will simulate the initial 401
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val request = Request.Builder()
            .url(mockWebServer.url("/protected"))
            .header("Authorization", "Bearer old_token")
            .build()

        // Act
        // Because the authenticator creates a NEW OkHttpClient() with the hardcoded base url,
        // it will attempt to hit https://codecloudex.dpdns.org/auth/refresh and fail because 
        // we are in a unit test sandbox, ultimately triggering a logout emission.
        okHttpClient.newCall(request).execute()

        // Assert
        coVerify(exactly = 1) { logoutBus.triggerLogout() }
    }
}
