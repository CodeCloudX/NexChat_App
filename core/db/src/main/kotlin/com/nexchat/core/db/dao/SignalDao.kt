package com.nexchat.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexchat.core.db.entity.SignalIdentityKeyEntity
import com.nexchat.core.db.entity.SignalPreKeyEntity
import com.nexchat.core.db.entity.SignalSessionEntity
import com.nexchat.core.db.entity.SignalSignedPreKeyEntity

// All query functions are synchronous (non-suspend) because libsignal-client Java interfaces
// are blocking by design and must not be called inside a coroutine suspend context.
@Dao
interface SignalDao {

    @Query("SELECT * FROM signal_sessions WHERE address = :address")
    fun getSessionBlocking(address: String): SignalSessionEntity?

    @Query("SELECT * FROM signal_sessions WHERE address LIKE :name || '.%'")
    fun getSubDeviceSessionsBlocking(name: String): List<SignalSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveSessionBlocking(session: SignalSessionEntity)

    @Query("DELETE FROM signal_sessions WHERE address = :address")
    fun deleteSessionBlocking(address: String)

    @Query("DELETE FROM signal_sessions WHERE address LIKE :name || '.%'")
    fun deleteAllSessionsBlocking(name: String)

    @Query("SELECT * FROM signal_prekeys WHERE key_id = :keyId")
    fun getPreKeyBlocking(keyId: Int): SignalPreKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun savePreKeyBlocking(preKey: SignalPreKeyEntity)

    @Query("DELETE FROM signal_prekeys WHERE key_id = :keyId")
    fun removePreKeyBlocking(keyId: Int)

    @Query("SELECT COUNT(*) > 0 FROM signal_prekeys WHERE key_id = :keyId")
    fun hasPreKeyBlocking(keyId: Int): Boolean

    @Query("SELECT * FROM signal_signed_prekeys WHERE key_id = :keyId")
    fun getSignedPreKeyBlocking(keyId: Int): SignalSignedPreKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveSignedPreKeyBlocking(signedPreKey: SignalSignedPreKeyEntity)

    @Query("UPDATE signal_signed_prekeys SET delete_at = :deleteAt WHERE key_id = :keyId")
    fun setSignedPreKeyDeleteAt(keyId: Int, deleteAt: Long)

    @Query("SELECT * FROM signal_signed_prekeys")
    fun getAllSignedPreKeysBlocking(): List<SignalSignedPreKeyEntity>

    @Query("DELETE FROM signal_signed_prekeys WHERE key_id = :keyId")
    fun deleteSignedPreKeyBlocking(keyId: Int)

    @Query("SELECT * FROM signal_identity_keys WHERE address = 'LOCAL_IDENTITY'")
    fun getMyIdentityKeyBlocking(): SignalIdentityKeyEntity?

    @Query("SELECT * FROM signal_identity_keys WHERE address = :address")
    fun getIdentityBlocking(address: String): SignalIdentityKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveIdentityKeyBlocking(identity: SignalIdentityKeyEntity)

    @Query("SELECT MAX(key_id) FROM signal_prekeys")
    suspend fun getMaxPreKeyId(): Int?

    @Query("SELECT MAX(key_id) FROM signal_signed_prekeys")
    suspend fun getMaxSignedPreKeyId(): Int?
}
