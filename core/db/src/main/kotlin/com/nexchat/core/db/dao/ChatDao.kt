package com.nexchat.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexchat.core.db.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chats ORDER BY last_message_at DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id")
    fun getChatById(id: String): Flow<ChatEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(chat: ChatEntity)

    @Query("UPDATE chats SET draft_text = :text, draft_saved_at = :savedAt WHERE id = :id")
    suspend fun updateDraft(id: String, text: String?, savedAt: Long?)

    @Query("UPDATE chats SET last_message_id = :msgId, last_message_at = :ts WHERE id = :chatId")
    suspend fun updateLastMessage(chatId: String, msgId: String, ts: Long)

    @Query("UPDATE chats SET unread_count = 0 WHERE id = :chatId")
    suspend fun clearUnread(chatId: String)

    @Query("UPDATE chats SET accent_color = :color WHERE id = :chatId")
    suspend fun updateAccentColor(chatId: String, color: String)

    @Query("UPDATE chats SET is_pinned = :value WHERE id = :id")
    suspend fun updatePinned(id: String, value: Boolean)

    @Query("UPDATE chats SET is_archived = :value WHERE id = :id")
    suspend fun updateArchived(id: String, value: Boolean)

    @Query("UPDATE chats SET is_muted = :value WHERE id = :id")
    suspend fun updateMuted(id: String, value: Boolean)
}
