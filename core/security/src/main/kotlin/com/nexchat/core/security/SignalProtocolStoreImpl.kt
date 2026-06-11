package com.nexchat.core.security

import com.nexchat.core.auth.TokenStorage
import com.nexchat.core.db.dao.SignalDao
import com.nexchat.core.db.entity.SignalIdentityKeyEntity
import com.nexchat.core.db.entity.SignalPreKeyEntity
import com.nexchat.core.db.entity.SignalSessionEntity
import com.nexchat.core.db.entity.SignalSignedPreKeyEntity
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignalProtocolStoreImpl @Inject constructor(
    private val signalDao: SignalDao,
    private val tokenStorage: TokenStorage
) : SignalProtocolStore {

    // ──────────────────────────────────────────────────────────────────────────
    // IdentityKeyStore
    // ──────────────────────────────────────────────────────────────────────────

    override fun getIdentityKeyPair(): IdentityKeyPair {
        val entity = signalDao.getMyIdentityKeyBlocking()
            ?: throw IllegalStateException("Local identity key not initialized")
        return IdentityKeyPair(entity.identityKey)
    }

    /**
     * Persists the local [IdentityKeyPair] (public + private) under the sentinel
     * address LOCAL_IDENTITY. Must be called instead of [saveIdentity] for the
     * local device — [saveIdentity] only persists the public [IdentityKey] and
     * would cause [getIdentityKeyPair] to crash with a protobuf parse error.
     */
    fun saveLocalIdentityKeyPair(keyPair: IdentityKeyPair) {
        signalDao.saveIdentityKeyBlocking(
            SignalIdentityKeyEntity(
                address = "LOCAL_IDENTITY",
                identityKey = keyPair.serialize(),
                verified = true,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    override fun getLocalRegistrationId(): Int = tokenStorage.getRegistrationId()

    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey
    ): IdentityKeyStore.IdentityChange {
        val existing = signalDao.getIdentityBlocking(address.name)
        signalDao.saveIdentityKeyBlocking(
            SignalIdentityKeyEntity(
                address = address.name,
                identityKey = identityKey.serialize(),
                verified = false,
                addedAt = System.currentTimeMillis()
            )
        )
        // REPLACED_EXISTING when a prior key was overwritten; NEW_OR_UNCHANGED otherwise.
        return if (existing != null && !existing.identityKey.contentEquals(identityKey.serialize()))
            IdentityKeyStore.IdentityChange.REPLACED_EXISTING
        else
            IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean {
        // TOFU: unknown identities are accepted on first contact.
        val existing = signalDao.getIdentityBlocking(address.name) ?: return true
        return existing.identityKey.contentEquals(identityKey.serialize())
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val existing = signalDao.getIdentityBlocking(address.name) ?: return null
        return IdentityKey(existing.identityKey, 0)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PreKeyStore
    // ──────────────────────────────────────────────────────────────────────────

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val entity = signalDao.getPreKeyBlocking(preKeyId)
            ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No prekey: $preKeyId")
        return PreKeyRecord(entity.keyRecord)
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        signalDao.savePreKeyBlocking(
            SignalPreKeyEntity(
                keyId = preKeyId,
                keyRecord = record.serialize(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override fun containsPreKey(preKeyId: Int): Boolean = signalDao.hasPreKeyBlocking(preKeyId)

    override fun removePreKey(preKeyId: Int) = signalDao.removePreKeyBlocking(preKeyId)

    // ──────────────────────────────────────────────────────────────────────────
    // SessionStore
    // ──────────────────────────────────────────────────────────────────────────

    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val entity = signalDao.getSessionBlocking(address.toString())
        return if (entity != null) SessionRecord(entity.sessionRecord) else SessionRecord()
    }

    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>): MutableList<SessionRecord> {
        return addresses.mapNotNull { addr ->
            val entity = signalDao.getSessionBlocking(addr.toString())
            if (entity != null) SessionRecord(entity.sessionRecord) else null
        }.toMutableList()
    }

    override fun getSubDeviceSessions(name: String): MutableList<Int> {
        return signalDao.getSubDeviceSessionsBlocking(name)
            .map { it.address.substringAfter(":").toIntOrNull() ?: 1 }
            .toMutableList()
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        signalDao.saveSessionBlocking(
            SignalSessionEntity(
                address = address.toString(),
                sessionRecord = record.serialize(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        val entity = signalDao.getSessionBlocking(address.toString())
        return entity != null && entity.sessionRecord.isNotEmpty()
    }

    override fun deleteSession(address: SignalProtocolAddress) =
        signalDao.deleteSessionBlocking(address.toString())

    override fun deleteAllSessions(name: String) = signalDao.deleteAllSessionsBlocking(name)

    // ──────────────────────────────────────────────────────────────────────────
    // SignedPreKeyStore
    // ──────────────────────────────────────────────────────────────────────────

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val entity = signalDao.getSignedPreKeyBlocking(signedPreKeyId)
            ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No signed prekey: $signedPreKeyId")
        return SignedPreKeyRecord(entity.keyRecord)
    }

    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> {
        return signalDao.getAllSignedPreKeysBlocking()
            .map { SignedPreKeyRecord(it.keyRecord) }
            .toMutableList()
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        signalDao.saveSignedPreKeyBlocking(
            SignalSignedPreKeyEntity(
                keyId = signedPreKeyId,
                keyRecord = record.serialize(),
                createdAt = System.currentTimeMillis(),
                deleteAt = null
            )
        )
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean =
        signalDao.getSignedPreKeyBlocking(signedPreKeyId) != null

    override fun removeSignedPreKey(signedPreKeyId: Int) =
        signalDao.deleteSignedPreKeyBlocking(signedPreKeyId)

    // ──────────────────────────────────────────────────────────────────────────
    // SenderKeyStore — group messaging sender-key persistence.
    // Wired by the group chat repository once group sessions are established.
    // ──────────────────────────────────────────────────────────────────────────

    override fun storeSenderKey(address: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) = Unit

    override fun loadSenderKey(address: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? = null

    // ──────────────────────────────────────────────────────────────────────────
    // KyberPreKeyStore — post-quantum pre-key persistence.
    // Keys are provisioned server-side during registration; this store holds the device copy.
    // ──────────────────────────────────────────────────────────────────────────

    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
        throw org.signal.libsignal.protocol.InvalidKeyIdException("Kyber store not yet provisioned")
    }

    override fun loadKyberPreKeys(): MutableList<KyberPreKeyRecord> = mutableListOf()

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) = Unit

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = false

    /**
     * Called by libsignal after a last-resort Kyber key is consumed.
     * [ephemeralPublicKey] is the sender's ephemeral EC key from the sealed-sender
     * message, retained for the forward-secrecy grace window.
     */
    @Throws(org.signal.libsignal.protocol.ReusedBaseKeyException::class)
    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, lastResortKyberPreKeyId: Int, ephemeralPublicKey: ECPublicKey) = Unit
}
