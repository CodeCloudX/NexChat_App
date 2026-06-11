package com.nexchat.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexchat.core.db.entity.ParticipantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParticipantDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(participant: ParticipantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(participants: List<ParticipantEntity>)

    @Query("SELECT * FROM participants WHERE chat_id = :chatId")
    fun getParticipants(chatId: String): Flow<List<ParticipantEntity>>

    @Query("DELETE FROM participants WHERE chat_id = :chatId AND user_id = :userId")
    suspend fun remove(chatId: String, userId: String)

    @Query("DELETE FROM participants WHERE chat_id = :chatId")
    suspend fun removeAll(chatId: String)
}
