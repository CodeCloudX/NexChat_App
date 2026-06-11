package com.nexchat.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String? = null,
    @ColumnInfo(name = "avatar_path") val avatarPath: String? = null,
    @ColumnInfo(name = "accent_color", defaultValue = "#0F766E") val accentColor: String = "#0F766E",
    @ColumnInfo(name = "is_muted", defaultValue = "0") val isMuted: Boolean = false,
    @ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived: Boolean = false,
    @ColumnInfo(name = "is_pinned", defaultValue = "0") val isPinned: Boolean = false,
    @ColumnInfo(name = "ephemeral_mode", defaultValue = "off") val ephemeralMode: String = "off",
    @ColumnInfo(name = "ephemeral_secs") val ephemeralSecs: Long? = null,
    @ColumnInfo(name = "draft_text") val draftText: String? = null,
    @ColumnInfo(name = "draft_saved_at") val draftSavedAt: Long? = null,
    @ColumnInfo(name = "last_message_id") val lastMessageId: String? = null,
    @ColumnInfo(name = "last_message_at") val lastMessageAt: Long? = null,
    @ColumnInfo(name = "unread_count", defaultValue = "0") val unreadCount: Int = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
