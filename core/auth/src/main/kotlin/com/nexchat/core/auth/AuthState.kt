package com.nexchat.core.auth

sealed class AuthState {
    object Loading : AuthState()
    object LoggedOut : AuthState()
    data class LoggedIn(val userId: String, val deviceId: String) : AuthState()
}
