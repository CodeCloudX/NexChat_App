package com.nexchat.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexchat.core.auth.TokenStorage
import com.nexchat.core.network.websocket.WebSocketManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import timber.log.Timber

@HiltWorker
class MessageSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val webSocketManager: WebSocketManager,
    private val tokenStorage: TokenStorage
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("MessageSyncWorker started (triggered by FCM)")
        val token = tokenStorage.getAccessToken() ?: return Result.failure()

        return try {
            // Wake up WebSocket to allow backend to flush offline messages to us.
            // The WS listener in repository will handle decryption and inserting to DB,
            // and triggering NexChatNotificationManager.
            
            webSocketManager.reconnectImmediate()
            
            // Hold the worker alive for 10 seconds to allow network handshake & messages to arrive.
            // Android allows up to 10 minutes for doWork(). 
            // In a more robust setup, we'd listen to an event bus for "sync_complete" from WS.
            delay(10_000)
            
            // Disconnect gracefully after sync if app is still in background.
            // ProcessLifecycleOwner handles foreground state automatically.
            webSocketManager.disconnect(1000, "sync_worker_completed")
            
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "MessageSyncWorker failed")
            Result.retry()
        }
    }
}
