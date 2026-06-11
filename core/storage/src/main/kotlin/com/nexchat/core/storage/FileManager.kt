package com.nexchat.core.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val baseDir: File = context.getExternalFilesDir(null) ?: context.filesDir

    private val imagesReceived = File(baseDir, "NexChat Images/Received").apply { mkdirs() }
    private val imagesSent = File(baseDir, "NexChat Images/Sent").apply { mkdirs() }
    private val videos = File(baseDir, "NexChat Video").apply { mkdirs() }
    private val audio = File(baseDir, "NexChat Audio").apply { mkdirs() }
    private val documents = File(baseDir, "NexChat Documents").apply { mkdirs() }
    
    private val cache = File(context.cacheDir, "nexchat").apply { mkdirs() }
    private val temp = File(context.cacheDir, "nexchat_temp").apply { mkdirs() }

    suspend fun saveMedia(messageId: String, bytes: ByteArray, mimeType: String, isSent: Boolean = false): String = withContext(Dispatchers.IO) {
        val dir = when {
            mimeType.startsWith("image/") -> if (isSent) imagesSent else imagesReceived
            mimeType.startsWith("video/") -> videos
            mimeType.startsWith("audio/") -> audio
            else -> documents
        }
        
        val extension = when (mimeType) {
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "video/mp4" -> ".mp4"
            "audio/mpeg" -> ".mp3"
            "audio/ogg" -> ".ogg"
            "application/pdf" -> ".pdf"
            else -> "" // Unknown types don't get extensions natively here, usually handled gracefully by OS.
        }

        val file = File(dir, "$messageId$extension")
        file.writeBytes(bytes)
        file.absolutePath
    }

    suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    suspend fun saveToGallery(path: String, mimeType: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (!file.exists()) throw java.io.FileNotFoundException("File not found at path: $path")

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.SIZE, file.length())
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relativePath = when {
                        mimeType.startsWith("image/") -> android.os.Environment.DIRECTORY_PICTURES + "/NexChat"
                        mimeType.startsWith("video/") -> android.os.Environment.DIRECTORY_MOVIES + "/NexChat"
                        mimeType.startsWith("audio/") -> android.os.Environment.DIRECTORY_MUSIC + "/NexChat"
                        else -> android.os.Environment.DIRECTORY_DOWNLOADS + "/NexChat"
                    }
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = when {
                mimeType.startsWith("image/") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                }
                mimeType.startsWith("video/") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                }
                mimeType.startsWith("audio/") -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        throw UnsupportedOperationException("Saving documents to gallery is not supported below API 29")
                    }
                }
            }

            val uri = resolver.insert(collection, contentValues)
                ?: throw Exception("Failed to create MediaStore entry")

            resolver.openOutputStream(uri)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            Timber.d("Successfully saved ${file.name} to gallery")
        }
    }

    suspend fun cleanTempDirectory() = withContext(Dispatchers.IO) {
        temp.listFiles()?.forEach { it.delete() }
    }

    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        cache.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
