package com.nexchat.core.security

import com.nexchat.core.db.dao.SignalDao
import com.nexchat.core.db.entity.SignalPreKeyEntity
import com.nexchat.core.db.entity.SignalSignedPreKeyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Async wrapper around the Room-backed pre-key DAOs. Separates the
 * lifecycle concern (key rotation scheduling) from the synchronous
 * [SignalProtocolStoreImpl] which must use blocking DAO calls on
 * libsignal's own threads.
 */
@Singleton
class PreKeyStoreDelegate @Inject constructor(
    private val signalDao: SignalDao
) {
    suspend fun getMaxPreKeyId(): Int = withContext(Dispatchers.IO) {
        signalDao.getMaxPreKeyId() ?: 0
    }

    suspend fun getMaxSignedPreKeyId(): Int = withContext(Dispatchers.IO) {
        signalDao.getMaxSignedPreKeyId() ?: 0
    }

    suspend fun savePreKeys(records: List<PreKeyRecord>) = withContext(Dispatchers.IO) {
        records.forEach { record ->
            signalDao.savePreKeyBlocking(
                SignalPreKeyEntity(
                    keyId = record.id,
                    keyRecord = record.serialize(),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun saveSignedPreKey(id: Int, record: SignedPreKeyRecord) = withContext(Dispatchers.IO) {
        signalDao.saveSignedPreKeyBlocking(
            SignalSignedPreKeyEntity(
                keyId = id,
                keyRecord = record.serialize(),
                createdAt = System.currentTimeMillis(),
                deleteAt = null
            )
        )
    }

    /** Schedule a signed pre-key for deletion 48 hours after rotation. */
    suspend fun scheduleSignedPreKeyDeletion(oldId: Int) = withContext(Dispatchers.IO) {
        val deleteAt = System.currentTimeMillis() + (48L * 60L * 60L * 1_000L)
        signalDao.setSignedPreKeyDeleteAt(oldId, deleteAt)
    }

    suspend fun deleteExpiredSignedPreKeys() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        signalDao.getAllSignedPreKeysBlocking().forEach { key ->
            val deleteAt = key.deleteAt
            if (deleteAt != null && deleteAt < now) {
                signalDao.deleteSignedPreKeyBlocking(key.keyId)
            }
        }
    }
}
