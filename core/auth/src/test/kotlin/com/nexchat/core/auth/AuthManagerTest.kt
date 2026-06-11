package com.nexchat.core.auth

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class AuthManagerTest {

    private lateinit var tokenStorage: TokenStorage
    private lateinit var authManager: AuthManager

    @Before
    fun setup() {
        tokenStorage = mockk(relaxed = true)
        // Assume logged out initially
        every { tokenStorage.getAccessToken() } returns null
        every { tokenStorage.getUserId() } returns null
        every { tokenStorage.getDeviceId() } returns null
        
        authManager = AuthManager(tokenStorage)
    }

    @Test
    fun `TestAuthManager_isLoggedIn_trueAfterLogin`() {
        assertThat(authManager.authState.value).isInstanceOf(AuthState.LoggedOut::class.java)

        authManager.login("user123", "device456", "access_tok", "refresh_tok")

        verify { tokenStorage.saveTokens("access_tok", "refresh_tok") }
        verify { tokenStorage.saveIdentity("user123", "device456") }

        val state = authManager.authState.value
        assertThat(state).isInstanceOf(AuthState.LoggedIn::class.java)
        
        if (state is AuthState.LoggedIn) {
            assertThat(state.userId).isEqualTo("user123")
            assertThat(state.deviceId).isEqualTo("device456")
        }
    }
}
