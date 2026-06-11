package com.nexchat.core.media

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

data class EncryptedMedia(
    val encrypted: ByteArray,
    val mediaKey: ByteArray,
    val iv: ByteArray,
    val sha256Hex: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedMedia) return false
        if (!encrypted.contentEquals(other.encrypted)) return false
        if (!mediaKey.contentEquals(other.mediaKey)) return false
        if (!iv.contentEquals(other.iv)) return false
        return sha256Hex == other.sha256Hex
    }
    override fun hashCode(): Int {
        var result = encrypted.contentHashCode()
        result = 31 * result + mediaKey.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + sha256Hex.hashCode()
        return result
    }
}

@Singleton
class MediaEncryptor @Inject constructor() {
    private val secureRandom = SecureRandom()
    private val ALGORITHM = "AES"
    private val TRANSFORMATION = "AES/CBC/PKCS5Padding"

    fun encrypt(bytes: ByteArray): EncryptedMedia {
        val key = ByteArray(32) // AES-256
        val iv = ByteArray(16)
        secureRandom.nextBytes(key)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, ALGORITHM), IvParameterSpec(iv))
        val encrypted = cipher.doFinal(bytes)

        val sha256 = generateSha256(encrypted)

        return EncryptedMedia(
            encrypted = encrypted,
            mediaKey = key,
            iv = iv,
            sha256Hex = sha256
        )
    }

    fun decrypt(data: EncryptedMedia): Result<ByteArray> {
        return runCatching {
            // Decrypt ONLY if integrity is verified by the caller first!
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(data.mediaKey, ALGORITHM), IvParameterSpec(data.iv))
            cipher.doFinal(data.encrypted)
        }
    }

    fun verifyIntegrity(bytes: ByteArray, sha256: String): Boolean {
        val actualSha256 = generateSha256(bytes)
        return actualSha256.equals(sha256, ignoreCase = true)
    }

    private fun generateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
