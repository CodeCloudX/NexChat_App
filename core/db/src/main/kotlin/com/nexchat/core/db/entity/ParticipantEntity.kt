package com.nexchat.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "participants",
    primaryKeys = ["chat_id", "user_id"],
)
data class ParticipantEntity(
    @ColumnInfo(name = "chat_id") val chatId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "role", defaultValue = "member") val role: String = "member",
    @ColumnInfo(name = "joined_at") val joinedAt: Long,
)
