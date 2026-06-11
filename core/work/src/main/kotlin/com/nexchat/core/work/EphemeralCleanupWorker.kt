package com.nexchat.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexchat.core.db.dao.MessageDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class EphemeralCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val messageDao: MessageDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("EphemeralCleanupWorker started")
        return try {
            val now = System.currentTimeMillis()
            val expiredMessages = messageDao.getExpiredMessages(now)

            if (expiredMessages.isNotEmpty()) {
                Timber.d("Found \${expiredMessages.size} expired messages, soft-deleting...")
                expiredMessages.forEach { msg ->
                    messageDao.softDelete(msg.id)
                }
            } else {
                Timber.d("No expired messages found")
            }

            // Let's also do a cleanup of standard synced messages that are older than say 90 days.
            // Currently, the prompt asks for "Time-bombed auto-delete from SQLite" which is getExpiredMessages.
            
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "EphemeralCleanupWorker failed")
            Result.retry()
        }
    }
}
