package com.nexchat.core.work

import android.content.Context
import android.util.Base64
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexchat.core.network.api.KeysApi
import com.nexchat.core.network.dto.PreKeyDto
import com.nexchat.core.network.dto.SignedPreKeyDto
import com.nexchat.core.network.dto.UploadPreKeysRequest
import com.nexchat.core.security.E2EEManager
import com.nexchat.core.security.PreKeyStoreDelegate
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class OTKReplenishWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val e2eeManager: E2EEManager,
    private val preKeyStoreDelegate: PreKeyStoreDelegate,
    private val keysApi: KeysApi
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("OTKReplenishWorker started")
        return try {
            val maxOtkId = preKeyStoreDelegate.getMaxPreKeyId()
            val newOtks = e2eeManager.generateOneTimePreKeys(maxOtkId + 1, 50).getOrThrow()

            // Fetch persisted keys — do NOT regenerate them.
            val identityKeyPair = e2eeManager.getIdentityKeyPair().getOrThrow()
            val identityKeyBase64 = Base64.encodeToString(
                identityKeyPair.publicKey.serialize(), Base64.NO_WRAP
            )

            val maxSpkId = preKeyStoreDelegate.getMaxSignedPreKeyId()
            val currentSpkRecord = e2eeManager.getSignedPreKey(maxSpkId).getOrThrow()

            val request = UploadPreKeysRequest(
                type = "one_time",
                identityKey = identityKeyBase64,
                signedPreKey = SignedPreKeyDto(
                    keyId = currentSpkRecord.id,
                    publicKey = Base64.encodeToString(
                        currentSpkRecord.keyPair.publicKey.serialize(), Base64.NO_WRAP
                    ),
                    signature = Base64.encodeToString(currentSpkRecord.signature, Base64.NO_WRAP)
                ),
                oneTimePrekeys = newOtks.map {
                    PreKeyDto(
                        keyId = it.id,
                        publicKey = Base64.encodeToString(it.keyPair.publicKey.serialize(), Base64.NO_WRAP)
                    )
                }
            )

            val response = keysApi.uploadPreKeys(request)
            if (response.isSuccessful) {
                Timber.d("Successfully replenished ${newOtks.size} OTKs")
                Result.success()
            } else {
                Timber.e("OTK replenishment failed: ${response.code()}")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "OTKReplenishWorker failed")
            Result.retry()
        }
    }
}
