package com.nexchat.core.auth

import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class TokenStorageTest {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var tokenStorage: TokenStorage

    @Before
    fun setup() {
        sharedPreferences = mockk()
        editor = mockk(relaxed = true)
        
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.clear() } returns editor
        
        tokenStorage = TokenStorage(sharedPreferences)
    }

    @Test
    fun `TestTokenStorage_saveAndGet_AccessToken`() {
        every { sharedPreferences.getString("access_token", null) } returns "mock_access_token"
        every { sharedPreferences.getString("refresh_token", null) } returns "mock_refresh_token"

        tokenStorage.saveTokens("mock_access_token", "mock_refresh_token")

        verify {
            editor.putString("access_token", "mock_access_token")
            editor.putString("refresh_token", "mock_refresh_token")
            editor.apply()
        }

        assertThat(tokenStorage.getAccessToken()).isEqualTo("mock_access_token")
        assertThat(tokenStorage.getRefreshToken()).isEqualTo("mock_refresh_token")
    }

    @Test
    fun `TestTokenStorage_clearAll_allNull`() {
        every { sharedPreferences.getString(any(), null) } returns null

        tokenStorage.clear()

        verify {
            editor.clear()
            editor.apply()
        }

        assertThat(tokenStorage.getAccessToken()).isNull()
        assertThat(tokenStorage.getUserId()).isNull()
    }
}
