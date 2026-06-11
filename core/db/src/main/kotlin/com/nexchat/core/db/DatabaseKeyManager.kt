package com.nexchat.core.db

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

private const val SEED_PREFS_FILE = "nexchat_db_seed"
private const val SEED_PREFS_KEY = "ikm"
private const val SEED_LENGTH = 32
private val HKDF_INFO = "nexchat_sqlcipher_v1".toByteArray(Charsets.UTF_8)

@Singleton
class DatabaseKeyManager @Inject constructor(
    @param:ApplicationContext private val ctx: Context,
) {
    @Volatile private var cachedPassphrase: ByteArray? = null

    fun getSQLCipherPassphrase(): ByteArray =
        cachedPassphrase ?: synchronized(this) {
            cachedPassphrase ?: hkdfDerive(getOrCreateIkm(), HKDF_INFO, SEED_LENGTH)
                .also { cachedPassphrase = it }
        }

    @Suppress("DEPRECATION")
    private fun getOrCreateIkm(): ByteArray {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            ctx,
            SEED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        val encoded = prefs.getString(SEED_PREFS_KEY, null)
        if (encoded != null) return Base64.decode(encoded, Base64.NO_WRAP)

        val seed = ByteArray(SEED_LENGTH).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(SEED_PREFS_KEY, Base64.encodeToString(seed, Base64.NO_WRAP)).apply()
        return seed
    }
}

// Internal visibility exposes this to the JVM unit test without making it part of the public API.
internal fun hkdfDerive(ikm: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
    require(outputLength <= 32) { "HKDF-SHA256 single-block expand supports at most 32 bytes" }

    // RFC 5869 §2.2 Extract: PRK = HMAC-SHA256(salt=zeros, IKM)
    val prk = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
        doFinal(ikm)
    }

    // RFC 5869 §2.3 Expand: T(1) = HMAC-SHA256(PRK, info || 0x01)
    val okm = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(prk, "HmacSHA256"))
        update(info)
        update(0x01.toByte())
        doFinal()
    }

    return if (outputLength == okm.size) okm else okm.copyOfRange(0, outputLength)
}
