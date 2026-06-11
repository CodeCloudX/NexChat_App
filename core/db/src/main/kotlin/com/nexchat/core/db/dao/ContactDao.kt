package com.nexchat.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexchat.core.db.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts WHERE is_blocked = 0 ORDER BY COALESCE(first_name || ' ' || last_name, display_name) ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: String): ContactEntity?

    @Query("""
        SELECT * FROM contacts WHERE
        display_name LIKE '%' || :query || '%' OR
        first_name   LIKE '%' || :query || '%' OR
        last_name    LIKE '%' || :query || '%' OR
        phone        LIKE '%' || :query || '%' OR
        email        LIKE '%' || :query || '%'
    """)
    suspend fun searchLocal(query: String): List<ContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(c: ContactEntity)

    @Query("UPDATE contacts SET banner_dismissed = 1 WHERE id = :id")
    suspend fun dismissBanner(id: String)

    @Query("UPDATE contacts SET is_blocked = 1 WHERE id = :id")
    suspend fun blockContact(id: String)

    @Query("UPDATE contacts SET first_name = :firstName, last_name = :lastName WHERE id = :id")
    suspend fun updateName(id: String, firstName: String, lastName: String?)

    @Query("UPDATE contacts SET accent_color = :color WHERE id = :id")
    suspend fun updateAccentColor(id: String, color: String)

    // isOnline is ephemeral presence state — only lastSeen is persisted.
    @Query("UPDATE contacts SET last_seen = :lastSeen WHERE id = :id")
    suspend fun updateOnlineStatus(id: String, lastSeen: Long?)
}
