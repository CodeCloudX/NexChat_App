package com.nexchat.feature.auth.ui

import android.content.Context
import android.telephony.TelephonyManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.nexchat.feature.auth.viewmodel.AuthUiState
import com.nexchat.feature.auth.viewmodel.AuthViewModel
import java.util.concurrent.TimeUnit

private data class CountryCode(val name: String, val dialCode: String, val iso: String)

private val COUNTRY_CODES = listOf(
    CountryCode("India", "+91", "IN"),
    CountryCode("United States", "+1", "US"),
    CountryCode("United Kingdom", "+44", "GB"),
    CountryCode("Australia", "+61", "AU"),
    CountryCode("Canada", "+1", "CA"),
    CountryCode("Germany", "+49", "DE"),
    CountryCode("France", "+33", "FR"),
    CountryCode("Brazil", "+55", "BR"),
    CountryCode("Japan", "+81", "JP"),
    CountryCode("China", "+86", "CN"),
    CountryCode("Singapore", "+65", "SG"),
    CountryCode("UAE", "+971", "AE"),
    CountryCode("Pakistan", "+92", "PK"),
    CountryCode("Bangladesh", "+880", "BD"),
    CountryCode("Nigeria", "+234", "NG"),
    CountryCode("South Africa", "+27", "ZA"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneScreen(
    onNavigateOtp: (verificationId: String) -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    val simCountryIso = remember {
        (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)
            ?.simCountryIso?.uppercase() ?: "US"
    }
    var selectedCountry by remember {
        mutableStateOf(COUNTRY_CODES.firstOrNull { it.iso == simCountryIso } ?: COUNTRY_CODES[1])
    }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var firebaseError by remember { mutableStateOf<String?>(null) }

    // Clear firebase error when user edits the phone number
    LaunchedEffect(phoneNumber) { firebaseError = null }

    val errorMessage = when {
        uiState is AuthUiState.Error -> (uiState as AuthUiState.Error).message
        firebaseError != null -> firebaseError
        else -> null
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Enter phone number") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = selectedCountry.dialCode,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Code") },
                    isError = errorMessage != null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .width(100.dp)
                        .clickable { showPicker = true }
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { if (it.length <= 15) phoneNumber = it },
                    label = { Text("Phone number") },
                    isError = errorMessage != null,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

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

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    firebaseError = null
                    val fullNumber = "${selectedCountry.dialCode}$phoneNumber"
                    val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                        .setPhoneNumber(fullNumber)
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(context as android.app.Activity)
                        .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                credential.smsCode?.let { viewModel.onPhoneAuth(it) }
                            }
                            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                                viewModel.onPhoneAuth("")
                                firebaseError = e.message ?: "Verification failed"
                            }
                            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                                onNavigateOtp(verificationId)
                            }
                        })
                        .build()
                    PhoneAuthProvider.verifyPhoneNumber(options)
                },
                enabled = phoneNumber.length >= 10 && uiState !is AuthUiState.Loading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
        }

        if (showPicker) {
            ModalBottomSheet(
                onDismissRequest = { showPicker = false },
                sheetState = sheetState
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search country") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
                val filtered = COUNTRY_CODES.filter {
                    it.name.contains(search, ignoreCase = true) || it.dialCode.contains(search)
                }
                LazyColumn {
                    items(filtered, key = { it.iso }) { country ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCountry = country
                                    showPicker = false
                                    search = ""
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text("${country.name}  ${country.dialCode}", modifier = Modifier.weight(1f))
                        }
                        HorizontalDivider()
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
