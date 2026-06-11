package com.nexchat.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_signed_prekeys")
data class SignalSignedPreKeyEntity(
    @PrimaryKey @ColumnInfo(name = "key_id") val keyId: Int,
    @ColumnInfo(name = "key_record") val keyRecord: ByteArray,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    // Scheduled deletion timestamp set after a rotation grace period.
    @ColumnInfo(name = "delete_at") val deleteAt: Long? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalSignedPreKeyEntity) return false
        return keyId == other.keyId && keyRecord.contentEquals(other.keyRecord)
    }

    override fun hashCode(): Int = 31 * keyId.hashCode() + keyRecord.contentHashCode()
}
