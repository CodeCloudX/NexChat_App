package com.nexchat.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index("chat_id"),
        Index(value = ["local_id"], unique = true),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "local_id") val localId: String,
    @ColumnInfo(name = "chat_id") val chatId: String,
    @ColumnInfo(name = "sender_id") val senderId: String,
    val type: String,
    val content: String? = null,
    @ColumnInfo(name = "media_path") val mediaPath: String? = null,
    @ColumnInfo(name = "media_url") val mediaUrl: String? = null,
    @ColumnInfo(name = "media_thumb") val mediaThumb: String? = null,
    @ColumnInfo(name = "media_key") val mediaKey: String? = null,
    @ColumnInfo(name = "media_iv") val mediaIv: String? = null,
    @ColumnInfo(name = "reply_to_id") val replyToId: String? = null,
    @ColumnInfo(name = "status", defaultValue = "queued") val status: String = "queued",
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @ColumnInfo(name = "expiry_at") val expiryAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "server_ts") val serverTs: Long? = null,
)
