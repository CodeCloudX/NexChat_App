package com.nexchat.core.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteDatabase
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles encrypted database backup using SQLCipher's sqlcipher_export() pragma.
 *
 * The export re-encrypts the live database using the user's chosen backup password,
 * producing a portable backup that is NOT tied to the device hardware Keystore.
 * The user can restore it on any device that knows the backup password.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val db: NexChatDatabase
) {
    /**
     * Creates an encrypted backup of the live database at [backupFile] using
     * [userPassword] as the SQLCipher key for the backup.
     *
     * Uses Room's existing open connection — no second connection or extra deps needed.
     * Fails immediately if [userPassword] is blank.
     */
    suspend fun createBackup(backupFile: File, userPassword: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(userPassword.isNotBlank()) { "Backup password must not be blank" }

                backupFile.parentFile?.mkdirs()

                // Room's SupportSQLiteDatabase is backed by net.zetetic SQLiteDatabase.
                // Cast to access rawExecSQL() which SupportSQLiteDatabase does not expose.
                val sqliteDb = db.openHelper.writableDatabase as SQLiteDatabase

                sqliteDb.rawExecSQL(
                    "ATTACH DATABASE '${backupFile.absolutePath}' AS backup KEY '$userPassword'"
                )
                sqliteDb.rawExecSQL("SELECT sqlcipher_export('backup')")
                sqliteDb.rawExecSQL("DETACH DATABASE backup")

                Timber.d("Portable encrypted backup written: ${backupFile.name}")
            }
        }

    /**
     * Deletes backup files older than [maxAgeDays] days from [backupDir].
     */
    fun pruneOldBackups(backupDir: File, maxAgeDays: Int = 7) {
        val cutoff = System.currentTimeMillis() - (maxAgeDays.toLong() * 24 * 60 * 60 * 1_000)
        backupDir.listFiles()
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }
}
