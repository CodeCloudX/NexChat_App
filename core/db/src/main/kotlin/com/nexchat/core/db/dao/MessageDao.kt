package com.nexchat.core.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.SkipQueryVerification
import com.nexchat.core.db.entity.MessageEntity

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(msgs: List<MessageEntity>)

    // COALESCE ensures undelivered messages (null server_ts) sort by local creation time.
    @Query("SELECT * FROM messages WHERE chat_id = :chatId AND is_deleted = 0 ORDER BY COALESCE(server_ts, created_at) DESC")
    fun getMessagesPaged(chatId: String): PagingSource<Int, MessageEntity>

    @Query("SELECT * FROM messages WHERE status = 'queued' ORDER BY created_at ASC")
    suspend fun getQueuedMessages(): List<MessageEntity>

    @Query("UPDATE messages SET status = :status, server_ts = :serverTs WHERE local_id = :localId")
    suspend fun updateStatus(localId: String, status: String, serverTs: Long?)

    @Query("UPDATE messages SET status = 'failed' WHERE local_id = :localId")
    suspend fun markFailed(localId: String)

    @Query("UPDATE messages SET is_deleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("SELECT * FROM messages WHERE expiry_at IS NOT NULL AND expiry_at <= :now AND is_deleted = 0")
    suspend fun getExpiredMessages(now: Long): List<MessageEntity>

    @Query("DELETE FROM messages WHERE status = 'sent' AND server_ts IS NOT NULL AND server_ts < :cutoffMs")
    suspend fun deleteExpiredSyncMessages(cutoffMs: Long)

    // messages_fts is created at runtime via RoomDatabase.Callback — skip compile-time
    // table validation for this query only; Room KSP cannot see runtime-created tables.
    @SkipQueryVerification
    @Query("""
        SELECT messages.* FROM messages
        INNER JOIN messages_fts ON messages.rowid = messages_fts.rowid
        WHERE messages_fts MATCH :query AND messages.is_deleted = 0
    """)
    suspend fun searchFts(query: String): List<MessageEntity>
}
