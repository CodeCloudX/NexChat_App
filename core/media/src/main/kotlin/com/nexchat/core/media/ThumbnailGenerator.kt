package com.nexchat.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vanniktech.blurhash.BlurHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThumbnailGenerator @Inject constructor() {
    
    suspend fun generateBlurhash(bytes: ByteArray): String? = withContext(Dispatchers.Default) {
        try {
            // Decode with sample size to save memory if it's large
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            
            // Calculate inSampleSize
            val requiredWidth = 100
            val requiredHeight = 100
            var inSampleSize = 1
            
            if (options.outHeight > requiredHeight || options.outWidth > requiredWidth) {
                val halfHeight: Int = options.outHeight / 2
                val halfWidth: Int = options.outWidth / 2
                while (halfHeight / inSampleSize >= requiredHeight && halfWidth / inSampleSize >= requiredWidth) {
                    inSampleSize *= 2
                }
            }
            
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return@withContext null
            
            // Generate BlurHash with 4x3 components
            val blurhash = BlurHash.encode(bitmap, 4, 3)
            bitmap.recycle()
            
            blurhash
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to generate blurhash")
            null
        }
    }
}
