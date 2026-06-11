package com.nexchat.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey @ColumnInfo(name = "message_id") val messageId: String,
    val type: String,
    @ColumnInfo(name = "local_path") val localPath: String? = null,
    @ColumnInfo(name = "remote_url") val remoteUrl: String? = null,
    @ColumnInfo(name = "file_size") val fileSize: Long? = null,
    @ColumnInfo(name = "mime_type") val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
    val blurhash: String? = null,
    @ColumnInfo(name = "encryption_key") val encryptionKey: String? = null,
    @ColumnInfo(name = "encryption_iv") val encryptionIv: String? = null,
    @ColumnInfo(name = "downloaded_at") val downloadedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
