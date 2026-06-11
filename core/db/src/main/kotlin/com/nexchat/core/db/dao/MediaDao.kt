package com.nexchat.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexchat.core.db.entity.MediaEntity

@Dao
interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaEntity)

    @Query("SELECT * FROM media WHERE message_id = :messageId")
    suspend fun getByMessageId(messageId: String): MediaEntity?

    @Query("UPDATE media SET local_path = :path WHERE message_id = :messageId")
    suspend fun updateLocalPath(messageId: String, path: String?)

    @Query("SELECT * FROM media WHERE downloaded_at IS NOT NULL AND downloaded_at < :cutoffMs")
    suspend fun getExpiredDownloads(cutoffMs: Long): List<MediaEntity>
}
