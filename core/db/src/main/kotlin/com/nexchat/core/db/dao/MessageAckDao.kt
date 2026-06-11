package com.nexchat.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexchat.core.db.entity.MessageAckEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageAckDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(ack: MessageAckEntity)

    @Query("SELECT * FROM message_acks WHERE message_id = :messageId")
    fun getAcksForMessage(messageId: String): Flow<List<MessageAckEntity>>

    @Query("UPDATE message_acks SET status = :status, updated_at = :updatedAt WHERE message_id = :messageId AND user_id = :userId")
    suspend fun updateStatus(messageId: String, userId: String, status: String, updatedAt: Long)
}
