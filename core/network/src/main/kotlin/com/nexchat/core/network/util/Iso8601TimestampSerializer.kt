package com.nexchat.core.network.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

/**
 * A custom serializer to map the Go backend's `time.Time` ISO-8601 strings
 * into Kotlin `Long` Unix epoch milliseconds.
 */
object Iso8601TimestampSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Iso8601Timestamp", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Long {
        val string = decoder.decodeString()
        return try {
            Instant.parse(string).toEpochMilli()
        } catch (e: Exception) {
            // Fallback if the string is just a number
            string.toLongOrNull() ?: 0L
        }
    }

    override fun serialize(encoder: Encoder, value: Long) {
        val string = Instant.ofEpochMilli(value).toString()
        encoder.encodeString(string)
    }
}
