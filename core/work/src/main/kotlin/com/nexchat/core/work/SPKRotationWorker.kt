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
class SPKRotationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val e2eeManager: E2EEManager,
    private val preKeyStoreDelegate: PreKeyStoreDelegate,
    private val keysApi: KeysApi
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("SPKRotationWorker started")
        return try {
            val maxSpkId = preKeyStoreDelegate.getMaxSignedPreKeyId()
            val newSpkId = maxSpkId + 1

            val spkRecord = e2eeManager.generateSignedPreKey(newSpkId).getOrThrow()

            // Schedule old SPK deletion after 48-hour grace window for in-flight messages.
            preKeyStoreDelegate.scheduleSignedPreKeyDeletion(maxSpkId)

            // Fetch the persisted identity key — do NOT regenerate it.
            val identityKeyPair = e2eeManager.getIdentityKeyPair().getOrThrow()
            val identityKeyBase64 = Base64.encodeToString(
                identityKeyPair.publicKey.serialize(), Base64.NO_WRAP
            )

            val request = UploadPreKeysRequest(
                type = "signed",
                identityKey = identityKeyBase64,
                signedPreKey = SignedPreKeyDto(
                    keyId = spkRecord.id,
                    publicKey = Base64.encodeToString(
                        spkRecord.keyPair.publicKey.serialize(), Base64.NO_WRAP
                    ),
                    signature = Base64.encodeToString(spkRecord.signature, Base64.NO_WRAP)
                ),
                oneTimePrekeys = emptyList()
            )

            val response = keysApi.uploadPreKeys(request)
            if (response.isSuccessful) {
                Timber.d("Successfully rotated SPK $newSpkId")
                Result.success()
            } else {
                Timber.e("SPK rotation failed: ${response.code()}")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "SPKRotationWorker failed")
            Result.retry()
        }
    }
}
