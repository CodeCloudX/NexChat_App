package com.nexchat.feature.auth.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.nexchat.feature.auth.viewmodel.AuthSideEffect
import com.nexchat.feature.auth.viewmodel.AuthUiState
import com.nexchat.feature.auth.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpScreen(
    verificationId: String,
    onNavigateHome: () -> Unit,
    onNavigateProfileSetup: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val digits = rememberSaveable { mutableStateOf(List(6) { "" }) }
    val focusRequesters = remember { List(6) { FocusRequester() } }
    var resendCooldown by remember { mutableStateOf(60) }

    LaunchedEffect(Unit) {
        viewModel.sideEffect.collect { effect ->
            when (effect) {
                is AuthSideEffect.NavigateHome -> onNavigateHome()
                is AuthSideEffect.NavigateProfileSetup -> onNavigateProfileSetup()
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequesters[0].requestFocus()
        while (resendCooldown > 0) {
            delay(1_000)
            resendCooldown--
        }
    }

    val errorMessage = (uiState as? AuthUiState.Error)?.message
    val hasError = errorMessage != null

    Scaffold(
        topBar = { TopAppBar(title = { Text("Enter verification code") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Enter the 6-digit code we sent to your phone",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                digits.value.forEachIndexed { index, digit ->
                    val borderColor = when {
                        hasError -> MaterialTheme.colorScheme.error
                        digit.isNotEmpty() -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    BasicTextField(
                        value = digit,
                        onValueChange = { input ->
                            when {
                                input.length > 1 -> {
                                    val pasted = input.filter { it.isDigit() }.take(6)
                                    digits.value = List(6) { i -> pasted.getOrNull(i)?.toString() ?: "" }
                                    val nextFocus = (pasted.length - 1).coerceAtMost(5)
                                    focusRequesters[nextFocus].requestFocus()
                                    if (pasted.length == 6) submitOtp(pasted, verificationId, viewModel)
                                }
                                input.length == 1 && input[0].isDigit() -> {
                                    digits.value = digits.value.toMutableList().also { it[index] = input }
                                    if (index < 5) focusRequesters[index + 1].requestFocus()
                                    val all = digits.value.joinToString("")
                                    if (all.length == 6) submitOtp(all, verificationId, viewModel)
                                }
                                input.isEmpty() -> {
                                    digits.value = digits.value.toMutableList().also { it[index] = "" }
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .size(48.dp)
                            .border(
                                width = if (hasError) 2.dp else 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .focusRequester(focusRequesters[index]),
                        decorationBox = { inner ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    digit,
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center,
                                    color = if (hasError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                inner()
                            }
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = hasError,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
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

            Spacer(Modifier.height(32.dp))

            TextButton(
                onClick = {
                    resendCooldown = 60
                },
                enabled = resendCooldown == 0
            ) {
                Text(if (resendCooldown > 0) "Resend code in ${resendCooldown}s" else "Resend code")
            }
        }
    }
}

private fun submitOtp(code: String, verificationId: String, viewModel: AuthViewModel) {
    val credential = PhoneAuthProvider.getCredential(verificationId, code)
    credential.smsCode?.let { viewModel.onPhoneAuth(it) }
        ?: run {
            FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener { result ->
                    result.user?.getIdToken(false)?.addOnSuccessListener { tokenResult ->
                        tokenResult.token?.let { viewModel.onPhoneAuth(it) }
                    }
                }
        }
}
