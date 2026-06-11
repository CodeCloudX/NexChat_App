package com.nexchat.core.media

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaCompressor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun shouldCompress(mimeType: String): Boolean {
        // We only compress images (excluding GIFs)
        return mimeType.startsWith("image/") && mimeType != "image/gif"
    }

    suspend fun compress(file: File): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val compressedFile = Compressor.compress(context, file) {
                resolution(1280, 1280)
                quality(80)
                format(android.graphics.Bitmap.CompressFormat.JPEG)
            }
            compressedFile.readBytes()
        }
    }
}
