package com.nexchat.core.media

import android.util.Base64
import com.nexchat.core.common.AppDispatchers
import com.nexchat.core.common.NetworkMonitor
import com.nexchat.core.network.api.MediaApi
import com.nexchat.core.storage.FileManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaDownloader @Inject constructor(
    private val mediaEncryptor: MediaEncryptor,
    private val mediaApi: MediaApi,
    private val fileManager: FileManager,
    private val okHttpClient: OkHttpClient,
    private val networkMonitor: NetworkMonitor,
    private val dispatchers: AppDispatchers
) {
    suspend fun download(
        messageId: String,
        mediaId: String,
        mediaKeyBase64: String,
        mediaIvBase64: String,
        mediaSha256: String,
        mimeType: String
    ): Result<String> = withContext(dispatchers.io) {
        runCatching {
            // 1. Get pre-signed download URL from backend
            val resp = mediaApi.getMediaInfo(mediaId)
            if (!resp.isSuccessful || resp.body() == null) {
                throw Exception("Failed to get media download info: ${resp.errorBody()?.string()}")
            }
            
            val downloadUrl = resp.body()!!.url

            // 2. Download encrypted bytes
            val encryptedBytes = downloadWithRetry(downloadUrl)

            // 3. Verify integrity BEFORE decryption to prevent tampered ciphertext attacks
            val isValid = mediaEncryptor.verifyIntegrity(encryptedBytes, mediaSha256)
            if (!isValid) {
                throw SecurityException("Media integrity verification failed (SHA-256 mismatch). File may be tampered.")
            }

            // 4. Decrypt
            val key = Base64.decode(mediaKeyBase64, Base64.NO_WRAP)
            val iv = Base64.decode(mediaIvBase64, Base64.NO_WRAP)
            val encryptedMedia = EncryptedMedia(encryptedBytes, key, iv, mediaSha256)
            
            val decryptedBytes = mediaEncryptor.decrypt(encryptedMedia).getOrThrow()

            // 5. Save to local scoped storage
            fileManager.saveMedia(messageId, decryptedBytes, mimeType)
        }
    }

    private suspend fun downloadWithRetry(downloadUrl: String, maxRetries: Int = 3): ByteArray {
        var currentAttempt = 0
        var lastException: Exception? = null

        while (currentAttempt < maxRetries) {
            try {
                val request = Request.Builder().url(downloadUrl).get().build()
                val response = okHttpClient.newCall(request).execute()
                
                if (response.isSuccessful && response.body != null) {
                    return response.body!!.bytes()
                } else {
                    throw Exception("S3 download failed with code ${response.code}")
                }
            } catch (e: Exception) {
                lastException = e
                currentAttempt++
                if (currentAttempt < maxRetries) {
                    val delayMs = (1000L * (1 shl currentAttempt))
                    Timber.w("Download failed, retrying in ${delayMs}ms. Attempt $currentAttempt/$maxRetries")
                    delay(delayMs)
                }
            }
        }
        throw Exception("Failed to download media after $maxRetries attempts", lastException)
    }

    fun shouldAutoDownload(mimeType: String, sizeBytes: Long? = null): Boolean {
        val isWifi = networkMonitor.isOnWifi()
        
        return if (isWifi) {
            // On WiFi: auto-download images, audio, video, GIF. Exclude documents.
            when {
                mimeType.startsWith("image/") -> true
                mimeType.startsWith("audio/") -> true
                mimeType.startsWith("video/") -> true
                else -> false
            }
        } else {
            // On Cellular Data: only images < 2MB, and all audio. No video, no GIF, no docs.
            when {
                mimeType.startsWith("image/") && mimeType != "image/gif" -> {
                    sizeBytes != null && sizeBytes < 2 * 1024 * 1024 // 2MB
                }
                mimeType.startsWith("audio/") -> true
                else -> false
            }
        }
    }
}
