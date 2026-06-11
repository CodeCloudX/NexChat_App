package com.nexchat.core.db

import android.util.Base64
import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    @TypeConverter
    fun fromByteArray(value: ByteArray?): String? =
        value?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

    @TypeConverter
    fun toByteArray(value: String?): ByteArray? =
        value?.let { Base64.decode(it, Base64.NO_WRAP) }

    // JSON is used instead of delimiter-joining to correctly handle strings containing commas.
    @TypeConverter
    fun fromStringList(value: List<String>?): String? =
        value?.let { Json.encodeToString<List<String>>(it) }

    @TypeConverter
    fun toStringList(value: String?): List<String>? =
        value?.let { Json.decodeFromString<List<String>>(it) }
}
