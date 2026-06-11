package com.nexchat.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val phone: String? = null,
    val email: String? = null,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "first_name") val firstName: String? = null,
    @ColumnInfo(name = "last_name") val lastName: String? = null,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String? = null,
    @ColumnInfo(name = "avatar_path") val avatarPath: String? = null,
    @ColumnInfo(name = "accent_color") val accentColor: String? = null,
    // ByteArray stored as Base64 TEXT via Converters — SQLCipher-safe, no BLOB corruption.
    @ColumnInfo(name = "public_key") val publicKey: ByteArray? = null,
    @ColumnInfo(name = "key_version", defaultValue = "1") val keyVersion: Int = 1,
    @ColumnInfo(name = "banner_dismissed", defaultValue = "0") val bannerDismissed: Boolean = false,
    @ColumnInfo(name = "is_blocked", defaultValue = "0") val isBlocked: Boolean = false,
    @ColumnInfo(name = "last_seen") val lastSeen: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContactEntity) return false
        return id == other.id && publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int = 31 * id.hashCode() + publicKey.contentHashCode()
}
