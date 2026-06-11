package com.nexchat.feature.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexchat.core.common.Resource
import com.nexchat.feature.auth.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class MagicLinkSent(val email: String, val cooldownSecs: Int = 60) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

sealed class AuthSideEffect {
    object NavigateHome : AuthSideEffect()
    object NavigateProfileSetup : AuthSideEffect()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _sideEffect = MutableSharedFlow<AuthSideEffect>(replay = 0, extraBufferCapacity = 1)
    val sideEffect: SharedFlow<AuthSideEffect> = _sideEffect.asSharedFlow()

    private var cooldownJob: Job? = null

    fun onSendMagicLink(email: String) {
        if (!email.contains("@") || email.substringAfter("@").isEmpty()) {
            _uiState.value = AuthUiState.Error("Enter a valid email address")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = repo.sendMagicLink(email)) {
                is Resource.Success -> {
                    _uiState.value = AuthUiState.MagicLinkSent(email)
                    startCooldownTimer(email)
                }
                is Resource.Error -> {
                    _uiState.value = AuthUiState.Error(result.message ?: "Failed to send link")
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun onVerifyToken(token: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = repo.verifyMagicLink(token)) {
                is Resource.Success -> emitNavigation(result.data.isNewUser)
                is Resource.Error -> _uiState.value = AuthUiState.Error(result.message ?: "Verification failed")
                is Resource.Loading -> Unit
            }
        }
    }

    fun onGoogleSignIn(firebaseIdToken: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = repo.googleAuth(firebaseIdToken)) {
                is Resource.Success -> emitNavigation(result.data.isNewUser)
                is Resource.Error -> _uiState.value = AuthUiState.Error(result.message ?: "Google sign-in failed")
                is Resource.Loading -> Unit
            }
        }
    }

    fun onPhoneAuth(firebaseIdToken: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = repo.phoneAuth(firebaseIdToken)) {
                is Resource.Success -> emitNavigation(result.data.isNewUser)
                is Resource.Error -> _uiState.value = AuthUiState.Error(result.message ?: "Phone auth failed")
                is Resource.Loading -> Unit
            }
        }
    }

    fun resetToIdle() {
        cooldownJob?.cancel()
        _uiState.value = AuthUiState.Idle
    }

    private fun startCooldownTimer(email: String) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (remaining in 60 downTo 0) {
                _uiState.value = AuthUiState.MagicLinkSent(email, remaining)
                if (remaining > 0) delay(1_000)
            }
        }
    }

    private fun emitNavigation(isNewUser: Boolean) {
        val effect = if (isNewUser) AuthSideEffect.NavigateProfileSetup else AuthSideEffect.NavigateHome
        viewModelScope.launch { _sideEffect.emit(effect) }
    }

    override fun onCleared() {
        cooldownJob?.cancel()
        super.onCleared()
    }
}
