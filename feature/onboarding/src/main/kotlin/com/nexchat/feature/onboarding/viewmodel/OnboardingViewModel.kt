package com.nexchat.feature.onboarding.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexchat.core.network.api.KeysApi
import com.nexchat.core.network.api.UserApi
import com.nexchat.core.network.dto.PreKeyDto
import com.nexchat.core.network.dto.SignedPreKeyDto
import com.nexchat.core.network.dto.UpdateProfileRequest
import com.nexchat.core.network.dto.UploadPreKeysRequest
import com.nexchat.core.security.E2EEManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import timber.log.Timber
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import android.graphics.Bitmap

sealed class OnboardingState {
    object Idle : OnboardingState()
    object SavingProfile : OnboardingState()
    object UploadingKeys : OnboardingState()
    object Success : OnboardingState()
    data class Error(val message: String) : OnboardingState()
}

val E2EE_SHOWN_KEY = booleanPreferencesKey("e2ee_info_shown")

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userApi: UserApi,
    private val keysApi: KeysApi,
    private val e2eeManager: E2EEManager,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _state = MutableStateFlow<OnboardingState>(OnboardingState.Idle)
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun saveProfile(name: String, avatarUri: Uri?) {
        if (name.isBlank()) {
            _state.value = OnboardingState.Error("Name cannot be empty")
            return
        }
        if (name.length > 25) {
            _state.value = OnboardingState.Error("Name must be 25 characters or less")
            return
        }
        viewModelScope.launch {
            try {
                _state.value = OnboardingState.SavingProfile

                if (avatarUri != null) {
                    val tempFile = uriToTempFile(context, avatarUri)
                    val compressed = Compressor.compress(context, tempFile) {
                        resolution(400, 400)
                        quality(85)
                        format(Bitmap.CompressFormat.JPEG)
                    }
                    val part = MultipartBody.Part.createFormData(
                        "avatar",
                        "avatar.jpg",
                        compressed.asRequestBody("image/jpeg".toMediaType())
                    )
                    userApi.updateAvatar(part)
                    tempFile.delete()
                }

                userApi.updateProfile(UpdateProfileRequest(displayName = name))

                _state.value = OnboardingState.UploadingKeys
                uploadE2EEKeys()
                _state.value = OnboardingState.Success
            } catch (e: Exception) {
                Timber.e(e, "saveProfile failed")
                _state.value = OnboardingState.Error(e.message ?: "Profile setup failed")
            }
        }
    }

    fun onE2EEInfoSeen() {
        viewModelScope.launch {
            dataStore.edit { it[E2EE_SHOWN_KEY] = true }
        }
    }

    private suspend fun uploadE2EEKeys() {
        val ikPair = e2eeManager.generateIdentityKeyPair().getOrThrow()
        val spk = e2eeManager.generateSignedPreKey(keyId = 1).getOrThrow()
        val otks = e2eeManager.generateOneTimePreKeys(startId = 1, count = 50).getOrThrow()

        val request = UploadPreKeysRequest(
            type = "initial",
            identityKey = Base64.encodeToString(ikPair.publicKey.serialize(), Base64.NO_WRAP),
            signedPreKey = SignedPreKeyDto(
                keyId = spk.id,
                publicKey = Base64.encodeToString(spk.keyPair.publicKey.serialize(), Base64.NO_WRAP),
                signature = Base64.encodeToString(spk.signature, Base64.NO_WRAP)
            ),
            oneTimePrekeys = otks.map { otk ->
                PreKeyDto(
                    keyId = otk.id,
                    publicKey = Base64.encodeToString(otk.keyPair.publicKey.serialize(), Base64.NO_WRAP)
                )
            }
        )

        val response = keysApi.uploadPreKeys(request)
        if (!response.isSuccessful) {
            throw Exception("Key upload failed: ${response.code()}")
        }
    }

    private fun uriToTempFile(context: Context, uri: Uri): File {
        val inputStream: InputStream = requireNotNull(context.contentResolver.openInputStream(uri)) {
            "Cannot open URI: $uri"
        }
        val temp = File.createTempFile("avatar_", ".jpg", context.cacheDir)
        temp.outputStream().use { inputStream.copyTo(it) }
        return temp
    }
}
