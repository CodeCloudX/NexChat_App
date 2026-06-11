package com.nexchat.core.media

import android.util.Base64
import com.nexchat.core.common.AppDispatchers
import com.nexchat.core.network.api.MediaApi
import com.nexchat.core.network.dto.ConfirmMediaRequest
import com.nexchat.core.network.dto.PresignedUrlRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class MediaResult(
    val mediaId: String,
    val mediaUrl: String,
    val mediaKeyBase64: String,
    val mediaIvBase64: String,
    val mediaSha256: String,
    val blurhash: String?
)

@Singleton
class MediaUploader @Inject constructor(
    private val mediaCompressor: MediaCompressor,
    private val thumbnailGenerator: ThumbnailGenerator,
    private val mediaEncryptor: MediaEncryptor,
    private val mediaApi: MediaApi,
    private val okHttpClient: OkHttpClient,
    private val dispatchers: AppDispatchers
) {
    suspend fun uploadMedia(
        chatId: String,
        file: File,
        mimeType: String
    ): Result<MediaResult> = withContext(dispatchers.io) {
        runCatching {
            // 1. Compress if it's an image
            val rawBytes = if (mediaCompressor.shouldCompress(mimeType)) {
                mediaCompressor.compress(file).getOrThrow()
            } else {
                file.readBytes()
            }

            // 2. Generate blurhash (if image)
            val blurhash = if (mimeType.startsWith("image/")) {
                thumbnailGenerator.generateBlurhash(rawBytes)
            } else null

            // 3. Encrypt
            val encryptedMedia = mediaEncryptor.encrypt(rawBytes)
            val encryptedBytes = encryptedMedia.encrypted

            // 4. Request Presigned URL (Aligned with Go Backend)
            val presignedReq = PresignedUrlRequest(
                chatId = chatId,
                filename = file.name,
                mimeType = mimeType,
                size = encryptedBytes.size.toLong()
            )
            
            val presignResp = mediaApi.getPresignedUrl(presignedReq)
            if (!presignResp.isSuccessful || presignResp.body() == null) {
                throw Exception("Failed to get presigned URL: ${presignResp.errorBody()?.string()}")
            }
            
            val uploadUrl = presignResp.body()!!.uploadUrl
            val mediaId = presignResp.body()!!.mediaId

            // 5. Upload to S3 with exponential backoff (Retry 3x)
            uploadWithRetry(uploadUrl, encryptedBytes)

            // 6. Confirm Upload
            val confirmResp = mediaApi.confirmUpload(ConfirmMediaRequest(mediaId))
            if (!confirmResp.isSuccessful || confirmResp.body() == null) {
                throw Exception("Failed to confirm media upload: ${confirmResp.errorBody()?.string()}")
            }

            val finalMediaUrl = confirmResp.body()!!.url

            // 7. Return complete metadata for WsEvent construction
            MediaResult(
                mediaId = mediaId,
                mediaUrl = finalMediaUrl,
                mediaKeyBase64 = Base64.encodeToString(encryptedMedia.mediaKey, Base64.NO_WRAP),
                mediaIvBase64 = Base64.encodeToString(encryptedMedia.iv, Base64.NO_WRAP),
                mediaSha256 = encryptedMedia.sha256Hex,
                blurhash = blurhash
            )
        }
    }

    private suspend fun uploadWithRetry(uploadUrl: String, bytes: ByteArray, maxRetries: Int = 3) {
        var currentAttempt = 0
        var lastException: Exception? = null

        while (currentAttempt < maxRetries) {
            try {
                val requestBody = bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(uploadUrl)
                    .put(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    return // Success
                } else {
                    throw Exception("S3 upload failed with code ${response.code}")
                }
            } catch (e: Exception) {
                lastException = e
                currentAttempt++
                if (currentAttempt < maxRetries) {
                    val delayMs = (1000L * (1 shl currentAttempt))
                    Timber.w("Upload failed, retrying in ${delayMs}ms. Attempt $currentAttempt/$maxRetries")
                    delay(delayMs)
                }
            }
        }
        throw Exception("Failed to upload media after $maxRetries attempts", lastException)
    }
}
