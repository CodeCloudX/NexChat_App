package com.nexchat.core.network

import com.google.common.truth.Truth.assertThat
import com.nexchat.core.auth.TokenStorage
import com.nexchat.core.network.interceptor.AuthInterceptor
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tokenStorage: TokenStorage
    private lateinit var okHttpClient: OkHttpClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        tokenStorage = mockk()
        
        val interceptor = AuthInterceptor(tokenStorage)
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testAddsAuthHeader_WhenTokenPresent() {
        // Arrange
        every { tokenStorage.getAccessToken() } returns "valid_token_123"
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()

        // Act
        okHttpClient.newCall(request).execute()

        // Assert
        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer valid_token_123")
        assertThat(recordedRequest.getHeader("X-Android-ID")).isNotNull()
    }

    @Test
    fun testNoHeader_WhenNoToken() {
        // Arrange
        every { tokenStorage.getAccessToken() } returns null
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()

        // Act
        okHttpClient.newCall(request).execute()

        // Assert
        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.getHeader("Authorization")).isNull()
    }
}
