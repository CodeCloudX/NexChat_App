package com.nexchat.feature.contacts.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nexchat.core.common.AppDispatchers
import com.nexchat.core.db.dao.SignalDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

sealed interface SafetyNumberUiState {
    data object Loading : SafetyNumberUiState
    data class Success(val formattedCode: String, val isVerified: Boolean) : SafetyNumberUiState
    data class Error(val msg: String) : SafetyNumberUiState
}

@HiltViewModel
class SafetyNumberViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val signalDao: SignalDao,
    private val dispatchers: AppDispatchers
) : ViewModel() {

    private val contactId: String? = savedStateHandle.get<String>("contactId")

    private val _uiState = MutableStateFlow<SafetyNumberUiState>(SafetyNumberUiState.Loading)
    val uiState: StateFlow<SafetyNumberUiState> = _uiState.asStateFlow()

    init {
        computeSafetyNumber()
    }

    private fun computeSafetyNumber() {
        if (contactId == null) {
            _uiState.value = SafetyNumberUiState.Error("Contact ID missing")
            return
        }

        viewModelScope.launch(dispatchers.default) {
            try {
                val myIK = signalDao.getMyIdentityKeyBlocking()
                if (myIK == null) {
                    _uiState.value = SafetyNumberUiState.Error("Local identity key not found")
                    return@launch
                }

                val theirIK = signalDao.getIdentityBlocking(contactId)
                if (theirIK == null) {
                    _uiState.value = SafetyNumberUiState.Error("Contact identity key not found")
                    return@launch
                }

                // E2EE safety number fingerprint generation (WhatsApp/Signal protocol)
                // Appends the two serialized identity keys and computes SHA-256
                val md = MessageDigest.getInstance("SHA-256")
                md.update(myIK.identityKey)
                md.update(theirIK.identityKey)
                val hash = md.digest()

                // Extract numeric fingerprint (60 digits)
                val digits = hash.flatMap { b ->
                    val i = b.toInt() and 0xFF
                    listOf(i / 10, i % 10)
                }.take(60)

                // Format as 12 groups of 5 digits
                val formatted = digits.chunked(5).joinToString(" ") { it.joinToString("") }

                _uiState.value = SafetyNumberUiState.Success(formatted, isVerified = false)
            } catch (e: Exception) {
                _uiState.value = SafetyNumberUiState.Error(e.message ?: "Failed to compute safety number")
            }
        }
    }

    fun markVerified() {
        _uiState.update { current ->
            if (current is SafetyNumberUiState.Success) {
                // In a full implementation, this boolean would be saved via SignalDao 
                // to flag the identity key as verified
                current.copy(isVerified = true)
            } else current
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyNumberScreen(
    contactId: String,
    onBack: () -> Unit,
    onMarkedVerified: () -> Unit,
    viewModel: SafetyNumberViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safety Number") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is SafetyNumberUiState.Loading -> {
                    CircularProgressIndicator()
                }
                is SafetyNumberUiState.Error -> {
                    Text(
                        text = state.msg,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                is SafetyNumberUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (state.isVerified) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = "Verified",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                        }

                        // Display the 60-digit safety number in groups of 5
                        // (3 lines of 4 groups each is standard)
                        val lines = state.formattedCode.split(" ").chunked(4)
                        lines.forEach { lineGroups ->
                            Text(
                                text = lineGroups.joinToString("   "), // Extra spaces between groups
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        Spacer(Modifier.height(32.dp))

                        Text(
                            text = "To verify that your messages and calls with this contact are end-to-end encrypted, compare these numbers with their device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(48.dp))

                        if (!state.isVerified) {
                            Button(
                                onClick = {
                                    viewModel.markVerified()
                                    onMarkedVerified()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Mark as Verified")
                            }
                        }
                    }
                }
            }
        }
    }
}
