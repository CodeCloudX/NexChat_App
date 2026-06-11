package com.nexchat.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexchat.core.auth.TokenStorage
import com.nexchat.core.network.api.UserApi
import com.nexchat.core.network.dto.FcmTokenRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class FcmTokenUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val tokenStorage: TokenStorage,
    private val userApi: UserApi
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // No session → user is not logged in. Abort silently; the app will
        // re-enqueue this worker after login completes (see NexChatApplication).
        if (tokenStorage.getAccessToken() == null) {
            return Result.success()
        }

        val fcmToken = tokenStorage.getFcmToken()
        if (fcmToken.isNullOrEmpty()) {
            Timber.w("FcmTokenUpdateWorker: no FCM token in storage yet")
            return Result.success()
        }

        return try {
            val response = userApi.updateFcmToken(FcmTokenRequest(fcmToken))
            if (response.isSuccessful) {
                Result.success()
            } else {
                Timber.e("FcmTokenUpdateWorker: backend rejected sync (${response.code()})")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "FcmTokenUpdateWorker: network error")
            Result.retry()
        }
    }
}
