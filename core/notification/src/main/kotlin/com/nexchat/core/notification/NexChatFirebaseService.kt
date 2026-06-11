package com.nexchat.core.notification

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nexchat.core.auth.TokenStorage
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class NexChatFirebaseService : FirebaseMessagingService() {

    @Inject lateinit var tokenStorage: TokenStorage
    @Inject lateinit var notificationManager: NexChatNotificationManager

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("FCM Token refreshed")
        tokenStorage.saveFcmToken(token)
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(
                "fcm_token_sync",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<com.nexchat.core.work.FcmTokenUpdateWorker>().build()
            )
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Timber.d("FCM Message received from: ${message.from}")

        when (message.data["type"]) {
            "new_message" -> {
                WorkManager.getInstance(applicationContext)
                    .enqueueUniqueWork(
                        "message_sync",
                        ExistingWorkPolicy.KEEP,
                        OneTimeWorkRequestBuilder<com.nexchat.core.work.MessageSyncWorker>().build()
                    )
            }
            "call_incoming" -> {
                Timber.d("Incoming call — WebRTC ringing handled by feature:calls")
            }
            else -> Timber.w("Unknown FCM message type: ${message.data["type"]}")
        }
    }
}
