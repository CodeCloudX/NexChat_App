package com.nexchat.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    private val tokenStorage: TokenStorage
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkInitialAuth()
    }

    private fun checkInitialAuth() {
        val token = tokenStorage.getAccessToken()
        val userId = tokenStorage.getUserId()
        val deviceId = tokenStorage.getDeviceId()

        if (!token.isNullOrEmpty() && !userId.isNullOrEmpty() && !deviceId.isNullOrEmpty()) {
            _authState.value = AuthState.LoggedIn(userId, deviceId)
        } else {
            _authState.value = AuthState.LoggedOut
        }
    }

    fun login(userId: String, deviceId: String, accessToken: String, refreshToken: String) {
        tokenStorage.saveTokens(accessToken, refreshToken)
        tokenStorage.saveIdentity(userId, deviceId)
        _authState.value = AuthState.LoggedIn(userId, deviceId)
    }

    fun logout() {
        tokenStorage.clear()
        _authState.value = AuthState.LoggedOut
    }
}
