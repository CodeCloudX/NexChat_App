package com.nexchat.feature.auth.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.res.painterResource
import com.nexchat.feature.auth.R
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.nexchat.feature.auth.BuildConfig
import com.nexchat.feature.auth.viewmodel.AuthSideEffect
import com.nexchat.feature.auth.viewmodel.AuthUiState
import com.nexchat.feature.auth.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    navController: NavController,
    onNavigatePhone: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateProfileSetup: () -> Unit,
    deepLinkToken: String? = null,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var googleError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(deepLinkToken) {
        if (!deepLinkToken.isNullOrBlank()) viewModel.onVerifyToken(deepLinkToken)
    }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is AuthSideEffect.NavigateHome -> onNavigateHome()
                is AuthSideEffect.NavigateProfileSetup -> onNavigateProfileSetup()
            }
        }
    }

    Scaffold { padding ->
        AnimatedContent(targetState = uiState, label = "auth_content") { state ->
            when (state) {
                is AuthUiState.MagicLinkSent -> MagicLinkSentContent(
                    email = state.email,
                    cooldownSecs = state.cooldownSecs,
                    onResend = { viewModel.onSendMagicLink(state.email) },
                    onUseDifferent = { viewModel.resetToIdle() },
                    modifier = Modifier.padding(padding)
                )
                else -> EmailInputContent(
                    isLoading = state is AuthUiState.Loading,
                    errorMessage = when {
                        state is AuthUiState.Error -> state.message
                        googleError != null -> googleError
                        else -> null
                    },
                    onSendMagicLink = { email ->
                        googleError = null
                        keyboardController?.hide()
                        viewModel.onSendMagicLink(email)
                    },
                    onGoogleSignIn = {
                        googleError = null
                        scope.launch {
                            val credentialManager = CredentialManager.create(context)
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(
                                    GetGoogleIdOption.Builder()
                                        .setFilterByAuthorizedAccounts(false)
                                        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                                        .build()
                                )
                                .build()
                            runCatching {
                                val result = credentialManager.getCredential(context as Activity, request)
                                val idToken = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
                                
                                // Exchange the Google Token for a Firebase session
                                val credential = GoogleAuthProvider.getCredential(idToken, null)
                                val authResult = FirebaseAuth.getInstance().signInWithCredential(credential).await()
                                
                                // Extract the true Firebase ID token
                                val firebaseToken = authResult.user?.getIdToken(false)?.await()?.token
                                    ?: throw Exception("Failed to retrieve Firebase ID token")

                                viewModel.onGoogleSignIn(firebaseToken)
                            }.onFailure { e ->
                                googleError = when {
                                    e is java.net.UnknownHostException ||
                                    e is java.io.IOException ->
                                        "No internet connection — check your network"
                                    e.message?.contains("cancel", ignoreCase = true) == true ->
                                        "Sign-in cancelled"
                                    else -> "Google sign-in failed — try again"
                                }
                            }
                        }
                    },
                    onNavigatePhone = onNavigatePhone,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun EmailInputContent(
    isLoading: Boolean,
    errorMessage: String?,
    onSendMagicLink: (String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onNavigatePhone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by rememberSaveable { mutableStateOf("") }
    val isValidEmail = email.contains("@") && email.substringAfter("@").isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Email,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text("NexChat", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in or create an account",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Email address") },
            singleLine = true,
            isError = errorMessage != null,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (isValidEmail) onSendMagicLink(email)
            }),
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onSendMagicLink(email) },
            enabled = isValidEmail && !isLoading,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Login Link")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = onGoogleSignIn,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = androidx.compose.ui.graphics.Color.Unspecified
            )
            Spacer(Modifier.width(12.dp))
            Text("Continue with Google")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onNavigatePhone,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text("Continue with Phone")
        }
    }
}
