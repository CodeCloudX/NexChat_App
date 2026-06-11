package com.nexchat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexchat.core.auth.AuthManager
import com.nexchat.core.auth.AuthState
import com.nexchat.feature.onboarding.viewmodel.E2EE_SHOWN_KEY
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    dataStore: DataStore<Preferences>,
    authManager: AuthManager
) : ViewModel() {

    val authState: StateFlow<AuthState> = authManager.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)

    val e2eeShown: StateFlow<Boolean> = dataStore.data
        .map { it[E2EE_SHOWN_KEY] == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
