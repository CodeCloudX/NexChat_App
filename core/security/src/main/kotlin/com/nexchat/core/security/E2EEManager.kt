package com.nexchat.core.security

import com.nexchat.core.common.AppDispatchers
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin facade over libsignal-client 0.86.5.
 *
 * Crypto is boring, minimal, and deterministic:
 * 1. Validate input (requireNotNull / require)
 * 2. Pass to libsignal
 * 3. Handle success or failure
 *
 * All operations run on [AppDispatchers.io] — Room DAO calls in
 * [SignalProtocolStoreImpl] are blocking and must not run on main thread.
 */
@Singleton
class E2EEManager @Inject constructor(
    private val protocolStore: SignalProtocolStoreImpl,
    private val dispatchers: AppDispatchers
) {
    /** Generates and persists a new identity key pair. Call once at registration. */
    suspend fun generateIdentityKeyPair(): Result<IdentityKeyPair> = withContext(dispatchers.io) {
        runCatching {
            val keyPair = IdentityKeyPair.generate()
            // saveLocalIdentityKeyPair stores keyPair.serialize() which includes BOTH
            // the public and private key. saveIdentity() must NOT be used here — it
            // only stores the public IdentityKey, which causes getIdentityKeyPair()
            // to crash with InvalidMessageException when it tries to deserialize
            // public-key-only bytes as a full IdentityKeyPair protobuf.
            protocolStore.saveLocalIdentityKeyPair(keyPair)
            keyPair
        }
    }

    /** Retrieves the persisted identity key pair. Throws if not yet initialized. */
    suspend fun getIdentityKeyPair(): Result<IdentityKeyPair> = withContext(dispatchers.io) {
        runCatching { protocolStore.getIdentityKeyPair() }
    }

    /** Generates and persists a new signed pre-key. */
    suspend fun generateSignedPreKey(keyId: Int): Result<SignedPreKeyRecord> = withContext(dispatchers.io) {
        runCatching {
            val identityKeyPair = protocolStore.getIdentityKeyPair()
            val ecKeyPair = ECKeyPair.generate()
            val signature = identityKeyPair.privateKey.calculateSignature(ecKeyPair.publicKey.serialize())
            val record = SignedPreKeyRecord(keyId, System.currentTimeMillis(), ecKeyPair, signature)
            protocolStore.storeSignedPreKey(keyId, record)
            record
        }
    }

    /** Retrieves a persisted signed pre-key. Throws if not found. */
    suspend fun getSignedPreKey(keyId: Int): Result<SignedPreKeyRecord> = withContext(dispatchers.io) {
        runCatching { protocolStore.loadSignedPreKey(keyId) }
    }

    /** Generates and persists [count] one-time pre-keys starting at [startId]. */
    suspend fun generateOneTimePreKeys(startId: Int, count: Int = 50): Result<List<PreKeyRecord>> =
        withContext(dispatchers.io) {
            runCatching {
                (startId until startId + count).map { id ->
                    val record = PreKeyRecord(id, ECKeyPair.generate())
                    protocolStore.storePreKey(id, record)
                    record
                }
            }
        }

    /**
     * Establishes an X3DH session using the server-issued pre-key bundle.
     *
     * All parameters must be present and valid. Missing crypto data is an error —
     * validate at the call site before invoking this method.
     *
     * @param kyberPreKeyId       Kyber pre-key ID from server. Pass 0 only when the
     *                             server explicitly confirms no PQXDH support.
     * @param kyberPreKeyBytes    Required when [kyberPreKeyId] > 0. Null when server
     *                             does not yet provision Kyber keys.
     * @param kyberPreKeySig      Required when [kyberPreKeyId] > 0.
     */
    suspend fun establishSession(
        recipientId: String,
        deviceId: Int,
        registrationId: Int,
        identityKeyBytes: ByteArray,
        signedPreKeyId: Int,
        signedPreKeyBytes: ByteArray,
        signedPreKeySig: ByteArray,
        preKeyId: Int,
        preKeyBytes: ByteArray?,
        kyberPreKeyId: Int,
        kyberPreKeyBytes: ByteArray?,
        kyberPreKeySig: ByteArray?
    ): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            require(identityKeyBytes.isNotEmpty()) { "identityKeyBytes must not be empty" }
            require(signedPreKeyBytes.isNotEmpty()) { "signedPreKeyBytes must not be empty" }
            require(signedPreKeySig.isNotEmpty()) { "signedPreKeySig must not be empty" }

            val identityKey = org.signal.libsignal.protocol.IdentityKey(identityKeyBytes, 0)
            val signedPreKey = ECPublicKey(signedPreKeyBytes)
            val preKey: ECPublicKey? = preKeyBytes?.takeIf { it.isNotEmpty() }?.let { ECPublicKey(it) }

            // When kyberPreKeyId > 0, both key bytes and signature are mandatory.
            if (kyberPreKeyId > 0) {
                requireNotNull(kyberPreKeyBytes) { "kyberPreKeyBytes required when kyberPreKeyId=$kyberPreKeyId" }
                requireNotNull(kyberPreKeySig) { "kyberPreKeySig required when kyberPreKeyId=$kyberPreKeyId" }
            }
            val kemKey: KEMPublicKey? = if (kyberPreKeyId > 0) KEMPublicKey(kyberPreKeyBytes!!) else null
            val kemSig: ByteArray = if (kyberPreKeyId > 0) kyberPreKeySig!! else ByteArray(0)

            val bundle = PreKeyBundle(
                registrationId, deviceId,
                preKeyId, preKey,
                signedPreKeyId, signedPreKey, signedPreKeySig,
                identityKey,
                kyberPreKeyId, requireNotNull(kemKey) { "KEMPublicKey must not be null" },
                kemSig
            )

            SessionBuilder(protocolStore, SignalProtocolAddress(recipientId, deviceId)).process(bundle)
            Timber.d("E2EE session established with $recipientId:$deviceId")
        }
    }

    suspend fun encryptMessage(
        recipientId: String,
        deviceId: Int,
        plaintext: String
    ): Result<ByteArray> = withContext(dispatchers.io) {
        runCatching {
            SessionCipher(protocolStore, SignalProtocolAddress(recipientId, deviceId))
                .encrypt(plaintext.toByteArray(Charsets.UTF_8))
                .serialize()
        }
    }

    suspend fun decryptMessage(
        senderId: String,
        deviceId: Int,
        ciphertext: ByteArray
    ): Result<String> = withContext(dispatchers.io) {
        runCatching {
            require(ciphertext.isNotEmpty()) { "ciphertext must not be empty" }
            val cipher = SessionCipher(protocolStore, SignalProtocolAddress(senderId, deviceId))
            // First nibble: PREKEY_TYPE (3) = new session handshake; else ongoing WHISPER (2).
            val plaintext = when ((ciphertext[0].toInt() and 0xFF) shr 4) {
                CiphertextMessage.PREKEY_TYPE -> cipher.decrypt(PreKeySignalMessage(ciphertext))
                else -> cipher.decrypt(SignalMessage(ciphertext))
            }
            String(plaintext, Charsets.UTF_8)
        }
    }
}
