package com.nexchat.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexchat.core.auth.AuthManager
import com.nexchat.core.auth.AuthState
import com.nexchat.core.db.dao.MessageDao
import com.nexchat.core.db.dao.ParticipantDao
import com.nexchat.core.network.websocket.DevicePayload
import com.nexchat.core.network.websocket.MessageSendPayload
import com.nexchat.core.network.websocket.WebSocketManager
import com.nexchat.core.network.websocket.WsState
import com.nexchat.core.security.E2EEManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import timber.log.Timber

@HiltWorker
class QueueFlushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val messageDao: MessageDao,
    private val participantDao: ParticipantDao,
    private val webSocketManager: WebSocketManager,
    private val e2eeManager: E2EEManager,
    private val authManager: AuthManager,
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val WORK_NAME = "queue_flush"

        /**
         * Enqueues a unique QueueFlushWorker constrained to an active network.
         * Using KEEP so a pending flush is never duplicated when messages accumulate quickly.
         */
        fun enqueue(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<QueueFlushWorker>()
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result {
        val myUserId = (authManager.authState.value as? AuthState.LoggedIn)?.userId
            ?: return Result.failure() // No authenticated user — nothing to flush.

        // Wait up to 5 s for the WebSocket to be CONNECTED before spending CPU on encryption.
        repeat(10) {
            if (webSocketManager.state.value == WsState.CONNECTED) return@repeat
            delay(500)
        }
        if (webSocketManager.state.value != WsState.CONNECTED) {
            Timber.w("QueueFlushWorker: WS not connected — deferring to WorkManager retry")
            return Result.retry()
        }

        val queued = messageDao.getQueuedMessages()
        if (queued.isEmpty()) return Result.success()

        var allSucceeded = true

        for (msg in queued) {
            val content = msg.content
            if (content.isNullOrBlank()) {
                messageDao.markFailed(msg.localId)
                allSucceeded = false
                continue
            }

            var sent = false
            repeat(3) { attempt ->
                if (sent) return@repeat
                try {
                    messageDao.updateStatus(msg.localId, "sending", null)

                    // Fetch other participants on IO — ParticipantDao.getParticipants() is a Flow.
                    val participants = participantDao.getParticipants(msg.chatId)
                        .first()
                        .filter { it.userId != myUserId }

                    if (participants.isEmpty()) {
                        // No recipients yet — reset and leave for later.
                        messageDao.updateStatus(msg.localId, "queued", null)
                        sent = true
                        return@repeat
                    }

                    // Encrypt independently for each participant device on Dispatchers.IO
                    // (E2EEManager.encryptMessage already dispatches to dispatchers.io internally).
                    val devicePayloads = mutableListOf<DevicePayload>()
                    for (p in participants) {
                        val result = e2eeManager.encryptMessage(p.userId, deviceId = 1, content)
                        result.onSuccess { cipherBytes ->
                            devicePayloads += DevicePayload(
                                deviceId = p.userId, // device-level routing key
                                ciphertext = android.util.Base64.encodeToString(
                                    cipherBytes,
                                    android.util.Base64.NO_WRAP,
                                )
                            )
                        }.onFailure { e ->
                            Timber.e(e, "Encryption failed for participant ${p.userId} — skipping device")
                        }
                    }

                    if (devicePayloads.isEmpty()) {
                        messageDao.updateStatus(msg.localId, "queued", null)
                        return@repeat
                    }

                    val payload = MessageSendPayload(
                        localId = msg.localId,
                        chatId = msg.chatId,
                        payloads = devicePayloads,
                        createdAt = msg.createdAt,
                    )
                    webSocketManager.sendMessage(payload)
                    sent = true
                } catch (e: Exception) {
                    Timber.e(e, "QueueFlush attempt ${attempt + 1} failed for ${msg.localId}")
                    if (attempt == 2) {
                        messageDao.markFailed(msg.localId)
                        allSucceeded = false
                    } else {
                        delay(1_000L * (attempt + 1))
                    }
                }
            }
        }

        return if (allSucceeded) Result.success() else Result.retry()
    }
}
