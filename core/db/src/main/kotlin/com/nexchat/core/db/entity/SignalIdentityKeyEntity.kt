package com.nexchat.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_identity_keys")
data class SignalIdentityKeyEntity(
    @PrimaryKey val address: String,
    @ColumnInfo(name = "identity_key") val identityKey: ByteArray,
    @ColumnInfo(name = "verified", defaultValue = "0") val verified: Boolean = false,
    @ColumnInfo(name = "added_at") val addedAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalIdentityKeyEntity) return false
        return address == other.address && identityKey.contentEquals(other.identityKey)
    }

    override fun hashCode(): Int = 31 * address.hashCode() + identityKey.contentHashCode()
}
