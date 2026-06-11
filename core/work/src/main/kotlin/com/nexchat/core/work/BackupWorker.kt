package com.nexchat.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexchat.core.auth.TokenStorage
import com.nexchat.core.db.BackupRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.File

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val tokenStorage: TokenStorage,
    private val backupRepository: BackupRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("BackupWorker started")
        return try {
            val password = tokenStorage.getBackupPassword()
            if (password.isNullOrEmpty()) {
                Timber.w("No backup password set — skipping backup")
                return Result.success()
            }

            val backupDir = File(applicationContext.filesDir, "backups")
            val backupFile = File(backupDir, "nexchat_backup_${System.currentTimeMillis()}.db")

            backupRepository.createBackup(backupFile, password).getOrThrow()
            backupRepository.pruneOldBackups(backupDir)

            Timber.d("Backup complete: ${backupFile.name}")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "BackupWorker failed")
            Result.retry()
        }
    }
}
