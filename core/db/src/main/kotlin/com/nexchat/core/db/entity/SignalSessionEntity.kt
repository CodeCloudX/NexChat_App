package com.nexchat.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_sessions")
data class SignalSessionEntity(
    // Format: "userId:deviceId" — matches libsignal SignalProtocolAddress.toString()
    @PrimaryKey val address: String,
    // ByteArray stored as Base64 TEXT via Converters — avoids SQLCipher BLOB type corruption.
    @ColumnInfo(name = "session_record") val sessionRecord: ByteArray,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignalSessionEntity) return false
        return address == other.address && sessionRecord.contentEquals(other.sessionRecord)
    }

    override fun hashCode(): Int = 31 * address.hashCode() + sessionRecord.contentHashCode()
}
