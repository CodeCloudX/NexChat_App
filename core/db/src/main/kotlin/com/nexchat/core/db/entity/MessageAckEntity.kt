package com.nexchat.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "message_acks",
    primaryKeys = ["message_id", "user_id"],
)
data class MessageAckEntity(
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val status: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
